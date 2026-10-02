package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.council.RequestType;
import fr.crusadecraft.core.council.RequestView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Une requête, présentée comme un événement de CK3 : le dignitaire, ce qu'il demande, et les réponses
 * possibles avec leur effet et leur coût.
 */
public class RequestScreen extends Screen {
    private static final int W = 300;

    private final RequestView view;
    private int left, top, h;

    public RequestScreen(RequestView view) {
        super(Component.translatable(view.type().titleKey()));
        this.view = view;
    }

    @Override
    protected void init() {
        h = 120 + view.choices().size() * 20;
        left = (width - W) / 2;
        top = Math.max(4, (height - h) / 2);
        int y = top + h - 8 - view.choices().size() * 20;
        for (RequestView.ChoiceView c : view.choices()) {
            MutableComponent label = Component.translatable(c.choice().key());
            if (c.goldCost() > 0) label.append(Component.translatable("crusadecraft.choice.cost.gold", c.goldCost()));
            if (c.prestigeCost() > 0) label.append(Component.translatable("crusadecraft.choice.cost.prestige", c.prestigeCost()));
            Button b = Button.builder(label, btn -> {
                CrusadeNetwork.toServer(new CourtPackets.AnswerRequest(view.id(), c.choice()));
                onClose();
            }).bounds(left + 12, y, W - 24, 18).build();
            MutableComponent tip = Component.translatable(c.choice().effectKey()).withStyle(ChatFormatting.GRAY);
            if (!c.enabled()) tip = Component.translatable("crusadecraft.choice.cannot_afford").withStyle(ChatFormatting.RED);
            b.setTooltip(Tooltip.create(tip));
            b.active = c.enabled();
            addRenderableWidget(b);
            y += 20;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, W, h);
        Ui.inset(g, left + 10, top + 10, 56, 72);
        Portraits.render(g, view.entityId(), left + 10, top + 10, 56, 72, mouseX, mouseY);
        int tx = left + 76;
        Ui.scaled(g, font, title, tx, top + 12, 1.2f, Ui.TEXT_TITLE, false);
        Component who = view.post() == null ? Component.literal(view.fromName())
                : Component.literal(view.fromName() + ", ").append(Component.translatable(view.post().key(view.fromFemale())));
        Ui.text(g, font, who, tx, top + 26, Ui.TEXT_MUTED);
        Component address = Component.translatable(view.rulerFeminine() ? "crusadecraft.dialogue.address.f" : "crusadecraft.dialogue.address.m");
        Component text = Component.translatable(view.type().textKey(), address, view.place(), view.count()).withStyle(ChatFormatting.ITALIC);
        Ui.wrapped(g, font, Component.literal("« ").append(text).append(" »"), tx, top + 40, W - 88, Ui.TEXT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Pour les tests visuels : le type affiché. */
    public RequestType type() {
        return view.type();
    }
}
