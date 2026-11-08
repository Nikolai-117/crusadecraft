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
    private static final String VERSION = "32";

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
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SettingsPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.SettingsPackets.Open::encode).decoder(fr.crusadecraft.network.packet.SettingsPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SettingsPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SettingsPackets.Result.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.SettingsPackets.Result::encode).decoder(fr.crusadecraft.network.packet.SettingsPackets.Result::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SettingsPackets.Result::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SettingsPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.SettingsPackets.Request::encode).decoder(fr.crusadecraft.network.packet.SettingsPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SettingsPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Request::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Catalog.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Catalog::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Catalog::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Catalog::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Entries.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Entries::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Entries::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Entries::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Ghost.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Ghost::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Ghost::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Ghost::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.GhostRequest.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.GhostRequest::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.GhostRequest::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.GhostRequest::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Place.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Place::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Place::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Place::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Rotate.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Rotate::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Rotate::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Rotate::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.ToggleFavorite.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.ToggleFavorite::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.ToggleFavorite::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.ToggleFavorite::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Favorites.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Favorites::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Favorites::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Favorites::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.DetailRequest.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.DetailRequest::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.DetailRequest::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.DetailRequest::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Detail.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Detail::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Detail::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Detail::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Progress.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Progress::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Progress::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Progress::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.PlanPackets.Choose.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.PlanPackets.Choose::encode).decoder(fr.crusadecraft.network.packet.PlanPackets.Choose::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.PlanPackets.Choose::handle).add();
        // P38 : le bâton d'intendant, les bâtiments des villes et leur fonction.
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.InhabitantPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.InhabitantPackets.Open::encode).decoder(fr.crusadecraft.network.packet.InhabitantPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.InhabitantPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.InhabitantPackets.Say.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.InhabitantPackets.Say::encode).decoder(fr.crusadecraft.network.packet.InhabitantPackets.Say::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.InhabitantPackets.Say::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.InhabitantPackets.OpenEgg.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.InhabitantPackets.OpenEgg::encode).decoder(fr.crusadecraft.network.packet.InhabitantPackets.OpenEgg::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.InhabitantPackets.OpenEgg::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.InhabitantPackets.ChooseEgg.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.InhabitantPackets.ChooseEgg::encode).decoder(fr.crusadecraft.network.packet.InhabitantPackets.ChooseEgg::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.InhabitantPackets.ChooseEgg::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SkinPackets.Upload.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.SkinPackets.Upload::encode).decoder(fr.crusadecraft.network.packet.SkinPackets.Upload::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SkinPackets.Upload::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SkinPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.SkinPackets.Request::encode).decoder(fr.crusadecraft.network.packet.SkinPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SkinPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SkinPackets.Library.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.SkinPackets.Library::encode).decoder(fr.crusadecraft.network.packet.SkinPackets.Library::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SkinPackets.Library::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SkinPackets.Data.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.SkinPackets.Data::encode).decoder(fr.crusadecraft.network.packet.SkinPackets.Data::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SkinPackets.Data::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.SkinPackets.Assign.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.SkinPackets.Assign::encode).decoder(fr.crusadecraft.network.packet.SkinPackets.Assign::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.SkinPackets.Assign::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NpcPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.NpcPackets.Open::encode).decoder(fr.crusadecraft.network.packet.NpcPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NpcPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NpcPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.NpcPackets.Request::encode).decoder(fr.crusadecraft.network.packet.NpcPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NpcPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NpcPackets.Edit.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.NpcPackets.Edit::encode).decoder(fr.crusadecraft.network.packet.NpcPackets.Edit::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NpcPackets.Edit::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPanelPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.CityPanelPackets.Open::encode).decoder(fr.crusadecraft.network.packet.CityPanelPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPanelPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPanelPackets.Edit.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.CityPanelPackets.Edit::encode).decoder(fr.crusadecraft.network.packet.CityPanelPackets.Edit::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPanelPackets.Edit::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPanelPackets.Live.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.CityPanelPackets.Live::encode).decoder(fr.crusadecraft.network.packet.CityPanelPackets.Live::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPanelPackets.Live::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPanelPackets.AskLive.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.CityPanelPackets.AskLive::encode).decoder(fr.crusadecraft.network.packet.CityPanelPackets.AskLive::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPanelPackets.AskLive::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.WorldEconomyPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Open::encode).decoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.WorldEconomyPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.WorldEconomyPackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Action::encode).decoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Action::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.WorldEconomyPackets.Action::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.WorldEconomyPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Request::encode).decoder(fr.crusadecraft.network.packet.WorldEconomyPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.WorldEconomyPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPostPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.CityPostPackets.Open::encode).decoder(fr.crusadecraft.network.packet.CityPostPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPostPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPostPackets.Act.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.CityPostPackets.Act::encode).decoder(fr.crusadecraft.network.packet.CityPostPackets.Act::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPostPackets.Act::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NationPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.NationPackets.Request::encode).decoder(fr.crusadecraft.network.packet.NationPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NationPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NationPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.NationPackets.Open::encode).decoder(fr.crusadecraft.network.packet.NationPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NationPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NationPackets.Act.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.NationPackets.Act::encode).decoder(fr.crusadecraft.network.packet.NationPackets.Act::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NationPackets.Act::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.OpenFound.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.OpenFound::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.OpenFound::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.OpenFound::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.Found.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.Found::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.Found::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.Found::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.OpenBuilding.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.OpenBuilding::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.OpenBuilding::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.OpenBuilding::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.Decide.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.Decide::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.Decide::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.Decide::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.OpenCity.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.OpenCity::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.OpenCity::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.OpenCity::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.CityAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.CityAction::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.CityAction::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.CityAction::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.OutlinesRequest.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.OutlinesRequest::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.OutlinesRequest::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.OutlinesRequest::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.BuildingPackets.Outlines.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.BuildingPackets.Outlines::encode).decoder(fr.crusadecraft.network.packet.BuildingPackets.Outlines::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.BuildingPackets.Outlines::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.SetStyle.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.SetStyle::encode).decoder(MapEditPackets.SetStyle::decode)
                .consumerMainThread(MapEditPackets.SetStyle::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.TownAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.TownAction::encode).decoder(MapEditPackets.TownAction::decode)
                .consumerMainThread(MapEditPackets.TownAction::handle).add();
        CHANNEL.messageBuilder(MapEditPackets.MergeRealm.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapEditPackets.MergeRealm::encode).decoder(MapEditPackets.MergeRealm::decode)
                .consumerMainThread(MapEditPackets.MergeRealm::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.NamePackets.Rename.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.NamePackets.Rename::encode).decoder(fr.crusadecraft.network.packet.NamePackets.Rename::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.NamePackets.Rename::handle).add();
        CHANNEL.messageBuilder(LineagePackets.OpenMcaTree.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LineagePackets.OpenMcaTree::encode).decoder(LineagePackets.OpenMcaTree::decode)
                .consumerMainThread(LineagePackets.OpenMcaTree::handle).add();
        // P17 : les terres pour les cartes du client (Xaero).
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.TerritorySyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.TerritorySyncPacket::encode).decoder(fr.crusadecraft.network.packet.TerritorySyncPacket::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.TerritorySyncPacket::handle).add();
        // P15 : les lieux.
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.LieuPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.LieuPackets.Open::encode).decoder(fr.crusadecraft.network.packet.LieuPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.LieuPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.LieuPackets.OpenForm.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.LieuPackets.OpenForm::encode).decoder(fr.crusadecraft.network.packet.LieuPackets.OpenForm::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.LieuPackets.OpenForm::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.LieuPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.LieuPackets.Request::encode).decoder(fr.crusadecraft.network.packet.LieuPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.LieuPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.LieuPackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.LieuPackets.Action::encode).decoder(fr.crusadecraft.network.packet.LieuPackets.Action::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.LieuPackets.Action::handle).add();
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
        // P18 : la carte de guerre
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.WarMapPackets.Edit.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.WarMapPackets.Edit::encode).decoder(fr.crusadecraft.network.packet.WarMapPackets.Edit::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.WarMapPackets.Edit::handle).add();
        // P19 : les villes du royaume
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.CityPackets.Request::encode).decoder(fr.crusadecraft.network.packet.CityPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPackets.Open.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.CityPackets.Open::encode).decoder(fr.crusadecraft.network.packet.CityPackets.Open::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPackets.Open::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.CityPackets.Action.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.CityPackets.Action::encode).decoder(fr.crusadecraft.network.packet.CityPackets.Action::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.CityPackets.Action::handle).add();
        // P18b : la carte en terrain
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.TerrainPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.TerrainPackets.Request::encode).decoder(fr.crusadecraft.network.packet.TerrainPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.TerrainPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.TerrainPackets.Tile.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.TerrainPackets.Tile::encode).decoder(fr.crusadecraft.network.packet.TerrainPackets.Tile::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.TerrainPackets.Tile::handle).add();
        // P60 : l'atelier des villes
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.UrbanPackets.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(fr.crusadecraft.network.packet.UrbanPackets.Request::encode).decoder(fr.crusadecraft.network.packet.UrbanPackets.Request::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.UrbanPackets.Request::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.UrbanPackets.Preview.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.UrbanPackets.Preview::encode).decoder(fr.crusadecraft.network.packet.UrbanPackets.Preview::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.UrbanPackets.Preview::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.UrbanPackets.Saved.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.UrbanPackets.Saved::encode).decoder(fr.crusadecraft.network.packet.UrbanPackets.Saved::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.UrbanPackets.Saved::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.UrbanPackets.Loaded.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.UrbanPackets.Loaded::encode).decoder(fr.crusadecraft.network.packet.UrbanPackets.Loaded::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.UrbanPackets.Loaded::handle).add();
        CHANNEL.messageBuilder(fr.crusadecraft.network.packet.UrbanPackets.Taken.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(fr.crusadecraft.network.packet.UrbanPackets.Taken::encode).decoder(fr.crusadecraft.network.packet.UrbanPackets.Taken::decode)
                .consumerMainThread(fr.crusadecraft.network.packet.UrbanPackets.Taken::handle).add();
    }

    public static void toPlayer(ServerPlayer player, Object packet) {
        // Un joueur fictif (machines, tests) n'a pas de client à qui parler.
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void toAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /** Vers le serveur ; sans connexion (écran de titre, banc d'essai visuel), la demande est simplement perdue. */
    public static void toServer(Object packet) {
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() && !connected()) return;
        CHANNEL.sendToServer(packet);
    }

    private static boolean connected() {
        return net.minecraft.client.Minecraft.getInstance().getConnection() != null;
    }
}
