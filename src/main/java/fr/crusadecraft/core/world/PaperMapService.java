package fr.crusadecraft.core.world;

import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Où se trouvent les cartes papier (P14). Minecraft n'envoie pas au client le centre d'une carte : on le lui dit,
 * une fois par carte et par session, pour celles qu'il tient et celles des cadres proches. Toutes les deux secondes,
 * une poignée d'entités au plus.
 */
public final class PaperMapService {
    private static final double FRAME_RANGE = 24;
    private static final Map<UUID, Set<Integer>> SENT = new HashMap<>();

    private PaperMapService() {}

    public static void onServerStopped() {
        SENT.clear();
    }

    public static void onLogout(UUID player) {
        SENT.remove(player);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (InteractionHand hand : InteractionHand.values()) send(player, player.getItemInHand(hand));
            for (ItemFrame frame : player.serverLevel().getEntitiesOfClass(ItemFrame.class, player.getBoundingBox().inflate(FRAME_RANGE))) {
                send(player, frame.getItem());
            }
        }
    }

    private static void send(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof MapItem)) return;
        Integer id = MapItem.getMapId(stack);
        if (id == null) return;
        Set<Integer> sent = SENT.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        if (sent.contains(id)) return;
        MapItemSavedData data = MapItem.getSavedData(stack, player.level());
        if (data == null) return;
        sent.add(id);
        CrusadeNetwork.toPlayer(player, new WorldPackets.MapCenter(id, data.dimension.location(), data.centerX, data.centerZ, 1 << data.scale));
    }
}
