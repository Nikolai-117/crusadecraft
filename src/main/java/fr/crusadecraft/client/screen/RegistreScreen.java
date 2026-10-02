package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.realm.RealmView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Le Registre du royaume : un livre ouvert sur deux pages de parchemin. À gauche le royaume,
 * à droite son domaine.
 */
public class RegistreScreen extends Screen {
    private static final int PAGE_W = 150;
    private static final int PAGE_H = 190;

    private final RealmView view;

    public RegistreScreen(RealmView view) {
        super(Component.translatable("item.crusadecraft.registre"));
        this.view = view;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = width / 2 - PAGE_W - 2;
        int top = (height - PAGE_H) / 2;
        // reliure
        g.fill(left - 6, top - 6, left + PAGE_W * 2 + 10, top + PAGE_H + 6, 0xFF5A2A1A);
        g.renderOutline(left - 6, top - 6, PAGE_W * 2 + 16, PAGE_H + 12, Ui.GOLD_DARK);
        Ui.parchment(g, left, top, PAGE_W, PAGE_H);
        Ui.parchment(g, left + PAGE_W + 4, top, PAGE_W, PAGE_H);
        g.fill(left + PAGE_W, top, left + PAGE_W + 4, top + PAGE_H, 0xFF3E1C10);

        // Page gauche : le royaume
        int cx = left + PAGE_W / 2;
        Ui.shield(g, font, cx - 14, top + 12, 28, 34, view.color(), Ui.initial(view.name()));
        Ui.wrappedCentered(g, font, Component.literal(view.name()).withStyle(ChatFormatting.BOLD), cx, top + 54, PAGE_W - 16, Ui.INK);
        Ui.centered(g, font, Component.translatable(view.rank().titleKey()), cx, top + 68, Ui.INK_MUTED);
        if (!view.motto().isEmpty()) {
            Ui.wrappedCentered(g, font, Component.literal("« " + view.motto() + " »").withStyle(ChatFormatting.ITALIC),
                    cx, top + 82, PAGE_W - 20, Ui.INK_MUTED);
        }
        Ui.separator(g, left + 20, top + 106, PAGE_W - 40, 0xFF8A6A3C);
        int y = top + 116;
        Component holder = Component.translatable(view.rank().holderKey(view.feminineTitle()));
        y += line(g, Component.translatable("crusadecraft.registre.ruler", view.rulerName(), holder), left + 10, y);
        y += line(g, Component.translatable("crusadecraft.registre.house", view.house()), left + 10, y);
        y += line(g, Component.translatable("crusadecraft.registre.founded", view.foundedDay()), left + 10, y);
        y += line(g, Component.translatable(view.state().key()), left + 10, y);
        line(g, Component.translatable("crusadecraft.registre.resources", view.gold(), view.prestige(), view.piety(), view.dread()), left + 10, y + 4);

        // Page droite : le domaine
        int rx = left + PAGE_W + 14;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.domain").withStyle(ChatFormatting.BOLD), rx, top + 12, Ui.INK);
        int ry = top + 28;
        for (ColonyInfo c : view.colonies()) {
            if (ry > top + PAGE_H - 30) break;
            if (c.isMissing()) {
                ry += line(g, Component.translatable("crusadecraft.realm.colony.missing", c.ref().toString()), rx, ry);
            } else {
                ry += line(g, Component.literal("• " + c.name()), rx, ry);
                ry += Ui.wrapped(g, font, Component.translatable("crusadecraft.registre.colony", c.townHallLevel(), c.citizens(),
                        String.format(Locale.ROOT, "%.1f", c.happiness())), rx + 6, ry, PAGE_W - 26, Ui.INK_MUTED);
            }
            ry += 4;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.registre.population", view.totalCitizens()),
                rx, top + PAGE_H - 20, Ui.INK_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int line(GuiGraphics g, Component c, int x, int y) {
        return Ui.wrapped(g, font, c, x, y, PAGE_W - 20, Ui.INK);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
