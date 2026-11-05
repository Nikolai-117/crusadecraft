package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qui vient de se passer dans une ville (P50), au fil de l'eau : qui a vendu quoi à qui, qui part, qui arrive. C'est ce que montre
 * l'onglet « En direct » de son panneau. Rien ne s'y enregistre : c'est une fenêtre sur le présent, pas une archive.
 */
public final class CityJournal {
    public enum Kind { SALE, ROAD, LIFE, NEWS }

    /** Au plus tant de lignes par ville. */
    public static final int KEPT = 48;

    /** Une ligne : l'heure qu'il était, ce qui s'est passé. */
    public record Line(String time, Kind kind, String text) {}

    private static final Map<UUID, Deque<Line>> LOG = new HashMap<>();

    private CityJournal() {}

    public static void onServerStopped() {
        LOG.clear();
    }

    /** L'heure du jeu, à la française : le tick zéro est à six heures du matin. La logique est pure. */
    public static String clock(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        long minutes = (t + 6000) % 24000 * 1440 / 24000;
        return String.format("%02dh%02d", minutes / 60, minutes % 60);
    }

    public static void add(MinecraftServer server, Site site, Kind kind, String text) {
        if (text == null || text.isBlank()) return;
        String shown = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        long time = server.overworld() == null ? 0 : server.overworld().getDayTime();
        Deque<Line> log = LOG.computeIfAbsent(site.id(), k -> new ArrayDeque<>());
        log.addFirst(new Line(clock(time), kind, shown));
        while (log.size() > KEPT) log.removeLast();
    }

    /** Du plus récent au plus ancien. */
    public static List<Line> of(UUID site) {
        Deque<Line> log = LOG.get(site);
        return log == null ? List.of() : new ArrayList<>(log);
    }

    static ListTag save(UUID site) {
        ListTag out = new ListTag();
        for (Line l : of(site)) {
            CompoundTag t = new CompoundTag();
            t.putString("time", l.time());
            t.putString("kind", l.kind().name());
            t.putString("text", l.text());
            out.add(t);
        }
        return out;
    }
}
