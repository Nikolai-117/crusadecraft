package fr.crusadecraft.core.character;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcul de l'opinion d'un personnage envers son souverain : la somme de ses motifs, bornée à ±100.
 *
 * <p>Deux sortes de motifs : ceux qui sont <b>enregistrés</b> et datés (récompense, réprimande…), et ceux
 * qui sont <b>calculés</b> à chaque fois depuis la situation (traits, bonheur de la colonie, poste
 * occupé, captivité). Le détail sert à l'infobulle, comme dans CK3.
 */
public final class Opinion {
    public static final int MIN = -100;
    public static final int MAX = 100;

    private Opinion() {}

    /** Une ligne du détail : une clé de traduction (avec un argument éventuel) et sa valeur. */
    public record Line(String key, String arg, int value) {}

    /** Situation dont dépendent les motifs calculés. */
    public record Context(List<Trait> traits, double colonyHappiness, boolean holdsPost, CharacterStatus status) {}

    public static List<Line> breakdown(List<OpinionModifier> stored, Context ctx, long today) {
        List<Line> lines = new ArrayList<>();
        for (Trait t : ctx.traits()) {
            if (t.liegeOpinion() != 0) lines.add(new Line("crusadecraft.opinion.trait", t.key(), t.liegeOpinion()));
        }
        int happiness = happinessModifier(ctx.colonyHappiness());
        if (happiness != 0) lines.add(new Line("crusadecraft.opinion.happiness", null, happiness));
        if (ctx.holdsPost()) lines.add(new Line("crusadecraft.opinion.holds_post", null, 10));
        if (ctx.status() == CharacterStatus.EMPRISONNE) lines.add(new Line("crusadecraft.opinion.imprisoned_now", null, -30));
        if (ctx.status() == CharacterStatus.EXILE) lines.add(new Line("crusadecraft.opinion.exiled_now", null, -30));
        for (OpinionModifier m : stored) {
            int v = m.valueAt(today);
            if (v != 0) lines.add(new Line(m.reason().key(), null, v));
        }
        return lines;
    }

    public static int total(List<Line> lines) {
        int sum = 0;
        for (Line l : lines) sum += l.value();
        return Math.max(MIN, Math.min(MAX, sum));
    }

    /**
     * Le bonheur de la colonie (0 à 10 dans MineColonies) pèse sur l'opinion : un peuple heureux sert
     * mieux. 5 est neutre ; chaque point d'écart vaut 3 points d'opinion.
     */
    public static int happinessModifier(double happiness) {
        return (int) Math.round((happiness - 5.0) * 3.0);
    }

    /** Retire les motifs expirés ; renvoie vrai si la liste a changé. */
    public static boolean prune(List<OpinionModifier> stored, long today) {
        return stored.removeIf(m -> m.expired(today));
    }
}
