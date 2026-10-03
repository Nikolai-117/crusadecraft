package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.diplomacy.ChanceryView;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** La Chancery Table : titres, contrats des vassaux, revendications. */
public class ChanceryScreen extends Screen {
    private static final int ROW = 18;
    private ChanceryView view;
    private int left, top, w, h;

    public ChanceryScreen(ChanceryView view) {
        super(Component.translatable("block.crusadecraft.chancery"));
        this.view = view;
    }

    public void refresh(ChanceryView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    private void send(DiplomacyPackets.ChanceryAction.Kind kind, UUID target, int province, int tax, int levy) {
        CrusadeNetwork.toServer(new DiplomacyPackets.ChanceryAction(kind, target, province, tax, levy));
    }

    static Component realmName(String place, TitleRank rank) {
        return AiNames.realm(rank, place);
    }

    @Override
    protected void init() {
        w = Math.min(460, width - 10);
        h = Math.min(258, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int colW = w / 2 - 14;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 78, top + h - 24, 70, 16).build());
        if (view.nextRank() != null) {
            Button create = Button.builder(Component.translatable("crusadecraft.chancery.create", Component.translatable(view.nextRank().titleKey())),
                    b -> send(DiplomacyPackets.ChanceryAction.Kind.CREATE_TITLE, null, -1, 0, 0)).bounds(left + 10, top + 96, colW, 16).build();
            create.active = view.canRankUp();
            create.setTooltip(Tooltip.create(Component.translatable(view.sealOk() ? "crusadecraft.chancery.create.tip" : "crusadecraft.chancery.seal_needed")));
            addRenderableWidget(create);
        }
        // Vassaux : impôt et levées, libération.
        int vy = top + 132;
        for (ChanceryView.Vassal v : view.vassals()) {
            if (vy > top + h - 44) break;
            int x = left + 10 + colW - 120;
            addRenderableWidget(small("−", x, vy, () -> send(DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, v.tax() - 5, v.levy())));
            addRenderableWidget(small("+", x + 40, vy, () -> send(DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, v.tax() + 5, v.levy())));
            addRenderableWidget(small("−", x + 62, vy, () -> send(DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, v.tax(), v.levy() - 5)));
            addRenderableWidget(small("+", x + 102, vy, () -> send(DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, v.tax(), v.levy() + 5)));
            vy += ROW;
        }
        // Revendications à fabriquer
        int rx = left + w / 2 + 4;
        int cy = top + 112 + Math.max(1, view.claims().size()) * 11 + 14;
        for (ChanceryView.Claim c : view.candidates()) {
            if (cy > top + h - 44) break;
            Button b = Button.builder(Component.translatable("crusadecraft.chancery.claim"),
                    btn -> send(DiplomacyPackets.ChanceryAction.Kind.CLAIM, null, c.province(), 0, 0)).bounds(rx + colW - 70, cy - 3, 70, 16).build();
            b.active = view.hasChancellor() && view.claimTarget() != c.province();
            b.setTooltip(Tooltip.create(Component.translatable(view.hasChancellor() ? "crusadecraft.chancery.claim.tip" : "crusadecraft.diplo.no_chancellor")));
            addRenderableWidget(b);
            cy += 14;
        }
    }

    private Button small(String label, int x, int y, Runnable action) {
        Button b = Button.builder(Component.literal(label), btn -> action.run()).bounds(x, y, 18, 16).build();
        b.active = view.sealOk();
        if (!view.sealOk()) b.setTooltip(Tooltip.create(Component.literal(view.sealProblem())));
        return b;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<Component> hover = null;
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        int colW = w / 2 - 14;
        Ui.scaled(g, font, Component.translatable("crusadecraft.chancery.title", view.realmName()), left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component seal = view.sealOk() ? Component.translatable("crusadecraft.chancery.seal_ok") : Component.literal(view.sealProblem());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(seal.getString(), w - 20)), left + 10, top + 24, view.sealOk() ? Ui.GREEN : 0xFFE0A070);

        // Titre
        int x = left + 10, y = top + 40;
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.rank", Component.translatable(view.rank().titleKey())), x, y, Ui.TEXT_TITLE);
        y += 12;
        if (view.nextRank() == null) {
            Ui.text(g, font, Component.translatable("crusadecraft.title.highest"), x, y, Ui.TEXT_MUTED);
        } else {
            Ui.text(g, font, Component.translatable("crusadecraft.chancery.next", Component.translatable(view.nextRank().titleKey())), x, y, Ui.TEXT);
            y += 11;
            req(g, "crusadecraft.chancery.req.provinces", view.provinces(), view.needProvinces(), x, y);
            req(g, "crusadecraft.chancery.req.gold", view.gold(), view.needGold(), x, y + 10);
            req(g, "crusadecraft.chancery.req.prestige", view.prestige(), view.needPrestige(), x, y + 20);
        }

