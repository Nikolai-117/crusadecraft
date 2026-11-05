package fr.crusadecraft.core.world;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le monde autour des joueurs : génération des royaumes IA, découverte, hérauts, économie simulée, carte.
 *
 * <p>Rien n'est posé de façon permanente : le héraut est une entité temporaire, marquée, retirée à la fin
 * de sa visite (et au chargement suivant, s'il reste d'un arrêt brutal).
 */
public final class WorldService {
    public static final String HERALD_TAG = "crusadecraft_herald";

    private record Herald(UUID realm, long untilTick, ResourceKey<Level> level) {}

    /** Hérauts présents (identifiant d'entité → visite). État de session. */
    private static final Map<UUID, Herald> HERALDS = new HashMap<>();
    /** Dernier territoire IA où se trouvait chaque joueur, pour annoncer les entrées. État de session. */

    private WorldService() {}

    public static void onServerStopped() {
        HERALDS.clear();
    }

    // ------------------------------------------------------------------ génération

    /** Génère les royaumes IA autour de la capitale de ce royaume, s'ils n'existent pas encore. */
    public static void ensureGenerated(MinecraftServer server, Realm founder) {
        if (!CrusadeConfig.SERVER.aiRealms.get() || CrusadeSavedData.get(server).world().generated()) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, founder.capital().dimension()));
        if (level == null) return;
        BlockPos center = MineColoniesBridge.center(founder.capital()).orElse(level.getSharedSpawnPos());
        generate(server, level, center.getX(), center.getZ());
    }

    /** (Re)génère le monde IA autour d'un point. Renvoie le nombre de royaumes créés. */
    public static int generate(MinecraftServer server, ServerLevel level, int cx, int cz) {
        long started = System.nanoTime();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int min = CrusadeConfig.SERVER.aiMinDistance.get();
        int max = Math.max(min + 200, CrusadeConfig.SERVER.aiMaxDistance.get());
        int radius = CrusadeConfig.SERVER.territoryRadius.get();
        WorldPlanner.Settings settings = settings(min, max, radius);
        List<WorldPlanner.Candidate> villages = VillageScanner.scan(level, cx, cz, max + settings.provinceRadius());
        List<Integer> taken = new ArrayList<>();
        for (Realm r : data.realms()) taken.add(r.color());
        // Les royaumes posés à la main restent : les nouveaux s'en écartent et ne reprennent ni leurs couleurs ni leurs numéros.
        List<int[]> avoid = new ArrayList<>(MineColoniesBridge.colonyCenters(level));
        for (AiRealm r : data.world().handMade()) {
            taken.add(r.color());
            for (AiProvince p : r.provinces()) if (p.dimension().equals(level.dimension().location())) avoid.add(new int[]{p.seat().getX(), p.seat().getZ()});
        }
        long seed = level.getSeed() ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
        WorldPlanner.Plan plan = new WorldPlanner(settings, seed, VillageScanner.landProbe(level), level.dimension().location())
                .startingAt(data.world().nextProvinceId())
                .plan(cx, cz, villages, avoid, taken, RealmService.currentDay(server));
        data.world().replace(plan.realms(), plan.dejure(), cx, cz, RealmService.currentDay(server));
        data.setDirty();
        int provinces = plan.realms().stream().mapToInt(r -> r.provinces().size()).sum();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} royaumes IA, {} provinces ({} villages repérés sans générer de chunk) en {} ms",
                plan.realms().size(), provinces, villages.size(), (System.nanoTime() - started) / 1_000_000);
        return plan.realms().size();
    }

    private static WorldPlanner.Settings settings(int min, int max, int radius) {
        return new WorldPlanner.Settings(CrusadeConfig.SERVER.aiRealmCount.get(), min, max,
                Math.max(radius * 3, (max - min) / 3), Math.max(radius * 3, 300), Math.max(radius + 20, 180),
                Math.max(radius * 2, 250), CrusadeConfig.SERVER.baronyCount.get());
    }

    /** Un monde généré avant les seigneuries indépendantes les reçoit maintenant, sans rien déplacer. */
    private static void upgrade(MinecraftServer server, WorldState world) {
        if (world.version() >= 2) return;
        world.setVersion(2);
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || CrusadeConfig.SERVER.baronyCount.get() <= 0) return;
        int min = CrusadeConfig.SERVER.aiMinDistance.get();
        int max = Math.max(min + 200, CrusadeConfig.SERVER.aiMaxDistance.get());
        WorldPlanner.Settings settings = settings(min, max, CrusadeConfig.SERVER.territoryRadius.get());
        List<int[]> seats = new ArrayList<>();
        int nextId = 0;
        for (AiRealm r : world.realms()) {
            for (AiProvince p : r.provinces()) {
                seats.add(new int[]{p.seat().getX(), p.seat().getZ()});
                nextId = Math.max(nextId, p.id() + 1);
            }
        }
        List<WorldPlanner.Candidate> villages = VillageScanner.scan(level, world.centerX(), world.centerZ(), settings.minRing() + 600);
        long seed = level.getSeed() ^ 0xBA2017L;
        List<AiRealm> baronies = new WorldPlanner(settings, seed, VillageScanner.landProbe(level), level.dimension().location())
                .baronies(world.centerX(), world.centerZ(), WorldPlanner.points(villages), MineColoniesBridge.colonyCenters(level),
                        seats, List.of(), nextId, settings.baronies(), world.dejure(), RealmService.currentDay(server));
        for (AiRealm b : baronies) world.add(b);
        CrusadeSavedData.get(server).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} seigneuries indépendantes ajoutées au monde existant", baronies.size());
    }

    // ------------------------------------------------------------------ boucle

    /** Appelé toutes les deux secondes environ. */
    public static void tick(MinecraftServer server) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        WorldState world = data.world();
        if (!world.generated()) {
            // Un royaume fondé avant cette version : on génère son voisinage maintenant.
            if (MineColoniesBridge.isReady()) data.realms().stream().findFirst().ifPresent(r -> ensureGenerated(server, r));
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) discoverNear(player);
        upgrade(server, world);
        long today = RealmService.currentDay(server);
        cleanupHeralds(server);
        economy(server, world, today);
    }

    /**
     * Découverte et entrée sur un territoire, pour un joueur. Renvoie le royaume découvert à l'instant, s'il y
     * en a un.
     */
    public static Optional<AiRealm> discoverNear(ServerPlayer player) {
        MinecraftServer server = player.server;
        WorldState world = CrusadeSavedData.get(server).world();
        long today = RealmService.currentDay(server);
        ResourceLocation dim = player.level().dimension().location();
        Optional<Map.Entry<AiRealm, AiProvince>> nearest = world.nearestProvince(dim, player.getX(), player.getZ());
        if (nearest.isEmpty()) return Optional.empty();
        AiRealm realm = nearest.get().getKey();
        double dist = Math.sqrt(nearest.get().getValue().distanceSqr(player.getX(), player.getZ()));
        boolean fresh = false;
        if (dist <= CrusadeConfig.SERVER.discoveryRadius.get() && realm.discover(player.getUUID(), today)) {
            fresh = true;
            CrusadeSavedData.get(server).setDirty();
            Component name = AiNames.realm(realm.rank(), realm.place());
            CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM,
                    Component.translatable("crusadecraft.toast.discovered.title"),
                    Component.translatable("crusadecraft.toast.discovered.body", name)));
            if (CrusadeConfig.SERVER.heralds.get()) sendHerald(player, realm);
        }
        // L'annonce d'entrée sur des terres est faite par EntryTitles (P16), pour tous les royaumes, chunk par chunk.
        return fresh ? Optional.of(realm) : Optional.empty();
    }

    // ------------------------------------------------------------------ hérauts

    private static void sendHerald(ServerPlayer player, AiRealm realm) {
        ServerLevel level = player.serverLevel();
        // Une sous-classe, et non un villageois ordinaire : Minecraft Comes Alive remplace tout villageois de la classe
        // exacte Villager par l'un des siens, et le héraut aurait disparu (puis serait resté à sa place pour toujours).
        Villager herald = new Villager(EntityType.VILLAGER, level) {};
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * 3, z = player.getZ() + look.z * 3;
        BlockPos spot = BlockPos.containing(x, player.getY(), z);
        // Toujours dans un chunk chargé : sinon le héraut serait mis de côté par le jeu, invisible.
        if (!level.hasChunkAt(spot)) {
            x = player.getX() + 1;
            z = player.getZ() + 1;
            spot = BlockPos.containing(x, player.getY(), z);
        }
        int y = standableY(level, spot, player.getBlockY());
        float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90f;
        herald.moveTo(x, y, z, yaw, 0);
        herald.setYHeadRot(yaw);
        herald.setVillagerData(herald.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER));
        Component realmName = AiNames.realmOf(realm.rank(), realm.place());
        herald.setCustomName(Component.translatable("crusadecraft.herald.name", realmName));
        herald.setCustomNameVisible(true);
        herald.setInvulnerable(true);
        herald.setNoAi(true);
        herald.addTag(HERALD_TAG);
        // Inscrit avant d'apparaître : sinon le garde-fou contre les hérauts égarés l'empêcherait d'entrer.
        HERALDS.put(herald.getUUID(), new Herald(realm.id(), level.getGameTime() + CrusadeConfig.SERVER.heraldSeconds.get() * 20L,
                level.dimension()));
        if (!level.addFreshEntity(herald)) {
            HERALDS.remove(herald.getUUID());
            return;
        }
        Component ruler = Component.translatable(realm.rank().holderKey(realm.ruler().female())).append(" " + realm.ruler().name());
        player.sendSystemMessage(Component.translatable("crusadecraft.herald.greeting", realmName, ruler));
    }

    /** Une hauteur où tenir debout près de celle du joueur : jamais sur un toit ni au fond d'une cave. */
    private static int standableY(ServerLevel level, BlockPos spot, int playerY) {
        if (!level.hasChunkAt(spot)) return playerY;
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
            BlockPos p = spot.atY(playerY + dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                return p.getY();
            }
        }
        return playerY;
    }

    private static void cleanupHeralds(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Herald>> it = HERALDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Herald> e = it.next();
            ServerLevel level = server.getLevel(e.getValue().level());
            if (level == null) {
                it.remove();
                continue;
            }
            if (level.getGameTime() < e.getValue().untilTick()) continue;
            Entity entity = level.getEntity(e.getKey());
            if (entity != null) entity.discard();
            it.remove();
        }
    }

    /** Un héraut resté d'une session précédente (arrêt brutal) est retiré dès qu'il réapparaît. */
    public static boolean isStrayHerald(Entity entity) {
        return entity.getTags().contains(HERALD_TAG) && !HERALDS.containsKey(entity.getUUID());
    }

    /** Le royaume dont cette entité est le héraut, si c'en est un. */
    public static Optional<UUID> heraldRealm(Entity entity) {
        Herald h = HERALDS.get(entity.getUUID());
        return h == null ? Optional.empty() : Optional.of(h.realm());
    }

    // ------------------------------------------------------------------ économie simulée

    private static void economy(MinecraftServer server, WorldState world, long today) {
        if (world.lastEconomyDay() == Long.MIN_VALUE) {
            world.setLastEconomyDay(today);
            return;
        }
        int guard = 0;
        while (world.lastEconomyDay() < today && guard++ < 3) {
            long day = world.lastEconomyDay() + 1;
            Map<UUID, List<AiProvince>> held = heldProvinces(world);
            for (AiRealm r : world.realms()) simulateDay(r, held.getOrDefault(r.id(), List.of()), day);
            fr.crusadecraft.core.war.WarService.onNewDay(server, day);
            fr.crusadecraft.core.ai.AiBrain.onNewDay(server, day);
            fr.crusadecraft.core.lineage.LifeService.onNewDay(server, day);
            fr.crusadecraft.core.nation.NationService.onNewDay(server, day);
            fr.crusadecraft.core.city.CityLife.onNewDay(server, day);
            fr.crusadecraft.core.ai.CivService.onNewDay(server, day);
            world.setLastEconomyDay(day);
        }
        if (world.lastEconomyDay() < today) world.setLastEconomyDay(today);
        CrusadeSavedData.get(server).setDirty();
    }

    /** Qui tient quoi : chaque province rangée sous le royaume qui la tient, conquêtes comprises. */
    static Map<UUID, List<AiProvince>> heldProvinces(WorldState world) {
        Map<UUID, List<AiProvince>> held = new HashMap<>();
        for (AiRealm r : world.realms()) {
            for (AiProvince p : r.provinces()) held.computeIfAbsent(p.holder() == null ? r.id() : p.holder(), k -> new ArrayList<>()).add(p);
        }
        return held;
    }

    static void simulateDay(AiRealm r, long day) {
        simulateDay(r, r.ownProvinces(), day);
    }

    /**
     * Un jour de vie d'un royaume IA : le revenu des provinces qu'il tient (conquêtes comprises), la croissance
     * des siennes, le prestige.
     */
    static void simulateDay(AiRealm r, List<AiProvince> held, long day) {
        double income = 0;
        for (AiProvince p : held) income += p.population() / 4.0 + p.development() / 5.0;
        for (AiProvince p : r.provinces()) {
            int cap = p.capital() ? 80 : 45;
            if (p.population() < cap && Math.floorMod(day * 31 + p.id(), 3) == 0) p.setPopulation(p.population() + Math.max(1, p.population() / 40));
            if (Math.floorMod(day + p.id(), 5) == 0) p.setDevelopment(p.development() + 1);
        }
        long earned = Math.round(income * r.personality().incomeFactor());
        r.setLastIncome(earned);
        r.setGold(r.gold() + earned);
        r.setPrestige(r.prestige() + r.rank().ordinal());
        if (r.personality() == AiPersonality.FANATIQUE) r.setPiety(r.piety() + 2);
    }

    // ------------------------------------------------------------------ vues

    public static Optional<AiRealmView> realmView(ServerPlayer player, UUID id) {
        WorldState world = CrusadeSavedData.get(player.server).world();
        Optional<AiRealm> found = world.realm(id);
        if (found.isEmpty() || !found.get().discoveredBy(player.getUUID())) return Optional.empty();
        AiRealm r = found.get();
        AiProvince capital = r.capital();
        int distance = capital == null ? 0 : (int) Math.sqrt(capital.distanceSqr(player.getX(), player.getZ()));
        return Optional.of(AiRealmView.of(r, r.discoveredBy().get(player.getUUID()), distance));
    }

    public static MapView mapView(ServerPlayer player) {
        return mapView(player, false);
    }

    /**
     * La carte de ce joueur. {@code editing} (accordé seulement à qui peut éditer) : tout le monde IA, découvert ou non,
     * avec ce qu'il faut pour le modifier.
     */
    public static MapView mapView(ServerPlayer player, boolean editing) {
        MinecraftServer server = player.server;
        boolean canEdit = WorldEditService.canEdit(player);
        editing = editing && canEdit;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        WorldState world = data.world();
        ResourceLocation dim = player.level().dimension().location();
        List<MapView.Territory> territories = new ArrayList<>();
        List<MapView.Seat> seats = new ArrayList<>();
        int focusX = player.getBlockX(), focusZ = player.getBlockZ();

        Map<UUID, Integer> index = new HashMap<>();
        for (Realm r : data.realms()) {
            int i = territories.size();
            index.put(r.id(), i);
            boolean mine = r.ruler().equals(player.getUUID());
            int population = 0;
            for (ColonyRef colony : r.allColonies()) {
                ColonyInfo info = MineColoniesBridge.info(colony);
                population += info.citizens();
                if (!colony.dimension().equals(dim)) continue;
                Optional<BlockPos> center = MineColoniesBridge.center(colony);
                if (center.isEmpty()) continue;
                boolean capital = colony.equals(r.capital());
                if (mine && capital) {
                    focusX = center.get().getX();
                    focusZ = center.get().getZ();
                }
                seats.add(new MapView.Seat(i, info.isMissing() ? "?" : info.name(), center.get().getX(), center.get().getZ(),
                        capital, nearestDejure(world, center.get().getX(), center.get().getZ()), false, false));
            }
            territories.add(new MapView.Territory(r.id(), false, mine, r.name(), r.rank(), r.color(), r.rulerName(),
                    r.feminineTitle(), null, population));
        }
        for (AiRealm r : world.realms()) {
            if (!editing && !r.discoveredBy(player.getUUID())) continue;
            index.put(r.id(), territories.size());
            territories.add(new MapView.Territory(r.id(), true, false, r.place(), r.rank(), r.color(), r.ruler().name(),
                    r.ruler().female(), r.personality(), r.population(), r.faith(), r.handMade(), r.style().ordinal()));
        }
        // Chaque province sous la bannière de celui qui la tient : une conquête change la carte.
        for (AiRealm r : world.realms()) {
            if (!editing && !r.discoveredBy(player.getUUID())) continue;
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(dim)) continue;
                Integer i = index.get(p.holder() == null ? r.id() : p.holder());
                if (i == null) continue;
                int town = fr.crusadecraft.core.build.SiteService.townOf(player.server, p.id())
                        .map(fr.crusadecraft.core.build.SiteService::percent).orElse(-1);
                seats.add(new MapView.Seat(i, p.name(), p.seat().getX(), p.seat().getZ(), p.capital() && p.holder() == null, p.dejureKingdom(),
                        p.anchor() != AiProvince.Anchor.LAND, true, p.id(), index.get(r.id()), null, town));
            }
        }
        // Les constructions (P14) des royaumes de cette carte, et les postes de garde MineColonies des joueurs.
        for (fr.crusadecraft.core.build.Site site : world.sites()) {
            Integer i = index.get(site.owner());
            if (i == null || !site.dimension().equals(dim)) continue;
            seats.add(new MapView.Seat(i, site.name(), site.anchor().getX(), site.anchor().getZ(), false, nearestDejure(world, site.anchor().getX(),
                    site.anchor().getZ()), false, false, -1, -1, new MapView.SiteInfo(site.id(), site.kind().ordinal(), site.radius(),
                    fr.crusadecraft.core.build.SiteService.percent(site), site.kind().garrison(), site.town() != null)));
        }
        for (Realm r : data.realms()) {
            Integer i = index.get(r.id());
            if (i == null) continue;
            for (ColonyRef colony : r.allColonies()) {
                if (!colony.dimension().equals(dim)) continue;
                for (MineColoniesBridge.GuardPost g : Territory.guardPosts(colony)) {
                    seats.add(new MapView.Seat(i, MineColoniesBridge.info(colony).name(), g.pos().getX(), g.pos().getZ(), false,
                            nearestDejure(world, g.pos().getX(), g.pos().getZ()), false, false, -1, -1,
                            new MapView.SiteInfo(null, MapView.SiteInfo.GUARD_POST, Territory.guardRadius(g.level()), 100, g.level())));
                }
            }
        }
        List<MapView.Dejure> dejure = new ArrayList<>();
        for (WorldState.DejureKingdom k : world.dejure()) dejure.add(new MapView.Dejure(k.place(), k.color()));
        return new MapView(dim, focusX, focusZ, player.getBlockX(), player.getBlockZ(), CrusadeConfig.SERVER.territoryRadius.get(),
                CrusadeConfig.SERVER.colonyRadius.get(), territories, seats, dejure, canEdit, editing, borders(world, dim, index),
                fr.crusadecraft.core.place.PlaceService.mapPlaces(server, dim),
                editing ? MapView.War.NONE : fr.crusadecraft.core.war.WarMapService.view(player, dim), editing ? List.of() : tradeLines(player, dim),
                editing ? List.of() : armyPawns(player, dim), editing ? List.of() : travellers(player, dim), editing ? List.of() : commerce(player, dim), editing ? List.of() : blockedCities(player, dim));
    }

    /** P55 : les villes coupées du monde, d'un royaume qu'on connaît. */
    static List<MapView.Blocked> blockedCities(ServerPlayer player, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        boolean all = fr.crusadecraft.core.build.plan.PlanTool.allowed(player);
        List<MapView.Blocked> out = new ArrayList<>();
        for (UUID id : fr.crusadecraft.core.city.CityBlockades.blocked(player.server)) {
            var s = data.world().site(id).orElse(null);
            if (s == null || !s.dimension().equals(dim) || (!all && !known(data, s.owner(), player))) continue;
            out.add(new MapView.Blocked(s.anchor().getX(), s.anchor().getZ(), s.name(), fr.crusadecraft.core.city.CityBlockades.explain(player.server, s)));
        }
        return out;
    }

    /** P51 : les lignes de commerce de cette dimension, du moins celles d'un royaume qu'on connaît. */
    static List<MapView.CommerceLine> commerce(ServerPlayer player, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        boolean all = fr.crusadecraft.core.build.plan.PlanTool.allowed(player);
        List<MapView.CommerceLine> out = new ArrayList<>();
        for (fr.crusadecraft.core.city.CityLine l : data.world().lines()) {
            var from = data.world().site(l.from).orElse(null);
            var to = data.world().site(l.to).orElse(null);
            if (from == null || to == null || !from.dimension().equals(dim)) continue;
            if (!all && !known(data, from.owner(), player) && !known(data, to.owner(), player)) continue;
            var route = fr.crusadecraft.core.city.CityRoads.between(player.server, from, to);
            String what = l.good == null ? "" : fr.crusadecraft.core.logic.Fr.key(l.good.key() + ".du");
            boolean trouble = l.status.equals("cut") || l.status.equals("blockade") || l.status.equals("war") || l.status.equals("toll");
            out.add(new MapView.CommerceLine(route.xs(), route.zs(), from.name(), to.name(), what, l.trips, l.ai, trouble ? l.status : ""));
        }
        return out;
    }

    /** P50 : ceux qui sont sur les routes : d'un royaume qu'on connaît, on sait qui marche, et où. */
    static List<MapView.Traveller> travellers(ServerPlayer player, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        List<MapView.Traveller> out = new ArrayList<>();
        for (fr.crusadecraft.core.city.CityTravel.Pawn p : fr.crusadecraft.core.city.CityTravel.pawns(player.server)) {
            if (!p.dim().equals(dim.toString())) continue;
            UUID owner = data.world().site(p.home()).map(fr.crusadecraft.core.build.Site::owner).orElse(null);
            boolean mine = owner != null && RealmService.realmOf(player.server, player.getUUID()).map(Realm::id).map(owner::equals).orElse(false);
            if (owner == null || (!mine && !known(data, owner, player) && !fr.crusadecraft.core.build.plan.PlanTool.allowed(player))) continue;
            out.add(new MapView.Traveller(p.x(), p.z(), p.toX(), p.toZ(), p.name(), p.homeName(), p.destName(), p.percent(), p.caravan(), p.cargo()));
        }
        return out;
    }

    /**
     * Les armées de cette dimension (P27) : la tienne quand elle est rassemblée (avec son but de marche), et les ostes
     * en campagne des royaumes de l'ordinateur que tu connais.
     */
    static List<MapView.ArmyPawn> armyPawns(ServerPlayer player, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        List<MapView.ArmyPawn> out = new ArrayList<>();
        Optional<Realm> mine = RealmService.realmOf(player.server, player.getUUID());
        ServerLevel level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim));
        if (mine.isPresent() && level != null) {
            fr.crusadecraft.core.war.ArmyService.position(level, mine.get().id()).ifPresent(p -> {
                fr.crusadecraft.core.war.ArmyService.Status s = fr.crusadecraft.core.war.ArmyService.status(player.server, mine.get().id());
                boolean marching = s.order() == fr.crusadecraft.core.war.ArmyService.Order.MARCH && s.post() != null;
                out.add(new MapView.ArmyPawn((int) p[0], (int) p[1], fr.crusadecraft.core.war.WarService.men(mine.get()), (int) p[2],
                        mine.get().color(), Component.literal(mine.get().name()), true, false, marching,
                        marching ? s.post().getX() : 0, marching ? s.post().getZ() : 0));
            });
        }
        for (fr.crusadecraft.core.war.HostService.Pawn h : fr.crusadecraft.core.war.HostService.pawns(player.server)) {
            if (!h.dimension().equals(dim) || !known(data, h.realm(), player)) continue;
            int color = data.world().realm(h.realm()).map(AiRealm::color).orElse(0x888888);
            Component who = data.world().realm(h.realm()).<Component>map(a -> AiNames.realmOf(a.rank(), a.place()))
                    .orElse(fr.crusadecraft.core.diplomacy.TradeService.name(data, h.realm()));
            out.add(new MapView.ArmyPawn(h.x(), h.z(), h.men(), h.inWorld() ? 1 : 0, color, who, false, h.beaten(), h.hasTarget(), h.targetX(), h.targetZ()));
        }
        return out;
    }

    /** Les routes de commerce (P25) de cette dimension que ce joueur connaît : les siennes, et celles entre royaumes connus. */
    static List<MapView.TradeLine> tradeLines(ServerPlayer player, ResourceLocation dim) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        UUID mine = RealmService.realmOf(player.server, player.getUUID()).map(Realm::id).orElse(null);
        List<MapView.TradeLine> out = new ArrayList<>();
        for (fr.crusadecraft.core.diplomacy.TradeService.Route r : fr.crusadecraft.core.diplomacy.TradeService.routes(player.server)) {
            if (!r.dim().equals(dim)) continue;
            boolean own = r.a().equals(mine) || r.b().equals(mine);
            if (!own && !(known(data, r.a(), player) && known(data, r.b(), player))) continue;
            var caravan = fr.crusadecraft.core.diplomacy.CaravanService.pawn(player.server, r);
            out.add(new MapView.TradeLine(r.x0(), r.z0(), r.x1(), r.z1(), r.income(), fr.crusadecraft.core.diplomacy.TradeService.name(data, r.a()),
                    fr.crusadecraft.core.diplomacy.TradeService.name(data, r.b()), goods(r), caravan.isPresent(),
                    caravan.map(c -> c.x()).orElse(0), caravan.map(c -> c.z()).orElse(0), r.raided()));
        }
        return out;
    }

    /** Les marchandises d'une route (P29) : « Blé ⇄ Fer ». */
    public static Component goods(fr.crusadecraft.core.diplomacy.TradeService.Route r) {
        if (r.goodA() == null || r.goodB() == null) return Component.empty();
        return Component.translatable("crusadecraft.trade.goods", Component.translatable(r.goodA().key()), Component.translatable(r.goodB().key()));
    }

    public static boolean known(CrusadeSavedData data, UUID realm, ServerPlayer player) {
        Optional<AiRealm> ai = data.world().realm(realm);
        return ai.map(a -> a.discoveredBy(player.getUUID())).orElse(data.realm(realm).isPresent());
    }

    /** Les cases peintes des royaumes de cette carte (les autres restent dans le brouillard), et les terres libres. */
    private static MapView.Borders borders(WorldState world, ResourceLocation dim, Map<UUID, Integer> index) {
        var cells = world.borders().cells(dim);
        long[] keys = new long[cells.size()];
        int[] owners = new int[cells.size()];
        int n = 0;
        for (var c : cells.long2ObjectEntrySet()) {
            Integer i = TerritoryMap.NEUTRAL.equals(c.getValue()) ? Integer.valueOf(MapView.Borders.NEUTRAL) : index.get(c.getValue());
            if (i == null) continue;
            keys[n] = c.getLongKey();
            owners[n++] = i;
        }
        return new MapView.Borders(java.util.Arrays.copyOf(keys, n), java.util.Arrays.copyOf(owners, n));
    }

    static int nearestDejure(WorldState world, int x, int z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < world.dejure().size(); i++) {
            WorldState.DejureKingdom k = world.dejure().get(i);
            double d = Math.hypot(k.centerX() - x, k.centerZ() - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Révèle tous les royaumes IA à ce joueur (commande d'administration). */
    public static int discoverAll(ServerPlayer player) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        long today = RealmService.currentDay(player.server);
        int n = 0;
        for (AiRealm r : data.world().realms()) if (r.discover(player.getUUID(), today)) n++;
        data.setDirty();
        return n;
    }
}
