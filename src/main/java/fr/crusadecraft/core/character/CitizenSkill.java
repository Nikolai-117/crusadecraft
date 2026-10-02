package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * Les onze compétences des citoyens MineColonies, dans l'ordre de leur énumération {@code Skill}.
 *
 * <p>Copie volontaire : le cœur de CrusadeCraft ne dépend pas des classes de MineColonies. Le pont
 * fait la correspondance par le nom.
 */
public enum CitizenSkill {
    Athletics, Dexterity, Strength, Agility, Stamina, Mana, Adaptability, Focus, Creativity, Knowledge, Intelligence;

    public static final int COUNT = values().length;

    public String key() {
        return "crusadecraft.skill." + name().toLowerCase(Locale.ROOT);
    }

    public static CitizenSkill byName(String name) {
        for (CitizenSkill s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        return null;
    }
}
