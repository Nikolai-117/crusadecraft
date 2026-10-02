package fr.crusadecraft.core.character;

import java.util.List;
import java.util.Locale;

/**
 * Calcule les aptitudes CK3 d'un personnage à partir des compétences MineColonies de son citoyen.
 *
 * <p>Chaque aptitude est une somme pondérée de compétences, divisée par un diviseur. Les valeurs par
 * défaut additionnent des <b>paires complémentaires</b> de MineColonies : Force et Agilité y sont
 * « adverses », leur somme aplatirait l'aptitude Martiale. Voir {@code docs/decisions.md}.
 *
 * <p>Format d'une règle (configuration) : {@code "Strength:1, Athletics:1, Stamina:0.5 / 5"}.
 */
public final class StatFormula {
    private final double[][] weights = new double[Stat.COUNT][CitizenSkill.COUNT];
    private final double[] divisors = new double[Stat.COUNT];

    public static final List<String> DEFAULT_RULES = List.of(
            "Adaptability:1, Creativity:1 / 4",
            "Strength:1, Athletics:1, Stamina:0.5 / 5",
            "Intelligence:1, Knowledge:1 / 4",
            "Dexterity:1, Agility:1 / 4",
            "Mana:1, Focus:1, Knowledge:0.5 / 5");

    private StatFormula() {}

    public static StatFormula defaults() {
        return parse(DEFAULT_RULES);
    }

    /**
     * Lit les cinq règles, dans l'ordre des {@link Stat}. Une règle illisible retombe sur la règle
     * par défaut correspondante plutôt que de casser le calcul.
     */
    public static StatFormula parse(List<? extends String> rules) {
        StatFormula f = new StatFormula();
        for (Stat stat : Stat.values()) {
            String rule = stat.ordinal() < rules.size() ? rules.get(stat.ordinal()) : null;
            if (!f.parseRule(stat, rule)) f.parseRule(stat, DEFAULT_RULES.get(stat.ordinal()));
        }
        return f;
    }

    private boolean parseRule(Stat stat, String rule) {
        if (rule == null) return false;
        double[] w = new double[CitizenSkill.COUNT];
        double divisor = 1;
        String body = rule;
        int slash = rule.lastIndexOf('/');
        try {
            if (slash >= 0) {
                divisor = Double.parseDouble(rule.substring(slash + 1).trim());
                body = rule.substring(0, slash);
            }
            if (divisor <= 0) return false;
            boolean any = false;
            for (String part : body.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                String[] kv = p.split(":");
                CitizenSkill skill = CitizenSkill.byName(kv[0].trim());
                if (skill == null) return false;
                double weight = kv.length > 1 ? Double.parseDouble(kv[1].trim().replace(',', '.')) : 1.0;
                w[skill.ordinal()] += weight;
                any = true;
            }
            if (!any) return false;
        } catch (NumberFormatException e) {
            return false;
        }
        weights[stat.ordinal()] = w;
        divisors[stat.ordinal()] = divisor;
        return true;
    }

    /** Aptitudes de base (sans éducation ni traits), arrondies, jamais négatives. */
    public int[] compute(int[] skills) {
        int[] out = new int[Stat.COUNT];
        for (int s = 0; s < Stat.COUNT; s++) {
            double sum = 0;
            for (int k = 0; k < CitizenSkill.COUNT && k < skills.length; k++) {
                sum += weights[s][k] * skills[k];
            }
            out[s] = (int) Math.max(0, Math.round(sum / divisors[s]));
        }
        return out;
    }

    /** Description lisible d'une règle, pour les infobulles : « Force + Athlétisme + ½ Endurance, ÷ 5 ». */
    public double weight(Stat stat, CitizenSkill skill) {
        return weights[stat.ordinal()][skill.ordinal()];
    }

    public double divisor(Stat stat) {
        return divisors[stat.ordinal()];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Stat s : Stat.values()) {
            sb.append(s).append('=');
            for (CitizenSkill k : CitizenSkill.values()) {
                double w = weight(s, k);
                if (w != 0) sb.append(k).append(':').append(String.format(Locale.ROOT, "%.2f", w)).append(' ');
            }
            sb.append("/ ").append(divisor(s)).append("; ");
        }
        return sb.toString();
    }
}
