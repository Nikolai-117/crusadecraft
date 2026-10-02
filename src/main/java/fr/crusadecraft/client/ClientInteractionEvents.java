package fr.crusadecraft.client;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Côté client : le clic sur un citoyen (sceptre, dignitaire) et le titre affiché au-dessus des têtes.
 * Le pendant serveur, qui empêche MineColonies de traiter le même clic, est dans {@code ServerEvents}.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInteractionEvents {

    private ClientInteractionEvents() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        Optional<CitizenRef> ref = MineColoniesBridge.refOf(event.getTarget());
        if (ref.isEmpty()) return;
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;

        if (player.getMainHandItem().is(ModItems.SCEPTRE.get())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (mainHand) {
                CrusadeNetwork.toServer(new RequestCharacterPacket(ref.get(), player.isShiftKeyDown()
                        ? RequestCharacterPacket.Mode.APPOINT : RequestCharacterPacket.Mode.SHEET));
            }
            return;
        }
        // Accroupi : MineColonies garde la main (sa fenêtre, ses dons d'objets).
        if (player.isShiftKeyDown() || !ClientTitles.isDignitary(ref.get())) return;
        if (ClientTitles.consumeBypass(ref.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (mainHand) CrusadeNetwork.toServer(new RequestCharacterPacket(ref.get(), RequestCharacterPacket.Mode.DIALOGUE));
    }

    /** « Aldric, Maréchal de Valbrume » au-dessus de la tête, sans toucher au nom du citoyen. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        Optional<CitizenRef> ref = MineColoniesBridge.refOf(event.getEntity());
        if (ref.isEmpty()) return;
        Component title = ClientTitles.title(ref.get());
        if (title == null) return;
        event.setContent(title);
        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTitles.clear();
    }
}
