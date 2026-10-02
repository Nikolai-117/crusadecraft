package fr.crusadecraft.core.council;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les ordres de déplacement en cours : qui doit aller où, et pourquoi.
 *
 * <p>Le cœur du mod décide (heures de réunion, de bureau, audiences) ; l'IA ajoutée aux citoyens dans
 * {@code compat.minecolonies.DutyAI} les exécute. Rien de tout cela n'est enregistré : un ordre ne vit
 * que le temps de la session, et retirer le mod ne laisse aucune trace chez MineColonies.
 */
public final class DutyOrders {

    public enum Kind {
        /** Réunion du conseil : rejoindre un siège (ou la table). */
        MEETING,
        /** Heures de bureau du maire. */
        OFFICE,
        /** Venir présenter une requête au souverain. */
        AUDIENCE
    }

    /**
     * @param target   où aller (pour une audience, mis à jour avec la position du souverain)
     * @param seat     siège où s'asseoir à l'arrivée, ou {@code null}
     * @param lookAt   ce qu'il regarde une fois arrivé
     * @param player   le souverain visé par une audience, ou {@code null}
     * @param untilTick fin de l'ordre (horloge du serveur)
     */
    public record Order(Kind kind, ResourceLocation dimension, BlockPos target, BlockPos seat, BlockPos lookAt,
                        UUID player, UUID requestId, long untilTick) {}

    private static final Map<CitizenRef, Order> ORDERS = new ConcurrentHashMap<>();

    private DutyOrders() {}

    public static Order get(CitizenRef ref) {
        return ORDERS.get(ref);
    }

    public static void give(CitizenRef ref, Order order) {
        Order current = ORDERS.get(ref);
        // Une audience prime sur la réunion ou le bureau.
        if (current != null && current.kind() == Kind.AUDIENCE && order.kind() != Kind.AUDIENCE) return;
        ORDERS.put(ref, order);
    }

    public static void cancel(CitizenRef ref) {
        ORDERS.remove(ref);
    }

    /** Retire tous les ordres d'un genre, sauf pour les citoyens encore concernés. */
    public static void cancelAll(Kind kind) {
        ORDERS.values().removeIf(o -> o.kind() == kind);
    }

    public static Map<CitizenRef, Order> all() {
        return ORDERS;
    }

    public static void clear() {
        ORDERS.clear();
    }
}
