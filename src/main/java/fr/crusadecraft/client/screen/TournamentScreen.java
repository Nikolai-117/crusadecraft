package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.court.TournamentView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtLifePackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** La Lice de tournoi : qui joutera, ce qu'il en coûte, et le tableau du dernier tournoi. */
public class TournamentScreen extends Screen {
    private TournamentView view;
    private int left, top, w, h;

    public TournamentScreen(TournamentView view) {
        super(Component.translatable("crusadecraft.tournament.title"));
        this.view = view;
    }

    public void refresh(TournamentView v) {
        this.view = v;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 10);
        h = Math.min(250, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 88, top + h - 22, 80, 16).build());
        Button host = Button.builder(Component.translatable("crusadecraft.tournament.host", view.cost()), b -> {
            if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(new CourtLifePackets.TournamentAction(true));
        }).bounds(left + 8, top + h - 22, 180, 16).build();
        host.active = view.problem().isEmpty();
        host.setTooltip(Tooltip.create(view.problem().isEmpty() ? Component.translatable("crusadecraft.tournament.host.tip")
                : view.problem().equals("crusadecraft.tournament.cooldown") ? Component.translatable(view.problem(), view.cooldownLeft())
                : Component.translatable(view.problem())));
        addRenderableWidget(host);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component res = Component.translatable("crusadecraft.tournament.money", view.cost(), view.gold());
        Ui.text(g, font, res, left + w - 10 - font.width(res), top + 10, Ui.TEXT_MUTED);
        int colW = (w - 28) / 2;
        int lx = left + 8, rx = left + 20 + colW, y = top + 28, ch = h - 56;
        Ui.inset(g, lx, y, colW + 4, ch);
        Ui.inset(g, rx - 2, y, colW + 4, ch);

        Ui.text(g, font, Component.translatable("crusadecraft.tournament.entrants", view.entrants().size()), lx + 5, y + 5, Ui.TEXT_TITLE);
        int ey = y + 18;
        if (view.entrants().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.tournament.no_knight"), lx + 5, ey, colW - 6, Ui.TEXT_MUTED);
        for (int i = 0; i < view.entrants().size() && ey + 10 < y + ch; i++) {
            String name = font.plainSubstrByWidth(view.entrants().get(i), colW - 14);
            Ui.text(g, font, Component.literal((i < view.own() ? "⚔ " : "  ") + name), lx + 5, ey, i < view.own() ? Ui.TEXT : Ui.TEXT_MUTED);
            ey += 11;
        }
        if (!view.problem().isEmpty()) {
            Component p = view.problem().equals("crusadecraft.tournament.cooldown") ? Component.translatable(view.problem(), view.cooldownLeft())
                    : Component.translatable(view.problem());
            List<FormattedCharSequence> lines = font.split(p, colW - 6);
            int py = y + ch - 4 - lines.size() * 10;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, lx + 5, py, 0xFFE0A070, false);
                py += 10;
            }
        }

        Ui.text(g, font, Component.translatable("crusadecraft.tournament.last"), rx + 3, y + 5, Ui.TEXT_TITLE);
        int ly = y + 18;
        if (view.last().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.tournament.none_yet"), rx + 3, ly, colW - 6, Ui.TEXT_MUTED);
        for (int i = 0; i < view.last().size(); i++) {
            boolean champion = i == view.last().size() - 1;
            List<FormattedCharSequence> lines = font.split(view.last().get(i), Math.round((colW - 6) / 0.8f));
            for (FormattedCharSequence l : lines) {
                if (ly + 9 > y + ch) break;
                g.pose().pushPose();
                g.pose().translate(rx + 3, ly, 0);
                g.pose().scale(0.8f, 0.8f, 1);
                g.drawString(font, l, 0, 0, champion ? Ui.TEXT_TITLE : Ui.TEXT, false);
                g.pose().popPose();
                ly += 9;
            }
            ly += 2;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
