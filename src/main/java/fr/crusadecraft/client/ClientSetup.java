package fr.crusadecraft.client;

import fr.crusadecraft.CrusadeCraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Initialisation côté client (bus du mod). */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(fr.crusadecraft.registry.ModBlockEntities.WAR_MAP.get(),
                fr.crusadecraft.client.map.WarMapRenderer::new);
    }

    /** P34.1 : le HUD du plan d'architecte, au-dessus de la barre d'objets. */
    @SubscribeEvent
    public static void onOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "plan_architecte",
                fr.crusadecraft.client.hud.PlanHud::render);
        // P38 : le HUD du bâton d'intendant.
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "baton_intendant",
                fr.crusadecraft.client.hud.CityHud::render);
    }

    /** P42 : l'œuf d'habitant prend les couleurs du royaume et du métier qu'il fera naître. */
    @SubscribeEvent
    public static void onItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> 0xFF000000 | fr.crusadecraft.item.InhabitantEggItem.tint(stack, layer), fr.crusadecraft.registry.ModItems.OEUF_HABITANT.get());
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.REALM_MENU);
        event.register(KeyBindings.UNDO_BORNAGE);
        event.register(KeyBindings.MAP);
        event.register(KeyBindings.POST);
        for (var k : fr.crusadecraft.client.map.PlanControl.ALL) event.register(k);
    }
}
