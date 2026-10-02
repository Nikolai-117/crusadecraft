package fr.crusadecraft.core.character;

import java.util.Locale;

/**
 * Les postes qu'un citoyen peut occuper.
 *
 * <p>Les cinq postes du conseil royal sont uniques dans le royaume ; un maire l'est dans sa colonie.
 * Chaque poste s'appuie sur une aptitude. Un personnage n'occupe qu'un seul poste à la fois.
 */
public enum Post {
    CHANCELIER(Stat.DIPLOMATIE, true),
    MARECHAL(Stat.MARTIAL, true),
    INTENDANT(Stat.INTENDANCE, true),
    MAITRE_ESPION(Stat.INTRIGUE, true),
    CHAPELAIN(Stat.ERUDITION, true),
    MAIRE(Stat.INTENDANCE, false);

    private final Stat stat;
    private final boolean council;

    Post(Stat stat, boolean council) {
        this.stat = stat;
        this.council = council;
    }

    public Stat stat() { return stat; }
    public boolean isCouncil() { return council; }

    /** Nom du poste, selon le genre du titulaire (« Maréchal », « Maréchale »). */
    public String key(boolean feminine) {
        return "crusadecraft.post." + name().toLowerCase(Locale.ROOT) + (feminine ? ".f" : ".m");
    }

    public static Post byName(String name) {
        try {
            return Post.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
