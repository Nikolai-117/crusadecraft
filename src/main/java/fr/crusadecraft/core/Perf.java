package fr.crusadecraft.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le temps que prend chaque système de CrusadeCraft sur le serveur (P24), pour {@code /crusade perf} : chaque appel
 * périodique est chronométré, et l'on garde le total, le nombre d'appels et le plus long depuis la dernière remise à
 * zéro. Le chronomètre coûte deux lectures d'horloge par appel : rien à côté de ce qu'il mesure.
 */
public final class Perf {
    /** Ce qu'un système a coûté : en tout, en moyenne par tick du serveur, au pire. */
    public record Line(String system, long totalNanos, long calls, long maxNanos, long ticks) {
        public double msPerTick() {
            return ticks == 0 ? 0 : totalNanos / 1e6 / ticks;
        }

        public double maxMs() {
            return maxNanos / 1e6;
        }
    }

    private static final Map<String, long[]> STATS = new LinkedHashMap<>();
    private static long ticks;

    private Perf() {}

    /** Chronomètre un appel. */
    public static void run(String system, Runnable task) {
        long t0 = System.nanoTime();
        try {
            task.run();
        } finally {
            record(system, System.nanoTime() - t0);
        }
    }

    static synchronized void record(String system, long nanos) {
        long[] s = STATS.computeIfAbsent(system, k -> new long[3]);
        s[0] += nanos;
        s[1]++;
        s[2] = Math.max(s[2], nanos);
    }

    /** Un tick du serveur de plus dans la mesure. */
    public static synchronized void tick() {
        ticks++;
    }

    public static synchronized long ticks() {
        return ticks;
    }

    /** Les systèmes, du plus coûteux au moins coûteux. */
    public static synchronized List<Line> report() {
        List<Line> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : STATS.entrySet()) out.add(new Line(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], ticks));
        out.sort(Comparator.comparingLong(Line::totalNanos).reversed());
        return out;
    }

    public static synchronized void reset() {
        STATS.clear();
        ticks = 0;
    }
}
