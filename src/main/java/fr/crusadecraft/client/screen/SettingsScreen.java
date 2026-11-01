package fr.crusadecraft.client.screen;

import fr.crusadecraft.config.SettingsService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.SettingsPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les réglages du monde, en jeu (P30) : les sections à gauche, leurs réglages à droite. Oui/non d'un clic, nombres
 * avec − et + (Maj : par dix) ou saisis puis validés, choix qu'on fait défiler ; les listes se lisent seulement. Le
 * commentaire du fichier s'affiche au survol du nom.
 */
public class SettingsScreen extends Screen {
    private static final int SECTIONS_W = 120, ROW_H = 20, SECTION_H = 11, CONTROL_W = 132;

    private SettingsService.View view;
    private final Map<String, List<SettingsService.Entry>> sections = new LinkedHashMap<>();
    private String section;
    private int sectionScroll, rowScroll;
    private int left, top, w, h, rx, rw, rowsTop, rowsBottom;
    private Component status;
    private boolean statusOk;
    private long statusUntil;

    public SettingsScreen(SettingsService.View view) {
        super(Component.translatable("crusadecraft.settings.title"));
        load(view);
    }

    private void load(SettingsService.View v) {
        this.view = v;
        sections.clear();
        for (SettingsService.Entry e : v.entries()) {
            int dot = e.path().indexOf('.');
            String s = dot < 0 ? "" : e.path().substring(0, dot);
            sections.computeIfAbsent(s, k -> new ArrayList<>()).add(e);
        }
        if (section == null || !sections.containsKey(section)) section = sections.keySet().stream().findFirst().orElse(null);
    }

    public void refresh(SettingsService.View v) {
        load(v);
        if (minecraft != null) rebuildWidgets();
    }

    public void showStatus(boolean success, Component message) {
        status = message;
        statusOk = success;
        statusUntil = Util.getMillis() + 6000;
    }

    private static Component sectionName(String s) {
        return s.isEmpty() ? Component.translatable("crusadecraft.settings.general")
                : Component.translatableWithFallback("crusadecraft.settings.section." + s, s);
    }

    private static String name(SettingsService.Entry e) {
        int dot = e.path().lastIndexOf('.');
        return SettingsService.humanize(dot < 0 ? e.path() : e.path().substring(dot + 1));
    }


    private List<SettingsService.Entry> rows() {
        return section == null ? List.of() : sections.getOrDefault(section, List.of());
    }

