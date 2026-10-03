package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.character.Trait;

import java.util.List;

/**
 * Les règles des intrigues, sans rien lire du jeu : qui est mécontent, ce que pèse une faction, qui complote, ce
 * que le Maître-espion découvre, quand le peuple se soulève. {@link IntrigueService} les applique.
 */
public final class IntrigueRules {
    /** Puissance à partir de laquelle une faction pose son ultimatum. */
    public static final int ULTIMATUM_POWER = 60;
    /** Jours entre deux ultimatums d'une même faction. */
    public static final int ULTIMATUM_COOLDOWN = 10;

    private IntrigueRules() {}

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ factions

    /** Un courtisan est-il assez mécontent pour se liguer ? Un loyal jamais ; un ambitieux dès qu'il n'aime plus. */
    public static boolean discontent(int opinion, List<Trait> traits) {
        if (traits.contains(Trait.LOYAL)) return false;
        int need = traits.contains(Trait.AMBITIEUX) ? 0 : traits.contains(Trait.CONTENT) ? -40 : -20;
        return opinion <= need;
    }

    /** Le poids d'un courtisan dans la cour : un conseiller compte plus qu'un maire. */
    public static int weight(boolean council) {
        return council ? 3 : 2;
    }

    /** Puissance (0 à 100) : la part de la cour qui soutient la faction, entamée par la crainte et l'ordre maintenu. */
    public static int power(int membersWeight, int courtWeight, int dread, int order) {
        if (courtWeight <= 0 || membersWeight <= 0) return 0;
        long raw = Math.round(100.0 * membersWeight / courtWeight) - Math.max(0, dread) / 2 - Math.max(0, order);
        return (int) Math.max(0, Math.min(100, raw));
    }

    public static boolean ultimatum(int power, int members, long daysSinceLast) {
        return power >= ULTIMATUM_POWER && members >= 2 && daysSinceLast >= ULTIMATUM_COOLDOWN;
    }

    /** Le conseiller honni d'une faction « changement » : celui qui cumule le plus de défauts qu'on ne pardonne pas. */
    public static int hatedScore(List<Trait> traits) {
        int s = 0;
        for (Trait t : traits) {
            if (t == Trait.CORROMPU) s += 3;
            else if (t == Trait.CRUEL || t == Trait.ARBITRAIRE || t == Trait.FOURBE || t == Trait.ARROGANT) s += 1;
        }
        return s;
    }

    // ------------------------------------------------------------------ complots

    /** Un courtisan qui hait se met à comploter (5 % par jour) ; un fourbe ou un ambitieux plus tôt qu'un autre. */
    public static boolean startsPlot(int opinion, List<Trait> traits, double roll) {
        if (traits.contains(Trait.LOYAL) || traits.contains(Trait.HONNETE)) return false;
        boolean schemer = traits.contains(Trait.FOURBE) || traits.contains(Trait.AMBITIEUX) || traits.contains(Trait.ARBITRAIRE)
                || traits.contains(Trait.CRUEL);
        if (opinion > (schemer ? -30 : -50)) return false;
        return roll < 0.05;
    }

    public static Plot.Kind plotKind(List<Trait> traits, double roll) {
        if (traits.contains(Trait.AVARE) || traits.contains(Trait.CORROMPU)) return Plot.Kind.DETOURNEMENT;
        if (traits.contains(Trait.COLERIQUE) || traits.contains(Trait.CRUEL)) return Plot.Kind.SABOTAGE;
        Plot.Kind[] all = Plot.Kind.values();
        return all[(int) Math.min(all.length - 1, Math.floor(roll * all.length))];
    }

    /** Avancée quotidienne d'un complot, selon l'Intrigue de qui l'ourdit. */
    public static int plotProgress(int intrigue) {
        return 3 + Math.max(0, intrigue) / 2;
    }

    /**
     * Chance du jour que le Maître-espion évente un complot, découvre une faction ou un secret : son Intrigue
     * contre la moitié de celle de sa cible, triplée s'il débusque les complots. Sans maître-espion, rien.
     */
    public static double discovery(int spyIntrigue, int targetIntrigue, boolean hunting) {
        if (spyIntrigue <= 0) return 0;
        double base = (spyIntrigue - targetIntrigue / 2.0) * (hunting ? 0.03 : 0.01);
        return hunting ? clamp(base, 0.05, 0.6) : clamp(base, 0.01, 0.25);
    }

    // ------------------------------------------------------------------ secrets

    /** Un courtisan se compromet (2 % par jour) : le genre de secret dépend de son caractère. {@code null} : rien. */
    public static Secret.Kind secretFor(List<Trait> traits, double roll) {
        if (roll >= 0.02) return null;
        if (traits.contains(Trait.AVARE) || traits.contains(Trait.FOURBE) || traits.contains(Trait.CORROMPU)) return Secret.Kind.CORRUPTION;
        if (traits.contains(Trait.CYNIQUE)) return Secret.Kind.HERESIE;
        return roll < 0.01 ? Secret.Kind.DETTES_DE_JEU : Secret.Kind.LIAISON;
    }

    // ------------------------------------------------------------------ révoltes

    /**
     * Chance du jour qu'une colonie se soulève : il faut que le peuple soit malheureux (moins de 5) et l'impôt au
     * moins normal. Le maréchal qui maintient l'ordre l'apaise.
     */
    public static double revoltChance(double happiness, int fiscalLevel, int order) {
        if (happiness >= 5 || fiscalLevel < 1) return 0;
        return clamp((5 - happiness) * 0.04 + (fiscalLevel - 1) * 0.05 - order * 0.005, 0, 0.3);
    }
}
