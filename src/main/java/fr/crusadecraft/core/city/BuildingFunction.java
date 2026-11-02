package fr.crusadecraft.core.city;

import fr.crusadecraft.core.lineage.Role;

import java.util.List;
import java.util.Locale;

/**
 * Ce qu'un bâtiment d'une ville est (P38), donné au bâton d'intendant : une maison, la mairie, la forge… Chaque
 * fonction amène ses gens : des familles dans une maison, un boulanger qui vend son pain, des gardes à la caserne.
 * Ce sont des étagères, pas du lore : rien ne dit qui habite où avant que le joueur le décide.
 *
 * @param role   leur rôle au registre des lignées
 * @param posts  les métiers de ceux qui y travaillent (un métier de Minecraft ou de Comes Alive), dans l'ordre
 * @param trades ce qu'ils vendent
 * @param icon   l'objet qui la représente dans l'écran du bâton
 * @param color  sa couleur sur le cadre et le plan
 */
public enum BuildingFunction {
    MAISON(Role.AUCUN, List.of(), Trades.NONE, "minecraft:red_bed", 0xB5714A),
    MAIRIE(Role.CONSEILLER, List.of("minecraft:cartographer"), Trades.VANILLA, "minecraft:bell", 0xE7BD55),
    CHATEAU(Role.CHEVALIER, List.of("minecraft:librarian", "mca:guard", "mca:guard"), Trades.VANILLA, "minecraft:golden_helmet", 0x9C8F7A),
    TEMPLE(Role.CLERC, List.of("minecraft:cleric"), Trades.VANILLA, "minecraft:candle", 0xC9D6E8),
    CASERNE(Role.SOLDAT, List.of("mca:guard", "mca:guard"), Trades.NONE, "minecraft:iron_sword", 0xB0413A),
    TOUR(Role.SOLDAT, List.of("mca:archer"), Trades.NONE, "minecraft:bow", 0x8E8E8E),
    BOULANGERIE(Role.ARTISAN, List.of("minecraft:farmer"), Trades.BAKER, "minecraft:bread", 0xE0B060),
    TAVERNE(Role.MARCHAND, List.of("minecraft:butcher"), Trades.TAVERN, "minecraft:mushroom_stew", 0xE0913A),
    FORGE(Role.ARTISAN, List.of("minecraft:weaponsmith"), Trades.SMITH, "minecraft:anvil", 0x6E6E6E),
    MARCHE(Role.MARCHAND, List.of("minecraft:farmer", "minecraft:fisherman", "minecraft:shepherd"), Trades.VANILLA, "minecraft:emerald", 0xD9C24A),
    BOUCHERIE(Role.ARTISAN, List.of("minecraft:butcher"), Trades.VANILLA, "minecraft:porkchop", 0xC0504A),
    FERME(Role.PAYSAN, List.of("minecraft:farmer"), Trades.VANILLA, "minecraft:wheat", 0x7FAE4E),
    ECURIE(Role.PAYSAN, List.of("minecraft:leatherworker"), Trades.VANILLA, "minecraft:saddle", 0x9A6B3F),
    BIBLIOTHEQUE(Role.ERUDIT, List.of("minecraft:librarian"), Trades.VANILLA, "minecraft:book", 0x8FA8C9),
    ATELIER(Role.ARTISAN, List.of("minecraft:mason"), Trades.VANILLA, "minecraft:stonecutter", 0xA88B5E),
    ENTREPOT(Role.MARCHAND, List.of("minecraft:none"), Trades.NONE, "minecraft:barrel", 0xC9A86A);

    /** Ce que vendent ses gens : les échanges de leur métier, ceux du boulanger, du tavernier, du forgeron, ou rien. */
    public enum Trades { NONE, VANILLA, BAKER, TAVERN, SMITH }

    private final Role role;
    private final List<String> posts;
    private final Trades trades;
    private final String icon;
    private final int color;

    BuildingFunction(Role role, List<String> posts, Trades trades, String icon, int color) {
        this.role = role;
        this.posts = posts;
        this.trades = trades;
        this.icon = icon;
        this.color = color;
    }

    public Role role() { return role; }
    public List<String> posts() { return posts; }
    public Trades trades() { return trades; }
    public String icon() { return icon; }
    public int color() { return color; }

    /** Une maison loge des familles ; les autres, ceux qui y travaillent. */
    public boolean dwelling() {
        return this == MAISON;
    }

    /** Ceux qui y travaillent, selon la taille : un garde de plus par 60 cases de sol à la caserne (six au plus). */
    public List<String> postsFor(int floor) {
        if (this == CASERNE) {
            int n = Math.min(6, 2 + floor / 60);
            return java.util.Collections.nCopies(n, "mca:guard");
        }
        if (this == FERME && floor >= 80) return List.of("minecraft:farmer", "minecraft:farmer");
        return posts;
    }

    /** Les familles d'une maison : une, deux dans une grande maison. */
    public static int families(int floor) {
        return floor >= 70 ? 2 : 1;
    }

    public String key() {
        return "crusadecraft.city.function." + name().toLowerCase(Locale.ROOT);
    }

    public static BuildingFunction byName(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static BuildingFunction byOrdinal(int i) {
        BuildingFunction[] all = values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
}
