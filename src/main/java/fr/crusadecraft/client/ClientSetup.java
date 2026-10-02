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

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.REALM_MENU);
        event.register(KeyBindings.MAP);
    }
}
