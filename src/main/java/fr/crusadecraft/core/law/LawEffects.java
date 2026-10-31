package fr.crusadecraft.core.law;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.Trait;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que font les lois, en chiffres. Rien n'y lit le jeu : les services appellent ces fonctions avec
 * l'état du royaume.
 */
public final class LawEffects {

    private LawEffects() {}

    // ------------------------------------------------------------------ fiscalité

    private static final double[] FISCAL = {0.7, 1.0, 1.4};
    /** Facteur du modificateur de bonheur MineColonies « Impôts » (1 = neutre). */
    private static final double[] FISCAL_HAPPINESS = {1.15, 1.0, 0.7};

    public static double fiscalMultiplier(LawState laws) {
        return FISCAL[laws.level(Law.FISCALITE)];
    }

    public static double taxHappinessFactor(LawState laws) {
        return FISCAL_HAPPINESS[laws.level(Law.FISCALITE)];
    }

    // ------------------------------------------------------------------ autorité de la couronne

    private static final double[] PROVINCE = {0.6, 0.8, 1.0, 1.2};
    private static final int[] AUTHORITY_OPINION = {10, 0, -5, -15};

    /** Part de l'impôt des provinces (colonies autres que la capitale) qui remonte à la couronne. */
    public static double provinceMultiplier(LawState laws) {
        return PROVINCE[laws.level(Law.AUTORITE)];
    }

    // ------------------------------------------------------------------ chaque jour

    /** Prestige gagné chaque jour grâce aux lois. */
    public static int dailyPrestige(LawState laws) {
        int p = 0;
        int authority = laws.level(Law.AUTORITE);
        if (authority == 2) p += 1;
        if (authority == 3) p += 2;
        if (laws.level(Law.CODE_CHEVALERESQUE) == 1) p += 1;
        return p;
    }

    /** Tous les combien de jours la crainte baisse d'un point. */
    public static int dreadDecayEveryDays(LawState laws) {
        return laws.level(Law.JUSTICE_ROYALE) == 1 ? 2 : 1;
    }

    /** Multiplicateur de l'entraînement des gardes par le maréchal. */
    public static double trainingMultiplier(LawState laws) {
        return laws.level(Law.CODE_CHEVALERESQUE) == 1 ? 1.5 : 1.0;
    }

    // ------------------------------------------------------------------ opinion

    /** Ce que les lois en vigueur valent dans l'opinion d'un sujet, ligne par ligne. */
    public static List<Opinion.Line> opinionLines(LawState laws, List<Trait> traits, boolean holdsPost) {
        List<Opinion.Line> lines = new ArrayList<>();
        int fiscal = laws.level(Law.FISCALITE);
        int f = 0;
        if (fiscal == 0) f = traits.contains(Trait.AVARE) ? 10 : 5;
        if (fiscal == 2) f = traits.contains(Trait.AVARE) ? -15 : traits.contains(Trait.GENEREUX) ? -5 : -10;
        add(lines, Law.FISCALITE, fiscal, f);

        if (holdsPost) {
            int authority = laws.level(Law.AUTORITE);
            int a = AUTHORITY_OPINION[authority];
            if (a < 0) {
                if (traits.contains(Trait.AMBITIEUX)) a *= 2;
                if (traits.contains(Trait.LOYAL) || traits.contains(Trait.CONTENT)) a /= 2;
            }
            add(lines, Law.AUTORITE, authority, a);
        }

        if (laws.level(Law.CODE_CHEVALERESQUE) == 1) {
            int c = 0;
            if (traits.contains(Trait.BRAVE)) c += 10;
            if (traits.contains(Trait.LACHE)) c -= 10;
            if (traits.contains(Trait.CRUEL)) c -= 5;
            if (traits.contains(Trait.COMPATISSANT)) c += 5;
            add(lines, Law.CODE_CHEVALERESQUE, 1, c);
        }

        if (laws.level(Law.JUSTICE_ROYALE) == 1) {
            int j = 0;
            if (traits.contains(Trait.JUSTE)) j += 10;
            if (traits.contains(Trait.ARBITRAIRE)) j -= 10;
            add(lines, Law.JUSTICE_ROYALE, 1, j);
        }
        return lines;
    }

