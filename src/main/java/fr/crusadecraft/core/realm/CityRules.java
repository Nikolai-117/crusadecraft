package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les règles des villes d'un royaume (P19), en logique pure, à la manière de la limite de domaine de CK3 : un souverain
 * ne tient en direct qu'un certain nombre de villes selon son rang ; au-delà, chaque ville de trop rend moins d'impôts.
 * Une ville confiée à un gouverneur ne compte plus dans le domaine, mais la couronne n'en reçoit que la part convenue.
 */
public final class CityRules {
    private CityRules() {}

    /** Le nombre de villes tenues en direct sans pénalité, selon le rang (baronnie, comté, duché, royaume, empire). */
    public static int domainLimit(TitleRank rank, List<? extends Integer> limits) {
        int i = rank.ordinal();
        int v = i < limits.size() ? limits.get(i) : limits.isEmpty() ? 1 : limits.get(limits.size() - 1);
        return Math.max(1, v);
    }

    /** Une ville est-elle tenue en direct ? Oui sans gouverneur. */
    public static boolean direct(CityTerms terms) {
        return terms == null || terms.governor() == null;
    }

    /** Les villes tenues en direct au-delà de la limite : la capitale d'abord, puis les autres dans leur ordre d'entrée. */
    public static Set<ColonyRef> overLimit(List<ColonyRef> colonies, Map<ColonyRef, CityTerms> cities, int limit) {
        Set<ColonyRef> over = new LinkedHashSet<>();
        int direct = 0;
        for (ColonyRef c : colonies) {
            if (!direct(cities.get(c))) continue;
            if (++direct > limit) over.add(c);
        }
        return over;
    }

    /** Combien de villes la couronne tient en direct. */
    public static int directCount(List<ColonyRef> colonies, Map<ColonyRef, CityTerms> cities) {
        int n = 0;
        for (ColonyRef c : colonies) if (direct(cities.get(c))) n++;
        return n;
    }

    /** La part des impôts d'une ville qui revient à la couronne (de 0 à 1). */
    public static double taxFactor(CityTerms terms, boolean overLimit, int penaltyPercent) {
        if (!direct(terms)) return terms.taxShare() / 100.0;
        return overLimit ? Math.max(0, 1 - penaltyPercent / 100.0) : 1.0;
    }

    /** La part des levées d'une ville qui revient à la couronne (de 0 à 1). */
    public static double levyFactor(CityTerms terms) {
        return direct(terms) ? 1.0 : terms.levyShare() / 100.0;
    }
}
