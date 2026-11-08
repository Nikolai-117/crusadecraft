package fr.crusadecraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Le kit d'interface Varenhold (P61), d'après la maquette de Romain : ardoise et dorure, dans l'esprit du hub de personnage de CK3. Tout est posé sur
 * une grille de pixels de texture : un pixel ici vaut deux pixels de la maquette, soit l'échelle d'interface 2 de Minecraft.
 *
 * <p>Deux voix : le titre en gothique (Jacquarda Bastarda 9, licence SIL OFL, redessinée en police bitmap), le texte dans la police du jeu, la plus
 * proche de la Pixelify de la maquette et la seule qui ait tous les accents partout.
 */
public final class Kit {
    // ------------------------------------------------------------------ les couleurs de la maquette
    /** Fond de fenêtre. */
    public static final int ARDOISE = 0xFF161A1F;
    /** Panneaux. */
    public static final int CARTE = 0xFF1E242B;
    /** Valeurs en creux. */
    public static final int PUITS = 0xFF0F1216;
    /** Cadre, liserés. */
    public static final int DORURE = 0xFFB08D3E;
    /** Action principale. */
    public static final int OR_BOUTON = 0xFF8A6A24;
    /** Texte principal. */
    public static final int CREME = 0xFFEADFC4;
    /** Libellés. */
    public static final int GRIS_DISCRET = 0xFF949CA6;
    /** Prudence, tiède. */
    public static final int AMBRE = 0xFFE3B34F;
    /** Sceau, badge. */
    public static final int CIRE = 0xFF9E2F1F;
    /** Bandeau du haut. */
    public static final int ENTETE = 0xFF0F1216;
    /** Navigation, pied. */
    public static final int RAIL = 0xFF12161A;
    /** Traits de deux pixels de la maquette. */
    public static final int CONTOUR = 0xFF07090B;
    /** Titres, icônes. */
    public static final int OR = 0xFFE8C066;
    /** Proposition, en cours. */
    public static final int AZUR = 0xFF1F3346;
    /** Texte secondaire. */
    public static final int GRIS_PERLE = 0xFFB4BAC2;
    /** Positif, loyal. */
    public static final int SINOPLE = 0xFF9FD07F;
    /** Alerte, manquant. */
    public static final int GUEULES = 0xFFEF8A73;
    /** Actes, propositions. */
    public static final int PARCHEMIN = 0xFFE8DFC8;
    /** Les valeurs mises en avant. */
    public static final int VALEUR = 0xFFF3E6C6;
    /** Encre sur parchemin. */
    public static final int ENCRE = 0xFF2A241A;

    // Les nuances qui font le relief (clair en haut à gauche, sombre en bas à droite).
    static final int CARTE_CLAIR = 0xFF2E3740, CARTE_SOMBRE = 0xFF12161A;
    static final int BOUTON = 0xFF2A3139, BOUTON_CLAIR = 0xFF414B56, BOUTON_SOMBRE = 0xFF171B20;
    static final int SURVOL = 0xFFD9B25A, SURVOL_TEXTE = 0xFFFFF4D6;
    static final int BLOQUE = 0xFF1C2126, BLOQUE_CLAIR = 0xFF2A323B, BLOQUE_SOMBRE = 0xFF13171B, BLOQUE_TEXTE = 0xFF7D858E;
    static final int OR_CLAIR = 0xFFC49A3C, OR_SOMBRE = 0xFF5A4214, OR_TEXTE = 0xFFFFF6DC;
    static final int SELECTION = 0xFF28313B, SELECTION_LISERE = 0xFFA7843A;
    static final int ALERTE = 0xFF2A1619, ALERTE_LISERE = 0xFF8E2B1E;
    static final int AZUR_LISERE = 0xFF7FB0E0, AZUR_INTERIEUR = 0xFF2F5577, AZUR_TEXTE = 0xFFEAF3FF;
    static final int PUITS_CLAIR = 0xFF2A323B, PUITS_SOMBRE = 0xFF0A0C0F;
    static final int ACTE_CLAIR = 0xFFF6F0E0, ACTE_SOMBRE = 0xFFC9BD9F;
    /** Les filets : doré pour un titre de panneau, ardoise pour une section. */
    public static final int FILET_OR = 0xFF6E5526, FILET = 0xFF2E3740;
    static final int CADRE_OR = 0xFF9C7C3C;

