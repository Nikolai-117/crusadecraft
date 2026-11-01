package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.build.plan.NationStyle;
import fr.crusadecraft.core.build.plan.PlanKinds;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.build.plan.TownSize;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.PlanPackets;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Le catalogue du plan d'architecte (P34, refait en P34.1) : les étagères à gauche (villes entières, chaque pack de
 * style, les fichiers du joueur), leurs plans au milieu avec une recherche, et à droite le plan choisi : sa taille et
 * un aperçu vu du ciel — pour une ville entière, son plan (bâtiments, rues, enceinte), qu'on peut retirer au sort.
 * Défilement doux, flèches et Entrée au clavier, double-clic pour prendre.
 */
public class PlanCatalogScreen extends Screen {
    private static final int SOURCES_W = 124, ROW_H = 12, SOURCE_H = 14;

    private final List<PlanLibrary.Source> sources;
    private final String folder;
    private String source;
    private List<PlanLibrary.Entry> entries = List.of();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, PlanTool.Detail> details = new HashMap<>();
    private final Map<String, Long> detailArrived = new HashMap<>();
    private final Map<String, Long> seeds = new HashMap<>();
    private final Random random = new Random();
    private int selected = -1;
    private float scroll, scrollTarget, sourceScroll, sourceTarget;
    private int left, top, w, h, sx, lx, lw, dx, dw, listTop, listBottom;
    private EditBox search;
    private Button take, reroll;
    private boolean loading;
    private long lastClick, lastNanos;
    private int lastClickRow = -1;
    private Button favorite;
    /** Le filtre des sortes : −1 tout, une sorte de {@link PlanKinds}, ou {@link #ONLY_FAVORITES}. */
    private int filter = -1;
    private static final int ONLY_FAVORITES = 9;
    private static final int[] CHIPS = {-1, PlanKinds.BATIMENT, PlanKinds.MURAILLE, PlanKinds.ROUTE, PlanKinds.DECORATION, ONLY_FAVORITES};
    /** Les favoris du monde, tels que le serveur les a envoyés. */
    private static final java.util.Set<String> FAVORITES = new java.util.HashSet<>();

    /** Une ligne de la liste : un en-tête de dossier, ou un plan. */
    private record Row(Component text, String ref, boolean header, Component full, int kind) {}

