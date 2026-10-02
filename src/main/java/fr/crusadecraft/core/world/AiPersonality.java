package fr.crusadecraft.core.world;

import java.util.Locale;

/**
 * Le tempérament d'un royaume IA. Il oriente son économie dès maintenant, et ses décisions plus tard
 * (diplomatie, guerre).
 */
public enum AiPersonality {
    /** Cherche à s'agrandir : plus de levées, moins d'or. */
    EXPANSIONNISTE(0.9, 1.3),
    /** Vit du commerce : plus d'or, peu d'armées. */
    MARCHAND(1.4, 0.8),
    /** Préfère la paix : économie stable, armée défensive. */
    PACIFIQUE(1.1, 0.9),
    /** Mû par la foi : piété, croisades, intolérance. */
    FANATIQUE(0.9, 1.2),
    /** Attaque les faibles, s'allie aux forts. */
    OPPORTUNISTE(1.0, 1.0);

    private final double incomeFactor;
    private final double levyFactor;

    AiPersonality(double incomeFactor, double levyFactor) {
        this.incomeFactor = incomeFactor;
        this.levyFactor = levyFactor;
    }

    public double incomeFactor() { return incomeFactor; }
    public double levyFactor() { return levyFactor; }

    public String key() {
        return "crusadecraft.personality." + name().toLowerCase(Locale.ROOT);
    }

    public static AiPersonality byName(String name) {
        try {
            return AiPersonality.valueOf(name);
        } catch (IllegalArgumentException e) {
            return OPPORTUNISTE;
        }
    }
}
