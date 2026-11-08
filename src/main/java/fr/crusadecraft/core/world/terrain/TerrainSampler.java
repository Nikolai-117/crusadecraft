package fr.crusadecraft.core.world.terrain;

import fr.crusadecraft.core.world.terrain.TerrainPalette.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.Tags;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.Locale;

/**
 * Sonde le monde pour la carte en terrain (P18b).
 *
 * <p>{@link #estimate} lit ce que le générateur <b>prévoit</b> en un point, sans générer ni charger aucun chunk : le
 * « niveau de surface préliminaire » que Minecraft calcule lui-même avant de creuser les grottes et de remplir les
 * nappes (une seule fonction de densité, de 8 blocs en 8 blocs), puis le biome, comme {@code /locate} et le repérage
 * des villages. Ni les constructions ni les structures n'y figurent : c'est une estimation, faite hors du fil du
 * serveur. Lire la colonne entière coûtait 4 ms par point, presque tout en calcul des nappes d'eau souterraines.
 *
 * <p>{@link #explored} lit au contraire un chunk <b>déjà chargé</b> : la vraie couleur de ce qui est en surface,
 * comme une carte papier.
 */
public final class TerrainSampler {
    private TerrainSampler() {}

    /** Ce qu'on retient d'un point : sa teinte (sans ombre) et son altitude. */
    public record Sample(int color, int height) {}

