package fr.crusadecraft.core.council;

import java.util.List;
import java.util.Locale;

/**
 * Les requêtes qu'un dignitaire vient présenter au souverain, comme les événements de CK3 : un texte,
 * et des choix aux effets annoncés.
 *
 * @param cooldownDays délai minimal entre deux requêtes de ce type pour un même personnage
 */
public enum RequestType {
    MAYOR_HOMELESS(3, List.of(Choice.PROMISE, Choice.IGNORE)),
    MAYOR_JOBLESS(3, List.of(Choice.PROMISE, Choice.IGNORE)),
    MAYOR_SICK(3, List.of(Choice.PAY_HEALER, Choice.PRAY, Choice.IGNORE)),
    MAYOR_HUNGER(2, List.of(Choice.OPEN_GRANARIES, Choice.TIGHTEN_BELTS)),
    SPY_RAID_WARNING(1, List.of(Choice.TO_ARMS, Choice.ACKNOWLEDGE)),
    GRIEVANCE(5, List.of(Choice.GRANT_FAVOUR, Choice.THREATEN, Choice.IGNORE)),
    AMBITION(7, List.of(Choice.PROMISE_HONOURS, Choice.REFUSE));

    /** Les réponses possibles. Le coût et l'effet sont appliqués par {@link RequestService}. */
    public enum Choice {
        PROMISE, IGNORE, PAY_HEALER, PRAY, OPEN_GRANARIES, TIGHTEN_BELTS, TO_ARMS, ACKNOWLEDGE,
        GRANT_FAVOUR, THREATEN, PROMISE_HONOURS, REFUSE;

        public String key() {
            return "crusadecraft.choice." + name().toLowerCase(Locale.ROOT);
        }

        public String effectKey() {
            return key() + ".effect";
        }
    }

    private final int cooldownDays;
    private final List<Choice> choices;

    RequestType(int cooldownDays, List<Choice> choices) {
        this.cooldownDays = cooldownDays;
        this.choices = choices;
    }

    public int cooldownDays() { return cooldownDays; }
    public List<Choice> choices() { return choices; }

    public String titleKey() {
        return "crusadecraft.request." + name().toLowerCase(Locale.ROOT) + ".title";
    }

    /** Texte de la requête ; arguments : formule d'adresse, nom de la colonie, nombre concerné. */
    public String textKey() {
        return "crusadecraft.request." + name().toLowerCase(Locale.ROOT) + ".text";
    }

    public static RequestType byName(String name) {
        try {
            return RequestType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
