package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.build.plan.NationStyle;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.build.plan.TownSize;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.PlanPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le catalogue du plan d'architecte (P34) : les étagères à gauche (villes entières, chaque pack de style, les fichiers
 * du joueur), leurs plans à droite rangés par dossier, une recherche au-dessus. Un clic choisit le plan et referme.
 */
public class PlanCatalogScreen extends Screen {
    private static final int SOURCES_W = 132, ROW_H = 11;

    private final List<PlanLibrary.Source> sources;
    private final String folder;
    private String source;
    private List<PlanLibrary.Entry> entries = List.of();
    private final List<Row> rows = new ArrayList<>();
    private int sourceScroll, rowScroll;
    private int left, top, w, h, rx, rw, listTop, listBottom;
    private EditBox search;
    private boolean loading;

    /** Une ligne de la liste : un en-tête de dossier, ou un plan. */
    private record Row(Component text, String ref, boolean header) {}

    public PlanCatalogScreen(List<PlanLibrary.Source> sources, String folder) {
        super(Component.translatable("crusadecraft.plan.catalog"));
        this.sources = sources;
        this.folder = folder;
        this.source = sources.isEmpty() ? null : sources.get(0).id();
    }

    @Override
    protected void init() {
        w = Math.min(width - 20, 460);
        h = Math.min(height - 20, 280);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rx = left + SOURCES_W + 14;
        rw = left + w - 10 - rx;
        String previous = search == null ? "" : search.getValue();
        search = new EditBox(font, rx, top + 24, rw, 14, Component.translatable("crusadecraft.plan.search"));
        search.setHint(Component.translatable("crusadecraft.plan.search"));
        search.setValue(previous);
        search.setResponder(s -> {
            rowScroll = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        listTop = top + 42;
        listBottom = top + h - 22;
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
        rowScroll = 0;
        rebuildRows();
    }

    private static Component sourceLabel(PlanLibrary.Source s) {
        if (PlanTool.TOWNS.equals(s.id())) return Component.translatable("crusadecraft.plan.source.towns");
        if (PlanLibrary.FILES.equals(s.id())) return Component.translatable("crusadecraft.plan.source.files");
        return Component.literal(s.label());
    }

    /** « Ville chinoise — bourg » pour une ville entière, sinon le nom du plan. */
    private static Component entryLabel(PlanLibrary.Entry e) {
        if (e.ref().startsWith("town:")) {
            String[] parts = e.ref().substring(5).split(":");
            if (parts.length == 2) {
                return Component.translatable("crusadecraft.plan.town_name", Component.translatable(NationStyle.byName(parts[0], NationStyle.EUROPEENNE).key()),
                        Component.translatable(TownSize.valueOf(parts[1]).key()));
            }
        }
        return Component.literal(e.label());
    }

    private static Component categoryLabel(String sourceId, String category) {
        if (PlanTool.TOWNS.equals(sourceId)) {
            return Component.translatable(NationStyle.byName(category, NationStyle.EUROPEENNE).key());
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
            if (!e.category().equals(category)) {
                category = e.category();
                rows.add(new Row(categoryLabel(source, category), null, true));
            }
            rows.add(new Row(label, e.ref(), false));
        }
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.text(g, font, title, left + 10, top + 8, Ui.TEXT_TITLE);
        // Les étagères.
        Ui.inset(g, left + 6, top + 22, SOURCES_W + 2, h - 46);
        int sy = top + 26;
        int maxSources = (h - 54) / 13;
        for (int i = sourceScroll; i < sources.size() && i < sourceScroll + maxSources; i++) {
            PlanLibrary.Source s = sources.get(i);
            boolean sel = s.id().equals(source);
            boolean hover = mouseX >= left + 8 && mouseX < left + 8 + SOURCES_W - 2 && mouseY >= sy && mouseY < sy + 12;
            if (sel || hover) g.fill(left + 8, sy - 1, left + 6 + SOURCES_W, sy + 11, sel ? Ui.PANEL_HIGHLIGHT : 0x40C9A55C);
            Ui.fitted(g, font, sourceLabel(s), left + 11, sy + 1, SOURCES_W - 34, sel ? Ui.TEXT_TITLE : Ui.TEXT);
            String n = String.valueOf(s.count());
            g.drawString(font, n, left + 4 + SOURCES_W - font.width(n), sy + 1, Ui.TEXT_MUTED, false);
            sy += 13;
        }
        // Les plans.
        Ui.inset(g, rx - 2, listTop - 2, rw + 4, listBottom - listTop + 4);
        if (loading) {
            Ui.text(g, font, Component.translatable("crusadecraft.plan.loading"), rx + 4, listTop + 4, Ui.TEXT_MUTED);
        } else if (rows.isEmpty()) {
            Component empty = PlanLibrary.FILES.equals(source) ? Component.translatable("crusadecraft.plan.empty.files")
                    : Component.translatable("crusadecraft.plan.empty");
            g.drawWordWrap(font, empty, rx + 4, listTop + 4, rw - 8, Ui.TEXT_MUTED);
        }
        int y = listTop;
        for (int i = rowScroll; i < rows.size() && i < rowScroll + visibleRows(); i++) {
            Row r = rows.get(i);
            if (r.header()) {
                Ui.fitted(g, font, r.text(), rx + 2, y + 1, rw - 6, Ui.GOLD);
            } else {
                boolean hover = mouseX >= rx && mouseX < rx + rw && mouseY >= y && mouseY < y + ROW_H;
                if (hover) g.fill(rx, y, rx + rw, y + ROW_H, 0x40C9A55C);
                Ui.fitted(g, font, r.text(), rx + 10, y + 1, rw - 14, hover ? Ui.TEXT_TITLE : Ui.TEXT);
            }
            y += ROW_H;
        }
        if (rows.size() > visibleRows()) {
            int barH = Math.max(10, (listBottom - listTop) * visibleRows() / rows.size());
            int barY = listTop + (listBottom - listTop - barH) * rowScroll / Math.max(1, rows.size() - visibleRows());
            g.fill(rx + rw, barY, rx + rw + 2, barY + barH, Ui.GOLD_DARK);
        }
        // Où déposer ses fichiers.
        Ui.fitted(g, font, Component.translatable("crusadecraft.plan.folder", folder), left + 10, top + h - 15, w - 20, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int sy = top + 26;
        int maxSources = (h - 54) / 13;
        for (int i = sourceScroll; i < sources.size() && i < sourceScroll + maxSources; i++) {
            if (mx >= left + 8 && mx < left + 6 + SOURCES_W && my >= sy && my < sy + 12) {
                String id = sources.get(i).id();
                if (!id.equals(source)) {
                    source = id;
                    entries = List.of();
                    rows.clear();
                    request(id);
                }
                return true;
            }
            sy += 13;
        }
        if (mx >= rx && mx < rx + rw && my >= listTop && my < listBottom) {
            int i = rowScroll + (int) ((my - listTop) / ROW_H);
            if (i >= 0 && i < rows.size() && !rows.get(i).header()) {
                CrusadeNetwork.toServer(new PlanPackets.Choose(rows.get(i).ref()));
                onClose();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < rx) {
            int maxSources = (h - 54) / 13;
            sourceScroll = Math.max(0, Math.min(Math.max(0, sources.size() - maxSources), sourceScroll - (int) Math.signum(delta)));
        } else {
            int step = hasShiftDown() ? 10 : 3;
            rowScroll = Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), rowScroll - (int) Math.signum(delta) * step));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
