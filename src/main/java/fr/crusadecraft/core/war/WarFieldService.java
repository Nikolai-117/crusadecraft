package fr.crusadecraft.core.war;

import fr.crusadecraft.compat.hyw.Soldiers;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * La guerre sur le terrain : sièges des villages ennemis (garnison, bannière à prendre), raids sur vos colonies,
 * vos hommes d'armes, et le registre des dégâts (explosions, blocs posés) restauré à la paix.
 *
 * <p>Toutes les troupes sont temporaires : marquées, suivies, retirées à la fin du combat, et au chargement
 * suivant si elles restent d'un arrêt brutal. Les citoyens MineColonies ne sont jamais touchés : les pillards
 * s'en prennent aux joueurs et aux gardes, et vos gardes les combattent.
 */
public final class WarFieldService {
    public static final String TAG = "crusadecraft_war";

    enum Kind { GARRISON, SQUAD, RAIDER }

    /** Une troupe en campagne : sa guerre, son rôle, le royaume qu'elle sert. */
    record Unit(UUID war, Kind kind, UUID realm) {}

    static final class Siege {
        UUID war;
        int province;
        /** Une construction assiégée (P14) plutôt qu'une province : elle change de mains si elle tombe. */
        UUID site;
        UUID player;
        UUID realm;
        UUID enemy;
        ResourceKey<Level> level;
        BlockPos seat;
        BlockPos banner;
        int captureTicks;
        final Set<UUID> garrison = new HashSet<>();
        final Set<UUID> squad = new HashSet<>();
    }

    static final class Raid {
        UUID war;
        UUID raider;
        UUID target;
        ResourceKey<Level> level;
        BlockPos center;
        String colonyName;
        long started;
        final Set<UUID> raiders = new HashSet<>();
    }

    private static final Map<UUID, Unit> UNITS = new HashMap<>();
    private static final Map<String, Siege> SIEGES = new HashMap<>();
    private static final Map<UUID, Raid> RAIDS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private WarFieldService() {}

    public static void onServerStopped() {
        UNITS.clear();
        SIEGES.clear();
        RAIDS.clear();
    }

    public static int activeSieges() {
        return SIEGES.size();
    }

    public static int activeRaids() {
        return RAIDS.size();
    }

    // ------------------------------------------------------------------ troupes

