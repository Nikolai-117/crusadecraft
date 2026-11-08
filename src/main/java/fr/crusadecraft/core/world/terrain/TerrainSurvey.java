package fr.crusadecraft.core.world.terrain;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Le relevé du monde tel qu'il est (P62) : chaque chunk déjà généré, lu sur le disque sans être chargé, donne sa vraie
 * couleur de surface à la carte. Ainsi la carte montre le monde où l'on joue, quel qu'il soit (généré, prégénéré, carte
 * téléchargée), et plus seulement ce que le générateur prévoit loin des joueurs.
 *
 * <p>Il tourne une fois par démarrage, dans un fil à part et sans hâte (un nombre réglable de chunks par seconde, une
 * pause quand le serveur peine). Un chunk déjà relevé n'est pas relu : ceux qui changent ensuite sont relevés à leur
 * sauvegarde. Les chunks se lisent par le lecteur de Minecraft lui-même ({@code ChunkMap.read}), qui sert aussi ce qui
 * n'est pas encore écrit ; les fichiers de région ne sont qu'ouverts en lecture, pour savoir quels chunks ils contiennent.
 */
public final class TerrainSurvey {
    /** Ce qu'un relevé a trouvé, remis au fil du serveur. */
    record Found(ResourceKey<Level> dim, long chunk, long packed) {}

    static final ConcurrentLinkedQueue<Found> FOUND = new ConcurrentLinkedQueue<>();
    private static volatile MinecraftServer running;
    private static Thread worker;
    private static volatile int total, done;

    private TerrainSurvey() {}

    /** Lance le relevé de ce démarrage, avec ce que l'on connaît déjà de chaque monde. Sur le fil du serveur. */
    static synchronized void start(MinecraftServer server, Map<ResourceKey<Level>, LongSet> known) {
        if (running == server) return;
        running = server;
        total = 0;
        done = 0;
        worker = new Thread(() -> work(server, known), "CrusadeCraft-Releve");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    static synchronized void stop() {
        running = null;
        if (worker != null) worker.interrupt();
        worker = null;
        FOUND.clear();
    }

    /** Pour les bancs d'essai et la commande de performance : chunks lus, sur combien. */
    public static int done() {
        return done;
    }

    public static int total() {
        return total;
    }

    public static boolean active() {
        return running != null && worker != null && worker.isAlive();
    }

    private static void work(MinecraftServer server, Map<ResourceKey<Level>, LongSet> known) {
        // Ce qui reste à lire, monde par monde, les régions les plus proches du point d'apparition d'abord.
        Map<ServerLevel, LongArrayList> todo = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimensionType().hasCeiling()) continue;
            LongArrayList chunks = new LongArrayList();
            LongSet seen = known.getOrDefault(level.dimension(), new LongOpenHashSet());
            for (long chunk : chunksOf(server, level)) if (!seen.contains(chunk)) chunks.add(chunk);
            if (!chunks.isEmpty()) todo.put(level, chunks);
            total += chunks.size();
        }
        if (total == 0) return;
        CrusadeCraft.LOGGER.info("CrusadeCraft : relevé du monde pour la carte, {} chunks à lire", total);
        long second = System.currentTimeMillis();
        int inSecond = 0;
        for (Map.Entry<ServerLevel, LongArrayList> e : todo.entrySet()) {
            ServerLevel level = e.getKey();
            for (long chunk : e.getValue()) {
                if (running != server || Thread.currentThread().isInterrupted()) return;
                int rate = CrusadeConfig.SERVER.worldSurvey.get();
                if (rate <= 0) return;
                try {
                    // Un serveur qui peine passe avant la carte.
                    while (server.getAverageTickTime() > 45f && running == server) Thread.sleep(1000);
                    if (inSecond >= rate) {
                        long wait = 1000 - (System.currentTimeMillis() - second);
                        if (wait > 0) Thread.sleep(wait);
                    }
                } catch (InterruptedException ie) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - second >= 1000) {
                    second = now;
                    inSecond = 0;
                }
                inSecond++;
                done++;
                TerrainSampler.Sample s = read(level, chunk);
                if (s != null) {
                    long packed = ((long) (s.color() & 0xFFFFFF) << 16) | (s.height() & 0xFFFFL);
                    FOUND.add(new Found(level.dimension(), chunk, packed));
                }
            }
        }
        if (running == server) CrusadeCraft.LOGGER.info("CrusadeCraft : relevé du monde terminé ({} chunks)", done);
    }

    /** Lit un chunk sur le disque et en tire sa surface ; {@code null} s'il n'est pas lisible. */
    public static TerrainSampler.Sample read(ServerLevel level, long chunk) {
        try {
            Optional<CompoundTag> tag = level.getChunkSource().chunkMap.read(new ChunkPos(chunk)).get(30, TimeUnit.SECONDS);
            return tag.map(t -> TerrainSampler.fromSaved(level, t)).orElse(null);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    /** Les chunks enregistrés d'un monde, par l'en-tête de ses fichiers de région, les plus proches de l'apparition d'abord. */
    static LongArrayList chunksOf(MinecraftServer server, ServerLevel level) {
        Path folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("region");
        LongArrayList out = new LongArrayList();
        if (!Files.isDirectory(folder)) return out;
        List<int[]> regions = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.forEach(p -> {
                int[] rxz = regionOf(p.getFileName().toString());
                if (rxz != null) regions.add(rxz);
            });
        } catch (IOException e) {
            return out;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int sx = spawn.getX() >> 9, sz = spawn.getZ() >> 9;
        regions.sort(Comparator.comparingLong(r -> (long) (r[0] - sx) * (r[0] - sx) + (long) (r[1] - sz) * (r[1] - sz)));
        byte[] header = new byte[4096];
        for (int[] r : regions) {
            Path file = folder.resolve("r." + r[0] + "." + r[1] + ".mca");
            try {
                if (Files.size(file) < 8192) continue;
                try (InputStream in = Files.newInputStream(file)) {
                    if (in.readNBytes(header, 0, 4096) < 4096) continue;
                }
            } catch (IOException e) {
                continue;
            }
            out.addAll(chunksIn(r[0], r[1], header));
        }
        return out;
    }

    /** {rx, rz} d'un nom de fichier « r.X.Z.mca », ou {@code null}. */
    static int[] regionOf(String name) {
        if (!name.startsWith("r.") || !name.endsWith(".mca")) return null;
        String[] parts = name.split("\\.");
        if (parts.length != 4) return null;
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Les chunks présents dans une région, d'après sa table des emplacements : 1024 entrées de quatre octets, dans l'ordre
     * x + 32 z ; une entrée nulle est un chunk jamais écrit.
     */
    static LongArrayList chunksIn(int rx, int rz, byte[] header) {
        LongArrayList out = new LongArrayList();
        for (int i = 0; i < 1024; i++) {
            int at = i * 4;
            int entry = ((header[at] & 0xFF) << 24) | ((header[at + 1] & 0xFF) << 16) | ((header[at + 2] & 0xFF) << 8) | (header[at + 3] & 0xFF);
            if (entry == 0) continue;
            out.add(ChunkPos.asLong((rx << 5) + (i & 31), (rz << 5) + (i >> 5)));
        }
        return out;
    }
}
