package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * L'éducation d'un personnage, déduite de ses aptitudes de base : l'aptitude la plus forte en donne
 * la branche, sa valeur en donne le rang (1 à 4). Elle ajoute un bonus à cette aptitude, comme dans CK3.
 */
public record Education(Stat stat, int tier) {
    /** Seuils de rang : 0-5, 6-11, 12-17, 18 et plus. */
    private static final int[] THRESHOLDS = {6, 12, 18};

    public static Education of(int[] baseStats) {
        int best = 0;
        for (int i = 1; i < baseStats.length; i++) {
            if (baseStats[i] > baseStats[best]) best = i;
        }
        int value = baseStats[best];
        int tier = 1;
        for (int t : THRESHOLDS) {
            if (value >= t) tier++;
        }
        return new Education(Stat.values()[best], tier);
    }

    /** Bonus apporté à l'aptitude de la branche. */
    public int bonus() {
        return tier;
    }

    public String key() {
        return "crusadecraft.education." + stat.name().toLowerCase(Locale.ROOT) + "." + tier;
    }
}
