package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.event.EventView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.IntriguePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Un événement à décider, à la manière de CK3 : une image, un récit, des choix. Chaque option montre ses effets
 * en infobulle ; celle qui s'appliquera d'office si l'on ne tranche pas est signalée.
 */
public class EventScreen extends Screen {
    private static final int OPTION_H = 18;

    private final EventView view;
    private final ItemStack icon;
    private int left, top, w, h;

    public EventScreen(EventView view) {
        super(view.title());
        this.view = view;
        Item item = ForgeRegistries.ITEMS.getValue(view.icon());
        this.icon = new ItemStack(item == null || item == Items.AIR ? Items.BOOK : item);
    }

    @Override
    protected void init() {
        w = Math.min(340, width - 16);
        h = Math.min(250, height - 12);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int n = view.options().size();
        int by = top + h - 10 - n * (OPTION_H + 2);
        for (int i = 0; i < n; i++) {
            EventView.OptionView o = view.options().get(i);
            int index = i;
            Button b = Button.builder(o.text(), btn -> {
                if (minecraft != null && minecraft.getConnection() != null) {
                    CrusadeNetwork.toServer(new IntriguePackets.AnswerEvent(view.id(), index));
                }
            }).bounds(left + 12, by + i * (OPTION_H + 2), w - 24, OPTION_H).build();
            b.active = o.allowed();
            b.setTooltip(Tooltip.create(tooltip(o, i)));
            addRenderableWidget(b);
        }
    }

    private Component tooltip(EventView.OptionView o, int index) {
        MutableComponent tip = o.text().copy().withStyle(ChatFormatting.GOLD);
        if (o.effects().isEmpty()) tip.append("\n").append(Component.translatable("crusadecraft.event.no_effect").withStyle(ChatFormatting.GRAY));
        for (Component fx : o.effects()) tip.append("\n").append(fx.copy().withStyle(ChatFormatting.GRAY));
        if (!o.allowed()) tip.append("\n\n").append(Component.translatable("crusadecraft.event.requires").withStyle(ChatFormatting.RED));
        if (index == view.defaultOption()) tip.append("\n\n").append(Component.translatable("crusadecraft.event.default").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        // L'image de l'événement, dans un cadre.
        Ui.inset(g, left + 10, top + 10, 40, 40);
        g.pose().pushPose();
        g.pose().translate(left + 14, top + 14, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        float k = Math.min(1.3f, (w - 72) / (float) Math.max(1, font.width(title)));
        Ui.scaled(g, font, title, left + 58, top + 14, k, Ui.TEXT_TITLE, false);
        Component deadline = view.daysLeft() <= 0 ? Component.translatable("crusadecraft.event.today")
                : Component.translatable("crusadecraft.event.days_left", view.daysLeft());
        Ui.text(g, font, deadline, left + 58, top + 32, Ui.TEXT_MUTED);
        if (view.waiting() > 1) {
            Component more = Component.translatable("crusadecraft.event.waiting", view.waiting());
            Ui.text(g, font, more, left + w - 10 - font.width(more), top + 32, 0xFFE0C060);
        }
        Ui.separator(g, left + 10, top + 56, w - 20, Ui.GOLD_DARK);
        // Le récit, coupé à la largeur ; ce qui ne tient pas est rogné proprement.
        int textTop = top + 64;
        int textBottom = top + h - 14 - view.options().size() * (OPTION_H + 2);
        List<FormattedCharSequence> lines = font.split(view.text(), w - 28);
        int y = textTop;
        for (FormattedCharSequence line : lines) {
            if (y + font.lineHeight > textBottom) break;
            g.drawString(font, line, left + 14, y, Ui.TEXT, false);
            y += font.lineHeight + 2;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
