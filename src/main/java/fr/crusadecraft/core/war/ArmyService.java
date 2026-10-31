package fr.crusadecraft.core.war;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.hyw.Soldiers;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les levées en chair et en os (P23) : au camp militaire du royaume, l'armée levée apparaît en vraies unités (Hundred
 * Years War, des golems à défaut), un soldat pour {@code hommesParSoldat} hommes, plafonné. Le souverain leur donne ses
 * ordres — suivre, tenir la position — et les démobilise ; un soldat tué est un homme de moins pour de bon.
 */
public final class ArmyService {
    /** L'étiquette des soldats de levée, suivie de l'identifiant de leur royaume. */
    public static final String TAG = "crusadecraft_levee";
    /** Au plus à cette distance d'un camp pour y rassembler l'armée. */
    static final int MUSTER_RANGE = 48;

    public enum Order { FOLLOW, HOLD }

    /** Ce que fait l'armée d'un royaume : son ordre, et le poste à tenir. Perdu au redémarrage : on retombe sur « tenir ». */
    record Command(Order order, BlockPos post) {}

    private static final Map<UUID, Command> COMMANDS = new HashMap<>();

    private ArmyService() {}

    public static void onServerStopped() {
        COMMANDS.clear();
    }

    /** Combien de soldats pour tant d'hommes : un pour {@code perSoldier}, au moins un s'il y a des hommes, plafonné. */
    public static int soldiersFor(int men, int perSoldier, int cap) {
        if (men <= 0) return 0;
        return Math.max(1, Math.min(cap, men / Math.max(1, perSoldier)));
    }

    static String tagOf(UUID realm) {
        return TAG + ":" + realm;
    }

    /** Le camp militaire bâti de ce royaume le plus proche du joueur, à portée. */
    static Optional<Site> campNear(ServerPlayer player, Realm realm) {
        Site best = null;
        double bestD = (double) MUSTER_RANGE * MUSTER_RANGE;
        for (Site s : CrusadeSavedData.get(player.server).world().sites()) {
            if (!s.built() || s.kind() != SiteKind.CAMP || !realm.id().equals(s.owner())) continue;
            if (!s.dimension().equals(player.level().dimension().location())) continue;
            double d = s.anchor().distSqr(player.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Les soldats de levée de ce royaume dans ce monde (chargés). */
    public static List<Mob> soldiers(ServerLevel level, UUID realm) {
        String tag = tagOf(realm);
        List<Mob> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Mob m && m.isAlive() && m.getTags().contains(tag)) out.add(m);
        }
        return out;
    }

    /** Rassemble l'armée levée au camp tout proche : les hommes deviennent des soldats. */
    public static Outcome muster(ServerPlayer player) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (!realm.armyRaised()) return Outcome.no("crusadecraft.army.not_raised");
        Optional<Site> camp = campNear(player, realm);
        if (camp.isEmpty()) return Outcome.no("crusadecraft.army.no_camp", MUSTER_RANGE);
        ServerLevel level = player.serverLevel();
        int perSoldier = CrusadeConfig.SERVER.menPerSoldier.get();
        int wanted = soldiersFor(WarService.men(realm), perSoldier, CrusadeConfig.SERVER.armyCap.get());
        int present = soldiers(level, realm.id()).size();
        int toSpawn = wanted - present;
        if (toSpawn <= 0) return Outcome.no("crusadecraft.army.already", present);
        BlockPos center = camp.get().anchor();
        Component name = Component.translatable("crusadecraft.army.soldier", realm.name());
        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            double a = i * 2.399963, r = 3 + Math.sqrt(i) * 1.6;
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
            Mob m = Soldiers.ally(level, player, present + i);
            if (m == null) continue;
            m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, (float) Math.toDegrees(a), 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
            m.setCustomName(name);
            m.setPersistenceRequired();
            m.addTag(TAG);
            m.addTag(tagOf(realm.id()));
            if (level.addFreshEntity(m)) spawned++;
        }
        COMMANDS.put(realm.id(), new Command(Order.HOLD, center));
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} rassemble {} soldat(s) au camp {}", realm.name(), spawned, center);
        return Outcome.ok(Component.translatable("crusadecraft.army.mustered", spawned, perSoldier));
    }

    /** Un ordre à toute l'armée : suivre le souverain, ou tenir là où il se tient. */
    public static Outcome order(ServerPlayer player, Order order) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        int n = soldiers(player.serverLevel(), found.get().id()).size();
        if (n == 0) return Outcome.no("crusadecraft.army.none");
        COMMANDS.put(found.get().id(), new Command(order, player.blockPosition()));
        return Outcome.ok(Component.translatable(order == Order.FOLLOW ? "crusadecraft.army.follow" : "crusadecraft.army.hold", n));
    }

    /** Les survivants rentrent chez eux : ils quittent le monde, les hommes restent comptés à l'armée. */
    public static Outcome dismiss(ServerPlayer player) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        int n = 0;
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Mob m : soldiers(level, found.get().id())) {
                m.discard();
                n++;
            }
        }
        COMMANDS.remove(found.get().id());
        if (n == 0) return Outcome.no("crusadecraft.army.none");
        return Outcome.ok(Component.translatable("crusadecraft.army.dismissed", n));
    }

    /** Chaque seconde : chaque armée suit son souverain ou regagne son poste. */
    public static void tick(MinecraftServer server) {
        if (COMMANDS.isEmpty()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        for (Map.Entry<UUID, Command> e : COMMANDS.entrySet()) {
            Optional<Realm> realm = data.realm(e.getKey());
            if (realm.isEmpty()) continue;
            ServerPlayer ruler = server.getPlayerList().getPlayer(realm.get().ruler());
            for (ServerLevel level : server.getAllLevels()) {
                for (Mob m : soldiers(level, e.getKey())) {
                    if (!(m instanceof PathfinderMob mob) || mob.getTarget() != null) continue;
                    BlockPos goal = e.getValue().order() == Order.FOLLOW && ruler != null && ruler.level() == level
                            ? ruler.blockPosition() : e.getValue().post();
                    if (mob.blockPosition().distSqr(goal) > 25) mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
                }
            }
        }
    }

    /** Un soldat de levée tombe : un homme de moins pour de bon, dans l'armée de son royaume. */
    public static void onDeath(MinecraftServer server, Entity entity) {
        if (!entity.getTags().contains(TAG)) return;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(TAG + ":")) continue;
            try {
                UUID realmId = UUID.fromString(tag.substring(TAG.length() + 1));
                CrusadeSavedData data = CrusadeSavedData.get(server);
                data.realm(realmId).ifPresent(r -> {
                    int men = WarService.men(r);
                    r.setArmyMen(Math.max(0, men - CrusadeConfig.SERVER.menPerSoldier.get()));
                    data.setDirty();
                });
            } catch (IllegalArgumentException ignored) {
                // Une étiquette abîmée : rien à compter.
            }
        }
    }
}
