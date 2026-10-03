package fr.crusadecraft.core.court;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.intrigue.Chronicle;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le tournoi, à la Lice : on invite ses chevaliers (conseillers et maires) et les champions des royaumes voisins,
 * on joute. L'hôte gagne du prestige ; le vainqueur devient champion ; on revient vétéran, ou blessé. Il faut la
 * recherche « Chevalerie » (réglable). Avec Epic Knights, le champion de la cour rapporte une lance.
 */
public final class TournamentService {
    private static final String COOLDOWN = "crusadecraft:tournament";
    /** Le dernier tableau de chaque royaume, pour le montrer à la Lice (non enregistré). */
    private static final Map<UUID, List<Component>> LAST = new HashMap<>();

    private TournamentService() {}

    public static void onServerStopped() {
        LAST.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Ses chevaliers : conseillers et maires libres et adultes, les meilleurs d'abord. */
    static List<CitizenRef> knights(MinecraftServer server, Realm realm) {
        List<CitizenRef> out = new ArrayList<>();
        List<CitizenRef> court = new ArrayList<>(realm.council().values());
        court.addAll(realm.mayors().values());
        for (CitizenRef ref : court) {
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
            if (info.isEmpty() || info.get().child()) continue;
            CharacterRecord rec = CharacterService.record(server, info.get());
            if (rec.status() == CharacterStatus.LIBRE && !rec.deceased()) out.add(ref);
        }
        out.sort(Comparator.comparingInt((CitizenRef r) -> -martial(server, r)));
        return out.subList(0, Math.min(5, out.size()));
    }

    private static int martial(MinecraftServer server, CitizenRef ref) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty()) return 0;
        return CharacterService.stats(info.get().skills(), CharacterService.record(server, info.get()).traits()).total()[Stat.MARTIAL.ordinal()];
    }

    /** Les royaumes IA invités : les plus proches de la capitale, en paix avec l'hôte, représentés par leur maréchal. */
    static List<AiRealm> guests(MinecraftServer server, Realm realm, ServerPlayer viewer, int places) {
        List<AiRealm> out = new ArrayList<>();
        for (AiRealm ai : data(server).world().realms()) {
            if (ai.landless() || !ai.discoveredBy(viewer.getUUID()) || data(server).wars().between(ai.id(), realm.id()).isPresent()) continue;
            out.add(ai);
        }
        double x = viewer.getX(), z = viewer.getZ();
        out.sort(Comparator.comparingDouble(a -> {
            AiProvince c = a.capital();
            return c == null ? Double.MAX_VALUE : c.distanceSqr(x, z);
        }));
        return out.subList(0, Math.max(0, Math.min(places, out.size())));
    }

    private static AiCharacter champion(AiRealm ai) {
        AiCharacter marshal = ai.council().get(Post.MARECHAL);
        return marshal != null ? marshal : ai.ruler();
    }

    public static long cooldownLeft(MinecraftServer server, Realm realm) {
        Long last = realm.intrigue().eventCooldowns().get(COOLDOWN);
        if (last == null) return 0;
        return Math.max(0, last + TournamentRules.COOLDOWN_DAYS - RealmService.currentDay(server));
    }

    /** Ce qui empêche d'organiser un tournoi, ou {@code null}. */
    public static String problem(MinecraftServer server, Realm realm) {
        if (CrusadeConfig.SERVER.tournamentNeedsResearch.get() && MineColoniesBridge.researchEffect(realm.capital(), Law.Research.CHEVALERIE) <= 0) {
            return "crusadecraft.tournament.need_research";
        }
        if (cooldownLeft(server, realm) > 0) return "crusadecraft.tournament.cooldown";
        if (realm.gold() < TournamentRules.cost(realm.rank())) return "crusadecraft.tournament.no_gold";
        if (knights(server, realm).isEmpty()) return "crusadecraft.tournament.no_knight";
        return null;
    }

    public static CouncilActions.Outcome host(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        String problem = problem(server, realm);
        if (problem != null) return CouncilActions.Outcome.no(problem);
        long today = RealmService.currentDay(server);
        Random random = new Random(today * 131L ^ realm.id().getLeastSignificantBits());
        List<CitizenRef> knights = knights(server, realm);
        List<AiRealm> guests = guests(server, realm, player, TournamentRules.MAX_ENTRANTS - knights.size());
        List<TournamentRules.Entrant> entrants = new ArrayList<>();
        for (int i = 0; i < knights.size(); i++) {
            CitizenRef ref = knights.get(i);
            CharacterRecord rec = data(server).character(ref).orElseThrow();
            entrants.add(new TournamentRules.Entrant(i, CharacterService.nameOf(server, ref), true, martial(server, ref), rec.traits()));
        }
        for (int i = 0; i < guests.size(); i++) {
            AiCharacter c = champion(guests.get(i));
            entrants.add(new TournamentRules.Entrant(knights.size() + i, c.name(), false, c.stat(Stat.MARTIAL), c.traits()));
        }
        TreasuryService.charge(server, realm, LedgerCategory.TOURNOIS, TournamentRules.cost(realm.rank()));
        realm.intrigue().eventCooldowns().put(COOLDOWN, today);
        TournamentRules.Result result = TournamentRules.run(entrants, random);
        int prestige = TournamentRules.hostPrestige(realm.rank(), entrants.size());

        // Les chevaliers de la cour : vétérans s'ils ont vaincu, blessés parfois, tous honorés d'avoir jouté.
        for (TournamentRules.Entrant e : entrants) {
            if (!e.mine()) continue;
            CharacterRecord rec = data(server).character(knights.get(e.index())).orElseThrow();
            rec.addModifier(OpinionModifier.of(OpinionReason.TOURNOI, today, 1));
            long wins = result.bouts().stream().filter(b -> b.winner() == e).count();
            if (wins > 0 && random.nextBoolean()) rec.addTrait(Trait.VETERAN);
            if (result.bouts().stream().anyMatch(b -> b.wounded() && b.winner() != e && (b.a() == e || b.b() == e))) rec.addTrait(Trait.BLESSE);
        }
        for (AiRealm g : guests) data(server).diplomacy().between(g.id(), realm.id()).addOpinion(g.id(), OpinionModifier.of(OpinionReason.TOURNOI, today, 1));
        TournamentRules.Entrant champ = result.champion();
        boolean mineWon = champ != null && champ.mine();
        if (mineWon) {
            CharacterRecord rec = data(server).character(knights.get(champ.index())).orElseThrow();
            rec.addTrait(Trait.CHAMPION);
            rec.addModifier(OpinionModifier.of(OpinionReason.TOURNOI_VICTOIRE, today, 1));
            rec.remember(today, "crusadecraft.history.champion");
            prestige += 30;
            prize(player);
        } else if (champ != null) {
            AiRealm g = guests.get(champ.index() - knights.size());
            g.setPrestige(g.prestige() + 20);
        }
        realm.setPrestige(realm.prestige() + prestige);

        String champName = champ == null ? "?" : champ.name();
        Component champOf = champ == null ? Component.literal("?") : mineWon ? Component.literal(realm.name())
                : AiNames.realm(guests.get(champ.index() - knights.size()).rank(), guests.get(champ.index() - knights.size()).place());
        List<Component> lines = new ArrayList<>();
        for (TournamentRules.Bout b : result.bouts()) {
            lines.add(Component.translatable(b.wounded() ? "crusadecraft.tournament.bout.wounded" : "crusadecraft.tournament.bout",
                    b.round(), b.a().name(), b.b().name(), b.winner().name()));
        }
        lines.add(Component.translatable(mineWon ? "crusadecraft.tournament.champion.mine" : "crusadecraft.tournament.champion.guest", champName, champOf,
                prestige));
        LAST.put(realm.id(), lines);
        Chronicle.record(server, realm, "crusadecraft.chronicle.tournament", Component.literal(champName), champOf);
        CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.tournament.title"),
                Component.translatable("crusadecraft.toast.tournament.body", champName, prestige)));
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.tournament.held", champName));
    }

    /** Avec Epic Knights, le champion de la cour rapporte une lance de ce mod (la meilleure qu'on trouve). */
    static void prize(ServerPlayer player) {
        if (!ModList.get().isLoaded("magistuarmory")) return;
        Item best = null;
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (!id.getNamespace().equals("magistuarmory") || !id.getPath().contains("lance")) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (best == null || id.getPath().contains("iron")) best = item;
        }
        if (best != null) {
            ItemStack stack = new ItemStack(best);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    public static TournamentView view(MinecraftServer server, Realm realm, ServerPlayer viewer) {
        List<String> entrants = new ArrayList<>();
        List<CitizenRef> knights = knights(server, realm);
        for (CitizenRef k : knights) entrants.add(CharacterService.nameOf(server, k));
        int own = entrants.size();
        for (AiRealm g : guests(server, realm, viewer, TournamentRules.MAX_ENTRANTS - knights.size())) {
            entrants.add(Component.translatable("crusadecraft.tournament.guest", champion(g).name(), AiNames.realm(g.rank(), g.place())).getString());
        }
        String problem = problem(server, realm);
        return new TournamentView(TournamentRules.cost(realm.rank()), realm.gold(), problem == null ? "" : problem, cooldownLeft(server, realm),
                entrants, own, LAST.getOrDefault(realm.id(), List.of()));
    }
}
