package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Un plan prêt à poser (P34), dans la même convention que les plans dessinés par le code ({@link Blueprints}) : les
 * blocs relatifs au milieu de son emprise, la couche du sol à y = 0, l'entrée tournée au sud (+z). Les fondations
 * passent en premier, puis le dégagement au-dessus, puis le bâtiment couche par couche.
 *
 * @param sizeX    largeur, entrée au sud
 * @param sizeZ    profondeur, entrée au sud
 * @param groundY  couches sous le sol (fondations du plan lui-même)
 * @param front    le côté de l'entrée dans le plan d'origine (déduit des portes), avant qu'on le tourne au sud
 * @param huts     blocs de cabane MineColonies qu'il contenait
 * @param replaced P38 : sortes de blocs remplacées par un équivalent installé
 * @param unknown  P38 : sortes de blocs sans équivalent, laissées vides
 */
public record Plan(PlanRef ref, String name, int sizeX, int sizeY, int sizeZ, int groundY, Direction front, int huts,
                   List<Blueprints.Placement> placements, int replaced, List<String> unknown) {

    public Plan(PlanRef ref, String name, int sizeX, int sizeY, int sizeZ, int groundY, Direction front, int huts,
                List<Blueprints.Placement> placements) {
        this(ref, name, sizeX, sizeY, sizeZ, groundY, front, huts, placements, 0, List.of());
    }

    /** Une emprise à partir de laquelle un fichier est une ville entière (P38), et non un bâtiment. */
    public static final int CITY = 96;

    /** Le côté le plus long de son emprise. */
    public int footprint() {
        return Math.max(sizeX, sizeZ);
    }

    /** P38 : une ville entière, venue d'un fichier (posée, elle devient une ville du royaume). */
    public boolean city() {
        return ref != null && ref.source() == PlanRef.Source.FILE && Math.min(sizeX, sizeZ) >= CITY;
    }
}
