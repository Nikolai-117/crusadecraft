package fr.crusadecraft.core.world;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repère les villages autour d'un point <b>sans générer ni charger aucun chunk</b>, comme la première étape
 * de {@code /locate} : l'algorithme de placement de Minecraft donne les emplacements possibles, la biome
 * écarte ceux qui ne conviennent pas, puis la vérification de structure lit les chunks déjà enregistrés
 * (village confirmé) ou prévoit le village d'une région jamais visitée.
 */
public final class VillageScanner {

    private VillageScanner() {}

    public static List<WorldPlanner.Candidate> scan(ServerLevel level, int cx, int cz, int radius) {
        List<WorldPlanner.Candidate> out = new ArrayList<>();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Optional<HolderSet.Named<Structure>> villages = registry.getTag(StructureTags.VILLAGE);
        if (villages.isEmpty()) return out;
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        Set<RandomSpreadStructurePlacement> placements = new LinkedHashSet<>();
        for (Holder<Structure> village : villages.get()) {
            for (StructurePlacement p : state.getPlacementsForStructure(village)) {
                if (p instanceof RandomSpreadStructurePlacement spread) placements.add(spread);
            }
        }
        Set<Long> seen = new java.util.HashSet<>();
        for (RandomSpreadStructurePlacement placement : placements) {
            int spacing = placement.spacing();
            int minRegionX = Math.floorDiv((cx - radius) >> 4, spacing), maxRegionX = Math.floorDiv((cx + radius) >> 4, spacing);
            int minRegionZ = Math.floorDiv((cz - radius) >> 4, spacing), maxRegionZ = Math.floorDiv((cz + radius) >> 4, spacing);
            for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
                    if (!seen.add(chunk.toLong()) || !placement.isStructureChunk(state, chunk.x, chunk.z)) continue;
                    int bx = chunk.getMiddleBlockX(), bz = chunk.getMiddleBlockZ();
                    if (Math.hypot(bx - cx, bz - cz) > radius) continue;
                    int y = generator.getBaseHeight(bx, bz, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
                    Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(bz), randomState.sampler());
                    for (Holder<Structure> village : villages.get()) {
                        if (!village.value().biomes().contains(biome)) continue;
                        StructureCheckResult result = level.structureManager().checkStructurePresence(chunk, village.value(), false);
                        if (result == StructureCheckResult.START_PRESENT) {
                            out.add(new WorldPlanner.Candidate(bx, y, bz, AiProvince.Anchor.VILLAGE));
                        } else if (result == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                            out.add(new WorldPlanner.Candidate(bx, y, bz, AiProvince.Anchor.VILLAGE_PREDICTED));
                        }
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Terre ferme : hors des océans et des rivières, et pas d'eau au-dessus du sol. */
    public static WorldPlanner.LandProbe landProbe(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        return (x, z) -> {
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            if (surface > floor) return null;
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                    QuartPos.fromBlock(z), randomState.sampler());
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)) return null;
            return surface;
        };
    }
}
