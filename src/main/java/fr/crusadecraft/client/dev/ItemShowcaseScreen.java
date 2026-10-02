package fr.crusadecraft.client.dev;

import fr.crusadecraft.client.screen.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Vitrine des objets et blocs de CrusadeCraft, agrandis : réservée au banc d'essai visuel. */
public class ItemShowcaseScreen extends Screen {
    private final List<ItemStack> items;

    public ItemShowcaseScreen(List<ItemStack> items) {
        super(Component.literal("Vitrine"));
        this.items = items;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        // Les cases rétrécissent avec le nombre d'objets pour toujours tenir dans la largeur.
        int cell = Math.min(70, (width - 30) / Math.max(1, items.size()));
        float scale = Math.min(3f, (cell - 8) / 16f);
        int total = items.size() * cell;
        int x0 = (width - total) / 2;
        int y0 = height / 2 - 40;
        Ui.panel(g, x0 - 10, y0 - 20, total + 20, 110);
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int x = x0 + i * cell;
            g.pose().pushPose();
            g.pose().translate(x + (cell - 16 * scale) / 2f, y0, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
            Ui.wrappedCentered(g, font, stack.getHoverName(), x + cell / 2, y0 + 56, cell - 4, Ui.TEXT);
        }
    }
}
