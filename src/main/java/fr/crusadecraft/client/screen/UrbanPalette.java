package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.build.plan.UrbanGenerator;
import fr.crusadecraft.core.build.plan.UrbanWard;

/** Les couleurs du plan de l'atelier (P60) : un parchemin, de l'encre, de l'eau grise-bleue. Une carte d'atlas plutôt qu'une vue de jeu : on doit y lire la ville d'un coup d'œil. */
final class UrbanPalette {
    private static final int PAPER = 0xD8CCB2, PAPER_DARK = 0xCDBFA3;

    private UrbanPalette() {}

    private static int rgb(int kind, int x, int z) {
        if (kind >= UrbanGenerator.VIEW_HOUSE && kind < UrbanGenerator.VIEW_HOUSE + UrbanWard.values().length) {
            return mix(UrbanWard.values()[kind - UrbanGenerator.VIEW_HOUSE].color(), 0x3A2E22, 0.55);
        }
        if (kind >= UrbanGenerator.VIEW_LOT && kind < UrbanGenerator.VIEW_LOT + UrbanWard.values().length) {
            return mix(UrbanWard.values()[kind - UrbanGenerator.VIEW_LOT].color(), PAPER, 0.62);
        }
        return switch (kind) {
            case UrbanGenerator.LAND -> ((x / 16 + z / 16) & 1) == 0 ? PAPER : PAPER_DARK;
            case UrbanGenerator.SEA, UrbanGenerator.RIVER, UrbanGenerator.MOAT -> 0x7C93A3;
            case UrbanGenerator.ROAD_MAIN -> 0xF2E8D2;
            case UrbanGenerator.ROAD -> 0xEDE1C8;
            case UrbanGenerator.ALLEY, UrbanGenerator.PATH -> 0xE6D8BC;
            case UrbanGenerator.PLAZA -> 0xF7EFDC;
            case UrbanGenerator.BRIDGE, UrbanGenerator.PIER -> 0x8A6A44;
            case UrbanGenerator.WALL -> 0x3A3128;
            case UrbanGenerator.TOWER -> 0x1F1A14;
            case UrbanGenerator.GATE -> 0xB79A63;
            case UrbanGenerator.BUILDING -> 0x6B5A46;
            case UrbanGenerator.SPECIAL -> 0x2A211A;
            case UrbanGenerator.DECOR -> 0x9C7C4E;
            case UrbanGenerator.FIELD -> Math.floorMod(x, 9) == 4 ? 0x7C93A3 : (x & 1) == 0 ? 0xBFAE6B : 0xAFA05F;
            case UrbanGenerator.TREE -> 0x4E6B3F;
            case UrbanGenerator.GARDEN -> 0x9DB383;
            default -> PAPER;
        };
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** La couleur d'une case, dans l'ordre qu'attend une image du jeu (A, B, G, R). */
    static int abgr(byte kind, int x, int z) {
        int c = rgb(kind & 0xFF, x, z);
        return 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF;
    }
}
