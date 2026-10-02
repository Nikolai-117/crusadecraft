package fr.crusadecraft.core.realm;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.logic.FoundingRules;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Opérations serveur sur les royaumes : fonder, consulter, réconcilier avec MineColonies.
 *
 * <p>Toute demande venant du client est revérifiée ici. L'écran de fondation n'est qu'un formulaire.
 */
public final class RealmService {

    private RealmService() {}

    /** Résultat du contrôle de fondation à une position donnée. */
    public record FoundingCheck(FoundingRules.Verdict verdict, ColonyRef colony, int townHallLevel, int minLevel) {}

    /** Ce que le joueur a saisi dans l'écran de fondation. */
    public record FoundingRequest(BlockPos thronePos, String name, String motto, String house, int colorIndex,
                                  boolean feminine) {}

    public static Optional<Realm> realmOf(MinecraftServer server, UUID player) {
        return CrusadeSavedData.get(server).realmOfRuler(player);
    }

    public static FoundingCheck checkFounding(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int minLevel = CrusadeConfig.SERVER.minTownHallLevel.get();
        boolean ready = MineColoniesBridge.isReady();
        boolean alreadyRuler = data.realmOfRuler(player.getUUID()).isPresent();
        Optional<ColonyRef> colony = ready ? MineColoniesBridge.colonyAt(player.level(), pos) : Optional.empty();
        ColonyRef ref = colony.orElse(null);
        boolean isOwner = ref != null && MineColoniesBridge.isOwner(ref, player.getUUID());
        boolean inTownHall = ref != null && (!CrusadeConfig.SERVER.throneMustBeInTownHall.get()
                || MineColoniesBridge.isInsideTownHall(ref, pos));
        int level = ref == null ? 0 : MineColoniesBridge.townHallLevel(ref);
        boolean isCapital = ref != null && data.realmWithCapital(ref).isPresent();
        FoundingRules.Verdict verdict = FoundingRules.check(ready, alreadyRuler, ref != null, isOwner, inTownHall,
                level, minLevel, isCapital);
        return new FoundingCheck(verdict, ref, level, minLevel);
    }