    private static void add(List<Opinion.Line> lines, Law law, int level, int value) {
        if (value != 0) lines.add(new Opinion.Line("crusadecraft.opinion.law", law.levelKey(level), value));
    }

    // ------------------------------------------------------------------ vote

    /**
     * Penchant d'un conseiller pour un changement de loi, hors opinion : positif s'il l'approuve.
     * Renforcer l'autorité ou la fiscalité déplaît par nature ; certains traits changent la donne.
     */
    public static int preference(Law law, int from, int to, List<Trait> traits) {
        if (from == to) return 0;
        boolean up = to > from;
        int p = 0;
        switch (law) {
            case SUCCESSION -> {
                // Les ambitieux préfèrent l'élection, les loyaux la tradition.
                if (traits.contains(Trait.AMBITIEUX)) p += to == 3 ? 3 : -1;
                if (traits.contains(Trait.LOYAL)) p += to == 0 ? 2 : -1;
            }
            case FISCALITE -> {
                p += up ? -3 : 2;
                if (traits.contains(Trait.AVARE)) p += up ? -3 : 2;
                if (traits.contains(Trait.GENEREUX)) p += up ? 1 : 0;
            }
            case AUTORITE -> {
                p += up ? -4 : 3;
                if (traits.contains(Trait.AMBITIEUX)) p += up ? -3 : 2;
                if (traits.contains(Trait.LOYAL)) p += up ? 4 : -2;
                if (traits.contains(Trait.CONTENT)) p += up ? 1 : 0;
            }
            case CODE_CHEVALERESQUE -> {
                int s = 0;
                if (traits.contains(Trait.BRAVE)) s += 4;
                if (traits.contains(Trait.LACHE)) s -= 4;
                if (traits.contains(Trait.CRUEL)) s -= 3;
                if (traits.contains(Trait.COMPATISSANT)) s += 2;
                if (traits.contains(Trait.HONNETE)) s += 1;
                p += up ? s : -s;
            }
            case JUSTICE_ROYALE -> {
                int s = 0;
                if (traits.contains(Trait.JUSTE)) s += 4;
                if (traits.contains(Trait.ARBITRAIRE)) s -= 4;
                if (traits.contains(Trait.FOURBE)) s -= 2;
                if (traits.contains(Trait.HONNETE)) s += 2;
                p += up ? s : -s;
            }
        }
        return p;
    }

    /** Un conseiller appelé à voter. */
    public record Voter(String name, int opinion, List<Trait> traits) {}

    /** Son vote et ce qui l'a décidé. */
    public record Ballot(String name, boolean approves, int score) {}

    /** Le résultat d'un vote : la loi passe si les voix pour égalent au moins les voix contre. */
    public record Vote(List<Ballot> ballots) {
        public int approvals() {
            return (int) ballots.stream().filter(Ballot::approves).count();
        }

        public int rejections() {
            return ballots.size() - approvals();
        }

        public boolean passes() {
            return approvals() >= rejections();
        }
    }

    /**
     * Score d'un conseiller : son opinion (un point par tranche de 10), son penchant, et la crainte qu'inspire
     * le souverain (un point par tranche de 25, au plus 4). Il vote pour dès que le score est positif ou nul.
     */
    public static Ballot ballot(Voter v, Law law, int from, int to, int dread) {
        int score = Math.floorDiv(v.opinion(), 10) + preference(law, from, to, v.traits())
                + Math.min(4, Math.max(0, dread) / 25);
        return new Ballot(v.name(), score >= 0, score);
    }

    public static Vote vote(List<Voter> voters, Law law, int from, int to, int dread) {
        List<Ballot> ballots = new ArrayList<>();
        for (Voter v : voters) ballots.add(ballot(v, law, from, to, dread));
        return new Vote(ballots);
    }

    /** Prestige à dépenser pour imposer une loi rejetée : un montant par voix contre. */
    public static int forceCost(Vote vote, int perVote) {
        return vote.passes() ? 0 : perVote * vote.rejections();
    }
}
