package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.council.DutyOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Un état « service » ajouté à l'IA des citoyens MineColonies : aller au conseil, au bureau, ou venir
 * présenter une requête au souverain.
 *
 * <p>Il s'insère dans la machine à états du citoyen par ses propres transitions, sans rien remplacer :
 * <ul>
 *   <li>il ne démarre que si le citoyen est oisif ou au travail : le sommeil, les repas, la fuite, la
 *       maladie et le deuil passent toujours avant ;</li>
 *   <li>en le quittant, il rend la main à l'état « oisif », d'où MineColonies reprend son cours ;</li>
 *   <li>rien n'est sauvegardé : les transitions sont rajoutées à chaque chargement de l'entité, et
 *       retirer CrusadeCraft ne laisse aucune trace.</li>
 * </ul>
 *
 * <p>La moindre erreur coupe l'état pour la session : le citoyen retrouve alors son comportement normal.
 */
public final class DutyAI {

    /** L'état ajouté à la machine de MineColonies. */
    public enum DutyState implements IState { DUTY }

    private static final Set<Entity> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Entity, BlockPos> LAST_TARGET = new WeakHashMap<>();
    private static volatile boolean disabled;
    /** Trace de mise au point (serveur de tests seulement) : -Dcrusadecraft.debugai=true. */
    private static final boolean TRACE = Boolean.getBoolean("crusadecraft.debugai");
    private static BiConsumer<CitizenRef, DutyOrders.Order> arrivalListener = (r, o) -> {};

    private DutyAI() {}

    /** Appelé à l'arrivée à destination (attendance d'une réunion, début d'une audience). */
    public static void onArrival(BiConsumer<CitizenRef, DutyOrders.Order> listener) {
        arrivalListener = listener;
    }

    public static void install(Entity entity) {
        if (disabled || !(entity instanceof EntityCitizen citizen) || entity.level().isClientSide) return;
        if (TRACE) CrusadeCraft.LOGGER.info("[debugai] installation sur {}", entity.getId());
        synchronized (INSTALLED) {
            if (!INSTALLED.add(entity)) return;
        }
        try {
            ITickRateStateMachine<IState> ai = citizen.getCitizenAI();
            ai.addTransition(new AIEventTarget<IState>(AIBlockingEventType.EVENT, () -> shouldStart(citizen), (IState) DutyState.DUTY, 20));
            ai.addTransition(new AITarget<>(DutyState.DUTY, () -> true, () -> tick(citizen), 10));
        } catch (LinkageError | RuntimeException e) {
            disable(e);
        }
    }

    private static boolean shouldStart(EntityCitizen citizen) {
        if (disabled) return false;
        try {
            IState state = citizen.getCitizenAI().getState();
            if (TRACE && order(citizen) != null) {
                CrusadeCraft.LOGGER.info("[debugai] {} état={} ordre={}", citizen.getId(), state, order(citizen).kind());
            }
            if (state != CitizenAIState.IDLE && state != CitizenAIState.WORK && state != CitizenAIState.WORKING) return false;
            DutyOrders.Order order = order(citizen);
            return order != null && order.untilTick() > citizen.level().getGameTime();
        } catch (RuntimeException e) {
            disable(e);
            return false;
        }
    }

    private static DutyOrders.Order order(EntityCitizen citizen) {
        Optional<CitizenRef> ref = MineColoniesBridge.refOf(citizen);
        if (ref.isEmpty()) return null;
        DutyOrders.Order order = DutyOrders.get(ref.get());
        if (order == null || !order.dimension().equals(citizen.level().dimension().location())) return null;
        return order;
    }

    private static IState tick(EntityCitizen citizen) {
        try {
            DutyOrders.Order order = order(citizen);
            long now = citizen.level().getGameTime();
            if (order == null || order.untilTick() <= now) {
                stand(citizen);
                return CitizenAIState.IDLE;
            }
            BlockPos target = order.target();
            if (order.kind() == DutyOrders.Kind.AUDIENCE) {
                ServerPlayer player = citizen.getServer() == null ? null : citizen.getServer().getPlayerList().getPlayer(order.player());
                if (player == null || player.level() != citizen.level()) {
                    stand(citizen);
                    return CitizenAIState.IDLE;
                }
                target = player.blockPosition();
            }
            if (citizen.isPassenger()) {
                lookAt(citizen, order.lookAt());
                return DutyState.DUTY;
            }
            double reach = order.kind() == DutyOrders.Kind.AUDIENCE ? 3.0 : order.seat() != null ? 1.6 : 2.5;
            Vec3 goal = Vec3.atBottomCenterOf(target);
            if (citizen.distanceToSqr(goal) > reach * reach) {
                if (TRACE) CrusadeCraft.LOGGER.info("[debugai] {} marche vers {} (distance² {}, navigation finie {})",
                        citizen.getId(), target, Math.round(citizen.distanceToSqr(goal)), citizen.getNavigation().isDone());
                BlockPos last = LAST_TARGET.get(citizen);
                if (citizen.getNavigation().isDone() || last == null || last.distSqr(target) > 4) {
                    citizen.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
                    LAST_TARGET.put(citizen, target);
                }
                return DutyState.DUTY;
            }
            citizen.getNavigation().stop();
            if (order.seat() != null && !SittingEntity.isSittingPosOccupied(order.seat(), citizen.level())) {
                SittingEntity.sitDown(order.seat(), citizen, (int) Math.max(40, order.untilTick() - now));
            }
            lookAt(citizen, order.kind() == DutyOrders.Kind.AUDIENCE ? target : order.lookAt());
            MineColoniesBridge.refOf(citizen).ifPresent(ref -> arrivalListener.accept(ref, order));
            return DutyState.DUTY;
        } catch (RuntimeException e) {
            disable(e);
            return CitizenAIState.IDLE;
        }
    }

    private static void lookAt(EntityCitizen citizen, BlockPos pos) {
        if (pos != null) citizen.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    private static void stand(EntityCitizen citizen) {
        LAST_TARGET.remove(citizen);
        if (citizen.isPassenger()) citizen.stopRiding();
    }

    private static void disable(Throwable t) {
        if (!disabled) {
            disabled = true;
            CrusadeCraft.LOGGER.error("CrusadeCraft : l'état « service » des citoyens est coupé pour la session "
                    + "(incompatibilité avec cette version de MineColonies). Les citoyens gardent leur comportement normal.", t);
        }
    }

    public static boolean isDisabled() {
        return disabled;
    }
}
