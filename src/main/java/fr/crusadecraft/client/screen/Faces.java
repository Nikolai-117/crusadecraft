package fr.crusadecraft.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Les petits visages de huit pixels de la maquette (P61), pour les listes : un conseiller, un votant. Ce ne sont pas des portraits : ils se déduisent
 * du nom (teint, cheveux, barbe), et le même nom donne toujours le même visage. La fiche d'un personnage, elle, montre le vrai.
 */
public final class Faces {
    private static final int[] SKIN = {0xFFE3B48C, 0xFFD39B73, 0xFFB57A52, 0xFF8A5634, 0xFF6B3F22, 0xFFF0C8A6};
    private static final int[] HAIR = {0xFF2B1D14, 0xFF4A2E1A, 0xFF7A4B25, 0xFFC8A04E, 0xFF9A3E20, 0xFFD8D2C4, 0xFF1A1A1A};
    private static final int EYE_WHITE = 0xFFF2F2F2, EYE = 0xFF3B6EA8, MOUTH = 0xFF8A4A3A;

    private Faces() {}

    private static int hash(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) h = (h ^ s.charAt(i)) * 0x01000193;
        return h & 0x7FFFFFFF;
    }

    /** Un visage de 8 x 8, chaque pixel faisant {@code scale} pixels d'écran. */
    public static void draw(GuiGraphics g, String name, boolean female, int x, int y, int scale) {
        int h = hash(name == null ? "" : name);
        int skin = SKIN[h % SKIN.length], hair = HAIR[(h / 7) % HAIR.length];
        boolean beard = !female && (h / 31) % 3 == 0;
        boolean bald = !female && (h / 97) % 7 == 0;
        int eye = (h / 13) % 3 == 0 ? 0xFF4C7A3A : (h / 13) % 3 == 1 ? 0xFF5A3A22 : EYE;
        // Le visage, puis les cheveux par-dessus.
        px(g, x, y, scale, 0, 0, 8, 8, skin);
        if (!bald) px(g, x, y, scale, 0, 0, 8, 2, hair);
        if (!bald) px(g, x, y, scale, 0, 2, 1, female ? 6 : 2, hair);
        if (!bald) px(g, x, y, scale, 7, 2, 1, female ? 6 : 2, hair);
        px(g, x, y, scale, 1, 4, 2, 1, EYE_WHITE);
        px(g, x, y, scale, 5, 4, 2, 1, EYE_WHITE);
        px(g, x, y, scale, 2, 4, 1, 1, eye);
        px(g, x, y, scale, 5, 4, 1, 1, eye);
        px(g, x, y, scale, 3, 5, 2, 1, darker(skin));
        if (beard) {
            px(g, x, y, scale, 1, 6, 6, 2, hair);
            px(g, x, y, scale, 3, 6, 2, 1, MOUTH);
        } else {
            px(g, x, y, scale, 3, 6, 2, 1, MOUTH);
        }
    }

    private static int darker(int c) {
        int r = (int) (((c >> 16) & 255) * 0.8), gg = (int) (((c >> 8) & 255) * 0.8), b = (int) ((c & 255) * 0.8);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private static void px(GuiGraphics g, int x, int y, int s, int px, int py, int w, int h, int color) {
        g.fill(x + px * s, y + py * s, x + (px + w) * s, y + (py + h) * s, color);
    }
}
