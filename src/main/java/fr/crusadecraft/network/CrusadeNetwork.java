package fr.crusadecraft.network;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.network.packet.FoundRealmPacket;
import fr.crusadecraft.network.packet.OpenFoundingScreenPacket;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal réseau de CrusadeCraft.
 *
 * <p>Règle : un paquet venant du client n'est qu'une <i>demande</i>. Le serveur revérifie tout
 * (distance, droits, état) avant d'agir.
 */
public final class CrusadeNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(CrusadeCraft.MOD_ID + ":main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    private CrusadeNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(ToastPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ToastPacket::encode).decoder(ToastPacket::decode).consumerMainThread(ToastPacket::handle).add();
        CHANNEL.messageBuilder(OpenFoundingScreenPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFoundingScreenPacket::encode).decoder(OpenFoundingScreenPacket::decode)
                .consumerMainThread(OpenFoundingScreenPacket::handle).add();
        CHANNEL.messageBuilder(OpenRealmScreenPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenRealmScreenPacket::encode).decoder(OpenRealmScreenPacket::decode)
                .consumerMainThread(OpenRealmScreenPacket::handle).add();
        CHANNEL.messageBuilder(FoundRealmPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FoundRealmPacket::encode).decoder(FoundRealmPacket::decode)
                .consumerMainThread(FoundRealmPacket::handle).add();
        CHANNEL.messageBuilder(RequestRealmPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestRealmPacket::encode).decoder(RequestRealmPacket::decode)
                .consumerMainThread(RequestRealmPacket::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        // Un joueur fictif (machines, tests) n'a pas de client à qui parler.
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
