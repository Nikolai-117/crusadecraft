package fr.crusadecraft.core.treasury;

import java.util.Locale;

/** Les postes du livre de comptes du royaume, recettes puis dépenses. */
public enum LedgerCategory {
    IMPOTS(true),
    DOMAINE(true),
    VASSAUX(true),
    TRIBUTS(true),
    COMMERCE(true),
    CONQUETES(true),
    BUTIN(true),
    DEPOTS(true),
    RETRAITS(false),
    SALAIRES(false),
    ENTRETIEN(false),
    REQUETES(false),
    RECOMPENSES(false),
    DIPLOMATIE(false),
    TITRES(false),
    ARMEE(false),
    PILLAGE(false),
    EVENEMENTS(false),
    AUTRES(false);

    private final boolean income;

    LedgerCategory(boolean income) {
        this.income = income;
    }

    /** Vrai pour une recette (les montants y sont positifs). */
    public boolean income() { return income; }

    public String key() {
        return "crusadecraft.ledger." + name().toLowerCase(Locale.ROOT);
    }

    public static LedgerCategory byName(String name) {
        try {
            return LedgerCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
