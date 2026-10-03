package fr.crusadecraft.core.character;

import java.util.List;

/**
 * La famille d'un citoyen selon MineColonies : son conjoint et ses enfants, quand ils vivent dans la même colonie.
 * C'est la vraie généalogie des colonies ; CrusadeCraft n'en invente rien.
 */
public record Family(CitizenRef partner, List<CitizenRef> children) {
    public static final Family NONE = new Family(null, List.of());
}
