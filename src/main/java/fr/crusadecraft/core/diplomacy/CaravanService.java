package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les caravanes (P29) : sur chaque route de commerce, une caravane va et vient d'une capitale à l'autre, avec les
 * marchandises des deux royaumes. Loin des joueurs, ce n'est qu'un chariot sur la carte et sur Dynmap. Quand un joueur
 * passe à moins de 64 blocs, elle paraît : un marchand ambulant et deux lamas, qui suivent la route. Tuer le marchand
 * pille la caravane : le pillard emporte l'or (en pièces State Marks si le mod est là), la route ne rapporte plus rien
 * pendant quelques jours, et les deux royaumes en veulent au royaume du pillard, sauf s'il est en guerre avec eux.
 */
public final class CaravanService {
    /** L'étiquette des bêtes et des gens d'une caravane, suivie de la clé de sa route. */
    public static final String TAG = "crusadecraft_caravane";
    /** Un joueur à moins de tant de blocs : la caravane paraît. */
    static final int APPEAR = 64;
    /** Plus aucun joueur à moins de tant de blocs : elle redevient un chariot sur la carte. */
    static final int VANISH = 112;
    /** Le marchand vise un point à tant de blocs devant lui, sur la route. */
    static final int LEAD = 24;
    /** Ce que le pillard emporte : tant de jours de revenu de la route. */
    static final int LOOT_DAYS = 5;

    /** Une caravane : où elle en est sur sa route (0 = première capitale, 1 = seconde), dans quel sens, pillée jusqu'à quand. */
    static final class Caravan {
        double t;
        int dir = 1;
        long raidedUntil = -1;
        final Set<UUID> units = new HashSet<>();
        UUID trader;
    }

    /** Non enregistré : au redémarrage, les caravanes repartent de leur première capitale, et un pillage est oublié. */
    private static final Map<String, Caravan> CARAVANS = new HashMap<>();

    private CaravanService() {}

    public static void onServerStopped() {
        CARAVANS.clear();
        TEST_WATCHERS.clear();
    }

    /** La clé d'une route : ses deux royaumes, toujours dans le même ordre. */
    public static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    /** La route est-elle pillée ce jour-là ? Elle ne rapporte alors rien. */
    public static boolean raided(UUID a, UUID b, long today) {
        Caravan c = CARAVANS.get(key(a, b));
        return c != null && today < c.raidedUntil;
    }

    /** Avance sur la route d'une distance, en rebondissant aux deux bouts. Logique pure : renvoie {t, sens}. */
    public static double[] advance(double t, int dir, double stride, int distance) {
        if (distance <= 0) return new double[]{t, dir};
        double next = t + dir * stride / distance;
        if (next >= 1) return new double[]{1, -1};
        if (next <= 0) return new double[]{0, 1};
        return new double[]{next, dir};
    }

    /** Ce que le pillard emporte. */
    public static long loot(long income) {
        return Math.max(1, income * LOOT_DAYS);
    }

    static double x(TradeService.Route r, double t) {
        return r.x0() + (r.x1() - r.x0()) * t;
    }

    static double z(TradeService.Route r, double t) {
        return r.z0() + (r.z1() - r.z0()) * t;
    }

    /** Où en est ce point sur la route, de 0 à 1. */
    static double project(TradeService.Route r, double px, double pz) {
        double dx = r.x1() - r.x0(), dz = r.z1() - r.z0(), len2 = dx * dx + dz * dz;
        if (len2 < 1) return 0;
        return Math.max(0, Math.min(1, ((px - r.x0()) * dx + (pz - r.z0()) * dz) / len2));
    }

