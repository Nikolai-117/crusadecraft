package fr.crusadecraft.core.build.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La taille d'une ville bâtie d'après les plans (P34, refaite en P36 avec Romain) : cinq tailles, du hameau à la
 * capitale. Chacune dit le niveau de ses plans (hameau et village de 1 à 3 ; bourg 3 ; ville et capitale au plus
 * haut), ses familles, son enceinte (rien, palissade, grande muraille) et son programme : l'hôtel de ville, la place,
 * puis les bâtiments et les décors, les maisons en proportion des habitants.
 */
public enum TownSize {
    HAMEAU(1, 3, 2, 6, Walls.NONE, program(1, 3, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0)),
    VILLAGE(1, 3, 3, 10, Walls.NONE, program(1, 5, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 3, 0)),
    BOURG(3, 3, 3, 16, Walls.PALISADE, program(1, 8, 1, 1, 2, 0, 1, 1, 0, 0, 1, 0, 0, 0, 2, 5, 1)),
    VILLE(5, 5, 5, 28, Walls.BIG, program(1, 14, 1, 1, 2, 1, 2, 1, 1, 1, 2, 1, 1, 0, 4, 8, 2)),
    CAPITALE(5, 5, 5, 45, Walls.BIG, program(1, 22, 2, 1, 3, 2, 4, 1, 1, 1, 3, 2, 1, 2, 6, 12, 4)),
    /** Quelques bâtiments de plus contre une ville déjà bâtie (P28 : un quartier). */
    QUARTIER(2, 3, 3, 4, Walls.NONE, List.of(Role.HOUSE, Role.LAMP, Role.HOUSE, Role.CRAFT));

    /** Ce qu'est un bâtiment ou un décor dans la ville : on le reconnaît au nom et au dossier de son plan. */
    public enum Role {
        TOWNHALL, PLAZA, HOUSE, TAVERN, FARM, MARKET, WAREHOUSE, CHURCH, GRAVEYARD, BARRACKS, TOWER, CRAFT, HOSPITAL, SCHOOL,
        STALL, LAMP, STATUE, GARDEN, DECOR;

        /** Un décor (puits, étal, lanterne, statue, jardin, mobilier) plutôt qu'un bâtiment. */
        public boolean decor() {
            return this == PLAZA || this == STALL || this == LAMP || this == STATUE || this == GARDEN || this == DECOR;
        }
    }

    /** L'enceinte d'une ville de cette taille, faite des pièces de muraille des packs. */
    public enum Walls { NONE, PALISADE, BIG }

    private final int minLevel, maxLevel, level, families;
    private final Walls walls;
    private final List<Role> program;

