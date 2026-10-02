package fr.crusadecraft.client;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Événements côté client (bus de Forge). */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (KeyBindings.REALM_MENU.consumeClick()) {
            CrusadeNetwork.toServer(new RequestRealmPacket(OpenRealmScreenPacket.Target.MENU));
        }
        while (KeyBindings.MAP.consumeClick()) {
            mc.player.displayClientMessage(Component.translatable("crusadecraft.map.soon"), true);
        }
    }
}
