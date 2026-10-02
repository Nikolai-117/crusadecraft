package fr.crusadecraft.client.map;

import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.client.Minecraft;

/** La dernière carte reçue du serveur, partagée par l'écran de la carte et la Table de guerre. */
public final class ClientMapData {
    private static MapView view;
    private static int version;
    private static long lastRequest;

    private ClientMapData() {}

    public static MapView view() {
        return view;
    }

    /** Augmente à chaque nouvelle carte : les textures savent ainsi quand se recalculer. */
    public static int version() {
        return version;
    }

    public static void set(MapView v) {
        view = v;
        version++;
    }

    public static void clear() {
        view = null;
        version++;
        lastRequest = 0;
    }

    /** Redemande la carte si la dernière demande date de plus de {@code maxAgeMs}. */
    public static void refreshIfStale(long maxAgeMs) {
        long now = System.currentTimeMillis();
        if (now - lastRequest < maxAgeMs || Minecraft.getInstance().getConnection() == null) return;
        lastRequest = now;
        CrusadeNetwork.toServer(new WorldPackets.RequestMap(false));
    }
}
