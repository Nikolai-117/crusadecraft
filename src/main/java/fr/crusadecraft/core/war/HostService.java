package fr.crusadecraft.core.war;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.hyw.Soldiers;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les ostes des royaumes de l'ordinateur (P27) : en guerre, chacun lève un ost qui part de sa capitale et marche sur
 * la carte — l'attaquant vers la province qu'il réclame (à défaut la capitale ennemie), le défenseur à la rencontre
 * de l'ost adverse s'il approche, sinon il garde sa capitale. Loin des joueurs, l'ost n'est qu'un pion sur la carte et
 * sur Dynmap. Quand l'armée en chair et en os d'un joueur ennemi arrive à sa hauteur, l'ost paraît en vrais soldats
 * et la bataille se livre ; défait, il laisse la victoire au score de la guerre et se reforme chez lui trois jours plus
 * tard. Les raids sur les colonies (P8) restent ce qu'ils étaient.
 */
public final class HostService {
    /** L'étiquette des soldats d'un ost, suivie de l'identifiant de leur royaume. */
    public static final String TAG = "crusadecraft_ost";
    /** L'armée d'un joueur à moins de tant de blocs de l'ost : la bataille s'engage. */
    static final int ENGAGE = 48;
    /** Plus aucun soldat ennemi à moins de tant de blocs : l'ost se retire du monde (il reste un pion). */
    static final int DISENGAGE = 112;
    /** L'ost s'arrête à tant de blocs de son but. */
    static final int ARRIVED = 24;
    /** Jours avant qu'un ost défait se reforme. */
    static final int REFORM_DAYS = 3;
    /** Victoire au score de la guerre pour l'ost défait. */
    static final int VICTORY = 15;

    /** Les soldats en chair et en os de chaque ost (non enregistré : l'ost redevient un pion au redémarrage). */
    private static final Map<String, Set<UUID>> UNITS = new HashMap<>();

    private HostService() {}

    public static void onServerStopped() {
        UNITS.clear();
    }

    private static String key(UUID war, UUID realm) {
        return war + "|" + realm;
    }

    /** Avance d'un pas vers le but, sans le dépasser ; s'arrête à {@link #ARRIVED} blocs. Logique pure. */
    public static double[] step(double x, double z, double tx, double tz, double stride) {
        double dx = tx - x, dz = tz - z, d = Math.sqrt(dx * dx + dz * dz);
        if (d <= ARRIVED) return new double[]{x, z};
        double move = Math.min(stride, d - ARRIVED);
        return new double[]{x + dx / d * move, z + dz / d * move};
    }

    /** Les hommes d'un ost, d'après la force de son royaume dans cette guerre. */
    public static int men(MinecraftServer server, War w, UUID realm) {
        int side = w.side(realm);
        return WarService.strength(server, realm, side > 0 ? w.attackerExhaustion() : w.defenderExhaustion());
    }

    /** Le siège d'un royaume : la capitale d'un royaume IA, le centre de la capitale d'un joueur. */
    static Optional<double[]> seat(MinecraftServer server, UUID realm) {
        Optional<AiRealm> ai = WarService.ai(server, realm);
        if (ai.isPresent()) {
            AiProvince c = ai.get().capital();
            return c == null ? Optional.empty() : Optional.of(new double[]{c.seat().getX(), c.seat().getZ()});
        }
        return WarService.player(server, realm).flatMap(r -> MineColoniesBridge.center(r.capital())).map(p -> new double[]{p.getX(), p.getZ()});
    }

    /** Le but d'un ost : la province réclamée (ou la capitale ennemie) pour l'attaquant ; l'ost adverse proche, ou chez soi. */
    static Optional<double[]> target(MinecraftServer server, War w, UUID realm, War.Host self) {
        boolean attacking = w.attacker().equals(realm);
        UUID enemy = attacking ? w.defender() : w.attacker();
        if (attacking) {
            if (w.targetProvince() >= 0) {
                for (AiRealm r : WarService.data(server).world().realms()) {
                    for (AiProvince p : r.provinces()) {
                        if (p.id() == w.targetProvince()) return Optional.of(new double[]{p.seat().getX(), p.seat().getZ()});
                    }
                }
            }
            return seat(server, enemy);
        }
        Optional<double[]> home = seat(server, realm);
        War.Host other = w.hosts().get(enemy);
        if (home.isPresent() && other != null && other.dimension().equals(self.dimension()) && !other.beaten(RealmService.currentDay(server))) {
            double dx = other.x() - home.get()[0], dz = other.z() - home.get()[1];
            if (dx * dx + dz * dz <= 1500.0 * 1500.0) return Optional.of(new double[]{other.x(), other.z()});
        }
        return home;
    }

