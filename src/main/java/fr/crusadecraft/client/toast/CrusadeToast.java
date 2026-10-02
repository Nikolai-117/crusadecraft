package fr.crusadecraft.client.toast;

import fr.crusadecraft.client.screen.Ui;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.network.packet.ToastPacket;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Notification façon CK3 : liseré coloré selon le type, icône, titre doré, texte. */
public class CrusadeToast implements Toast {
    private static final int WIDTH = 200;

    private final ToastPacket.Kind kind;
    private final Component title;
    private final Component body;

    private final List<FormattedCharSequence> lines;

    public CrusadeToast(ToastPacket.Kind kind, Component title, Component body) {
        this.kind = kind;
        this.title = title;
        this.body = body;
        List<FormattedCharSequence> all = net.minecraft.client.Minecraft.getInstance().font.split(body, WIDTH - 34);
        this.lines = all.size() > MAX_LINES ? all.subList(0, MAX_LINES) : all;
    }

    private static final int MAX_LINES = 3;

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return Math.max(32, 20 + lines.size() * 10);
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent component, long timeSinceLastVisible) {
        Font font = component.getMinecraft().font;
        int w = width(), h = height();
        g.fill(0, 0, w, h, 0xF21A1511);
        g.fill(0, 0, 3, h, accent());
        g.renderOutline(0, 0, w, h, Ui.GOLD_DARK);
        g.renderItem(icon(), 7, Math.min(8, (h - 16) / 2));
        g.drawString(font, font.plainSubstrByWidth(title.getString(), w - 34), 28, 6, Ui.TEXT_TITLE, false);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), 28, 17 + i * 10, Ui.TEXT, false);
        }
        long duration = CrusadeConfig.CLIENT.toastSeconds.get() * 1000L;
        return timeSinceLastVisible >= duration ? Visibility.HIDE : Visibility.SHOW;
    }

    private int accent() {
        return switch (kind) {
            case ALERT, WAR -> Ui.RED;
            case LETTER -> 0xFF5A7FB0;
            case EVENT -> 0xFF8C6BB0;
            case COUNCIL -> Ui.GREEN;
            default -> Ui.GOLD;
        };
    }

    private ItemStack icon() {
        return switch (kind) {
            case ALERT -> new ItemStack(Items.BELL);
            case WAR -> new ItemStack(Items.IRON_SWORD);
            case LETTER -> new ItemStack(Items.PAPER);
            case EVENT -> new ItemStack(Items.WRITABLE_BOOK);
            case COUNCIL -> new ItemStack(Items.LECTERN);
            default -> new ItemStack(ModItems.THRONE.get());
        };
    }
}