    /**
     * Fonde le royaume si tout est encore valable. Renvoie le royaume créé, ou vide avec un message
     * déjà envoyé au joueur.
     */
    public static Optional<Realm> found(ServerPlayer player, FoundingRequest request) {
        if (player.distanceToSqr(request.thronePos().getCenter()) > 64) {
            player.sendSystemMessage(Component.translatable("crusadecraft.founding.too_far"));
            return Optional.empty();
        }
        FoundingCheck check = checkFounding(player, request.thronePos());
        if (!check.verdict().ok()) {
            player.sendSystemMessage(Component.translatable(check.verdict().messageKey(), check.minLevel()));
            return Optional.empty();
        }
        String name = TextRules.clean(request.name(), TextRules.NAME_MAX);
        if (!TextRules.isValidName(name)) {
            player.sendSystemMessage(Component.translatable("crusadecraft.founding.bad_name"));
            return Optional.empty();
        }
        String motto = TextRules.clean(request.motto(), TextRules.MOTTO_MAX);
        String house = TextRules.clean(request.house(), TextRules.HOUSE_MAX);
        if (house.isEmpty()) house = player.getGameProfile().getName();
        int color = Heraldry.byOrdinal(request.colorIndex()).rgb();

        Realm realm = new Realm(UUID.randomUUID(), name, motto, house, color, player.getUUID(),
                player.getGameProfile().getName(), request.feminine(), check.colony(), TitleRank.BARONNIE,
                currentDay(player.server));
        realm.setPrestige(CrusadeConfig.SERVER.startingPrestige.get());
        CrusadeSavedData.get(player.server).add(realm);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} fonde le royaume « {} » (capitale {})",
                player.getGameProfile().getName(), name, check.colony());
        CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM,
                Component.translatable("crusadecraft.toast.founded.title"),
                Component.translatable("crusadecraft.toast.founded.body", name)));
        return Optional.of(realm);
    }

    public static RealmView view(MinecraftServer server, Realm realm) {
        List<ColonyInfo> colonies = new ArrayList<>();
        for (ColonyRef ref : realm.allColonies()) colonies.add(MineColoniesBridge.info(ref));
        List<RealmView.Seat> seats = new ArrayList<>();
        for (Post post : Post.values()) {
            if (!post.isCouncil()) continue;
            seats.add(seat(server, realm, post, realm.council().get(post), ""));
        }
        for (ColonyInfo colony : colonies) {
            seats.add(seat(server, realm, Post.MAIRE, realm.mayors().get(colony.ref()), colony.name()));
        }
        return new RealmView(realm.id(), realm.name(), realm.motto(), realm.house(), realm.color(), realm.rulerName(),
                realm.feminineTitle(), realm.rank(), realm.state(), realm.foundedDay(), currentDay(server),
                realm.gold(), realm.prestige(), realm.piety(), realm.dread(), colonies, seats);
    }

    private static RealmView.Seat seat(MinecraftServer server, Realm realm, Post post, CitizenRef holder, String place) {
        if (holder == null) return new RealmView.Seat(post, null, "", false, 0, place);
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(holder);
        String name = info.map(CitizenInfo::name).orElseGet(() -> CharacterService.nameOf(server, holder));
        boolean female = info.map(CitizenInfo::female).orElse(false);
        return new RealmView.Seat(post, holder, name, female, CharacterService.opinionOf(server, realm, holder), place);
    }

    public static boolean dissolve(MinecraftServer server, UUID realmId) {
        boolean removed = CrusadeSavedData.get(server).remove(realmId);
        if (removed) CrusadeCraft.LOGGER.info("CrusadeCraft : royaume {} dissous", realmId);
        return removed;
    }

    /** Jour du monde (cycles jour/nuit écoulés, sommeil compris). */
    public static long currentDay(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld == null ? 0 : overworld.getDayTime() / 24000L;
    }

    /** Remet chaque royaume en accord avec MineColonies. Ne modifie que nos propres données. */
    public static void reconcileAll(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        for (Realm realm : CrusadeSavedData.get(server).realms()) reconcile(server, realm);
    }

    public static void reconcileColony(MinecraftServer server, ColonyRef colony) {
        if (!MineColoniesBridge.isReady()) return;
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            if (realm.holds(colony)) reconcile(server, realm);
        }
    }

    /** Une colonie vient d'être supprimée dans MineColonies (elle peut encore être visible un instant). */
    public static void onColonyDeleted(MinecraftServer server, ColonyRef colony) {
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            if (realm.capital().equals(colony)) {
                setState(server, realm, RealmState.DORMANT_NO_CAPITAL);
            } else if (realm.holds(colony)) {
                reconcile(server, realm);
            }
        }
    }

    private static void reconcile(MinecraftServer server, Realm realm) {
        RealmState after;
        if (!MineColoniesBridge.exists(realm.capital())) {
            after = RealmState.DORMANT_NO_CAPITAL;
        } else if (!MineColoniesBridge.isOwner(realm.capital(), realm.ruler())) {
            after = RealmState.DORMANT_CAPITAL_LOST;
        } else {
            after = RealmState.ACTIVE;
        }
        setState(server, realm, after);
    }

    private static void setState(MinecraftServer server, Realm realm, RealmState after) {
        RealmState before = realm.state();
        if (after == before) return;
        realm.setState(after);
        CrusadeSavedData.get(server).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : royaume « {} » : {} -> {}", realm.name(), before, after);
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) {
            CrusadeNetwork.toPlayer(ruler, ToastPacket.of(after.isActive() ? ToastPacket.Kind.REALM : ToastPacket.Kind.ALERT,
                    Component.literal(realm.name()), Component.translatable(after.key())));
        }
    }

    /** À la connexion : on rafraîchit le nom affiché du souverain (un pseudo peut changer). */
    public static void onLogin(ServerPlayer player) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        data.realmOfRuler(player.getUUID()).ifPresent(realm -> {
            String name = player.getGameProfile().getName();
            if (!name.equals(realm.rulerName())) {
                realm.setRulerName(name);
                data.setDirty();
            }
        });
    }
}
