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
        // La carte de l'éditeur montre tout le monde IA : elle ne remplace pas celle de la Table de guerre.
        if (!view.editing()) fr.crusadecraft.client.map.ClientMapData.set(view);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen screen) screen.refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.WarPinForm form) form.parent().refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.StaffScreen staff) staff.setMap(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.MapScreen(view));
    }

    /** Le menu du bâton de bornage (clic droit dans le vide). */
    /** P34 : le catalogue du plan d'architecte (le serveur répond par ses étagères). */
    public static void requestPlanCatalog() {
        fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.PlanPackets.Request(""));
    }

    public static void planCatalog(java.util.List<fr.crusadecraft.core.build.plan.PlanLibrary.Source> sources, String folder) {
        Minecraft.getInstance().setScreen(new fr.crusadecraft.client.screen.PlanCatalogScreen(sources, folder));
    }

    /** P39 : les lots du fantôme en cours d'arrivée. */
    private static String ghostRef = "";
    private static final it.unimi.dsi.fastutil.ints.IntArrayList GHOST_POS = new it.unimi.dsi.fastutil.ints.IntArrayList(),
            GHOST_IDS = new it.unimi.dsi.fastutil.ints.IntArrayList();

    public static void planGhost(String ref, int part, int total, int[] positions, int[] states) {
        if (part == 0 || !ref.equals(ghostRef)) {
            ghostRef = ref;
            GHOST_POS.clear();
            GHOST_IDS.clear();
        }
        GHOST_POS.addElements(GHOST_POS.size(), positions);
        GHOST_IDS.addElements(GHOST_IDS.size(), states);
        if (part < total - 1) return;
        int[] pos = GHOST_POS.toIntArray(), ids = GHOST_IDS.toIntArray();
        GHOST_POS.clear();
        GHOST_IDS.clear();
        GHOST_POS.trim();
        GHOST_IDS.trim();
        net.minecraft.world.level.block.state.BlockState[] st = new net.minecraft.world.level.block.state.BlockState[ids.length];
        for (int i = 0; i < ids.length; i++) st[i] = net.minecraft.world.level.block.Block.stateById(ids[i]);
        fr.crusadecraft.client.map.GhostRenderer.set(ref, pos, st);
    }

    /** P39.2 : le clic droit avec le plan d'architecte : figer le fantôme et ouvrir le panneau de placement. */
    public static boolean openPlacement() {
        return fr.crusadecraft.client.map.PlanControl.openPanel(Minecraft.getInstance());
    }

    /** Le clic droit avec le plan d'architecte : poser là où est son fantôme. */
    public static boolean placePlan() {
        return fr.crusadecraft.client.map.PlanControl.place(Minecraft.getInstance());
    }

    public static void planFavorites(java.util.List<String> refs) {
        fr.crusadecraft.client.screen.PlanCatalogScreen.setFavorites(refs);
    }

    public static void planDetail(fr.crusadecraft.core.build.plan.PlanTool.Detail detail) {
        if (Minecraft.getInstance().screen instanceof fr.crusadecraft.client.screen.PlanCatalogScreen screen) screen.showDetail(detail);
    }

    public static void planEntries(String source, java.util.List<fr.crusadecraft.core.build.plan.PlanLibrary.Entry> entries) {
        if (Minecraft.getInstance().screen instanceof fr.crusadecraft.client.screen.PlanCatalogScreen screen) screen.showEntries(source, entries);
    }

    public static void openStaffMenu() {
        Minecraft.getInstance().setScreen(new fr.crusadecraft.client.screen.StaffScreen());
    }

    /** Les lieux (P15) : rafraîchit l'écran ouvert, ou l'ouvre. */
    public static void lieux(fr.crusadecraft.core.place.LieuView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        // Écran déjà ouvert (ou un de ses formulaires) : on le rafraîchit sans perdre où l'on en était dans la liste.
        if (mc.screen instanceof fr.crusadecraft.client.screen.LieuxScreen screen) screen.refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.LieuForm form && form.parent() != null) form.parent().refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.LieuTextScreen text) text.parent().refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.LieuxScreen(view));
    }

    /** Les villes du royaume (P19) : rafraîchit l'écran ouvert, ou l'ouvre. */
    public static void cities(fr.crusadecraft.core.realm.CityView view, boolean open, net.minecraft.network.chat.Component message, boolean success) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.CitiesScreen screen) screen.refresh(view, message, success);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.CitiesScreen(view));
        else if (message != null && mc.player != null) mc.player.displayClientMessage(message, true);
    }

    /** Le bâton a visé le sol en mode « Lieu » : on nomme le lieu à poser. */
    public static void lieuForm(fr.crusadecraft.core.place.LieuKind kind, net.minecraft.core.BlockPos pos) {
        Minecraft.getInstance().setScreen(fr.crusadecraft.client.screen.LieuForm.create(null, kind, pos, null));
    }

    /** Le registre des lignées : rafraîchit l'écran ouvert, ou l'ouvre. */
    public static void lineage(fr.crusadecraft.core.lineage.LineageView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.LineageScreen screen) screen.refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.LineageForm form) form.parent().refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.PersonForm form) form.parent().refresh(view);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.TreeScreen tree) tree.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.LineageScreen(view));
    }

    /** Les réglages du monde (P30) : rafraîchit l'écran ouvert, ou l'ouvre. */
    public static void settings(fr.crusadecraft.config.SettingsService.View view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.SettingsScreen screen) screen.refresh(view);
        else if (open) mc.setScreen(new fr.crusadecraft.client.screen.SettingsScreen(view));
    }

    public static void settingsResult(boolean success, net.minecraft.network.chat.Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.SettingsScreen screen) screen.showStatus(success, message);
        else if (mc.player != null) mc.player.displayClientMessage(message, true);
    }

    /** L'arbre de Comes Alive (P26), ouvert sur une personne du registre. */
    public static void mcaTree(java.util.UUID person) {
        fr.crusadecraft.compat.mca.McaBridge.openTreeScreen(person);
    }

    public static void lineageResult(boolean success, net.minecraft.network.chat.Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.LineageScreen screen) screen.showStatus(success, message);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.LineageForm form) form.parent().showStatus(success, message);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.PersonForm form) form.parent().showStatus(success, message);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.TreeScreen tree) tree.showStatus(success, message);
        else if (mc.player != null) mc.player.displayClientMessage(message, true);
    }

    /** Une tuile de la carte en terrain (P18b) : la carte ouverte se redessinera d'elle-même. */
    public static void terrainTile(net.minecraft.resources.ResourceLocation dimension, fr.crusadecraft.core.world.terrain.TerrainTile tile) {
        fr.crusadecraft.client.map.ClientTerrain.put(dimension, tile);
    }

    /** La réponse du serveur à une retouche de l'éditeur : au pied de la carte si elle est ouverte. */
    public static void mapEditResult(boolean success, net.minecraft.network.chat.Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen screen) screen.showStatus(success, message);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.WarPinForm form) form.parent().showStatus(success, message);
        else if (mc.screen instanceof fr.crusadecraft.client.screen.MapEditForm form) form.showStatus(success, message);
        else if (mc.player != null) mc.player.displayClientMessage(message, true);
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
