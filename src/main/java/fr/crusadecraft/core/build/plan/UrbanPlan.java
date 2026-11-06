package fr.crusadecraft.core.build.plan;

import java.util.List;
import java.util.Map;

/**
 * Ce que le générateur a dressé (P60).
 *
 * @param town   le plan de ville, tel que le chantier et le fantôme le lisent
 * @param half   le rayon de la grille : elle va de −half à +half
 * @param view   une valeur par case, pour l'aperçu
 * @param lotGrid le numéro de l'îlot de chaque case, −1 hors de tout îlot
 * @param placed les bâtiments, avec leur numéro pour les retouches à la main
 * @param lots   les îlots
 */
public record UrbanPlan(TownPlan town, int half, byte[] view, short[] lotGrid, List<UrbanGenerator.Placed> placed, List<UrbanGenerator.Lot> lots, Stats stats) {
    /**
     * Le bilan affiché sous le plan.
     *
     * @param noRoom  ce qu'on demandait et qui n'a pas trouvé place : pour le dire plutôt que de le taire
     * @param noModel ce qu'on demandait et dont le pack n'a aucun modèle
     */
    public record Stats(int people, int houses, int housesWanted, int buildings, int lots, int gates, int towers, int bridges, int noRoom, int noModel, int size, Map<TownSize.Role, Integer> built) {}
}