    /** Toutes les cinq secondes : chaque caravane avance, paraît près des joueurs, s'efface loin d'eux. */
    public static void tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.caravans.get()) return;
        long today = RealmService.currentDay(server);
        Set<String> live = new HashSet<>();
        for (TradeService.Route r : TradeService.routes(server)) {
            String key = key(r.a(), r.b());
            live.add(key);
            Caravan c = CARAVANS.computeIfAbsent(key, k -> new Caravan());
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, r.dim()));
            if (level == null) continue;
            if (today < c.raidedUntil) continue;
            Entity trader = c.trader == null ? null : level.getEntity(c.trader);
            if (trader != null && trader.isAlive()) {
                c.t = project(r, trader.getX(), trader.getZ());
                if (c.t >= 0.999) c.dir = -1;
                if (c.t <= 0.001) c.dir = 1;
                if (nearestPlayer(level, trader.getX(), trader.getZ()) > VANISH) vanish(level, c);
                else steer(level, r, c, (PathfinderMob) trader);
                continue;
            }
            if (!c.units.isEmpty()) vanish(level, c);
            double[] next = advance(c.t, c.dir, CrusadeConfig.SERVER.caravanSpeed.get(), r.distance());
            c.t = next[0];
            c.dir = (int) next[1];
            double x = x(r, c.t), z = z(r, c.t);
            if (nearestPlayer(level, x, z) <= APPEAR && level.hasChunkAt(BlockPos.containing(x, 0, z))) appear(server, level, r, c, key);
        }
        // Les routes qui ont disparu emportent leur caravane.
        for (Map.Entry<String, Caravan> e : new ArrayList<>(CARAVANS.entrySet())) {
            if (live.contains(e.getKey())) continue;
            for (ServerLevel level : server.getAllLevels()) vanish(level, e.getValue());
            CARAVANS.remove(e.getKey());
        }
    }

    /** Les joueurs des bancs d'essai : ils ne sont pas inscrits parmi les joueurs du monde. */
    private static final Set<ServerPlayer> TEST_WATCHERS = new HashSet<>();

    public static void watchForTests(ServerPlayer player, boolean watching) {
        if (watching) TEST_WATCHERS.add(player);
        else TEST_WATCHERS.remove(player);
    }

    static double nearestPlayer(ServerLevel level, double x, double z) {
        double best = Double.MAX_VALUE;
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer p : TEST_WATCHERS) if (p.level() == level) players.add(p);
        for (ServerPlayer p : players) {
            if (p.isSpectator()) continue;
            best = Math.min(best, Math.sqrt((p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z)));
        }
        return best;
    }

    /** Le marchand vise un point un peu plus loin sur la route, dans son sens de marche. */
    static void steer(ServerLevel level, TradeService.Route r, Caravan c, PathfinderMob trader) {
        if (trader.getTarget() != null) return;
        double ahead = Math.max(0, Math.min(1, c.t + c.dir * (double) LEAD / Math.max(1, r.distance())));
        double gx = x(r, ahead), gz = z(r, ahead);
        BlockPos goal = BlockPos.containing(gx, 0, gz);
        if (!level.hasChunkAt(goal)) return;
        trader.getNavigation().moveTo(gx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, goal.getX(), goal.getZ()), gz, 0.6);
    }

    /** La caravane paraît : un marchand ambulant à son nom, deux lamas en laisse. */
    static boolean appear(MinecraftServer server, ServerLevel level, TradeService.Route r, Caravan c, String key) {
        double x = x(r, c.t), z = z(r, c.t);
        BlockPos pos = new BlockPos((int) Math.floor(x), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)),
                (int) Math.floor(z));
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null) return false;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        trader.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        trader.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        trader.setCustomName(Component.translatable("crusadecraft.caravan.name", TradeService.name(data, r.a()), TradeService.name(data, r.b())));
        trader.setCustomNameVisible(true);
        trader.setDespawnDelay(Integer.MAX_VALUE / 2);
        trader.setPersistenceRequired();
        trader.addTag(TAG);
        trader.addTag(TAG + ":" + key);
        if (!level.addFreshEntity(trader)) return false;
        c.trader = trader.getUUID();
        c.units.add(trader.getUUID());
        for (int i = 0; i < 2; i++) {
            TraderLlama llama = EntityType.TRADER_LLAMA.create(level);
            if (llama == null) continue;
            llama.moveTo(pos.getX() + 0.5 + (i == 0 ? 2 : -2), pos.getY(), pos.getZ() + 0.5, 0, 0);
            llama.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            llama.setPersistenceRequired();
            llama.addTag(TAG);
            llama.addTag(TAG + ":" + key);
            if (!level.addFreshEntity(llama)) continue;
            llama.setLeashedTo(trader, true);
            c.units.add(llama.getUUID());
        }
        steer(level, r, c, trader);
        return true;
    }

    static void vanish(ServerLevel level, Caravan c) {
        for (UUID id : c.units) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        c.units.clear();
        c.trader = null;
    }

    /**
     * Un marchand de caravane meurt : si c'est de la main d'un joueur, la caravane est pillée. Le pillard emporte
     * l'or, la route s'arrête quelques jours, et les deux royaumes de la route en veulent au sien.
     */
    public static void onDeath(MinecraftServer server, LivingEntity dead, Entity killer) {
        if (!dead.getTags().contains(TAG) || !(dead instanceof WanderingTrader)) return;
        String key = null;
        for (String t : dead.getTags()) if (t.startsWith(TAG + ":")) key = t.substring(TAG.length() + 1);
        if (key == null) return;
        Caravan c = CARAVANS.get(key);
        if (c == null || !(killer instanceof ServerPlayer player)) return;
        Optional<TradeService.Route> route = Optional.empty();
        for (TradeService.Route r : TradeService.routes(server)) if (key(r.a(), r.b()).equals(key)) route = Optional.of(r);
        if (route.isEmpty()) return;
        raid(server, player, route.get(), c, (ServerLevel) dead.level(), dead.blockPosition());
    }

    static void raid(MinecraftServer server, ServerPlayer player, TradeService.Route r, Caravan c, ServerLevel level, BlockPos where) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        c.raidedUntil = today + CrusadeConfig.SERVER.caravanRaidDays.get();
        long gold = loot(r.income());
        for (ItemStack stack : TreasuryService.coins(gold)) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, where.getX() + 0.5, where.getY() + 0.5,
                    where.getZ() + 0.5, stack);
            level.addFreshEntity(item);
        }
        vanish(level, c);
        Optional<Realm> thief = RealmService.realmOf(server, player.getUUID());
        for (UUID victim : List.of(r.a(), r.b())) {
            // On n'en veut pas à soi-même, ni à l'ennemi qui pille en temps de guerre.
            if (thief.isEmpty() || thief.get().id().equals(victim)) continue;
            if (data.wars().between(thief.get().id(), victim).isPresent()) continue;
            data.diplomacy().between(thief.get().id(), victim).addOpinion(victim, OpinionModifier.of(OpinionReason.BRIGANDAGE, today, 1));
        }
        fr.crusadecraft.core.ai.AiBrain.publish(server, WorldNews.of(today, "crusadecraft.news.caravan_raided", List.of(r.a(), r.b()),
                TradeService.name(data, r.a()), TradeService.name(data, r.b()), Component.literal(player.getGameProfile().getName())), true);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} pille la caravane {} ({} pièces)", player.getGameProfile().getName(), key(r.a(), r.b()), gold);
        data.setDirty();
    }

    /** Une caravane pour la carte : où elle est, et si elle est pillée. */
    public record Pawn(UUID a, UUID b, int x, int z, boolean raided, boolean inWorld) {}

    public static Optional<Pawn> pawn(MinecraftServer server, TradeService.Route r) {
        if (!CrusadeConfig.SERVER.caravans.get()) return Optional.empty();
        Caravan c = CARAVANS.get(key(r.a(), r.b()));
        double t = c == null ? 0 : c.t;
        boolean raided = c != null && RealmService.currentDay(server) < c.raidedUntil;
        return Optional.of(new Pawn(r.a(), r.b(), (int) Math.round(x(r, t)), (int) Math.round(z(r, t)), raided, c != null && c.trader != null));
    }

    /** Banc d'essai : place la caravane de cette route à ce point de sa route. */
    public static void placeForTests(UUID a, UUID b, double t) {
        CARAVANS.computeIfAbsent(key(a, b), k -> new Caravan()).t = t;
    }
}