    /** Toutes les cinq secondes : chaque ost marche, et livre bataille s'il rencontre une armée ennemie en chair et en os. */
    public static void tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.aiHosts.get()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        if (data.wars().all().isEmpty()) return;
        long today = RealmService.currentDay(server);
        double stride = CrusadeConfig.SERVER.hostSpeed.get();
        for (War w : new ArrayList<>(data.wars().all())) {
            for (UUID realm : List.of(w.attacker(), w.defender())) {
                Optional<AiRealm> ai = WarService.ai(server, realm);
                if (ai.isEmpty() || ai.get().landless() || ai.get().capital() == null) {
                    w.hosts().remove(realm);
                    continue;
                }
                AiProvince capital = ai.get().capital();
                War.Host h = w.hosts().get(realm);
                if (h == null || (h.beatenUntil() > 0 && !h.beaten(today))) {
                    h = new War.Host(capital.dimension(), capital.seat().getX(), capital.seat().getZ(), 0);
                    w.hosts().put(realm, h);
                }
                if (h.beaten(today)) continue;
                Optional<double[]> goal = target(server, w, realm, h);
                if (goal.isPresent() && !UNITS.containsKey(key(w.id(), realm))) {
                    double[] next = step(h.x(), h.z(), goal.get()[0], goal.get()[1], stride);
                    h = h.at(next[0], next[1]);
                    w.hosts().put(realm, h);
                }
                battle(server, w, ai.get(), h);
            }
        }
        data.setDirty();
    }

    /** Les soldats de levée des joueurs ennemis de ce royaume dans cette guerre, à moins de {@code range} blocs de l'ost. */
    static List<Mob> enemiesNear(MinecraftServer server, War w, UUID realm, ServerLevel level, War.Host h, int range) {
        List<Mob> out = new ArrayList<>();
        for (Realm r : WarService.data(server).realms()) {
            if (!w.opposes(r.id(), realm)) continue;
            for (Mob m : ArmyService.soldiers(level, r.id())) {
                double dx = m.getX() - h.x(), dz = m.getZ() - h.z();
                if (dx * dx + dz * dz <= (double) range * range) out.add(m);
            }
        }
        return out;
    }

    private static void battle(MinecraftServer server, War w, AiRealm ai, War.Host h) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, h.dimension()));
        if (level == null) return;
        String key = key(w.id(), ai.id());
        Set<UUID> units = UNITS.get(key);
        if (units == null) {
            BlockPos spot = new BlockPos((int) h.x(), 0, (int) h.z());
            if (!level.hasChunkAt(spot) || enemiesNear(server, w, ai.id(), level, h, ENGAGE).isEmpty()) return;
            muster(server, w, ai, h, level);
            return;
        }
        units.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (units.isEmpty()) {
            UNITS.remove(key);
            return;
        }
        // Plus d'ennemi en vue : l'ost se retire du monde, il redevient un pion à sa place.
        if (enemiesNear(server, w, ai.id(), level, h, DISENGAGE).isEmpty()) {
            for (UUID id : units) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
            UNITS.remove(key);
        }
    }

    /** L'ost paraît en vrais soldats, autour de sa place : un soldat pour {@code hommesParSoldat} hommes, plafonné. */
    static int muster(MinecraftServer server, War w, AiRealm ai, War.Host h, ServerLevel level) {
        int count = ArmyService.soldiersFor(men(server, w, ai.id()) - h.lost() * CrusadeConfig.SERVER.menPerSoldier.get(),
                CrusadeConfig.SERVER.menPerSoldier.get(), CrusadeConfig.SERVER.armyCap.get());
        if (count <= 0) return 0;
        Set<UUID> units = new HashSet<>();
        Component name = Component.translatable("crusadecraft.host.soldier", AiNames.realmOf(ai.rank(), ai.place()));
        for (int i = 0; i < count; i++) {
            double[] off = Formation.LIGNE.offset(i, count, 0);
            int x = (int) Math.round(h.x() + off[0]), z = (int) Math.round(h.z() + off[1]);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isLoaded(pos)) continue;
            Mob m = Soldiers.enemy(level, i);
            if (m == null) continue;
            m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
            m.setCustomName(name);
            m.setPersistenceRequired();
            m.addTag(TAG);
            m.addTag(TAG + ":" + ai.id());
            if (level.addFreshEntity(m)) units.add(m.getUUID());
        }
        if (units.isEmpty()) return 0;
        UNITS.put(key(w.id(), ai.id()), units);
        CrusadeCraft.LOGGER.info("CrusadeCraft : l'ost de {} paraît, {} soldat(s), en {}, {}", ai.place(), units.size(), (int) h.x(), (int) h.z());
        for (UUID id : WarService.participants(w)) {
            if (!w.opposes(id, ai.id())) continue;
            WarService.notify(server, id, ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.host.title"),
                    Component.translatable("crusadecraft.toast.host.body", AiNames.realmOf(ai.rank(), ai.place()), units.size()));
        }
        return units.size();
    }

    /** Un soldat d'ost tombe : l'ost s'affaiblit ; le dernier tombé, l'ost est défait et la victoire va à l'ennemi. */
    public static void onDeath(MinecraftServer server, Entity entity) {
        if (!entity.getTags().contains(TAG)) return;
        UUID realm = ArmyService.realmOf(entity, TAG);
        if (realm == null) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        for (War w : data.wars().involving(realm)) {
            String key = key(w.id(), realm);
            Set<UUID> units = UNITS.get(key);
            if (units == null || !units.remove(entity.getUUID())) continue;
            War.Host h = w.hosts().get(realm);
            if (h != null) w.hosts().put(realm, h.withLoss());
            if (units.isEmpty()) {
                UNITS.remove(key);
                beaten(server, w, realm, today);
            }
            data.setDirty();
            return;
        }
    }

    /** L'ost est défait : victoire au score pour l'autre camp, fatigue pour le sien, et il se reforme plus tard chez lui. */
    static void beaten(MinecraftServer server, War w, UUID realm, long today) {
        int side = w.side(realm);
        w.addBattle(-side * VICTORY);
        if (side > 0) w.tire(10, 0);
        else w.tire(0, 10);
        War.Host h = w.hosts().get(realm);
        if (h != null) w.hosts().put(realm, h.beatenUntil(today + REFORM_DAYS));
        Component who = WarService.ai(server, realm).map(a -> AiNames.realmOf(a.rank(), a.place())).orElse(Component.literal("?"));
        w.log(today, "crusadecraft.war.log.host_beaten", -side * VICTORY, who.getString());
        WarService.notifyBoth(server, w, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.host_beaten.title"),
                Component.translatable("crusadecraft.toast.host_beaten.body", who));
    }

    /** Un pion d'ost pour la carte : où il est, d'où il vient, où il va, combien d'hommes. */
    public record Pawn(UUID war, UUID realm, ResourceLocation dimension, int x, int z, int men, boolean beaten, int targetX, int targetZ,
                       boolean hasTarget, boolean inWorld) {}

    /** Les ostes en campagne, pour la carte du mod et pour Dynmap. */
    public static List<Pawn> pawns(MinecraftServer server) {
        List<Pawn> out = new ArrayList<>();
        if (!CrusadeConfig.SERVER.aiHosts.get()) return out;
        long today = RealmService.currentDay(server);
        for (War w : WarService.data(server).wars().all()) {
            for (Map.Entry<UUID, War.Host> e : w.hosts().entrySet()) {
                War.Host h = e.getValue();
                Optional<double[]> goal = h.beaten(today) ? Optional.empty() : target(server, w, e.getKey(), h);
                out.add(new Pawn(w.id(), e.getKey(), h.dimension(), (int) h.x(), (int) h.z(),
                        Math.max(0, men(server, w, e.getKey()) - h.lost() * CrusadeConfig.SERVER.menPerSoldier.get()), h.beaten(today),
                        goal.map(g -> (int) g[0]).orElse(0), goal.map(g -> (int) g[1]).orElse(0), goal.isPresent(),
                        UNITS.containsKey(key(w.id(), e.getKey()))));
            }
        }
        return out;
    }
}