    private void send(String path, String value) {
        CrusadeNetwork.toServer(new SettingsPackets.Request(path, value));
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 560);
        h = Math.min(height - 16, 310);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rx = left + 8 + SECTIONS_W + 8;
        rw = left + w - 8 - rx;
        rowsTop = top + 26;
        rowsBottom = top + h - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 68, top + h - 24, 60, 16).build());
        if (!view.allowed()) return;
        List<SettingsService.Entry> rows = rows();
        int visible = Math.max(1, (rowsBottom - rowsTop) / ROW_H);
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, rows.size() - visible)));
        int cx = rx + rw - CONTROL_W;
        for (int i = rowScroll; i < rows.size() && i - rowScroll < visible; i++) {
            SettingsService.Entry e = rows.get(i);
            int y = rowsTop + (i - rowScroll) * ROW_H + 2;
            switch (e.kind()) {
                case BOOL -> {
                    boolean on = Boolean.parseBoolean(e.value());
                    addRenderableWidget(Button.builder(Component.translatable(on ? "crusadecraft.settings.yes" : "crusadecraft.settings.no")
                            .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED), b -> send(e.path(), String.valueOf(!on)))
                            .bounds(cx, y, CONTROL_W, 16).build());
                }
                case ENUM -> {
                    int at = Math.max(0, e.choices().indexOf(e.value()));
                    String next = e.choices().isEmpty() ? e.value() : e.choices().get((at + 1) % e.choices().size());
                    addRenderableWidget(Button.builder(Component.literal(e.value()), b -> send(e.path(), next)).bounds(cx, y, CONTROL_W, 16)
                            .tooltip(Tooltip.create(Component.literal(String.join(", ", e.choices())))).build());
                }
                case INT, DOUBLE, TEXT -> {
                    boolean number = e.kind() != SettingsService.Kind.TEXT;
                    int boxW = CONTROL_W - (number ? 2 * 16 + 4 : 0) - 26;
                    int bx = cx + (number ? 18 : 0);
                    EditBox box = new EditBox(font, bx, y, boxW, 16, Component.literal(name(e)));
                    box.setMaxLength(e.kind() == SettingsService.Kind.TEXT ? 200 : 24);
                    box.setValue(e.value());
                    addRenderableWidget(box);
                    addRenderableWidget(Button.builder(Component.literal("✔"), b -> send(e.path(), box.getValue()))
                            .bounds(bx + boxW + 2, y, 22, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.settings.apply"))).build());
                    if (number) {
                        addRenderableWidget(Button.builder(Component.literal("−"), b -> send(e.path(), step(e, -1))).bounds(cx, y, 16, 16).build());
                        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(e.path(), step(e, 1))).bounds(cx + CONTROL_W - 16, y, 16, 16).build());
                    }
                }
                case LIST -> { }
            }
        }
    }

    /** Un cran de plus ou de moins (dix avec Maj), dans les bornes du réglage. */
    private static String step(SettingsService.Entry e, int dir) {
        int k = hasShiftDown() ? 10 : 1;
        try {
            if (e.kind() == SettingsService.Kind.DOUBLE) {
                double v = Double.parseDouble(e.value()) + dir * k * 0.05;
                if (!e.min().isEmpty()) v = Math.max(Double.parseDouble(e.min()), v);
                if (!e.max().isEmpty()) v = Math.min(Double.parseDouble(e.max()), v);
                return String.valueOf(Math.round(v * 100) / 100.0);
            }
            long v = Long.parseLong(e.value()) + (long) dir * k;
            if (!e.min().isEmpty()) v = Math.max(Long.parseLong(e.min()), v);
            if (!e.max().isEmpty()) v = Math.min(Long.parseLong(e.max()), v);
            return String.valueOf(v);
        } catch (NumberFormatException ex) {
            return e.value();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.2f, Ui.TEXT_TITLE, false);
        if (!view.allowed()) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.settings.denied"), left + w / 2, top + h / 2 - 8, w - 40, Ui.TEXT_MUTED);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        Component hint = Component.translatable("crusadecraft.settings.hint");
        Ui.scaled(g, font, hint, left + w - 10 - Math.round(font.width(hint) * 0.75f), top + 10, 0.75f, Ui.TEXT_MUTED, false);
        // Les sections.
        int sx = left + 8, sy = top + 26, sh = h - 26 - 30;
        Ui.inset(g, sx, sy, SECTIONS_W, sh);
        List<String> names = new ArrayList<>(sections.keySet());
        int visible = Math.max(1, (sh - 4) / SECTION_H);
        sectionScroll = Math.max(0, Math.min(sectionScroll, Math.max(0, names.size() - visible)));
        for (int i = sectionScroll; i < names.size() && i - sectionScroll < visible; i++) {
            int y = sy + 2 + (i - sectionScroll) * SECTION_H;
            boolean chosen = names.get(i).equals(section);
            if (chosen || Ui.inside(mouseX, mouseY, sx + 2, y, SECTIONS_W - 4, SECTION_H)) {
                g.fill(sx + 2, y, sx + SECTIONS_W - 2, y + SECTION_H - 1, chosen ? 0xFF3A2E22 : Ui.PANEL_HIGHLIGHT);
            }
            String label = font.plainSubstrByWidth(sectionName(names.get(i)).getString(), SECTIONS_W - 10);
            Ui.text(g, font, Component.literal(label), sx + 5, y + 1, chosen ? Ui.TEXT_TITLE : Ui.TEXT);
        }
        // Il y en a d'autres plus bas : une flèche le dit (la molette les fait venir).
        if (sectionScroll + visible < names.size()) Ui.centered(g, font, Component.literal("▼"), sx + SECTIONS_W / 2, sy + sh - 10, Ui.GOLD);
        // Les réglages de la section choisie.
        Ui.inset(g, rx, rowsTop - 2, rw, rowsBottom - rowsTop + 4);
        List<SettingsService.Entry> rows = rows();
        int rowsVisible = Math.max(1, (rowsBottom - rowsTop) / ROW_H);
        List<Component> tip = null;
        for (int i = rowScroll; i < rows.size() && i - rowScroll < rowsVisible; i++) {
            SettingsService.Entry e = rows.get(i);
            int y = rowsTop + (i - rowScroll) * ROW_H;
            int nameW = rw - CONTROL_W - 12;
            Ui.fitted(g, font, Component.literal(name(e)), rx + 5, y + 6, nameW, Ui.TEXT);
            if (e.kind() == SettingsService.Kind.LIST) {
                String v = font.plainSubstrByWidth(e.value(), Math.round(CONTROL_W / 0.7f));
                Ui.scaled(g, font, Component.literal(v), rx + rw - CONTROL_W, y + 7, 0.7f, Ui.TEXT_MUTED, false);
            }
            if (Ui.inside(mouseX, mouseY, rx + 2, y, nameW + 4, ROW_H)) {
                tip = new ArrayList<>();
                tip.add(Component.literal(e.path()).withStyle(ChatFormatting.GOLD));
                for (String line : e.comment().split("\n")) if (!line.isBlank()) tip.add(Component.literal(line.strip()).withStyle(ChatFormatting.GRAY));
                if (!e.min().isEmpty()) tip.add(Component.translatable("crusadecraft.settings.range", e.min(), e.max()).withStyle(ChatFormatting.DARK_GRAY));
                if (e.kind() == SettingsService.Kind.LIST) tip.add(Component.translatable("crusadecraft.settings.list").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (rowScroll + rowsVisible < rows.size()) Ui.centered(g, font, Component.literal("▼"), rx + rw / 2, rowsBottom - 8, Ui.GOLD);
        if (status != null && Util.getMillis() < statusUntil) {
            Ui.scaled(g, font, status, left + 10, top + h - 19, 0.8f, statusOk ? Ui.GREEN : Ui.RED, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int sx = left + 8, sy = top + 26;
        if (Ui.inside(mx, my, sx, sy, SECTIONS_W, h - 56)) {
            int i = sectionScroll + (int) ((my - sy - 2) / SECTION_H);
            List<String> names = new ArrayList<>(sections.keySet());
            if (i >= 0 && i < names.size()) {
                section = names.get(i);
                rowScroll = 0;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < rx) sectionScroll = Math.max(0, sectionScroll - (int) Math.signum(delta));
        else rowScroll = Math.max(0, rowScroll - (int) Math.signum(delta));
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
