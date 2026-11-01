package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.SettingsService;
import fr.crusadecraft.core.ai.VillageService;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.court.FeastService;
import fr.crusadecraft.core.diplomacy.CaravanService;
import fr.crusadecraft.core.diplomacy.TradeService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.lineage.House;
import fr.crusadecraft.core.lineage.LineageService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.ArmyService;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.Formation;
import fr.crusadecraft.core.war.HostService;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtLifePackets;
import fr.crusadecraft.network.packet.LineagePackets;
import fr.crusadecraft.network.packet.SettingsPackets;
import fr.crusadecraft.network.packet.WarPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Le banc d'essai en monde réel des phases 26 à 30 ({@code ./gradlew runWorldShots -Pshots_v3=true}) : une colonie et
 * un royaume, un banquet, l'arbre de la maison, les réglages, l'armée rassemblée au camp puis en ligne, un ost ennemi
 * qui paraît face à elle, la carte de guerre avec les pions, une caravane sur sa route, et un village de l'ordinateur
 * avec son premier quartier. Chaque étape agit sur le serveur, ouvre au besoin un écran, attend, puis photographie.
 */
final class WorldShotsV3 {
    /** Une étape : ce que fait le serveur, ce que fait le client, combien de ticks attendre, et la capture (ou rien). */
    record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {}

    private static List<Step> steps;
    private static int index = -1;
    private static int wait;
    private static boolean acted;

    private static BlockPos colony;
    private static BlockPos camp;
    private static java.util.UUID house;
    private static java.util.UUID enemy;

    private WorldShotsV3() {}

