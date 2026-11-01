package fr.crusadecraft.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Boîte à outils de dessin des écrans de CrusadeCraft, dans l'esprit de CK3 : panneaux sombres
 * à liseré doré, parchemin pour les livres, blasons.
 *
 * <p>Tout est dessiné avec des aplats : aucune texture d'interface à maintenir.
 */
public final class Ui {
    public static final int PANEL = 0xF01A1511;
    public static final int PANEL_INNER = 0xFF241D17;
    public static final int PANEL_HIGHLIGHT = 0xFF33291F;
    public static final int GOLD = 0xFFC9A55C;
    public static final int GOLD_DARK = 0xFF6E5528;
    public static final int TEXT = 0xFFEDE3CC;
    public static final int TEXT_MUTED = 0xFFA89A7C;
    public static final int TEXT_TITLE = 0xFFF3D88C;
    public static final int RED = 0xFFB0413A;
    public static final int GREEN = 0xFF6E9E4A;
    public static final int PARCHMENT = 0xFFEADDBE;
    public static final int PARCHMENT_DARK = 0xFFD4C29A;
    public static final int INK = 0xFF3A2918;
    public static final int INK_MUTED = 0xFF7A6345;

    private Ui() {}

    /** Panneau principal : ombre, fond sombre, double liseré doré. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, PANEL);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 24, 0x40C9A55C, 0x00C9A55C);
        g.renderOutline(x, y, w, h, GOLD);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, GOLD_DARK);
    }

    /** Encadré secondaire à l'intérieur d'un panneau. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        g.renderOutline(x, y, w, h, GOLD_DARK);
    }

    /** Page de parchemin (registre, lettres). */
    public static void parchment(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x80000000);
        g.fill(x, y, x + w, y + h, PARCHMENT);
        g.fillGradient(x, y, x + w, y + 12, 0x30603A10, 0x00603A10);
        g.fillGradient(x, y + h - 12, x + w, y + h, 0x00603A10, 0x30603A10);
        g.renderOutline(x, y, w, h, 0xFF8A6A3C);
    }

    /** Filet doré horizontal avec un losange au centre. */
    public static void separator(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
        int cx = x + w / 2;
        g.fill(cx - 1, y - 2, cx + 2, y + 3, color);
        g.fill(cx - 2, y - 1, cx + 3, y + 2, color);
    }

    /**
     * Blason : un écu à pointe, aux couleurs du royaume, avec son initiale.
     */
    public static void shield(GuiGraphics g, Font font, int x, int y, int w, int h, int rgb, String initial) {
        shieldShape(g, x - 1, y - 1, w + 2, h + 2, GOLD);
        shieldShape(g, x, y, w, h, 0xFF000000 | rgb);
        // reflet
        g.fillGradient(x + 2, y + 1, x + w / 2, y + h / 2, 0x30FFFFFF, 0x00FFFFFF);
        if (initial != null && !initial.isEmpty()) {
            float scale = Math.max(1f, h / 18f);
            g.pose().pushPose();
            g.pose().translate(x + w / 2f, y + h * 0.42f, 0);
            g.pose().scale(scale, scale, 1);
            int tw = font.width(initial);
            int textColor = luminance(rgb) > 0.6 ? 0xFF2A2018 : 0xFFF6E7B8;
            g.drawString(font, initial, -tw / 2, -4, textColor, false);
            g.pose().popPose();
        }
    }

    private static void shieldShape(GuiGraphics g, int x, int y, int w, int h, int color) {
        int straight = (int) (h * 0.55f);
        for (int r = 0; r < h; r++) {
            int inset = 0;
            if (r >= straight) {
                float t = (r - straight) / (float) Math.max(1, h - straight);
                inset = (int) (w / 2f * Math.pow(t, 1.7));
            }
            if (x + inset < x + w - inset) g.fill(x + inset, y + r, x + w - inset, y + r + 1, color);
        }
    }

    public static double luminance(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0, gr = ((rgb >> 8) & 0xFF) / 255.0, b = (rgb & 0xFF) / 255.0;
        return 0.2126 * r + 0.7152 * gr + 0.0722 * b;
    }

    /** Jauge horizontale, valeur entre 0 et 1. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        g.fill(x, y, x + w, y + h, 0xFF110D0A);
        int filled = (int) Math.round(Math.max(0, Math.min(1, frac)) * (w - 2));
        g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
        g.renderOutline(x, y, w, h, GOLD_DARK);
    }

    /** Couleur du bonheur MineColonies (0 à 10) : rouge, ambre, vert. */
    public static int happinessColor(double happiness) {
        if (happiness < 4) return RED;
        if (happiness < 6.5) return 0xFFC99A3C;
        return GREEN;
    }

    public static void text(GuiGraphics g, Font font, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    public static void centered(GuiGraphics g, Font font, Component c, int cx, int y, int color) {
        g.drawString(font, c, cx - font.width(c) / 2, y, color, false);
    }

    public static void scaled(GuiGraphics g, Font font, Component c, int x, int y, float scale, int color, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int dx = centered ? -font.width(c) / 2 : 0;
        g.drawString(font, c, dx, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Un texte sur une ligne qui tient dans {@code maxWidth} : réduit jusqu'aux deux tiers s'il le faut, coupé au-delà.
     * Centré verticalement sur la ligne d'un texte ordinaire.
     */
    public static void fitted(GuiGraphics g, Font font, Component c, int x, int y, int maxWidth, int color) {
        int width = font.width(c);
        if (width <= maxWidth) {
            text(g, font, c, x, y, color);
            return;
        }
        float k = Math.max(0.67f, maxWidth / (float) width);
        String s = font.plainSubstrByWidth(c.getString(), Math.round(maxWidth / k));
        scaled(g, font, Component.literal(s).withStyle(c.getStyle()), x, y + Math.round((1 - k) * 4), k, color, false);
    }

    /** Texte coupé sur plusieurs lignes ; renvoie la hauteur occupée. */
    public static int wrapped(GuiGraphics g, Font font, Component c, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(c, width);
        int dy = 0;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y + dy, color, false);
            dy += font.lineHeight + 1;
        }
        return dy;
    }

    public static void wrappedCentered(GuiGraphics g, Font font, Component c, int cx, int y, int width, int color) {
        List<FormattedCharSequence> lines = font.split(c, width);
        int dy = 0;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, cx - font.width(line) / 2, y + dy, color, false);
            dy += font.lineHeight + 1;
        }
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Initiale d'un nom, pour le blason. */
    public static String initial(String name) {
        if (name == null || name.isBlank()) return "?";
        int cp = name.strip().codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(cp)));
    }
}
