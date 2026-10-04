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

    private enum Step { BOOT, LOADING, SETUP, WALL, MAP, SHEET, HERALD, REALM, LETTER, SEAL, CHANCERY, COLOMBIER, TOWNHALL, TOWNHALL_CLICK, WAR, SIEGE, CAPTURE, CAPTURED, WAR_TABLE, RAID, EDITOR, EDITOR_REALM, EDITOR_PAINT, LINEAGE_OPEN, LINEAGE_HOUSE, LINEAGE_SPAWN, LINEAGE_SHOT, STAFF_MENU, STAFF_SHOT, FORT_SHOT, SITES_MAP, PAPER_SHOT, DONE }

    private static Step step = Step.BOOT;
    private static int wait;
    private static int shot;

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
                createWorld(mc);
                step = Step.LOADING;
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
                grab(mc, "carte_papier");
                step = Step.DONE;
                wait = 20;
            }
            case DONE -> {
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
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261003L, true, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
        net.minecraft.world.item.MapItem.renderBiomePreviewMap(level, map);
        player.getInventory().setItem(0, map);
        player.getInventory().selected = 0;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.connection.teleport(fortGround.getX() + 0.5, fortGround.getY() + 1, fortGround.getZ() + 40.5, 180f, 60f);
    }

    private static void grab(Minecraft mc, String name) {
        String file = "crusadecraft_monde_" + String.format("%02d", ++shot) + "_" + name + ".png";
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> CrusadeCraft.LOGGER.info("WorldShots : {}", msg.getString()));
    }
}
