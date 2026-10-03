package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.client.screen.Ui;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * Un bouton « CrusadeCraft » sur la fenêtre de l'Hôtel de ville de MineColonies, qui ouvre le menu royal. Sans
 * mixin : la fenêtre est un écran BlockUI dont on ne touche rien ; on dessine le bouton par-dessus et l'on
 * intercepte son clic. Si MineColonies change sa fenêtre, le bouton disparaît simplement (une ligne au journal).
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TownHallButton {
    private static final int W = 104, H = 18;
    private static Screen lastScreen;
    private static boolean lastIsTownHall;
    private static boolean broken;

    private TownHallButton() {}

    /** L'écran est-il la fenêtre de l'Hôtel de ville ? On cherche, dans l'écran BlockUI, la fenêtre qu'il porte. */
    public static boolean isTownHall(Screen screen) {
        if (broken || screen == null) return false;
        if (screen == lastScreen) return lastIsTownHall;
        lastScreen = screen;
        lastIsTownHall = false;
        if (!screen.getClass().getName().startsWith("com.ldtteam.blockui")) return false;
        try {
            for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    if (f.get(screen) instanceof AbstractWindowTownHall) {
                        lastIsTownHall = true;
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            CrusadeCraft.LOGGER.warn("CrusadeCraft : bouton de l'Hôtel de ville désactivé ({})", e.toString());
        }
        return false;
    }

    /** BlockUI donne à son écran sa propre largeur : on se cale sur celle de la fenêtre du jeu, dans laquelle on dessine. */
    private static int x(Screen s) {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth() - W - 6;
    }

    private static int y() {
        return 6;
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        if (!isTownHall(s)) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = x(s), y = y();
        boolean hover = Ui.inside(event.getMouseX(), event.getMouseY(), x, y, W, H);
        // BlockUI laisse le test de profondeur actif et dessine haut sur l'axe z : on passe devant, sans profondeur.
        RenderSystem.disableDepthTest();
        g.pose().pushPose();
        // BlockUI peut laisser sa propre échelle sur la pile : on repart d'une matrice neutre.
        g.pose().setIdentity();
        g.pose().translate(0, 0, 2000);
        Ui.panel(g, x, y, W, H);
        if (hover) g.fill(x + 2, y + 2, x + W - 2, y + H - 2, Ui.PANEL_HIGHLIGHT);
        Ui.centered(g, Minecraft.getInstance().font, Component.translatable("crusadecraft.townhall.button"), x + W / 2, y + 5, Ui.TEXT_TITLE);
        g.pose().popPose();
        g.flush();
        RenderSystem.enableDepthTest();
        if (hover) g.renderTooltip(Minecraft.getInstance().font, Component.translatable("crusadecraft.townhall.button.tip"), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen s = event.getScreen();
        if (event.getButton() != 0 || !isTownHall(s) || !Ui.inside(event.getMouseX(), event.getMouseY(), x(s), y(), W, H)) return;
        event.setCanceled(true);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (Minecraft.getInstance().getConnection() != null) CrusadeNetwork.toServer(new RequestRealmPacket(OpenRealmScreenPacket.Target.MENU));
    }
}
