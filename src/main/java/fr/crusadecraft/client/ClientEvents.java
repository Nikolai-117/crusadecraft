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
        if (mc.player == null) return;
        fr.crusadecraft.client.map.StaffPreview.tick(mc);
        if (mc.screen != null) return;
        while (KeyBindings.UNDO_BORNAGE.consumeClick()) {
            CrusadeNetwork.toServer(fr.crusadecraft.network.packet.StaffPackets.Action.of(fr.crusadecraft.network.packet.StaffPackets.Action.Op.UNDO));
        }
        while (KeyBindings.REALM_MENU.consumeClick()) {
            CrusadeNetwork.toServer(new RequestRealmPacket(OpenRealmScreenPacket.Target.MENU));
        }
        while (KeyBindings.MAP.consumeClick()) {
            CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.WorldPackets.RequestMap(true));
        }
    }

    /** L'info-bulle d'une carte papier : à qui sont les terres en son centre (P14). */
    @SubscribeEvent
    public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof net.minecraft.world.item.MapItem)) return;
        Integer id = net.minecraft.world.item.MapItem.getMapId(event.getItemStack());
        var view = fr.crusadecraft.client.map.ClientMapData.view();
        if (id == null || view == null) return;
        fr.crusadecraft.client.map.PaperMapOverlay.center(id).filter(c -> c.dimension().equals(view.dimension())).ifPresent(c -> {
            int t = fr.crusadecraft.client.map.PaperMapOverlay.ownerAtCenter(view, c);
            if (t < 0) return;
            var territory = view.territories().get(t);
            net.minecraft.network.chat.Component name = territory.ai() ? fr.crusadecraft.core.world.AiNames.realmOf(territory.rank(), territory.label())
                    : net.minecraft.network.chat.Component.literal(territory.label());
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("crusadecraft.papermap.tooltip", name)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("crusadecraft.papermap.ruler",
                    net.minecraft.network.chat.Component.translatable(territory.rank().holderKey(territory.rulerFemale())), territory.ruler())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        });
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        fr.crusadecraft.client.map.ClientMapData.clear();
        fr.crusadecraft.client.map.PaperMapOverlay.clear();
    }
}
