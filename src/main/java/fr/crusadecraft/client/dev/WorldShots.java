package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.block.WarMapBlock;
import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import fr.crusadecraft.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Banc d'essai en monde réel, réservé au développement ({@code ./gradlew runWorldShots}) : crée un monde
 * plat jetable dans {@code run-worldshots/saves}, y accroche une Table de guerre, génère et révèle les
 * royaumes IA, puis photographie le mur, la carte, une fiche et la venue d'un héraut. Le jeu se ferme ensuite.
 * Rien d'autre que ce dossier de développement n'est touché.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorldShots {
    private static final boolean ENABLED = Boolean.getBoolean("crusadecraft.worldshots");
    private static final String WORLD = "crusadecraft-shots";

    private enum Step { V9_MAP, V9_ZOOM, V9_HUB, V9_CHANCERY, V9_TABLE, V8, COPY_LOADING, COPY, BOOT, LOADING, SETUP, WALL, MAP, SHEET, HERALD, REALM, LETTER, SEAL, CHANCERY, COLOMBIER, TOWNHALL, TOWNHALL_CLICK, WAR, WAR_MAP_OPEN, WAR_MAP, WAR_MAP_SHOT, SIEGE, CAPTURE, CAPTURED, WAR_TABLE, RAID, EDITOR, EDITOR_REALM, EDITOR_PAINT, LINEAGE_OPEN, LINEAGE_HOUSE, LINEAGE_SPAWN, LINEAGE_SHOT, STAFF_MENU, STAFF_SHOT, FORT_SHOT, SITES_MAP, PAPER_SHOT, LIEU_FORM, LIEU_SCREEN, LIEU_MAP, CHAT_MAP, WALK_IN, TITLE_SHOT, XAERO_SHOT, TERRAIN_OPEN, TERRAIN_WAIT, TERRAIN_SHOT, TERRAIN_POLITICAL, V3, V4, V5, V6, V7, DONE }

    private static Step step = Step.BOOT;
    private static int wait;
    private static int shot;
    private static boolean held;

    private WorldShots() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // La fenêtre peut perdre le focus pendant les prises : le menu pause ne doit pas masquer les captures.
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        // Comes Alive demande au joueur qui il est à son premier monde : le banc d'essai passe cet écran.
        if (mc.screen != null && mc.screen.getClass().getName().startsWith("forge.net.mca")) mc.setScreen(null);
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case BOOT -> {
                if (mc.screen == null || mc.level != null || mc.getOverlay() != null) return;
                // -Pshots_open=dossier : une copie d'un monde, qu'on ne recrée surtout pas.
                String copy = System.getProperty("crusadecraft.worldshots.open", "");
                if (!copy.isEmpty()) {
                    CrusadeCraft.LOGGER.info("WorldShots : ouverture de la copie {}", copy);
                    mc.createWorldOpenFlows().loadLevel(mc.screen, copy);
                    step = Step.COPY_LOADING;
                    wait = 20;
                    return;
                }
                createWorld(mc);
                step = Step.LOADING;
            }
            case COPY_LOADING -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                    step = Step.COPY;
                    wait = 200;
                    return;
                }
                WorldShotsCopy.answer(mc);
                wait = 20;
            }
            case COPY -> {
                if (WorldShotsCopy.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case V9_MAP -> {
                grab(mc, "p63_carte");
                // De près, autour de la capitale : là où la carte de Xaero a ses détails.
                if (mc.screen != null) {
                    double cx = mc.getWindow().getGuiScaledWidth() / 2.0, cy = mc.getWindow().getGuiScaledHeight() / 2.0;
                    for (int i = 0; i < 6; i++) mc.screen.mouseScrolled(cx, cy, 1.0);
                }
                step = Step.V9_ZOOM;
                wait = 160;
            }
            case V9_ZOOM -> {
                grab(mc, "p63_carte_pres");
                mc.setScreen(null);
                CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.RequestRealmPacket(fr.crusadecraft.network.packet.OpenRealmScreenPacket.Target.MENU));
                step = Step.V9_HUB;
                wait = 160;
            }
            case V9_HUB -> {
                grab(mc, "p63_royaume");
                if (mc.screen != null) mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_4, 0, 0);
                step = Step.V9_CHANCERY;
                wait = 160;
            }
            case V9_CHANCERY -> {
                grab(mc, "p63_chancellerie");
                mc.setScreen(null);
                step = Step.V9_TABLE;
                wait = 200;
            }
            case V9_TABLE -> {
                grab(mc, "p63_table_apres");
                step = Step.DONE;
            }
            case V8 -> {
                if (WorldShotsV8.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case LOADING -> {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                step = Step.SETUP;
                wait = 100;
            }
            case SETUP -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> setup(server));
                step = Step.WALL;
                wait = 160;
            }
            case WALL -> {
                // -Pshots_v9=true (P63) : les cartes sur la carte de Xaero : la Table de guerre, la carte, le menu royal.
                if (Boolean.getBoolean("crusadecraft.worldshots.v9")) {
                    grab(mc, "p63_table");
                    MinecraftServer server = mc.getSingleplayerServer();
                    server.execute(() -> foundForMaps(server));
                    CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                    step = Step.V9_MAP;
                    wait = 200;
                    return;
                }
                // -Pshots_v8=true : l'atelier des villes (phase 60).
                if (Boolean.getBoolean("crusadecraft.worldshots.v8")) {
                    step = Step.V8;
                    return;
                }
                // -Pshots_v3=true : le scénario des phases 26 à 30 à la place du reste.
                if (Boolean.getBoolean("crusadecraft.worldshots.v3")) {
                    step = Step.V3;
                    return;
                }
                // -Pshots_v7=true : le panneau, l'éditeur, les skins, les voyages, le courrier (phases 46 à 49).
                if (Boolean.getBoolean("crusadecraft.worldshots.v7")) {
                    step = Step.V7;
                    return;
                }
                // -Pshots_v6=true : les nations, les habitants et leur journée (phases 41 à 43).
                if (Boolean.getBoolean("crusadecraft.worldshots.v6")) {
                    step = Step.V6;
                    return;
                }
                // -Pshots_v5=true : les villes importées de la phase 38 et le bâton d'intendant.
                if (Boolean.getBoolean("crusadecraft.worldshots.v5")) {
                    step = Step.V5;
                    return;
                }
                // -Pshots_v4=true : les villes de la phase 34, une par style.
                if (Boolean.getBoolean("crusadecraft.worldshots.v4")) {
                    step = Step.V4;
                    return;
                }
                grab(mc, "table_de_guerre");
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                step = Step.MAP;
                wait = 40;
            }
            case MAP -> {
                grab(mc, "carte");
                MapView view = ClientMapData.view();
                MapView.Territory ai = view == null ? null : view.territories().stream().filter(MapView.Territory::ai).findFirst().orElse(null);
                if (ai != null) CrusadeNetwork.toServer(new WorldPackets.RequestAiRealm(ai.id()));
                step = Step.SHEET;
                wait = 30;
            }
            case SHEET -> {
                grab(mc, "fiche");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> visitHiddenRealm(server));
                step = Step.HERALD;
                wait = 200;
            }
            case HERALD -> {
                grab(mc, "heraut");
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> foundAndWrite(server));
                step = Step.REALM;
                wait = 80;
            }
            case REALM -> {
                // La lettre d'hommage est dans l'inventaire : on la lit comme un joueur.
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var stack = inv.getItem(i);
                    if (stack.is(fr.crusadecraft.registry.ModItems.LETTER.get()) && fr.crusadecraft.item.LetterItem.awaitsAnswer(stack)) {
                        fr.crusadecraft.client.ClientPackets.openLetter(stack);
                        break;
                    }
                }
                step = Step.LETTER;
                wait = 20;
            }
            case LETTER -> {
                grab(mc, "lettre_reelle");
                var data = mc.screen instanceof fr.crusadecraft.client.screen.LetterScreen ? findLetter(mc) : null;
                if (data != null) CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.DiplomacyPackets.AnswerLetter(data, 0));
                mc.setScreen(null);
                step = Step.SEAL;
                wait = 30;
            }
            case SEAL -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    CrusadeNetwork.toPlayer(player, new fr.crusadecraft.network.packet.DiplomacyPackets.OpenChancery(
                            fr.crusadecraft.core.diplomacy.TitleService.view(player), true));
                });
                step = Step.CHANCERY;
                wait = 30;
            }
            case CHANCERY -> {
                grab(mc, "chancellerie_reelle");
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    CrusadeNetwork.toPlayer(player, new fr.crusadecraft.network.packet.DiplomacyPackets.OpenDiplomacy(
                            fr.crusadecraft.core.diplomacy.DiplomacyService.view(player), true));
                });
                step = Step.COLOMBIER;
                wait = 30;
            }
            case COLOMBIER -> {
                grab(mc, "colombier_reel");
                mc.setScreen(null);
                // La fenêtre de l'Hôtel de ville de MineColonies, avec le bouton CrusadeCraft.
                var realm = fr.crusadecraft.core.realm.RealmService.realmOf(mc.getSingleplayerServer(), mc.player.getUUID());
                boolean opened = realm.isPresent() && fr.crusadecraft.compat.minecolonies.MineColoniesClientKit.openTownHall(realm.get().capital());
                CrusadeCraft.LOGGER.info("WorldShots : fenêtre de l'Hôtel de ville {}", opened ? "ouverte" : "introuvable");
                step = Step.TOWNHALL;
                wait = 40;
            }
            case TOWNHALL -> {
                if (mc.screen != null) {
                    StringBuilder chain = new StringBuilder();
                    for (Class<?> c = mc.screen.getClass(); c != null; c = c.getSuperclass()) chain.append(c.getName()).append(" < ");
                    CrusadeCraft.LOGGER.info("WorldShots : écran {} ({} x {}) ; Hôtel de ville reconnu : {}", chain, mc.screen.width,
                            mc.screen.height, fr.crusadecraft.compat.minecolonies.TownHallButton.isTownHall(mc.screen));
                }
                grab(mc, "hotel_de_ville");
                // Un clic simulé sur le bouton « Mon royaume » : le menu royal doit s'ouvrir.
                if (mc.screen != null) {
                    double cx = mc.getWindow().getGuiScaledWidth() - 6 - 52, cy = 15;
                    boolean handled = net.minecraftforge.client.ForgeHooksClient.onScreenMouseClickedPre(mc.screen, cx, cy, 0);
                    CrusadeCraft.LOGGER.info("WorldShots : clic sur le bouton de l'Hôtel de ville {}", handled ? "intercepté" : "ignoré");
                }
                step = Step.TOWNHALL_CLICK;
                wait = 30;
            }
            case TOWNHALL_CLICK -> {
                CrusadeCraft.LOGGER.info("WorldShots : après le clic, écran {}", mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName());
                grab(mc, "menu_depuis_hotel");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> declareWar(server));
                step = Step.WAR;
                wait = 90;
            }
            case WAR -> {
                // Le siège a commencé : garnison et bannière devant le joueur.
                grab(mc, "siege");
                // La carte de guerre (P18) : le souverain trace son plan pendant le siège.
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> drawWarPlan(server));
                step = Step.WAR_MAP_OPEN;
                wait = 20;
            }
            case WAR_MAP_OPEN -> {
                int[] v = warView;
                if (v != null) fr.crusadecraft.client.screen.MapScreen.centerNextOn(v[0], v[1], v[2]);
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                step = Step.WAR_MAP;
                wait = 40;
            }
            case WAR_MAP -> {
                if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m) m.previewWar(4);
                MapView view = mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m ? m.currentView() : null;
                CrusadeCraft.LOGGER.info("WorldShots : carte de guerre — {} plan(s), {} siège(s)", view == null ? 0 : view.war().plans().size(),
                        view == null ? 0 : view.war().sieges().size());
                step = Step.WAR_MAP_SHOT;
                wait = 10;
            }
            case WAR_MAP_SHOT -> {
                grab(mc, "carte_guerre");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> stormBanner(server));
                step = Step.SIEGE;
                wait = 110;
            }
            case SIEGE -> {
                grab(mc, "prise_banniere");
                step = Step.CAPTURE;
                wait = 130;
            }
            case CAPTURE -> {
                grab(mc, "place_prise");
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> fr.crusadecraft.network.packet.WarPackets.sendWar(server.getPlayerList().getPlayers().get(0), true));
                step = Step.CAPTURED;
                wait = 30;
            }
            case CAPTURED -> {
                grab(mc, "table_guerre_reelle");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> callRaid(server));
                step = Step.WAR_TABLE;
                wait = 90;
            }
            case WAR_TABLE -> {
                grab(mc, "raid");
                // L'éditeur de la carte (P12), par les vrais paquets : l'hôte de la partie solo y a droit sans réglage.
                fr.crusadecraft.client.screen.MapScreen.centerNextOn(mc.player.getBlockX() + 260, mc.player.getBlockZ() + 120);
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true, true));
                step = Step.EDITOR;
                wait = 40;
            }
            case EDITOR -> {
                boolean editing = mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m && m.currentView().editing();
                CrusadeCraft.LOGGER.info("WorldShots : éditeur de la carte {}", editing ? "ouvert" : "refusé");
                CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.MapEditPackets.CreateRealm("Bois-Joli", "Aymeric",
                        TitleRank.DUCHE.ordinal(), 12, 1, 0, mc.player.getBlockX() + 320, mc.player.getBlockZ() + 140));
                step = Step.EDITOR_REALM;
                wait = 30;
            }
            case EDITOR_REALM -> {
                MapView view = mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m ? m.currentView() : null;
                MapView.Territory made = view == null ? null
                        : view.territories().stream().filter(t -> t.label().equals("Bois-Joli")).findFirst().orElse(null);
                CrusadeCraft.LOGGER.info("WorldShots : royaume posé à la main {}", made != null ? "reçu sur la carte" : "absent");
                if (made != null) {
                    int px = mc.player.getBlockX(), pz = mc.player.getBlockZ();
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.MapEditPackets.CreateCity(made.id(), "Clairfontaine", 1, px + 420, pz + 60));
                    java.util.List<Long> cells = new java.util.ArrayList<>();
                    for (int cx = (px + 180) >> 4; cx <= (px + 470) >> 4; cx++) {
                        for (int cz = (pz + 20) >> 4; cz <= (pz + 230) >> 4; cz++) cells.add(fr.crusadecraft.core.world.TerritoryMap.key(cx, cz));
                    }
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.MapEditPackets.Paint(made.id(), false,
                            cells.stream().mapToLong(Long::longValue).toArray()));
                }
                step = Step.EDITOR_PAINT;
                wait = 30;
            }
            case EDITOR_PAINT -> {
                var data = fr.crusadecraft.data.CrusadeSavedData.get(mc.getSingleplayerServer());
                var made = data.world().realms().stream().filter(r -> r.place().equals("Bois-Joli")).findFirst();
                CrusadeCraft.LOGGER.info("WorldShots : côté serveur, Bois-Joli {} avec {} province(s), {} case(s) peinte(s)",
                        made.isPresent() ? "existe" : "manque", made.map(r -> r.provinces().size()).orElse(0), data.world().borders().size());
                grab(mc, "editeur_carte");
                // Le registre des lignées (P13), par les vrais paquets, avec Minecraft Comes Alive.
                mc.setScreen(null);
                CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LineagePackets.Request(null, null));
                step = Step.LINEAGE_OPEN;
                wait = 20;
            }
            case LINEAGE_OPEN -> {
                CrusadeCraft.LOGGER.info("WorldShots : registre des lignées {}", mc.screen instanceof fr.crusadecraft.client.screen.LineageScreen ? "ouvert" : "absent");
                CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LineagePackets.Action(
                        fr.crusadecraft.network.packet.LineagePackets.Action.Op.NEW_HOUSE, null, "Aubépine", 2, 3, null, null));
                step = Step.LINEAGE_HOUSE;
                wait = 20;
            }
            case LINEAGE_HOUSE -> {
                grab(mc, "registre_reel");
                var data = fr.crusadecraft.data.CrusadeSavedData.get(mc.getSingleplayerServer());
                var house = data.lineage().houses().stream().filter(x -> x.name().equals("Aubépine")).findFirst();
                CrusadeCraft.LOGGER.info("WorldShots : maison Aubépine {} ({} membres)", house.isPresent() ? "générée" : "absente",
                        house.map(x -> data.lineage().members(x.id()).size()).orElse(0));
                house.ifPresent(x -> CrusadeNetwork.toServer(fr.crusadecraft.network.packet.LineagePackets.Action.of(
                        fr.crusadecraft.network.packet.LineagePackets.Action.Op.SPAWN, x.id(), x.id())));
                step = Step.LINEAGE_SPAWN;
                wait = 30;
            }
            case LINEAGE_SPAWN -> {
                mc.setScreen(null);
                mc.player.setXRot(18f);
                step = Step.LINEAGE_SHOT;
                wait = 60;
            }
            case LINEAGE_SHOT -> {
                MinecraftServer server = mc.getSingleplayerServer();
                var data = fr.crusadecraft.data.CrusadeSavedData.get(server);
                long incarnated = data.lineage().persons().stream().filter(p -> p.inWorld()).count();
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                long tagged = sp.serverLevel().getEntities(sp, sp.getBoundingBox().inflate(16), e -> e.getTags().contains(
                        fr.crusadecraft.compat.mca.McaBridge.TAG)).size();
                CrusadeCraft.LOGGER.info("WorldShots : {} personne(s) incarnée(s), {} villageois Comes Alive marqués autour du joueur", incarnated, tagged);
                grab(mc, "famille_comes_alive");
                // Le bâton de bornage (P14) : en main, au nom du royaume du joueur.
                server.execute(() -> giveStaff(server));
                step = Step.STAFF_MENU;
                wait = 30;
            }
            case STAFF_MENU -> {
                // La case tenue est choisie par le client : le bâton est posé dans la première.
                mc.player.getInventory().selected = 0;
                CrusadeCraft.LOGGER.info("WorldShots : bâton en main {}",
                        fr.crusadecraft.item.BornageStaffItem.held(mc.player).isPresent() ? "oui" : "non");
                mc.setScreen(new fr.crusadecraft.client.screen.StaffScreen());
                step = Step.STAFF_SHOT;
                wait = 30;
            }
            case STAFF_SHOT -> {
                grab(mc, "baton_de_bornage");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> buildFort(server));
                step = Step.FORT_SHOT;
                wait = 120;
            }
            case FORT_SHOT -> {
                var data = fr.crusadecraft.data.CrusadeSavedData.get(mc.getSingleplayerServer());
                var fort = data.world().sites().stream().filter(x -> x.kind() == fr.crusadecraft.core.build.SiteKind.FORT).findFirst();
                CrusadeCraft.LOGGER.info("WorldShots : fort {} ({} %)", fort.isPresent() ? "posé" : "absent",
                        fort.map(fr.crusadecraft.core.build.SiteService::percent).orElse(0));
                grab(mc, "fort");
                fort.ifPresent(f -> fr.crusadecraft.client.screen.MapScreen.centerNextOn(f.anchor().getX(), f.anchor().getZ() - 60, 3));
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                step = Step.SITES_MAP;
                wait = 40;
            }
            case SITES_MAP -> {
                grab(mc, "carte_constructions");
                mc.setScreen(null);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> givePaperMap(server));
                step = Step.PAPER_SHOT;
                wait = 120;
            }
            case PAPER_SHOT -> {
                // Ni chat ni notification par-dessus la carte tenue en main.
                mc.gui.getChat().clearMessages(false);
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
                mc.options.broadcastOptions();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToasts().clear();
                grab(mc, "carte_papier");
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
                // Le serveur filtre les messages selon ce réglage : on le lui redit, sinon le chat resterait muet.
                mc.options.broadcastOptions();
                // Les lieux (P15) : un lieu saint posé au bâton, en mode « Lieu ».
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> placeShrine(server));
                step = Step.LIEU_FORM;
                wait = 30;
            }
            case LIEU_FORM -> {
                CrusadeCraft.LOGGER.info("WorldShots : formulaire du lieu {}",
                        mc.screen instanceof fr.crusadecraft.client.screen.LieuForm ? "ouvert" : "absent");
                grab(mc, "lieu_formulaire");
                CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LieuPackets.Action(fr.crusadecraft.network.packet.LieuPackets.Action.Op.CREATE,
                        null, "Abbaye de Saint-Aubin", fr.crusadecraft.core.place.LieuKind.LIEU_SAINT.ordinal(), null, shrinePos, 0));
                step = Step.LIEU_SCREEN;
                wait = 30;
            }
            case LIEU_SCREEN -> {
                var lieux = mc.screen instanceof fr.crusadecraft.client.screen.LieuxScreen screen ? screen.view() : null;
                CrusadeCraft.LOGGER.info("WorldShots : écran des lieux {} ({} lieux)", lieux != null ? "ouvert" : "absent",
                        lieux == null ? 0 : lieux.entries().size());
                grab(mc, "lieux_reels");
                if (shrinePos != null) fr.crusadecraft.client.screen.MapScreen.centerNextOn(shrinePos.getX(), shrinePos.getZ(), 2);
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                step = Step.LIEU_MAP;
                wait = 40;
            }
            case LIEU_MAP -> {
                grab(mc, "carte_lieux");
                mc.setScreen(null);
                // Le territoire au chunk (P16) : la carte du chat, puis le titre à l'entrée d'un royaume.
                mc.gui.getChat().clearMessages(false);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> chatMap(server));
                step = Step.CHAT_MAP;
                wait = 20;
            }
            case CHAT_MAP -> {
                grab(mc, "carte_chat");
                mc.gui.getChat().clearMessages(false);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> walkOut(server));
                step = Step.WALK_IN;
                wait = 50;
            }
            case WALK_IN -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> walkIn(server));
                step = Step.TITLE_SHOT;
                wait = 32;
            }
            case TITLE_SHOT -> {
                mc.gui.getChat().clearMessages(false);
                grab(mc, "titre_entree");
                // Xaero's World Map (P17), s'il est chargé : sa touche d'ouverture, comme le ferait le joueur.
                net.minecraft.client.KeyMapping open = null;
                for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) if (k.getName().equals("gui.xaero_open_map")) open = k;
                CrusadeCraft.LOGGER.info("WorldShots : Xaero's World Map {}", open == null ? "absent" : "ouvert");
                if (open != null) {
                    net.minecraft.client.KeyMapping.click(open.getKey());
                    step = Step.XAERO_SHOT;
                    wait = 80;
                } else {
                    step = Step.TERRAIN_OPEN;
                    wait = 20;
                }
            }
            case XAERO_SHOT -> {
                CrusadeCraft.LOGGER.info("WorldShots : écran {}", mc.screen == null ? "aucun" : mc.screen.getClass().getName());
                grab(mc, "xaero_carte");
                mc.setScreen(null);
                step = Step.TERRAIN_OPEN;
                wait = 20;
            }
            case TERRAIN_OPEN -> {
                // La carte en terrain (P18b) : large, autour du joueur, en vue « Terrain », le temps que les tuiles arrivent.
                fr.crusadecraft.client.screen.MapScreen.centerNextOn(mc.player.getBlockX(), mc.player.getBlockZ(), 10);
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
                step = Step.TERRAIN_WAIT;
                wait = 30;
            }
            case TERRAIN_WAIT -> {
                if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m) m.previewMode(2);
                step = Step.TERRAIN_SHOT;
                wait = 300;
            }
            case TERRAIN_SHOT -> {
                CrusadeCraft.LOGGER.info("WorldShots : carte en terrain, {} tuile(s) reçue(s)", fr.crusadecraft.client.map.ClientTerrain.version());
                grab(mc, "carte_terrain");
                if (mc.screen instanceof fr.crusadecraft.client.screen.MapScreen m) m.previewMode(0);
                step = Step.TERRAIN_POLITICAL;
                wait = 40;
            }
            case TERRAIN_POLITICAL -> {
                grab(mc, "carte_politique_terrain");
                mc.setScreen(null);
                step = Step.DONE;
                wait = 20;
            }
            case V3 -> {
                if (WorldShotsV3.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case V4 -> {
                if (WorldShotsV4.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case V5 -> {
                if (WorldShotsV5.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case V7 -> {
                if (WorldShotsV7.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case V6 -> {
                if (WorldShotsV6.tick(mc, WorldShots::grab)) step = Step.DONE;
            }
            case DONE -> {
                // -Dcrusadecraft.worldshots.hold=240 : la partie reste ouverte (pour regarder la carte web Dynmap).
                int hold = Integer.getInteger("crusadecraft.worldshots.hold", 0);
                if (hold > 0 && !held) {
                    held = true;
                    CrusadeCraft.LOGGER.info("WorldShots : partie gardée ouverte {} secondes", hold);
                    wait = hold * 20;
                    return;
                }
                CrusadeCraft.LOGGER.info("WorldShots : terminé, fermeture du jeu");
                mc.stop();
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        Path dir = mc.getLevelSource().getBaseDir().resolve(WORLD);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                CrusadeCraft.LOGGER.warn("WorldShots : ancien monde non effacé", e);
            }
        }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        // -Pshots_world=normal : un monde ordinaire, pour voir la carte en terrain ; sinon plat, plus rapide et prévisible.
        var preset = Boolean.getBoolean("crusadecraft.worldshots.normal") ? WorldPresets.NORMAL : WorldPresets.FLAT;
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261003L, true, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions());
    }

    /** Un mur, neuf cadres, les royaumes IA ; tout est révélé sauf le premier, gardé pour le héraut. */
    private static void setup(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        level.setDayTime(6000);
        BlockPos base = player.blockPosition();
        int wallZ = base.getZ() - 5;
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(new BlockPos(base.getX() + x, base.getY() + y, wallZ), Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                level.setBlockAndUpdate(new BlockPos(base.getX() + x, base.getY() + y, wallZ + 1),
                        ModBlocks.WAR_MAP.get().defaultBlockState().setValue(WarMapBlock.FACING, Direction.SOUTH));
            }
        }
        WorldService.generate(server, level, base.getX(), base.getZ());
        Iterator<AiRealm> it = CrusadeSavedData.get(server).world().realms().iterator();
        if (it.hasNext()) it.next();
        long today = fr.crusadecraft.core.realm.RealmService.currentDay(server);
        while (it.hasNext()) it.next().discover(player.getUUID(), today);
        player.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180f, -8f);
    }

    /** P63 : une colonie et un royaume au pied du joueur, pour ouvrir le menu royal sur la carte de Xaero. */
    private static void foundForMaps(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        fr.crusadecraft.config.CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
        BlockPos center = player.blockPosition().south(6);
        fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.createColony(player.serverLevel(), center, player, "Valbrume", 3);
        fr.crusadecraft.core.realm.RealmService.found(player, new fr.crusadecraft.core.realm.RealmService.FoundingRequest(
                center.east(), "Royaume de Valbrume", "Par le fer et la foi", "Aubépine", 1, false));
    }

    private static void visitHiddenRealm(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        AiRealm first = CrusadeSavedData.get(server).world().realms().iterator().next();
        AiProvince capital = first.capital();
        player.connection.teleport(capital.seat().getX() + 0.5, capital.seat().getY() + 1, capital.seat().getZ() + 12.5, 180f, 5f);
    }

    /** Identifiant de la lettre qui attend une réponse, dans l'inventaire du joueur. */
    private static java.util.UUID findLetter(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.is(fr.crusadecraft.registry.ModItems.LETTER.get()) && fr.crusadecraft.item.LetterItem.awaitsAnswer(stack)) {
                return fr.crusadecraft.item.LetterItem.data(stack).getUUID("id");
            }
        }
        return null;
    }

    /**
     * Une colonie MineColonies au pied du joueur, un royaume fondé dessus, puis une proposition d'hommage à une
     * seigneurie voisine (bien disposée) ; la réponse arrive aussitôt et le courrier est pris.
     */
    private static void foundAndWrite(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        fr.crusadecraft.config.CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
        fr.crusadecraft.config.CrusadeConfig.SERVER.chanceryNeedsResearch.set(false);
        fr.crusadecraft.config.CrusadeConfig.SERVER.letterDelaySeconds.set(0);
        BlockPos center = player.blockPosition().south(6);
        colonyCenter = center;
        fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.createColony(level, center, player, "Valbrume", 3);
        player.connection.teleport(center.getX() + 0.5, center.getY(), center.getZ() + 2.5, 180f, 0f);
        var realm = fr.crusadecraft.core.realm.RealmService.found(player, new fr.crusadecraft.core.realm.RealmService.FoundingRequest(
                center.east(), "Royaume de Valbrume", "Par le fer et la foi", "Aubépine", 1, false)).orElseThrow();
        realm.setGold(800);
        realm.setPrestige(400);
        realm.setDread(30);
        AiRealm barony = CrusadeSavedData.get(server).world().realms().stream()
                .filter(r -> r.rank() == fr.crusadecraft.core.realm.TitleRank.BARONNIE).findFirst().orElseThrow();
        long today = fr.crusadecraft.core.realm.RealmService.currentDay(server);
        barony.discover(player.getUUID(), today);
        var rel = CrusadeSavedData.get(server).diplomacy().between(barony.id(), realm.id());
        for (int i = 0; i < 4; i++) rel.addOpinion(barony.id(), fr.crusadecraft.core.character.OpinionModifier.of(
                fr.crusadecraft.core.character.OpinionReason.CADEAU, today, 3));
        player.getInventory().add(new net.minecraft.world.item.ItemStack(fr.crusadecraft.registry.ModItems.SEAL.get()));
        fr.crusadecraft.core.diplomacy.DiplomacyService.propose(player, barony.id(),
                fr.crusadecraft.core.diplomacy.DiplomacyRules.Proposal.VASSALISATION, 15, 10);
        fr.crusadecraft.core.diplomacy.DiplomacyService.takeMail(player);
    }

    private static java.util.UUID enemy;

    /** Guerre (humiliation) contre un royaume qui n'est pas notre vassal, armée levée, puis on marche sur sa capitale. */
    private static void declareWar(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        server.setDifficulty(Difficulty.NORMAL, true);
        var data = CrusadeSavedData.get(server);
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        long today = fr.crusadecraft.core.realm.RealmService.currentDay(server);
        AiRealm target = data.world().realms().stream()
                .filter(r -> data.diplomacy().suzerainOf(r.id()).isEmpty() && !r.landless())
                .findFirst().orElseThrow();
        target.discover(player.getUUID(), today);
        enemy = target.id();
        data.diplomacy().between(target.id(), realm.id()).setHostile(true);
        var out = fr.crusadecraft.core.war.WarService.declare(player, target.id(), fr.crusadecraft.core.war.CasusBelli.HUMILIATE, -1);
        CrusadeCraft.LOGGER.info("WorldShots : déclaration — {}", out.message().getString());
        realm.setArmyMen(60);
        fr.crusadecraft.core.war.WarService.raise(player, true);
        AiProvince capital = target.capital();
        player.connection.teleport(capital.seat().getX() + 0.5, capital.seat().getY() + 1, capital.seat().getZ() + 16.5, 180f, 8f);
    }

    /** Le centre de la colonie du joueur, noté à la fondation. */
    private static BlockPos colonyCenter;

    /** Où centrer la carte de guerre, et à quelle échelle : x, z, blocs par point. */
    private static volatile int[] warView;

    private static long[] squareOf(int cx, int cz, int r) {
        long[] out = new long[(2 * r + 1) * (2 * r + 1)];
        int i = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) out[i++] = fr.crusadecraft.core.world.TerritoryMap.key(cx + dx, cz + dz);
        return out;
    }

    /**
     * Le plan de bataille du joueur contre l'ennemi, tracé par le service comme depuis l'écran : la capitale ennemie en
     * objectif, une zone d'offensive sur la route, la défense autour de chez soi, deux flèches et trois repères.
     */
    private static void drawWarPlan(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        BlockPos home = colonyCenter != null ? colonyCenter
                : fr.crusadecraft.compat.minecolonies.MineColoniesBridge.center(realm.capital()).orElse(player.blockPosition());
        // La capitale assiégée touche la colonie de démonstration : l'objectif du plan est un royaume plus lointain.
        BlockPos target = CrusadeSavedData.get(server).world().realm(enemy).orElseThrow().capital().seat();
        double best = Double.MAX_VALUE;
        for (AiRealm r : CrusadeSavedData.get(server).world().realms()) {
            if (r.capital() == null) continue;
            double d = Math.sqrt(r.capital().seat().distSqr(home));
            if (d >= 350 && d < best) {
                best = d;
                target = r.capital().seat();
            }
        }
        int hx = home.getX(), hz = home.getZ(), tx = target.getX(), tz = target.getZ();
        CrusadeCraft.LOGGER.info("WorldShots : plan de guerre de {} , {} vers {} , {}", hx, hz, tx, tz);
        double dx = tx - hx, dz = tz - hz, dist = Math.max(1, Math.hypot(dx, dz));
        // Les points de la route, de chez soi (0) à la capitale ennemie (1).
        java.util.function.DoubleFunction<int[]> at = t -> new int[]{(int) Math.round(hx + dx * t), (int) Math.round(hz + dz * t)};
        double side = 90 / dist;
        int[] mid = at.apply(0.62), rally = at.apply(0.25), ford = at.apply(0.48);
        log("défense", fr.crusadecraft.core.war.WarMapService.paint(player, fr.crusadecraft.core.war.WarMap.Zone.DEFENSE, squareOf(hx >> 4, hz >> 4, 4)));
        log("offensive", fr.crusadecraft.core.war.WarMapService.paint(player, fr.crusadecraft.core.war.WarMap.Zone.OFFENSIVE, squareOf(mid[0] >> 4, mid[1] >> 4, 3)));
        log("objectif", fr.crusadecraft.core.war.WarMapService.paint(player, fr.crusadecraft.core.war.WarMap.Zone.OBJECTIF, squareOf(tx >> 4, tz >> 4, 2)));
        int[] a0 = at.apply(0.12), a1 = at.apply(0.88);
        // Deux flèches côte à côte, de part et d'autre de la route.
        int ox = (int) Math.round(-dz * side), oz = (int) Math.round(dx * side);
        log("flèche", fr.crusadecraft.core.war.WarMapService.arrow(player, a0[0] + ox, a0[1] + oz, a1[0] + ox / 3, a1[1] + oz / 3));
        log("flèche", fr.crusadecraft.core.war.WarMapService.arrow(player, a0[0] - ox, a0[1] - oz, a1[0] - ox / 3, a1[1] - oz / 3));
        log("repère", fr.crusadecraft.core.war.WarMapService.pin(player, rally[0], rally[1], fr.crusadecraft.core.war.WarMap.Mark.RASSEMBLEMENT, "Ralliement de l'ost"));
        log("repère", fr.crusadecraft.core.war.WarMapService.pin(player, ford[0] + ox / 2, ford[1] + oz / 2, fr.crusadecraft.core.war.WarMap.Mark.DANGER, "Gué gardé"));
        log("repère", fr.crusadecraft.core.war.WarMapService.pin(player, tx, tz - 40, fr.crusadecraft.core.war.WarMap.Mark.OBJECTIF, "Prendre la capitale"));
        int[] c = at.apply(0.5);
        warView = new int[]{c[0], c[1], (int) Math.max(2, Math.ceil(dist / 260))};
    }

    private static void log(String what, fr.crusadecraft.core.council.CouncilActions.Outcome out) {
        CrusadeCraft.LOGGER.info("WorldShots : plan de guerre, {} — {}", what, out.message().getString());
    }

    /** La garnison tombe ; le joueur se poste au pied de la bannière. */
    private static void stormBanner(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        var source = server.createCommandSourceStack().withSuppressedOutput();
        for (String type : new String[]{"vindicator", "pillager"}) {
            server.getCommands().performPrefixedCommand(source, "kill @e[tag=" + fr.crusadecraft.core.war.WarFieldService.TAG + ",type=minecraft:" + type + "]");
        }
        AiProvince capital = CrusadeSavedData.get(server).world().realm(enemy).orElseThrow().capital();
        BlockPos banner = fr.crusadecraft.core.war.WarFieldService.bannerOf(capital.id()).orElse(capital.seat());
        CrusadeCraft.LOGGER.info("WorldShots : bannière du siège en {}", banner.toShortString());
        player.connection.teleport(banner.getX() + 0.5, banner.getY(), banner.getZ() + 3.5, 180f, 25f);
    }

    /** Retour à la capitale ; le prochain raid ennemi est avancé à maintenant. */
    private static void callRaid(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        var data = CrusadeSavedData.get(server);
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        var center = fr.crusadecraft.compat.minecolonies.MineColoniesBridge.center(realm.capital()).orElse(player.blockPosition());
        player.connection.teleport(center.getX() + 0.5, center.getY() + 1, center.getZ() + 4.5, 180f, 0f);
        long interval = fr.crusadecraft.config.CrusadeConfig.SERVER.raidMinutes.get() * 1200L;
        data.wars().between(realm.id(), enemy).ifPresent(w -> w.setLastRaidTick(player.serverLevel().getGameTime() - interval - 1));
    }

    private static BlockPos fortGround;

    /** Le bâton dans la main du joueur, réglé sur son royaume, sur un terrain dégagé au sud de la capitale. */
    private static void giveStaff(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        var staff = new net.minecraft.world.item.ItemStack(fr.crusadecraft.registry.ModItems.BORNAGE.get());
        fr.crusadecraft.item.BornageStaffItem.setOwner(staff, realm.id(), realm.color(), realm.name());
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, staff);
        BlockPos center = fr.crusadecraft.compat.minecolonies.MineColoniesBridge.center(realm.capital()).orElse(player.blockPosition());
        fortGround = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.south(80)).below();
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 1, fortGround.getZ() + 40.5, 180f, 0f);
    }

    /** Un fort du royaume du joueur, posé d'un coup comme dans l'éditeur ; le joueur le regarde d'un peu plus haut. */
    private static void buildFort(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        var out = fr.crusadecraft.core.build.SiteService.place(player, fr.crusadecraft.core.build.SiteKind.FORT, realm.id(), fortGround,
                Direction.SOUTH, true);
        CrusadeCraft.LOGGER.info("WorldShots : construction du fort — {}", out.message().getString());
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 16, fortGround.getZ() + 38.5, 180f, 22f);
    }

    /** Une carte papier remplie, tenue à deux mains, regardée de près : les terres du royaume par-dessus. */
    private static void givePaperMap(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        var map = net.minecraft.world.item.MapItem.create(level, fortGround.getX(), fortGround.getZ() - 80, (byte) 2, true, false);
        // La surcouche ne peint que ce qui est exploré : la carte de démonstration l'est entièrement.
        var saved = net.minecraft.world.item.MapItem.getSavedData(map, level);
        byte grass = net.minecraft.world.level.material.MapColor.GRASS.getPackedId(net.minecraft.world.level.material.MapColor.Brightness.NORMAL);
        if (saved != null) for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) saved.updateColor(x, z, grass);
        player.getInventory().setItem(0, map);
        player.getInventory().selected = 0;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 1, fortGround.getZ() - 55.5, 180f, 72f);
    }

    private static BlockPos shrinePos;

    /** Le joueur à la lisière de ses terres ; la carte des chunks voisins arrive dans son chat. */
    private static void chatMap(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 1, fortGround.getZ() + 0.5, 180f, 10f);
        for (var line : fr.crusadecraft.core.world.ClaimService.map(player)) player.sendSystemMessage(line);
    }

    /** Hors de toute terre, loin à l'ouest. */
    private static void walkOut(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.connection.teleport(fortGround.getX() - 900.5, fortGround.getY() + 30, fortGround.getZ() + 0.5, 90f, 10f);
    }

    /** De retour sur ses terres : le nom du royaume s'affiche en grand. */
    private static void walkIn(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 1, fortGround.getZ() + 30.5, 180f, -5f);
    }

    /** Le bâton revient en main, en mode « Lieu » ; un clic au sol près de la capitale ouvre le formulaire du lieu. */
    private static void placeShrine(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = player.serverLevel();
        var realm = fr.crusadecraft.core.realm.RealmService.realmOf(server, player.getUUID()).orElseThrow();
        var staff = new net.minecraft.world.item.ItemStack(fr.crusadecraft.registry.ModItems.BORNAGE.get());
        fr.crusadecraft.item.BornageStaffItem.setOwner(staff, realm.id(), realm.color(), realm.name());
        fr.crusadecraft.item.BornageStaffItem.setLieu(staff, fr.crusadecraft.core.place.LieuKind.LIEU_SAINT);
        player.getInventory().setItem(0, staff);
        BlockPos center = fr.crusadecraft.compat.minecolonies.MineColoniesBridge.center(realm.capital()).orElse(player.blockPosition());
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(18, 0, 18)).below();
        shrinePos = ground.above();
        player.connection.teleport(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 4.5, 180f, 30f);
        fr.crusadecraft.core.world.TerritoryService.onStaffUse(player, staff, ground, Direction.UP, false);
    }

    private static void grab(Minecraft mc, String name) {
        String file = "crusadecraft_monde_" + String.format("%02d", ++shot) + "_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> CrusadeCraft.LOGGER.info("WorldShots : {}", msg.getString()));
    }
}
