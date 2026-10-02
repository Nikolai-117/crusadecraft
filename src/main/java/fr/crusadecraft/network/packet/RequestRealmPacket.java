package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Le joueur demande à voir son royaume (touche du menu royal, registre). */
public record RequestRealmPacket(OpenRealmScreenPacket.Target target) {

    public static void encode(RequestRealmPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.target);
    }

    public static RequestRealmPacket decode(FriendlyByteBuf buf) {
        return new RequestRealmPacket(buf.readEnum(OpenRealmScreenPacket.Target.class));
    }

    public static void handle(RequestRealmPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        sendView(player, realm.get(), p.target);
    }

    public static void sendView(ServerPlayer player, Realm realm, OpenRealmScreenPacket.Target target) {
        CrusadeNetwork.toPlayer(player, new OpenRealmScreenPacket(RealmService.view(player.server, realm), target));
    }
}
