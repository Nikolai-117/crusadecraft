package fr.crusadecraft.core.build.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La taille d'une ville bâtie d'après les plans (P34), et ce qu'elle compte : un hôtel de ville au milieu, une place
 * devant, puis maisons, taverne, fermes, marché, temple, caserne et tours. Le niveau voulu des plans (1 à 5, celui
 * des cabanes de MineColonies) grandit avec la ville.
 */
public enum TownSize {
    HAMEAU(2, 6, roles(Role.HOUSE, 3, Role.FARM, 1)),
    BOURG(3, 10, roles(Role.PLAZA, 1, Role.HOUSE, 5, Role.TAVERN, 1, Role.FARM, 1, Role.TOWER, 1)),
    VILLE(4, 16, roles(Role.PLAZA, 1, Role.HOUSE, 8, Role.TAVERN, 1, Role.FARM, 2, Role.MARKET, 1, Role.CHURCH, 1, Role.BARRACKS, 1, Role.TOWER, 1)),
    CAPITALE(5, 24, roles(Role.PLAZA, 1, Role.HOUSE, 11, Role.TAVERN, 1, Role.FARM, 2, Role.MARKET, 1, Role.CHURCH, 1, Role.BARRACKS, 1,
            Role.TOWER, 2, Role.CRAFT, 2)),
    /** Quelques bâtiments de plus contre une ville déjà bâtie (P28 : un quartier). */
    QUARTIER(3, 4, roles(Role.HOUSE, 2, Role.CRAFT, 1));

    /** Ce qu'est un bâtiment dans la ville : on le reconnaît au nom de son plan. */
    public enum Role { TOWNHALL, PLAZA, HOUSE, TAVERN, FARM, MARKET, CHURCH, BARRACKS, TOWER, CRAFT }

    private final int level, families;
    private final List<Role> program;

    TownSize(int level, int families, List<Role> program) {
        this.level = level;
        this.families = families;
        this.program = program;
    }

    private static List<Role> roles(Object... pairs) {
        List<Role> out = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) for (int n = 0; n < (Integer) pairs[i + 1]; n++) out.add((Role) pairs[i]);
        return List.copyOf(out);
    }

    /** Le niveau voulu des plans. */
    public int level() {
        return level;
    }

    /** Familles au registre des lignées quand la ville est bâtie. */
    public int families() {
        return families;
    }

    /** Les bâtiments autour de l'hôtel de ville (le quartier n'en a pas). */
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
        if (population >= 20) return BOURG;
        return HAMEAU;
    }

    public static TownSize byOrdinal(int i) {
        TownSize[] all = values();
        return all[Math.floorMod(i, all.length)];
    }
}
