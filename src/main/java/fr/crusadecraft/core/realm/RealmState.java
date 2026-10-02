package fr.crusadecraft.core.realm;

/**
 * État d'un royaume.
 *
 * <p>Un royaume dont la capitale disparaît ou change de mains n'est pas supprimé : il passe
 * en sommeil et revient tel quel si la situation se rétablit. Supprimer des données parce
 * qu'une colonie n'a pas encore fini de charger est exactement l'erreur à ne pas commettre.
 */
public enum RealmState {
    ACTIVE,
    /** Capitale introuvable dans MineColonies. */
    DORMANT_NO_CAPITAL,
    /** La capitale appartient désormais à quelqu'un d'autre que le souverain. */
    DORMANT_CAPITAL_LOST;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public String key() {
        return "crusadecraft.realm.state." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static RealmState byName(String name) {
        for (RealmState s : values()) {
            if (s.name().equals(name)) return s;
        }
        return ACTIVE;
    }
}
