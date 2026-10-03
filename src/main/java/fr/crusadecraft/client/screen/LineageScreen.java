package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LineagePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le registre des lignées (P13) : à gauche les maisons ou les communautés, à droite celle qu'on a ouverte, rangée
 * par générations (ou par foyers), et ce qu'on peut faire de chacun. Avec Comes Alive, « Faire venir » fait
 * apparaître les gens en villageois près du joueur, arbre généalogique compris.
 *
 * <p>L'écran ne fait que demander : le serveur vérifie, répond par un message au pied, et renvoie le registre.
 */
public class LineageScreen extends Screen {
    private static final int LIST_W = 128;
    private static final int ROW_H = 11;
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private LineageView view;
    private boolean communitiesTab;
    private UUID selectedPerson;
    private int listScroll, rowScroll;
    private int left, top, w, h;
    private int rightX, rightW, rowsTop, rowsBottom, listBottomY;
    private Component status;
    private boolean statusOk;
    private long statusUntil;
    /** Suppression armée (un second clic confirme). */
    private UUID armed;
    private long armedUntil;

    public LineageScreen(LineageView view) {
        super(Component.translatable("crusadecraft.lineage.title"));
        this.view = view;
        if (view.selection() != null) communitiesTab = !view.selection().house();
    }

    public void refresh(LineageView newView) {
        this.view = newView;
        if (newView.selection() != null) communitiesTab = !newView.selection().house();
        if (selectedPerson != null && (newView.selection() == null
                || newView.selection().rows().stream().noneMatch(r -> r.id().equals(selectedPerson)))) {
            selectedPerson = null;
        }
        if (minecraft != null) rebuildWidgets();
    }

    public void showStatus(boolean success, Component message) {
        status = message;
        statusOk = success;
        statusUntil = Util.getMillis() + 6000;
    }

    /** Aperçu du banc d'essai visuel : choisir une personne. */
    public void previewPerson(int rowIndex) {
        if (view.selection() != null && rowIndex >= 0 && rowIndex < view.selection().rows().size()) {
            selectedPerson = view.selection().rows().get(rowIndex).id();
        }
        if (minecraft != null) rebuildWidgets();
    }

    /** La maison ou la communauté affichée, pour que l'écran y revienne après un formulaire. */
    public UUID shownForForm() {
        return shown();
    }

    private UUID shown() {
        return view.selection() == null ? null : view.selection().id();
    }

    private void act(LineagePackets.Action.Op op, UUID target) {
        CrusadeNetwork.toServer(LineagePackets.Action.of(op, target, shown()));
    }

