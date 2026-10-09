package fr.crusadecraft.core.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * La géométrie des terres pour une carte web (P17), en logique pure : des chunks d'un royaume, on tire des rectangles
 * qui les couvrent exactement (le remplissage) et les segments droits de leur bord (la frontière), comme les claims
 * de Factions sur Dynmap. Les zones à trou ou en morceaux ne posent aucun problème : rien n'est un polygone à trous.
 */
public final class WebMapGeometry {
    private WebMapGeometry() {}

    /** Un rectangle de chunks : de (x0, z0) à (x1, z1) inclus. */
    public record Rect(int x0, int z0, int x1, int z1) {
        public int chunks() {
            return (x1 - x0 + 1) * (z1 - z0 + 1);
        }
    }

    /** Un segment de frontière, en blocs, horizontal ou vertical. */
    public record Segment(int x0, int z0, int x1, int z1) {}

    /**
     * Des rectangles disjoints qui couvrent exactement ces chunks : chaque rangée s'étend vers l'est tant qu'elle peut,
     * puis vers le sud tant que les rangées suivantes sont pleines sur la même largeur.
     */
    public static List<Rect> rectangles(LongSet chunks) {
        long[] keys = chunks.toLongArray();
        // Par rangée (z) puis par colonne (x) : on balaie du nord-ouest au sud-est.
        Long[] boxed = Arrays.stream(keys).boxed().toArray(Long[]::new);
        Arrays.sort(boxed, (a, b) -> {
            int za = TerritoryMap.cellZ(a), zb = TerritoryMap.cellZ(b);
            if (za != zb) return Integer.compare(za, zb);
            return Integer.compare(TerritoryMap.cellX(a), TerritoryMap.cellX(b));
        });
        LongOpenHashSet left = new LongOpenHashSet(chunks);
        List<Rect> out = new ArrayList<>();
        for (long k : boxed) {
            if (!left.contains(k)) continue;
            int x0 = TerritoryMap.cellX(k), z0 = TerritoryMap.cellZ(k);
            int x1 = x0;
            while (left.contains(TerritoryMap.key(x1 + 1, z0))) x1++;
            int z1 = z0;
            boolean grow = true;
            while (grow) {
                for (int x = x0; x <= x1; x++) {
                    if (!left.contains(TerritoryMap.key(x, z1 + 1))) {
                        grow = false;
                        break;
                    }
                }
                if (grow) z1++;
            }
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) left.remove(TerritoryMap.key(x, z));
            out.add(new Rect(x0, z0, x1, z1));
        }
        return out;
    }

    /**
     * Le bord de ces chunks, en segments droits les plus longs possible (en blocs) : chaque côté de chunk qui touche un
     * chunk hors de l'ensemble, fusionné avec ses voisins alignés.
     */
    public static List<Segment> border(LongSet chunks) {
        // Côtés horizontaux : sur la ligne z (en chunks), de x à x+1 ; côtés verticaux : sur la ligne x, de z à z+1.
        LongOpenHashSet horizontal = new LongOpenHashSet(), vertical = new LongOpenHashSet();
        for (long k : chunks) {
            int x = TerritoryMap.cellX(k), z = TerritoryMap.cellZ(k);
            if (!chunks.contains(TerritoryMap.key(x, z - 1))) horizontal.add(TerritoryMap.key(x, z));
            if (!chunks.contains(TerritoryMap.key(x, z + 1))) horizontal.add(TerritoryMap.key(x, z + 1));
            if (!chunks.contains(TerritoryMap.key(x - 1, z))) vertical.add(TerritoryMap.key(x, z));
            if (!chunks.contains(TerritoryMap.key(x + 1, z))) vertical.add(TerritoryMap.key(x + 1, z));
        }
        List<Segment> out = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        for (long k : horizontal) {
            if (seen.contains(k)) continue;
            int x = TerritoryMap.cellX(k), z = TerritoryMap.cellZ(k);
            int a = x, b = x;
            while (horizontal.contains(TerritoryMap.key(a - 1, z))) a--;
            while (horizontal.contains(TerritoryMap.key(b + 1, z))) b++;
            for (int i = a; i <= b; i++) seen.add(TerritoryMap.key(i, z));
            out.add(new Segment(a * TerritoryMap.CELL, z * TerritoryMap.CELL, (b + 1) * TerritoryMap.CELL, z * TerritoryMap.CELL));
        }
        seen.clear();
        for (long k : vertical) {
            if (seen.contains(k)) continue;
            int x = TerritoryMap.cellX(k), z = TerritoryMap.cellZ(k);
            int a = z, b = z;
            while (vertical.contains(TerritoryMap.key(x, a - 1))) a--;
            while (vertical.contains(TerritoryMap.key(x, b + 1))) b++;
            for (int i = a; i <= b; i++) seen.add(TerritoryMap.key(x, i));
            out.add(new Segment(x * TerritoryMap.CELL, a * TerritoryMap.CELL, x * TerritoryMap.CELL, (b + 1) * TerritoryMap.CELL));
        }
        return out;
    }

    /** Un cercle en polygone (pour une région). */
    public static double[][] circle(double cx, double cz, double r, int points) {
        double[] xs = new double[points], zs = new double[points];
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            xs[i] = cx + Math.cos(a) * r;
            zs[i] = cz + Math.sin(a) * r;
        }
        return new double[][]{xs, zs};
    }
}
