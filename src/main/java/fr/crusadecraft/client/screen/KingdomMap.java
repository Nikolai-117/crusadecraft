package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.client.map.ClientTerrain;
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
 * La carte du royaume du menu royal (P61) : le vrai monde (P62), tel que le serveur l'a relevé, et par-dessus, en voile, le domaine en or, les
 * vassaux en orange, les voisins connus en ardoise ; ce qu'on ne connaît pas reste dans l'ombre. Les capitales sur de petites plaques. Elle se
 * refait quand la carte du monde change, et quand un morceau du paysage arrive.
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
    private int lod, terrainVersion = -1;
    private long lastRender, lastAsk;
    /** La part du royaume dans un pixel du vrai monde : assez pour lire les terres, pas au point de cacher le paysage. */
    private static final double VEIL = 0.42;

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
        long now = net.minecraft.Util.getMillis();
        boolean landscape = ClientTerrain.version() != terrainVersion && now - lastRender > 400;
        if (v != drawn || iw != width || ih != height || landscape) render(v, iw, ih);
        else if (now - lastAsk > 1000) ask(v);
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
        // Le cadre : son royaume, ses vassaux et ce qu'il revendique, avec au moins 700 blocs autour pour voir ses voisins.
        // Tout le monde connu ferait de son domaine un point (vu en jeu le 8 novembre).
        double reach = 0;
        for (MapView.Seat s : v.seats()) {
            int t = s.territory();
            boolean ours = t >= 0 && t < v.territories().size() && (v.territories().get(t).mine() || vassals.contains(v.territories().get(t).id()));
            if (ours || claiming.contains(s.name()) || claimed.contains(s.name())) reach = Math.max(reach, Math.hypot(s.x() - centerX, s.z() - centerZ));
        }
        double extent = Math.max(700, reach + v.territoryRadius() * 1.5);
        scale = Math.max(1.0, 2 * extent / Math.max(1, Math.min(w, h)));
        // Le plus fin des paysages (un point par chunk) tant que la vue n'est pas immense.
        lod = scale <= 24 ? 0 : ClientTerrain.lodFor(scale);
        lastRender = net.minecraft.Util.getMillis();
        terrainVersion = ClientTerrain.version();
        ask(v);
        MapRaster.Result r = MapRaster.render(v, w, h, centerX, centerZ, scale, false);
        ResourceLocation dim = v.dimension();
        int[] px = new int[w * h];
        int[] realm = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            double wz = centerZ + (yy - h / 2.0) * scale;
            for (int xx = 0; xx < w; xx++) {
                double wx = centerX + (xx - w / 2.0) * scale;
                int owner = r.ownerAt(xx, yy);
                realm[yy * w + xx] = owner >= 0 && owner < v.seats().size() ? v.seats().get(owner).territory() : owner;
                int land = ClientTerrain.color(dim, wx, wz, lod);
                int color;
                if (land == 0) {
                    // Rien n'est encore arrivé du serveur : les aplats de la maquette, en attendant.
                    color = owner == MapRaster.NONE ? shade(r.pixels()[yy * w + xx]) : colorOf(v, owner);
                } else if (owner >= 0) {
                    color = mix(land, colorOf(v, owner), VEIL);
                } else if (owner == MapRaster.FOGGED) {
                    color = mix(land, FOG, 0.62);
                } else {
                    color = mix(land, Kit.PUITS, 0.12);
                }
                px[yy * w + xx] = 0xFF000000 | (color & 0xFFFFFF);
            }
        }
        // Les frontières : franches entre deux royaumes, légères entre deux provinces d'un même royaume.
        int[] out = px.clone();
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int i = yy * w + xx, owner = r.ownerAt(xx, yy);
                int right = xx + 1 < w ? i + 1 : i, below = yy + 1 < h ? i + w : i;
                if (realm[i] < 0 && realm[right] < 0 && realm[below] < 0) continue;
                if (realm[i] != realm[right] || realm[i] != realm[below]) {
                    out[i] = 0xFF000000 | mix(px[i], Kit.CONTOUR, 0.78);
                } else if (owner >= 0 && (owner != r.ownerAt(xx + 1, yy) || owner != r.ownerAt(xx, yy + 1))) {
                    out[i] = 0xFF000000 | mix(px[i], Kit.CONTOUR, 0.32);
                }
            }
        }
        px = out;
        raster = new MapRaster.Result(w, h, px, r.owners());
        texture.upload(raster);
    }

    /** Demande au serveur le paysage de ce qui est à l'écran (la demande se répète d'elle-même quand il vieillit). */
    private void ask(MapView v) {
        lastAsk = net.minecraft.Util.getMillis();
        if (width <= 0 || scale <= 0) return;
        ClientTerrain.ensure(v.dimension(), lod, centerX - width / 2.0 * scale, centerZ - height / 2.0 * scale,
                centerX + width / 2.0 * scale, centerZ + height / 2.0 * scale);
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
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
        // Les capitales, la sienne d'abord puis de la plus proche à la plus lointaine ; une plaque qui en chevaucherait une autre se tait.
        java.util.List<MapView.Seat> capitals = new java.util.ArrayList<>();
        for (MapView.Seat s : v.seats()) {
            if (s.capital() && s.territory() >= 0 && s.territory() < v.territories().size()) capitals.add(s);
        }
        capitals.sort(java.util.Comparator.comparing((MapView.Seat s) -> !v.territories().get(s.territory()).mine())
                .thenComparingDouble(s -> Math.hypot(s.x() - centerX, s.z() - centerZ)));
        java.util.List<int[]> placed = new java.util.ArrayList<>();
        for (MapView.Seat s : capitals) {
            int sx = x + (int) Math.round(w / 2.0 + (s.x() - centerX) / scale), sy = y + (int) Math.round(h / 2.0 + (s.z() - centerZ) / scale);
            if (sx < x + 4 || sx > x + w - 4 || sy < y + 4 || sy > y + h - 4) continue;
            boolean mine = v.territories().get(s.territory()).mine();
            String name = font.plainSubstrByWidth(s.name(), 70);
            int tw = font.width(name) + (mine ? 12 : 0) + 6;
            int lx = Math.max(x + 1, Math.min(x + w - tw - 1, sx - tw / 2)), ly = Math.max(y + 1, Math.min(y + h - 12, sy - 6));
            boolean clash = false;
            for (int[] r : placed) clash |= lx < r[0] + r[2] + 2 && r[0] < lx + tw + 2 && ly < r[1] + 13 && r[1] < ly + 13;
            if (clash) continue;
            placed.add(new int[]{lx, ly, tw});
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
