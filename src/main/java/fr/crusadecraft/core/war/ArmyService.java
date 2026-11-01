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
 *
 * <p>P27 : marcher vers un point (depuis la carte, ou par commande), en formation (masse, ligne, colonne, carré) ;
 * les longues marches se font par étapes de quelques dizaines de blocs, chunks chargés seulement.
 */
public final class ArmyService {
    /** L'étiquette des soldats de levée, suivie de l'identifiant de leur royaume. */
    public static final String TAG = "crusadecraft_levee";
    /** Au plus à cette distance d'un camp pour y rassembler l'armée. */
    static final int MUSTER_RANGE = 48;

    public enum Order {
        FOLLOW, HOLD, MARCH;

        public String key() {
            return "crusadecraft.army.order." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Une étape de marche : on ne demande jamais un chemin plus long, les soldats ne le trouveraient pas. */
    static final int STAGE = 28;

    /**
     * Ce que fait l'armée d'un royaume : son ordre, le poste à tenir (ou le but de la marche), la formation et la
     * direction qu'elle regarde (en radians). Perdu au redémarrage : on retombe sur « tenir » sa place.
     */
    record Command(Order order, BlockPos post, Formation formation, double facing) {}

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
        COMMANDS.put(realm.id(), new Command(Order.HOLD, center, formationOf(realm.id()), 0));
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} rassemble {} soldat(s) au camp {}", realm.name(), spawned, center);
        return Outcome.ok(Component.translatable("crusadecraft.army.mustered", spawned, perSoldier));
    }

    /** La direction que regarde un joueur, en radians (0 = vers les x croissants). */
    static double facing(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return Math.atan2(Math.cos(yaw), -Math.sin(yaw));
    }

    static Formation formationOf(UUID realm) {
        Command c = COMMANDS.get(realm);
        return c == null ? Formation.MASSE : c.formation();
    }

