package fr.crusadecraft.core.logic;

/**
 * Peut-on fonder un royaume ici ? Logique pure, testée sans Minecraft.
 *
 * <p>Les vérifications sont ordonnées pour donner au joueur le motif le plus utile en premier :
 * inutile de parler du niveau de l'Hôtel de ville à quelqu'un qui n'est pas dans une colonie.
 */
public final class FoundingRules {

    public enum Verdict {
        OK("ok"),
        COLONIES_NOT_READY("colonies_not_ready"),
        ALREADY_RULER("already_ruler"),
        NOT_IN_COLONY("not_in_colony"),
        NOT_OWNER("not_owner"),
        NOT_IN_TOWN_HALL("not_in_town_hall"),
        TOWN_HALL_TOO_LOW("town_hall_too_low"),
        COLONY_ALREADY_CAPITAL("colony_already_capital");

        private final String key;

        Verdict(String key) {
            this.key = key;
        }

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return "crusadecraft.founding." + key;
        }
    }

    /**
     * @param coloniesReady    MineColonies a fini de charger ses colonies
     * @param alreadyRuler     le joueur règne déjà sur un royaume
     * @param inColony         la position est dans une colonie
     * @param isOwner          le joueur est propriétaire de cette colonie
     * @param inTownHall       la position est dans l'Hôtel de ville (ou la règle est désactivée)
     * @param townHallLevel    niveau de l'Hôtel de ville
     * @param minTownHallLevel niveau exigé par la configuration
     * @param colonyIsCapital  la colonie est déjà la capitale d'un autre royaume
     */
    public static Verdict check(boolean coloniesReady, boolean alreadyRuler, boolean inColony, boolean isOwner,
                                boolean inTownHall, int townHallLevel, int minTownHallLevel,
                                boolean colonyIsCapital) {
        if (!coloniesReady) return Verdict.COLONIES_NOT_READY;
        if (alreadyRuler) return Verdict.ALREADY_RULER;
        if (!inColony) return Verdict.NOT_IN_COLONY;
        if (!isOwner) return Verdict.NOT_OWNER;
        if (!inTownHall) return Verdict.NOT_IN_TOWN_HALL;
        if (townHallLevel < minTownHallLevel) return Verdict.TOWN_HALL_TOO_LOW;
        if (colonyIsCapital) return Verdict.COLONY_ALREADY_CAPITAL;
        return Verdict.OK;
    }

    private FoundingRules() {}
}
