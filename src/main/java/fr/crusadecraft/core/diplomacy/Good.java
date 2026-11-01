package fr.crusadecraft.core.diplomacy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.Tags;

import java.util.Locale;

/**
 * La marchandise d'un royaume (P29) : ce que produit le pays autour de sa capitale, lu dans le biome. Une route entre
 * deux royaumes qui ne produisent pas la même chose rapporte davantage, et sa caravane porte les deux.
 */
public enum Good {
    BLE, BOIS, FER, SEL, POISSON, VIN, EPICES, LAINE, FOURRURES, PIERRE;

    private static boolean warned;

    public String key() {
        return "crusadecraft.good." + name().toLowerCase(Locale.ROOT);
    }

    /** Ce qu'on sait du pays, en logique pure. */
    public record Land(boolean water, boolean mountain, boolean forest, boolean jungle, boolean savanna, boolean badlands, boolean dry,
                       boolean cold, boolean warm) {}

    /** La marchandise d'un pays : l'eau d'abord, puis le relief, la forêt, le climat ; le blé à défaut. */
    public static Good of(Land land) {
        if (land.water()) return POISSON;
        if (land.badlands()) return PIERRE;
        if (land.mountain()) return FER;
        if (land.jungle()) return EPICES;
        if (land.dry()) return SEL;
        if (land.cold()) return FOURRURES;
        if (land.savanna()) return LAINE;
        if (land.forest()) return BOIS;
        if (land.warm()) return VIN;
        return BLE;
    }

    /**
     * La marchandise d'un endroit du monde, d'après le biome que le générateur y met : rien n'est chargé pour le
     * savoir, la capitale d'un royaume lointain ne coûte pas un chunk. {@code null} si le paysage n'a pas pu être lu.
     */
    public static Good at(ServerLevel level, BlockPos pos) {
        Holder<Biome> b;
        try {
            b = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, pos.getY()))),
                    QuartPos.fromBlock(pos.getZ()), level.getChunkSource().randomState().sampler());
        } catch (RuntimeException e) {
            // Un générateur qui ne se laisse pas interroger ainsi : le biome du chunk s'il est chargé, le blé sinon.
            if (!warned) {
                warned = true;
                fr.crusadecraft.CrusadeCraft.LOGGER.warn("CrusadeCraft : biome illisible au générateur en {}, repli", pos, e);
            }
            if (!level.hasChunkAt(pos)) return null;
            b = level.getBiome(pos);
        }
        float temperature = b.value().getBaseTemperature();
        return of(new Land(b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_RIVER),
                b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL), b.is(BiomeTags.IS_FOREST) || b.is(BiomeTags.IS_TAIGA),
                b.is(BiomeTags.IS_JUNGLE), b.is(BiomeTags.IS_SAVANNA), b.is(BiomeTags.IS_BADLANDS),
                b.is(Tags.Biomes.IS_DESERT) || b.is(Tags.Biomes.IS_DRY_OVERWORLD), b.is(Tags.Biomes.IS_SNOWY) || temperature < 0.15f,
                temperature > 0.85f));
    }
}
