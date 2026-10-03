package fr.crusadecraft.client;

import fr.crusadecraft.core.world.AiRealmView;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.client.screen.TreasuryScreen;
import fr.crusadecraft.core.law.LawsView;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.client.screen.CharacterScreen;
import fr.crusadecraft.client.screen.CouncilScreen;
import fr.crusadecraft.client.screen.MayorScreen;
import fr.crusadecraft.client.screen.RequestScreen;
import fr.crusadecraft.client.screen.DialogueScreen;
import fr.crusadecraft.client.screen.FoundingScreen;
import fr.crusadecraft.client.screen.RealmScreen;
import fr.crusadecraft.client.screen.RegistreScreen;
import fr.crusadecraft.client.toast.CrusadeToast;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.core.council.MayorView;
import fr.crusadecraft.core.council.RequestView;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Réactions du client aux paquets du serveur. Classe chargée uniquement côté client
 * (appelée à travers {@code DistExecutor}).
 */
public final class ClientPackets {

    private ClientPackets() {}

    public static void showToast(ToastPacket.Kind kind, Component title, Component body) {
        if (!CrusadeConfig.CLIENT.showToasts.get()) return;
        Minecraft.getInstance().getToasts().addToast(new CrusadeToast(kind, title, body));
    }

    public static void openFounding(BlockPos thronePos, ColonyInfo colony, String playerName) {
        Minecraft.getInstance().setScreen(new FoundingScreen(thronePos, colony, playerName));
    }

    public static void syncTitles(List<SyncTitlesPacket.Entry> entries) {
        ClientTitles.replace(entries);
    }

    public static void openCharacter(CharacterView view, RequestCharacterPacket.Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == RequestCharacterPacket.Mode.DIALOGUE) {
            mc.setScreen(new DialogueScreen(view));
        } else if (mc.screen instanceof CharacterScreen open && open.isFor(view)) {
            open.refresh(view);
        } else {
            mc.setScreen(new CharacterScreen(view, mode == RequestCharacterPacket.Mode.APPOINT));
        }
    }

    public static void openCouncil(CouncilView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CouncilScreen open) open.refresh(view);
        else mc.setScreen(new CouncilScreen(view));
    }

    public static void openMayor(MayorView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MayorScreen open && open.isFor(view)) open.refresh(view);
        else mc.setScreen(new MayorScreen(view));
    }

    public static void openRequest(RequestView view) {
        Minecraft.getInstance().setScreen(new RequestScreen(view));
    }

    public static void openRealm(RealmView view, OpenRealmScreenPacket.Target target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == OpenRealmScreenPacket.Target.REGISTRE) {
            mc.setScreen(new RegistreScreen(view));
        } else if (mc.screen instanceof RealmScreen open && open.isFor(view)) {
            open.refresh(view);
        } else {
            mc.setScreen(new RealmScreen(view));
        }
    }

    public static void treasury(TreasuryView view, BlockPos chest) {
        Minecraft mc = Minecraft.getInstance();
        if (chest != null) {
            if (mc.screen instanceof TreasuryScreen open && open.isFor(chest)) open.refresh(view);
            else mc.setScreen(new TreasuryScreen(view, chest));
        } else if (mc.screen instanceof RealmScreen open) {
            open.setTreasury(view);
        }
    }

    public static void laws(LawsView view) {
        if (Minecraft.getInstance().screen instanceof RealmScreen open) open.setLaws(view);
    }

    public static void map(MapView view, boolean open) {
        fr.crusadecraft.client.map.ClientMapData.set(view);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen screen) screen.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.MapScreen(view));
    }

    public static void aiRealm(AiRealmView view) {
        Minecraft.getInstance().setScreen(new fr.crusadecraft.client.screen.AiRealmScreen(view));
    }

    public static void diplomacy(fr.crusadecraft.core.diplomacy.DiplomacyView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.DiplomacyScreen screen) screen.refresh(view);
        else if (mc.screen instanceof RealmScreen realm && !open) realm.setDiplomacy(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.DiplomacyScreen(view));
    }

    public static void chancery(fr.crusadecraft.core.diplomacy.ChanceryView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.ChanceryScreen screen) screen.refresh(view);
        else if (mc.screen instanceof RealmScreen realm && !open) realm.setChancery(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.ChanceryScreen(view));
    }

    public static void news(java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> lines,
                            java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> chronicle) {
        if (Minecraft.getInstance().screen instanceof RealmScreen realm) realm.setNews(lines, chronicle);
    }

    /** Un événement à décider ; {@code null} : plus rien n'attend, l'écran se ferme. */
    public static void event(fr.crusadecraft.core.event.EventView view) {
        Minecraft mc = Minecraft.getInstance();
        if (view == null) {
            if (mc.screen instanceof fr.crusadecraft.client.screen.EventScreen) mc.setScreen(null);
            return;
        }
        mc.setScreen(new fr.crusadecraft.client.screen.EventScreen(view));
    }

    public static void tournament(fr.crusadecraft.core.court.TournamentView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.TournamentScreen screen) screen.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.TournamentScreen(view));
    }

    public static void house(fr.crusadecraft.core.court.HouseView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.HouseScreen screen) screen.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.HouseScreen(view));
    }

    public static void chronicleBook(String realm, String house, java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> lines) {
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.BookViewScreen(
                new fr.crusadecraft.client.screen.ChronicleBook(realm, house, lines)));
    }

    public static void intrigue(fr.crusadecraft.core.intrigue.IntrigueView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.IntrigueScreen screen) screen.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.IntrigueScreen(view));
    }

    public static void war(fr.crusadecraft.core.war.WarView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.WarScreen screen) screen.refresh(view);
        else if (mc.screen instanceof RealmScreen realm && !open) realm.setWar(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.WarScreen(view));
    }

    public static void openLetter(net.minecraft.world.item.ItemStack stack) {
        Minecraft.getInstance().setScreen(new fr.crusadecraft.client.screen.LetterScreen(fr.crusadecraft.item.LetterItem.data(stack).copy()));
    }
}