    public static boolean isStray(Entity e) {
        return e.getTags().contains(TAG) && !UNITS.containsKey(e.getUUID());
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Fait paraître une troupe : inscrite avant d'entrer, pour que le garde-fou des égarés la laisse passer. */
    private static Mob spawn(ServerLevel level, Mob mob, BlockPos pos, Unit unit, Component name) {
        if (mob == null) return null;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
        mob.setCustomName(name);
        mob.setCustomNameVisible(false);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        UNITS.put(mob.getUUID(), unit);
        if (!level.addFreshEntity(mob)) {
            UNITS.remove(mob.getUUID());
            return null;
        }
        return mob;
    }

    private static void discard(MinecraftServer server, ResourceKey<Level> key, Set<UUID> ids) {
        ServerLevel level = server.getLevel(key);
        for (UUID id : ids) {
            UNITS.remove(id);
            if (level == null) continue;
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        ids.clear();
    }

    private static int alive(ServerLevel level, Set<UUID> ids) {
        int n = 0;
        for (Iterator<UUID> it = ids.iterator(); it.hasNext(); ) {
            Entity e = level.getEntity(it.next());
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    /** Une troupe tombe : elle compte dans le score, et un homme d'armes perdu manque à l'armée. */
    public static void onDeath(MinecraftServer server, LivingEntity entity) {
        Unit unit = UNITS.remove(entity.getUUID());
        if (unit == null) return;
        Optional<War> war = WarService.data(server).wars().get(unit.war());
        if (war.isEmpty()) return;
        int side = war.get().side(unit.realm());
        war.get().addBattle(-side);
        if (unit.kind() == Kind.SQUAD) {
            WarService.player(server, unit.realm()).ifPresent(r -> r.setArmyMen(Math.max(0, WarService.men(r) - 10)));
        }
        WarService.data(server).setDirty();
    }

    /**
     * Les golems d'un village le défendent, pas contre sa propre garnison : entre eux, ni cible ni coups. Vos
     * hommes d'armes (golems de joueur) et vos gardes, eux, combattent tout ce qui est marqué ennemi.
     */
    public static boolean sameSide(Entity attacker, Entity target) {
        if (attacker == null || target == null) return false;
        Unit a = UNITS.get(attacker.getUUID()), t = UNITS.get(target.getUUID());
        if (a != null && a.kind() != Kind.SQUAD && target instanceof IronGolem g && !g.isPlayerCreated()) return true;
        return t != null && t.kind() != Kind.SQUAD && attacker instanceof IronGolem g && !g.isPlayerCreated();
    }

    // ------------------------------------------------------------------ boucle

    /** Appelé chaque seconde. */
    public static void tick(MinecraftServer server) {
        if (CrusadeConfig.SERVER.sieges.get()) {
            startSieges(server);
            updateSieges(server);
        }
        if (CrusadeConfig.SERVER.raids.get()) raids(server);
    }

    // ------------------------------------------------------------------ sièges

    private static String key(UUID war, int province) {
        return war + "|" + province;
    }

    private static String siteKey(UUID war, UUID site) {
        return war + "|site:" + site;
    }

    private static void startSieges(MinecraftServer server) {
        int radius = CrusadeConfig.SERVER.siegeRadius.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Realm> realm = RealmService.realmOf(server, player.getUUID());
            if (realm.isEmpty()) continue;
            for (War w : WarService.data(server).wars().involving(realm.get().id())) {
                for (UUID enemy : WarService.participants(w)) {
                    if (!w.opposes(realm.get().id(), enemy)) continue;
                    Optional<AiRealm> ai = WarService.ai(server, enemy);
                    if (ai.isEmpty()) continue;
                    for (AiProvince p : ai.get().ownProvinces()) {
                        if (w.occupied().contains(p.id()) || SIEGES.containsKey(key(w.id(), p.id()))) continue;
                        if (!p.dimension().equals(player.level().dimension().location())) continue;
                        if (p.distanceSqr(player.getX(), player.getZ()) > (double) radius * radius) continue;
                        int count = WarRules.clamp((int) Math.round(p.population() / 6.0 * (1 - w.defenderExhaustion() / 150.0)), 3, 10);
                        beginSiege(server, player, realm.get(), w, ai.get(), p.seat(), count, p.id(), null, Component.literal(p.name()));
                    }
                    // Ses places fortes (P14) : tours, camps, avant-postes, forts, donjons.
                    for (fr.crusadecraft.core.build.Site site : WarService.data(server).world().sites()) {
                        if (!site.built() || !site.kind().capturable() || !enemy.equals(site.owner())) continue;
                        if (SIEGES.containsKey(siteKey(w.id(), site.id()))) continue;
                        if (!site.dimension().equals(player.level().dimension().location())) continue;
                        if (site.distanceSqr(player.getX(), player.getZ()) > (double) radius * radius) continue;
                        int count = Math.max(2, (int) Math.round(site.kind().garrison() * (1 - w.defenderExhaustion() / 150.0)));
                        beginSiege(server, player, realm.get(), w, ai.get(), site.anchor(), count, -1, site.id(),
                                fr.crusadecraft.core.build.SiteService.displayName(site));
                    }
                }
            }
        }
    }

    private static DyeColor dye(int rgb) {
        return fr.crusadecraft.core.build.Banners.dye(rgb);
    }

    /** Le pied du siège : le point le plus bas autour du centre du village (une place, une rue), jamais un toit. */
    private static BlockPos bannerSpot(ServerLevel level, BlockPos seat) {
        BlockPos best = null;
        for (int r = 0; r <= 8; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos p = surface(level, seat.getX() + dx, seat.getZ() + dz);
                    if (!level.isLoaded(p) || !level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty()
                            || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                    if (best == null || p.getY() < best.getY()) best = p;
                }
            }
        }
        return best;
    }

    /** La bannière d'un siège en cours sur cette province, s'il y en a un. */
    public static Optional<BlockPos> bannerOf(int province) {
        for (Siege s : SIEGES.values()) if (s.province == province) return Optional.ofNullable(s.banner);
        return Optional.empty();
    }

    /**
     * Un siège commence devant ce siège (une province, ou une construction si {@code site} n'est pas nul) : sa garnison
     * sort, la bannière de l'ennemi est plantée, vos hommes d'armes vous rejoignent.
     */
    private static void beginSiege(MinecraftServer server, ServerPlayer player, Realm realm, War w, AiRealm enemy, BlockPos seat, int count,
                                   int province, UUID site, Component placeName) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(seat)) return;
        BlockPos top = bannerSpot(level, seat);
        if (top == null) top = surface(level, seat.getX(), seat.getZ());
        Siege s = new Siege();
        s.war = w.id();
        s.province = province;
        s.site = site;
        s.player = player.getUUID();
        s.realm = realm.id();
        s.enemy = enemy.id();
        s.level = level.dimension();
        s.seat = seat;
        // La bannière du royaume ennemi, plantée au cœur du siège ; notée pour être retirée à coup sûr.
        if (level.getBlockState(top).canBeReplaced()) {
            WarService.data(server).wars().damage().record(w.id(), level.dimension().location(), top, level.getBlockState(top), null);
            level.setBlockAndUpdate(top, BannerBlock.byColor(dye(enemy.color())).defaultBlockState());
            s.banner = top;
        } else {
            s.banner = top.above();
        }
        Component garrisonName = Component.translatable("crusadecraft.war.unit.garrison", AiNames.realmOf(enemy.rank(), enemy.place()));
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, r = 3 + RANDOM.nextDouble() * 6;
            BlockPos pos = surface(level, (int) Math.floor(top.getX() + Math.cos(a) * r), (int) Math.floor(top.getZ() + Math.sin(a) * r));
            if (!level.isLoaded(pos)) continue;
            Mob m = spawn(level, Soldiers.enemy(level, i), pos, new Unit(w.id(), Kind.GARRISON, enemy.id()), garrisonName);
            if (m != null) {
                m.setTarget(player);
                s.garrison.add(m.getUUID());
            }
        }
        // Vos hommes d'armes, si l'armée est levée.
        int men = WarService.men(realm);
        if (realm.armyRaised() && men >= 10) {
            int squad = Math.max(1, Math.min(6, men / 15));
            Component squadName = Component.translatable("crusadecraft.war.unit.squad", realm.name());
            for (int i = 0; i < squad; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                BlockPos pos = surface(level, (int) Math.floor(player.getX() + Math.cos(a) * 3), (int) Math.floor(player.getZ() + Math.sin(a) * 3));
                Mob m = spawn(level, Soldiers.ally(level, player, i), pos, new Unit(w.id(), Kind.SQUAD, realm.id()), squadName);
                if (m != null) s.squad.add(m.getUUID());
            }
        }
        SIEGES.put(site != null ? siteKey(w.id(), site) : key(w.id(), province), s);
        WarService.data(server).setDirty();
        WarService.notify(server, realm.id(), ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.siege.title", placeName),
                Component.translatable("crusadecraft.toast.war.siege.body", s.garrison.size()));
    }

    private static void updateSieges(MinecraftServer server) {
        int captureTicks = CrusadeConfig.SERVER.captureSeconds.get() * 20;
        int leash = CrusadeConfig.SERVER.siegeRadius.get() * 2;
        for (Iterator<Map.Entry<String, Siege>> it = SIEGES.entrySet().iterator(); it.hasNext(); ) {
            Siege s = it.next().getValue();
            Optional<War> war = WarService.data(server).wars().get(s.war);
            ServerLevel level = server.getLevel(s.level);
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            if (war.isEmpty() || level == null) {
                endSiege(server, s, false);
                it.remove();
                continue;
            }
            boolean present = player != null && player.isAlive() && player.level() == level
                    && player.distanceToSqr(s.seat.getX(), player.getY(), s.seat.getZ()) <= (double) leash * leash;
            if (!present) {
                endSiege(server, s, false);
                it.remove();
                continue;
            }
            // Les hommes d'armes chargent la garnison.
            Mob nearestEnemy = null;
            for (UUID id : s.garrison) {
                Entity e = level.getEntity(id);
                if (e instanceof Mob m && m.isAlive() && m.distanceToSqr(s.banner.getX(), s.banner.getY(), s.banner.getZ()) <= 24 * 24) {
                    nearestEnemy = m;
                    break;
                }
            }
            for (UUID id : s.squad) {
                Entity e = level.getEntity(id);
                if (e instanceof Mob m && m.isAlive() && (m.getTarget() == null || !m.getTarget().isAlive()) && nearestEnemy != null) m.setTarget(nearestEnemy);
            }
            double bx = player.getX() - (s.banner.getX() + 0.5), bz = player.getZ() - (s.banner.getZ() + 0.5);
            boolean atBanner = bx * bx + bz * bz <= 6 * 6 && Math.abs(player.getY() - s.banner.getY()) <= 4;
            if (atBanner && nearestEnemy == null) {
                s.captureTicks += 20;
                int percent = Math.min(100, s.captureTicks * 100 / captureTicks);
                player.displayClientMessage(Component.translatable("crusadecraft.war.capture.progress", percent), true);
            } else if (s.captureTicks > 0) {
                s.captureTicks = Math.max(0, s.captureTicks - 10);
            }
            if (s.captureTicks >= captureTicks) {
                capture(server, war.get(), s);
                endSiege(server, s, true);
                it.remove();
            }
        }
    }

    private static void capture(MinecraftServer server, War w, Siege s) {
        long today = RealmService.currentDay(server);
        if (s.site != null) {
            // Une place forte prise change aussitôt de mains, et ses terres avec elle.
            var site = WarService.data(server).world().site(s.site);
            Component siteName = site.map(fr.crusadecraft.core.build.SiteService::displayName).orElse(Component.literal("?"));
            fr.crusadecraft.core.build.SiteService.capture(server, s.site, s.realm);
            w.addBattle(w.side(s.realm) * 4);
            w.log(today, "crusadecraft.war.log.captured", w.side(s.realm) * 4, siteName.getString());
            WarService.data(server).setDirty();
            WarService.notifyBoth(server, w, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.captured.title"),
                    Component.translatable("crusadecraft.toast.war.captured.body", siteName, WarService.name(server, s.realm)));
            return;
        }
        w.occupied().add(s.province);
        w.addBattle(w.side(s.realm) * 5);
        String name = WarService.province(server, s.province).map(AiProvince::name).orElse("?");
        w.log(today, "crusadecraft.war.log.captured", w.side(s.realm) * 5, name);
        WarService.data(server).setDirty();
        WarService.notifyBoth(server, w, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.captured.title"),
                Component.translatable("crusadecraft.toast.war.captured.body", name, WarService.name(server, s.realm)));
    }

    /** Fin d'un siège : la garnison se retire, vos hommes rentrent, la bannière est retirée. */
    private static void endSiege(MinecraftServer server, Siege s, boolean captured) {
        discard(server, s.level, s.garrison);
        discard(server, s.level, s.squad);
        ServerLevel level = server.getLevel(s.level);
        if (level != null && s.banner != null) {
            DamageLedger.Entry banner = WarService.data(server).wars().damage().remove(level.dimension().location(), s.banner);
            if (banner != null) restore(server, banner);
        }
        if (!captured) {
            WarService.notify(server, s.realm, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.siege_lifted.title"),
                    Component.translatable("crusadecraft.toast.war.siege_lifted.body"));
        }
    }

    // ------------------------------------------------------------------ raids

    private static void raids(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        long now = overworld.getGameTime();
        long interval = CrusadeConfig.SERVER.raidMinutes.get() * 1200L;
        for (War w : new ArrayList<>(WarService.data(server).wars().all())) {
            Raid active = RAIDS.get(w.id());
            if (active != null) {
                updateRaid(server, w, active, now);
                continue;
            }
            if (w.lastRaidTick() == Long.MIN_VALUE) {
                // Le premier raid laisse le temps de s'organiser.
                w.setLastRaidTick(now);
                continue;
            }
            if (now - w.lastRaidTick() < interval) continue;
            for (int side : new int[]{1, -1}) {
                UUID raider = side > 0 ? w.attacker() : w.defender();
                UUID target = side > 0 ? w.defender() : w.attacker();
                Optional<AiRealm> ai = WarService.ai(server, raider);
                Optional<Realm> realm = WarService.player(server, target);
                if (ai.isEmpty() || realm.isEmpty()) continue;
                int strength = WarService.strength(server, raider, side > 0 ? w.attackerExhaustion() : w.defenderExhaustion());
                if (strength < 8) continue;
                w.setLastRaidTick(now);
                if (!raidInWorld(server, w, ai.get(), realm.get(), strength, now)) raidAbstract(server, w, ai.get(), realm.get(), strength);
                WarService.data(server).setDirty();
                break;
            }
        }
    }

    /** Un raid en chair et en os, si le souverain est près d'une de ses colonies pour la défendre. */
    private static boolean raidInWorld(MinecraftServer server, War w, AiRealm raider, Realm target, int strength, long now) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(target.ruler());
        if (ruler == null) return false;
        ServerLevel level = ruler.serverLevel();
        ColonyRef best = null;
        BlockPos center = null;
        double bestD = 160 * 160;
        for (ColonyRef c : target.allColonies()) {
            if (!c.dimension().equals(level.dimension().location())) continue;
            Optional<BlockPos> pos = MineColoniesBridge.center(c);
            if (pos.isEmpty()) continue;
            double d = ruler.distanceToSqr(pos.get().getX(), ruler.getY(), pos.get().getZ());
            if (d < bestD) {
                bestD = d;
                best = c;
                center = pos.get();
            }
        }
        if (best == null) return false;
        AiProvince capital = raider.capital();
        double angle = capital == null ? RANDOM.nextDouble() * Math.PI * 2
                : Math.atan2(capital.seat().getZ() - center.getZ(), capital.seat().getX() - center.getX());
        BlockPos origin = surface(level, (int) (center.getX() + Math.cos(angle) * 40), (int) (center.getZ() + Math.sin(angle) * 40));
        if (!level.isLoaded(origin)) return false;
        Raid raid = new Raid();
        raid.war = w.id();
        raid.raider = raider.id();
        raid.target = target.id();
        raid.level = level.dimension();
        raid.center = center;
        raid.colonyName = MineColoniesBridge.info(best).name();
        raid.started = now;
        int count = WarRules.clamp(strength / 12, 3, 10);
        Component name = Component.translatable("crusadecraft.war.unit.raider", AiNames.realmOf(raider.rank(), raider.place()));
        for (int i = 0; i < count; i++) {
            BlockPos pos = surface(level, origin.getX() + RANDOM.nextInt(9) - 4, origin.getZ() + RANDOM.nextInt(9) - 4);
            if (!level.isLoaded(pos)) continue;
            Mob m = spawn(level, Soldiers.enemy(level, i), pos, new Unit(w.id(), Kind.RAIDER, raider.id()), name);
            if (m != null) {
                m.getNavigation().moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.0);
                raid.raiders.add(m.getUUID());
            }
        }
        if (raid.raiders.isEmpty()) return false;
        RAIDS.put(w.id(), raid);
        WarService.notify(server, target.id(), ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.war.raid.title"),
                Component.translatable("crusadecraft.toast.war.raid.body", AiNames.realmOf(raider.rank(), raider.place()), raid.colonyName, raid.raiders.size()));
        return true;
    }

    /** Un raid loin de tout regard : vos gardes et votre armée contre les pillards, sur le papier. */
    private static void raidAbstract(MinecraftServer server, War w, AiRealm raider, Realm target, int strength) {
        int guards = 0;
        for (ColonyRef c : target.allColonies()) {
            ColonyReport r = MineColoniesBridge.report(c);
            guards += r.guards();
        }
        int men = WarService.men(target);
        double defense = guards * 4 + (target.armyRaised() ? men / 2.0 : men / 6.0);
        double attack = strength / 3.0 * (0.75 + RANDOM.nextDouble() * 0.5);
        resolveRaid(server, w, raider, target, defense >= attack, MineColoniesBridge.info(target.capital()).name());
    }

    private static void updateRaid(MinecraftServer server, War w, Raid raid, long now) {
        ServerLevel level = server.getLevel(raid.level);
        if (level == null) {
            RAIDS.remove(w.id());
            return;
        }
        int alive = 0, near = 0;
        for (UUID id : raid.raiders) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            alive++;
            if (m.distanceToSqr(raid.center.getX(), m.getY(), raid.center.getZ()) <= 48 * 48) near++;
            if (m.getTarget() == null && m.getNavigation().isDone()) {
                m.getNavigation().moveTo(raid.center.getX() + 0.5, raid.center.getY(), raid.center.getZ() + 0.5, 1.0);
            }
        }
        boolean timeUp = now - raid.started >= 4800;
        if (alive > 0 && !timeUp) return;
        Optional<AiRealm> raider = WarService.ai(server, raid.raider);
        Optional<Realm> target = WarService.player(server, raid.target);
        discard(server, raid.level, raid.raiders);
        RAIDS.remove(w.id());
        if (raider.isEmpty() || target.isEmpty()) return;
        resolveRaid(server, w, raider.get(), target.get(), alive == 0 || near == 0, raid.colonyName);
    }

    private static void resolveRaid(MinecraftServer server, War w, AiRealm raider, Realm target, boolean repelled, String colony) {
        long today = RealmService.currentDay(server);
        int side = w.side(target.id());
        Component who = AiNames.realmOf(raider.rank(), raider.place());
        if (repelled) {
            w.addBattle(side * 8);
            w.log(today, "crusadecraft.war.log.raid_repelled", side * 8, colony);
            WarService.notify(server, target.id(), ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.raid_repelled.title"),
                    Component.translatable("crusadecraft.toast.war.raid_repelled.body", colony, who));
        } else {
            long loot = TreasuryService.charge(server, target, LedgerCategory.PILLAGE, Math.round(Math.max(0, target.gold()) * 0.1));
            raider.setGold(raider.gold() + loot);
            w.addBattle(-side * 8);
            w.log(today, "crusadecraft.war.log.raid_lost", -side * 8, colony);
            WarService.notify(server, target.id(), ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.war.raid_lost.title"),
                    Component.translatable("crusadecraft.toast.war.raid_lost.body", colony, who, loot));
        }
        WarService.data(server).setDirty();
    }

    // ------------------------------------------------------------------ fin de guerre et dégâts

    public static void onWarEnded(MinecraftServer server, UUID war) {
        for (Iterator<Map.Entry<String, Siege>> it = SIEGES.entrySet().iterator(); it.hasNext(); ) {
            Siege s = it.next().getValue();
            if (!s.war.equals(war)) continue;
            discard(server, s.level, s.garrison);
            discard(server, s.level, s.squad);
            it.remove();
        }
        Raid raid = RAIDS.remove(war);
        if (raid != null) discard(server, raid.level, raid.raiders);
        UNITS.values().removeIf(u -> u.war().equals(war));
    }

    /** Remet quelques blocs en place à chaque appel (la file survit aux redémarrages). */
    public static void restoreTick(MinecraftServer server) {
        DamageLedger ledger = WarService.data(server).wars().damage();
        if (ledger.pending() == 0) return;
        for (DamageLedger.Entry e : ledger.take(48)) restore(server, e);
        WarService.data(server).setDirty();
    }

    static void restore(MinecraftServer server, DamageLedger.Entry e) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, e.dimension()));
        if (level == null) return;
        BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), e.state());
        level.setBlock(e.pos(), state, 3);
        if (e.blockEntity() != null) {
            BlockEntity be = level.getBlockEntity(e.pos());
            if (be != null) {
                be.load(e.blockEntity());
                be.setChanged();
            }
        }
    }

    /** En territoire ennemi, en guerre, on ne casse que par explosifs. Les blocs posés là pendant la guerre se reprennent. */
    public static boolean breakAllowed(MinecraftServer server, ServerPlayer player, BlockPos pos) {
        if (!CrusadeConfig.SERVER.antiGrief.get() || (player.isCreative() && player.hasPermissions(2))) return true;
        Optional<Realm> realm = RealmService.realmOf(server, player.getUUID());
        if (realm.isEmpty()) return true;
        ResourceLocation dim = player.level().dimension().location();
        if (!WarService.enemyTerritory(server, realm.get().id(), dim, pos)) return true;
        return WarService.data(server).wars().damage().has(dim, pos);
    }

    /** Un bloc posé en territoire ennemi sera retiré à la paix. */
    public static void onPlace(MinecraftServer server, ServerPlayer player, Level level, BlockPos pos, BlockState replaced) {
        if (!CrusadeConfig.SERVER.antiGrief.get()) return;
        Optional<Realm> realm = RealmService.realmOf(server, player.getUUID());
        if (realm.isEmpty()) return;
        ResourceLocation dim = level.dimension().location();
        if (!WarService.enemyTerritory(server, realm.get().id(), dim, pos)) return;
        WarService.warZone(server, dim, pos).ifPresent(w -> {
            WarService.data(server).wars().damage().record(w.id(), dim, pos, replaced, null);
            WarService.data(server).setDirty();
        });
    }

    /**
     * Une explosion dans un théâtre de guerre : chaque bloc touché est noté puis retiré sans rien lâcher (pas de
     * doublons à la restauration). La TNT reste au jeu normal, pour que les charges s'enchaînent.
     */
    public static void onExplosion(MinecraftServer server, Level level, List<BlockPos> affected) {
        if (!CrusadeConfig.SERVER.antiGrief.get() || WarService.data(server).wars().all().isEmpty()) return;
        ResourceLocation dim = level.dimension().location();
        List<BlockPos> handled = new ArrayList<>();
        for (BlockPos pos : affected) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.TNT)) continue;
            Optional<War> w = WarService.warZone(server, dim, pos);
            if (w.isEmpty()) continue;
            BlockEntity be = level.getBlockEntity(pos);
            CompoundTag nbt = be == null ? null : be.saveWithFullMetadata();
            if (be instanceof Container container) container.clearContent();
            WarService.data(server).wars().damage().record(w.get().id(), dim, pos, state, nbt);
            handled.add(pos);
        }
        if (handled.isEmpty()) return;
        affected.removeAll(handled);
        for (BlockPos pos : handled) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        WarService.data(server).setDirty();
    }
}