    /** Un tick du banc d'essai ; renvoie {@code true} quand tout est fait. */
    static boolean tick(Minecraft mc, java.util.function.BiConsumer<Minecraft, String> grab) {
        if (steps == null) steps = build();
        if (wait > 0) {
            wait--;
            return false;
        }
        if (index >= 0 && acted) {
            Step done = steps.get(index);
            if (done.shot() != null) grab.accept(mc, done.shot());
            acted = false;
        }
        index++;
        if (index >= steps.size()) return true;
        Step s = steps.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        CrusadeCraft.LOGGER.info("WorldShots v3 : étape {}", s.name());
        if (s.server() != null) server.execute(() -> {
            try {
                s.server().accept(server);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v3 : l'étape {} a échoué côté serveur", s.name(), e);
            }
        });
        if (s.client() != null) s.client().accept(mc);
        wait = s.ticks();
        acted = true;
        return false;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static Realm realm(MinecraftServer server) {
        return RealmService.realmOf(server, player(server).getUUID()).orElseThrow();
    }

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
    }

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        out.add(new Step("fondation", WorldShotsV3::found, null, 120, null));
        out.add(new Step("banquet", server -> log("banquet", FeastService.hold(player(server))),
                mc -> CrusadeNetwork.toServer(CourtLifePackets.HouseAction.of(CourtLifePackets.HouseAction.Kind.REFRESH)), 40, "maison_banquet"));
        out.add(new Step("cour_en_fete", server -> {
            ServerPlayer p = player(server);
            look(p, colony.getX() + 0.5, colony.getY() + 6, colony.getZ() + 14.5, 180f, 25f);
        }, mc -> mc.setScreen(null), 400, "cour_en_fete"));
        out.add(new Step("registre", null, mc -> CrusadeNetwork.toServer(new LineagePackets.Request(house, null)), 30, null));
        out.add(new Step("arbre", null, mc -> {
            if (mc.screen instanceof fr.crusadecraft.client.screen.LineageScreen ls) {
                mc.setScreen(new fr.crusadecraft.client.screen.TreeScreen(ls, ls.currentView()));
            }
        }, 30, "arbre_reel"));
        out.add(new Step("reglages", server -> log("réglage", SettingsService.set(player(server), "commerce.vitesseCaravanes", "5")),
                mc -> CrusadeNetwork.toServer(new SettingsPackets.Request("", "")), 30, "reglages_reels"));
        out.add(new Step("camp", WorldShotsV3::musterAtCamp, mc -> mc.setScreen(null), 80, "armee_rassemblee"));
        out.add(new Step("ligne", server -> {
            // Tenir à dix blocs devant le camp, face au sud ; la caméra recule pour voir la ligne se former.
            ServerPlayer p = player(server);
            look(p, camp.getX() + 0.5, camp.getY() + 1, camp.getZ() - 10.5, 0f, 0f);
            log("formation", ArmyService.formation(p, Formation.LIGNE));
            log("tenir", ArmyService.order(p, ArmyService.Order.HOLD));
            look(p, camp.getX() + 0.5, camp.getY() + 8, camp.getZ() - 28.5, 0f, 18f);
        }, null, 240, "armee_en_ligne"));
        out.add(new Step("table_guerre", server -> WarPackets.sendWar(player(server), true), null, 40, "table_guerre_armee"));
        out.add(new Step("ost", WorldShotsV3::hostBattle, mc -> mc.setScreen(null), 120, "bataille"));
        out.add(new Step("bataille_suite", null, null, 300, "bataille_suite"));
        out.add(new Step("carte_guerre", null, mc -> {
            fr.crusadecraft.client.screen.MapScreen.openNextAtWar();
            CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
        }, 60, "carte_guerre_armees"));
        out.add(new Step("caravane", WorldShotsV3::caravan, mc -> mc.setScreen(null), 100, "caravane"));
        out.add(new Step("village", WorldShotsV3::village, null, 120, "village_ia"));
        out.add(new Step("carte_commerce", null, mc -> CrusadeNetwork.toServer(new WorldPackets.RequestMap(true)), 60, "carte_commerce"));
        out.add(new Step("fin", null, mc -> mc.setScreen(null), 10, null));
        return out;
    }

    private static void log(String what, fr.crusadecraft.core.council.CouncilActions.Outcome out) {
        CrusadeCraft.LOGGER.info("WorldShots v3 : {} — {} ({})", what, out.success() ? "accepté" : "refusé", out.message().getString());
    }

    /** Une colonie, un royaume, une maison au registre dont les gens paraissent avec Comes Alive. */
    private static void found(MinecraftServer server) {
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        fr.crusadecraft.config.CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
        WorldEditService.grantForTests(p.getUUID(), true);
        colony = p.blockPosition().south(6);
        MineColoniesTestKit.createColony(level, colony, p, "Valbrume", 3);
        Realm realm = RealmService.found(p, new RealmService.FoundingRequest(colony.east(), "Royaume de Valbrume", "Par le fer et la foi",
                "Aubépine", 1, false)).orElseThrow();
        realm.setGold(5000);
        realm.setPrestige(500);
        log("maison", LineageService.createHouse(p, "Aubépine", 2, realm.id(), 3));
        var state = CrusadeSavedData.get(server).lineage();
        house = state.houses().stream().filter(h -> h.name().equals("Aubépine")).map(House::id).findFirst().orElse(null);
        p.connection.teleport(colony.getX() + 0.5, colony.getY(), colony.getZ() + 3.5, 180f, 0f);
        if (house != null) log("apparition", LineageService.spawn(p, house));
    }

    /** Un camp militaire bâti à côté de la colonie ; l'armée levée y paraît. */
    private static void musterAtCamp(MinecraftServer server) {
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        Realm realm = realm(server);
        realm.setArmyRaised(true);
        realm.setArmyMen(120);
        BlockPos g = ground(level, colony.getX() + 40, colony.getZ()).below();
        log("camp", SiteService.place(p, SiteKind.CAMP, realm.id(), g, Direction.SOUTH, true));
        Optional<Site> site = CrusadeSavedData.get(server).world().sites().stream()
                .filter(s -> s.kind() == SiteKind.CAMP && realm.id().equals(s.owner())).findFirst();
        site.ifPresent(s -> {
            for (int i = 0; i < 40 && !s.built(); i++) SiteService.advanceForTests(level, s, 100000);
        });
        camp = g;
        p.connection.teleport(g.getX() + 0.5, g.getY() + 1, g.getZ() + 6.5, 180f, 15f);
        log("rassembler", ArmyService.muster(p));
        look(p, g.getX() + 0.5, g.getY() + 6, g.getZ() + 16.5, 180f, 22f);
    }

    /** Un royaume de l'ordinateur déclare la guerre ; son ost est posé à quarante blocs de l'armée, qui l'engage. */
    private static void hostBattle(MinecraftServer server) {
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        server.setDifficulty(Difficulty.EASY, true);
        Realm realm = realm(server);
        var data = CrusadeSavedData.get(server);
        AiRealm ai = data.world().realms().stream().filter(r -> !r.landless() && data.diplomacy().suzerainOf(r.id()).isEmpty())
                .findFirst().orElseThrow();
        ai.discover(p.getUUID(), RealmService.currentDay(server));
        enemy = ai.id();
        War war = WarService.declareBetween(server, ai.id(), realm.id(), CasusBelli.HUMILIATE, -1);
        List<Mob> army = ArmyService.soldiers(level, realm.id());
        double ax = army.stream().mapToDouble(Mob::getX).average().orElse(camp.getX());
        double az = army.stream().mapToDouble(Mob::getZ).average().orElse(camp.getZ());
        war.hosts().put(ai.id(), new War.Host(level.dimension().location(), ax, az - 30, 0));
        HostService.tick(server);
        CrusadeCraft.LOGGER.info("WorldShots v3 : ost de {} en {}, {} ; armée en {}, {}", ai.place(), (int) ax, (int) (az - 30), (int) ax, (int) az);
        look(p, ax + 18.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) ax, (int) az) + 10, az - 15, 90f, 30f);
    }

    /** Un traité de commerce avec un royaume connu ; la caravane, posée à trente blocs de la colonie, paraît devant le joueur. */
    private static void caravan(MinecraftServer server) {
        ServerPlayer p = player(server);
        Realm realm = realm(server);
        var data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        AiRealm partner = data.world().realms().stream().filter(r -> !r.id().equals(enemy) && !r.landless() && r.capital() != null)
                .min(java.util.Comparator.comparingDouble(r -> r.capital().seat().distSqr(colony))).orElseThrow();
        partner.discover(p.getUUID(), today);
        data.diplomacy().between(realm.id(), partner.id()).sign(Treaty.COMMERCE, today);
        TradeService.Route r = TradeService.routes(server).stream().filter(x -> x.a().equals(partner.id()) || x.b().equals(partner.id()))
                .findFirst().orElseThrow();
        double t = 40.0 / Math.max(1, r.distance());
        CaravanService.placeForTests(r.a(), r.b(), t);
        double cx = r.x0() + (r.x1() - r.x0()) * t, cz = r.z0() + (r.z1() - r.z0()) * t;
        ServerLevel level = p.serverLevel();
        look(p, cx + 10.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) cx, (int) cz) + 5, cz + 0.5, 90f, 20f);
        CaravanService.tick(server);
        CrusadeCraft.LOGGER.info("WorldShots v3 : caravane — {}", CaravanService.describeForTests(level, r.a(), r.b()));
    }

    /** Un royaume posé à l'est fonde un village sur la plaine, qui s'achève et gagne aussitôt un quartier. */
    private static void village(MinecraftServer server) {
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        int x = colony.getX() + 250, z = colony.getZ() + 120;
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
        AiRealm ai = WorldEditService.addRealm(server, level, "Rochegarde", "Mathilde", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                AiPersonality.PACIFIQUE, x + 90, z);
        ai.discover(p.getUUID(), RealmService.currentDay(server));
        Optional<Site> v = VillageService.foundAt(server, ai, level, x, z, 0);
        if (v.isEmpty()) {
            CrusadeCraft.LOGGER.warn("WorldShots v3 : village refusé — {}", fr.crusadecraft.core.ai.CivService.refusal(server, level, ai, SiteKind.VILLAGE, x, z, 0));
            return;
        }
        for (int i = 0; i < 40 && !v.get().built(); i++) SiteService.advanceForTests(level, v.get(), 100000);
        Optional<Site> q = VillageService.growAt(server, ai, v.get(), new Random(1));
        q.ifPresent(s -> {
            for (int i = 0; i < 40 && !s.built(); i++) SiteService.advanceForTests(level, s, 100000);
        });
        CrusadeCraft.LOGGER.info("WorldShots v3 : village {} ({}), quartier {}", v.get().name(), v.get().built() ? "achevé" : "en chantier",
                q.map(s -> s.built() ? "achevé" : "en chantier").orElse("absent"));
        look(p, x + 0.5, v.get().anchor().getY() + 28, z + 26.5, 180f, 50f);
    }
}
