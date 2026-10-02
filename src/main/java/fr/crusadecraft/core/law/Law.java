package fr.crusadecraft.core.law;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Les lois du royaume. Chacune a des niveaux, du plus doux au plus fort ; certains s'ouvrent par une
 * recherche de gouvernance de l'Université de la capitale.
 */
public enum Law {
    FISCALITE(1, level("legere", null), level("normale", null), level("lourde", null)),
    AUTORITE(1, level("aucune", null), level("limitee", null), level("elevee", Research.CHANCELLERIE),
            level("absolue", Research.AUTORITE_ROYALE)),
    CODE_CHEVALERESQUE(0, level("aucun", null), level("proclame", Research.CHEVALERIE)),
    JUSTICE_ROYALE(0, level("coutumiere", null), level("royale", Research.CHANCELLERIE));

    /** Un niveau de loi et la recherche qui l'ouvre ({@code null} : toujours ouvert). */
    public record Level(String id, ResourceLocation research) {}

    private final int defaultLevel;
    private final List<Level> levels;

    Law(int defaultLevel, Level... levels) {
        this.defaultLevel = defaultLevel;
        this.levels = List.of(levels);
    }

    private static Level level(String id, ResourceLocation research) {
        return new Level(id, research);
    }

    public int defaultLevel() { return defaultLevel; }
    public List<Level> levels() { return levels; }

    public int clamp(int level) {
        return Math.max(0, Math.min(levels.size() - 1, level));
    }

    public String key() {
        return "crusadecraft.law." + name().toLowerCase(Locale.ROOT);
    }

    public String levelKey(int level) {
        return key() + "." + levels.get(clamp(level)).id();
    }

    public static Law byName(String name) {
        try {
            return Law.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Les effets de recherche de la branche « Gouvernance », déclarés dans notre datapack. */
    public static final class Research {
        public static final ResourceLocation FISCALITE = id("fiscalite");
        public static final ResourceLocation CHANCELLERIE = id("chancellerie");
        public static final ResourceLocation CHEVALERIE = id("chevalerie");
        public static final ResourceLocation AUTORITE_ROYALE = id("autoriteroyale");

        private static ResourceLocation id(String path) {
            return ResourceLocation.tryParse("crusadecraft:effects/" + path);
        }

        private Research() {}
    }
}
