package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Portraits des personnages : le vrai citoyen, rendu en direct s'il est chargé autour du joueur,
 * sinon une silhouette.
 */
public final class Portraits {

    private Portraits() {}

    public static void render(GuiGraphics g, int entityId, int x, int y, int w, int h, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null || entityId < 0 ? null : mc.level.getEntity(entityId);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (entity instanceof LivingEntity living) {
            int cx = x + w / 2;
            int feet = y + h - 4;
            int scale = Math.max(20, (int) (h / 2.4));
            // P42 : son nom est déjà écrit à côté, et il déborderait du cadre.
            boolean shown = living.isCustomNameVisible();
            living.setCustomNameVisible(false);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, scale,
                    (float) (cx - mouseX), (float) (feet - scale * 1.6 - mouseY), living);
            living.setCustomNameVisible(shown);
        } else {
            silhouette(g, x, y, w, h);
        }
        g.disableScissor();
    }

    private static void silhouette(GuiGraphics g, int x, int y, int w, int h) {
        int cx = x + w / 2;
        int color = 0xFF3A3128;
        int head = Math.max(10, w / 4);
        int headTop = y + h / 4;
        g.fill(cx - head / 2, headTop, cx + head / 2, headTop + head, color);
        int bodyTop = headTop + head + 3;
        g.fill(cx - head, bodyTop, cx + head, y + h - 2, color);
        g.fill(cx - head - 4, bodyTop + 4, cx + head + 4, y + h - 2, color);
    }

    public static ItemStack statIcon(Stat stat) {
        return switch (stat) {
            case DIPLOMATIE -> new ItemStack(Items.FEATHER);
            case MARTIAL -> new ItemStack(Items.IRON_SWORD);
            case INTENDANCE -> new ItemStack(Items.GOLD_INGOT);
            case INTRIGUE -> new ItemStack(Items.ENDER_EYE);
            case ERUDITION -> new ItemStack(Items.BOOK);
        };
    }
}
