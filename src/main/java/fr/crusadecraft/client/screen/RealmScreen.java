package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.realm.RealmView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Le menu royal, façon CK3 : un en-tête au blason du royaume, huit onglets, un contenu.
 *
 * <p>Chaque phase remplit un onglet. Ceux qui n'ont pas encore de contenu affichent un état vide.
 */
public class RealmScreen extends Screen {

    public enum Tab {
        ROYAUME(Items.GOLDEN_HELMET), CONSEIL(Items.LECTERN), VASSAUX(Items.SHIELD), DIPLOMATIE(Items.PAPER),
        TRESOR(Items.GOLD_INGOT), GUERRE(Items.IRON_SWORD), LOIS(Items.WRITABLE_BOOK), CHRONIQUES(Items.BOOK);

        final ItemStack icon;

        Tab(net.minecraft.world.item.Item icon) {
            this.icon = new ItemStack(icon);
        }

        Component label() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT));
        }

        Component emptyTitle() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT) + ".empty.title");
        }

        Component emptyBody() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT) + ".empty.body");
        }
    }

    private static Tab lastTab = Tab.ROYAUME;

    private RealmView view;
    private Tab tab = lastTab;
    private int left, top, w, h;

    public RealmScreen(RealmView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    /** Ouvre le menu directement sur un onglet. */
    public static RealmScreen withTab(RealmView view, Tab tab) {
        RealmScreen screen = new RealmScreen(view);
        screen.tab = tab;
        return screen;
    }

    public boolean isFor(RealmView other) {
        return view.id().equals(other.id());
    }

    public void refresh(RealmView newView) {
        this.view = newView;
    }

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(262, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        renderHeader(g);
        renderTabs(g, mouseX, mouseY);
        int cy = top + 82;
        int ch = h - 82 - 10;
        Ui.inset(g, left + 8, cy, w - 16, ch);
        if (tab == Tab.ROYAUME) {
            renderRealmTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else {
            renderEmpty(g, left + 8, cy, w - 16, ch);
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderTabTooltips(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g) {
        Ui.shield(g, font, left + 12, top + 10, 34, 42, view.color(), Ui.initial(view.name()));
        Component rankTitle = Component.translatable(view.rank().titleKey());
        Ui.scaled(g, font, Component.literal(view.name()), left + 56, top + 12, 1.6f, Ui.TEXT_TITLE, false);
        Component sub = Component.translatable("crusadecraft.realm.header.sub", rankTitle, view.house());
        Ui.text(g, font, sub, left + 56, top + 29, Ui.TEXT);
        if (!view.motto().isEmpty()) {
            Ui.text(g, font, Component.literal("« " + view.motto() + " »").withStyle(ChatFormatting.ITALIC),
                    left + 56, top + 41, Ui.TEXT_MUTED);
        }
        if (!view.state().isActive()) {
            Component state = Component.translatable(view.state().key());
            int sw = font.width(state);
            g.fill(left + w - sw - 18, top + 10, left + w - 8, top + 24, 0xC0601010);
            Ui.text(g, font, state, left + w - sw - 13, top + 13, 0xFFFFD0C0);
        }
    }

    private int tabWidth() {
        return (w - 16) / Tab.values().length;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            boolean selected = t == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24);
            g.fill(tx, ty, tx + tw - 2, ty + 26, selected ? Ui.PANEL_INNER : hover ? Ui.PANEL_HIGHLIGHT : 0xFF1F1813);
            g.renderOutline(tx, ty, tw - 2, selected ? 27 : 24, selected ? Ui.GOLD : Ui.GOLD_DARK);
            g.renderItem(t.icon, tx + (tw - 2) / 2 - 8, ty + 4);
            if (selected) g.fill(tx + 1, ty + 25, tx + tw - 3, ty + 27, Ui.PANEL_INNER);
        }
    }

    private void renderTabTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            if (Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24)) {
                g.renderTooltip(font, t.label(), mouseX, mouseY);
            }
        }
    }

    private void renderEmpty(GuiGraphics g, int x, int y, int cw, int ch) {
        int cx = x + cw / 2;
        Ui.scaled(g, font, tab.label(), cx, y + 14, 1.3f, Ui.TEXT_TITLE, true);
        Ui.separator(g, x + cw / 4, y + 32, cw / 2, Ui.GOLD_DARK);
        g.renderItem(tab.icon, cx - 8, y + 44);
        Ui.centered(g, font, tab.emptyTitle(), cx, y + 68, Ui.TEXT);
        Ui.wrappedCentered(g, font, tab.emptyBody(), cx, y + 84, cw - 60, Ui.TEXT_MUTED);
    }

    private void renderRealmTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        int col = x + 10;
        int line = y + 10;
        rowWidth = cw / 2 - 16;
        Component holder = Component.translatable(view.rank().holderKey(view.feminineTitle()));
        row(g, "crusadecraft.realm.ruler", Component.literal(view.rulerName()).append(", ").append(holder), col, line);
        row(g, "crusadecraft.realm.house", Component.literal(view.house()), col, line += 12);
        ColonyInfo capital = view.capital();
        row(g, "crusadecraft.realm.capital", Component.literal(capital == null || capital.isMissing() ? "—" : capital.name()), col, line += 12);
        long age = Math.max(0, view.currentDay() - view.foundedDay());
        row(g, "crusadecraft.realm.founded", Component.translatable("crusadecraft.realm.founded.value", view.foundedDay(), age), col, line += 12);
        row(g, "crusadecraft.realm.state", Component.translatable(view.state().key()), col, line += 12);
        row(g, "crusadecraft.realm.population", Component.literal(String.valueOf(view.totalCitizens())), col, line += 12);

        // Ressources du souverain
        int ry = line + 20;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.resources"), col, ry, Ui.TEXT_TITLE);
        ry += 12;
        resource(g, new ItemStack(Items.GOLD_NUGGET), view.gold(), "crusadecraft.resource.gold", col, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.GOLDEN_HELMET), view.prestige(), "crusadecraft.resource.prestige", col + 46, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.CANDLE), view.piety(), "crusadecraft.resource.piety", col + 92, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.WITHER_SKELETON_SKULL), view.dread(), "crusadecraft.resource.dread", col + 138, ry, mouseX, mouseY);

        // Domaine
        int dx = x + cw / 2 + 4;
        int dw = cw / 2 - 14;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.domain"), dx, y + 10, Ui.TEXT_TITLE);
        int dy = y + 24;
        List<ColonyInfo> colonies = view.colonies();
        int rowH = 40;
        int shown = 0;
        for (ColonyInfo c : colonies) {
            int remainingAfter = colonies.size() - shown - 1;
            if (dy + rowH > y + ch) break;
            if (remainingAfter > 0 && dy + rowH + 10 > y + ch) break;
            g.fill(dx, dy, dx + dw, dy + rowH - 4, 0xFF1C1612);
            g.renderOutline(dx, dy, dw, rowH - 4, Ui.GOLD_DARK);
            if (c.isMissing()) {
                Ui.text(g, font, Component.translatable("crusadecraft.realm.colony.missing", c.ref().toString()), dx + 4, dy + 4, Ui.RED);
            } else {
                Component role = Component.translatable(shown == 0 ? "crusadecraft.realm.colony.capital" : "crusadecraft.realm.colony.province");
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(c.name(), dw - font.width(role) - 14)), dx + 4, dy + 4, Ui.TEXT);
                Ui.text(g, font, role, dx + dw - font.width(role) - 4, dy + 4, Ui.TEXT_MUTED);
                Component stats = Component.translatable("crusadecraft.realm.colony.stats", c.townHallLevel(), c.citizens(), c.maxCitizens());
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(stats.getString(), dw - 8)), dx + 4, dy + 15, Ui.TEXT_MUTED);
                Component label = Component.translatable("crusadecraft.realm.colony.happiness.short");
                Ui.text(g, font, label, dx + 4, dy + 25, Ui.TEXT_MUTED);
                int bx = dx + 8 + font.width(label);
                int bw = dw - (bx - dx) - 6;
                Ui.bar(g, bx, dy + 26, bw, 6, c.happiness() / 10.0, Ui.happinessColor(c.happiness()));
                if (Ui.inside(mouseX, mouseY, dx, dy + 23, dw, 12)) {
                    pendingTooltip = List.of(Component.translatable("crusadecraft.realm.colony.happiness",
                            String.format(Locale.ROOT, "%.1f", c.happiness())));
                }
            }
            dy += rowH;
            shown++;
        }
        if (shown < colonies.size()) {
            Ui.wrapped(g, font, (colonies.size() - shown == 1 ? Component.translatable("crusadecraft.realm.colony.more.one")
                    : Component.translatable("crusadecraft.realm.colony.more", colonies.size() - shown)), dx + 2, dy, dw - 4, Ui.TEXT_MUTED);
        }
        if (pendingTooltip != null) {
            g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private List<Component> pendingTooltip;

    private int rowWidth = 200;

    private void row(GuiGraphics g, String labelKey, Component value, int x, int y) {
        Component label = Component.translatable(labelKey);
        Ui.text(g, font, label, x, y, Ui.TEXT_MUTED);
        int room = rowWidth - 70;
        String shown = font.plainSubstrByWidth(value.getString(), room);
        Ui.text(g, font, Component.literal(shown), x + 70, y, Ui.TEXT);
    }

    private void resource(GuiGraphics g, ItemStack icon, long value, String key, int x, int y, int mouseX, int mouseY) {
        g.renderItem(icon, x, y);
        Ui.text(g, font, Component.literal(String.valueOf(value)), x + 18, y + 5, Ui.TEXT);
        if (Ui.inside(mouseX, mouseY, x, y, 42, 16)) {
            pendingTooltip = List.of(Component.translatable(key),
                    Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            if (Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24)) {
                tab = t;
                lastTab = t;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fr.crusadecraft.client.KeyBindings.REALM_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
