package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce que pense un souverain IA d'un royaume de joueur, et ce qu'il accepte. Rien n'y lit le jeu : tout
 * arrive en paramètres, ce qui permet d'afficher le détail (« pourquoi il refuse ») et de le tester.
 */
public final class DiplomacyRules {

    private DiplomacyRules() {}

    /** Ce que le souverain IA sait de son interlocuteur. */
    public record Context(AiPersonality personality, List<Trait> rulerTraits, TitleRank aiRank, TitleRank playerRank,
                          int playerPrestige, int playerDread, boolean sameDejure, int chancellorDiplomacy,
                          int playerStrength, int aiStrength, int playerThreat) {
        /** Rapport de forces, du point de vue du joueur (2 = deux fois plus fort). */
        public double ratio() {
            return Math.max(1, playerStrength) / (double) Math.max(1, aiStrength);
        }
    }

    // ------------------------------------------------------------------ opinion

    public static List<Opinion.Line> opinion(Relation rel, UUID ai, UUID player, Context c, long today) {
        List<Opinion.Line> lines = new ArrayList<>();
        int personality = switch (c.personality()) {
            case PACIFIQUE -> 10;
            case MARCHAND -> 5;
            case EXPANSIONNISTE -> -10;
            case FANATIQUE -> -5;
            default -> 0;
        };
        add(lines, "crusadecraft.diplo.op.personality", c.personality().key(), personality);
        for (Trait t : c.rulerTraits()) {
            int v = switch (t) {
                case CONFIANT -> 10;
                case PARANOIAQUE -> -10;
                case GENEREUX -> 5;
                case ARROGANT, AMBITIEUX -> -5;
                default -> 0;
            };
            add(lines, "crusadecraft.opinion.trait", t.key(), v);
        }
        add(lines, "crusadecraft.diplo.op.prestige", null, Math.min(15, Math.max(0, c.playerPrestige()) / 40));
        add(lines, "crusadecraft.diplo.op.rank", null, 5 * (c.playerRank().ordinal() - c.aiRank().ordinal()));
        if (c.sameDejure()) add(lines, "crusadecraft.diplo.op.dejure", null, -10);
        add(lines, "crusadecraft.diplo.op.threat", null, fr.crusadecraft.core.ai.AiRules.threatOpinion(c.playerThreat()));
        add(lines, "crusadecraft.diplo.op.chancellor", null, Math.max(0, c.chancellorDiplomacy()) / 3);
        if (rel.has(Treaty.ALLIANCE, today)) add(lines, Treaty.ALLIANCE.key(), null, 25);
        if (rel.has(Treaty.ALLIANCE_DEFENSIVE, today)) add(lines, Treaty.ALLIANCE_DEFENSIVE.key(), null, 15);
        if (rel.has(Treaty.NON_AGRESSION, today)) add(lines, Treaty.NON_AGRESSION.key(), null, 10);
        if (rel.has(Treaty.COMMERCE, today)) add(lines, Treaty.COMMERCE.key(), null, c.personality() == AiPersonality.MARCHAND ? 20 : 10);
        if (rel.has(Treaty.PASSAGE, today)) add(lines, Treaty.PASSAGE.key(), null, 5);
        if (rel.hostile()) add(lines, "crusadecraft.diplo.hostile", null, -30);
        if (rel.war()) add(lines, "crusadecraft.diplo.guerre", null, -100);
        if (rel.isVassalOf(ai, player)) {
            int contract = 0;
            if (rel.vassalTax() > 25) contract -= rel.vassalTax() - 25;
            if (rel.vassalLevy() > 25) contract -= (rel.vassalLevy() - 25) / 2;
            if (rel.vassalTax() < 15) contract += 5;
            add(lines, "crusadecraft.diplo.op.contract", null, contract);
        }
        if (rel.paysTribute(ai, today)) add(lines, "crusadecraft.diplo.op.tribute", null, -20);
        for (OpinionModifier m : rel.opinionOf(ai)) {
            int v = m.valueAt(today);
            if (v != 0) lines.add(new Opinion.Line(m.reason().key(), null, v));
        }
        return lines;
    }

    private static void add(List<Opinion.Line> lines, String key, String arg, int value) {
        if (value != 0) lines.add(new Opinion.Line(key, arg, value));
    }

    // ------------------------------------------------------------------ propositions

    /** Ce que le joueur peut demander par le Colombier. */
    public enum Proposal {
        NON_AGRESSION(Treaty.NON_AGRESSION, 0),
        COMMERCE(Treaty.COMMERCE, 10),
        PASSAGE(Treaty.PASSAGE, 10),
        ALLIANCE_DEFENSIVE(Treaty.ALLIANCE_DEFENSIVE, 35),
        ALLIANCE(Treaty.ALLIANCE, 60),
        VASSALISATION(null, 40),
        TRIBUT(null, 0);

        private final Treaty treaty;
        private final int threshold;

        Proposal(Treaty treaty, int threshold) {
            this.treaty = treaty;
            this.threshold = threshold;
        }

        public Treaty treaty() { return treaty; }
        public int threshold() { return threshold; }

