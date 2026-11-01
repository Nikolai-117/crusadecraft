package fr.crusadecraft.core.build;

import java.util.Locale;

/**
 * Ce qu'on peut construire pour un royaume (P14). Chaque construction étend le territoire autour d'elle ; celles
 * qui ont une garnison se défendent et se prennent en guerre, comme une capitale.
 *
 * @param radius   territoire autour d'elle, en blocs
 * @param garrison soldats qui la défendent pendant un siège (0 : elle ne se défend pas)
 * @param cost     or demandé au trésor en survie (chantier progressif)
 * @param size     côté de son emprise au sol, en blocs (pour l'aperçu et le terrain)
 * @param survival un joueur peut la bâtir en survie pour son propre royaume
 */
public enum SiteKind {
    BORNE(32, 0, 40, 1, true),
    TOUR(64, 3, 150, 5, true),
    CAMP(64, 6, 200, 11, true),
    AVANT_POSTE(96, 5, 300, 15, true),
    FORT(144, 10, 900, 25, true),
    DONJON(160, 12, 1500, 11, false),
    VILLAGE(110, 0, 600, 25, false),
    /** P28 : deux maisons de plus contre un village, qui le font grandir. */
    QUARTIER(48, 0, 300, 13, false);

    private final int radius, garrison, cost, size;
    private final boolean survival;

    SiteKind(int radius, int garrison, int cost, int size, boolean survival) {
        this.radius = radius;
        this.garrison = garrison;
        this.cost = cost;
        this.size = size;
        this.survival = survival;
    }

    public int radius() { return radius; }
    public int garrison() { return garrison; }
    public int cost() { return cost; }
    public int size() { return size; }
    public boolean survival() { return survival; }

    /** Une place forte : elle se défend et se prend par la bannière. */
    public boolean capturable() {
        return garrison > 0;
    }

    /** Elle fonde une ville (village) ou loge un souverain (donjon) : elle est rattachée à une province. */
    public boolean settlement() {
        return this == VILLAGE || this == DONJON;
    }

    public String key() {
        return "crusadecraft.site." + name().toLowerCase(Locale.ROOT);
    }

    public static SiteKind byName(String name, SiteKind fallback) {
        try {
            return SiteKind.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    public static SiteKind byOrdinal(int i) {
        SiteKind[] all = values();
        return all[Math.floorMod(i, all.length)];
    }
}
