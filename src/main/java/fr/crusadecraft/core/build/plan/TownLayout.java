package fr.crusadecraft.core.build.plan;

import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Le plan d'une ville (P34), en logique pure : l'hôtel de ville au milieu, la place devant son entrée, puis chaque
 * bâtiment sur des anneaux de plus en plus larges, l'entrée tournée vers le centre, jamais à moins d'une rue d'un
 * autre. Les rues : un carré autour du centre, et de chaque porte un chemin jusqu'à lui.
 */
public final class TownLayout {
    /** Une emprise au sol, entrée au sud (+z), comme les plans. */
    public record Box(int w, int d) {}

    /** Où va un bâtiment : son milieu relatif au centre de la ville, et sa rotation (entrée vers le centre). */
    public record Spot(int x, int z, Rotation rotation) {}

    /** Un rectangle de blocs, bornes comprises. */
    record Rect(int x0, int z0, int x1, int z1) {
        boolean overlaps(Rect o, int gap) {
            return x0 - gap <= o.x1 && o.x0 <= x1 + gap && z0 - gap <= o.z1 && o.z0 <= z1 + gap;
        }

        boolean contains(int x, int z) {
            return x0 <= x && x <= x1 && z0 <= z && z <= z1;
        }
    }

    /** Largeur d'une rue entre deux bâtiments. */
    public static final int STREET = 4;

    private TownLayout() {}

    /** Le rectangle occupé par une emprise posée là, tournée ainsi (bornes exactes des plans). */
    static Rect rect(Box b, Spot s) {
        int minX = -(b.w() / 2), maxX = b.w() - 1 - b.w() / 2, minZ = -(b.d() / 2), maxZ = b.d() - 1 - b.d() / 2;
        int[] a = PlanBuilder.turn(minX, minZ, s.rotation()), c = PlanBuilder.turn(maxX, maxZ, s.rotation());
        return new Rect(s.x() + Math.min(a[0], c[0]), s.z() + Math.min(a[1], c[1]), s.x() + Math.max(a[0], c[0]), s.z() + Math.max(a[1], c[1]));
    }

    /** L'entrée tournée vers le centre, vue depuis un bâtiment posé en (x, z). */
    static Rotation facingCenter(int x, int z) {
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
        return z > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
    }

    /**
     * Range les bâtiments. Le premier est le centre (l'hôtel de ville), {@code plaza} (peut être {@code null}) se
     * pose devant lui ; les autres suivent dans l'ordre donné. Un bâtiment qui ne trouve pas de place est laissé
     * de côté ({@code null} à sa place dans la liste rendue).
     */
    public static List<Spot> arrange(Box center, Box plaza, List<Box> others, long seed) {
        List<Spot> out = new ArrayList<>();
        List<Rect> taken = new ArrayList<>();
        Spot c = new Spot(0, 0, Rotation.NONE);
        out.add(c);
        taken.add(rect(center, c));
        if (plaza != null) {
            Spot p = new Spot(0, center.d() - 1 - center.d() / 2 + STREET + plaza.d() / 2 + 1, Rotation.NONE);
            out.add(p);
            taken.add(rect(plaza, p));
        }
        Random random = new Random(seed);
        double turn = random.nextDouble() * Math.PI * 2;
        int core = Math.max(center.w(), center.d()) / 2 + STREET;
        for (Box b : others) {
            Spot found = null;
            int half = Math.max(b.w(), b.d()) / 2;
            for (int r = core + half; r < core + half + 400 && found == null; r += 3) {
                int n = Math.max(8, (int) (2 * Math.PI * r / (Math.max(b.w(), b.d()) + STREET)));
                for (int k = 0; k < n && found == null; k++) {
                    double a = turn + k * 2 * Math.PI / n;
                    int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                    Spot s = new Spot(x, z, facingCenter(x, z));
                    Rect rr = rect(b, s);
                    boolean free = true;
                    for (Rect t : taken) if (rr.overlaps(t, STREET)) {
                        free = false;
                        break;
                    }
                    if (free) {
                        found = s;
                        taken.add(rr);
                    }
                }
            }
            out.add(found);
            turn += 2.39996;
        }
        return out;
    }

