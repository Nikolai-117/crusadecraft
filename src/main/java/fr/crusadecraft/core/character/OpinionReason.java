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
    NOMME(15, 10, true),
    // P3 : cour et requêtes
    COUR(5, 2, false),
    REUNION(3, 1, false),
    PROMESSE(5, 4, true),
    REQUETE_SATISFAITE(8, 5, true),
    REQUETE_IGNOREE(-10, 6, true),
    FAVEUR(15, 8, true),
    MENACE(-10, 8, true),
    REFUS(-15, 8, true),
    // P4 : trésor et lois
    SALAIRE_IMPAYE(-10, 5, true),
    LOI_IMPOSEE(-15, 10, true),
    // P6 : diplomatie (opinion d'un souverain envers un autre royaume)
    CADEAU(10, 20, true),
    INSULTE(-30, 20, true),
    TRAITE_SIGNE(10, 20, true),
    REFUS_DIPLOMATIQUE(-8, 8, true),
    CHANCELIER_RELATIONS(5, 10, true),
    EXIGENCE(-15, 20, true),
    HOMMAGE(10, 30, true),
    // P9 : événements, factions, complots, secrets
    EVENEMENT(10, 20, true),
    FACTION_CONCEDEE(15, 15, true),
    FACTION_REFUSEE(-20, 20, true),
    COMPLOT_PARDONNE(15, 15, true),
    CHANTAGE(-10, 30, true),
    SECRET_REVELE(-30, 30, true);

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
