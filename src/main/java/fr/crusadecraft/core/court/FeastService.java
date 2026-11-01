package fr.crusadecraft.core.court;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.intrigue.Chronicle;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les fêtes et les deuils de la cour (P26). Un banquet coûte de l'or et rapporte du prestige ; conseillers et maires
 * l'apprécient, les voisins invités aussi ; pendant une demi-journée, les gens du registre qui vivent dans le monde
 * se rassemblent à la cour. À la mort d'un chef de maison, du souverain, de son conjoint ou de son héritier, la cour
 * prend le deuil quelques jours : pas de banquet, et le premier jour tout le monde se rend aux funérailles.
 */
public final class FeastService {
    private static final String COOLDOWN = "crusadecraft:feast";
    /** Un banquet dure une demi-journée du monde. */
    public static final long FEAST_TICKS = 12000;
    /** Les voisins invités au plus. */
    static final int GUESTS = 3;

    private FeastService() {}

    /** Ce que coûte un banquet : soixante pièces par rang, de la baronnie à l'empire. */
    public static long cost(TitleRank rank) {
        return 60L * (rank.ordinal() + 1);
    }

    /** Le prestige qu'il rapporte, plus cinq par voisin venu. */
    public static int prestige(TitleRank rank, int guests) {
        return 10 + 5 * rank.ordinal() + 5 * guests;
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static long cooldownLeft(MinecraftServer server, Realm realm) {
        Long last = realm.intrigue().eventCooldowns().get(COOLDOWN);
        if (last == null) return 0;
        return Math.max(0, last + CrusadeConfig.SERVER.feastDays.get() - RealmService.currentDay(server));
    }

    /** La cour de ce royaume festoie-t-elle ? */
    public static boolean feasting(MinecraftServer server, UUID realm) {
        return data(server).realm(realm).map(r -> r.court().feasting(server.overworld().getGameTime())).orElse(false);
    }

    /** Est-ce le jour des funérailles (le premier du deuil) ? */
    public static boolean funeral(MinecraftServer server, UUID realm) {
        Optional<Realm> r = data(server).realm(realm);
        if (r.isEmpty()) return false;
        long today = RealmService.currentDay(server);
        return r.get().court().mourning(today) && today == r.get().court().mourningUntilDay() - CrusadeConfig.SERVER.mourningDays.get();
    }

    /** Ce qui empêche de donner un banquet, ou {@code null}. */
    public static String problem(MinecraftServer server, Realm realm) {
        long today = RealmService.currentDay(server);
        if (realm.court().mourning(today)) return "crusadecraft.feast.mourning";
        if (realm.court().feasting(server.overworld().getGameTime())) return "crusadecraft.feast.already";
        if (data(server).wars().atWar(realm.id())) return "crusadecraft.feast.war";
        if (cooldownLeft(server, realm) > 0) return "crusadecraft.feast.cooldown";
        if (realm.gold() < cost(realm.rank())) return "crusadecraft.feast.no_gold";
        return null;
    }

    /** Les voisins invités : les plus proches de la capitale, connus du souverain, en paix avec lui. */
    static List<AiRealm> guests(MinecraftServer server, Realm realm, ServerPlayer host) {
        List<AiRealm> out = new ArrayList<>();
        for (AiRealm ai : data(server).world().realms()) {
            if (ai.landless() || !ai.discoveredBy(host.getUUID()) || data(server).wars().between(ai.id(), realm.id()).isPresent()) continue;
            out.add(ai);
        }
        double x = host.getX(), z = host.getZ();
        out.sort(Comparator.comparingDouble(a -> {
            AiProvince c = a.capital();
            return c == null ? Double.MAX_VALUE : c.distanceSqr(x, z);
        }));
        return out.subList(0, Math.min(GUESTS, out.size()));
    }

    /** Les gens de la cour qui en gardent un souvenir : conseillers et maires. */
    static List<CitizenRef> courtiers(Realm realm) {
        List<CitizenRef> out = new ArrayList<>(realm.council().values());
        for (CitizenRef m : realm.mayors().values()) if (!out.contains(m)) out.add(m);
        return out;
    }

    private static void remember(MinecraftServer server, Realm realm, OpinionReason reason, long today) {
        for (CitizenRef ref : courtiers(realm)) {
            MineColoniesBridge.citizen(ref).ifPresent(info -> CharacterService.record(server, info).addModifier(OpinionModifier.of(reason, today, 1)));
        }
    }

    public static CouncilActions.Outcome hold(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        String problem = problem(server, realm);
        if (problem != null) return CouncilActions.Outcome.no(problem);
        long today = RealmService.currentDay(server);
        List<AiRealm> guests = guests(server, realm, player);
        TreasuryService.charge(server, realm, LedgerCategory.EVENEMENTS, cost(realm.rank()));
        realm.intrigue().eventCooldowns().put(COOLDOWN, today);
        realm.court().setFeastUntilTick(server.overworld().getGameTime() + FEAST_TICKS);
        int prestige = prestige(realm.rank(), guests.size());
        realm.setPrestige(realm.prestige() + prestige);
        remember(server, realm, OpinionReason.FESTIN, today);
        for (AiRealm g : guests) data(server).diplomacy().between(g.id(), realm.id()).addOpinion(g.id(), OpinionModifier.of(OpinionReason.FESTIN, today, 1));
        Chronicle.record(server, realm, "crusadecraft.chronicle.feast", Component.literal(String.valueOf(guests.size())));
        CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.feast.title"),
                Component.translatable("crusadecraft.toast.feast.body", guests.size(), prestige)));
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.feast.held", prestige));
    }

    /** La cour prend le deuil de cette personne (rien si le deuil est coupé dans les réglages). */
    public static void mourn(MinecraftServer server, Realm realm, UUID person, String name) {
        int days = CrusadeConfig.SERVER.mourningDays.get();
        if (days <= 0) return;
        long today = RealmService.currentDay(server);
        realm.court().mourn(person, name, today + days);
        remember(server, realm, OpinionReason.DEUIL_PARTAGE, today);
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) {
            CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.mourning.title"),
                    Component.translatable("crusadecraft.toast.mourning.body", name, days)));
        }
        data(server).setDirty();
    }

    /** Ce que l'écran de la maison montre de la vie de la cour. */
    public static HouseView.Court view(MinecraftServer server, Realm realm) {
        long today = RealmService.currentDay(server);
        String problem = problem(server, realm);
        boolean mourning = realm.court().mourning(today);
        return new HouseView.Court(cost(realm.rank()), problem == null ? "" : problem, cooldownLeft(server, realm),
                realm.court().feasting(server.overworld().getGameTime()), mourning ? realm.court().mournedName() : "",
                mourning ? realm.court().mourningUntilDay() - today : 0, realm.id());
    }
}
