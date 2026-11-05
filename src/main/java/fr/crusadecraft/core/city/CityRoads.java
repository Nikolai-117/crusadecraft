package fr.crusadecraft.core.city;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.world.terrain.TerrainSampler;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Les routes entre les villes (P52). Un chemin se cherche sur le terrain que le générateur prévoit, sans charger un seul chunk : il
 * contourne l'eau et les pentes, et préfère les routes qui existent déjà, si bien qu'un réseau se forme de lui-même. La recherche
 * tourne hors du fil du serveur ; en attendant son résultat, on marche tout droit.
 */
public final class CityRoads {
    /** Le maillage de la recherche, au plus fin. */
    static final int CELL = 16;
    /** Une recherche s'arrête après tant de cases : on marche alors tout droit. */
    static final int MAX_NODES = 20000;
    /** On aplanit le tracé en tant de points au plus entre deux villes : assez pour la carte, sans gonfler la sauvegarde. */
    static final int MAX_POINTS = 96;

    /** Un tracé : une suite de points, et sa longueur en blocs. */
    public record Route(int[] xs, int[] zs, double length) {
        public static Route of(List<int[]> points) {
            int[] x = new int[points.size()], z = new int[points.size()];
            double len = 0;
            for (int i = 0; i < points.size(); i++) {
                x[i] = points.get(i)[0];
                z[i] = points.get(i)[1];
                if (i > 0) len += Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
            return new Route(x, z, len);
        }

        public static Route straight(BlockPos a, BlockPos b) {
            return of(List.of(new int[]{a.getX(), a.getZ()}, new int[]{b.getX(), b.getZ()}));
        }

        /** Le point à cette fraction du chemin, de 0 (départ) à 1 (arrivée). */
        public int[] at(double f) {
            double want = Math.max(0, Math.min(1, f)) * length, walked = 0;
            for (int i = 1; i < xs.length; i++) {
                double seg = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                if (walked + seg >= want && seg > 0) {
                    double t = (want - walked) / seg;
                    return new int[]{(int) Math.round(xs[i - 1] + (xs[i] - xs[i - 1]) * t), (int) Math.round(zs[i - 1] + (zs[i] - zs[i - 1]) * t)};
                }
                walked += seg;
            }
            return new int[]{xs[xs.length - 1], zs[zs.length - 1]};
        }

        public Route reversed() {
            int n = xs.length;
            int[] x = new int[n], z = new int[n];
            for (int i = 0; i < n; i++) {
                x[i] = xs[n - 1 - i];
                z[i] = zs[n - 1 - i];
            }
            return new Route(x, z, length);
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putIntArray("x", xs);
            t.putIntArray("z", zs);
            return t;
        }

        public static Route load(CompoundTag t) {
            int[] x = t.getIntArray("x"), z = t.getIntArray("z");
            List<int[]> pts = new ArrayList<>();
            for (int i = 0; i < Math.min(x.length, z.length); i++) pts.add(new int[]{x[i], z[i]});
            return of(pts);
        }
    }

    /** Ce qu'on sait du terrain, pour la recherche. */
    public interface Terrain {
        /** L'altitude de ce point. */
        int height(int x, int z);

        /** Une route passe-t-elle déjà par là ? */
        default boolean road(int x, int z) {
            return false;
        }
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrusadeCraft routes");
        t.setDaemon(true);
        return t;
    });
    private static final Set<String> PENDING = new HashSet<>();
    /** La dernière position où chacun a creusé son chemin. */
    private static final Map<UUID, BlockPos> WORN = new HashMap<>();

    private CityRoads() {}

