package fr.crusadecraft.core.lineage;

import java.util.Locale;

/**
 * Ce qu'une personne fait dans le monde. Le rôle donne aussi son métier au villageois de Comes Alive qui l'incarne
 * (des métiers ordinaires de Minecraft, ou ceux de Comes Alive : garde, archer).
 */
public enum Role {
    SOUVERAIN(null),
    CONSORT(null),
    HERITIER(null),
    FAMILLE(null),
    CONSEILLER("minecraft:librarian"),
    CHEVALIER("mca:guard"),
    SOLDAT("mca:archer"),
    CLERC("minecraft:cleric"),
    ERUDIT("minecraft:librarian"),
    MARCHAND("minecraft:cartographer"),
    ARTISAN("minecraft:toolsmith"),
    PAYSAN("minecraft:farmer"),
    AUCUN(null);

    private final String profession;

    Role(String profession) {
        this.profession = profession;
    }

    /** Le métier du villageois Comes Alive, ou {@code null} pour aucun (les nobles ne travaillent pas). */
    public String profession() {
        return profession;
    }

    public String key() {
        return "crusadecraft.lineage.role." + name().toLowerCase(Locale.ROOT);
    }

    public boolean noble() {
        return this == SOUVERAIN || this == CONSORT || this == HERITIER || this == FAMILLE;
    }

    public static Role byName(String name, Role fallback) {
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
