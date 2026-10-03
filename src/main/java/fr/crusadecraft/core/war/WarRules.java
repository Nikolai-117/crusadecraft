package fr.crusadecraft.core.war;

import fr.crusadecraft.core.realm.TitleRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les règles de la guerre, sans rien lire du jeu : motifs ouverts, score, escarmouches de campagne, paix.
 * Les services les appellent avec l'état du monde ; les tests les vérifient à part.
 */
public final class WarRules {
    /** Les batailles (escarmouches, sièges, raids) comptent au plus pour 60 points. */
    public static final int BATTLE_CAP = 60;
    /** Tenir la province visée rapporte un point par jour, jusqu'à 20. */
    public static final int GOAL_TICK_CAP = 20;
    /** Occuper tout le royaume adverse vaut 60 points. */
    public static final int OCCUPATION_MAX = 60;

    private WarRules() {}

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------ motifs de guerre

    /** Pourquoi un motif est fermé ; {@code null} s'il est ouvert. */
    public record CbContext(TitleRank attackerRank, TitleRank defenderRank, boolean hasClaim, boolean dejureMatch,
                            boolean hostile, int defenderOpinion, int attackerPrestige, boolean defenderIsVassal,
                            boolean defenderInfidel, int attackerPiety) {}

    public static String cbProblem(CasusBelli cb, CbContext c) {
        return switch (cb) {
            case CLAIM -> c.hasClaim() ? null : "crusadecraft.war.cb.no_claim";
            case DEJURE -> !c.attackerRank().isAtLeast(TitleRank.ROYAUME) ? "crusadecraft.war.cb.need_kingdom"
                    : c.dejureMatch() ? null : "crusadecraft.war.cb.not_dejure";
            case CONQUEST -> !c.attackerRank().isAtLeast(TitleRank.DUCHE) ? "crusadecraft.war.cb.need_duchy"
                    : c.attackerPrestige() < cb.prestigeCost() ? "crusadecraft.war.cb.no_prestige" : null;
            case VASSALIZE -> c.defenderIsVassal() ? "crusadecraft.war.cb.already_vassal"
                    : c.defenderRank().ordinal() < c.attackerRank().ordinal() ? null : "crusadecraft.war.cb.rank";
            case HUMILIATE -> c.hostile() || c.defenderOpinion() <= -20 ? null : "crusadecraft.war.cb.no_rivalry";
            case GUERRE_SAINTE -> !c.defenderInfidel() ? "crusadecraft.war.cb.not_infidel"
                    : !c.attackerRank().isAtLeast(TitleRank.COMTE) ? "crusadecraft.war.cb.need_county"
                    : c.attackerPiety() < CasusBelli.CRUSADE_PIETY ? "crusadecraft.war.cb.no_piety" : null;
        };
    }

    /** Ce qui interdit toute déclaration entre deux royaumes ; {@code null} si rien. */
    public record Bonds(boolean atWar, boolean truce, boolean nonAggression, boolean alliance, boolean suzerainOrVassal,
                        boolean vassalOfOther) {}

    public static String declarationProblem(Bonds b) {
        if (b.atWar()) return "crusadecraft.war.block.at_war";
        if (b.suzerainOrVassal()) return "crusadecraft.war.block.liege";
        if (b.vassalOfOther()) return "crusadecraft.war.block.vassal_of_other";
        if (b.truce()) return "crusadecraft.war.block.truce";
        if (b.alliance()) return "crusadecraft.war.block.alliance";
        if (b.nonAggression()) return "crusadecraft.war.block.non_aggression";
        return null;
    }

    // ------------------------------------------------------------------ score

    /** Points d'occupation : la part des provinces adverses tenues, la capitale comptant double. */
    public static int occupationScore(int occupied, int defenderProvinces, boolean capitalOccupied) {
        if (defenderProvinces <= 0 || occupied <= 0) return 0;
        double share = (occupied + (capitalOccupied ? 1 : 0)) / (double) (defenderProvinces + 1);
        return clamp((int) Math.round(OCCUPATION_MAX * share), 0, OCCUPATION_MAX);
    }

