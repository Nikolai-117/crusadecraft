package fr.crusadecraft.client.map;

import fr.crusadecraft.core.world.terrain.TerrainPalette;
import fr.crusadecraft.core.world.terrain.TerrainTile;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.TerrainPackets;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/**
 * Les tuiles de la carte en terrain reçues du serveur (P18b), pour la dimension où se trouve le joueur. Une tuile
 * fine qui manque laisse voir la plus grossière, qui arrive en premier ; une tuile reçue depuis plus de deux minutes
 * est redemandée si on la regarde encore (des chunks parcourus entre-temps y ont pris leur vraie couleur).
 *
 * <p>Chaque point reçoit son ombre une fois pour toutes (elle dépend de son voisin du nord-ouest, peut-être dans une
 * autre tuile : elle est refaite quand ce voisin arrive), puis un pixel fond les quatre points qui l'entourent.
 */
public final class ClientTerrain {
    private static final int MAX_TILES = 1536;
    private static final long ASK_AGAIN_MS = 15_000, STALE_MS = 120_000;
    private static ResourceLocation dimension;
    private static final Long2ObjectLinkedOpenHashMap<TerrainTile> TILES = new Long2ObjectLinkedOpenHashMap<>();
    /** Les teintes ombrées de chaque tuile, faites à la première lecture. */
    private static final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> SHADED = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static long lastShadedKey = Long.MIN_VALUE;
    private static int[] lastShaded;
    private static final Long2LongOpenHashMap ASKED = new Long2LongOpenHashMap(), RECEIVED = new Long2LongOpenHashMap();
    private static int version;
    // La dernière tuile lue : les pixels voisins tombent presque toujours dans la même.
    private static long lastKey = Long.MIN_VALUE;
    private static TerrainTile lastTile;

    private ClientTerrain() {}

    public static void put(ResourceLocation dim, TerrainTile tile) {
        if (!dim.equals(dimension)) clear(dim);
        long key = tile.key();
        TILES.remove(key);
        TILES.put(key, tile);
        RECEIVED.put(key, Util.getMillis());
        ASKED.remove(key);
        // Son ombre, et celle des tuiles au sud et à l'est, qui prenaient ce voisin manquant pour plat.
        SHADED.remove(key);
        SHADED.remove(TerrainTile.key(tile.lod(), tile.tx() + 1, tile.tz()));
        SHADED.remove(TerrainTile.key(tile.lod(), tile.tx(), tile.tz() + 1));
        SHADED.remove(TerrainTile.key(tile.lod(), tile.tx() + 1, tile.tz() + 1));
        while (TILES.size() > MAX_TILES) {
            long old = TILES.firstLongKey();
            TILES.removeFirst();
            RECEIVED.remove(old);
            SHADED.remove(old);
        }
        lastKey = Long.MIN_VALUE;
        lastTile = null;
        lastShadedKey = Long.MIN_VALUE;
        lastShaded = null;
        version++;
    }

    /** Change à chaque tuile reçue : la carte ouverte sait qu'il faut se redessiner. */
    public static int version() {
        return version;
    }

    public static void clear(ResourceLocation dim) {
        dimension = dim;
        TILES.clear();
        SHADED.clear();
        ASKED.clear();
        RECEIVED.clear();
        lastKey = Long.MIN_VALUE;
        lastTile = null;
        lastShadedKey = Long.MIN_VALUE;
        lastShaded = null;
        version++;
    }

    /** Le niveau de détail qui convient à ce zoom : un point de terrain tous les deux à huit pixels, fondus entre eux. */
    public static int lodFor(double blocksPerPixel) {
        if (blocksPerPixel <= 8) return 0;
        if (blocksPerPixel <= 32) return 1;
        return 2;
    }

    private static TerrainTile tile(long key) {
        if (key == lastKey) return lastTile;
        lastKey = key;
        lastTile = TILES.get(key);
        return lastTile;
    }

    private static int height(int lod, int sx, int sz, int fallback) {
        TerrainTile t = tile(TerrainTile.key(lod, Math.floorDiv(sx, TerrainTile.SIZE), Math.floorDiv(sz, TerrainTile.SIZE)));
        if (t == null) return fallback;
        int i = t.index(Math.floorMod(sx, TerrainTile.SIZE), Math.floorMod(sz, TerrainTile.SIZE));
        return t.colors()[i] == 0 ? fallback : t.heights()[i];
    }

    /** Les teintes ombrées d'une tuile présente. */
    private static int[] shaded(long key, TerrainTile t) {
        // La clé de la toute première tuile du monde vaut zéro, comme lastShadedKey au départ : sans ce garde-fou, la carte plantait.
        if (key == lastShadedKey && lastShaded != null) return lastShaded;
        int[] out = SHADED.get(key);
        if (out == null) {
            out = new int[TerrainTile.SIZE * TerrainTile.SIZE];
            int l = t.lod(), spacing = TerrainTile.spacing(l);
            for (int lz = 0; lz < TerrainTile.SIZE; lz++) {
                for (int lx = 0; lx < TerrainTile.SIZE; lx++) {
                    int i = t.index(lx, lz), c = t.colors()[i];
                    if (c == 0) continue;
                    int h = t.heights()[i];
                    int nw = lx > 0 && lz > 0 ? (t.colors()[i - TerrainTile.SIZE - 1] == 0 ? h : t.heights()[i - TerrainTile.SIZE - 1])
                            : height(l, t.tx() * TerrainTile.SIZE + lx - 1, t.tz() * TerrainTile.SIZE + lz - 1, h);
                    out[i] = 0xFF000000 | TerrainPalette.shade(c & 0xFFFFFF, h, nw, spacing);
                }
            }
            SHADED.put(key, out);
        }
        lastShadedKey = key;
        lastShaded = out;
        return out;
    }