        // Vassaux
        int vy = top + 118;
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.vassals", view.vassals().size()), x, vy, Ui.TEXT_TITLE);
        vy += 14;
        if (view.vassals().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.chancery.no_vassals"), x, vy, colW, Ui.TEXT_MUTED);
        }
        for (ChanceryView.Vassal v : view.vassals()) {
            if (vy > top + h - 44) break;
            Component name = v.ai() ? realmName(v.label(), v.rank()) : Component.literal(v.label());
            Component shortName = Component.literal(v.label());
            g.fill(x, vy + 3, x + 6, vy + 9, 0xFF000000 | v.color());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(shortName.getString(), colW - 134)), x + 9, vy + 4, Ui.TEXT);
            int cx = x + colW - 120;
            Ui.text(g, font, Component.literal(v.tax() + "%"), cx + 21, vy + 4, Ui.TEXT_MUTED);
            Ui.text(g, font, Component.literal(v.levy() + "%"), cx + 83, vy + 4, Ui.TEXT_MUTED);
            if (Ui.inside(mouseX, mouseY, x, vy, colW - 124, ROW)) {
                g.renderComponentTooltip(font, java.util.List.of(name,
                        Component.translatable("crusadecraft.chancery.vassal.provinces", v.provinces()),
                        Component.translatable("crusadecraft.diplo.contract", v.tax(), v.levy()),
                        Component.translatable("crusadecraft.chancery.vassal.share", v.dailyShare()),
                        Component.translatable("crusadecraft.diplo.opinion", DiplomacyScreen.signed(v.opinion()))), mouseX, mouseY);
            }
            vy += ROW;
        }
        if (!view.vassals().isEmpty()) {
            Ui.scaled(g, font, Component.translatable("crusadecraft.chancery.vassal.columns"), x + colW - 120, top + 122, 0.7f, Ui.TEXT_MUTED, false);
        }

        // Revendications
        int rx = left + w / 2 + 4, ry = top + 40;
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.claims"), rx, ry, Ui.TEXT_TITLE);
        ry += 12;
        if (view.claimTarget() >= 0) {
            String target = view.candidates().stream().filter(c -> c.province() == view.claimTarget()).map(ChanceryView.Claim::name)
                    .findFirst().orElse("?");
            String fab = font.plainSubstrByWidth(Component.translatable("crusadecraft.chancery.fabricating", target).getString(), colW);
            Ui.text(g, font, Component.literal(fab), rx, ry, Ui.TEXT);
            Ui.bar(g, rx, ry + 11, colW, 6, view.claimProgress() / 100.0, Ui.GOLD);
            ry += 24;
        } else {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.chancery.fabricating.none"), rx, ry, colW, Ui.TEXT_MUTED);
            ry += 24;
        }
        int ly = top + 112;
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.claimed", view.claims().size()), rx, ly - 12 + 2, Ui.TEXT_MUTED);
        if (view.claims().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.chancery.claimed.none"), rx, ly, 0xFF6E6250);
        }
        for (ChanceryView.Claim c : view.claims()) {
            Component line = Component.literal("• " + c.name() + " (").append(realmName(c.realmPlace(), c.realmRank())).append(")");
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), colW)), rx, ly, Ui.TEXT);
            ly += 11;
        }
        int cy = top + 112 + Math.max(1, view.claims().size()) * 11 + 2;
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.candidates"), rx, cy, Ui.TEXT_MUTED);
        cy += 12;
        for (ChanceryView.Claim c : view.candidates()) {
            if (cy > top + h - 44) break;
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(c.name(), colW - 76)), rx, cy, Ui.TEXT);
            if (Ui.inside(mouseX, mouseY, rx, cy - 2, colW - 76, 12)) {
                hover = List.of(Component.literal(c.name()), realmName(c.realmPlace(), c.realmRank()),
                        Component.translatable("crusadecraft.chancery.distance", c.distance()));
            }
            cy += 14;
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (hover != null) g.renderComponentTooltip(font, hover, mouseX, mouseY);
    }

    private void req(GuiGraphics g, String key, long have, long need, int x, int y) {
        boolean ok = have >= need;
        Ui.text(g, font, Component.translatable(key, have, need), x + 6, y, ok ? Ui.GREEN : Ui.RED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Pour les boutons de contrat : la valeur bornée. */
    static int rate(int v) {
        return Relation.clampRate(v);
    }
}
