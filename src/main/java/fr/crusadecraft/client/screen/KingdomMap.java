package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.client.map.MapTexture;
import fr.crusadecraft.core.world.MapView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/**
 * La carte du royaume du menu royal (P61), dans l'esprit de la maquette : le vrai territoire, mais en aplats. Le domaine en or, les vassaux en orange,
 * les voisins connus en ardoise, l'inconnu dans le noir ; les capitales sur de petites plaques. Elle se refait quand la carte du monde change.
 */
final class KingdomMap {
    static final int DOMAIN = 0xFFC9A24A, VASSAL = 0xFFC06A2A, KNOWN = 0xFF4A5562, FOG = 0xFF0B0E11, CLAIMING = 0xFF2F5577, CLAIMED = 0xFF7FB0E0;
    private final MapTexture texture = new MapTexture("royaume_menu");
    private MapView drawn;
    private MapRaster.Result raster;
    private int width, height;
    private Set<UUID> vassals = Set.of();
    private Set<String> claiming = Set.of(), claimed = Set.of();
    private double centerX, centerZ, scale;

    void release() {
        texture.release();
    }

    /** Ce qu'il faut savoir pour la colorier : qui est vassal, quelle province on revendique, lesquelles le sont déjà (par leur nom). */
    void configure(Set<UUID> vassals, Set<String> claiming, Set<String> claimed) {
        if (!vassals.equals(this.vassals) || !claiming.equals(this.claiming) || !claimed.equals(this.claimed)) {
            this.vassals = vassals;
            this.claiming = claiming;
            this.claimed = claimed;
            drawn = null;
        }
    }

    void draw(GuiGraphics g, Font font, int x, int y, int w, int h) {
        Kit.well(g, x, y, w, h);
        MapView v = ClientMapData.view();
        if (v == null) {
            Ui.centered(g, font, Component.translatable("crusadecraft.tab.loading"), x + w / 2, y + h / 2 - 4, Kit.GRIS_DISCRET);
            return;
        }
        int iw = w - 4, ih = h - 4;
        if (v != drawn || iw != width || ih != height) render(v, iw, ih);
        ResourceLocation id = texture.id();
        if (id != null) g.blit(id, x + 2, y + 2, 0, 0, iw, ih, iw, ih);
        labels(g, font, v, x + 2, y + 2, iw, ih);
    }

    private void render(MapView v, int w, int h) {
        drawn = v;
        width = w;
        height = h;
        // Centrée sur la capitale de son royaume, assez large pour ses voisins.
        centerX = v.focusX();
        centerZ = v.focusZ();
        for (MapView.Seat s : v.seats()) {
            if (s.capital() && s.territory() >= 0 && s.territory() < v.territories().size() && v.territories().get(s.territory()).mine()) {
                centerX = s.x();
                centerZ = s.z();
            }
        }
        scale = MapRaster.fit(v, centerX, centerZ, w, h) * 0.55;
        MapRaster.Result r = MapRaster.render(v, w, h, centerX, centerZ, scale, false);
        int[] px = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int owner = r.ownerAt(xx, yy);
                // Hors des royaumes, le terrain d'origine assombri : la mer et les rivières se devinent encore.
                int color = owner == MapRaster.NONE ? shade(r.pixels()[yy * w + xx]) : colorOf(v, owner);
                // Un trait sombre là où l'on change de maître : les provinces se lisent.
                if (owner >= 0 && (owner != r.ownerAt(xx + 1, yy) || owner != r.ownerAt(xx, yy + 1))) color = Kit.CONTOUR;
                px[yy * w + xx] = color;
            }
        }
        raster = new MapRaster.Result(w, h, px, r.owners());
        texture.upload(raster);
    }

    private static int shade(int argb) {
        int r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
        // Un quart du terrain, trois quarts du creux de la maquette.
        r = (r + 3 * 0x0F) / 4 / 2 + 6;
        gg = (gg + 3 * 0x12) / 4 / 2 + 8;
        b = (b + 3 * 0x16) / 4 / 2 + 11;
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private int colorOf(MapView v, int owner) {
        if (owner == MapRaster.FOGGED || owner < 0 || owner >= v.seats().size()) return FOG;
        MapView.Seat s = v.seats().get(owner);
        if (claiming.contains(s.name())) return CLAIMING;
        if (claimed.contains(s.name())) return CLAIMED;
        int t = s.territory();
        if (t < 0 || t >= v.territories().size()) return KNOWN;
        MapView.Territory territory = v.territories().get(t);
        if (territory.mine()) return DOMAIN;
        if (vassals.contains(territory.id())) return VASSAL;
        return KNOWN;
    }

    private void labels(GuiGraphics g, Font font, MapView v, int x, int y, int w, int h) {
        for (MapView.Seat s : v.seats()) {
            if (!s.capital() || s.territory() < 0 || s.territory() >= v.territories().size()) continue;
            int sx = x + (int) Math.round(w / 2.0 + (s.x() - centerX) / scale), sy = y + (int) Math.round(h / 2.0 + (s.z() - centerZ) / scale);
            if (sx < x + 4 || sx > x + w - 4 || sy < y + 4 || sy > y + h - 4) continue;
            boolean mine = v.territories().get(s.territory()).mine();
            String name = font.plainSubstrByWidth(s.name(), 70);
            int tw = font.width(name) + (mine ? 12 : 0) + 6;
            int lx = Math.max(x + 1, Math.min(x + w - tw - 1, sx - tw / 2)), ly = Math.max(y + 1, Math.min(y + h - 12, sy - 6));
            g.fill(lx, ly, lx + tw, ly + 11, 0xF01B2026);
            g.renderOutline(lx, ly, tw, 11, mine ? 0xFF6E5526 : Kit.CONTOUR);
            int tx = lx + 3;
            if (mine) {
                Kit.icon(g, Kit.Icon.ROYAUME, tx, ly + 1, Kit.OR);
                tx += 12;
            }
            g.drawString(font, name, tx, ly + 2, Kit.CREME, false);
        }
    }
}
