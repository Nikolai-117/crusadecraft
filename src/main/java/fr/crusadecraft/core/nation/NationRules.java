package fr.crusadecraft.core.nation;

import fr.crusadecraft.core.diplomacy.Treaty;

import java.util.Map;

/** Ce que décident les souverains de l'ordinateur face à une nation (P41), en logique pure. */
public final class NationRules {
    private NationRules() {}

    /** L'opinion qu'il faut pour accepter ce traité. */
    public static int threshold(Treaty treaty) {
        return switch (treaty) {
            case COMMERCE -> 5;
            case PASSAGE -> 10;
            case NON_AGRESSION -> 0;
            case ALLIANCE_DEFENSIVE -> 40;
            case ALLIANCE -> 60;
        };
    }

    public static boolean accepts(Treaty treaty, int opinion) {
        return opinion >= threshold(treaty);
    }

    /** Un texte à trous : {@code {royaume}} devient sa valeur ; un trou inconnu reste tel quel, pour se voir au lieu de disparaître. */
    public static String fill(String text, Map<String, String> values) {
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return out;
    }
}
