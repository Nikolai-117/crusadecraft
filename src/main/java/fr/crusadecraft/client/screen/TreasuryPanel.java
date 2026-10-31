package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.treasury.TreasuryView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le contenu du Trésor, dessiné à l'identique dans l'onglet du menu royal et au Coffre du Trésor : le
 * solde, les comptes du jour, la prévision de demain et l'historique de la semaine.
 */
final class TreasuryPanel {
    private static final int LINE = 10;
    private static final int MAX_COLONIES = 4;

    private TreasuryPanel() {}

    static String signed(long v) {
        return (v > 0 ? "+" : v < 0 ? "−" : "") + Math.abs(v);
    }

    private static int amountColor(long v) {
        return v > 0 ? Ui.GREEN : v < 0 ? Ui.RED : Ui.TEXT_MUTED;
    }

    /** Dessine tout le panneau ; renvoie l'infobulle à afficher, ou {@code null}. */
    static List<Component> render(GuiGraphics g, Font font, TreasuryView v, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<Component> tooltip = null;
        int colW = w / 2 - 12;
        int lx = x + 8, rx = x + w / 2 + 4;
        // L'historique ne s'affiche que s'il reste la place (petits écrans : menu royal en interface large).
        int historyH = h >= 150 ? 44 : 0;

        // Solde
        g.renderItem(new ItemStack(Items.GOLD_NUGGET), lx, y + 4);
        Component balance = Component.translatable("crusadecraft.treasury.balance", v.gold());
        Ui.scaled(g, font, balance, lx + 20, y + 7, 1.25f, v.gold() < 0 ? Ui.RED : Ui.TEXT_TITLE, false);
        if (v.gold() < 0) {
            Ui.text(g, font, Component.translatable("crusadecraft.treasury.debt", -v.gold(), v.debtLimit()), lx, y + 22, Ui.RED);
        }

        // Aujourd'hui
        int ly = y + 36;
        Ui.text(g, font, Component.translatable("crusadecraft.treasury.today", v.day()), lx, ly, Ui.TEXT_TITLE);
        ly += 12;
        if (v.today().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.treasury.today.none"), lx, ly, Ui.TEXT_MUTED);
            ly += LINE;
        }
        long net = 0;
        for (TreasuryView.Entry e : v.today()) {
            if (ly > y + h - historyH - 24) break;
            line(g, font, Component.translatable(e.category().key()), e.amount(), lx, ly, colW);
            net += e.amount();
            ly += LINE;
        }
        if (!v.today().isEmpty()) {
            Ui.separator(g, lx, ly + 1, colW, Ui.GOLD_DARK);
            line(g, font, Component.translatable("crusadecraft.treasury.net"), net, lx, ly + 4, colW);
        }

        // Demain
        int ry = y + 4;
        Ui.text(g, font, Component.translatable("crusadecraft.treasury.forecast"), rx, ry, Ui.TEXT_TITLE);
        ry += 12;
        if (!v.taxesActive()) {
            Ui.text(g, font, Component.translatable("crusadecraft.treasury.taxes_off"), rx, ry, Ui.TEXT_MUTED);
            ry += LINE;
        }
        int shown = 0;
        long hidden = 0;
        for (TreasuryView.ColonyLine c : v.colonies()) {
            if (shown >= MAX_COLONIES) {
                hidden += c.collected();
                continue;
            }
            line(g, font, Component.literal(c.name()), c.raided() ? Ui.RED : Ui.TEXT, c.collected(), rx, ry, colW);
            if (Ui.inside(mouseX, mouseY, rx, ry - 1, colW, LINE)) tooltip = colonyTooltip(v, c);
            ry += LINE;
            shown++;
        }
        if (shown < v.colonies().size()) {
            line(g, font, Component.translatable("crusadecraft.treasury.other_colonies", v.colonies().size() - shown), hidden, rx, ry, colW);
            ry += LINE;
        }
        if (v.domain() > 0) {
            line(g, font, Component.translatable("crusadecraft.ledger.domaine"), v.domain(), rx, ry, colW);
            ry += LINE;
        }
        line(g, font, Component.translatable("crusadecraft.ledger.salaires"), -v.salaries(), rx, ry, colW);
        if (Ui.inside(mouseX, mouseY, rx, ry - 1, colW, LINE)) {
            tooltip = List.of(Component.translatable("crusadecraft.ledger.salaires"),
                    Component.translatable("crusadecraft.treasury.salaries.tip").withStyle(ChatFormatting.GRAY));
        }
        ry += LINE;
        long upkeep = 0;
        for (TreasuryView.ColonyLine c : v.colonies()) upkeep += c.upkeep();
        line(g, font, Component.translatable("crusadecraft.ledger.entretien"), -upkeep, rx, ry, colW);
        if (Ui.inside(mouseX, mouseY, rx, ry - 1, colW, LINE)) {
            tooltip = List.of(Component.translatable("crusadecraft.ledger.entretien"),
                    Component.translatable("crusadecraft.treasury.upkeep.tip").withStyle(ChatFormatting.GRAY));
        }
        ry += LINE;
        Ui.separator(g, rx, ry + 1, colW, Ui.GOLD_DARK);
        line(g, font, Component.translatable("crusadecraft.treasury.forecast.net"), v.forecastIncome() - v.forecastExpenses(), rx, ry + 4, colW);
        Component fiscal = Component.translatable("crusadecraft.treasury.fiscal", Component.translatable(Law.FISCALITE.levelKey(v.fiscalLevel())));
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(fiscal.getString(), colW)), rx, ry + 16, Ui.TEXT_MUTED);

        // Historique de la semaine
        if (historyH > 0) {
            Component histTip = history(g, font, v, x + 8, y + h - historyH, w - 16, historyH - 4, mouseX, mouseY);
            if (histTip != null) tooltip = List.of(histTip);
        }
        return tooltip;
    }

    private static void line(GuiGraphics g, Font font, Component label, long amount, int x, int y, int w) {
        line(g, font, label, Ui.TEXT, amount, x, y, w);
    }

    private static void line(GuiGraphics g, Font font, Component label, int labelColor, long amount, int x, int y, int w) {
        String a = signed(amount);
        String l = font.plainSubstrByWidth(label.getString(), w - font.width(a) - 6);
        Ui.text(g, font, Component.literal(l), x, y, labelColor);
        Ui.text(g, font, Component.literal(a), x + w - font.width(a), y, amountColor(amount));
    }

    private static List<Component> colonyTooltip(TreasuryView v, TreasuryView.ColonyLine c) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(c.name()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("crusadecraft.treasury.colony.buildings", c.buildings()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("crusadecraft.treasury.colony.happiness", String.format(Locale.ROOT, "%.1f", c.happiness()),
                percent(0.5 + Math.max(0, Math.min(10, c.happiness())) / 10.0)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("crusadecraft.treasury.colony.fiscal", percent(v.fiscalMultiplier())).withStyle(ChatFormatting.GRAY));
        if (v.stewardBonus() > 0) tip.add(Component.translatable("crusadecraft.treasury.colony.steward", bonus(v.stewardBonus())).withStyle(ChatFormatting.GRAY));
        if (v.researchBonus() > 0) tip.add(Component.translatable("crusadecraft.treasury.colony.research", bonus(v.researchBonus())).withStyle(ChatFormatting.GRAY));
        if (!c.capital()) tip.add(Component.translatable("crusadecraft.treasury.colony.province", percent(v.provinceMultiplier())).withStyle(ChatFormatting.GRAY));
        if (c.raided()) tip.add(Component.translatable("crusadecraft.treasury.colony.raided").withStyle(ChatFormatting.RED));
        if (c.upkeep() > 0) tip.add(Component.translatable("crusadecraft.treasury.colony.upkeep", c.upkeep()).withStyle(ChatFormatting.GRAY));
        if (!c.governor().isEmpty()) {
            tip.add(Component.translatable("crusadecraft.treasury.colony.governor", c.governor(), c.crownShare()).withStyle(ChatFormatting.AQUA));
        } else if (c.overDomain()) {
            tip.add(Component.translatable("crusadecraft.treasury.colony.over_domain", 100 - c.crownShare()).withStyle(ChatFormatting.RED));
        }
        return tip;
    }

    private static String percent(double m) {
        return "×" + String.format(Locale.ROOT, "%.2f", m);
    }

    private static String bonus(double b) {
        return "+" + Math.round(b * 100) + " %";
    }

    /** Barres des sept derniers jours : recettes au-dessus de la ligne, dépenses en dessous. */
    private static Component history(GuiGraphics g, Font font, TreasuryView v, int x, int y, int w, int h, int mouseX, int mouseY) {
        Ui.text(g, font, Component.translatable("crusadecraft.treasury.history"), x, y, Ui.TEXT_MUTED);
        List<TreasuryView.DayLine> days = v.history();
        if (days.isEmpty()) return null;
        long max = 1;
        for (TreasuryView.DayLine d : days) max = Math.max(max, Math.max(d.income(), -d.expenses()));
        int chartX = x + 70;
        int chartW = w - 70;
        int slot = chartW / days.size();
        int mid = y + 4 + (h - 4) / 2;
        int half = (h - 6) / 2;
        g.fill(chartX, mid, chartX + chartW, mid + 1, Ui.GOLD_DARK);
        Component tip = null;
        for (int i = 0; i < days.size(); i++) {
            TreasuryView.DayLine d = days.get(i);
            int bx = chartX + i * slot + slot / 4;
            int bw = Math.max(2, slot / 2);
            int up = (int) Math.round(half * (double) d.income() / max);
            int down = (int) Math.round(half * (double) -d.expenses() / max);
            if (up > 0) g.fill(bx, mid - up, bx + bw, mid, Ui.GREEN);
            if (down > 0) g.fill(bx, mid + 1, bx + bw, mid + 1 + down, Ui.RED);
            if (Ui.inside(mouseX, mouseY, chartX + i * slot, y, slot, h)) {
                tip = Component.translatable("crusadecraft.treasury.history.day", d.day(), signed(d.income()), signed(d.expenses()));
            }
        }
        return tip;
    }
}
