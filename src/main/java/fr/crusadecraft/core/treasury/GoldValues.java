package fr.crusadecraft.core.treasury;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ce que vaut l'or physique au Coffre du Trésor : {@code "minecraft:gold_ingot=9"}. L'unité est la pièce
 * d'or ; par défaut une pépite vaut 1, un lingot 9 et un bloc 81, comme au craft.
 */
public final class GoldValues {

    public record Denomination(ResourceLocation item, int value) {}

    public static final List<String> DEFAULTS = List.of(
            "minecraft:gold_nugget=1", "minecraft:gold_ingot=9", "minecraft:gold_block=81");

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

    /** Valeur d'un objet, 0 s'il n'est pas de l'or reconnu. */
    public int valueOf(ResourceLocation item) {
        for (Denomination d : denominations) if (d.item().equals(item)) return d.value();
        return 0;
    }
}