    public static void onServerStopped() {
        PENDING.clear();
        WORN.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    /**
     * La route de {@code from} à {@code to}. Si elle n'est pas encore tracée, on en lance le calcul et l'on rend la ligne droite : le tracé
     * servira dès qu'il sera prêt.
     */
    public static Route between(MinecraftServer server, Site from, Site to) {
        String k = key(from.id(), to.id());
        Route known = data(server).world().roads().get(k);
        if (known != null) return from.id().compareTo(to.id()) < 0 ? known : known.reversed();
        schedule(server, from, to, k);
        return Route.straight(from.anchor(), to.anchor());
    }

    /** Toutes les routes tracées, pour les dessiner. */
    public static Collection<Route> all(MinecraftServer server) {
        return data(server).world().roads().values();
    }

    private static void schedule(MinecraftServer server, Site from, Site to, String k) {
        if (!PENDING.add(k)) return;
        Site a = from.id().compareTo(to.id()) < 0 ? from : to, b = a == from ? to : from;
        if (!a.dimension().equals(b.dimension())) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, a.dimension()));
        if (level == null) return;
        Set<Long> existing = cells(all(server));
        int ax = a.anchor().getX(), az = a.anchor().getZ(), bx = b.anchor().getX(), bz = b.anchor().getZ();
        int sea = level.getSeaLevel();
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        WORKER.execute(() -> {
            List<int[]> points = null;
            try {
                Map<Long, Integer> heights = new HashMap<>();
                Terrain terrain = new Terrain() {
                    @Override
                    public int height(int x, int z) {
                        return heights.computeIfAbsent(pack(x, z), k2 -> TerrainSampler.estimate(level, generator, random, x, z).height());
                    }

                    @Override
                    public boolean road(int x, int z) {
                        return existing.contains(pack(x >> 4, z >> 4));
                    }
                };
                points = plan(ax, az, bx, bz, terrain, sea, MAX_NODES);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : route non tracée : {}", e.toString());
            }
            List<int[]> done = points;
            server.execute(() -> {
                PENDING.remove(k);
                if (done == null) return;
                data(server).world().roads().put(k, Route.of(done));
                data(server).setDirty();
            });
        });
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static Set<Long> cells(Collection<Route> routes) {
        Set<Long> out = new HashSet<>();
        for (Route r : routes) {
            int steps = (int) (r.length() / 8) + 1;
            for (int i = 0; i <= steps; i++) {
                int[] p = r.at((double) i / steps);
                out.add(pack(p[0] >> 4, p[1] >> 4));
            }
        }
        return out;
    }

