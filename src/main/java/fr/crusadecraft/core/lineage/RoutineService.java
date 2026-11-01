package fr.crusadecraft.core.lineage;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;
import java.util.UUID;

/**
 * La journée des gens du registre qui vivent dans le monde (P20) : le jour, ils se rendent à la cour de leur royaume
 * (le centre de la capitale) ; le soir, ils rentrent là où ils sont apparus ; quand leur royaume est en guerre, ils
 * se réfugient à la cour, jour et nuit. On ne fait que leur donner un but de marche, comme les villageois en ont ;
 * leur propre intelligence (celle de Minecraft Comes Alive) fait le reste, et peut parfois l'emporter.
 *
 * <p>P26 : pendant un banquet et le jour des funérailles, tout le monde se rassemble à la cour, jour et nuit ; on y
 * voit des notes de musique ou de la fumée, et de temps en temps quelqu'un dit une réplique aux joueurs tout proches.
 */
public final class RoutineService {
    /** Assez près : on ne les pousse plus. */
    static final int NEAR = 6;
    /** Plus loin, on ne les fait pas traverser le monde : ils restent où ils sont. */
    static final int FAR = 512;

    private RoutineService() {}

    /** Où doit être cette personne à cette heure : la cour ou chez elle. */
    public enum Goal { COURT, HOME }

    /** Assez près de la cour pour en être (particules, répliques). */
    static final int AT_COURT = 12;
    /** Les joueurs qui entendent une réplique. */
    static final int HEARING = 12;
    /** Au plus une réplique par personne tous les tant de ticks. */
    static final int SPEAK_EVERY = 900;
    private static final java.util.Map<UUID, Long> LAST_SPOKE = new java.util.HashMap<>();
    private static final java.util.Random RANDOM = new java.util.Random();

    public static void onServerStopped() {
        LAST_SPOKE.clear();
    }

    /** Le but de la journée, en logique pure : la guerre l'emporte, sinon le jour (de 1 000 à 11 000) la cour. */
    public static Goal goal(long dayTime, boolean atWar) {
        return goal(dayTime, atWar, false);
    }

    /** Avec un rassemblement (banquet, funérailles) : la cour, à toute heure. */
    public static Goal goal(long dayTime, boolean atWar, boolean gathering) {
        if (atWar || gathering) return Goal.COURT;
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 1000 && t < 11000 ? Goal.COURT : Goal.HOME;
    }

    /** La cour d'un royaume : le centre de la capitale d'un joueur, le siège de la capitale d'un royaume IA. */
    static Optional<BlockPos> court(CrusadeSavedData data, UUID realm) {
        Optional<Realm> human = data.realm(realm);
        if (human.isPresent()) return MineColoniesBridge.center(human.get().capital());
        Optional<AiRealm> ai = data.world().realm(realm);
        if (ai.isPresent() && ai.get().capital() != null) return Optional.of(ai.get().capital().seat());
        return Optional.empty();
    }

    /** Toutes les cinq secondes : chacun reçoit son but s'il en est loin. Renvoie le nombre de personnes mises en marche. */
    public static int tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.routines.get()) return 0;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int moved = 0;
        for (Person p : data.lineage().persons()) {
            if (!p.alive() || !p.inWorld() || p.realm() == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim()));
            if (level == null) continue;
            Entity e = level.getEntity(p.id());
            if (!(e instanceof PathfinderMob mob) || !mob.isAlive()) continue;
            boolean atWar = data.wars().atWar(p.realm());
            boolean feast = fr.crusadecraft.core.court.FeastService.feasting(server, p.realm());
            boolean funeral = !feast && fr.crusadecraft.core.court.FeastService.funeral(server, p.realm());
            Goal goal = goal(level.getDayTime(), atWar, feast || funeral);
            Optional<BlockPos> target = goal == Goal.COURT ? court(data, p.realm()) : Optional.of(p.worldPos());
            if (target.isEmpty()) continue;
            if (walk(mob, target.get())) moved++;
            boolean atCourt = goal == Goal.COURT && mob.blockPosition().distSqr(target.get()) <= AT_COURT * AT_COURT;
            if (atCourt && (feast || funeral)) {
                level.sendParticles(feast ? net.minecraft.core.particles.ParticleTypes.NOTE : net.minecraft.core.particles.ParticleTypes.SMOKE,
                        mob.getX(), mob.getY() + mob.getBbHeight() + 0.4, mob.getZ(), feast ? 2 : 4, 0.3, 0.2, 0.3, feast ? 1.0 : 0.01);
            }
            Repliques.Moment moment = feast ? Repliques.Moment.FETE : funeral ? Repliques.Moment.DEUIL : atWar ? Repliques.Moment.GUERRE
                    : atCourt ? Repliques.Moment.COUR : null;
            if (moment != null) speak(level, mob, p, data.lineage(), moment);
        }
        return moved;
    }

    /**
     * De temps en temps, une réplique de cette personne aux joueurs tout proches : jamais plus d'une par personne
     * toutes les {@link #SPEAK_EVERY} ticks, et pas à chaque passage (une chance sur six).
     */
    static void speak(ServerLevel level, PathfinderMob mob, Person p, LineageState state, Repliques.Moment moment) {
        if (!CrusadeConfig.SERVER.repliques.get() || RANDOM.nextInt(6) != 0) return;
        long now = level.getGameTime();
        Long last = LAST_SPOKE.get(p.id());
        if (last != null && now - last < SPEAK_EVERY) return;
        java.util.List<net.minecraft.server.level.ServerPlayer> hearers = level.getPlayers(pl -> pl.distanceToSqr(mob) <= HEARING * HEARING);
        if (hearers.isEmpty()) return;
        net.minecraft.network.chat.Component line = Repliques.pick(Repliques.entries(), moment, p.role(), p.traits(), RANDOM);
        if (line == null) return;
        LAST_SPOKE.put(p.id(), now);
        net.minecraft.network.chat.Component said = net.minecraft.network.chat.Component.translatable("crusadecraft.replique.said",
                state.fullName(p), line).withStyle(net.minecraft.ChatFormatting.GRAY);
        for (net.minecraft.server.level.ServerPlayer pl : hearers) pl.sendSystemMessage(said);
    }

    /** Un but de marche, comme un villageois en reçoit ; sinon un simple chemin. Rien si on est déjà là, ou trop loin. */
    static boolean walk(PathfinderMob mob, BlockPos target) {
        double d = mob.blockPosition().distSqr(target);
        if (d <= NEAR * NEAR || d > (double) FAR * FAR) return false;
        if (mob instanceof Villager villager && villager.getBrain().checkMemory(MemoryModuleType.WALK_TARGET,
                net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(target), 0.6f, NEAR - 2));
            return true;
        }
        return mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
    }
}
