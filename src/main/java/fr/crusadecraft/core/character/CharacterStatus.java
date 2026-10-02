package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * Situation d'un personnage vis-à-vis de la couronne.
 *
 * <p>Ce n'est qu'un état dans nos données : le citoyen continue sa vie dans MineColonies. Un prisonnier
 * ou un exilé ne peut simplement plus occuper de poste, et il en garde rancune.
 */
public enum CharacterStatus {
    LIBRE, EMPRISONNE, EXILE;

    public String key() {
        return "crusadecraft.status." + name().toLowerCase(Locale.ROOT);
    }

    public static CharacterStatus byName(String name) {
        for (CharacterStatus s : values()) {
            if (s.name().equals(name)) return s;
        }
        return LIBRE;
    }
}