    private static final ResourceLocation GOTHIC = new ResourceLocation(CrusadeCraft.MOD_ID, "gothique");
    private static final ResourceLocation ICONS = new ResourceLocation(CrusadeCraft.MOD_ID, "textures/gui/kit/icons.png");
    private static final ResourceLocation RESOURCES = new ResourceLocation(CrusadeCraft.MOD_ID, "textures/gui/kit/resources.png");

    private Kit() {}

    // ------------------------------------------------------------------ les icônes

    /** Les glyphes de la maquette, 10 x 10, d'une seule couleur : on les teinte au dessin. */
    public enum Icon {
        ROYAUME, CONSEIL, LOIS, CHANCELLERIE, DOMAINE, TRESOR, ARMEE, MAISON, CHRONIQUE, INTRIGUES, SCEPTRE, REGLAGES, FERMER, REMPLI, VERROU,
        SUIVANT, HAUSSE, BAISSE, LETTRE, DRAPEAU
    }

    /** Les ressources de la maquette, 12 x 12, dans leurs couleurs. */
    public enum Resource { OR, PRESTIGE, PIETE, PIERRE, SCEAU }

    public static void icon(GuiGraphics g, Icon icon, int x, int y, int color) {
        RenderSystem.enableBlend();
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, ((color >>> 24) & 255) / 255f);
        g.blit(ICONS, x, y, icon.ordinal() * 10, 0, 10, 10, Icon.values().length * 10, 10);
        g.setColor(1f, 1f, 1f, 1f);
    }

    public static void resource(GuiGraphics g, Resource r, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(RESOURCES, x, y, r.ordinal() * 12, 0, 12, 12, Resource.values().length * 12, 12);
    }

    // ------------------------------------------------------------------ le titre gothique

    /** Un texte dans la voix du monde : la gothique. Les caractères qu'elle n'a pas viennent de la police du jeu. */
    public static MutableComponent gothic(String text) {
        return Component.literal(text).withStyle(s -> s.withFont(GOTHIC));
    }

    public static MutableComponent gothic(Component text) {
        return gothic(text.getString());
    }

    /** La hauteur d'une ligne de gothique : ses capitales font neuf pixels, ses accents et ses fioritures en ajoutent. */
    public static final int GOTHIC_HEIGHT = 15;

    /** Un titre en gothique, à la taille voulue (1 ou 2, pour rester net), avec l'ombre portée de la maquette. */
    public static void title(GuiGraphics g, Font font, Component text, int x, int y, int scale, int color, boolean shadow) {
        Component c = gothic(text);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        if (shadow) g.drawString(font, c, 1, 1, CONTOUR, false);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    public static int titleWidth(Font font, Component text, int scale) {
        return font.width(gothic(text)) * scale;
    }

    // ------------------------------------------------------------------ les panneaux

    /** Un rectangle aux angles cassés de deux pixels, comme le découpage de la maquette. */
    private static void chamfered(GuiGraphics g, int x, int y, int w, int h, int color, int cut) {
        g.fill(x + cut, y, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + cut, y + h - cut, color);
        g.fill(x + w - cut, y + cut, x + w, y + h - cut, color);
    }

    /** Le panneau ancré : bord noir, dorure, noir, fond ardoise et son grain, petits ornements dorés aux angles. */
    public static void anchored(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x66000000);
        chamfered(g, x, y, w, h, CONTOUR, 2);
        chamfered(g, x + 1, y + 1, w - 2, h - 2, CADRE_OR, 2);
        chamfered(g, x + 2, y + 2, w - 4, h - 4, CONTOUR, 2);
        chamfered(g, x + 3, y + 3, w - 6, h - 6, ARDOISE, 1);
        grain(g, x + 3, y + 3, w - 6, h - 6);
        for (int[] c : new int[][]{{x - 1, y - 1}, {x + w - 2, y - 1}, {x - 1, y + h - 2}, {x + w - 2, y + h - 2}}) {
            g.fill(c[0], c[1], c[0] + 3, c[1] + 3, CONTOUR);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 2, c[1] + 2, OR);
        }
    }

    /** Le grain de la maquette : une ligne un peu plus claire toutes les trois. */
    public static void grain(GuiGraphics g, int x, int y, int w, int h) {
        for (int yy = y; yy < y + h; yy += 3) g.fill(x, yy, x + w, yy + 1, 0x05C8DCFF);
    }

    /** La carte : relief clair en haut à gauche, sombre en bas à droite. */
    public static void card(GuiGraphics g, int x, int y, int w, int h) {
        relief(g, x, y, w, h, CARTE, CARTE_CLAIR, CARTE_SOMBRE);
    }

    /** Le puits : un creux, pour les valeurs et les ressources. */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        relief(g, x, y, w, h, PUITS, PUITS_SOMBRE, PUITS_CLAIR);
    }

    /** L'acte : le parchemin, réservé aux titres et aux propositions. */
    public static void deed(GuiGraphics g, int x, int y, int w, int h) {
        relief(g, x, y, w, h, PARCHEMIN, ACTE_CLAIR, ACTE_SOMBRE);
    }

    /** La sélection : un liseré doré intérieur, pour l'onglet ou le siège actif. */
    public static void selection(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SELECTION);
        g.renderOutline(x, y, w, h, CONTOUR);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, SELECTION_LISERE);
    }

    /** L'alerte : un blocage expliqué en clair, jamais caché dans une infobulle. */
    public static void alert(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, ALERTE);
        g.renderOutline(x, y, w, h, CONTOUR);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, ALERTE_LISERE);
    }

    /** Une proposition en cours : l'azur. */
    public static void proposal(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, AZUR);
        g.renderOutline(x, y, w, h, AZUR_LISERE);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, AZUR_INTERIEUR);
    }

    private static void relief(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, CONTOUR);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, dark);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, dark);
    }

    // ------------------------------------------------------------------ les boutons

    public enum State { NORMAL, HOVER, PRESSED, DISABLED }

    /** Un bouton de la maquette : normal, survolé (liseré doré), pressé (relief inversé), bloqué ; doré pour l'action principale. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, State state, boolean primary) {
        int fill, light, dark;
        if (state == State.DISABLED) {
            fill = BLOQUE;
            light = BLOQUE_CLAIR;
            dark = BLOQUE_SOMBRE;
        } else if (primary) {
            fill = OR_BOUTON;
            light = OR_CLAIR;
            dark = OR_SOMBRE;
        } else {
            fill = BOUTON;
            light = BOUTON_CLAIR;
            dark = BOUTON_SOMBRE;
        }
        if (state == State.PRESSED) relief(g, x, y, w, h, fill, dark, light);
        else relief(g, x, y, w, h, fill, light, dark);
        if (state == State.HOVER) g.renderOutline(x, y, w, h, SURVOL);
    }

    public static int buttonText(State state, boolean primary) {
        return state == State.DISABLED ? BLOQUE_TEXTE : state == State.HOVER ? SURVOL_TEXTE : primary ? OR_TEXTE : CREME;
    }

    // ------------------------------------------------------------------ petits éléments

    public enum Chip { GOLD, AMBER, PROPOSAL, ALERT, SOLID }

    /** Une pastille : capitale, inoccupé, en cours. Renvoie sa largeur. */
    public static int chip(GuiGraphics g, Font font, Component text, int x, int y, Chip kind) {
        int w = font.width(text) + 6, h = 11;
        switch (kind) {
            case GOLD -> {
                g.renderOutline(x, y, w, h, FILET_OR);
                g.drawString(font, text, x + 3, y + 2, OR, false);
            }
            case AMBER -> {
                g.renderOutline(x, y, w, h, AMBRE);
                g.drawString(font, text, x + 3, y + 2, AMBRE, false);
            }
            case PROPOSAL -> {
                g.fill(x, y, x + w, y + h, AZUR);
                g.renderOutline(x, y, w, h, AZUR_LISERE);
                g.drawString(font, text, x + 3, y + 2, AZUR_TEXTE, false);
            }
            case ALERT -> {
                g.fill(x, y, x + w, y + h, 0xFFC0473A);
                g.renderOutline(x, y, w, h, CONTOUR);
                g.drawString(font, text, x + 3, y + 2, 0xFFFFE8E0, false);
            }
            case SOLID -> {
                // Le niveau en vigueur : l'aplat doré de la maquette.
                g.fill(x, y, x + w, y + h, OR_BOUTON);
                g.renderOutline(x, y, w, h, CONTOUR);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, OR_CLAIR);
                g.drawString(font, text, x + 3, y + 2, OR_TEXTE, false);
            }
        }
        return w;
    }

    /** Une touche du clavier ou de la souris, pour expliquer un geste. Renvoie sa largeur. */
    public static int key(GuiGraphics g, Font font, Component text, int x, int y) {
        int w = Math.max(11, font.width(text) + 6), h = 10;
        g.fill(x, y, x + w, y + h, CONTOUR);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOUTON);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BOUTON_SOMBRE);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + 1, CREME, false);
        return w;
    }

    /** Un titre de section : la gothique, un filet jusqu'au bout, et parfois un texte à droite. */
    public static void section(GuiGraphics g, Font font, Component title, int x, int y, int w, Component right, int rightColor) {
        title(g, font, title, x, y - 3, 1, OR, false);
        int tx = x + titleWidth(font, title, 1) + 5;
        // Le texte de droite cède la place au titre plutôt que de le chevaucher.
        if (right != null && tx + font.width(right) > x + w) right = null;
        int rx = right == null ? x + w : x + w - font.width(right);
        if (right != null) g.drawString(font, right, rx, y + 1, rightColor, false);
        if (rx - 5 > tx) g.fill(tx, y + 4, rx - 5, y + 5, FILET);
    }

    /** Le filet doré sous un titre de panneau, avec un losange au milieu. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        int cx = x + w / 2;
        g.fill(x, y, cx - 4, y + 1, FILET_OR);
        g.fill(cx + 4, y, x + w, y + 1, FILET_OR);
        g.fill(cx - 1, y - 1, cx + 2, y + 2, DORURE);
    }

    /** Une jauge en segments : les étapes d'un titre, par exemple. */
    public static void segments(GuiGraphics g, int x, int y, int w, int count, int filled) {
        int gap = 2, sw = (w - gap * (count - 1)) / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            int sx = x + i * (sw + gap);
            g.fill(sx, y, sx + sw, y + 5, CONTOUR);
            g.fill(sx + 1, y + 1, sx + sw - 1, y + 4, i < filled ? 0xFFC9A24A : 0xFF2A323B);
        }
    }

    /** Une jauge pleine : bonheur, population. */
    public static void gauge(GuiGraphics g, int x, int y, int w, double frac, int color) {
        g.fill(x, y, x + w, y + 5, CONTOUR);
        g.fill(x + 1, y + 1, x + w - 1, y + 4, 0xFF2A323B);
        g.fill(x + 1, y + 1, x + 1 + (int) Math.round((w - 2) * Math.max(0, Math.min(1, frac))), y + 4, color);
    }

    /** Une coche ou une croix, selon qu'une condition est remplie. */
    public static void check(GuiGraphics g, boolean ok, int x, int y) {
        icon(g, ok ? Icon.REMPLI : Icon.FERMER, x, y, ok ? SINOPLE : GUEULES);
    }

    /** Un portrait encadré : le visage dans un cadre ardoise, liseré doré s'il est mis en avant. */
    public static void portraitFrame(GuiGraphics g, int x, int y, int w, int h, boolean gold) {
        g.fill(x, y, x + w, y + h, CONTOUR);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A333D);
        if (gold) g.renderOutline(x + 1, y + 1, w - 2, h - 2, SELECTION_LISERE);
    }

    /** Un séparateur vertical, entre deux groupes de la barre du haut. */
    public static void divider(GuiGraphics g, int x, int y, int h) {
        g.fill(x, y, x + 1, y + h, FILET);
    }
}