    /**
     * Cherche un chemin (A*, en logique pure) : marcher coûte sa longueur, une pente davantage, l'eau bien plus, et une route déjà là moitié
     * moins. Renvoie les points du tracé, ou {@code null} si la recherche dépasse sa limite.
     */
    public static List<int[]> plan(int x0, int z0, int x1, int z1, Terrain terrain, int sea, int maxNodes) {
        double direct = Math.hypot(x1 - x0, z1 - z0);
        int cell = (int) Math.max(CELL, Math.ceil(direct / 80));
        if (direct <= cell * 2) return List.of(new int[]{x0, z0}, new int[]{x1, z1});
        int gx1 = Math.round((float) (x1 - x0) / cell), gz1 = Math.round((float) (z1 - z0) / cell);
        int margin = (int) Math.max(16, direct / cell / 2);
        record Node(int x, int z, double f) {}
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        open.add(new Node(0, 0, 0));
        best.put(pack(0, 0), 0d);
        int visited = 0;
        boolean found = false;
        while (!open.isEmpty()) {
            Node n = open.poll();
            if (n.x() == gx1 && n.z() == gz1) {
                found = true;
                break;
            }
            if (++visited > maxNodes) return null;
            double g = best.get(pack(n.x(), n.z()));
            int h = terrain.height(x0 + n.x() * cell, z0 + n.z() * cell);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = n.x() + dx, nz = n.z() + dz;
                    if (nx < Math.min(0, gx1) - margin || nx > Math.max(0, gx1) + margin || nz < Math.min(0, gz1) - margin || nz > Math.max(0, gz1) + margin) continue;
                    int wx = x0 + nx * cell, wz = z0 + nz * cell;
                    int h2 = terrain.height(wx, wz);
                    double step = Math.hypot(dx, dz) * cell;
                    double slope = Math.abs(h2 - h) / step;
                    double weight = 1 + slope * slope * 24;
                    if (h2 < sea) weight += 8;
                    if (terrain.road(wx, wz)) weight *= 0.5;
                    double ng = g + step * weight;
                    long key = pack(nx, nz);
                    Double known = best.get(key);
                    if (known != null && known <= ng) continue;
                    best.put(key, ng);
                    from.put(key, pack(n.x(), n.z()));
                    // L'estimation vise la route la moins chère possible (une route déjà là) ; un peu forcée, pour aller droit au but.
                    double rest = Math.hypot(gx1 - nx, gz1 - nz) * cell * 0.75;
                    open.add(new Node(nx, nz, ng + rest));
                }
            }
        }
        if (!found) return null;
        List<int[]> reversed = new ArrayList<>();
        long at = pack(gx1, gz1);
        while (true) {
            int gx = (int) (at >> 32), gz = (int) at;
            reversed.add(new int[]{x0 + gx * cell, z0 + gz * cell});
            Long prev = from.get(at);
            if (prev == null) break;
            at = prev;
        }
        List<int[]> out = new ArrayList<>();
        for (int i = reversed.size() - 1; i >= 0; i--) out.add(reversed.get(i));
        out.set(0, new int[]{x0, z0});
        out.set(out.size() - 1, new int[]{x1, z1});
        return simplify(out);
    }

    /** On enlève les points alignés ; un long tracé n'en garde pas plus que {@link #MAX_POINTS}. */
    static List<int[]> simplify(List<int[]> pts) {
        List<int[]> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 1; i < pts.size() - 1; i++) {
            int[] a = out.get(out.size() - 1), b = pts.get(i), c = pts.get(i + 1);
            long cross = (long) (b[0] - a[0]) * (c[1] - b[1]) - (long) (b[1] - a[1]) * (c[0] - b[0]);
            if (cross != 0) out.add(b);
        }
        out.add(pts.get(pts.size() - 1));
        if (out.size() <= MAX_POINTS) return out;
        List<int[]> thin = new ArrayList<>();
        double every = (out.size() - 1.0) / (MAX_POINTS - 1);
        for (int i = 0; i < MAX_POINTS; i++) thin.add(out.get((int) Math.round(i * every)));
        return thin;
    }

    // ------------------------------------------------------------------ le chemin au sol

    /** Sous les pas d'une caravane, sur une ligne où on l'a demandé, l'herbe devient un chemin. Jamais dans une ville : elle a ses rues. */
    public static void wear(MinecraftServer server, ServerLevel level, PathfinderMob mob, UUID who) {
        if (!mob.onGround()) return;
        BlockPos here = mob.blockPosition();
        BlockPos last = WORN.put(who, here);
        if (CityService.cityAt(server, level.dimension().location(), here).isPresent()) return;
        if (last == null || last.distSqr(here) > 64 * 64) last = here;
        int steps = (int) Math.max(Math.abs(here.getX() - last.getX()), Math.abs(here.getZ() - last.getZ()));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            int x = (int) Math.round(last.getX() + (here.getX() - last.getX()) * t), z = (int) Math.round(last.getZ() + (here.getZ() - last.getZ()) * t);
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) dig(level, new BlockPos(x + d[0], here.getY() - 1, z + d[1]));
        }
    }

    /** Un bloc de terre ou d'herbe, à l'air libre, devient un chemin ; ce qui pousse dessus s'en va. */
    private static void dig(ServerLevel level, BlockPos pos) {
        // Le sol peut être un cran plus haut ou plus bas d'un côté à l'autre du chemin.
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos p = pos.offset(0, dy, 0);
            if (!level.isLoaded(p)) return;
            BlockState st = level.getBlockState(p);
            if (!(st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM))) continue;
            BlockState above = level.getBlockState(p.above());
            if (!above.isAir() && !above.canBeReplaced()) return;
            if (!above.isAir()) level.removeBlock(p.above(), false);
            level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), 3);
            return;
        }
    }
}