    /** Un ordre à toute l'armée : suivre le souverain, ou tenir là où il se tient, face à où il regarde. */
    public static Outcome order(ServerPlayer player, Order order) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        int n = soldiers(player.serverLevel(), found.get().id()).size();
        if (n == 0) return Outcome.no("crusadecraft.army.none");
        COMMANDS.put(found.get().id(), new Command(order, player.blockPosition(), formationOf(found.get().id()), facing(player)));
        return Outcome.ok(Component.translatable(order == Order.FOLLOW ? "crusadecraft.army.follow" : "crusadecraft.army.hold", n));
    }

    /** Le centre de l'armée : la moyenne des soldats. */
    static double[] centroid(List<Mob> soldiers) {
        double x = 0, z = 0;
        for (Mob m : soldiers) {
            x += m.getX();
            z += m.getZ();
        }
        return new double[]{x / soldiers.size(), z / soldiers.size()};
    }

    /** Marcher vers ce point du monde où se tient le souverain (depuis la carte, ou {@code /crusade armee marcher}). */
    public static Outcome march(ServerPlayer player, int x, int z) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        ServerLevel level = player.serverLevel();
        List<Mob> army = soldiers(level, found.get().id());
        if (army.isEmpty()) return Outcome.no("crusadecraft.army.none");
        double[] c = centroid(army);
        int y = level.hasChunkAt(new BlockPos(x, 0, z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : (int) army.get(0).getY();
        double facing = Math.atan2(z - c[1], x - c[0]);
        COMMANDS.put(found.get().id(), new Command(Order.MARCH, new BlockPos(x, y, z), formationOf(found.get().id()), facing));
        int distance = (int) Math.round(Math.sqrt((x - c[0]) * (x - c[0]) + (z - c[1]) * (z - c[1])));
        return Outcome.ok(Component.translatable("crusadecraft.army.march", army.size(), x, z, distance));
    }

    /** La formation de l'armée ; elle s'applique à l'ordre en cours. */
    public static Outcome formation(ServerPlayer player, Formation formation) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Command c = COMMANDS.get(found.get().id());
        int n = soldiers(player.serverLevel(), found.get().id()).size();
        if (n == 0 || c == null) return Outcome.no("crusadecraft.army.none");
        COMMANDS.put(found.get().id(), new Command(c.order(), c.post(), formation, c.facing()));
        return Outcome.ok(Component.translatable("crusadecraft.army.formation.set", Component.translatable(formation.key())));
    }

    /** Ce que l'écran de guerre montre de l'armée en chair et en os : soldats présents, ordre, formation, but. */
    public record Status(int soldiers, Order order, Formation formation, BlockPos post) {}

    public static Status status(MinecraftServer server, UUID realm) {
        int n = 0;
        for (ServerLevel level : server.getAllLevels()) n += soldiers(level, realm).size();
        Command c = COMMANDS.get(realm);
        return new Status(n, c == null ? Order.HOLD : c.order(), c == null ? Formation.MASSE : c.formation(), c == null ? null : c.post());
    }

    /** Où est l'armée d'un royaume dans ce monde, et combien de soldats : pour son pion sur la carte. */
    public static Optional<double[]> position(ServerLevel level, UUID realm) {
        List<Mob> army = soldiers(level, realm);
        if (army.isEmpty()) return Optional.empty();
        double[] c = centroid(army);
        return Optional.of(new double[]{c[0], c[1], army.size()});
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

    /** Le royaume d'un soldat de levée, lu dans son étiquette. */
    static UUID realmOf(Entity e) {
        return realmOf(e, TAG);
    }

    /** Le royaume inscrit dans une étiquette « préfixe:identifiant » (levées, ostes). */
    static UUID realmOf(Entity e, String prefix) {
        for (String tag : e.getTags()) {
            if (!tag.startsWith(prefix + ":")) continue;
            try {
                return UUID.fromString(tag.substring(prefix.length() + 1));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Chaque seconde : chaque armée suit son souverain, tient son poste ou marche, chacun à sa place dans la formation.
     * Une seule passe sur les entités par monde ; les soldats sont rangés par identifiant, pour garder leur place.
     */
    public static void tick(MinecraftServer server) {
        if (COMMANDS.isEmpty()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Map<UUID, ServerPlayer> rulers = new HashMap<>();
        for (UUID id : COMMANDS.keySet()) data.realm(id).ifPresent(r -> rulers.put(id, server.getPlayerList().getPlayer(r.ruler())));
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, List<PathfinderMob>> armies = new HashMap<>();
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof PathfinderMob mob) || !mob.isAlive() || !e.getTags().contains(TAG)) continue;
                UUID realm = realmOf(e);
                if (realm != null && COMMANDS.containsKey(realm)) armies.computeIfAbsent(realm, k -> new ArrayList<>()).add(mob);
            }
            for (Map.Entry<UUID, List<PathfinderMob>> army : armies.entrySet()) {
                Command c = COMMANDS.get(army.getKey());
                ServerPlayer ruler = rulers.get(army.getKey());
                boolean follow = c.order() == Order.FOLLOW && ruler != null && ruler.level() == level;
                BlockPos base = follow ? ruler.blockPosition() : c.post();
                double facing = follow ? facing(ruler) : c.facing();
                List<PathfinderMob> list = army.getValue();
                list.sort(java.util.Comparator.comparing(Entity::getUUID));
                for (int i = 0; i < list.size(); i++) {
                    PathfinderMob mob = list.get(i);
                    if (mob.getTarget() != null) continue;
                    double[] off = c.formation().offset(i, list.size(), facing);
                    double gx = base.getX() + 0.5 + off[0], gz = base.getZ() + 0.5 + off[1];
                    double dx = gx - mob.getX(), dz = gz - mob.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= 1.5) continue;
                    if (d > STAGE) {
                        // Une étape vers le but, sur un chunk chargé : on ne demande pas de chemin plus long.
                        gx = mob.getX() + dx / d * STAGE;
                        gz = mob.getZ() + dz / d * STAGE;
                    }
                    BlockPos step = BlockPos.containing(gx, 0, gz);
                    if (!level.hasChunkAt(step)) continue;
                    int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, step.getX(), step.getZ());
                    mob.getNavigation().moveTo(gx, gy, gz, 1.0);
                }
            }
        }
    }

    /** Un soldat de levée tombe : un homme de moins pour de bon, dans l'armée de son royaume. */
    public static void onDeath(MinecraftServer server, Entity entity) {
        if (!entity.getTags().contains(TAG)) return;
        UUID realmId = realmOf(entity);
        if (realmId == null) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        data.realm(realmId).ifPresent(r -> {
            int men = WarService.men(r);
            r.setArmyMen(Math.max(0, men - CrusadeConfig.SERVER.menPerSoldier.get()));
            data.setDirty();
        });
    }
}
