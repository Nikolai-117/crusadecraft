package fr.crusadecraft.core.diplomacy;

import java.util.Locale;

/** Le statut qu'affiche une relation, du plus fort au plus faible. */
public enum DiploStatus {
    SUZERAIN(0xFF6FA8DC),
    VASSAL(0xFF6FA8DC),
    GUERRE(0xFFB3261E),
    ALLIANCE(0xFF6E9E4A),
    ALLIANCE_DEFENSIVE(0xFF7CB342),
    NON_AGRESSION(0xFFC9A55C),
    COMMERCE(0xFFD4A017),
    HOSTILE(0xFFD9732B),
    NEUTRE(0xFFA89A7C);

    private final int color;

    DiploStatus(int color) {
        this.color = color;
    }

    public int color() { return color; }

    public String key() {
        return "crusadecraft.diplo." + name().toLowerCase(Locale.ROOT);
    }
}
