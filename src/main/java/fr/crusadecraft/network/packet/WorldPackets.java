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

    /** Une ligne des nouvelles du monde : son jour et son texte. */
    public record NewsLine(long day, Component text) {}

    public static void sendNews(ServerPlayer player) {
        java.util.List<NewsLine> lines = new java.util.ArrayList<>();
        java.util.List<NewsLine> chronicle = new java.util.ArrayList<>();
        fr.crusadecraft.core.realm.RealmService.realmOf(player.server, player.getUUID()).ifPresent(r -> {
            for (fr.crusadecraft.core.world.WorldNews n : fr.crusadecraft.core.ai.AiBrain.newsFor(player.server, r)) {
                lines.add(new NewsLine(n.day(), n.text()));
            }
            for (fr.crusadecraft.core.world.WorldNews n : r.intrigue().chronicle()) chronicle.add(new NewsLine(n.day(), n.text()));
        });
        CrusadeNetwork.toPlayer(player, new NewsData(lines, chronicle));
    }

    static void writeLines(FriendlyByteBuf buf, java.util.List<NewsLine> lines) {
        buf.writeVarInt(lines.size());
        for (NewsLine l : lines) {
            buf.writeLong(l.day());
            buf.writeComponent(l.text());
        }
    }

    static java.util.List<NewsLine> readLines(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        java.util.List<NewsLine> lines = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(new NewsLine(buf.readLong(), buf.readComponent()));
        return lines;
    }

    /** Les nouvelles du monde que ce souverain peut connaître, et la chronique de son règne, pour l'onglet Chroniques. */
    public record NewsData(java.util.List<NewsLine> lines, java.util.List<NewsLine> chronicle) {
        public static void encode(NewsData p, FriendlyByteBuf buf) {
            writeLines(buf, p.lines);
            writeLines(buf, p.chronicle);
        }

        public static NewsData decode(FriendlyByteBuf buf) {
            return new NewsData(readLines(buf), readLines(buf));
        }

        public static void handle(NewsData p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.news(p.lines, p.chronicle));
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

    /** {@code edit} : la carte de l'éditeur (accordée seulement à qui peut éditer). */
    public record RequestMap(boolean open, boolean edit) {
        public RequestMap(boolean open) {
            this(open, false);
        }

        public static void encode(RequestMap p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.open);
            buf.writeBoolean(p.edit);
        }

        public static RequestMap decode(FriendlyByteBuf buf) { return new RequestMap(buf.readBoolean(), buf.readBoolean()); }

        public static void handle(RequestMap p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CrusadeNetwork.toPlayer(player, new MapData(WorldService.mapView(player, p.edit), p.open));
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
