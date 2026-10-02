package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.world.AiRealmView;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Paquets du monde : carte du royaume, fiche d'un royaume IA. */
public final class WorldPackets {

    private WorldPackets() {}

    public static void sendAiRealm(ServerPlayer player, UUID realm) {
        Optional<AiRealmView> view = WorldService.realmView(player, realm);
        if (view.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.world.unknown_realm"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new AiRealmData(view.get()));
    }

    // ------------------------------------------------------------------ serveur → client

    /** La carte ; {@code open} l'ouvre, sinon elle ne fait que rafraîchir (Table de guerre, écran ouvert). */
    public record MapData(MapView view, boolean open) {
        public static void encode(MapData p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static MapData decode(FriendlyByteBuf buf) {
            return new MapData(MapView.read(buf), buf.readBoolean());
        }

        public static void handle(MapData p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.map(p.view, p.open));
        }
    }

    public record AiRealmData(AiRealmView view) {
        public static void encode(AiRealmData p, FriendlyByteBuf buf) { p.view.write(buf); }
        public static AiRealmData decode(FriendlyByteBuf buf) { return new AiRealmData(AiRealmView.read(buf)); }
        public static void handle(AiRealmData p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.aiRealm(p.view));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    public record RequestMap(boolean open) {
        public static void encode(RequestMap p, FriendlyByteBuf buf) { buf.writeBoolean(p.open); }
        public static RequestMap decode(FriendlyByteBuf buf) { return new RequestMap(buf.readBoolean()); }

        public static void handle(RequestMap p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CrusadeNetwork.toPlayer(player, new MapData(WorldService.mapView(player), p.open));
        }
    }

    public record RequestAiRealm(UUID realm) {
        public static void encode(RequestAiRealm p, FriendlyByteBuf buf) { buf.writeUUID(p.realm); }
        public static RequestAiRealm decode(FriendlyByteBuf buf) { return new RequestAiRealm(buf.readUUID()); }

        public static void handle(RequestAiRealm p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) sendAiRealm(player, p.realm);
        }
    }
}