    public static void setFavorites(List<String> refs) {
        FAVORITES.clear();
        FAVORITES.addAll(refs);
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof PlanCatalogScreen screen) screen.rebuildRows();
    }

    /** La clé d'un favori : pour une ville entière, sans son tirage. */
    private static boolean isFavorite(String ref) {
        return ref != null && FAVORITES.contains(ref);
    }

    public PlanCatalogScreen(List<PlanLibrary.Source> sources, String folder) {
        super(Component.translatable("crusadecraft.plan.catalog"));
        this.sources = sources;
        this.folder = folder;
        this.source = sources.isEmpty() ? null : sources.get(0).id();
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 640);
        h = Math.min(height - 16, 330);
        left = (width - w) / 2;
        top = (height - h) / 2;
        sx = left + 8;
        dw = Mth.clamp((int) ((w - SOURCES_W - 30) * 0.38), 150, 210);
        dx = left + w - 8 - dw;
        lx = sx + SOURCES_W + 8;
        lw = dx - 8 - lx;
        listTop = top + 58;
        listBottom = top + h - 24;
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, lx, top + 25, lw, 14, Component.translatable("crusadecraft.plan.search"));
        search.setHint(Component.translatable("crusadecraft.plan.search"));
        search.setValue(previous);
        search.setResponder(s -> {
            scrollTarget = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        take = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.take"), b -> chooseSelected())
                .bounds(dx + 4, listBottom - 18, dw - 8, 16).build());
        reroll = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.reroll"), b -> rerollSelected())
                .bounds(dx + 4, listBottom - 36, dw - 8, 16).build());
        favorite = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.favorite.add"), b -> {
            if (selected >= 0 && selected < rows.size()) CrusadeNetwork.toServer(new PlanPackets.ToggleFavorite(rows.get(selected).ref()));
        }).bounds(dx + 4, listBottom - 36, dw - 8, 16).build());
        favorite.setY(listBottom - 36);
        if (source != null && entries.isEmpty() && !loading) request(source);
        rebuildRows();
    }

    private void request(String id) {
        loading = true;
        CrusadeNetwork.toServer(new PlanPackets.Request(id));
    }

    /** Les plans d'une étagère, arrivés du serveur. */
    public void showEntries(String id, List<PlanLibrary.Entry> list) {
        if (!id.equals(source)) return;
        loading = false;
        entries = list;
        scroll = scrollTarget = 0;
        selected = -1;
        rebuildRows();
        selectNext(1);
    }

    /** Ce qu'on sait d'un plan, arrivé du serveur. */
    public void showDetail(PlanTool.Detail d) {
        details.put(d.ref(), d);
        detailArrived.put(d.ref(), Util.getMillis());
    }

    // ------------------------------------------------------------------ libellés

    private static Component sourceLabel(PlanLibrary.Source s) {
        if (PlanTool.TOWNS.equals(s.id())) return Component.translatable("crusadecraft.plan.source.towns");
        if (PlanLibrary.FILES.equals(s.id())) return Component.translatable("crusadecraft.plan.source.files");
        if (PlanTool.FAVORITES.equals(s.id())) return Component.translatable("crusadecraft.plan.source.favorites");
        return Component.literal(s.label());
    }

    private static boolean isTown(String ref) {
        return ref != null && ref.startsWith("town:");
    }

    /** « Ville de style chinois — bourg » pour une ville entière, sinon le nom du plan. */
    private static Component entryLabel(PlanLibrary.Entry e) {
        if (isTown(e.ref())) {
            String[] parts = e.ref().substring(5).split(":");
            if (parts.length >= 2) {
                return Component.translatable("crusadecraft.plan.town_name", Component.translatable(NationStyle.byName(parts[0], NationStyle.EUROPEENNE).key()),
                        Component.translatable(TownSize.valueOf(parts[1]).key()));
            }
        }
        return Component.literal(e.label());
    }

    /** Dans la liste, sous l'en-tête du style, une ville entière se nomme par sa taille : « Bourg ». */
    private static Component rowLabel(PlanLibrary.Entry e) {
        if (isTown(e.ref())) {
            String[] parts = e.ref().substring(5).split(":");
            if (parts.length >= 2) {
                String size = Component.translatable(TownSize.valueOf(parts[1]).key()).getString();
                return Component.literal(size.isEmpty() ? size : Character.toUpperCase(size.charAt(0)) + size.substring(1));
            }
        }
        return Component.literal(e.label());
    }

    private static Component categoryLabel(String sourceId, String category) {
        if (PlanTool.TOWNS.equals(sourceId)) return Component.translatable(NationStyle.byName(category, NationStyle.EUROPEENNE).key());
        if (PlanTool.FAVORITES.equals(sourceId) && NationStyle.byName(category, null) != null) {
            return Component.translatable("crusadecraft.plan.source.towns").append(" · ").append(Component.translatable(NationStyle.byName(category, null).key()));
        }
        return Component.literal(category.isEmpty() ? "/" : category);
    }

    private void rebuildRows() {
        rows.clear();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        String category = null;
        for (PlanLibrary.Entry e : entries) {
            Component label = entryLabel(e);
            if (!q.isEmpty() && !label.getString().toLowerCase(Locale.ROOT).contains(q) && !e.category().toLowerCase(Locale.ROOT).contains(q)) continue;
            if (filter == ONLY_FAVORITES ? !isFavorite(e.ref()) : filter >= 0 && e.kind() != filter) continue;
            if (!e.category().equals(category)) {
                category = e.category();
                rows.add(new Row(categoryLabel(source, category), null, true, null, -1));
            }
            rows.add(new Row(rowLabel(e), e.ref(), false, label, e.kind()));
        }
        if (selected >= rows.size() || (selected >= 0 && rows.get(selected).header())) selected = -1;
    }

    // ------------------------------------------------------------------ choisir

    /** La référence envoyée au serveur : pour une ville entière, avec la graine du tirage affiché. */
    private String fullRef(String ref) {
        if (!isTown(ref)) return ref;
        return ref + ":" + seeds.computeIfAbsent(ref, k -> random.nextLong() & 0xFFFFFFFFFFFFL);
    }

    private void select(int row) {
        if (row < 0 || row >= rows.size() || rows.get(row).header()) return;
        selected = row;
        String ref = fullRef(rows.get(row).ref());
        if (!details.containsKey(ref)) CrusadeNetwork.toServer(new PlanPackets.DetailRequest(ref));
        // Garder la ligne choisie à l'écran.
        float visible = (listBottom - listTop) / (float) ROW_H;
        if (row < scrollTarget) scrollTarget = row;
        else if (row + 1 > scrollTarget + visible) scrollTarget = row + 1 - visible;
        clampScroll();
    }

    private void selectNext(int dir) {
        int i = selected;
        for (int n = 0; n < rows.size(); n++) {
            i += dir;
            if (i < 0 || i >= rows.size()) return;
            if (!rows.get(i).header()) {
                select(i);
                return;
            }
        }
    }

    private void chooseSelected() {
        if (selected < 0 || selected >= rows.size()) return;
        CrusadeNetwork.toServer(new PlanPackets.Choose(fullRef(rows.get(selected).ref())));
        onClose();
    }

    private void rerollSelected() {
        if (selected < 0 || !isTown(rows.get(selected).ref())) return;
        seeds.put(rows.get(selected).ref(), random.nextLong() & 0xFFFFFFFFFFFFL);
        select(selected);
    }

    private void clampScroll() {
        float max = Math.max(0, rows.size() - (listBottom - listTop) / (float) ROW_H);
        scrollTarget = Mth.clamp(scrollTarget, 0, max);
        int maxSources = (h - 60) / SOURCE_H;
        sourceTarget = Mth.clamp(sourceTarget, 0, Math.max(0, sources.size() - maxSources));
    }

    // ------------------------------------------------------------------ dessin

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        long now = Util.getNanos();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 14);
        sourceScroll += (sourceTarget - sourceScroll) * Math.min(1, dt * 14);

        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.text(g, font, title, left + 10, top + 8, Ui.TEXT_TITLE);
        int count = 0;
        for (Row r : rows) if (!r.header()) count++;
        Component n = Component.translatable("crusadecraft.plan.count", count);
        g.drawString(font, n, lx + lw - font.width(n), top + 9, Ui.TEXT_MUTED, false);

        renderSources(g, mouseX, mouseY);
        renderChips(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        renderDetail(g);

        Component keys = Component.translatable("crusadecraft.plan.keys");
        Ui.fitted(g, font, Component.translatable("crusadecraft.plan.folder", folder), left + 10, top + h - 15, w - 30 - font.width(keys), Ui.TEXT_MUTED);
        g.drawString(font, keys, left + w - 10 - font.width(keys), top + h - 15, Ui.TEXT_MUTED, false);

        boolean has = selected >= 0 && selected < rows.size();
        take.visible = has;
        reroll.visible = has && isTown(rows.get(selected).ref());
        favorite.visible = has;
        if (has) {
            // Favori et autre tirage côte à côte pour une ville entière ; le favori seul sur la ligne sinon.
            boolean two = reroll.visible;
            favorite.setX(dx + 4);
            favorite.setWidth(two ? (dw - 8) / 2 - 1 : dw - 8);
            reroll.setX(dx + 4 + (dw - 8) / 2 + 1);
            reroll.setWidth((dw - 8) / 2 - 1);
            favorite.setMessage(Component.translatable(isFavorite(rows.get(selected).ref()) ? "crusadecraft.plan.favorite.remove" : "crusadecraft.plan.favorite.add"));
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private void renderSources(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = top + 24, y1 = top + h - 24;
        Ui.inset(g, sx, y0, SOURCES_W, y1 - y0);
        g.enableScissor(sx + 1, y0 + 1, sx + SOURCES_W - 1, y1 - 1);
        float y = y0 + 3 - sourceScroll * SOURCE_H;
        for (PlanLibrary.Source s : sources) {
            int iy = Math.round(y);
            boolean sel = s.id().equals(source);
            boolean hover = mouseX >= sx + 2 && mouseX < sx + SOURCES_W - 2 && mouseY >= iy && mouseY < iy + SOURCE_H - 1 && mouseY >= y0 && mouseY < y1;
            if (sel) {
                g.fill(sx + 2, iy, sx + SOURCES_W - 2, iy + SOURCE_H - 1, Ui.PANEL_HIGHLIGHT);
                g.fill(sx + 2, iy, sx + 4, iy + SOURCE_H - 1, Ui.GOLD);
            } else if (hover) {
                g.fill(sx + 2, iy, sx + SOURCES_W - 2, iy + SOURCE_H - 1, 0x30C9A55C);
            }
            Ui.fitted(g, font, sourceLabel(s), sx + 8, iy + 3, SOURCES_W - 40, sel ? Ui.TEXT_TITLE : Ui.TEXT);
            String c = String.valueOf(s.count());
            g.drawString(font, c, sx + SOURCES_W - 6 - font.width(c), iy + 3, Ui.TEXT_MUTED, false);
            y += SOURCE_H;
        }
        g.disableScissor();
    }

    /** Le libellé d'une puce de filtre. */
    private static Component chipLabel(int chip) {
        return switch (chip) {
            case -1 -> Component.translatable("crusadecraft.plan.filter.all");
            case ONLY_FAVORITES -> Component.literal("★");
            default -> Component.translatable("crusadecraft.plan.chip." + chip);
        };
    }

    /** Les puces de filtre, de gauche à droite ; renvoie leurs bornes {x0, x1}. */
    private int[][] chipBounds() {
        // Des puces de même largeur, qui remplissent la ligne ; l'étoile, plus étroite, au bout.
        int[][] out = new int[CHIPS.length][2];
        int gap = 2, star = 14;
        int each = (lw - star - gap * (CHIPS.length - 1)) / (CHIPS.length - 1);
        int x = lx;
        for (int i = 0; i < CHIPS.length; i++) {
            int wdt = CHIPS[i] == ONLY_FAVORITES ? star : each;
            out[i][0] = x;
            out[i][1] = x + wdt;
            x += wdt + gap;
        }
        return out;
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        int y = top + 43;
        int[][] b = chipBounds();
        for (int i = 0; i < CHIPS.length; i++) {
            boolean on = filter == CHIPS[i];
            boolean hover = mouseX >= b[i][0] && mouseX < b[i][1] && mouseY >= y && mouseY < y + 12;
            g.fill(b[i][0], y, b[i][1], y + 12, on ? 0xFF6E5528 : hover ? 0x50C9A55C : Ui.PANEL_INNER);
            g.renderOutline(b[i][0], y, b[i][1] - b[i][0], 12, on ? Ui.GOLD : Ui.GOLD_DARK);
            Component label = chipLabel(CHIPS[i]);
            int tw = Math.min(font.width(label), b[i][1] - b[i][0] - 4);
            Ui.fitted(g, font, label, b[i][0] + (b[i][1] - b[i][0] - tw) / 2, y + 2, b[i][1] - b[i][0] - 4, on ? Ui.TEXT_TITLE : Ui.TEXT);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        Ui.inset(g, lx - 2, listTop - 2, lw + 4, listBottom - listTop + 4);
        if (loading) {
            Ui.text(g, font, Component.translatable("crusadecraft.plan.loading"), lx + 6, listTop + 6, Ui.TEXT_MUTED);
            return;
        }
        if (rows.isEmpty()) {
            Component empty = PlanLibrary.FILES.equals(source) ? Component.translatable("crusadecraft.plan.empty.files")
                    : Component.translatable("crusadecraft.plan.empty");
            g.drawWordWrap(font, empty, lx + 6, listTop + 6, lw - 12, Ui.TEXT_MUTED);
            return;
        }
        g.enableScissor(lx - 1, listTop - 1, lx + lw + 1, listBottom + 1);
        int first = Math.max(0, (int) Math.floor(scroll));
        float y = listTop - (scroll - first) * ROW_H;
        for (int i = first; i < rows.size() && y < listBottom; i++) {
            Row r = rows.get(i);
            int iy = Math.round(y);
            if (r.header()) {
                Ui.fitted(g, font, r.text(), lx + 3, iy + 2, lw - 8, Ui.GOLD);
                g.fill(lx + 3 + Math.min(lw - 12, font.width(r.text()) + 4), iy + 6, lx + lw - 4, iy + 7, Ui.GOLD_DARK);
            } else {
                boolean hover = mouseX >= lx && mouseX < lx + lw && mouseY >= iy && mouseY < iy + ROW_H && mouseY >= listTop && mouseY < listBottom;
                if (i == selected) {
                    g.fillGradient(lx, iy, lx + lw, iy + ROW_H, 0x60C9A55C, 0x30C9A55C);
                    g.fill(lx, iy, lx + 2, iy + ROW_H, Ui.GOLD);
                } else if (hover) {
                    g.fill(lx, iy, lx + lw, iy + ROW_H, 0x25C9A55C);
                }
                Ui.fitted(g, font, r.text(), lx + 10, iy + 2, lw - 24, i == selected ? Ui.TEXT_TITLE : hover ? 0xFFFFF6DF : Ui.TEXT);
                if (isFavorite(r.ref())) g.drawString(font, "★", lx + lw - 10, iy + 2, Ui.GOLD, false);
            }
            y += ROW_H;
        }
        g.disableScissor();
        float visible = (listBottom - listTop) / (float) ROW_H;
        if (rows.size() > visible) {
            int track = listBottom - listTop;
            int barH = Math.max(12, (int) (track * visible / rows.size()));
            int barY = listTop + (int) ((track - barH) * (scroll / Math.max(1, rows.size() - visible)));
            g.fill(lx + lw + 1, barY, lx + lw + 3, barY + barH, Ui.GOLD_DARK);
        }
    }

    private void renderDetail(GuiGraphics g) {
        int y0 = top + 24, y1 = listBottom;
        Ui.inset(g, dx, y0, dw, y1 - y0 + 2);
        if (selected < 0 || selected >= rows.size()) {
            g.drawWordWrap(font, Component.translatable("crusadecraft.plan.pick"), dx + 6, y0 + 8, dw - 12, Ui.TEXT_MUTED);
            return;
        }
        Row row = rows.get(selected);
        String ref = fullRef(row.ref());
        boolean town = isTown(row.ref());
        int y = y0 + 6;
        List<FormattedCharSequence> name = font.split(row.full(), dw - 12);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            g.drawString(font, name.get(i), dx + 6, y, Ui.TEXT_TITLE, false);
            y += 10;
        }
        Component origin = town ? Component.translatable("crusadecraft.plan.hud.town")
                : PlanRef.decode(row.ref()).map(r -> (Component) Component.literal(r.source() == PlanRef.Source.FILE
                ? Component.translatable("crusadecraft.plan.source.files").getString() + " · " + r.category() : r.pack() + " · " + r.category()))
                .orElse(Component.empty());
        if (!town) {
            Ui.fitted(g, font, origin, dx + 6, y, dw - 12, Ui.TEXT_MUTED);
            y += 12;
        } else {
            y += 2;
        }
        PlanTool.Detail d = details.get(ref);
        if (d == null) {
            Ui.text(g, font, Component.translatable("crusadecraft.plan.loading"), dx + 6, y, Ui.TEXT_MUTED);
            return;
        }
        Component size = town ? Component.translatable("crusadecraft.plan.detail.town", d.sizeX(), d.sizeZ(), d.pieces())
                : Component.translatable("crusadecraft.plan.tooltip.size", d.sizeX(), d.sizeY(), d.sizeZ());
        Ui.fitted(g, font, size, dx + 6, y, dw - 12, Ui.TEXT);
        y += 10;
        if (town) {
            String[] parts = row.ref().substring(5).split(":");
            NationStyle style = NationStyle.byName(parts[0], NationStyle.EUROPEENNE);
            Ui.fitted(g, font, Component.translatable("crusadecraft.town.pattern." + style.pattern().name().toLowerCase(Locale.ROOT)), dx + 6, y, dw - 12, Ui.TEXT);
            y += 10;
            Ui.fitted(g, font, Component.translatable(d.walled() ? "crusadecraft.plan.detail.walled" : "crusadecraft.plan.detail.open"), dx + 6, y, dw - 12, Ui.TEXT_MUTED);
            y += 10;
        } else if (!d.role().isEmpty() || d.kind() != PlanKinds.AUTRE) {
            Component kind = Component.translatable("crusadecraft.plan.kind." + d.kind());
            Component line = d.role().isEmpty() ? kind : kind.copy().append(" · ").append(Component.translatable("crusadecraft.plan.role." + d.role()));
            Ui.fitted(g, font, line, dx + 6, y, dw - 12, Ui.GOLD);
            y += 10;
        }
        if (!town && d.huts() > 0) {
            Ui.fitted(g, font, Component.translatable("crusadecraft.plan.detail.huts", d.huts()), dx + 6, y, dw - 12, Ui.TEXT_MUTED);
            y += 10;
        }
        // L'aperçu vu du ciel, qui paraît en fondu.
        boolean showLegend = town && favorite.getY() - 6 - y - legendHeight() - 6 >= 56;
        int bottom = favorite.getY() - 6 - (showLegend ? legendHeight() + 6 : 0);
        int area = Math.min(dw - 12, bottom - y - 4);
        if (area > 24 && d.gridW() > 0 && d.gridH() > 0) {
            float scale = Math.min(area / (float) d.gridW(), area / (float) d.gridH());
            int pw = (int) (d.gridW() * scale), ph = (int) (d.gridH() * scale);
            int px = dx + (dw - pw) / 2, py = y + 4;
            float fade = Mth.clamp((Util.getMillis() - detailArrived.getOrDefault(ref, 0L)) / 250f, 0, 1);
            g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, Ui.GOLD_DARK);
            g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, town ? 0xFF33402A : 0xFF1C1712);
            int[] c = d.colors();
            for (int gz = 0; gz < d.gridH(); gz++) {
                for (int gx = 0; gx < d.gridW(); gx++) {
                    int col = c[gz * d.gridW() + gx];
                    if (col == 0) continue;
                    int x0 = px + (int) (gx * scale), z0 = py + (int) (gz * scale);
                    int x1 = px + (int) ((gx + 1) * scale), z1 = py + (int) ((gz + 1) * scale);
                    g.fill(x0, z0, Math.max(x0 + 1, x1), Math.max(z0 + 1, z1), ((int) (fade * 255) << 24) | (col & 0xFFFFFF));
                }
            }
            Component north = Component.translatable("crusadecraft.plan.detail.north");
            g.drawString(font, north, px + pw - font.width(north) - 2, py + 2, 0xFFFFF6DF, true);
            if (showLegend) legend(g, dx + 6, py + ph + 6);
        }
    }

    private static final int[][] LEGEND = {{0xE7BD55, 0}, {0xB5714A, 1}, {0xD9C24A, 2}, {0xC9D6E8, 3}, {0xB0413A, 4}, {0x7FAE4E, 5}};

    /** La hauteur de la légende, selon la largeur du panneau. */
    private int legendHeight() {
        int cx = 0, rowsCount = 1;
        for (int[] k : LEGEND) {
            int wdt = 8 + font.width(Component.translatable("crusadecraft.plan.legend." + k[1])) + 6;
            if (cx + wdt > dw - 10) {
                cx = 0;
                rowsCount++;
            }
            cx += wdt;
        }
        return rowsCount * 10;
    }

    /** La légende du plan d'une ville : une case de couleur par sorte de bâtiment. */
    private void legend(GuiGraphics g, int x, int y) {
        int cx = x, cy = y;
        for (int[] k : LEGEND) {
            Component label = Component.translatable("crusadecraft.plan.legend." + k[1]);
            int wdt = 8 + font.width(label) + 6;
            if (cx + wdt > dx + dw - 4) {
                cx = x;
                cy += 10;
            }
            g.fill(cx, cy + 1, cx + 6, cy + 7, 0xFF000000 | k[0]);
            g.drawString(font, label, cx + 8, cy, Ui.TEXT_MUTED, false);
            cx += wdt;
        }
    }

    // ------------------------------------------------------------------ gestes

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int[][] chips = chipBounds();
        for (int i = 0; i < CHIPS.length; i++) {
            if (mx >= chips[i][0] && mx < chips[i][1] && my >= top + 43 && my < top + 55) {
                filter = filter == CHIPS[i] ? -1 : CHIPS[i];
                scrollTarget = 0;
                selected = -1;
                rebuildRows();
                selectNext(1);
                return true;
            }
        }
        int y0 = top + 24, y1 = top + h - 24;
        if (mx >= sx && mx < sx + SOURCES_W && my >= y0 && my < y1) {
            int i = (int) ((my - y0 - 3) / SOURCE_H + sourceScroll);
            if (i >= 0 && i < sources.size()) {
                String id = sources.get(i).id();
                if (!id.equals(source)) {
                    source = id;
                    entries = List.of();
                    rows.clear();
                    selected = -1;
                    request(id);
                }
                return true;
            }
        }
        if (mx >= lx && mx < lx + lw && my >= listTop && my < listBottom) {
            int i = (int) ((my - listTop) / ROW_H + scroll);
            if (i >= 0 && i < rows.size() && !rows.get(i).header()) {
                long now = Util.getMillis();
                if (i == lastClickRow && now - lastClick < 400) {
                    chooseSelected();
                    return true;
                }
                lastClick = now;
                lastClickRow = i;
                select(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN) {
            selectNext(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            selectNext(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            chooseSelected();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < lx - 2) sourceTarget -= (float) Math.signum(delta) * 2;
        else scrollTarget -= (float) Math.signum(delta) * (hasShiftDown() ? 12 : 3);
        clampScroll();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