    /** Le score de la guerre, vu de l'attaquant, borné à ±100. */
    public static int score(int battle, int occupation, int goalTicks) {
        return clamp(battle + occupation + goalTicks, -100, 100);
    }

    // ------------------------------------------------------------------ campagne

    /** Une escarmouche : gain de score (vu du camp A) et pertes de chaque camp, en pour cent des hommes. */
    public record Skirmish(int delta, int lossA, int lossB) {}

    /**
     * Les armées se cherchent et se heurtent chaque jour. Le hasard {@code roll} (0 à 1) pèse d'un quart dans
     * un sens ou dans l'autre ; chaque point de Martial du général ajoute 2 % à son armée.
     */
    public static Skirmish skirmish(int strengthA, int strengthB, int generalA, int generalB, double roll) {
        double a = Math.max(1, strengthA) * (1 + 0.02 * Math.max(0, generalA)) * (0.75 + roll * 0.5);
        double b = Math.max(1, strengthB) * (1 + 0.02 * Math.max(0, generalB)) * (0.75 + (1 - roll) * 0.5);
        double share = a / (a + b);
        int delta = clamp((int) Math.round((share - 0.5) * 16), -8, 8);
        int lossA = delta >= 0 ? 2 + (int) Math.round(2 * (1 - share)) : 5 + (int) Math.round(5 * (1 - share));
        int lossB = delta <= 0 ? 2 + (int) Math.round(2 * share) : 5 + (int) Math.round(5 * share);
        return new Skirmish(delta, lossA, lossB);
    }

    // ------------------------------------------------------------------ paix

    /** Les termes d'une paix, vus de celui qui la propose. */
    public enum Peace {
        /** Chacun rentre chez soi. */
        WHITE,
        /** Mon camp l'emporte : le but de guerre (ou les réparations) est accordé. */
        VICTORY,
        /** Mon camp s'incline. */
        SURRENDER;

        public String key() {
            return "crusadecraft.war.peace." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Un facteur de la réponse, pour l'infobulle. */
    public record Factor(String key, int value) {}

    public record Verdict(boolean accepted, List<Factor> factors) {}

    /**
     * Ce que répond une IA à une paix proposée par l'autre camp. {@code aiScore} est le score vu de l'IA
     * (+100 : elle gagne), {@code aiExhaustion} sa fatigue (0 à 100).
     */
    public static Verdict aiAnswer(Peace proposed, int aiScore, int aiExhaustion, long days) {
        List<Factor> f = new ArrayList<>();
        f.add(new Factor("crusadecraft.war.f.score", -aiScore));
        f.add(new Factor("crusadecraft.war.f.exhaustion", aiExhaustion / 2));
        int total;
        switch (proposed) {
            case SURRENDER -> {
                f.add(new Factor("crusadecraft.war.f.offered_victory", 100));
                total = 100;
            }
            case WHITE -> {
                int length = days >= 5 ? 10 : -10;
                f.add(new Factor("crusadecraft.war.f.length", length));
                f.add(new Factor("crusadecraft.war.f.white", -15));
                total = -aiScore + aiExhaustion / 2 + length - 15;
            }
            default -> {
                f.add(new Factor("crusadecraft.war.f.demand", -60));
                total = -aiScore + aiExhaustion / 2 - 60;
            }
        }
        return new Verdict(total >= 0, f);
    }

    /** Prestige gagné (et perdu par l'autre camp) à l'issue d'une guerre. */
    public static int victoryPrestige(CasusBelli cb) {
        return switch (cb) {
            case HUMILIATE -> 60;
            case CONQUEST, GUERRE_SAINTE -> 40;
            default -> 30;
        };
    }
}
