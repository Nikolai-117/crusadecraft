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
    private static final Map<UUID, UUID> LAST_TERRITORY = new HashMap<>();

    private WorldService() {}

    public static void onServerStopped() {
        HERALDS.clear();
        LAST_TERRITORY.clear();
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
        long seed = level.getSeed() ^ ((long) cx * 341873128712L) ^ ((long) cz * 132897987541L);
        WorldPlanner.Plan plan = new WorldPlanner(settings, seed, VillageScanner.landProbe(level), level.dimension().location())
                .plan(cx, cz, villages, MineColoniesBridge.colonyCenters(level), taken, RealmService.currentDay(server));
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
        if (nearest.isEmpty()) {
            LAST_TERRITORY.remove(player.getUUID());
            return Optional.empty();
        }
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
        UUID current = dist <= CrusadeConfig.SERVER.territoryRadius.get() ? realm.id() : null;
        UUID previous = current == null ? LAST_TERRITORY.remove(player.getUUID()) : LAST_TERRITORY.put(player.getUUID(), current);
        if (current != null && !current.equals(previous) && !fresh && realm.discoveredBy(player.getUUID())) {
            player.displayClientMessage(Component.translatable("crusadecraft.world.entering",
                    AiNames.realmOf(realm.rank(), realm.place())), true);
        }
        return fresh ? Optional.of(realm) : Optional.empty();
    }

    // ------------------------------------------------------------------ hérauts

    private static void sendHerald(ServerPlayer player, AiRealm realm) {
        ServerLevel level = player.serverLevel();
        Villager herald = EntityType.VILLAGER.create(level);
        if (herald == null) return;
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
            for (AiRealm r : world.realms()) simulateDay(r, day);
            world.setLastEconomyDay(day);
        }
        if (world.lastEconomyDay() < today) world.setLastEconomyDay(today);
        CrusadeSavedData.get(server).setDirty();
    }

    /** Un jour de vie d'un royaume IA : revenus, croissance, prestige. */
    static void simulateDay(AiRealm r, long day) {
        double income = 0;
        for (AiProvince p : r.provinces()) {
            income += p.population() / 4.0 + p.development() / 5.0;
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
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        WorldState world = data.world();
        ResourceLocation dim = player.level().dimension().location();
        List<MapView.Territory> territories = new ArrayList<>();
        List<MapView.Seat> seats = new ArrayList<>();
        int focusX = player.getBlockX(), focusZ = player.getBlockZ();

        for (Realm r : data.realms()) {
            int index = territories.size();
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
                seats.add(new MapView.Seat(index, info.isMissing() ? "?" : info.name(), center.get().getX(), center.get().getZ(),
                        capital, nearestDejure(world, center.get().getX(), center.get().getZ()), false));
            }
            territories.add(new MapView.Territory(r.id(), false, mine, r.name(), r.rank(), r.color(), r.rulerName(),
                    r.feminineTitle(), null, population));
        }
        for (AiRealm r : world.realms()) {
            if (!r.discoveredBy(player.getUUID())) continue;
            int index = territories.size();
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(dim)) continue;
                seats.add(new MapView.Seat(index, p.name(), p.seat().getX(), p.seat().getZ(), p.capital(), p.dejureKingdom(),
                        p.anchor() != AiProvince.Anchor.LAND));
            }
            territories.add(new MapView.Territory(r.id(), true, false, r.place(), r.rank(), r.color(), r.ruler().name(),
                    r.ruler().female(), r.personality(), r.population()));
        }
        List<MapView.Dejure> dejure = new ArrayList<>();
        for (WorldState.DejureKingdom k : world.dejure()) dejure.add(new MapView.Dejure(k.place(), k.color()));
        return new MapView(dim, focusX, focusZ, player.getBlockX(), player.getBlockZ(), CrusadeConfig.SERVER.territoryRadius.get(),
                CrusadeConfig.SERVER.colonyRadius.get(), territories, seats, dejure);
    }

    private static int nearestDejure(WorldState world, int x, int z) {
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
