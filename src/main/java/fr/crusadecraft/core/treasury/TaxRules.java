package fr.crusadecraft.core.treasury;

import java.util.List;

/**
 * Le calcul des impôts et de l'entretien d'une colonie, sans rien lire du jeu : tout arrive en paramètre,
 * ce qui permet de le tester à part.
 *
 * <p>Impôt d'un jour = somme des barèmes des bâtiments construits
 * × bonheur (de 0,5 à 1,5, comme War 'N Taxes)
 * × fiscalité (loi)
 * × (1 + bonus de l'intendant + bonus des recherches)
 * × moitié si la colonie est attaquée
 * × autorité de la couronne, pour une province.
 */
public final class TaxRules {

    private TaxRules() {}

    /** Un bâtiment construit : son identifiant MineColonies et son niveau. */
    public record Building(String id, int level) {}

    /** Tout ce qui module l'impôt d'une colonie. */
    public record Modifiers(double happiness, double fiscalMultiplier, double stewardBonus, double researchBonus,
                            boolean raided, double provinceMultiplier) {
        public static Modifiers neutral() {
            return new Modifiers(5.0, 1.0, 0, 0, false, 1.0);
        }
    }

    /** Résultat pour une colonie : l'impôt brut (barèmes seuls), l'impôt perçu et l'entretien militaire. */
    public record ColonyTax(double gross, long collected, long upkeep) {}

    public static final double HAPPINESS_MIN = 0.5;
    public static final double HAPPINESS_MAX = 1.5;

    /** Bonheur MineColonies (0 à 10) → multiplicateur, linéaire de 0,5 à 1,5 ; 5 donne 1. */
    public static double happinessMultiplier(double happiness) {
        double h = Math.max(0, Math.min(10, happiness));
        return HAPPINESS_MIN + h / 10.0 * (HAPPINESS_MAX - HAPPINESS_MIN);
    }

    /**
     * Bonus de l'intendant : 1 % par point d'Intendance, doublé et augmenté de 10 % quand on lui confie
     * la collecte des impôts.
     */
    public static double stewardBonus(int stewardship, boolean collecting) {
        if (stewardship <= 0 && !collecting) return 0;
        return collecting ? stewardship * 0.02 + 0.10 : stewardship * 0.01;
    }

    /** La tâche « Gérer le domaine » : selon la population du royaume et l'Intendance de l'intendant. */
    public static long domainIncome(int population, int stewardship, double meetingBonus) {
        return Math.max(0, Math.round(population * (0.2 + stewardship / 50.0) * meetingBonus));
    }

    public static ColonyTax compute(List<Building> buildings, TaxTable taxes, TaxTable upkeep, Modifiers m) {
        double gross = 0;
        double maintenance = 0;
        for (Building b : buildings) {
            if (b.level() <= 0) continue;
            gross += taxes.rate(b.id()).at(b.level());
            if (upkeep.lists(b.id())) maintenance += upkeep.rate(b.id()).at(b.level());
        }
        double collected = gross
                * happinessMultiplier(m.happiness())
                * m.fiscalMultiplier()
                * (1 + m.stewardBonus() + m.researchBonus())
                * (m.raided() ? 0.5 : 1.0)
                * m.provinceMultiplier();
        return new ColonyTax(gross, Math.max(0, Math.round(collected)), Math.round(maintenance));
    }

    /**
     * Ce qu'on peut réellement débiter sans dépasser la dette permise. Renvoie le montant débité (positif),
     * peut-être inférieur à la somme demandée.
     */
    public static long chargeable(long balance, long amount, long debtLimit) {
        if (amount <= 0) return 0;
        long floor = -Math.max(0, debtLimit);
        long room = balance - floor;
        if (room <= 0) return 0;
        return Math.min(amount, room);
    }
}
