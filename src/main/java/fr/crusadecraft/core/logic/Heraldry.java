package fr.crusadecraft.core.logic;

/**
 * Les émaux héraldiques proposés pour la couleur d'un royaume.
 *
 * <p>Seize teintes lisibles sur la carte, dans l'ordre où l'écran de fondation les présente.
 */
public enum Heraldry {
    GUEULES(0xB3261E),
    AZUR(0x1F4E9C),
    SINOPLE(0x2E7D32),
    SABLE(0x2B2B2B),
    OR(0xD4A017),
    ARGENT(0xD9D9D9),
    POURPRE(0x7B2D8B),
    ORANGE(0xD9732B),
    CARNATION(0xE8A88E),
    TENNE(0x8A5A2B),
    SANGUINE(0x7A1E2C),
    CELESTE(0x6FA8DC),
    VERT_PRE(0x7CB342),
    ACIER(0x5E6B78),
    ROSE(0xD86C9A),
    TURQUOISE(0x1E8C8C);

    private final int rgb;

    Heraldry(int rgb) {
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }

    public String key() {
        return "crusadecraft.tincture." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Ramène n'importe quelle couleur reçue du client à l'émail le plus proche. */
    public static Heraldry nearest(int rgb) {
        Heraldry best = GUEULES;
        long bestDist = Long.MAX_VALUE;
        for (Heraldry h : values()) {
            int dr = ((h.rgb >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
            int dg = ((h.rgb >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
            int db = (h.rgb & 0xFF) - (rgb & 0xFF);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestDist) {
                bestDist = d;
                best = h;
            }
        }
        return best;
    }

    public static Heraldry byOrdinal(int index) {
        Heraldry[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
