package fr.crusadecraft.client.map;

import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcule l'image de la carte, pixel par pixel, sans rien de graphique : chaque pixel prend la couleur du
 * siège le plus proche dans son rayon (vue politique : le royaume ; vue de jure : le royaume de jure), les
 * frontières s'assombrissent, le parchemin entoure ce qui est connu et le brouillard couvre le reste.
 *
 * <p>Une case peinte à la main l'emporte sur ces cercles : elle prend la couleur de son royaume (et son pixel, le
 * siège de ce royaume le plus proche), ou redevient terre libre.
 */
public final class MapRaster {
    public static final int PARCHMENT = 0xE8D9B0;
    private static final int FOG = 0x8F8572;
    /** Indice de propriétaire d'un pixel hors de tout territoire. */
    public static final int NONE = -1;
    /** Indice de propriétaire d'un pixel dans le brouillard. */
    public static final int FOGGED = -2;
    /** Dans une retouche en cours ({@code overlay}) : la case est effacée, le territoire automatique revient. */
    public static final int ERASED = -2;
    private static final int UNPAINTED = Integer.MIN_VALUE;

    /**
     * @param pixels  couleurs ARGB, ligne par ligne
     * @param owners  pour chaque pixel, l'indice du siège qui le tient, {@link #NONE} ou {@link #FOGGED}
     */
    public record Result(int width, int height, int[] pixels, int[] owners) {
        public int ownerAt(int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) return NONE;
            return owners[y * width + x];
        }
    }

    private MapRaster() {}

    public static Result render(MapView v, int w, int h, double centerX, double centerZ, double blocksPerPixel, boolean dejure) {
        return render(v, w, h, centerX, centerZ, blocksPerPixel, dejure, null);
    }

    /**
     * @param overlay retouches que le serveur n'a pas encore confirmées (case → royaume, {@link MapView.Borders#NEUTRAL}
     *                ou {@link #ERASED}), ou {@code null}
     */
    public static Result render(MapView v, int w, int h, double centerX, double centerZ, double blocksPerPixel, boolean dejure,
                                Long2IntMap overlay) {
        Long2IntOpenHashMap painted = new Long2IntOpenHashMap(v.borders().size() + (overlay == null ? 0 : overlay.size()));
        painted.defaultReturnValue(UNPAINTED);
        for (int i = 0; i < v.borders().size(); i++) painted.put(v.borders().keys()[i], v.borders().owners()[i]);
        if (overlay != null) {
            for (Long2IntMap.Entry e : overlay.long2IntEntrySet()) {
                if (e.getIntValue() == ERASED) painted.remove(e.getLongKey());
                else painted.put(e.getLongKey(), e.getIntValue());
            }
        }
        int territories = v.territories().size();
        List<List<Integer>> seatsOf = new ArrayList<>(territories);
        for (int t = 0; t < territories; t++) seatsOf.add(new ArrayList<>());
        for (int i = 0; i < v.seats().size(); i++) {
            int t = v.seats().get(i).territory();
            if (t >= 0 && t < territories) seatsOf.get(t).add(i);
        }
        int[] pixels = new int[w * h];
        int[] owners = new int[w * h];
        int n = v.seats().size();
        double[] sx = new double[n], sz = new double[n], r2 = new double[n], reveal2 = new double[n];
        int[] colors = new int[n];
        int[] group = new int[n];
        for (int i = 0; i < n; i++) {
            MapView.Seat s = v.seats().get(i);
            MapView.Territory t = v.territories().get(s.territory());
            sx[i] = s.x();
            sz[i] = s.z();
            double r = s.province() ? v.territoryRadius() : v.colonyRadius();
            r2[i] = r * r;
            reveal2[i] = (r * 2.2) * (r * 2.2);
            if (dejure && s.dejure() >= 0 && s.dejure() < v.dejure().size()) {
                colors[i] = v.dejure().get(s.dejure()).color();
                group[i] = s.dejure();
            } else {
                colors[i] = t.color();
                group[i] = 1000 + s.territory();
            }
        }
        double playerReveal2 = 320.0 * 320.0;
        int[] groups = new int[w * h];
        for (int y = 0; y < h; y++) {
            double wz = centerZ + (y - h / 2.0) * blocksPerPixel;
            for (int x = 0; x < w; x++) {
                double wx = centerX + (x - w / 2.0) * blocksPerPixel;
                int best = -1;
                double bestD = Double.MAX_VALUE;
                // Dans l'éditeur, pas de brouillard : c'est souvent en terre inconnue qu'on pose ses royaumes.
                boolean revealed = v.editing() || sq(wx - v.playerX()) + sq(wz - v.playerZ()) <= playerReveal2;
                for (int i = 0; i < n; i++) {
                    double d = sq(wx - sx[i]) + sq(wz - sz[i]);
                    if (d <= r2[i] && d < bestD) {
                        best = i;
                        bestD = d;
                    }
                    if (d <= reveal2[i]) revealed = true;
                }
                int idx = y * w + x;
                int noise = grain(wx, wz);
                int paint = painted.isEmpty() ? UNPAINTED
                        : painted.get(TerritoryMap.key(TerritoryMap.cellOf(wx), TerritoryMap.cellOf(wz)));
                if (paint != UNPAINTED) {
                    // Une case peinte : son royaume, ou personne. Le siège retenu est le plus proche de ce royaume.
                    best = -1;
                    if (paint >= 0 && paint < territories) {
                        double near = Double.MAX_VALUE;
                        for (int i : seatsOf.get(paint)) {
                            double d = sq(wx - sx[i]) + sq(wz - sz[i]);
                            if (d < near) {
                                near = d;
                                best = i;
                            }
                        }
                        if (best < 0) {
                            owners[idx] = NONE;
                            groups[idx] = 1000 + paint;
                            pixels[idx] = 0xFF000000 | shade(mix(v.territories().get(paint).color(), PARCHMENT, 0.45), noise);
                            continue;
                        }
                        revealed = true;
                    }
                }
                if (best >= 0) {
                    owners[idx] = best;
                    groups[idx] = group[best];
                    pixels[idx] = 0xFF000000 | shade(mix(colors[best], PARCHMENT, 0.45), noise);
                } else if (revealed) {
                    owners[idx] = NONE;
                    groups[idx] = -1;
                    pixels[idx] = 0xFF000000 | shade(PARCHMENT, noise);
                } else {
                    owners[idx] = FOGGED;
                    groups[idx] = -2;
                    boolean hatch = Math.floorMod(x + y, 6) == 0;
                    pixels[idx] = 0xFF000000 | shade(hatch ? mix(FOG, 0x000000, 0.12) : FOG, noise / 2);
                }
            }
        }
        // Frontières : un pixel de territoire voisin d'un autre groupe (ou du vide) s'assombrit.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (owners[idx] < 0) continue;
                int g = groups[idx];
                boolean edge = (x + 1 < w && groups[idx + 1] != g) || (x > 0 && groups[idx - 1] != g)
                        || (y + 1 < h && groups[idx + w] != g) || (y > 0 && groups[idx - w] != g);
                if (edge) pixels[idx] = 0xFF000000 | mix(pixels[idx] & 0xFFFFFF, 0x2A1E12, 0.55);
            }
        }
        return new Result(w, h, pixels, owners);
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Grain du parchemin : une petite variation stable, liée aux coordonnées du monde. */
    private static int grain(double wx, double wz) {
        long h = (long) Math.floor(wx / 12) * 73856093L ^ (long) Math.floor(wz / 12) * 19349663L;
        h ^= (h >>> 13);
        return (int) Math.floorMod(h, 13L) - 6;
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shade(int rgb, int delta) {
        int r = clamp(((rgb >> 16) & 0xFF) + delta), g = clamp(((rgb >> 8) & 0xFF) + delta), b = clamp((rgb & 0xFF) + delta);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Écart conseillé entre deux pixels pour embrasser tous les sièges connus autour d'un point. */
    public static double fit(MapView v, double centerX, double centerZ, int w, int h) {
        double far = 600;
        for (MapView.Seat s : v.seats()) far = Math.max(far, Math.hypot(s.x() - centerX, s.z() - centerZ) + v.territoryRadius());
        return Math.max(1.0, 2 * far / Math.max(1, Math.min(w, h)));
    }
}
