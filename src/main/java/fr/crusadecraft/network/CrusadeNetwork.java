package fr.crusadecraft.network;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.network.packet.CharacterActionPacket;
import fr.crusadecraft.network.packet.CourtLifePackets;
import fr.crusadecraft.network.packet.CourtPackets;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import fr.crusadecraft.network.packet.EconomyPackets;
import fr.crusadecraft.network.packet.FoundRealmPacket;
import fr.crusadecraft.network.packet.IntriguePackets;
import fr.crusadecraft.network.packet.LineagePackets;
import fr.crusadecraft.network.packet.MapEditPackets;
import fr.crusadecraft.network.packet.OpenCharacterPacket;
import fr.crusadecraft.network.packet.OpenFoundingScreenPacket;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
import fr.crusadecraft.network.packet.ToastPacket;
import fr.crusadecraft.network.packet.WarPackets;
import fr.crusadecraft.network.packet.WorldPackets;
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
    private static final String VERSION = "11";

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
        // P4 : trésor et lois
        CHANNEL.messageBuilder(EconomyPackets.TreasuryData.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EconomyPackets.TreasuryData::encode).decoder(EconomyPackets.TreasuryData::decode)
                .consumerMainThread(EconomyPackets.TreasuryData::handle).add();
        CHANNEL.messageBuilder(EconomyPackets.LawsData.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EconomyPackets.LawsData::encode).decoder(EconomyPackets.LawsData::decode)
                .consumerMainThread(EconomyPackets.LawsData::handle).add();
        CHANNEL.messageBuilder(EconomyPackets.RequestTab.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EconomyPackets.RequestTab::encode).decoder(EconomyPackets.RequestTab::decode)
                .consumerMainThread(EconomyPackets.RequestTab::handle).add();
        CHANNEL.messageBuilder(EconomyPackets.TreasuryAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EconomyPackets.TreasuryAction::encode).decoder(EconomyPackets.TreasuryAction::decode)
                .consumerMainThread(EconomyPackets.TreasuryAction::handle).add();
        CHANNEL.messageBuilder(EconomyPackets.ProposeLaw.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EconomyPackets.ProposeLaw::encode).decoder(EconomyPackets.ProposeLaw::decode)
                .consumerMainThread(EconomyPackets.ProposeLaw::handle).add();
        // P5 : le monde
        CHANNEL.messageBuilder(WorldPackets.MapData.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldPackets.MapData::encode).decoder(WorldPackets.MapData::decode)
                .consumerMainThread(WorldPackets.MapData::handle).add();
        CHANNEL.messageBuilder(WorldPackets.AiRealmData.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldPackets.AiRealmData::encode).decoder(WorldPackets.AiRealmData::decode)
                .consumerMainThread(WorldPackets.AiRealmData::handle).add();
        CHANNEL.messageBuilder(WorldPackets.RequestMap.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorldPackets.RequestMap::encode).decoder(WorldPackets.RequestMap::decode)
                .consumerMainThread(WorldPackets.RequestMap::handle).add();
        CHANNEL.messageBuilder(WorldPackets.RequestAiRealm.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorldPackets.RequestAiRealm::encode).decoder(WorldPackets.RequestAiRealm::decode)
                .consumerMainThread(WorldPackets.RequestAiRealm::handle).add();
        // P6 : diplomatie
        CHANNEL.messageBuilder(DiplomacyPackets.OpenDiplomacy.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DiplomacyPackets.OpenDiplomacy::encode).decoder(DiplomacyPackets.OpenDiplomacy::decode)
                .consumerMainThread(DiplomacyPackets.OpenDiplomacy::handle).add();
        CHANNEL.messageBuilder(DiplomacyPackets.OpenChancery.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DiplomacyPackets.OpenChancery::encode).decoder(DiplomacyPackets.OpenChancery::decode)
                .consumerMainThread(DiplomacyPackets.OpenChancery::handle).add();
        CHANNEL.messageBuilder(DiplomacyPackets.DiploAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DiplomacyPackets.DiploAction::encode).decoder(DiplomacyPackets.DiploAction::decode)
                .consumerMainThread(DiplomacyPackets.DiploAction::handle).add();
        CHANNEL.messageBuilder(DiplomacyPackets.AnswerLetter.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DiplomacyPackets.AnswerLetter::encode).decoder(DiplomacyPackets.AnswerLetter::decode)
                .consumerMainThread(DiplomacyPackets.AnswerLetter::handle).add();
        CHANNEL.messageBuilder(DiplomacyPackets.ChanceryAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DiplomacyPackets.ChanceryAction::encode).decoder(DiplomacyPackets.ChanceryAction::decode)
                .consumerMainThread(DiplomacyPackets.ChanceryAction::handle).add();

        // P7 : guerre
        CHANNEL.messageBuilder(WarPackets.OpenWar.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WarPackets.OpenWar::encode).decoder(WarPackets.OpenWar::decode)
                .consumerMainThread(WarPackets.OpenWar::handle).add();
        CHANNEL.messageBuilder(WarPackets.WarAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WarPackets.WarAction::encode).decoder(WarPackets.WarAction::decode)
                .consumerMainThread(WarPackets.WarAction::handle).add();

        // P8 : nouvelles du monde
        CHANNEL.messageBuilder(WorldPackets.NewsData.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldPackets.NewsData::encode).decoder(WorldPackets.NewsData::decode)
                .consumerMainThread(WorldPackets.NewsData::handle).add();

        // P9 : événements et intrigues
        CHANNEL.messageBuilder(IntriguePackets.OpenEvent.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntriguePackets.OpenEvent::encode).decoder(IntriguePackets.OpenEvent::decode)
                .consumerMainThread(IntriguePackets.OpenEvent::handle).add();
        CHANNEL.messageBuilder(IntriguePackets.OpenIntrigue.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntriguePackets.OpenIntrigue::encode).decoder(IntriguePackets.OpenIntrigue::decode)
                .consumerMainThread(IntriguePackets.OpenIntrigue::handle).add();
        CHANNEL.messageBuilder(IntriguePackets.RequestEvent.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IntriguePackets.RequestEvent::encode).decoder(IntriguePackets.RequestEvent::decode)
                .consumerMainThread(IntriguePackets.RequestEvent::handle).add();
        CHANNEL.messageBuilder(IntriguePackets.AnswerEvent.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IntriguePackets.AnswerEvent::encode).decoder(IntriguePackets.AnswerEvent::decode)
                .consumerMainThread(IntriguePackets.AnswerEvent::handle).add();
        CHANNEL.messageBuilder(IntriguePackets.IntrigueAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(IntriguePackets.IntrigueAction::encode).decoder(IntriguePackets.IntrigueAction::decode)
                .consumerMainThread(IntriguePackets.IntrigueAction::handle).add();
        CHANNEL.messageBuilder(IntriguePackets.ChronicleBook.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntriguePackets.ChronicleBook::encode).decoder(IntriguePackets.ChronicleBook::decode)
                .consumerMainThread(IntriguePackets.ChronicleBook::handle).add();

        // P10 : vie de cour
        CHANNEL.messageBuilder(CourtLifePackets.OpenTournament.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CourtLifePackets.OpenTournament::encode).decoder(CourtLifePackets.OpenTournament::decode)
                .consumerMainThread(CourtLifePackets.OpenTournament::handle).add();
        CHANNEL.messageBuilder(CourtLifePackets.OpenHouse.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CourtLifePackets.OpenHouse::encode).decoder(CourtLifePackets.OpenHouse::decode)
                .consumerMainThread(CourtLifePackets.OpenHouse::handle).add();
        CHANNEL.messageBuilder(CourtLifePackets.TournamentAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtLifePackets.TournamentAction::encode).decoder(CourtLifePackets.TournamentAction::decode)
                .consumerMainThread(CourtLifePackets.TournamentAction::handle).add();
        CHANNEL.messageBuilder(CourtLifePackets.HouseAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CourtLifePackets.HouseAction::encode).decoder(CourtLifePackets.HouseAction::decode)
                .consumerMainThread(CourtLifePackets.HouseAction::handle).add();
        // P14 : bâton de bornage, constructions
        CHANNEL.messageBuilder(WorldPackets.MapCenter.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldPackets.MapCenter::encode).decoder(WorldPackets.MapCenter::decode)
                .consumerMainThread(WorldPackets.MapCenter::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.SiteAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.SiteAction::encode).decoder(MapEditPackets.SiteAction::decode)
                .consumerMainThread(MapEditPackets.SiteAction::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.StaffPackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.StaffPackets.Action::encode).decoder(fr.crusadecraft.network.packet.StaffPackets.Action::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.StaffPackets.Action::handle).add();
        // P13 : registre des lignées
        CHANNEL.messageBuilder(LineagePackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LineagePackets.Open::encode).decoder(LineagePackets.Open::decode)
                .consumerMainThread(LineagePackets.Open::handle).add();
        CHANNEL.messageBuilder(LineagePackets.Result.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LineagePackets.Result::encode).decoder(LineagePackets.Result::decode)
                .consumerMainThread(LineagePackets.Result::handle).add();
        CHANNEL.messageBuilder(LineagePackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LineagePackets.Request::encode).decoder(LineagePackets.Request::decode)
                .consumerMainThread(LineagePackets.Request::handle).add();
        CHANNEL.messageBuilder(LineagePackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LineagePackets.Action::encode).decoder(LineagePackets.Action::decode)
                .consumerMainThread(LineagePackets.Action::handle).add();
        // P12 : éditeur de la carte
        CHANNEL.messageBuilder(MapEditPackets.CreateRealm.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.CreateRealm::encode).decoder(MapEditPackets.CreateRealm::decode)
                .consumerMainThread(MapEditPackets.CreateRealm::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.EditRealm.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.EditRealm::encode).decoder(MapEditPackets.EditRealm::decode)
                .consumerMainThread(MapEditPackets.EditRealm::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.DeleteRealm.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.DeleteRealm::encode).decoder(MapEditPackets.DeleteRealm::decode)
                .consumerMainThread(MapEditPackets.DeleteRealm::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.CreateCity.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.CreateCity::encode).decoder(MapEditPackets.CreateCity::decode)
                .consumerMainThread(MapEditPackets.CreateCity::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.CityAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.CityAction::encode).decoder(MapEditPackets.CityAction::decode)
                .consumerMainThread(MapEditPackets.CityAction::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.Paint.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.Paint::encode).decoder(MapEditPackets.Paint::decode)
                .consumerMainThread(MapEditPackets.Paint::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.Wipe.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.Wipe::encode).decoder(MapEditPackets.Wipe::decode)
                .consumerMainThread(MapEditPackets.Wipe::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.Result.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapEditPackets.Result::encode).decoder(MapEditPackets.Result::decode)
                .consumerMainThread(MapEditPackets.Result::handle).add();
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