        public String key() {
            return "crusadecraft.proposal." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Un facteur de la décision, pour l'infobulle. */
    public record Factor(String key, int value) {}

    /** La réponse prévue : acceptée si le total atteint zéro, et le détail. */
    public record Verdict(boolean accepted, int total, List<Factor> factors, String blockedKey) {
        public static Verdict blocked(String key) {
            return new Verdict(false, -999, List.of(), key);
        }
    }

    /**
     * Un souverain IA ne prête hommage qu'à plus titré que lui, sauf un seigneur indépendant (baronnie), qui
     * peut se donner à un baron voisin.
     */
    public static boolean canVassalize(TitleRank ai, TitleRank player) {
        return ai.ordinal() < player.ordinal() || ai == TitleRank.BARONNIE;
    }

    public static Verdict evaluate(Proposal p, int opinion, Context c, Relation rel, UUID ai, UUID player, int tax, int levy, long today) {
        if (rel.war()) return Verdict.blocked("crusadecraft.diplo.block.war");
        if (rel.suzerain() != null && p != Proposal.VASSALISATION) {
            if (p.treaty() != null) return Verdict.blocked("crusadecraft.diplo.block.vassal");
        }
        if (p.treaty() != null && rel.has(p.treaty(), today)) return Verdict.blocked("crusadecraft.diplo.block.already");
        if (p == Proposal.ALLIANCE_DEFENSIVE && rel.has(Treaty.ALLIANCE, today)) return Verdict.blocked("crusadecraft.diplo.block.already");
        List<Factor> f = new ArrayList<>();
        f.add(new Factor("crusadecraft.diplo.f.opinion", opinion));
        f.add(new Factor("crusadecraft.diplo.f.threshold", -p.threshold()));
        switch (p) {
            case NON_AGRESSION -> {
                if (c.personality() == AiPersonality.PACIFIQUE) f.add(new Factor(c.personality().key(), 15));
                if (c.personality() == AiPersonality.EXPANSIONNISTE && c.ratio() < 1) f.add(new Factor(c.personality().key(), -15));
            }
            case COMMERCE -> {
                if (c.personality() == AiPersonality.MARCHAND) f.add(new Factor(c.personality().key(), 25));
            }
            case ALLIANCE_DEFENSIVE, ALLIANCE -> {
                if (c.personality() == AiPersonality.PACIFIQUE) f.add(new Factor(c.personality().key(), 10));
                if (c.ratio() >= 1.5) f.add(new Factor("crusadecraft.diplo.f.strong_friend", 10));
                if (c.sameDejure()) f.add(new Factor("crusadecraft.diplo.op.dejure", -10));
            }
            case VASSALISATION -> {
                if (rel.isVassalOf(ai, player)) return Verdict.blocked("crusadecraft.diplo.block.already");
                if (!canVassalize(c.aiRank(), c.playerRank())) return Verdict.blocked("crusadecraft.diplo.block.rank");
                int strength = (int) Math.round(Math.max(-40, Math.min(40, (c.ratio() - 1) * 20)));
                f.add(new Factor("crusadecraft.diplo.f.strength", strength));
                f.add(new Factor("crusadecraft.diplo.f.dread", Math.min(20, Math.max(0, c.playerDread()) / 2)));
                f.add(new Factor("crusadecraft.diplo.f.contract", (25 - Relation.clampRate(tax)) + (25 - Relation.clampRate(levy)) / 2));
                if (c.aiRank() == TitleRank.BARONNIE) f.add(new Factor("crusadecraft.diplo.f.small_lord", 10));
                if (c.rulerTraits().contains(Trait.AMBITIEUX)) f.add(new Factor(Trait.AMBITIEUX.key(), -15));
                if (c.rulerTraits().contains(Trait.CONTENT)) f.add(new Factor(Trait.CONTENT.key(), 10));
            }
            case TRIBUT -> {
                if (rel.paysTribute(ai, today)) return Verdict.blocked("crusadecraft.diplo.block.already");
                // Un tribut s'arrache par la force : l'opinion compte peu, la peur beaucoup.
                f.set(0, new Factor("crusadecraft.diplo.f.opinion", opinion / 4));
                f.add(new Factor("crusadecraft.diplo.f.strength", (int) Math.round(Math.max(-60, Math.min(40, (c.ratio() - 1.5) * 30)))));
                f.add(new Factor("crusadecraft.diplo.f.dread", Math.min(25, Math.max(0, c.playerDread()) / 2)));
                f.add(new Factor("crusadecraft.diplo.f.pride", -15));
            }
            default -> {
            }
        }
        int total = 0;
        for (Factor x : f) total += x.value();
        return new Verdict(total >= 0, total, f, null);
    }

    // ------------------------------------------------------------------ cadeaux, insultes, tributs

    /** Opinion gagnée par un cadeau : la moitié de l'or offert, au plus 30 ; un avare apprécie davantage. */
    public static int giftOpinion(int gold, List<Trait> traits) {
        double v = Math.min(30, gold / 2.0);
        if (traits.contains(Trait.AVARE)) v *= 1.5;
        return (int) Math.round(v);
    }

    /** Tribut exigé : le dixième du revenu quotidien du royaume IA, au moins 2. */
    public static int tributeFor(long aiDailyIncome) {
        return (int) Math.max(2, Math.round(aiDailyIncome * 0.1));
    }
}
