package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * Motifs d'opinion datés, à la manière des modificateurs de CK3.
 *
 * <p>Durées en jours du monde (un jour = 20 minutes de jeu). Un motif « dégressif » perd de sa valeur
 * jusqu'à zéro au fil de sa durée ; les autres restent entiers jusqu'à expiration.
 */
public enum OpinionReason {
    RECOMPENSE(20, 10, true),
    REPRIMANDE(-15, 8, true),
    DESTITUE(-20, 12, true),
    EMPRISONNE(-40, 40, true),
    EXILE(-50, 40, true),
    LIBERE(10, 6, true),
    RAPPELE(10, 6, true),
    POSTE_REFUSE_PAR_SOUVERAIN(-10, 6, true),
    NOMME(15, 10, true);

    private final int value;
    private final int durationDays;
    private final boolean decaying;

    OpinionReason(int value, int durationDays, boolean decaying) {
        this.value = value;
        this.durationDays = durationDays;
        this.decaying = decaying;
    }

    public int value() { return value; }
    public int durationDays() { return durationDays; }
    public boolean decaying() { return decaying; }

    public String key() {
        return "crusadecraft.opinion." + name().toLowerCase(Locale.ROOT);
    }

    public static OpinionReason byName(String name) {
        try {
            return OpinionReason.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
