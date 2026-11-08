package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.core.council.MayorView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtPackets;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Le Bureau du Maire : le maire, le rapport de la ville, les candidats, les requêtes du maire. */
public class MayorScreen extends Screen {
    private MayorView view;
    private int left, top, w, h;
    private final List<Object[]> requestHits = new ArrayList<>();
    private List<Component> pendingTooltip;

    public MayorScreen(MayorView view) {
        super(Component.translatable("block.crusadecraft.mayor_desk"));
        this.view = view;
    }

    public boolean isFor(MayorView other) {
        return view.colony().equals(other.colony());
    }

    public void refresh(MayorView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 12);
        h = Math.min(236, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (view.mayor() != null) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.dialogue.sheet"), b ->
                    CrusadeNetwork.toServer(new RequestCharacterPacket(view.mayor().ref(), RequestCharacterPacket.Mode.SHEET)))
                    .bounds(left + 12, top + 74, 100, 16).build(KitButton::new));
        }
        if (view.canAct()) {
            int cx = left + w / 2 + 6;
            int y = top + 50;
            for (MayorView.Person p : view.candidates()) {
                Button appoint = Button.builder(Component.translatable("crusadecraft.mayor.appoint"), b ->
                        CrusadeNetwork.toServer(new CourtPackets.MayorAction(view.colony(), p.ref(), false, null)))
                        .bounds(left + w - 66, y, 56, 14).build(KitButton::new);
                appoint.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mayor.appoint.tip", p.name())));
                addRenderableWidget(appoint);
                y += 17;
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + w - 90, top + h - 26, 80, 18).build(KitButton::new));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        requestHits.clear();
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, Component.translatable("crusadecraft.mayor.title", view.colonyName()), left + 12, top + 9, 1.3f, Ui.TEXT_TITLE, false);
        Component office = view.deskPlaced()
                ? Component.translatable("crusadecraft.mayor.office", view.officeHour())
                : Component.translatable("crusadecraft.mayor.no_desk");
        Ui.text(g, font, office, left + 12, top + 25, Ui.TEXT_MUTED);

        // Le maire
        int x = left + 12, y = top + 40;
        Ui.text(g, font, Component.translatable("crusadecraft.mayor.current"), x, y, Ui.TEXT_TITLE);
        if (view.mayor() == null) {
            Ui.wrapped(g, font, Component.translatable(view.canAct() ? "crusadecraft.mayor.none.act" : "crusadecraft.mayor.none"),
                    x, y + 12, w / 2 - 24, Ui.TEXT_MUTED);
        } else {
            MayorView.Person m = view.mayor();
            Ui.text(g, font, Component.literal(m.name()), x, y + 12, Ui.TEXT);
            Ui.text(g, font, Component.translatable("crusadecraft.mayor.person", m.stewardship(), CharacterScreen.signed(m.opinion())),
                    x, y + 23, Ui.TEXT_MUTED);
        }

        // Rapport de la ville
        renderReport(g, x, top + 96, mouseX, mouseY);

        // Candidats
        if (view.canAct()) {
            int cx = left + w / 2 + 6;
            Ui.text(g, font, Component.translatable("crusadecraft.mayor.candidates"), cx, top + 40, Ui.TEXT_TITLE);
            int cy = top + 50;
            if (view.mayor() != null) {
                Ui.wrapped(g, font, Component.translatable("crusadecraft.mayor.filled"), cx, cy + 2, w / 2 - 24, Ui.TEXT_MUTED);
            } else if (view.candidates().isEmpty()) {
                Ui.text(g, font, Component.translatable("crusadecraft.mayor.no_candidates"), cx, cy + 2, Ui.TEXT_MUTED);
            }
            for (MayorView.Person p : view.candidates()) {
                String line = font.plainSubstrByWidth(p.name(), w / 2 - 120);
                Ui.text(g, font, Component.literal(line), cx, cy + 3, Ui.TEXT);
                g.renderItem(new ItemStack(Items.GOLD_INGOT), cx + w / 2 - 118, cy - 1);
                Ui.text(g, font, Component.literal(String.valueOf(p.stewardship())), cx + w / 2 - 100, cy + 3, Ui.TEXT);
                cy += 17;
            }
        }

        // Requêtes du maire
        renderRequests(g, left + w / 2 + 6, top + h - 72, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private void renderReport(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ColonyReport r = view.report();
        Ui.text(g, font, Component.translatable("crusadecraft.mayor.report"), x, y, Ui.TEXT_TITLE);
        y += 12;
        line(g, x, y, Items.PLAYER_HEAD, Component.translatable("crusadecraft.mayor.citizens", r.citizens(), r.maxCitizens()), false);
        line(g, x, y + 12, Items.RED_BED, Component.translatable("crusadecraft.mayor.homeless", r.homeless()), r.homeless() > 0);
        line(g, x, y + 24, Items.WOODEN_PICKAXE, Component.translatable("crusadecraft.mayor.jobless", r.jobless()), r.jobless() > 0);
        line(g, x, y + 36, Items.SPIDER_EYE, Component.translatable("crusadecraft.mayor.sick", r.sick()), r.sick() > 0);
        line(g, x, y + 48, Items.BREAD, Component.translatable("crusadecraft.mayor.hungry", r.hungry()), r.hungry() > 0);
        line(g, x, y + 60, Items.IRON_SWORD, Component.translatable("crusadecraft.mayor.guards", r.guards()), false);
        int by = y + 74;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.colony.happiness.short"), x, by, Ui.TEXT_MUTED);
        Ui.bar(g, x + 50, by + 1, 80, 6, r.happiness() / 10.0, Ui.happinessColor(r.happiness()));
        Ui.text(g, font, Component.literal(String.format(Locale.ROOT, "%.1f", r.happiness())), x + 136, by, Ui.TEXT);
        if (r.raided() || r.raidTonight()) {
            Ui.text(g, font, Component.translatable(r.raided() ? "crusadecraft.mayor.raided" : "crusadecraft.mayor.raid_tonight"),
                    x, by + 12, Ui.RED);
        }
    }

    private void line(GuiGraphics g, int x, int y, net.minecraft.world.item.Item icon, Component text, boolean alert) {
        g.pose().pushPose();
        g.pose().translate(x, y - 1, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        g.renderItem(new ItemStack(icon), 0, 0);
        g.pose().popPose();
        Ui.text(g, font, text, x + 13, y, alert ? 0xFFE0A050 : Ui.TEXT);
    }

    private void renderRequests(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        if (view.requests().isEmpty()) return;
        int rw = w / 2 - 18;
        Ui.text(g, font, Component.translatable("crusadecraft.council.requests", view.requests().size()), x, y, Ui.TEXT_TITLE);
        y += 11;
        for (CouncilView.RequestSummary r : view.requests()) {
            if (y + 11 > top + h - 30) break;
            boolean hover = Ui.inside(mouseX, mouseY, x, y - 1, rw, 11);
            if (hover) g.fill(x, y - 1, x + rw, y + 10, Ui.PANEL_HIGHLIGHT);
            String text = font.plainSubstrByWidth(Component.translatable(r.type().titleKey()).getString(), rw - 8);
            Ui.text(g, font, Component.literal("• " + text), x + 2, y, hover ? Ui.TEXT_TITLE : Ui.TEXT);
            requestHits.add(new Object[]{r, x, y - 1, rw});
            if (hover) pendingTooltip = List.of(Component.translatable("crusadecraft.council.answer").withStyle(ChatFormatting.GOLD));
            y += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] hit : requestHits) {
            if (Ui.inside(mouseX, mouseY, (int) hit[1], (int) hit[2], (int) hit[3], 11)) {
                CouncilView.RequestSummary r = (CouncilView.RequestSummary) hit[0];
                CrusadeNetwork.toServer(new CourtPackets.MayorAction(view.colony(), null, true, r.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
