package fr.crusadecraft.core.world.terrain;

/**
 * Les couleurs de la carte en terrain (P18b), en logique pure : la teinte d'un paysage selon son genre, son altitude
 * et la profondeur de l'eau, puis l'ombre du relief. Le style est celui d'un atlas peint, pas d'une photo : des
 * teintes douces, des mers qui s'assombrissent au large, des sommets qui blanchissent.
 */
public final class TerrainPalette {
    /** Les genres de paysage. L'ordre compte : il voyage par son rang. */
    public enum Kind {
        DEEP_OCEAN(0x1F4E79), OCEAN(0x2E6DA4), FROZEN_OCEAN(0x7FA6C9), RIVER(0x3A7BC0), BEACH(0xE3D3A0),
        PLAINS(0x8DB360), MEADOW(0x9CC46B), FOREST(0x4C7A34), DARK_FOREST(0x34592A), BIRCH_FOREST(0x6A9A45),
        TAIGA(0x4F6E4A), SNOWY_TAIGA(0xB9C9C0), SNOWY(0xEDF2F5), JUNGLE(0x2F7A2A), SAVANNA(0xBFB257),
        DESERT(0xE2CF8A), BADLANDS(0xC2733A), SWAMP(0x5A6B3A), MANGROVE(0x4F6A30), MUSHROOM(0x9C7FA0),
        CHERRY(0xE8B4C8), HILLS(0x7E8C6A), MOUNTAIN(0x8A8A80), PEAK(0xF0F0F0), NETHER(0x7A2E2A), END(0xDCDDA0),
        VOID(0x15151A);

        public final int color;

        Kind(int color) {
            this.color = color;
        }

        public boolean water() {
            return this == DEEP_OCEAN || this == OCEAN || this == FROZEN_OCEAN || this == RIVER;
        }

        public static Kind byOrdinal(int i) {
            Kind[] all = values();
            return i >= 0 && i < all.length ? all[i] : PLAINS;
        }
    }

    /** La mer au large et l'eau peu profonde : on passe de l'une à l'autre avec la profondeur. */
    static final int SHALLOW = 0x5C9BD1, DEEP = 0x173B60;
    private static final int ROCK = 0x9A9A8E, SNOWCAP = 0xF4F6F8;

    private TerrainPalette() {}

    /**
     * La teinte d'un point, avant l'ombre du relief.
     *
     * @param height     altitude de la surface (le fond, sous l'eau)
     * @param waterDepth profondeur de l'eau au-dessus du fond, 0 sur la terre ferme
     * @param seaLevel   niveau de la mer de ce monde
     */
    public static int color(Kind kind, int height, int waterDepth, int seaLevel) {
        if (kind == Kind.VOID || kind == Kind.NETHER || kind == Kind.END) return kind.color;
        if (waterDepth > 0) {
            if (kind == Kind.FROZEN_OCEAN) return mix(0xC8DCEB, kind.color, Math.min(1, waterDepth / 12.0));
            // Peu profond : clair ; au large : sombre. Une rivière ou un lac restent clairs.
            double t = Math.min(1, waterDepth / 30.0);
            int base = kind.water() ? kind.color : SHALLOW;
            return t < 0.25 ? mix(SHALLOW, base, t / 0.25) : mix(base, DEEP, (t - 0.25) / 0.75 * 0.8);
        }
        int c = kind.color;
        int above = height - seaLevel;
        // La roche puis la neige gagnent en altitude, sauf sur ce qui est déjà blanc ou minéral.
        if (kind != Kind.SNOWY && kind != Kind.PEAK && kind != Kind.DESERT && kind != Kind.BADLANDS && kind != Kind.BEACH) {
            if (above > 70) c = mix(c, ROCK, Math.min(0.75, (above - 70) / 60.0));
        }
        if (above > 130) c = mix(c, SNOWCAP, Math.min(0.9, (above - 130) / 40.0));
        return c;
    }

    /**
     * L'ombre du relief, la lumière venant du nord-ouest : une pente qui monte vers elle s'éclaire, l'autre
     * s'assombrit. {@code spacing} est l'écart en blocs entre deux points, pour qu'une même pente garde la même ombre
     * à tous les zooms.
     */
    public static int shade(int rgb, int height, int heightNorthWest, int spacing) {
        double slope = (heightNorthWest - height) / (double) Math.max(1, spacing) * 16;
        double k = Math.max(-0.35, Math.min(0.3, -slope * 0.06));
        return k >= 0 ? mix(rgb, 0xFFFFFF, k) : mix(rgb, 0x000000, -k);
    }

    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