    /** Le côté d'un carré qui couvre toute la ville, rue comprise. */
    public static int extent(List<Box> boxes, List<Spot> spots) {
        int m = 0;
        for (int i = 0; i < boxes.size(); i++) {
            if (spots.get(i) == null) continue;
            Rect r = rect(boxes.get(i), spots.get(i));
            m = Math.max(m, Math.max(Math.max(Math.abs(r.x0()), Math.abs(r.x1())), Math.max(Math.abs(r.z0()), Math.abs(r.z1()))));
        }
        return 2 * (m + STREET) + 1;
    }

    /**
     * Les rues, cases (x, z) relatives au centre : le carré autour de l'hôtel de ville et de sa place, et de chaque
     * porte un chemin de deux de large jusqu'à lui. Les cases sous un bâtiment n'en sont pas.
     */
    public static List<int[]> streets(List<Box> boxes, List<Spot> spots) {
        List<Rect> rects = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) rects.add(spots.get(i) == null ? null : rect(boxes.get(i), spots.get(i)));
        // Le carré du centre : autour de l'hôtel de ville et de la place.
        Rect core = rects.get(0);
        int cx0 = core.x0(), cz0 = core.z0(), cx1 = core.x1(), cz1 = core.z1();
        int center = boxes.size() > 1 && spots.size() > 1 && spots.get(1) != null && spots.get(1).x() == 0 && spots.get(1).rotation() == Rotation.NONE
                && spots.get(1).z() > 0 ? 2 : 1;
        for (int i = 1; i < center; i++) {
            Rect r = rects.get(i);
            cx0 = Math.min(cx0, r.x0());
            cz0 = Math.min(cz0, r.z0());
            cx1 = Math.max(cx1, r.x1());
            cz1 = Math.max(cz1, r.z1());
        }
        int x0 = cx0 - 2, z0 = cz0 - 2, x1 = cx1 + 2, z1 = cz1 + 2;
        Set<Long> cells = new LinkedHashSet<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            add(cells, x, z0 - 1);
            add(cells, x, z0);
            add(cells, x, z1);
            add(cells, x, z1 + 1);
        }
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            add(cells, x0 - 1, z);
            add(cells, x0, z);
            add(cells, x1, z);
            add(cells, x1 + 1, z);
        }
        // De chaque porte au carré : droit devant, puis à angle droit.
        for (int i = center; i < boxes.size(); i++) {
            Spot s = spots.get(i);
            if (s == null) continue;
            Box b = boxes.get(i);
            int[] door = PlanBuilder.turn(0, b.d() - 1 - b.d() / 2 + 1, s.rotation());
            int[] f = PlanBuilder.turn(0, 1, s.rotation());
            int x = s.x() + door[0], z = s.z() + door[1];
            boolean alongX = f[0] != 0;
            int guard = 0;
            // Droit devant, jusqu'à la hauteur du carré.
            while (guard++ < 600 && (alongX ? (x < x0 || x > x1) : (z < z0 || z > z1))) {
                add(cells, x, z);
                add(cells, x + (alongX ? 0 : 1), z + (alongX ? 1 : 0));
                x += f[0];
                z += f[1];
            }
            // Puis à angle droit, jusqu'au carré.
            while (guard++ < 1200 && (alongX ? (z < z0 || z > z1) : (x < x0 || x > x1))) {
                add(cells, x, z);
                add(cells, x + (alongX ? 1 : 0), z + (alongX ? 0 : 1));
                if (alongX) z += z < z0 ? 1 : -1;
                else x += x < x0 ? 1 : -1;
            }
        }
        List<int[]> out = new ArrayList<>();
        for (long key : cells) {
            int x = (int) (key >> 32), z = (int) key;
            boolean under = false;
            for (Rect r : rects) if (r != null && r.contains(x, z)) {
                under = true;
                break;
            }
            if (!under) out.add(new int[]{x, z});
        }
        return out;
    }

    private static void add(Set<Long> cells, int x, int z) {
        cells.add(((long) x << 32) | (z & 0xFFFFFFFFL));
    }
}
