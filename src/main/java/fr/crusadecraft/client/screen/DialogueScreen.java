package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.ClientTitles;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CharacterActionPacket;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Le dialogue avec un dignitaire, comme une fenêtre d'événement de CK3 : son portrait, ce qu'il dit
 * (selon son opinion de vous), et ce que vous pouvez faire.
 */
public class DialogueScreen extends Screen {
    private static final int W = 300;
    private static final int H = 182;

    private final CharacterView view;
    private int left, top;

    public DialogueScreen(CharacterView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        List<Button> buttons = new ArrayList<>();
        Button order = Button.builder(Component.translatable("crusadecraft.dialogue.order"), b -> {}).build();
        order.active = false;
        order.setTooltip(Tooltip.create(Component.translatable("crusadecraft.dialogue.order.tip")));
        buttons.add(order);
        if (view.canAct() && !view.deceased()) {
            buttons.add(act("crusadecraft.action.reward", CharacterService.Action.REWARD, "crusadecraft.action.reward.tip"));
            buttons.add(act("crusadecraft.action.reprimand", CharacterService.Action.REPRIMAND, "crusadecraft.action.reprimand.tip"));
            buttons.add(view.status() == CharacterStatus.EMPRISONNE
                    ? act("crusadecraft.action.release", CharacterService.Action.RELEASE, null)
                    : act("crusadecraft.action.imprison", CharacterService.Action.IMPRISON, "crusadecraft.action.imprison.tip"));
            buttons.add(view.status() == CharacterStatus.EXILE
                    ? act("crusadecraft.action.recall", CharacterService.Action.RECALL, null)
                    : act("crusadecraft.action.exile", CharacterService.Action.EXILE, "crusadecraft.action.exile.tip"));
        }
        buttons.add(Button.builder(Component.translatable("crusadecraft.dialogue.sheet"), b ->
                CrusadeNetwork.toServer(new RequestCharacterPacket(view.ref(), RequestCharacterPacket.Mode.SHEET))).build());
        buttons.add(Button.builder(Component.translatable("crusadecraft.dialogue.minecolonies"), b -> openMineColonies()).build());
        buttons.add(Button.builder(Component.translatable("crusadecraft.dialogue.farewell"), b -> onClose()).build());

        int colW = (W - 30) / 2;
        int bx0 = left + 10, by0 = top + H - 10 - ((buttons.size() + 1) / 2) * 20;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            b.setX(bx0 + (i % 2) * (colW + 10));
            b.setY(by0 + (i / 2) * 20);
            b.setWidth(colW);
            b.setHeight(18);
            addRenderableWidget(b);
        }
    }

    private Button act(String key, CharacterService.Action action, String tip) {
        Button b = Button.builder(Component.translatable(key), btn -> CrusadeNetwork.toServer(
                new CharacterActionPacket(view.ref(), action, null, RequestCharacterPacket.Mode.DIALOGUE))).build();
        if (tip != null) b.setTooltip(Tooltip.create(Component.translatable(tip)));
        return b;
    }

    /** Rouvre la fenêtre MineColonies du citoyen : le prochain clic sur lui passe directement à MineColonies. */
    private void openMineColonies() {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(view.entityId());
        onClose();
        if (entity == null || mc.player == null || mc.gameMode == null) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("crusadecraft.dialogue.too_far"), true);
            return;
        }
        ClientTitles.allowNextClick(view.ref());
        mc.gameMode.interact(mc.player, entity, InteractionHand.MAIN_HAND);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.inset(g, left + 10, top + 10, 64, 80);
        Portraits.render(g, view.entityId(), left + 10, top + 10, 64, 80, mouseX, mouseY);
        int tx = left + 84;
        Ui.scaled(g, font, Component.literal(view.name()), tx, top + 12, 1.3f, Ui.TEXT_TITLE, false);
        Component role = view.post() != null ? Component.translatable(view.post().key(view.female()))
                : Component.translatable(view.status().key());
        Ui.text(g, font, role, tx, top + 26, Ui.TEXT_MUTED);
        if (view.hasOpinion()) {
            int op = view.opinion();
            int color = op >= 20 ? Ui.GREEN : op <= -20 ? Ui.RED : 0xFFC99A3C;
            Ui.text(g, font, Component.translatable("crusadecraft.character.opinion").append(" " + CharacterScreen.signed(op)), tx, top + 37, color);
        }
        Component address = Component.translatable(view.rulerFeminine() ? "crusadecraft.dialogue.address.f" : "crusadecraft.dialogue.address.m");
        Component speech = Component.translatable(greetingKey(), address).withStyle(ChatFormatting.ITALIC);
        Ui.wrapped(g, font, Component.literal("« ").append(speech).append(" »"), tx, top + 52, W - 94, Ui.TEXT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String greetingKey() {
        if (view.deceased()) return "crusadecraft.dialogue.greeting.deceased";
        if (view.status() == CharacterStatus.EMPRISONNE) return "crusadecraft.dialogue.greeting.imprisoned";
        if (view.status() == CharacterStatus.EXILE) return "crusadecraft.dialogue.greeting.exiled";
        int op = view.opinion();
        if (!view.hasOpinion()) return "crusadecraft.dialogue.greeting.foreign";
        if (op >= 50) return "crusadecraft.dialogue.greeting.devoted";
        if (op >= 15) return "crusadecraft.dialogue.greeting.friendly";
        if (op > -15) return "crusadecraft.dialogue.greeting.neutral";
        if (op > -50) return "crusadecraft.dialogue.greeting.cold";
        return "crusadecraft.dialogue.greeting.hostile";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
