package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.intrigue.IntrigueRules;
import fr.crusadecraft.core.intrigue.IntrigueView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.IntriguePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Les intrigues de la cour : ce que le Maître-espion a découvert. À gauche les factions et les complots éventés, à
 * droite les secrets (chantage ou révélation) et les provinces en révolte.
 */
public class IntrigueScreen extends Screen {
    private static final int SECRET_ROW = 22;

    private IntrigueView view;
    private int left, top, w, h;
    private List<Component> pendingTooltip;

    public IntrigueScreen(IntrigueView view) {
        super(Component.translatable("crusadecraft.intrigue.title"));
        this.view = view;
    }

    public void refresh(IntrigueView v) {
        this.view = v;
        rebuildWidgets();
    }

    private void send(IntriguePackets.IntrigueAction a) {
        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(a);
    }

    private int columnX() {
        return left + w / 2 + 4;
    }

    private int columnW() {
        return w / 2 - 14;
    }

    @Override
    protected void init() {
        w = Math.min(460, width - 10);
        h = Math.min(256, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 88, top + h - 22, 80, 16).build(KitButton::new));
        if (view.pendingEvents() > 0) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.realm.events", view.pendingEvents()),
                    b -> {
                        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(new IntriguePackets.RequestEvent(null));
                    }).bounds(left + w - 196, top + h - 22, 104, 16).build(KitButton::new));
        }
        int x = columnX(), cw = columnW();
        int y = top + 58;
        int bw = 54;
        for (IntrigueView.SecretLine s : view.secrets()) {
            if (y + SECRET_ROW > top + h - 60) break;
            Button blackmail = Button.builder(Component.translatable("crusadecraft.intrigue.blackmail"),
                    b -> send(new IntriguePackets.IntrigueAction(IntriguePackets.IntrigueAction.Kind.BLACKMAIL, s.id())))
                    .bounds(x + cw - 2 * bw - 3, y + 2, bw, 14).build(KitButton::new);
            blackmail.setTooltip(Tooltip.create(Component.translatable("crusadecraft.intrigue.blackmail.tip")));
            addRenderableWidget(blackmail);
            Button expose = Button.builder(Component.translatable("crusadecraft.intrigue.expose"),
                    b -> send(new IntriguePackets.IntrigueAction(IntriguePackets.IntrigueAction.Kind.EXPOSE, s.id())))
                    .bounds(x + cw - bw, y + 2, bw, 14).build(KitButton::new);
            expose.setTooltip(Tooltip.create(Component.translatable("crusadecraft.intrigue.expose.tip")));
            addRenderableWidget(expose);
            y += SECRET_ROW;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component spy = view.spymaster().isEmpty() ? Component.translatable("crusadecraft.intrigue.no_spy")
                : Component.translatable(view.hunting() ? "crusadecraft.intrigue.spy.hunting" : "crusadecraft.intrigue.spy", view.spymaster());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(spy.getString(), w - 110)), left + 10, top + 26,
                view.spymaster().isEmpty() ? 0xFFE3B34F : Ui.TEXT_MUTED);
        Component dread = Component.translatable("crusadecraft.intrigue.dread", view.dread());
        Ui.text(g, font, dread, left + w - 10 - font.width(dread), top + 10, Ui.TEXT_MUTED);
        if (Ui.inside(mouseX, mouseY, left + w - 10 - font.width(dread), top + 9, font.width(dread), 10)) {
            pendingTooltip = List.of(Component.translatable("crusadecraft.intrigue.dread.tip").withStyle(ChatFormatting.GRAY));
        }
        if (view.order()) {
            Component order = Component.translatable("crusadecraft.intrigue.order");
            Ui.text(g, font, order, left + w - 10 - font.width(order), top + 26, Ui.GREEN);
        }
        int lw = w / 2 - 14;
        Ui.inset(g, left + 8, top + 40, lw + 4, h - 70);
        Ui.inset(g, columnX() - 2, top + 40, columnW() + 4, h - 70);
        renderFactions(g, left + 12, top + 44, lw - 4, mouseX, mouseY);
        renderSecrets(g, columnX() + 2, top + 44, columnW() - 4);
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private void renderFactions(GuiGraphics g, int x, int y, int cw, int mouseX, int mouseY) {
        int bottom = top + h - 34;
        Ui.text(g, font, Component.translatable("crusadecraft.intrigue.factions"), x, y, Ui.TEXT_TITLE);
        y += 13;
        if (view.factions().isEmpty()) {
            Component none = view.hidden() > 0 ? Component.translatable("crusadecraft.intrigue.rumours", view.hidden())
                    : Component.translatable(view.spymaster().isEmpty() ? "crusadecraft.intrigue.factions.unknown" : "crusadecraft.intrigue.factions.none");
            y += Ui.wrapped(g, font, none, x, y, cw, Ui.TEXT_MUTED) + 4;
        }
        for (IntrigueView.FactionLine f : view.factions()) {
            if (y + 30 > bottom) break;
            Component name = Component.translatable(f.kind().key());
            Ui.text(g, font, name, x, y, Ui.TEXT);
            String power = f.power() + " %";
            Ui.text(g, font, Component.literal(power), x + cw - font.width(power), y, f.power() >= IntrigueRules.ULTIMATUM_POWER ? Ui.RED : 0xFFE3B34F);
            // La jauge de puissance, avec le seuil de l'ultimatum.
            g.fill(x, y + 11, x + cw, y + 15, 0xFF1B2026);
            g.fill(x, y + 11, x + cw * Math.min(100, f.power()) / 100, y + 15, f.power() >= IntrigueRules.ULTIMATUM_POWER ? Ui.RED : 0xFFE3B34F);
            int mark = x + cw * IntrigueRules.ULTIMATUM_POWER / 100;
            g.fill(mark, y + 10, mark + 1, y + 16, Ui.GOLD);
            Component who = Component.translatable(f.place().isEmpty() ? "crusadecraft.intrigue.members" : "crusadecraft.intrigue.members.place",
                    String.join(", ", f.members()), f.place());
            String line = font.plainSubstrByWidth(who.getString(), Math.round(cw / 0.8f));
            Ui.scaled(g, font, Component.literal(line), x, y + 18, 0.8f, Ui.TEXT_MUTED, false);
            if (Ui.inside(mouseX, mouseY, x, y, cw, 26)) {
                List<Component> tip = new ArrayList<>();
                tip.add(name.copy().withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable(f.kind().key() + ".desc").withStyle(ChatFormatting.GRAY));
                tip.add(who.copy().withStyle(ChatFormatting.GRAY));
                tip.add(Component.translatable("crusadecraft.intrigue.power.tip", IntrigueRules.ULTIMATUM_POWER).withStyle(ChatFormatting.DARK_GRAY));
                pendingTooltip = tip;
            }
            y += 30;
        }
        y += 4;
        Ui.text(g, font, Component.translatable("crusadecraft.intrigue.plots"), x, y, Ui.TEXT_TITLE);
        y += 13;
        if (view.plots().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.intrigue.plots.none"), x, y, cw, Ui.TEXT_MUTED);
        }
        for (IntrigueView.PlotLine p : view.plots()) {
            if (y + 12 > bottom) break;
            Component line = Component.translatable("crusadecraft.intrigue.plot", p.plotter(), Component.translatable(p.kind().key()));
            float k = Math.max(0.6f, Math.min(1f, (cw - 34) / (float) Math.max(1, font.width(line))));
            Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), Math.round((cw - 34) / k))), x, y + Math.round((1 - k) * 4), k,
                    Ui.TEXT, false);
            String pct = p.progress() + " %";
            Ui.text(g, font, Component.literal(pct), x + cw - font.width(pct), y, Ui.RED);
            y += 12;
        }
    }

    private void renderSecrets(GuiGraphics g, int x, int y, int cw) {
        int bottom = top + h - 34;
        Ui.text(g, font, Component.translatable("crusadecraft.intrigue.secrets"), x, y, Ui.TEXT_TITLE);
        y += 14;
        if (view.secrets().isEmpty()) {
            y += Ui.wrapped(g, font, Component.translatable(view.spymaster().isEmpty() ? "crusadecraft.intrigue.secrets.unknown"
                    : "crusadecraft.intrigue.secrets.none"), x, y, cw, Ui.TEXT_MUTED) + 4;
        }
        int shown = 0;
        for (IntrigueView.SecretLine s : view.secrets()) {
            if (y + SECRET_ROW > top + h - 60) break;
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(s.holder(), cw - 116)), x, y + 1, Ui.TEXT);
            Ui.scaled(g, font, Component.translatable(s.kind().key()), x, y + 11, 0.75f, 0xFFE3B34F, false);
            y += SECRET_ROW;
            shown++;
        }
        if (shown < view.secrets().size()) {
            Ui.text(g, font, Component.translatable("crusadecraft.intrigue.more", view.secrets().size() - shown), x, y, Ui.TEXT_MUTED);
            y += 12;
        }
        y += 6;
        if (y + 12 > bottom) return;
        Ui.text(g, font, Component.translatable("crusadecraft.intrigue.revolts"), x, y, Ui.TEXT_TITLE);
        y += 13;
        Component revolts = view.revolts().isEmpty() ? Component.translatable("crusadecraft.intrigue.revolts.none")
                : Component.literal(String.join(", ", view.revolts()));
        Ui.wrapped(g, font, revolts, x, y, cw, view.revolts().isEmpty() ? Ui.TEXT_MUTED : Ui.RED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
