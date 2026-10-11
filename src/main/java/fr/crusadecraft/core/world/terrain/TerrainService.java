package fr.crusadecraft.core.world.terrain;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.TerrainPackets;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * La carte en terrain (P18b), côté serveur : prépare les tuiles que les joueurs demandent, et retient la vraie surface
 * des chunks qu'ils ont parcourus.
 *
 * <ul>
 *   <li>Les tuiles <b>estimées</b> se calculent dans un fil à part, de basse priorité : le serveur ne ralentit pas. Elles
 *   restent en mémoire et dans un cache sur disque ({@code data/crusadecraft_terrain}), qu'on peut effacer sans rien
 *   perdre : il se refait de lui-même.</li>
 *   <li>Les chunks <b>parcourus</b> sont relevés autour de chaque joueur, sur le fil du serveur, seulement s'ils sont
 *   déjà en mémoire ; leur vraie couleur remplace l'estimation à l'envoi.</li>
 * </ul>
 * Un monde à plafond (le Nether) n'a pas de carte en terrain : sa « surface » serait la roche du plafond.
 */
public final class TerrainService {
    /** Tuiles gardées en mémoire, par monde. */
    static final int MEMORY_TILES = 768;
    /** Tuiles demandées au plus d'un coup. */
    public static final int MAX_REQUEST = 64;
    /** Rayon, en chunks, du relevé autour d'un joueur ; et combien de chunks au plus par passage. */
    static final int EXPLORE_RADIUS = 5, EXPLORE_BUDGET = 96;
    private static final String FOLDER = "crusadecraft_terrain";
    private static final int FORMAT = 1;

    private record TileId(ResourceKey<Level> dim, long key) {}

    /** Une tuile à préparer : les plus grossières d'abord, puis dans l'ordre des demandes. */
    private record Job(TileId id, long seq) implements Comparable<Job> {
        @Override
        public int compareTo(Job o) {
            int byLod = Integer.compare(TerrainTile.lodOf(o.id.key), TerrainTile.lodOf(id.key));
            return byLod != 0 ? byLod : Long.compare(seq, o.seq);
        }
    }

    private static final Map<ResourceKey<Level>, LinkedHashMap<Long, TerrainTile>> TILES = new HashMap<>();
    private static final Map<TileId, Set<UUID>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> EXPLORED = new HashMap<>();
    private static final Set<ResourceKey<Level>> EXPLORED_DIRTY = new HashSet<>();
    private static final PriorityBlockingQueue<Job> QUEUE = new PriorityBlockingQueue<>();
    private static final AtomicLong SEQ = new AtomicLong();
    private static volatile MinecraftServer running;
    private static Thread worker;
    private static boolean warned;

    private TerrainService() {}

    // ------------------------------------------------------------------ demandes

    /** Un joueur demande des tuiles de sa dimension : celles qui sont prêtes partent, les autres se préparent. */
    public static void request(ServerPlayer player, int lod, long[] keys) {
        MinecraftServer server = player.server;
        start(server);
        ResourceKey<Level> dim = player.level().dimension();
        int n = 0;
        for (long key : keys) {
            if (++n > MAX_REQUEST) break;
            if (TerrainTile.lodOf(key) != lod || lod >= TerrainTile.LODS) continue;
            TileId id = new TileId(dim, key);
            TerrainTile ready = cache(dim).get(key);
            if (ready != null) {
                send(player, ready);
                continue;
            }
            Set<UUID> waiting = PENDING.get(id);
            if (waiting == null) {
                waiting = new HashSet<>();
                PENDING.put(id, waiting);
                QUEUE.add(new Job(id, SEQ.incrementAndGet()));
            }
            waiting.add(player.getUUID());
        }
    }

