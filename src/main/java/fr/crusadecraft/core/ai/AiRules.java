package fr.crusadecraft.core.ai;

import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.WarRules;
import fr.crusadecraft.core.world.AiPersonality;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Les décisions des royaumes IA, sans rien lire du jeu : qui attaquer, quel traité signer, quand faire la paix,
 * qui se ligue contre un joueur trop menaçant. {@link AiBrain} les applique au monde ; les tests les vérifient à part.
 */
public final class AiRules {
    /** Menace ajoutée par une déclaration de guerre, une province conquise, un royaume soumis. */
    public static final int THREAT_DECLARE = 5;
    public static final int THREAT_PROVINCE = 20;
    public static final int THREAT_VASSAL = 15;
    public static final int THREAT_MAX = 200;

    private AiRules() {}

    // ------------------------------------------------------------------ guerre

    /**
     * Ce qu'un royaume IA sait d'une cible possible.
     *
     * @param mySide     sa force et celle de ses alliés
     * @param theirSide  celle de la cible et des alliés qui la défendraient
     * @param distance   entre les deux capitales, en blocs
     * @param provinces  provinces que la cible tient (une conquête en vise une)
     */
    public record Target(UUID id, boolean player, int opinion, boolean hostile, int mySide, int theirSide, double distance,
                         int provinces, TitleRank rank) {}

    /** Le rapport de forces qu'exige ce tempérament pour entrer en guerre ; l'infini : jamais. */
    public static double requiredRatio(AiPersonality p, int opinion) {
        return switch (p) {
            case EXPANSIONNISTE -> opinion <= -20 ? 1.1 : 1.3;
            case OPPORTUNISTE -> 1.6;
            case FANATIQUE -> opinion <= -20 ? 1.3 : 2.0;
            case MARCHAND -> opinion <= -40 ? 2.0 : Double.POSITIVE_INFINITY;
            case PACIFIQUE -> opinion <= -60 ? 2.5 : Double.POSITIVE_INFINITY;
        };
    }

    /**
     * La cible la plus avantageuse parmi celles qui passent le seuil, ou {@code null}. Un joueur n'est visé que
     * s'il est en rivalité ouverte avec le royaume ou franchement haï : l'IA ne tombe pas sur un voisin paisible.
     */
    public static Target pickTarget(AiPersonality p, List<Target> targets, double range) {
        Target best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Target t : targets) {
            if (t.distance() > range) continue;
            if (!t.player() && t.provinces() <= 0) continue;
            if (t.player() && !t.hostile() && t.opinion() > -40) continue;
            double ratio = t.mySide() / (double) Math.max(1, t.theirSide());
            if (ratio < requiredRatio(p, t.opinion())) continue;
            double score = ratio - t.opinion() / 50.0 - t.distance() / Math.max(1, range);
            if (score > bestScore) {
                best = t;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * Le motif choisi : une province à prendre si l'on est au moins comte, sinon la soumission d'un plus petit
     * (jamais d'un joueur), à défaut l'humiliation.
     */
    public static CasusBelli casusBelli(TitleRank me, Target t) {
        if (t.provinces() > 0 && me.isAtLeast(TitleRank.COMTE)) return CasusBelli.CONQUEST;
        if (!t.player() && t.rank().ordinal() < me.ordinal()) return CasusBelli.VASSALIZE;
        return CasusBelli.HUMILIATE;
    }

    /** Chance (0 à 1) qu'un camp mené par l'IA prenne une place ennemie dans la journée. */
    public static double occupationChance(int mySide, int theirSide) {
        double ratio = mySide / (double) Math.max(1, theirSide);
        return Math.max(0.03, Math.min(0.35, 0.1 + 0.15 * (ratio - 1)));
    }

    /**
     * La paix entre deux IA : +1 l'attaquant l'emporte, −1 le défenseur, 0 paix blanche ; {@code null} si la
     * guerre continue. Un camp écrasé cède, une guerre trop longue ou trop lasse s'arrête sur le score du moment.
     */
    public static Integer peaceBetweenAis(int score, int attackerExhaustion, int defenderExhaustion, long days) {
        if (score >= 50) return 1;
        if (score <= -50) return -1;
        if (days >= 30 || (attackerExhaustion >= 60 && defenderExhaustion >= 60)) return Math.abs(score) >= 20 ? Integer.signum(score) : 0;
        if (attackerExhaustion >= 85) return score >= 20 ? 1 : score <= -10 ? -1 : 0;
        if (defenderExhaustion >= 85) return score <= -20 ? -1 : score >= 10 ? 1 : 0;
        return null;
    }

    /** Une IA lasse qui ne gagne ni ne perd propose la paix blanche au joueur. */
    public static boolean offersWhitePeace(int aiScore, int aiExhaustion, long days) {
        return Math.abs(aiScore) < 25 && (aiExhaustion >= 70 || days >= 30);
    }

    // ------------------------------------------------------------------ traités

    /** Le traité que deux IA concluent, ou {@code null} : il faut que chacune estime l'autre. */
    public static Treaty treaty(AiPersonality a, AiPersonality b, int opinionAB, int opinionBA, Set<Treaty> current) {
        int least = Math.min(opinionAB, opinionBA);
        if (least >= 60 && !current.contains(Treaty.ALLIANCE)) return Treaty.ALLIANCE;
        if (least >= 40 && !current.contains(Treaty.ALLIANCE) && !current.contains(Treaty.ALLIANCE_DEFENSIVE)) return Treaty.ALLIANCE_DEFENSIVE;
        if ((a == AiPersonality.MARCHAND || b == AiPersonality.MARCHAND) && least >= 5 && !current.contains(Treaty.COMMERCE)) return Treaty.COMMERCE;
        if ((a == AiPersonality.PACIFIQUE || b == AiPersonality.PACIFIQUE) && least >= 0 && !current.contains(Treaty.NON_AGRESSION)) {
            return Treaty.NON_AGRESSION;
        }
        return null;
    }

    // ------------------------------------------------------------------ menace et coalitions

    /** La menace retombe d'un point par jour, de deux en temps de paix. */
    public static int decayThreat(int threat, boolean atWar) {
        return Math.max(0, threat - (atWar ? 1 : 2));
    }

    public static int addThreat(int threat, int amount) {
        return WarRules.clamp(threat + amount, 0, THREAT_MAX);
    }

    /** Ce que la menace d'un joueur coûte dans l'opinion des souverains IA. */
    public static int threatOpinion(int threat) {
        return -Math.min(40, Math.max(0, threat) / 3);
    }

    /**
     * Une IA rejoint-elle la coalition contre un joueur menaçant ? Il faut que la menace dépasse le seuil, que
     * rien ne la lie au joueur (alliance, pacte, vassalité) et qu'elle le voie d'un mauvais œil ; les pacifiques
     * prennent peur plus tôt, les conquérants plus tard.
     */
    public static boolean joinsCoalition(AiPersonality p, int opinion, int threat, int threshold, boolean bound) {
        if (bound || threat < threshold) return false;
        int need = switch (p) {
            case PACIFIQUE -> -10;
            case EXPANSIONNISTE -> -30;
            default -> -20;
        };
        return opinion <= need;
    }

    /** La coalition se défait quand la menace est retombée sous la moitié du seuil. */
    public static boolean coalitionDissolves(int threat, int threshold) {
        return threat < threshold / 2;
    }

    /** La coalition attaque quand elle pèse nettement plus que le joueur et ses alliés. */
    public static boolean coalitionAttacks(int coalitionStrength, int playerSide, int members) {
        return members >= 2 && coalitionStrength >= playerSide * 1.2;
    }
}
