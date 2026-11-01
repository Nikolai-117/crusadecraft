package fr.crusadecraft.core.treasury;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ce que vaut l'or physique au Coffre du Trésor : {@code "minecraft:gold_ingot=9"}. L'unité est la pièce
 * d'or ; par défaut une pépite vaut 1, un lingot 9 et un bloc 81, comme au craft.
 */
public final class GoldValues {

    public record Denomination(ResourceLocation item, int value) {}

    public static final List<String> DEFAULTS = List.of(
            "minecraft:gold_nugget=1", "minecraft:gold_ingot=9", "minecraft:gold_block=81");

    /** Le mod des pièces State Marks (P29) : bronze, argent, or. */
    public static final String STATE_MARKS_MOD = "stans_funtime_rapey";
    public static final List<String> STATE_MARKS = List.of(STATE_MARKS_MOD + ":bronze_state_mark=1", STATE_MARKS_MOD + ":silver_state_mark=9",
            STATE_MARKS_MOD + ":gold_state_mark=81");

    private final List<Denomination> denominations;

    private GoldValues(List<Denomination> denominations) {
        this.denominations = denominations;
    }

    /** Du plus précieux au moins précieux ; lignes illisibles ignorées. */
    public static GoldValues parse(List<? extends String> lines) {
        List<Denomination> out = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            int eq = line.lastIndexOf('=');
            if (eq <= 0) continue;
            ResourceLocation id = ResourceLocation.tryParse(line.substring(0, eq).trim());
            try {
                int value = Integer.parseInt(line.substring(eq + 1).trim());
                if (id != null && value > 0 && out.stream().noneMatch(d -> d.item().equals(id))) out.add(new Denomination(id, value));
            } catch (NumberFormatException ignored) {
                // ligne ignorée
            }
        }
        out.sort(Comparator.comparingInt(Denomination::value).reversed());
        return new GoldValues(List.copyOf(out));
    }

    public List<Denomination> denominations() { return denominations; }

    /** Ces valeurs, et celles-ci en plus (sans doublon). */
    public GoldValues with(List<? extends String> more) {
        List<String> lines = new ArrayList<>();
        for (Denomination d : denominations) lines.add(d.item() + "=" + d.value());
        lines.addAll(more);
        return parse(lines);
    }

    /**
     * De quoi payer {@code amount} pièces en objets, les plus précieux d'abord, parmi ceux de {@code preferred} s'ils
     * suffisent. Renvoie {objet, nombre} ; logique pure.
     */
    public List<Map.Entry<ResourceLocation, Integer>> change(long amount, java.util.function.Predicate<ResourceLocation> preferred) {
        List<Denomination> pool = new ArrayList<>();
        for (Denomination d : denominations) if (preferred.test(d.item())) pool.add(d);
        if (pool.isEmpty() || pool.stream().noneMatch(d -> d.value() == 1)) pool = new ArrayList<>(denominations);
        List<Map.Entry<ResourceLocation, Integer>> out = new ArrayList<>();
        long left = amount;
        for (Denomination d : pool) {
            long n = left / d.value();
            if (n <= 0) continue;
            out.add(Map.entry(d.item(), (int) Math.min(Integer.MAX_VALUE, n)));
            left -= n * d.value();
        }
        return out;
    }

    /** Valeur d'un objet, 0 s'il n'est pas de l'or reconnu. */
    public int valueOf(ResourceLocation item) {
        for (Denomination d : denominations) if (d.item().equals(item)) return d.value();
        return 0;
    }
}
