package fr.crusadecraft.client.map;

import fr.crusadecraft.CrusadeCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * La carte de Xaero telle qu'il l'a dessinée chez le joueur (P63), sous nos royaumes : ses propres images, prises dans son
 * cache sur le disque ({@code xaero/world-map/<monde>/<dimension>/<mw>/}), sans rien recalculer ni rien écrire. Le détail
 * ({@code cache_<version>/x_z.xwmc}, un point par bloc) et trois zooms plus lointains ({@code cache/1..3}, deux, quatre et
 * huit blocs par point) ; ce que Xaero n'a jamais vu reste à notre paysage.
 *
 * <p>Le format est celui de Xaero's World Map 1.39 : une entrée {@code cache.xaero} dans un zip, un en-tête, puis chaque
 * image de 64 × 64 points en clair (format {@code GL_RGBA8}, quatre octets dont les trois derniers sont le rouge, le vert et
 * le bleu). On lit l'en-tête pour savoir quelles images existent, puis on retrouve chacune par sa signature, sans dépendre de
 * ce que Xaero range après elle (hauteurs, biomes). Une autre version de Xaero qui changerait ce format ne casse rien : ses
 * fichiers ne se lisent plus, et la carte garde notre paysage.
 */
public final class XaeroCache {
    /** Une région fait 8 × 8 images de 64 points : 512 points de côté, quel que soit le zoom. */
    static final int SIDE = 512, TEX = 64, LEVELS = 4;
    private static final int MAX_LOADED = 40;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrusadeCraft-Xaero");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    /** Les régions lues, les plus récemment servies en dernier. */
    private static final Map<Long, int[]> LOADED = new LinkedHashMap<>(64, 0.75f, true);
    private static final Set<Long> MISSING = ConcurrentHashMap.newKeySet(), LOADING = ConcurrentHashMap.newKeySet();

    private static Path folder;
    private static int leafVersion = 1;
    private static String identity;
    private static long checkedAt, refreshedAt = System.currentTimeMillis();
    private static boolean warned;
    private static long lastKey = Long.MIN_VALUE;
    private static int[] lastPixels;

    private XaeroCache() {}

    /** Vrai si Xaero a un cache pour le monde où l'on joue : la carte le montre alors, et assombrit le reste. */
    public static boolean active(ResourceLocation dim) {
        return locate(dim);
    }

    /**
     * La couleur que Xaero montre en ce point (ARGB opaque), au zoom qui convient à {@code blocksPerPixel} ; 0 si Xaero ne l'a
     * pas (encore) dessiné. Une région pas encore lue se lit à part : la carte se redessine quand elle arrive.
     */
    public static int color(ResourceLocation dim, double x, double z, double blocksPerPixel) {
        if (!locate(dim)) return 0;
        int want = blocksPerPixel < 2 ? 0 : blocksPerPixel < 4 ? 1 : blocksPerPixel < 8 ? 2 : 3;
        // Le zoom voulu, puis plus grossier (tout de suite là), puis plus fin.
        for (int l = want; l < LEVELS; l++) {
            int c = sample(l, x, z);
            if (c != 0) return c;
        }
        for (int l = want - 1; l >= 0; l--) {
            int c = sample(l, x, z);
            if (c != 0) return c;
        }
        return 0;
    }

    private static long key(int level, int rx, int rz) {
        return ((long) level << 60) | (((long) rx & 0x3FFFFFFFL) << 30) | ((long) rz & 0x3FFFFFFFL);
    }

    private static int sample(int level, double x, double z) {
        int span = SIDE << level;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int rx = Math.floorDiv(bx, span), rz = Math.floorDiv(bz, span);
        long k = key(level, rx, rz);
        int[] px;
        if (k == lastKey) {
            px = lastPixels;
        } else {
            synchronized (LOADED) {
                px = LOADED.get(k);
            }
            if (px == null) {
                request(level, rx, rz, k);
                return 0;
            }
            lastKey = k;
            lastPixels = px;
        }
        int ix = (bx - rx * span) >> level, iz = (bz - rz * span) >> level;
        return px[iz * SIDE + ix];
    }

