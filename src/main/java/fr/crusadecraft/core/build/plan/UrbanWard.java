package fr.crusadecraft.core.build.plan;

import java.util.List;

/** La nature d'un îlot (P60) : ce qu'on y bâtit, et de quel niveau. Le générateur les répartit d'après leur poids, et l'on peut en changer à la main. */
public enum UrbanWard {
    MARCHE(0xE3B35B, 1, 3, List.of(TownSize.Role.MARKET, TownSize.Role.STALL, TownSize.Role.WAREHOUSE, TownSize.Role.TAVERN)),
    NOBLES(0xB98FD6, 4, 5, List.of(TownSize.Role.SCHOOL, TownSize.Role.HOSPITAL, TownSize.Role.GARDEN, TownSize.Role.STATUE)),
    MARCHANDS(0xD99A5B, 2, 4, List.of(TownSize.Role.WAREHOUSE, TownSize.Role.TAVERN, TownSize.Role.CRAFT)),
    ARTISANS(0xA67C52, 1, 3, List.of(TownSize.Role.CRAFT, TownSize.Role.TAVERN)),
    HABITATIONS(0xC9B896, 1, 3, List.of(TownSize.Role.TAVERN)),
    RELIGIEUX(0x8FB5D6, 2, 5, List.of(TownSize.Role.CHURCH, TownSize.Role.GRAVEYARD, TownSize.Role.GARDEN)),
    ADMINISTRATION(0xCC8F8F, 3, 5, List.of(TownSize.Role.TOWNHALL, TownSize.Role.HOSPITAL, TownSize.Role.SCHOOL)),
    MILITAIRE(0x8C8C8C, 2, 4, List.of(TownSize.Role.BARRACKS, TownSize.Role.TOWER)),
    PORT(0x6FA3B8, 1, 3, List.of(TownSize.Role.WAREHOUSE, TownSize.Role.CRAFT, TownSize.Role.TAVERN)),
    PAUVRES(0x8F7F6A, 1, 1, List.of()),
    PARC(0x7DB069, 1, 5, List.of(TownSize.Role.GARDEN, TownSize.Role.STATUE));

    private final int color, minLevel, maxLevel;
    private final List<TownSize.Role> roles;

    UrbanWard(int color, int minLevel, int maxLevel, List<TownSize.Role> roles) {
        this.color = color;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.roles = roles;
    }

    public int color() {
        return color;
    }

    public int minLevel() {
        return minLevel;
    }

    public int maxLevel() {
        return maxLevel;
    }

    /** Ce qui y va d'abord, hors maisons. */
    public List<TownSize.Role> roles() {
        return roles;
    }

    /** Y bâtit-on des maisons ? */
    public boolean houses() {
        return this != PARC && this != MILITAIRE && this != RELIGIEUX;
    }

    public String key() {
        return "crusadecraft.urban.ward." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Le réglage qui donne son poids. */
    public UrbanParams.Def weight() {
        return switch (this) {
            case MARCHE -> UrbanParams.Q_MARCHE;
            case NOBLES -> UrbanParams.Q_NOBLES;
            case MARCHANDS -> UrbanParams.Q_MARCHANDS;
            case ARTISANS -> UrbanParams.Q_ARTISANS;
            case HABITATIONS -> UrbanParams.Q_HABITATIONS;
            case RELIGIEUX -> UrbanParams.Q_RELIGIEUX;
            case ADMINISTRATION -> UrbanParams.Q_ADMIN;
            case MILITAIRE -> UrbanParams.Q_MILITAIRE;
            case PORT -> UrbanParams.Q_PORT;
            case PAUVRES -> UrbanParams.Q_PAUVRES;
            case PARC -> UrbanParams.Q_PARC;
        };
    }
}
