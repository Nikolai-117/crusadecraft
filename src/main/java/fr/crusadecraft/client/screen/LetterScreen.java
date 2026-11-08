package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.item.LetterItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Une lettre dépliée : le parchemin, la signature, et les réponses possibles. */
public class LetterScreen extends Screen {
    private final CompoundTag data;
    private int left, top, w, h;

    public LetterScreen(CompoundTag data) {
        super(LetterItem.json(data, "subject"));
        this.data = data;
    }

    @Override
    protected void init() {
        w = Math.min(300, width - 12);
        h = Math.min(220, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        ListTag choices = data.getList("choices", Tag.TAG_COMPOUND);
        int answered = data.getInt("answered");
        if (answered < 0 && !choices.isEmpty() && data.hasUUID("id")) {
            UUID id = data.getUUID("id");
            int bw = (w - 30 - (choices.size() - 1) * 6) / choices.size();
            for (int i = 0; i < choices.size(); i++) {
                CompoundTag c = choices.getCompound(i);
                Letter.Action action;
                try {
                    action = Letter.Action.valueOf(c.getString("action"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                boolean seal = c.getBoolean("seal");
                int index = i;
                Component label = Component.translatable(action.key());
                Button b = Button.builder(label, btn -> {
                    CrusadeNetwork.toServer(new DiplomacyPackets.AnswerLetter(id, index));
                    onClose();
                }).bounds(left + 15 + i * (bw + 6), top + h - 30, bw, 18).build(KitButton::new);
                if (seal) b.setTooltip(Tooltip.create(Component.translatable("crusadecraft.letter.seal_tip")));
                addRenderableWidget(b);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.parchment(g, left, top, w, h);
        int x = left + 16;
        int y = top + 14;
        Ui.scaled(g, font, title, x, y, 1.2f, Ui.INK, false);
        y += 16;
        Ui.text(g, font, Component.translatable("crusadecraft.letter.from", LetterItem.json(data, "sender")), x, y, Ui.INK_MUTED);
        y += 10;
        Ui.text(g, font, Component.translatable("crusadecraft.letter.day", data.getLong("day")), x, y, Ui.INK_MUTED);
        y += 14;
        Ui.separator(g, x, y, w - 32, Ui.INK_MUTED);
        y += 8;
        Ui.wrapped(g, font, LetterItem.json(data, "body"), x, y, w - 32, Ui.INK);
        // Le sceau de cire, en bas à droite.
        int sx = left + w - 34, sy = top + h - 58;
        g.fill(sx + 3, sy, sx + 13, sy + 16, 0xFF8E1F18);
        g.fill(sx, sy + 3, sx + 16, sy + 13, 0xFF8E1F18);
        g.fill(sx + 4, sy + 4, sx + 12, sy + 12, 0xFFB5342A);
        int answered = data.getInt("answered");
        if (answered >= 0) {
            Ui.text(g, font, Component.translatable("crusadecraft.letter.answered").withStyle(ChatFormatting.ITALIC), x, top + h - 24, Ui.INK_MUTED);
        } else if (!data.getList("choices", Tag.TAG_COMPOUND).isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.letter.until", data.getLong("expires")), x, top + h - 42, Ui.INK_MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
