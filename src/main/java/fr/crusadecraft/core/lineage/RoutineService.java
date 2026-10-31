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
 */
public final class RoutineService {
    /** Assez près : on ne les pousse plus. */
    static final int NEAR = 6;
    /** Plus loin, on ne les fait pas traverser le monde : ils restent où ils sont. */
    static final int FAR = 512;

    private RoutineService() {}

    /** Où doit être cette personne à cette heure : la cour ou chez elle. */
    public enum Goal { COURT, HOME }

    /** Le but de la journée, en logique pure : la guerre l'emporte, sinon le jour (de 1 000 à 11 000) la cour. */
    public static Goal goal(long dayTime, boolean atWar) {
        if (atWar) return Goal.COURT;
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
            Goal goal = goal(level.getDayTime(), data.wars().atWar(p.realm()));
            Optional<BlockPos> target = goal == Goal.COURT ? court(data, p.realm()) : Optional.of(p.worldPos());
            if (target.isEmpty()) continue;
            if (walk(mob, target.get())) moved++;
        }
        return moved;
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
