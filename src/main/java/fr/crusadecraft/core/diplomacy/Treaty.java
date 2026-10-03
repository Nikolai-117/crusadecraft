package fr.crusadecraft.core.diplomacy;

import java.util.Locale;

/** Les traités entre deux royaumes, avec leur durée en jours (permanents : jusqu'à rupture). */
public enum Treaty {
    NON_AGRESSION(60),
    COMMERCE(40),
    PASSAGE(30),
    ALLIANCE_DEFENSIVE(-1),
    ALLIANCE(-1);

    private final int days;

    Treaty(int days) {
        this.days = days;
    }

    public boolean permanent() { return days < 0; }
    public int days() { return days; }

    public long expiry(long today) {
        return permanent() ? Long.MAX_VALUE : today + days;
    }

    public String key() {
        return "crusadecraft.treaty." + name().toLowerCase(Locale.ROOT);
    }

    public static Treaty byName(String name) {
        try {
            return Treaty.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
