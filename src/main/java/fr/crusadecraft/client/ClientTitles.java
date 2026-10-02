package fr.crusadecraft.client;

import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ce que le client sait des dignitaires : leur titre, pour l'afficher au-dessus de leur tête. */
public final class ClientTitles {
    private static final Map<CitizenRef, Component> TITLES = new HashMap<>();
    private static CitizenRef bypass;

    private ClientTitles() {}

    public static void replace(List<SyncTitlesPacket.Entry> entries) {
        TITLES.clear();
        for (SyncTitlesPacket.Entry e : entries) TITLES.put(e.ref(), e.title());
    }

    public static Component title(CitizenRef ref) {
        return TITLES.get(ref);
    }

    public static boolean isDignitary(CitizenRef ref) {
        return TITLES.containsKey(ref);
    }

    /** Laisse passer le prochain clic sur ce citoyen jusqu'à MineColonies (bouton « Fenêtre MineColonies »). */
    public static void allowNextClick(CitizenRef ref) {
        bypass = ref;
    }

    public static boolean consumeBypass(CitizenRef ref) {
        if (ref.equals(bypass)) {
            bypass = null;
            return true;
        }
        return false;
    }

    public static void clear() {
        TITLES.clear();
        bypass = null;
    }
}
