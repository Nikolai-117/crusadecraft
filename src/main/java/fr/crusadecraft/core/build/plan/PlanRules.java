package fr.crusadecraft.core.build.plan;

import java.util.Locale;

/**
 * Ce que devient chaque bloc d'un plan quand on le pose (P34). Logique pure, sur l'identifiant du bloc.
 *
 * <p>Les plans de MineColonies portent des blocs qui n'ont de sens que pour son bâtisseur : les blocs de
 * substitution de Structurize (« laisser le terrain », « remplir », « eau », « bloc au choix »), et les blocs de
 * cabane, liés à une colonie. On les traduit en blocs ordinaires ; tout le reste est posé tel quel, avec les données
 * de son entité (les textures des blocs Domum Ornamentum en dépendent).
 */
public final class PlanRules {

    public enum Kind {
        /** Posé tel quel. */
        KEEP,
        /** Laisser le terrain tel qu'il est. */
        SKIP,
        /** Remplir de terre (sous le sol) ou d'herbe (au sol). */
        SOLID,
        /** De l'eau. */
        FLUID,
        /** Le bloc de remplacement que porte le bloc de substitution (dans les données de son entité). */
        TAG,
        /** Un bloc de cabane MineColonies : gardé pour une cabane à fonder, sinon remplacé par un tonneau. */
        HUT,
        /** Un autre bloc de MineColonies : gardé s'il est décoratif, remplacé par un tonneau s'il a une entité. */
        COLONY,
        /** Retiré (balises de chemin, ruban de chantier). */
        AIR
    }

    private PlanRules() {}

    public static Kind classify(String id) {
        if (id == null) return Kind.KEEP;
        String s = id.toLowerCase(Locale.ROOT);
        switch (s) {
            case "structurize:blocksubstitution":
                return Kind.SKIP;
            case "structurize:blocksolidsubstitution":
                return Kind.SOLID;
            case "structurize:blockfluidsubstitution":
                return Kind.FLUID;
            case "structurize:blocktagsubstitution":
                return Kind.TAG;
            case "minecolonies:blockwaypoint":
            case "minecolonies:blockconstructiontape":
            case "structurize:blockbarrier":
                return Kind.AIR;
            default:
                break;
        }
        if (s.startsWith("minecolonies:blockhut")) return Kind.HUT;
        if (s.startsWith("minecolonies:")) return Kind.COLONY;
        return Kind.KEEP;
    }

    /** « townhall » pour « minecolonies:blockhuttownhall », sinon vide. */
    public static String hutType(String id) {
        if (id == null) return "";
        String s = id.toLowerCase(Locale.ROOT);
        return s.startsWith("minecolonies:blockhut") ? s.substring("minecolonies:blockhut".length()) : "";
    }

    /** Le niveau d'un plan d'après son nom (« townhall3 » → 3), 0 s'il n'en porte pas. */
    public static int level(String label) {
        int i = label.length();
        while (i > 0 && Character.isDigit(label.charAt(i - 1))) i--;
        if (i == label.length() || label.length() - i > 2) return 0;
        return Integer.parseInt(label.substring(i));
    }

    /** Le nom sans son niveau : « townhall3 » → « townhall ». */
    public static String base(String label) {
        int i = label.length();
        while (i > 0 && Character.isDigit(label.charAt(i - 1))) i--;
        return label.substring(0, i);
    }
}