    TownSize(int minLevel, int maxLevel, int level, int families, Walls walls, List<Role> program) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.level = level;
        this.families = families;
        this.walls = walls;
        this.program = program;
    }

    /**
     * Le programme d'une ville, dans l'ordre où les plans de ville le placent : la place, ses statues, le marché et ses
     * étals, l'entrepôt, la taverne, le temple et le cimetière, l'hôpital, l'école, les artisans, puis les maisons avec
     * une lanterne toutes les deux maisons, les fermes, la caserne et les tours de garde.
     */
    private static List<Role> program(int plaza, int houses, int taverns, int markets, int farms, int warehouses, int crafts, int churches,
                                      int graveyards, int hospitals, int towers, int barracks, int statues, int schools, int stalls,
                                      int lamps, int extraStatues) {
        List<Role> out = new ArrayList<>();
        for (int i = 0; i < plaza; i++) out.add(Role.PLAZA);
        for (int i = 0; i < statues; i++) out.add(Role.STATUE);
        for (int i = 0; i < markets; i++) out.add(Role.MARKET);
        for (int i = 0; i < stalls; i++) out.add(Role.STALL);
        for (int i = 0; i < warehouses; i++) out.add(Role.WAREHOUSE);
        for (int i = 0; i < taverns; i++) out.add(Role.TAVERN);
        for (int i = 0; i < churches; i++) out.add(Role.CHURCH);
        for (int i = 0; i < extraStatues; i++) out.add(Role.STATUE);
        for (int i = 0; i < graveyards; i++) out.add(Role.GRAVEYARD);
        for (int i = 0; i < hospitals; i++) out.add(Role.HOSPITAL);
        for (int i = 0; i < schools; i++) out.add(Role.SCHOOL);
        for (int i = 0; i < crafts; i++) out.add(Role.CRAFT);
        int lamp = 0;
        for (int i = 0; i < houses; i++) {
            out.add(Role.HOUSE);
            if (i % 2 == 1 && lamp < lamps) {
                out.add(Role.LAMP);
                lamp++;
            }
        }
        for (; lamp < lamps; lamp++) out.add(Role.LAMP);
        for (int i = 0; i < farms; i++) out.add(Role.FARM);
        for (int i = 0; i < barracks; i++) out.add(Role.BARRACKS);
        for (int i = 0; i < towers; i++) out.add(Role.TOWER);
        return List.copyOf(out);
    }

    /** Le niveau voulu des plans. */
    public int level() {
        return level;
    }

    /** Le plus bas niveau permis. */
    public int minLevel() {
        return minLevel;
    }

    /** Le plus haut niveau permis : jamais au-delà de 3 pour un hameau ou un village. */
    public int maxLevel() {
        return maxLevel;
    }

    /** Familles au registre des lignées quand la ville est bâtie. */
    public int families() {
        return families;
    }

    public Walls walls() {
        return walls;
    }

    /** Les bâtiments et décors autour de l'hôtel de ville (le quartier n'en a pas). */
    public List<Role> program() {
        return program;
    }

    public boolean hasCenter() {
        return this != QUARTIER;
    }

    public String key() {
        return "crusadecraft.town.size." + name().toLowerCase(Locale.ROOT);
    }

    /** La taille d'une ville de la carte d'après sa population (en milliers d'âmes, comme la carte). */
    public static TownSize forCity(int population, boolean capital) {
        if (capital) return CAPITALE;
        if (population >= 40) return VILLE;
        if (population >= 28) return BOURG;
        if (population >= 18) return VILLAGE;
        return HAMEAU;
    }

    /**
     * P36 : ce qui remplit la ville une fois son programme posé (Romain : « que toute cette place soit utilisée ») —
     * le long des rues, jusqu'à ce que plus rien ne tienne : des maisons surtout, des lanternes, des étals, des artisans, des puits,
     * des statues ; puis des jardins dans les cœurs d'îlots.
     */
    public List<Role> fill() {
        int n = switch (this) {
            case HAMEAU -> 6;
            case VILLAGE -> 16;
            case BOURG -> 70;
            case VILLE -> 240;
            case CAPITALE -> 380;
            case QUARTIER -> 0;
        };
        Role[] cycle = this == HAMEAU || this == VILLAGE
                ? new Role[]{Role.HOUSE, Role.LAMP, Role.HOUSE, Role.DECOR, Role.PLAZA}
                : new Role[]{Role.HOUSE, Role.HOUSE, Role.LAMP, Role.HOUSE, Role.DECOR, Role.HOUSE, Role.STALL, Role.HOUSE, Role.CRAFT, Role.DECOR,
                Role.HOUSE, Role.STATUE, Role.HOUSE, Role.LAMP, Role.HOUSE, Role.PLAZA, Role.DECOR};
        List<Role> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(cycle[i % cycle.length]);
        int gardens = switch (this) {
            case HAMEAU -> 2;
            case VILLAGE -> 6;
            case BOURG -> 24;
            case VILLE -> 70;
            case CAPITALE -> 110;
            case QUARTIER -> 0;
        };
        for (int i = 0; i < gardens; i++) out.add(Role.GARDEN);
        return out;
    }

    public static TownSize byOrdinal(int i) {
        TownSize[] all = values();
        return all[Math.floorMod(i, all.length)];
    }
}
