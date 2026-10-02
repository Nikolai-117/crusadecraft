package fr.crusadecraft.core.treasury;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Barème par bâtiment MineColonies : un montant de base et un montant par niveau, en or par jour.
 * Repris de War 'N Taxes (impôt = base + par niveau × niveau), à l'échelle d'une journée du monde.
 *
 * <p>Format d'une ligne de configuration : {@code "townhall=3:1"} (base 3, plus 1 par niveau). Le nom est
 * celui du bâtiment dans MineColonies, avec ou sans {@code minecolonies:}.
 */
public final class TaxTable {

    public record Rate(double base, double perLevel) {
        public double at(int level) {
            return Math.max(0, base + perLevel * level);
        }
    }

    private final Map<String, Rate> rates = new HashMap<>();
    private final Rate fallback;

    public TaxTable(Rate fallback) {
        this.fallback = fallback;
    }

    /** Lit des lignes {@code nom=base:parNiveau} ; les lignes illisibles sont ignorées. */
    public static TaxTable parse(List<? extends String> lines, Rate fallback) {
        TaxTable t = new TaxTable(fallback);
        for (String line : lines) {
            Map.Entry<String, Rate> e = parseLine(line);
            if (e != null) t.rates.put(e.getKey(), e.getValue());
        }
        return t;
    }

    static Map.Entry<String, Rate> parseLine(String line) {
        if (line == null) return null;
        int eq = line.indexOf('=');
        if (eq <= 0) return null;
        String name = normalize(line.substring(0, eq));
        String[] parts = line.substring(eq + 1).split(":");
        try {
            double base = Double.parseDouble(parts[0].trim());
            double per = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 0;
            if (name.isEmpty() || base < 0 || per < 0) return null;
            return Map.entry(name, new Rate(base, per));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String normalize(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }

    /** Le barème d'un bâtiment, ou celui par défaut s'il n'est pas listé. */
    public Rate rate(String buildingId) {
        return rates.getOrDefault(normalize(buildingId), fallback);
    }

    /** Vrai si le bâtiment est listé explicitement (utile pour l'entretien, où rien n'est dû par défaut). */
    public boolean lists(String buildingId) {
        return rates.containsKey(normalize(buildingId));
    }
}
