package fr.crusadecraft.core.character;

import java.util.Locale;

/** Les cinq aptitudes d'un personnage, à la manière de CK3. */
public enum Stat {
    DIPLOMATIE, MARTIAL, INTENDANCE, INTRIGUE, ERUDITION;

    public static final int COUNT = values().length;

    public String key() {
        return "crusadecraft.stat." + name().toLowerCase(Locale.ROOT);
    }
}
