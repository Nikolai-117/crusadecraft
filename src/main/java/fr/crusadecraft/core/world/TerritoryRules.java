package fr.crusadecraft.core.world;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Les règles du bornage (P14), sans rien de Minecraft : les cases d'un rectangle ou d'un polygone tracé au bâton, et
 * ce qu'un joueur en survie peut en prendre (son propre royaume seulement, au contact de ses terres, près d'une de
 * ses colonies ou constructions, jamais sur les terres d'un autre, contre de l'or).
 */
public final class TerritoryRules {
    /** Au-delà, un tracé est refusé : c'est déjà un carré de 2 048 blocs de côté. */
    public static final int MAX_CELLS = 16384;

    private TerritoryRules() {}

    /** Les cases d'un rectangle donné par deux coins (en blocs). */
    public static long[] rectangle(int x1, int z1, int x2, int z2) {
        int cx1 = Math.min(TerritoryMap.cellOf(x1), TerritoryMap.cellOf(x2)), cx2 = Math.max(TerritoryMap.cellOf(x1), TerritoryMap.cellOf(x2));
        int cz1 = Math.min(TerritoryMap.cellOf(z1), TerritoryMap.cellOf(z2)), cz2 = Math.max(TerritoryMap.cellOf(z1), TerritoryMap.cellOf(z2));
        long count = (long) (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
        if (count > MAX_CELLS) return null;
        long[] out = new long[(int) count];
        int i = 0;
        for (int x = cx1; x <= cx2; x++) for (int z = cz1; z <= cz2; z++) out[i++] = TerritoryMap.key(x, z);
        return out;
    }

    /**
     * Les cases d'un polygone (sommets en blocs, dans l'ordre du tracé) : celles dont le centre est dedans, et celles
     * que traverse son contour (un tracé fin le long d'une rivière prend bien ses cases). Vide si trop grand.
     */
    public static long[] polygon(List<int[]> points) {
        if (points.size() < 3) return points.size() == 2 ? rectangle(points.get(0)[0], points.get(0)[1], points.get(1)[0], points.get(1)[1]) : null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int cx1 = TerritoryMap.cellOf(minX), cx2 = TerritoryMap.cellOf(maxX), cz1 = TerritoryMap.cellOf(minZ), cz2 = TerritoryMap.cellOf(maxZ);
        if ((long) (cx2 - cx1 + 1) * (cz2 - cz1 + 1) > MAX_CELLS * 4L) return null;
        LongLinkedOpenHashSet cells = new LongLinkedOpenHashSet();
        for (int x = cx1; x <= cx2; x++) {
            for (int z = cz1; z <= cz2; z++) {
                if (inside(x * TerritoryMap.CELL + 8.0, z * TerritoryMap.CELL + 8.0, points)) cells.add(TerritoryMap.key(x, z));
            }
        }
        // Le contour : on le parcourt par pas d'une demi-case.
        for (int i = 0; i < points.size(); i++) {
            int[] a = points.get(i), b = points.get((i + 1) % points.size());
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int steps = Math.max(1, (int) Math.ceil(len / (TerritoryMap.CELL / 2.0)));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                cells.add(TerritoryMap.key(TerritoryMap.cellOf(a[0] + (b[0] - a[0]) * t), TerritoryMap.cellOf(a[1] + (b[1] - a[1]) * t)));
            }
        }
        return cells.size() > MAX_CELLS ? null : cells.toLongArray();
    }

    /** Le point est-il dans le polygone ? (règle du rayon : on compte les bords franchis.) */
    static boolean inside(double x, double z, List<int[]> pts) {
        boolean in = false;
        for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
            double xi = pts.get(i)[0], zi = pts.get(i)[1], xj = pts.get(j)[0], zj = pts.get(j)[1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    /**
     * Le verdict d'un tracé en survie.
     *
     * @param cells les cases qui seront vraiment prises (les nouvelles, au bon endroit)
     * @param taken cases écartées parce qu'elles sont à un autre royaume
     * @param far   cases écartées parce qu'elles sont trop loin de vos colonies et constructions
     * @param owned cases qui étaient déjà à vous
     */
    public record Verdict(String refusal, long[] cells, int taken, int far, int owned, long cost) {
        public boolean ok() {
            return refusal == null;
        }
    }

    /**
     * Ce qu'un royaume peut prendre de ce tracé en survie.
     *
     * @param owner       qui tient chaque case aujourd'hui (vide : personne)
     * @param anchors     les centres de ses colonies et constructions (x, z)
     * @param maxDistance distance maximale d'une case à l'un de ces centres, en blocs
     */
    public static Verdict survival(UUID realm, long[] stroke, LongFunction<Optional<UUID>> owner, List<double[]> anchors, int maxDistance,
                                   int costPerCell, int maxCells) {
        if (stroke == null) return new Verdict("crusadecraft.bornage.too_big", new long[0], 0, 0, 0, 0);
        LongOpenHashSet chosen = new LongOpenHashSet();
        int taken = 0, far = 0, owned = 0;
        double max2 = (double) maxDistance * maxDistance;
        for (long key : stroke) {
            Optional<UUID> o = owner.apply(key);
            if (o.isPresent() && o.get().equals(realm)) {
                owned++;
                continue;
            }
            if (o.isPresent()) {
                taken++;
                continue;
            }
            double cx = TerritoryMap.cellX(key) * TerritoryMap.CELL + 8.0, cz = TerritoryMap.cellZ(key) * TerritoryMap.CELL + 8.0;
            boolean near = false;
            for (double[] a : anchors) {
                if ((cx - a[0]) * (cx - a[0]) + (cz - a[1]) * (cz - a[1]) <= max2) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                far++;
                continue;
            }
            chosen.add(key);
        }
        if (chosen.isEmpty()) {
            String why = owned > 0 && taken == 0 && far == 0 ? "crusadecraft.bornage.already" : taken > 0 ? "crusadecraft.bornage.taken"
                    : "crusadecraft.bornage.far";
            return new Verdict(why, new long[0], taken, far, owned, 0);
        }
        if (chosen.size() > maxCells) return new Verdict("crusadecraft.bornage.too_many", new long[0], taken, far, owned, 0);
        // Les nouvelles terres touchent les vôtres : au moins une case nouvelle borde une case déjà à vous.
        boolean touches = false;
        for (long key : chosen) {
            int x = TerritoryMap.cellX(key), z = TerritoryMap.cellZ(key);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long n = TerritoryMap.key(x + d[0], z + d[1]);
                if (!chosen.contains(n) && owner.apply(n).map(realm::equals).orElse(false)) {
                    touches = true;
                    break;
                }
            }
            if (touches) break;
        }
        if (!touches) return new Verdict("crusadecraft.bornage.detached", new long[0], taken, far, owned, 0);
        return new Verdict(null, chosen.toLongArray(), taken, far, owned, (long) chosen.size() * costPerCell);
    }
}