    /** La teinte ombrée d'un point de la grille (indices globaux), 0 si sa tuile manque. */
    private static int sample(int lod, int sx, int sz) {
        long key = TerrainTile.key(lod, Math.floorDiv(sx, TerrainTile.SIZE), Math.floorDiv(sz, TerrainTile.SIZE));
        TerrainTile t = tile(key);
        if (t == null) return 0;
        return shaded(key, t)[t.index(Math.floorMod(sx, TerrainTile.SIZE), Math.floorMod(sz, TerrainTile.SIZE))];
    }

    /**
     * La teinte de ce point du monde (ARGB opaque), fondue entre les quatre points de grille qui l'entourent, à ce niveau
     * de détail ou à un plus grossier ; 0 si rien n'est connu.
     */
    public static int color(ResourceLocation dim, double x, double z, int lod) {
        if (!dim.equals(dimension)) return 0;
        for (int l = lod; l < TerrainTile.LODS; l++) {
            int spacing = TerrainTile.spacing(l);
            // Les points se mesurent au milieu de leur case : on se repère par rapport à ces milieux.
            double fx = x / spacing - 0.5, fz = z / spacing - 0.5;
            int sx = (int) Math.floor(fx), sz = (int) Math.floor(fz);
            double ax = fx - sx, az = fz - sz;
            int c00 = sample(l, sx, sz), c10 = sample(l, sx + 1, sz), c01 = sample(l, sx, sz + 1), c11 = sample(l, sx + 1, sz + 1);
            int near = ax < 0.5 ? (az < 0.5 ? c00 : c01) : (az < 0.5 ? c10 : c11);
            if (near == 0) continue;
            // Un voisin inconnu prend la teinte du plus proche : pas de bord sombre au bout de ce qui est arrivé.
            if (c00 == 0) c00 = near;
            if (c10 == 0) c10 = near;
            if (c01 == 0) c01 = near;
            if (c11 == 0) c11 = near;
            int top = TerrainPalette.mix(c00 & 0xFFFFFF, c10 & 0xFFFFFF, ax), bottom = TerrainPalette.mix(c01 & 0xFFFFFF, c11 & 0xFFFFFF, ax);
            return 0xFF000000 | TerrainPalette.mix(top, bottom, az);
        }
        return 0;
    }

    /**
     * Demande au serveur les tuiles de ce rectangle qui manquent (ou ont vieilli) : les grossières, qui couvrent tout
     * vite, puis celles du niveau voulu. Une tuile déjà demandée n'est redemandée qu'après quinze secondes.
     */
    public static void ensure(ResourceLocation dim, int lod, double minX, double minZ, double maxX, double maxZ) {
        if (!dim.equals(dimension)) clear(dim);
        long now = Util.getMillis();
        for (int l = TerrainTile.LODS - 1; l >= lod; l--) {
            int tx0 = TerrainTile.tileOf(minX, l), tx1 = TerrainTile.tileOf(maxX, l);
            int tz0 = TerrainTile.tileOf(minZ, l), tz1 = TerrainTile.tileOf(maxZ, l);
            if ((long) (tx1 - tx0 + 1) * (tz1 - tz0 + 1) > 400) continue;
            LongArrayList want = new LongArrayList();
            // Du centre vers les bords : ce qu'on regarde arrive d'abord.
            int cx = (tx0 + tx1) / 2, cz = (tz0 + tz1) / 2;
            int reach = Math.max(Math.max(cx - tx0, tx1 - cx), Math.max(cz - tz0, tz1 - cz));
            for (int ring = 0; ring <= reach; ring++) {
                for (int tx = cx - ring; tx <= cx + ring; tx++) {
                    for (int tz = cz - ring; tz <= cz + ring; tz++) {
                        if (Math.max(Math.abs(tx - cx), Math.abs(tz - cz)) != ring) continue;
                        if (tx < tx0 || tx > tx1 || tz < tz0 || tz > tz1) continue;
                        long key = TerrainTile.key(l, tx, tz);
                        boolean have = TILES.containsKey(key) && now - RECEIVED.get(key) < STALE_MS;
                        if (have || now - ASKED.getOrDefault(key, Long.MIN_VALUE / 2) < ASK_AGAIN_MS) continue;
                        ASKED.put(key, now);
                        want.add(key);
                    }
                }
            }
            for (int from = 0; from < want.size(); from += 64) {
                long[] part = want.subList(from, Math.min(want.size(), from + 64)).toLongArray();
                CrusadeNetwork.toServer(new TerrainPackets.Request(l, part));
            }
        }
    }
}