    /** Le genre de paysage d'un biome, par ses étiquettes (celles de Minecraft et de Forge, que suivent les mods). */
    public static Kind classify(Holder<Biome> biome) {
        String path = biome.unwrapKey().map(k -> k.location().getPath().toLowerCase(Locale.ROOT)).orElse("");
        float temperature = biome.value().getBaseTemperature();
        if (biome.is(BiomeTags.IS_NETHER)) return Kind.NETHER;
        if (biome.is(BiomeTags.IS_END)) return path.contains("void") ? Kind.VOID : Kind.END;
        if (biome.is(BiomeTags.IS_DEEP_OCEAN)) return temperature < 0.15f ? Kind.FROZEN_OCEAN : Kind.DEEP_OCEAN;
        if (biome.is(BiomeTags.IS_OCEAN)) return temperature < 0.15f || path.contains("frozen") ? Kind.FROZEN_OCEAN : Kind.OCEAN;
        if (biome.is(BiomeTags.IS_RIVER)) return Kind.RIVER;
        if (biome.is(BiomeTags.IS_BEACH)) return temperature < 0.15f ? Kind.SNOWY : Kind.BEACH;
        if (biome.is(BiomeTags.IS_BADLANDS)) return Kind.BADLANDS;
        if (biome.is(BiomeTags.IS_JUNGLE)) return Kind.JUNGLE;
        if (biome.is(BiomeTags.IS_SAVANNA)) return Kind.SAVANNA;
        if (path.contains("mangrove")) return Kind.MANGROVE;
        if (path.contains("swamp") || path.contains("marsh") || path.contains("bog") || biome.is(Tags.Biomes.IS_SWAMP)) return Kind.SWAMP;
        if (path.contains("mushroom") || biome.is(Tags.Biomes.IS_MUSHROOM)) return Kind.MUSHROOM;
        if (path.contains("cherry")) return Kind.CHERRY;
        if (path.contains("peak") || biome.is(Tags.Biomes.IS_PEAK)) return Kind.PEAK;
        if (biome.is(BiomeTags.IS_TAIGA)) return temperature < 0.15f ? Kind.SNOWY_TAIGA : Kind.TAIGA;
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_MOUNTAIN)) return temperature < 0.15f ? Kind.PEAK : Kind.MOUNTAIN;
        if (path.contains("desert") || biome.is(Tags.Biomes.IS_DESERT)) return Kind.DESERT;
        if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || biome.is(Tags.Biomes.IS_SNOWY)) return Kind.SNOWY;
        if (biome.is(BiomeTags.IS_FOREST)) {
            if (path.contains("dark") || path.contains("roofed")) return Kind.DARK_FOREST;
            if (path.contains("birch")) return Kind.BIRCH_FOREST;
            return Kind.FOREST;
        }
        if (biome.is(Tags.Biomes.IS_CONIFEROUS)) return Kind.TAIGA;
        if (path.contains("meadow") || path.contains("flower")) return Kind.MEADOW;
        if (path.contains("windswept") || path.contains("hill") || path.contains("highland") || biome.is(Tags.Biomes.IS_SLOPE)) return Kind.HILLS;
        if (path.contains("forest") || path.contains("wood") || path.contains("grove") || biome.is(Tags.Biomes.IS_DENSE)) return Kind.FOREST;
        if (path.contains("plain") || path.contains("field") || path.contains("prairie") || biome.is(Tags.Biomes.IS_PLAINS)) return Kind.PLAINS;
        // Un biome de mod sans étiquette parlante : sa température décide.
        if (temperature >= 1.5f) return Kind.DESERT;
        if (temperature < 0.15f) return Kind.SNOWY;
        return Kind.PLAINS;
    }

    /**
     * Ce que le générateur prévoit en ce point, sans générer de chunk. Appelé hors du fil du serveur : n'utilise que le
     * générateur, ses graines et les hauteurs du monde, comme les fils de génération eux-mêmes.
     */
    public static Sample estimate(ServerLevel level, ChunkGenerator generator, RandomState random, int x, int z) {
        if (generator instanceof NoiseBasedChunkGenerator noise) return estimateNoise(level, noise, random, x, z);
        // Un autre générateur (monde plat, mod) : sa colonne, qui ne coûte rien chez lui.
        NoiseColumn column = generator.getBaseColumn(x, z, level, random);
        int min = level.getMinBuildHeight(), top = level.getMaxBuildHeight() - 1;
        int surface = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
        for (int y = top; y >= min; y--) {
            BlockState s = column.getBlock(y);
            if (s.isAir()) continue;
            if (surface == Integer.MIN_VALUE) surface = y;
            if (s.getFluidState().isEmpty()) {
                floor = y;
                break;
            }
        }
        if (surface == Integer.MIN_VALUE) {
            // Rien du tout : le vide de l'End.
            return new Sample(Kind.VOID.color, min);
        }
        if (floor == Integer.MIN_VALUE) floor = min;
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z),
                random.sampler());
        Kind kind = classify(biome);
        int depth = surface - floor;
        return new Sample(TerrainPalette.color(kind, floor, depth, generator.getSeaLevel()), depth > 0 ? floor : surface);
    }

    /** Le seuil de Minecraft pour sa surface préliminaire ({@code NoiseChunk.computePreliminarySurfaceLevel}). */
    private static final double SURFACE_DENSITY = 0.390625;

    /**
     * La surface préliminaire, comme Minecraft la cherche (du haut vers le bas, de cellule en cellule), puis affinée
     * entre les deux dernières mesures ; sous le niveau de la mer, de l'eau.
     */
    static Sample estimateNoise(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
        NoiseSettings ns = generator.generatorSettings().value().noiseSettings();
        DensityFunction density = random.router().initialDensityWithoutJaggedness();
        int cell = ns.getCellHeight(), min = ns.minY();
        double above = Double.NaN;
        double surface = Double.NaN;
        for (int y = min + ns.height(); y >= min; y -= cell) {
            double d = density.compute(new DensityFunction.SinglePointContext(x, y, z));
            if (d > SURFACE_DENSITY) {
                surface = Double.isNaN(above) ? y : y + (d - SURFACE_DENSITY) / (d - above) * cell;
                break;
            }
            above = d;
        }
        if (Double.isNaN(surface)) return new Sample(Kind.VOID.color, min);
        int ground = (int) Math.floor(surface);
        int sea = generator.getSeaLevel();
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(ground, sea - 1)),
                QuartPos.fromBlock(z), random.sampler());
        Kind kind = classify(biome);
        // L'eau : dans une mer ou une rivière dès qu'on passe sous la surface ; ailleurs seulement dans une vraie cuvette.
        // La surface préliminaire flotte de quelques blocs autour du niveau de la mer, et des plaines basses se seraient
        // sinon criblées de flaques.
        int below = sea - 1 - ground;
        boolean water = !level.dimensionType().hasCeiling() && (kind.water() ? below > 0 : below >= 4);
        // Une terre émergée dans une mer (îlot, haut-fond) ou au bord d'une rivière n'est pas de l'eau.
        if (!water && kind.water()) kind = kind == Kind.RIVER ? Kind.PLAINS : Kind.BEACH;
        return new Sample(TerrainPalette.color(kind, ground, water ? below : 0, sea), ground);
    }

    /** Les seize points relevés dans un chunk : une grille de quatre sur quatre, au milieu de ses carrés de quatre blocs. */
    public static final int[] POINTS = {2, 6, 10, 14};

    /**
     * La vraie surface d'un chunk chargé : la moyenne de seize points, avec leur couleur de carte et l'eau assombrie par
     * sa profondeur ; {@code null} si le chunk n'est pas en mémoire (rien n'est chargé pour le lire).
     */
    public static Sample explored(ServerLevel level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk == null ? null : explored(level, chunk);
    }

    public static Sample explored(ServerLevel level, LevelChunk chunk) {
        Mean mean = new Mean();
        for (int dx : POINTS) {
            for (int dz : POINTS) {
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, dx, dz);
                if (surface < level.getMinBuildHeight()) continue;
                BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + dx, surface, chunk.getPos().getMinBlockZ() + dz);
                mean.add(chunk.getBlockState(pos).getMapColor(level, pos), surface, floor, level.getSeaLevel());
            }
        }
        return mean.sample();
    }

    // ------------------------------------------------------------------ un chunk lu sur le disque (P62)

    /** Le lecteur des blocs d'une section, celui de Minecraft lui-même (sa palette, ses bits). */
    private static final Codec<PalettedContainer<BlockState>> BLOCKS = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    /** Les chunks enregistrés avant la 1.18 ont une autre forme : on les laisse au relevé en jeu. */
    private static final int FIRST_DATA_VERSION = 2860;

    /**
     * La vraie surface d'un chunk tel qu'il est enregistré (P62), lue dans ses données sans le charger dans le monde : ses
     * cartes des hauteurs, puis le bloc du dessus, section par section. Les mêmes seize points et les mêmes couleurs
     * qu'{@link #explored(ServerLevel, LevelChunk)}. {@code null} pour un chunk inachevé ou illisible. Sans état : sûr hors
     * du fil du serveur.
     */
    public static Sample fromSaved(ServerLevel level, CompoundTag tag) {
        if (tag == null || tag.getInt("DataVersion") < FIRST_DATA_VERSION) return null;
        String status = tag.getString("Status");
        if (!status.equals("full") && !status.equals("minecraft:full")) return null;
        CompoundTag maps = tag.getCompound("Heightmaps");
        if (!maps.contains("WORLD_SURFACE", Tag.TAG_LONG_ARRAY)) return null;
        int min = level.getMinBuildHeight();
        int bits = Mth.ceillog2(level.getHeight() + 1);
        SimpleBitStorage surfaces, floors;
        try {
            surfaces = new SimpleBitStorage(bits, 256, maps.getLongArray("WORLD_SURFACE"));
            floors = maps.contains("OCEAN_FLOOR", Tag.TAG_LONG_ARRAY) ? new SimpleBitStorage(bits, 256, maps.getLongArray("OCEAN_FLOOR")) : surfaces;
        } catch (RuntimeException e) {
            return null;
        }
        ListTag sections = tag.getList("sections", Tag.TAG_COMPOUND);
        Int2ObjectOpenHashMap<PalettedContainer<BlockState>> read = new Int2ObjectOpenHashMap<>();
        Mean mean = new Mean();
        for (int dx : POINTS) {
            for (int dz : POINTS) {
                int index = dx + dz * 16;
                // La carte des hauteurs garde le premier bloc libre, compté depuis le fond du monde.
                int surface = surfaces.get(index) + min - 1, floor = floors.get(index) + min - 1;
                if (surface < min) continue;
                PalettedContainer<BlockState> blocks = section(sections, read, surface >> 4);
                if (blocks == null) continue;
                BlockState state = blocks.get(dx, surface & 15, dz);
                mean.add(state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), surface, floor, level.getSeaLevel());
            }
        }
        return mean.sample();
    }

    private static PalettedContainer<BlockState> section(ListTag sections, Int2ObjectOpenHashMap<PalettedContainer<BlockState>> read, int y) {
        if (read.containsKey(y)) return read.get(y);
        PalettedContainer<BlockState> found = null;
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);
            if (section.getByte("Y") != y || !section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            found = BLOCKS.parse(NbtOps.INSTANCE, section.getCompound("block_states")).result().orElse(null);
            break;
        }
        read.put(y, found);
        return found;
    }

    /** La moyenne des points d'un chunk : leur couleur de carte, l'eau teintée par sa profondeur. */
    private static final class Mean {
        private int r, g, b, h, n;

        void add(MapColor mapColor, int surface, int floor, int seaLevel) {
            int rgb = mapColor == MapColor.NONE ? Kind.VOID.color : mapColor.col;
            boolean water = mapColor == MapColor.WATER;
            if (water) rgb = TerrainPalette.color(Kind.OCEAN, floor, Math.max(1, surface - floor), seaLevel);
            r += (rgb >> 16) & 0xFF;
            g += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
            h += water ? floor : surface;
            n++;
        }

        Sample sample() {
            return n == 0 ? null : new Sample(((r / n) << 16) | ((g / n) << 8) | (b / n), h / n);
        }
    }
}
