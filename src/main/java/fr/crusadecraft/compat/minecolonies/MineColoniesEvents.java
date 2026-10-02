package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.eventbus.events.ColonyManagerLoadedModEvent;
import com.minecolonies.api.eventbus.events.ColonyManagerUnloadedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyNameChangedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyPlayerRankChangedModEvent;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.RealmService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Écoute le bus d'événements de MineColonies.
 *
 * <p>{@link ColonyManagerLoadedModEvent} est le signal « tout est chargé » qui manquait à War 'N Taxes :
 * sa réparation automatique tournait avant, voyait des colonies à moitié chargées sans propriétaire,
 * et leur écrivait un faux propriétaire. Ici, rien ne touche aux colonies avant ce signal, et même
 * après, la réconciliation ne modifie que nos propres données.
 */
public final class MineColoniesEvents {

    private MineColoniesEvents() {}

    public static void register() {
        var bus = IMinecoloniesAPI.getInstance().getEventBus();
        bus.subscribe(ColonyManagerLoadedModEvent.class, e -> {
            MineColoniesBridge.markReady(true);
            CrusadeCraft.LOGGER.info("CrusadeCraft : colonies MineColonies chargées");
            withServer(RealmService::reconcileAll);
        });
        bus.subscribe(ColonyManagerUnloadedModEvent.class, e -> MineColoniesBridge.markReady(false));
        // MineColonies publie la suppression AVANT de retirer la colonie : on la traite explicitement.
        bus.subscribe(ColonyDeletedModEvent.class, e -> {
            if (e.getColony() == null) return;
            ColonyRef ref = refOf(e.getColony());
            withServer(server -> RealmService.onColonyDeleted(server, ref));
        });
        bus.subscribe(ColonyPlayerRankChangedModEvent.class, e -> onColonyChanged(e.getColony()));
        bus.subscribe(ColonyNameChangedModEvent.class, e -> onColonyChanged(e.getColony()));
    }

    /** Filet de sécurité : si le signal n'est jamais venu, on considère les colonies chargées au démarrage. */
    public static void assumeReadyIfSilent() {
        if (!MineColoniesBridge.isReady() && !MineColoniesBridge.isBroken()) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : MineColonies n'a pas signalé la fin de son chargement ; "
                    + "on le suppose terminé une fois le serveur démarré.");
            MineColoniesBridge.markReady(true);
        }
    }

    public static void onServerStopped() {
        MineColoniesBridge.markReady(false);
    }

    private static void onColonyChanged(IColony colony) {
        if (colony == null) return;
        ColonyRef ref = refOf(colony);
        withServer(server -> RealmService.reconcileColony(server, ref));
    }

    private static ColonyRef refOf(IColony colony) {
        return new ColonyRef(colony.getDimension().location(), colony.getID());
    }

    /**
     * Exécute l'action au tick suivant, sur le fil principal. Pas {@code server.execute()} : depuis le fil
     * serveur, elle s'exécuterait tout de suite, avant que MineColonies ait fini sa propre modification.
     */
    private static void withServer(java.util.function.Consumer<MinecraftServer> action) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.tell(new TickTask(server.getTickCount() + 1, () -> action.accept(server)));
    }
}
