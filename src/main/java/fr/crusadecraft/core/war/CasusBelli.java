package fr.crusadecraft.core.war;

import java.util.Locale;

/**
 * Les motifs de guerre. Certains visent une province (revendication, de jure, conquête), d'autres le royaume
 * entier (vassalisation, humiliation). La guerre sainte viendra avec les croisades.
 */
public enum CasusBelli {
    /** Une revendication fabriquée par le chancelier. */
    CLAIM(true, 0),
    /** Une province du royaume de jure dont on porte le titre. */
    DEJURE(true, 0),
    /** La conquête pure : coûteuse en prestige, réservée aux grands. */
    CONQUEST(true, 100),
    /** Forcer un souverain moins titré à prêter hommage. */
    VASSALIZE(false, 0),
    /** Humilier un rival : prestige et or. */
    HUMILIATE(false, 0);

    private final boolean targetsProvince;
    private final int prestigeCost;

    CasusBelli(boolean targetsProvince, int prestigeCost) {
        this.targetsProvince = targetsProvince;
        this.prestigeCost = prestigeCost;
    }

    public boolean targetsProvince() { return targetsProvince; }
    public int prestigeCost() { return prestigeCost; }

    public String key() {
        return "crusadecraft.cb." + name().toLowerCase(Locale.ROOT);
    }

    public static CasusBelli byName(String name) {
        try {
            return CasusBelli.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