    @Override
    protected void init() {
        w = Math.min(width - 16, 480);
        h = Math.min(height - 16, 300);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rightX = left + 8 + LIST_W + 8;
        rightW = left + w - 8 - rightX;

        int bx = left + w - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx - 50, top + 6, 50, 16).build());
        addRenderableWidget(Button.builder(tabLabel(true), b -> {
            communitiesTab = true;
            listScroll = 0;
            rebuildWidgets();
        }).bounds(bx - 54 - 92, top + 6, 90, 16).build());
        addRenderableWidget(Button.builder(tabLabel(false), b -> {
            communitiesTab = false;
            listScroll = 0;
            rebuildWidgets();
        }).bounds(bx - 54 - 92 - 74, top + 6, 72, 16).build());

        if (view.canEdit()) {
            Button create = Button.builder(Component.translatable(communitiesTab ? "crusadecraft.lineage.new_community" : "crusadecraft.lineage.new_house"),
                    b -> minecraft.setScreen(communitiesTab ? LineageForm.newCommunity(this, view.realms()) : LineageForm.newHouse(this, view.realms())))
                    .bounds(left + 8, top + h - 24, LIST_W, 16).build();
            create.setTooltip(Tooltip.create(Component.translatable(communitiesTab ? "crusadecraft.lineage.new_community.tip"
                    : "crusadecraft.lineage.new_house.tip")));
            addRenderableWidget(create);
        }

        // Les actions sur la maison ou la communauté ouverte, en grille sous la liste de gauche.
        LineageView.Selection sel = view.selection();
        listBottomY = top + h - (view.canEdit() ? 28 : 8);
        if (sel != null && view.canEdit()) {
            List<Button> group = new ArrayList<>();
            group.add(button("crusadecraft.lineage.grow", sel.house() ? "crusadecraft.lineage.grow.house.tip" : "crusadecraft.lineage.grow.community.tip",
                    b -> act(LineagePackets.Action.Op.GROW, sel.id())));
            group.add(button("crusadecraft.lineage.rename", null, b -> minecraft.setScreen(LineageForm.rename(this, sel.id(), sel.title()))));
            if (view.mca()) {
                group.add(button("crusadecraft.lineage.spawn", "crusadecraft.lineage.spawn.all.tip", b -> act(LineagePackets.Action.Op.SPAWN, sel.id())));
                group.add(button("crusadecraft.lineage.tree", "crusadecraft.lineage.tree.tip", b -> act(LineagePackets.Action.Op.SYNC_TREE, sel.id())));
                group.add(button("crusadecraft.lineage.adopt", "crusadecraft.lineage.adopt.tip", b -> act(LineagePackets.Action.Op.ADOPT, sel.id())));
            }
            Button remove = Button.builder(armedLabel(sel.id(), "crusadecraft.lineage.remove"), b -> {
                if (confirm(sel.id())) act(LineagePackets.Action.Op.REMOVE, sel.id());
                rebuildWidgets();
            }).size(54, 15).build();
            remove.setTooltip(Tooltip.create(Component.translatable(sel.house() ? "crusadecraft.lineage.remove.house.tip"
                    : "crusadecraft.lineage.remove.community.tip")));
            group.add(remove);
            int cell = (LIST_W - 2) / 2, rows = (group.size() + 1) / 2;
            int gy = top + h - 26 - rows * 16;
            for (int i = 0; i < group.size(); i++) {
                Button b = group.get(i);
                b.setWidth(cell);
                b.setPosition(left + 8 + (i % 2) * (cell + 2), gy + (i / 2) * 16);
                addRenderableWidget(b);
            }
            listBottomY = gy - 4;
        }
        rowsTop = top + 30 + 24;

        // Les actions sur la personne choisie, en bas du panneau.
        int bottom = top + h - 22;
        LineageView.Row person = selectedRow();
        if (person != null && view.canEdit()) {
            List<Button> group = new ArrayList<>();
            group.add(button("crusadecraft.lineage.rename", null, b -> minecraft.setScreen(LineageForm.rename(this, person.id(), firstName(person.name())))));
            Component roleLabel = Component.translatable("crusadecraft.lineage.role", Component.translatable(person.role().key()));
            Button role = Button.builder(roleLabel, b -> {
                Role[] all = Role.values();
                Role next = all[(person.role().ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length];
                CrusadeNetwork.toServer(new LineagePackets.Action(LineagePackets.Action.Op.ROLE, person.id(), "", next.ordinal(), 0, null, shown()));
            }).size(Math.min(130, 12 + font.width(roleLabel)), 15).build();
            role.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.role.tip")));
            group.add(role);
            group.add(button("crusadecraft.lineage.child", "crusadecraft.lineage.child.tip", b -> act(LineagePackets.Action.Op.ADD_CHILD, person.id())));
            if (person.spouse().isEmpty()) group.add(button("crusadecraft.lineage.marry", "crusadecraft.lineage.marry.tip",
                    b -> act(LineagePackets.Action.Op.MARRY, person.id())));
            group.add(button(person.alive() ? "crusadecraft.lineage.kill" : "crusadecraft.lineage.revive", null,
                    b -> act(LineagePackets.Action.Op.TOGGLE_ALIVE, person.id())));
            if (view.mca() && person.alive()) group.add(button("crusadecraft.lineage.spawn", "crusadecraft.lineage.spawn.one.tip",
                    b -> act(LineagePackets.Action.Op.SPAWN, person.id())));
            Button remove = Button.builder(armedLabel(person.id(), "crusadecraft.lineage.remove"), b -> {
                if (confirm(person.id())) act(LineagePackets.Action.Op.REMOVE, person.id());
                rebuildWidgets();
            }).size(54, 15).build();
            group.add(remove);
            int rowsNeeded = rowsFor(group);
            int start = bottom - rowsNeeded * 16 + 1;
            flow(group, start);
            rowsBottom = start - 14;
        } else {
            rowsBottom = bottom;
        }
    }

    private Button button(String key, String tip, Button.OnPress press) {
        Component label = Component.translatable(key);
        Button b = Button.builder(label, press).size(font.width(label) + 12, 15).build();
        if (tip != null) b.setTooltip(Tooltip.create(Component.translatable(tip)));
        return b;
    }

    /** Range des boutons de gauche à droite dans le panneau, en passant à la ligne quand il le faut. */
    private int flow(List<Button> buttons, int y) {
        int x = rightX;
        for (Button b : buttons) {
            if (x + b.getWidth() > rightX + rightW && x > rightX) {
                x = rightX;
                y += 16;
            }
            b.setPosition(x, y);
            addRenderableWidget(b);
            x += b.getWidth() + 2;
        }
        return y + 16;
    }

    private int rowsFor(List<Button> buttons) {
        int x = rightX, rows = 1;
        for (Button b : buttons) {
            if (x + b.getWidth() > rightX + rightW && x > rightX) {
                x = rightX;
                rows++;
            }
            x += b.getWidth() + 2;
        }
        return rows;
    }

    private Component tabLabel(boolean communities) {
        Component label = Component.translatable(communities ? "crusadecraft.lineage.tab.communities" : "crusadecraft.lineage.tab.houses");
        return communities == communitiesTab ? Component.literal("» ").append(label) : label;
    }

    private Component armedLabel(UUID what, String key) {
        boolean live = what.equals(armed) && Util.getMillis() < armedUntil;
        return live ? Component.translatable("crusadecraft.mapedit.confirm").withStyle(ChatFormatting.RED) : Component.translatable(key);
    }

    private boolean confirm(UUID what) {
        if (what.equals(armed) && Util.getMillis() < armedUntil) {
            armed = null;
            return true;
        }
        armed = what;
        armedUntil = Util.getMillis() + 4000;
        return false;
    }

    private LineageView.Row selectedRow() {
        if (selectedPerson == null || view.selection() == null) return null;
        for (LineageView.Row r : view.selection().rows()) if (r.id().equals(selectedPerson)) return r;
        return null;
    }

    private static String firstName(String full) {
        int space = full.indexOf(' ');
        return space > 0 ? full.substring(0, space) : full;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 9, 1.2f, Ui.TEXT_TITLE, false);
        renderList(g, mouseX, mouseY);
        renderSelection(g, mouseX, mouseY);
        if (status != null && Util.getMillis() < statusUntil) {
            Component s = Component.literal(font.plainSubstrByWidth(status.getString(), w - 20 - (view.canEdit() ? LIST_W : 0)));
            Ui.text(g, font, s, view.canEdit() ? left + 8 + LIST_W + 8 : left + 10, top + h - 10 - 1, statusOk ? 0xFF9FD07A : 0xFFE0796E);
        } else if (!view.mca()) {
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable("crusadecraft.lineage.no_mca.hint").getString(), rightW)),
                    rightX, top + h - 11, Ui.TEXT_MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int listTop() {
        return top + 30;
    }

    private int listBottom() {
        return listBottomY;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 8, y0 = listTop(), y1 = listBottom();
        Ui.inset(g, x, y0, LIST_W, y1 - y0);
        int rowH = 21;
        int count = communitiesTab ? view.communities().size() : view.houses().size();
        if (count == 0) {
            Ui.wrapped(g, font, Component.translatable(communitiesTab ? "crusadecraft.lineage.empty.communities" : "crusadecraft.lineage.empty.houses"),
                    x + 5, y0 + 6, LIST_W - 10, Ui.TEXT_MUTED);
            return;
        }
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, count - (y1 - y0 - 4) / rowH)));
        g.enableScissor(x + 1, y0 + 1, x + LIST_W - 1, y1 - 1);
        for (int i = listScroll; i < count; i++) {
            int ry = y0 + 2 + (i - listScroll) * rowH;
            if (ry > y1) break;
            UUID id;
            String name;
            Component sub;
            int color;
            if (communitiesTab) {
                LineageView.CommunityLine c = view.communities().get(i);
                id = c.id();
                name = c.name();
                sub = Component.translatable("crusadecraft.lineage.list.community", Component.translatable(c.kind().key()), c.count());
                color = 0xFF8A7A5A;
            } else {
                LineageView.HouseLine hl = view.houses().get(i);
                id = hl.id();
                name = hl.name();
                sub = Component.translatable("crusadecraft.lineage.list.house", hl.living(), hl.total());
                color = 0xFF000000 | hl.color();
            }
            boolean chosen = id.equals(shown());
            boolean hover = Ui.inside(mouseX, mouseY, x + 2, ry, LIST_W - 4, rowH - 1);
            if (chosen || hover) g.fill(x + 2, ry, x + LIST_W - 2, ry + rowH - 1, chosen ? 0xFF3A2E22 : Ui.PANEL_HIGHLIGHT);
            g.fill(x + 4, ry + 3, x + 7, ry + rowH - 4, color);
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(name, LIST_W - 16)), x + 10, ry + 2, chosen ? Ui.TEXT_TITLE : Ui.TEXT);
            Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), (int) ((LIST_W - 16) / 0.75f))), x + 10, ry + 12, 0.75f,
                    Ui.TEXT_MUTED, false);
        }
        g.disableScissor();
    }

    private void renderSelection(GuiGraphics g, int mouseX, int mouseY) {
        LineageView.Selection sel = view.selection();
        int x = rightX, y = top + 30;
        if (sel == null) {
            Ui.wrapped(g, font, Component.translatable(communitiesTab ? "crusadecraft.lineage.pick.community" : "crusadecraft.lineage.pick.house"),
                    x, y + 4, rightW, Ui.TEXT_MUTED);
            if (!view.canEdit()) Ui.wrapped(g, font, Component.translatable("crusadecraft.lineage.readonly"), x, y + 30, rightW, Ui.TEXT_MUTED);
            return;
        }
        Ui.scaled(g, font, Component.literal(sel.title()), x, y, 1.15f, Ui.TEXT_TITLE, false);
        long living = sel.rows().stream().filter(LineageView.Row::alive).count();
        Component sub = sel.house()
                ? Component.translatable("crusadecraft.lineage.sub.house", sel.realm(), living, sel.rows().size())
                : Component.translatable("crusadecraft.lineage.sub.community", Component.translatable(sel.kind().key()), sel.realm(), sel.rows().size());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), rightW)), x, y + 12, Ui.TEXT_MUTED);

        int y0 = rowsTop, y1 = rowsBottom;
        if (y1 - y0 < ROW_H) return;
        Ui.inset(g, x, y0, rightW, y1 - y0);
        List<Line> lines = lines(sel);
        int visible = (y1 - y0 - 4) / ROW_H;
        rowScroll = Math.max(0, Math.min(rowScroll, Math.max(0, lines.size() - visible)));
        g.enableScissor(x + 1, y0 + 1, x + rightW - 1, y1 - 1);
        for (int i = rowScroll; i < lines.size() && i - rowScroll < visible; i++) {
            Line line = lines.get(i);
            int ry = y0 + 2 + (i - rowScroll) * ROW_H;
            if (line.row == null) {
                Ui.text(g, font, line.header, x + 4, ry + 1, Ui.GOLD);
                continue;
            }
            LineageView.Row r = line.row;
            boolean chosen = r.id().equals(selectedPerson);
            boolean hover = Ui.inside(mouseX, mouseY, x + 2, ry, rightW - 4, ROW_H);
            if (chosen || hover) g.fill(x + 2, ry, x + rightW - 2, ry + ROW_H - 1, chosen ? 0xFF3A2E22 : Ui.PANEL_HIGHLIGHT);
            int indent = r.member() ? 8 : 18;
            String sign = r.female() ? "♀" : "♂";
            int textColor = !r.alive() ? 0xFF7C7062 : chosen ? Ui.TEXT_TITLE : Ui.TEXT;
            // Le nom se raccourcit, jamais l'âge.
            String suffix = ", " + r.age();
            String role = r.role() == Role.AUCUN ? "" : Component.translatable(r.role().key()).getString();
            String spouse = r.spouse().isEmpty() ? "" : "⚭ " + r.spouse();
            int roleW = role.isEmpty() ? 0 : font.width(role) + 8;
            int spouseW = spouse.isEmpty() ? 0 : Math.min(font.width(spouse) + 8, rightW / 3);
            Ui.text(g, font, Component.literal(sign), x + indent - 6, ry + 1, r.female() ? 0xFFD98FB0 : 0xFF8FB4D9);
            int room = rightW - indent - roleW - spouseW - 14 - font.width(suffix);
            String main = font.plainSubstrByWidth((r.alive() ? "" : "† ") + r.name(), Math.max(20, room)) + suffix;
            Ui.text(g, font, Component.literal(main), x + indent + 2, ry + 1, textColor);
            int rx = x + rightW - 6;
            if (r.inWorld()) {
                g.fill(rx - 3, ry + 3, rx, ry + 6, 0xFF7FCF5A);
                rx -= 7;
            }
            if (!spouse.isEmpty()) {
                Component sp = Component.literal(font.plainSubstrByWidth(spouse, spouseW - 8));
                rx -= font.width(sp);
                Ui.text(g, font, sp, rx, ry + 1, Ui.TEXT_MUTED);
                rx -= 8;
            }
            if (!role.isEmpty()) {
                rx -= font.width(role);
                Ui.text(g, font, Component.literal(role), rx, ry + 1, r.role().noble() ? Ui.GOLD : Ui.TEXT_MUTED);
            }
        }
        g.disableScissor();
        if (lines.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.lineage.nobody"), x + 6, y0 + 6, rightW - 12, Ui.TEXT_MUTED);
    }

    /** Une ligne de la liste : un en-tête (génération, foyer) ou une personne. */
    private record Line(Component header, LineageView.Row row) {}

    private List<Line> lines(LineageView.Selection sel) {
        List<Line> out = new ArrayList<>();
        int group = Integer.MIN_VALUE;
        for (LineageView.Row r : sel.rows()) {
            if (r.group() != group) {
                group = r.group();
                Component header = sel.house()
                        ? Component.translatable("crusadecraft.lineage.generation", group < ROMAN.length ? ROMAN[group] : String.valueOf(group + 1))
                        : Component.translatable("crusadecraft.lineage.household", group + 1);
                out.add(new Line(header, null));
            }
            out.add(new Line(null, r));
        }
        return out;
    }

    // ------------------------------------------------------------------ souris

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int x = left + 8, y0 = listTop(), y1 = listBottom();
        if (Ui.inside(mouseX, mouseY, x, y0, LIST_W, y1 - y0)) {
            int index = listScroll + (int) ((mouseY - y0 - 2) / 21);
            int count = communitiesTab ? view.communities().size() : view.houses().size();
            if (index >= 0 && index < count) {
                UUID id = communitiesTab ? view.communities().get(index).id() : view.houses().get(index).id();
                selectedPerson = null;
                rowScroll = 0;
                CrusadeNetwork.toServer(new LineagePackets.Request(id, null));
            }
            return true;
        }
        LineageView.Selection sel = view.selection();
        if (sel != null && Ui.inside(mouseX, mouseY, rightX, rowsTop, rightW, rowsBottom - rowsTop)) {
            List<Line> lines = lines(sel);
            int index = rowScroll + (int) ((mouseY - rowsTop - 2) / ROW_H);
            if (index >= 0 && index < lines.size() && lines.get(index).row() != null) {
                UUID id = lines.get(index).row().id();
                selectedPerson = id.equals(selectedPerson) ? null : id;
                armed = null;
                rebuildWidgets();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside(mouseX, mouseY, left + 8, listTop(), LIST_W, listBottom() - listTop())) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            return true;
        }
        if (Ui.inside(mouseX, mouseY, rightX, rowsTop, rightW, rowsBottom - rowsTop)) {
            rowScroll = Math.max(0, rowScroll - (int) Math.signum(delta) * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
