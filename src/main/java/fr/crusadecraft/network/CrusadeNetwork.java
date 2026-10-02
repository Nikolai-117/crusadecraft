package fr.crusadecraft.network;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.network.packet.CharacterActionPacket;
import fr.crusadecraft.network.packet.CourtPackets;
import fr.crusadecraft.network.packet.FoundRealmPacket;
import fr.crusadecraft.network.packet.OpenCharacterPacket;
import fr.crusadecraft.network.packet.OpenFoundingScreenPacket;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
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
        // P2 : personnages
        CHANNEL.messageBuilder(SyncTitlesPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncTitlesPacket::encode).decoder(SyncTitlesPacket::decode)
                .consumerMainThread(SyncTitlesPacket::handle).add();
        CHANNEL.messageBuilder(OpenCharacterPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCharacterPacket::encode).decoder(OpenCharacterPacket::decode)
                .consumerMainThread(OpenCharacterPacket::handle).add();
        CHANNEL.messageBuilder(RequestCharacterPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestCharacterPacket::encode).decoder(RequestCharacterPacket::decode)
                .consumerMainThread(RequestCharacterPacket::handle).add();
        CHANNEL.messageBuilder(CharacterActionPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CharacterActionPacket::encode).decoder(CharacterActionPacket::decode)
                .consumerMainThread(CharacterActionPacket::handle).add();
        // P3 : cour
        CHANNEL.messageBuilder(CourtPackets.OpenCouncil.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CourtPackets.OpenCouncil::encode).decoder(CourtPackets.OpenCouncil::decode)
                .consumerMainThread(CourtPackets.OpenCouncil::handle).add();
        CHANNEL.messageBuilder(CourtPackets.OpenMayor.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CourtPackets.OpenMayor::encode).decoder(CourtPackets.OpenMayor::decode)
                .consumerMainThread(CourtPackets.OpenMayor::handle).add();
        CHANNEL.messageBuilder(CourtPackets.OpenRequest.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CourtPackets.OpenRequest::encode).decoder(CourtPackets.OpenRequest::decode)
                .consumerMainThread(CourtPackets.OpenRequest::handle).add();
        CHANNEL.messageBuilder(CourtPackets.CouncilAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtPackets.CouncilAction::encode).decoder(CourtPackets.CouncilAction::decode)
                .consumerMainThread(CourtPackets.CouncilAction::handle).add();
        CHANNEL.messageBuilder(CourtPackets.MayorAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtPackets.MayorAction::encode).decoder(CourtPackets.MayorAction::decode)
                .consumerMainThread(CourtPackets.MayorAction::handle).add();
        CHANNEL.messageBuilder(CourtPackets.AnswerRequest.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtPackets.AnswerRequest::encode).decoder(CourtPackets.AnswerRequest::decode)
                .consumerMainThread(CourtPackets.AnswerRequest::handle).add();
        CHANNEL.messageBuilder(CourtPackets.OpenOffice.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtPackets.OpenOffice::encode).decoder(CourtPackets.OpenOffice::decode)
                .consumerMainThread(CourtPackets.OpenOffice::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        // Un joueur fictif (machines, tests) n'a pas de client à qui parler.
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void toServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
