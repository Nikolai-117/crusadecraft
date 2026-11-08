package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.place.LieuKind;
import fr.crusadecraft.core.place.LieuView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LieuPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Les lieux (P15) : à gauche l'arbre des titres et des places (un royaume, ses villes, ses forts…), à droite la fiche
 * du lieu choisi, avec le texte qu'y écrit le joueur. En haut, la chaîne des lieux où il se tient.
 *
 * <p>L'écran ne fait que demander : le serveur vérifie, répond par un message au pied, et renvoie les lieux.
 */
public class LieuxScreen extends Screen {
    private static final int LIST_W = 176;
    private static final int ROW_H = 11;

    private LieuView view;
    private int listScroll, textScroll;
    private int left, top, w, h, listTop, listBottom, rightX, rightW, textTop, textBottom;
    private Component status;
    private boolean statusOk;
    private long statusUntil;
    private UUID armed;
    private long armedUntil;

    public LieuxScreen(LieuView view) {
        super(Component.translatable("crusadecraft.lieu.title"));
        this.view = view;
        takeStatus(view);
    }

    public void refresh(LieuView newView) {
        UUID before = selectedId();
        this.view = newView;
        if (before == null || !before.equals(selectedId())) textScroll = 0;
        takeStatus(newView);
        if (minecraft != null) rebuildWidgets();
    }

    private void takeStatus(LieuView v) {
        if (v.status() != null) {
            status = v.status();
            statusOk = v.statusOk();
            statusUntil = Util.getMillis() + 6000;
        }
    }

    public LieuView view() {
        return view;
    }

    UUID selectedId() {
        return view.selection() == null ? null : view.selection().id();
    }

