package fr.crusadecraft.compat.dynmap;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

/**
 * La carte web Dynmap (P17), vue du reste du mod : sans aucune classe de Dynmap, pour que tout charge sans lui. Si
 * Dynmap est absent, rien ne se passe ; s'il change au point de casser, on le dit une fois dans le journal et on
 * s'arrête, sans jamais faire planter le serveur.
 */
public final class DynmapBridge {
    private static boolean registered;
    private static boolean broken;

    private DynmapBridge() {}

    /** Au démarrage du mod : on s'inscrit auprès de Dynmap, qui nous préviendra quand sa carte sera prête. */
    public static void init() {
        if (!ModList.get().isLoaded("dynmap")) return;
        try {
            DynmapCompat.register();
            registered = true;
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /** Chaque tick : à l'intervalle réglé, la carte web est redessinée si quelque chose a changé. */
    public static void tick(MinecraftServer server, int tick) {
        if (!registered || broken || !CrusadeConfig.SERVER.webMap.get()) return;
        if (tick % (CrusadeConfig.SERVER.webMapSeconds.get() * 20) != 40) return;
        try {
            if (DynmapCompat.ready()) DynmapCompat.update(server);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    public static void onServerStopped() {
        if (registered && !broken) DynmapCompat.reset();
    }

    /** La carte web est-elle branchée (Dynmap présent et prêt) ? */
    public static boolean ready() {
        try {
            return registered && !broken && DynmapCompat.ready();
        } catch (LinkageError e) {
            return false;
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        CrusadeCraft.LOGGER.error("CrusadeCraft : la carte web Dynmap est coupée (version de Dynmap incompatible ?)", e);
    }
}
