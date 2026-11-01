package fr.crusadecraft.core.build.plan;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Le style d'une nation (P34) : les packs de MineColonies et de Byzantine où elle prend ses bâtiments, par ordre de
 * préférence, et la matière de ses rues. Un style dont aucun pack n'est installé retombe sur les plans dessinés par
 * le code. Ce sont des étagères, pas du lore : rien ne dit qui habite quoi.
 */
public enum NationStyle {
    EUROPEENNE(List.of("medievaloak", "medievalspruce", "medievaldarkoak", "medievalbirch", "colonial")),
    CHINOISE(List.of("pagoda")),
    JAPONAISE(List.of("shogun", "pagoda")),
    EGYPTIENNE(List.of("nile", "desertoasis", "sandstone")),
    ROMAINE(List.of("byzantinefor120", "byzantine", "ancientathens")),
    NORDIQUE(List.of("nordicspruce", "nordic", "caledonia"));

    private final List<String> packs;

    NationStyle(List<String> packs) {
        this.packs = packs;
    }

    /** Les packs voulus, noms ramenés par {@link PlanRef#normalize}. */
    public List<String> packs() {
        return packs;
    }

    /** La matière des rues (lue à la demande : le style se charge sans les blocs du jeu, dans les tests). */
    public BlockState street() {
        return switch (this) {
            case EUROPEENNE -> Blocks.DIRT_PATH.defaultBlockState();
            case CHINOISE -> Blocks.STONE_BRICKS.defaultBlockState();
            case JAPONAISE -> Blocks.GRAVEL.defaultBlockState();
            case EGYPTIENNE -> Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            case ROMAINE -> Blocks.POLISHED_ANDESITE.defaultBlockState();
            case NORDIQUE -> Blocks.COARSE_DIRT.defaultBlockState();
        };
    }

    /** Le plan de ses villes, d'après l'urbanisme attesté de la civilisation (voir {@link TownLayout}). */
    public TownLayout.Pattern pattern() {
        return switch (this) {
            case EUROPEENNE -> TownLayout.Pattern.MARCHE;
            case CHINOISE -> TownLayout.Pattern.KAOGONG;
            case JAPONAISE -> TownLayout.Pattern.JOBO;
            case EGYPTIENNE -> TownLayout.Pattern.KAHUN;
            case ROMAINE -> TownLayout.Pattern.CASTRUM;
            case NORDIQUE -> TownLayout.Pattern.TRELLEBORG;
        };
    }

    /**
     * Une enceinte autour de ses villes de cette taille ? Décidé avec Romain (P36) : aucune pour un hameau ou un
     * village, une palissade pour un bourg, la grande muraille du thème pour une ville ou une capitale.
     */
    public boolean walled(TownSize size) {
        return size.walls() != TownSize.Walls.NONE;
    }

    /** La matière de l'enceinte : pierre, briques grises, briques crues du Nil, levée de terre viking. */
    public BlockState wall() {
        return switch (this) {
            case EUROPEENNE -> Blocks.COBBLESTONE.defaultBlockState();
            case CHINOISE -> Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            case EGYPTIENNE -> Blocks.MUD_BRICKS.defaultBlockState();
            case NORDIQUE -> Blocks.DIRT.defaultBlockState();
            default -> Blocks.STONE_BRICKS.defaultBlockState();
        };
    }

    /** Hauteur de l'enceinte : la levée de terre est basse, les murs de pierre ont des créneaux en plus. */
    public int wallHeight() {
        return this == NORDIQUE ? 3 : this == EGYPTIENNE ? 4 : 5;
    }

    public String key() {
        return "crusadecraft.style." + name().toLowerCase(Locale.ROOT);
    }

    public static NationStyle byName(String name, NationStyle fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static NationStyle byOrdinal(int i) {
        NationStyle[] all = values();
        return all[Math.floorMod(i, all.length)];
    }
}