    private static void request(UUID id) {
        CrusadeNetwork.toServer(LieuPackets.Request.of(id));
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 500);
        h = Math.min(height - 16, 300);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listTop = top + 40;
        rightX = left + 8 + LIST_W + 8;
        rightW = left + w - 8 - rightX;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 58, top + 6, 50, 16).build(KitButton::new));

        Button create = Button.builder(Component.translatable("crusadecraft.lieu.new"),
                b -> minecraft.setScreen(LieuForm.create(this, null, null, view.canEdit() ? selectedId() : null)))
                .bounds(left + 8, top + h - 24, LIST_W, 16).build(KitButton::new);
        create.setTooltip(Tooltip.create(Component.translatable(view.canEdit() ? "crusadecraft.lieu.new.tip" : "crusadecraft.lieu.new.survival.tip")));
        addRenderableWidget(create);
        listBottom = top + h - 28;

        LieuView.Selection sel = view.selection();
        if (sel == null) return;
        List<Button> actions = new ArrayList<>();
        if (sel.textEditable()) actions.add(button("crusadecraft.lieu.write", "crusadecraft.lieu.write.tip",
                b -> minecraft.setScreen(new LieuTextScreen(this, sel.id(), sel.name(), sel.text()))));
        if (sel.renamable()) actions.add(button("crusadecraft.lieu.rename", null,
                b -> minecraft.setScreen(LieuForm.rename(this, sel.id(), sel.rawName()))));
        if (sel.reparentable()) actions.add(button("crusadecraft.lieu.reparent", "crusadecraft.lieu.reparent.tip",
                b -> minecraft.setScreen(LieuForm.parentPicker(this, sel.id()))));
        if (view.canEdit() && sel.deletable()) actions.add(button("crusadecraft.lieu.holder", "crusadecraft.lieu.holder.tip",
                b -> minecraft.setScreen(LieuForm.holder(this, sel.id()))));
        if (sel.hasPos()) actions.add(button("crusadecraft.lieu.on_map", null, b -> {
            MapScreen.centerNextOn(sel.x(), sel.z(), 3);
            CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
        }));
        if (sel.deletable()) {
            Button del = Button.builder(armedLabel(sel.id()), b -> {
                if (armed != null && armed.equals(sel.id()) && Util.getMillis() < armedUntil) {
                    armed = null;
                    CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.DELETE, sel.id(), "", null));
                } else {
                    armed = sel.id();
                    armedUntil = Util.getMillis() + 4000;
                }
                rebuildWidgets();
            }).size(70, 16).build(KitButton::new);
            del.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.delete.tip")));
            actions.add(del);
        }
        // Les actions en rangées de trois au pied de la fiche (deux sur un écran très étroit).
        int perRow = Math.max(2, Math.min(3, (rightW + 2) / 66));
        int bw = (rightW - (perRow - 1) * 2) / perRow;
        int rows = Math.max(1, (actions.size() + perRow - 1) / perRow);
        int ay = top + h - 24 - (rows - 1) * 18;
        for (int i = 0; i < actions.size(); i++) {
            Button b = actions.get(i);
            b.setX(rightX + (i % perRow) * (bw + 2));
            b.setY(ay + (i / perRow) * 18);
            b.setWidth(bw);
            addRenderableWidget(b);
        }
        textBottom = actions.isEmpty() ? top + h - 8 : ay - 6;
    }

    private Button button(String key, String tip, Button.OnPress press) {
        Button b = Button.builder(Component.translatable(key), press).size(72, 16).build(KitButton::new);
        if (tip != null) b.setTooltip(Tooltip.create(Component.translatable(tip)));
        return b;
    }

    private Component armedLabel(UUID id) {
        boolean on = armed != null && armed.equals(id) && Util.getMillis() < armedUntil;
        return Component.translatable(on ? "crusadecraft.mapedit.confirm" : "crusadecraft.lieu.delete");
    }

    @Override
    public void tick() {
        if (armed != null && Util.getMillis() >= armedUntil) {
            armed = null;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 9, 1.2f, Ui.TEXT_TITLE, false);
        // Sous le titre : où l'on est, ou le résultat de la dernière demande.
        boolean showStatus = status != null && Util.getMillis() < statusUntil;
        Component line = showStatus ? status : Component.translatable("crusadecraft.lieu.here", view.here());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), w - 80)), left + 10, top + 25,
                showStatus ? (statusOk ? Ui.GREEN : Ui.RED) : Ui.TEXT_MUTED);

        // L'arbre.
        Ui.inset(g, left + 8, listTop, LIST_W, listBottom - listTop);
        List<LieuView.Entry> entries = view.entries();
        if (entries.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.lieu.empty"), left + 12, listTop + 4, LIST_W - 8, Ui.TEXT_MUTED);
        }
        int visible = (listBottom - listTop - 4) / ROW_H;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - visible)));
        UUID chosen = selectedId();
        for (int i = listScroll; i < entries.size() && i - listScroll < visible; i++) {
            LieuView.Entry e = entries.get(i);
            int ry = listTop + 2 + (i - listScroll) * ROW_H;
            boolean on = e.id().equals(chosen);
            if (on || Ui.inside(mouseX, mouseY, left + 10, ry, LIST_W - 4, ROW_H)) {
                g.fill(left + 10, ry, left + 8 + LIST_W - 2, ry + ROW_H, on ? 0xFF2E3740 : Ui.PANEL_HIGHLIGHT);
            }
            int x = left + 12 + Math.min(e.depth(), 6) * 8;
            LieuKind kind = LieuKind.byOrdinal(e.kind());
            g.drawString(font, String.valueOf(kind.icon()), x, ry + 2, 0xFF000000 | e.color(), false);
            int nameX = x + 10;
            String name = font.plainSubstrByWidth(e.name().getString(), left + 8 + LIST_W - 4 - nameX - (e.hasText() ? 8 : 0));
            g.drawString(font, name, nameX, ry + 2, on ? Ui.TEXT_TITLE : kind.title() ? Ui.TEXT : Ui.TEXT_MUTED, false);
            if (e.hasText()) g.drawString(font, "✎", left + 8 + LIST_W - 11, ry + 2, Ui.GOLD_DARK, false);
        }

        // La fiche.
        LieuView.Selection sel = view.selection();
        if (sel == null) {
            Ui.wrapped(g, font, Component.translatable(entries.isEmpty() ? "crusadecraft.lieu.none_yet" : "crusadecraft.lieu.pick"),
                    rightX, listTop + 4, rightW, Ui.TEXT_MUTED);
        } else {
            renderSheet(g, sel, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSheet(GuiGraphics g, LieuView.Selection sel, int mouseX, int mouseY) {
        LieuKind kind = LieuKind.byOrdinal(sel.kind());
        int y = listTop;
        Ui.scaled(g, font, Component.literal(kind.icon() + " ").append(sel.name()), rightX, y, 1.15f, Ui.TEXT_TITLE, false);
        y += 13;
        Component sub = Component.translatable(kind.key()).append(Component.literal(" · ")).append(sel.origin());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), rightW)), rightX, y, Ui.TEXT_MUTED);
        y += 13;
        y = line(g, "crusadecraft.lieu.field.holder", sel.holder(), y);
        y = line(g, "crusadecraft.lieu.field.parent", sel.parentName(), y);
        if (sel.population() > 0) y = line(g, "crusadecraft.lieu.field.population", Component.literal(String.valueOf(sel.population())), y);
        if (sel.hasPos()) {
            Component pos = sel.radius() > 0 ? Component.translatable("crusadecraft.lieu.pos.radius", sel.x(), sel.z(), sel.radius())
                    : Component.translatable("crusadecraft.lieu.pos", sel.x(), sel.z());
            y = line(g, "crusadecraft.lieu.field.position", pos, y);
        }
        if (sel.foundedDay() > 0) y = line(g, "crusadecraft.lieu.field.founded", Component.translatable("crusadecraft.letter.day", sel.foundedDay()), y);
        // Le texte de la fiche, sur parchemin.
        textTop = y + 4;
        int bottom = Math.max(textTop + 24, textBottom);
        Ui.parchment(g, rightX, textTop, rightW, bottom - textTop);
        if (sel.text().isBlank()) {
            Ui.wrapped(g, font, Component.translatable(sel.textEditable() ? "crusadecraft.lieu.text.empty.writer" : "crusadecraft.lieu.text.empty"),
                    rightX + 6, textTop + 5, rightW - 12, Ui.INK_MUTED);
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(sel.text()), rightW - 12);
        int fit = Math.max(1, (bottom - textTop - 8) / 10);
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, lines.size() - fit)));
        for (int i = textScroll; i < lines.size() && i - textScroll < fit; i++) {
            g.drawString(font, lines.get(i), rightX + 6, textTop + 5 + (i - textScroll) * 10, Ui.INK, false);
        }
        if (lines.size() > fit) {
            String more = (textScroll + fit < lines.size() ? "▼ " : "") + (textScroll > 0 ? "▲" : "");
            g.drawString(font, more, rightX + rightW - 6 - font.width(more), bottom - 10, Ui.INK_MUTED, false);
        }
    }

    private int line(GuiGraphics g, String key, Component value, int y) {
        Component label = Component.translatable(key);
        Ui.text(g, font, label, rightX, y, Ui.GOLD);
        int lx = rightX + font.width(label) + 4;
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(value.getString(), rightX + rightW - lx)), lx, y, Ui.TEXT);
        return y + 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (Ui.inside(mouseX, mouseY, left + 8, listTop, LIST_W, listBottom - listTop)) {
            int index = listScroll + (int) ((mouseY - listTop - 2) / ROW_H);
            if (index >= 0 && index < view.entries().size()) {
                armed = null;
                request(view.entries().get(index).id());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside(mouseX, mouseY, left + 8, listTop, LIST_W, listBottom - listTop)) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta) * 3);
            return true;
        }
        if (view.selection() != null && Ui.inside(mouseX, mouseY, rightX, textTop, rightW, Math.max(24, textBottom - textTop))) {
            textScroll = Math.max(0, textScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Aperçu du banc d'essai visuel : choisir un lieu sans passer par le serveur. */
    public void previewScroll(int scroll) {
        listScroll = scroll;
    }
}