    private static void request(int level, int rx, int rz, long k) {
        if (MISSING.contains(k) || !LOADING.add(k)) return;
        Path base = folder;
        String id = identity;
        int version = leafVersion;
        LOADER.execute(() -> {
            try {
                Path file = level == 0 ? base.resolve("cache_" + version).resolve(rx + "_" + rz + ".xwmc")
                        : base.resolve("cache").resolve(String.valueOf(level)).resolve(rx + "_" + rz + ".xwmc");
                int[] px = Files.isRegularFile(file) ? parse(read(file), level == 0) : null;
                Minecraft.getInstance().execute(() -> {
                    LOADING.remove(k);
                    // Le monde a changé pendant la lecture : on oublie.
                    if (!id.equals(identity)) return;
                    if (px == null) {
                        MISSING.add(k);
                        return;
                    }
                    synchronized (LOADED) {
                        LOADED.put(k, px);
                        if (LOADED.size() > MAX_LOADED) {
                            Long oldest = LOADED.keySet().iterator().next();
                            LOADED.remove(oldest);
                            if (oldest == lastKey) {
                                lastKey = Long.MIN_VALUE;
                                lastPixels = null;
                            }
                        }
                    }
                    ClientTerrain.touch();
                });
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    CrusadeCraft.LOGGER.warn("CrusadeCraft : cache de Xaero illisible ({}), la carte garde son paysage là", t.toString());
                }
                Minecraft.getInstance().execute(() -> {
                    LOADING.remove(k);
                    MISSING.add(k);
                });
            }
        });
    }

    private static byte[] read(Path file) throws java.io.IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (!e.getName().equals("cache.xaero")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
                zip.transferTo(out);
                return out.toByteArray();
            }
        }
        return null;
    }

    /**
     * Les 512 × 512 points d'une région (ARGB, 0 là où Xaero n'a rien), d'après le contenu de {@code cache.xaero} ;
     * {@code null} si ce n'est pas un cache lisible. {@code leaf} : le niveau détaillé, dont l'en-tête porte cinq nombres de
     * plus que celui des zooms lointains.
     */
    static int[] parse(byte[] d, boolean leaf) {
        if (d == null || d.length < 16 || (int32(d, 0) >>> 16) != 1) return null;
        int p = 4 + (leaf ? 20 : 0);
        // Les images présentes : un octet (x << 4 | z), puis leur version ; 255 pour finir.
        int[] present = new int[64];
        int n = 0;
        while (p < d.length && (d[p] & 0xFF) != 255) {
            if (n == 64) return null;
            present[n++] = d[p] & 0xFF;
            p += 5;
        }
        p++;
        // La palette des biomes : un nombre, puis pour chacun 255 (aucun) ou 0 suivi de son nom.
        if (p + 4 > d.length) return null;
        int biomes = int32(d, p);
        p += 4;
        if (biomes < 0 || biomes > 100_000) return null;
        for (int i = 0; i < biomes; i++) {
            if (p >= d.length) return null;
            int b = d[p++] & 0xFF;
            if (b == 255) continue;
            if (p + 2 > d.length) return null;
            p += 2 + (((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF));
        }
        int[] px = new int[SIDE * SIDE];
        boolean any = false;
        for (int t = 0; t < n; t++) {
            int b = present[t];
            // Chaque image : sa place, « non compressée », GL_RGBA8 (0x8058), 16 384 octets.
            byte[] sig = {(byte) b, 0, 0, 0, (byte) 0x80, 0x58, 0, 0, 0x40, 0};
            int at = find(d, sig, p);
            if (at < 0) continue;
            int c = at + sig.length;
            if (c + TEX * TEX * 4 > d.length) break;
            int ox = (b >> 4) * TEX, oz = (b & 15) * TEX;
            for (int k = 0; k < TEX * TEX; k++) {
                int r = d[c + k * 4 + 1] & 0xFF, g = d[c + k * 4 + 2] & 0xFF, bl = d[c + k * 4 + 3] & 0xFF;
                if ((r | g | bl) == 0) continue;
                px[(oz + k / TEX) * SIDE + ox + k % TEX] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                any = true;
            }
            p = c + TEX * TEX * 4;
        }
        return any ? px : null;
    }

    private static int int32(byte[] d, int p) {
        return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16) | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }

    private static int find(byte[] d, byte[] sig, int from) {
        outer:
        for (int i = Math.max(0, from); i + sig.length <= d.length; i++) {
            for (int j = 0; j < sig.length; j++) if (d[i + j] != sig[j]) continue outer;
            return i;
        }
        return -1;
    }

    /**
     * Où Xaero range la carte du monde où l'on joue. On le lui demande (monde, dimension, sous-monde, version du cache) ;
     * sans Xaero, ou dans une autre dimension que celle où l'on se tient, rien.
     */
    private static boolean locate(ResourceLocation dim) {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 3000) return folder != null && dimMatches(dim);
        checkedAt = now;
        // Xaero dessine à mesure qu'on explore : toutes les deux minutes, ce qu'on a lu se relira à la demande.
        if (now - refreshedAt > 120_000) {
            refreshedAt = now;
            synchronized (LOADED) {
                LOADED.clear();
            }
            MISSING.clear();
            lastKey = Long.MIN_VALUE;
            lastPixels = null;
        }
        String[] ids = null;
        if (ModList.get().isLoaded("xaeroworldmap")) {
            try {
                ids = fr.crusadecraft.compat.xaero.XaeroWorldMapIds.current();
            } catch (Throwable t) {
                ids = null;
            }
        }
        Path found = null;
        String id = null;
        if (ids != null && ids[0] != null && ids[1] != null) {
            // Un monde solo n'a pas de sous-monde : ses images sont dans le dossier de la dimension.
            Path mw = Minecraft.getInstance().gameDirectory.toPath().resolve("xaero").resolve("world-map").resolve(ids[0]).resolve(ids[1]);
            if (ids[2] != null && !ids[2].isEmpty()) mw = mw.resolve(ids[2]);
            if (Files.isDirectory(mw)) {
                found = mw;
                id = ids[0] + "/" + ids[1] + "/" + ids[2];
                try {
                    leafVersion = Integer.parseInt(ids[3]);
                } catch (NumberFormatException e) {
                    leafVersion = 1;
                }
            }
        }
        if (!java.util.Objects.equals(id, identity)) {
            // Un autre monde : tout ce qu'on avait lu ne vaut plus.
            if (found != null) CrusadeCraft.LOGGER.info("CrusadeCraft : la carte montre celle de Xaero, lue dans {}", found);
            else if (ids != null) CrusadeCraft.LOGGER.info("CrusadeCraft : pas de carte de Xaero pour {}", String.join(" / ", java.util.Arrays.asList(ids)));
            identity = id;
            folder = found;
            synchronized (LOADED) {
                LOADED.clear();
            }
            MISSING.clear();
            lastKey = Long.MIN_VALUE;
            lastPixels = null;
            ClientTerrain.touch();
        }
        return folder != null && dimMatches(dim);
    }

    private static boolean dimMatches(ResourceLocation dim) {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().location().equals(dim);
    }
}
