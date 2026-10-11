package fr.crusadecraft.client.map;

import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
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
 *
 * <p>Avec un terrain (P18b), le parchemin laisse place au vrai paysage : les royaumes le teintent sans le cacher, le
 * brouillard l'assombrit et le hachure, et la vue « Terrain » le montre seul, frontières en trait. Pendant une guerre,
 * les provinces occupées se hachurent à la couleur de l'occupant et la ligne de front se trace en rouge.
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
    private static final int FRONT = 0xC0281E;

    /** Ce que la carte montre : les États, les royaumes de jure, ou le paysage seul. */
    public enum Mode { POLITICAL, DEJURE, TERRAIN }

    /** La teinte du paysage en un point du monde (ARGB opaque), 0 si elle n'est pas connue. */
    @FunctionalInterface
    public interface TerrainSource {
        int color(double x, double z);
    }

    /**
     * Ce qui s'ajoute à la carte : le paysage, et pendant une guerre les provinces occupées (identifiant de province →
     * couleur de l'occupant) et les deux camps (indices de royaumes) pour la ligne de front.
     */
    public record Extras(TerrainSource terrain, Int2IntMap occupied, IntSet friends, IntSet foes) {
        public static final Extras NONE = new Extras(null, new Int2IntOpenHashMap(), new IntOpenHashSet(), new IntOpenHashSet());
    }

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
        return render(v, w, h, centerX, centerZ, blocksPerPixel, dejure ? Mode.DEJURE : Mode.POLITICAL, overlay, Extras.NONE);
    }

    public static Result render(MapView v, int w, int h, double centerX, double centerZ, double blocksPerPixel, Mode mode,
                                Long2IntMap overlay, Extras extras) {
        boolean dejure = mode == Mode.DEJURE, terrainOnly = mode == Mode.TERRAIN;
        TerrainSource terrain = extras.terrain();
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
        int[] occupier = new int[n];
        for (int i = 0; i < n; i++) {
            MapView.Seat s = v.seats().get(i);
            MapView.Territory t = v.territories().get(s.territory());
            // Le centre du bloc, comme le serveur (Territory) : les mêmes chunks de part et d'autre.
            sx[i] = s.x() + 0.5;
            sz[i] = s.z() + 0.5;
            // Une construction tient le territoire de sa nature (rien tant qu'elle est en chantier).
            double r = s.site() != null ? s.site().radius() : s.province() ? v.territoryRadius() : v.colonyRadius();
            r2[i] = r * r;
            reveal2[i] = (Math.max(r, 32) * 2.2) * (Math.max(r, 32) * 2.2);
            occupier[i] = s.provinceId() >= 0 && extras.occupied().containsKey(s.provinceId()) ? extras.occupied().get(s.provinceId()) : -1;
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
                // Les terres se décident au chunk (P16), comme sur le serveur : par la distance au centre du chunk.
                double qx = Math.floor(wx / TerritoryMap.CELL) * TerritoryMap.CELL + TerritoryMap.CELL / 2.0;
                double qz = Math.floor(wz / TerritoryMap.CELL) * TerritoryMap.CELL + TerritoryMap.CELL / 2.0;
                int best = -1;
                double bestD = Double.MAX_VALUE;
                // Dans l'éditeur, pas de brouillard : c'est souvent en terre inconnue qu'on pose ses royaumes.
                boolean revealed = v.editing() || sq(wx - v.playerX()) + sq(wz - v.playerZ()) <= playerReveal2;
                for (int i = 0; i < n; i++) {
                    double d = sq(qx - sx[i]) + sq(qz - sz[i]);
                    if (d <= r2[i] && d < bestD) {
                        best = i;
                        bestD = d;
                    }
                    if (sq(wx - sx[i]) + sq(wz - sz[i]) <= reveal2[i]) revealed = true;
                }
                int idx = y * w + x;
                int noise = grain(wx, wz);
                // Le fond : le paysage s'il est connu, sinon le parchemin.
                int land = terrain == null ? 0 : terrain.color(wx, wz);
                int ground = land != 0 ? land & 0xFFFFFF : shade(PARCHMENT, noise);
                if (terrainOnly) revealed = true;
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
                            pixels[idx] = 0xFF000000 | (terrainOnly ? ground : tint(v.territories().get(paint).color(), ground, land != 0, noise));
                            continue;
                        }
                        revealed = true;
                    }
                }
                if (best >= 0) {
                    owners[idx] = best;
                    groups[idx] = group[best];
                    int c = terrainOnly ? ground : tint(colors[best], ground, land != 0, noise);
                    // Une province occupée : rayée à la couleur de l'occupant.
                    if (!terrainOnly && occupier[best] >= 0 && Math.floorMod(x - y, 7) < 2) c = mix(c, occupier[best], 0.7);
                    pixels[idx] = 0xFF000000 | c;
                } else if (revealed) {
                    owners[idx] = NONE;
                    groups[idx] = -1;
                    pixels[idx] = 0xFF000000 | (land != 0 && !terrainOnly ? mix(ground, PARCHMENT, 0.15) : ground);
                } else {
                    owners[idx] = FOGGED;
                    groups[idx] = -2;
                    boolean hatch = Math.floorMod(x + y, 6) == 0;
                    int fog = land != 0 ? mix(ground, FOG, 0.6) : FOG;
                    pixels[idx] = 0xFF000000 | shade(hatch ? mix(fog, 0x000000, 0.12) : fog, noise / 2);
                }
            }
        }
        // Frontières : un pixel de territoire voisin d'un autre groupe (ou du vide) s'assombrit. Entre deux camps en
        // guerre, c'est la ligne de front, en rouge et plus épaisse.
        boolean war = !dejure && !terrainOnly && !extras.foes().isEmpty();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (owners[idx] < 0) continue;
                int g = groups[idx];
                boolean edge = (x + 1 < w && groups[idx + 1] != g) || (x > 0 && groups[idx - 1] != g)
                        || (y + 1 < h && groups[idx + w] != g) || (y > 0 && groups[idx - w] != g);
                if (!edge) continue;
                if (war && front(groups, w, h, x, y, extras)) {
                    pixels[idx] = 0xFF000000 | FRONT;
                    continue;
                }
                pixels[idx] = 0xFF000000 | mix(pixels[idx] & 0xFFFFFF, 0x2A1E12, terrainOnly ? 0.75 : 0.55);
            }
        }
        return new Result(w, h, pixels, owners);
    }

    /** La couleur d'un royaume posée sur le fond : franche sur le parchemin, en voile sur le paysage. */
    private static int tint(int realm, int ground, boolean landscape, int noise) {
        return landscape ? mix(ground, realm, 0.5) : shade(mix(realm, PARCHMENT, 0.45), noise);
    }

    /** Ce pixel de bord touche-t-il, à deux pixels près, l'autre camp ? (groupe 1000 + indice de royaume) */
    private static boolean front(int[] groups, int w, int h, int x, int y, Extras extras) {
        int mine = groups[y * w + x] - 1000;
        boolean friend = extras.friends().contains(mine), foe = extras.foes().contains(mine);
        if (!friend && !foe) return false;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int other = groups[ny * w + nx] - 1000;
                if (other < 0) continue;
                if (friend && extras.foes().contains(other)) return true;
                if (foe && extras.friends().contains(other)) return true;
            }
        }
        return false;
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