    private static LinkedHashMap<Long, TerrainTile> cache(ResourceKey<Level> dim) {
        return TILES.computeIfAbsent(dim, d -> new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, TerrainTile> eldest) {
                return size() > MEMORY_TILES;
            }
        });
    }

    /** Une tuile vient d'être préparée (fil du serveur) : en mémoire, puis à ceux qui l'attendent. */
    private static void ready(MinecraftServer server, TileId id, TerrainTile tile) {
        if (running != server) return;
        cache(id.dim()).put(id.key(), tile);
        Set<UUID> waiting = PENDING.remove(id);
        if (waiting == null) return;
        for (UUID uuid : waiting) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null && p.level().dimension().equals(id.dim())) send(p, tile);
        }
    }

    private static void send(ServerPlayer player, TerrainTile estimate) {
        if (player instanceof FakePlayer) return;
        ServerLevel level = player.serverLevel();
        CrusadeNetwork.toPlayer(player, new TerrainPackets.Tile(level.dimension().location(), compose(level, estimate)));
    }

    /** L'estimation, où les chunks parcourus prennent leur vraie couleur. */
    public static TerrainTile compose(ServerLevel level, TerrainTile estimate) {
        Long2LongOpenHashMap explored = explored(level);
        if (explored.isEmpty()) return estimate;
        int[] colors = estimate.colors().clone();
        short[] heights = estimate.heights().clone();
        int lod = estimate.lod();
        for (int lz = 0; lz < TerrainTile.SIZE; lz++) {
            int bz = TerrainTile.blockOf(estimate.tz() * TerrainTile.SIZE + lz, lod);
            for (int lx = 0; lx < TerrainTile.SIZE; lx++) {
                int bx = TerrainTile.blockOf(estimate.tx() * TerrainTile.SIZE + lx, lod);
                long seen = explored.getOrDefault(ChunkPos.asLong(bx >> 4, bz >> 4), Long.MIN_VALUE);
                if (seen == Long.MIN_VALUE) continue;
                int i = estimate.index(lx, lz);
                colors[i] = 0xFF000000 | (int) (seen >>> 16);
                heights[i] = (short) seen;
            }
        }
        return new TerrainTile(lod, estimate.tx(), estimate.tz(), colors, heights);
    }

    // ------------------------------------------------------------------ calcul (fil à part)

    private static synchronized void start(MinecraftServer server) {
        if (running == server && worker != null && worker.isAlive()) return;
        running = server;
        worker = new Thread(() -> work(server), "CrusadeCraft-Terrain");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private static void work(MinecraftServer server) {
        while (running == server) {
            Job job;
            try {
                job = QUEUE.take();
            } catch (InterruptedException e) {
                return;
            }
            if (running != server) return;
            TileId id = job.id();
            TerrainTile tile;
            try {
                tile = load(server, id);
                if (tile == null) {
                    tile = compute(server.getLevel(id.dim()), TerrainTile.lodOf(id.key()), TerrainTile.txOf(id.key()), TerrainTile.tzOf(id.key()));
                    store(server, id, tile);
                }
            } catch (Throwable t) {
                // Un générateur de mod qui n'aime pas qu'on l'interroge hors de ses fils : la tuile reste inconnue.
                if (!warned) {
                    warned = true;
                    CrusadeCraft.LOGGER.warn("CrusadeCraft : carte en terrain indisponible pour {} ({}) — la carte garde son parchemin là",
                            id.dim().location(), t.toString());
                }
                tile = blank(TerrainTile.lodOf(id.key()), TerrainTile.txOf(id.key()), TerrainTile.tzOf(id.key()));
            }
            TerrainTile done = tile;
            server.execute(() -> ready(server, id, done));
        }
    }

    static TerrainTile blank(int lod, int tx, int tz) {
        return new TerrainTile(lod, tx, tz, new int[TerrainTile.SIZE * TerrainTile.SIZE], new short[TerrainTile.SIZE * TerrainTile.SIZE]);
    }

    /** Calcule une tuile entière : un point au milieu de chaque case, sans générer de chunk. */
    public static TerrainTile compute(ServerLevel level, int lod, int tx, int tz) {
        if (level == null || level.dimensionType().hasCeiling()) return blank(lod, tx, tz);
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int[] colors = new int[TerrainTile.SIZE * TerrainTile.SIZE];
        short[] heights = new short[TerrainTile.SIZE * TerrainTile.SIZE];
        for (int lz = 0; lz < TerrainTile.SIZE; lz++) {
            int z = TerrainTile.blockOf(tz * TerrainTile.SIZE + lz, lod);
            for (int lx = 0; lx < TerrainTile.SIZE; lx++) {
                if (running != null && running.isStopped()) return blank(lod, tx, tz);
                int x = TerrainTile.blockOf(tx * TerrainTile.SIZE + lx, lod);
                TerrainSampler.Sample s = TerrainSampler.estimate(level, generator, random, x, z);
                int i = lz * TerrainTile.SIZE + lx;
                colors[i] = 0xFF000000 | s.color();
                heights[i] = (short) s.height();
            }
        }
        return new TerrainTile(lod, tx, tz, colors, heights);
    }

    // ------------------------------------------------------------------ chunks parcourus (fil du serveur)

    /** Relève la vraie surface des chunks chargés autour de chaque joueur ; ceux qu'il foule sont relus à chaque fois. */
    public static void tick(MinecraftServer server) {
        int budget = EXPLORE_BUDGET;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            if (level.dimensionType().hasCeiling()) continue;
            Long2LongOpenHashMap map = explored(level);
            int pcx = player.chunkPosition().x, pcz = player.chunkPosition().z;
            for (int dx = -EXPLORE_RADIUS; dx <= EXPLORE_RADIUS && budget > 0; dx++) {
                for (int dz = -EXPLORE_RADIUS; dz <= EXPLORE_RADIUS && budget > 0; dz++) {
                    long key = ChunkPos.asLong(pcx + dx, pcz + dz);
                    boolean near = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                    if (!near && map.containsKey(key)) continue;
                    if (exploreNow(level, pcx + dx, pcz + dz)) budget--;
                }
            }
        }
        if (server.getTickCount() % 6000 == 0) saveExplored(server);
    }

    /** Relève un chunk s'il est en mémoire ; vrai s'il l'a été. */
    public static boolean exploreNow(ServerLevel level, int chunkX, int chunkZ) {
        TerrainSampler.Sample s = TerrainSampler.explored(level, chunkX, chunkZ);
        if (s == null) return false;
        long packed = ((long) (s.color() & 0xFFFFFF) << 16) | (s.height() & 0xFFFFL);
        long old = explored(level).put(ChunkPos.asLong(chunkX, chunkZ), packed);
        if (old != packed) EXPLORED_DIRTY.add(level.dimension());
        return true;
    }

    /** Les chunks parcourus d'un monde : chunk → couleur (24 bits) et altitude (16 bits). */
    static Long2LongOpenHashMap explored(ServerLevel level) {
        return EXPLORED.computeIfAbsent(level.dimension(), d -> readExplored(level.getServer(), d));
    }

    // ------------------------------------------------------------------ disque

    private static Path folder(MinecraftServer server, ResourceKey<Level> dim) {
        ResourceLocation id = dim.location();
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FOLDER).resolve("v" + FORMAT)
                .resolve(id.getNamespace()).resolve(id.getPath().replace('/', '_'));
    }

    private static Path tileFile(MinecraftServer server, TileId id) {
        long k = id.key();
        return folder(server, id.dim()).resolve(String.valueOf(TerrainTile.lodOf(k)))
                .resolve(TerrainTile.txOf(k) + "." + TerrainTile.tzOf(k) + ".bin");
    }

    private static TerrainTile load(MinecraftServer server, TileId id) {
        Path file = tileFile(server, id);
        if (!Files.isRegularFile(file)) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            TerrainTile t = TerrainTile.read(in);
            return t.key() == id.key() ? t : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void store(MinecraftServer server, TileId id, TerrainTile tile) {
        Path file = tileFile(server, id);
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
                tile.write(out);
            }
        } catch (IOException e) {
            // Un cache : s'il ne s'écrit pas, la tuile se refera.
        }
    }

    private static Long2LongOpenHashMap readExplored(MinecraftServer server, ResourceKey<Level> dim) {
        Long2LongOpenHashMap map = new Long2LongOpenHashMap();
        Path file = folder(server, dim).resolve("parcouru.bin");
        if (!Files.isRegularFile(file)) return map;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) map.put(in.readLong(), in.readLong());
        } catch (IOException | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : relevé des chunks parcourus illisible pour {}, il se refera", dim.location());
            map.clear();
        }
        return map;
    }

    /** Écrit les chunks parcourus des mondes qui ont changé. */
    public static void saveExplored(MinecraftServer server) {
        for (ResourceKey<Level> dim : new HashSet<>(EXPLORED_DIRTY)) {
            Long2LongOpenHashMap map = EXPLORED.get(dim);
            if (map == null) continue;
            Path file = folder(server, dim).resolve("parcouru.bin");
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling("parcouru.bin.tmp");
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
                    out.writeInt(map.size());
                    for (Long2LongMap.Entry e : map.long2LongEntrySet()) {
                        out.writeLong(e.getLongKey());
                        out.writeLong(e.getLongValue());
                    }
                }
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                EXPLORED_DIRTY.remove(dim);
            } catch (IOException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : relevé des chunks parcourus non écrit pour {}", dim.location(), e);
            }
        }
    }

    public static void onLogout(UUID player) {
        for (Set<UUID> waiting : PENDING.values()) waiting.remove(player);
    }

    public static void onServerStopping(MinecraftServer server) {
        saveExplored(server);
    }

    public static synchronized void onServerStopped() {
        running = null;
        if (worker != null) worker.interrupt();
        worker = null;
        QUEUE.clear();
        TILES.clear();
        PENDING.clear();
        EXPLORED.clear();
        EXPLORED_DIRTY.clear();
        warned = false;
    }

    /** Pour les bancs d'essai : combien de tuiles attendent encore d'être préparées. */
    public static int pending() {
        return PENDING.size();
    }

    /** Pour les bancs d'essai : cette tuile est-elle prête en mémoire ? */
    public static boolean ready(ServerLevel level, long key) {
        LinkedHashMap<Long, TerrainTile> tiles = TILES.get(level.dimension());
        return tiles != null && tiles.containsKey(key);
    }
}
