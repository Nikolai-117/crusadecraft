package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les gens prennent la route (P48 et P50). Une caravane, c'est un marchand de la ville, ses lamas bâtés et, si la ville a une
 * caserne, une escorte : elle emporte le surplus, le vend là-bas à quelqu'un, et rapporte ce qui manque chez elle. D'autres
 * vont voir du pays. Loin de tout joueur, ce n'est qu'un point qui avance sur la route, et le commerce a lieu quand même. Un joueur
 * qui passe à moins de 64 blocs la croise en chair et en os ; elle marche vraiment, et ne disparaît jamais sous ses yeux.
 */
public final class CityTravel {
    /** Un marcheur fait deux blocs et demi par seconde : tant de ticks par bloc. */
    static final int TICKS_PER_BLOCK = 8;
    /** Une route ne dure ni moins d'une minute, ni plus d'un jour. */
    static final int ROAD_MIN = 1200, ROAD_MAX = 24000;
    /** Il reste sur place de cinq à dix minutes. */
    static final int STAY_MIN = 6000, STAY_SPREAD = 6000;
    /** Un joueur à moins de tant de blocs : il paraît ; plus aucun à moins de tant : il redevient un point sur la route. */
    static final int APPEAR = 64, VANISH = 112;
    /** Plus loin que ça de là où il devrait être, il rattrape son retard d'un coup. */
    static final int LAG = 24;
    /** Un joueur à moins de tant de blocs de lui le voit. */
    static final int UNSEEN = 48;
    /** Sans avoir bougé depuis tant de ticks, il est coincé (une rivière, une falaise) : il franchit l'obstacle, même sous les yeux de quelqu'un. */
    static final int STUCK = 600;
    /** Sans avoir bougé depuis tant de ticks, il cherche un détour. */
    static final int DETOUR = 100;
    /** Au plus tant de voyageurs d'une ville à la fois, escorte comprise. */
    static final int AT_ONCE = 5;
    /** On se décide tous les tant de ticks : quatre fois par journée de jeu. */
    public static final int PULSE = 6000;
    /** Chances à chaque fois, sur cent : un marchand part, quelqu'un d'autre part. */
    static final int MERCHANT = 30, OTHER = 10;
    /** Il n'emporte que le surplus : ce qui dépasse ce stock, et rapporte tant par pièce ; moins, sans traité de commerce. */
    static final int SURPLUS = 48, LOAD = 24, PRICE = 2, PRICE_FOREIGN = 1;
    /** Ce qu'emporte, au plus, celui qui part acheter ce qui manque. */
    static final int PURSE_CARRIED = 40;
    /** Au plus tant de gardes dans une escorte. */
    static final int ESCORT = 2;
    /** La vitesse de marche sur la route. */
    static final float ROAD_SPEED = 0.65f;
    /** Les bêtes d'une caravane portent cette étiquette, suivie de l'identifiant de leur marchand. */
    public static final String PACK_TAG = "crusadecraft_bat";

    private static final Random RANDOM = new Random();
    /** Là où chacun était, et depuis quand : pour voir s'il est coincé. */
    private static final Map<UUID, long[]> MOVED = new HashMap<>();
    private static int sweepIn;

    private CityTravel() {}

    public static void onServerStopped() {
        MOVED.clear();
        sweepIn = 0;
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** La durée d'une route de cette longueur. */
    public static int roadTicks(double distance) {
        return (int) Math.max(ROAD_MIN, Math.min(ROAD_MAX, distance * TICKS_PER_BLOCK));
    }

    /** Une fois par jour de jeu. */
    static void onDay(MinecraftServer server, Site home, Random random) {
        maybeDepart(server, home, random);
    }

    /** Quatre fois par jour de jeu : chaque ville voit si quelqu'un part. Sans joueur aussi : le commerce n'attend personne. */
    public static void pulse(MinecraftServer server) {
        for (Site s : new ArrayList<>(data(server).world().sites())) {
            if (CityService.city(s) && !s.buildings().isEmpty()) maybeDepart(server, s, RANDOM);
        }
    }

    private static boolean guardJob(BuildingFunction f) {
        return f == BuildingFunction.CASERNE || f == BuildingFunction.TOUR || f == BuildingFunction.CHATEAU;
    }

    private static boolean free(Person p) {
        if (p.trip() != null || p.age() < 16 || p.age() >= CityRules.RETIREMENT) return false;
        return !p.hasSettings() || (p.settings().canTravel && p.settings().behavior == NpcSettings.Behavior.SCHEDULE);
    }

    private static void maybeDepart(MinecraftServer server, Site home, Random random) {
        if (!CrusadeConfig.SERVER.routines.get() || !home.settings().canTravel) return;
        LineageState state = data(server).lineage();
        int away = 0;
        List<Person> merchants = new ArrayList<>(), others = new ArrayList<>();
        for (CityBuilding b : home.buildings()) {
            if (b.function() == null) continue;
            for (UUID id : b.occupants()) {
                Person p = state.person(id).filter(Person::alive).orElse(null);
                if (p == null) continue;
                if (p.trip() != null) {
                    away++;
                    continue;
                }
                if (!free(p) || guardJob(b.function())) continue;
                if (b.function() == BuildingFunction.MARCHE || b.function() == BuildingFunction.ENTREPOT) merchants.add(p);
                else others.add(p);
            }
        }
        if (away >= AT_ONCE) return;
        if (!merchants.isEmpty() && random.nextInt(100) < MERCHANT) {
            Person p = merchants.get(random.nextInt(merchants.size()));
            destination(server, home, random).ifPresent(dest -> depart(server, home, dest, p, true));
        } else if (!others.isEmpty() && random.nextInt(100) < OTHER) {
            Person p = others.get(random.nextInt(others.size()));
            destination(server, home, random).ifPresent(dest -> depart(server, home, dest, p, false));
        }
    }

    /** Où aller : une ville du même monde, qui ne soit pas en guerre avec la sienne. */
    public static Optional<Site> destination(MinecraftServer server, Site home, Random random) {
        CrusadeSavedData data = data(server);
        List<Site> out = new ArrayList<>();
        for (Site s : data.world().sites()) {
            if (s == home || !CityService.city(s) || s.buildings().isEmpty() || !s.dimension().equals(home.dimension())) continue;
            if (!s.owner().equals(home.owner()) && data.diplomacy().between(s.owner(), home.owner()).war()) continue;
            out.add(s);
        }
        return out.isEmpty() ? Optional.empty() : Optional.of(out.get(random.nextInt(out.size())));
    }

    /** Il part : un marchand charge ce que sa ville a en trop ; s'il n'y a rien, il emporte de quoi acheter ce qui manque. */
    public static void depart(MinecraftServer server, Site home, Site dest, Person p, boolean merchant) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension()));
        if (level == null) return;
        CityGood good = null;
        int amount = 0, money = 0;
        if (merchant) {
            good = richest(home.economy());
            if (good != null) amount = home.economy().take(good, LOAD);
            else if (lacking(home.economy()) != null) {
                money = (int) Math.min(PURSE_CARRIED, home.economy().purse() / 4);
                home.economy().addPurse(-money);
            }
        }
        long now = level.getGameTime();
        int road = roadTicks(Math.sqrt(home.anchor().distSqr(dest.anchor())));
        Trip trip = new Trip(home.id(), dest.id(), Trip.Stage.OUT, now, now + road, good, amount, null, 0, money);
        p.setTrip(trip);
        CityRumors.add(server, home, "voyage", p.name(), dest.name());
        if (good != null) CityJournal.add(server, home, CityJournal.Kind.ROAD, Fr.key("crusadecraft.city.journal.caravan", p.name(), Fr.key(good.key() + ".du"), amount, dest.name()));
        if (good != null || money > 0) recruit(server, home, p, trip);
        CityPost.travelNews(server, home, dest, p);
        data(server).setDirty();
    }

    /** Une caravane emmène une escorte, si sa ville a des gardes de libres. */
    private static void recruit(MinecraftServer server, Site home, Person lead, Trip t) {
        LineageState state = data(server).lineage();
        int n = 0;
        for (CityBuilding b : home.buildings()) {
            if (b.function() == null || !guardJob(b.function())) continue;
            for (UUID id : b.occupants()) {
                Person g = state.person(id).filter(Person::alive).orElse(null);
                if (g == null || !free(g)) continue;
                g.setTrip(new Trip(t.home(), t.dest(), t.stage(), t.start(), t.end(), null, 0, null, 0, 0, lead.id()));
                if (++n >= ESCORT) return;
            }
        }
    }

    private static CityGood richest(CityEconomy e) {
        CityGood best = null;
        for (CityGood g : CityGood.values()) {
            if (e.stock(g) > SURPLUS && (best == null || e.stock(g) > e.stock(best))) best = g;
        }
        return best;
    }

    private static CityGood lacking(CityEconomy e) {
        CityGood worst = null;
        for (CityGood g : CityGood.values()) {
            if (e.stock(g) < CityEconomy.LOW && (worst == null || e.stock(g) < e.stock(worst))) worst = g;
        }
        return worst;
    }

    // ------------------------------------------------------------------ en route

    /** Toutes les deux secondes, pour tous ceux qui sont en route ou en visite. */
    public static void tick(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        for (Person p : new ArrayList<>(data.lineage().persons())) {
            Trip t = p.trip();
            if (t == null) continue;
            if (!p.alive()) {
                p.setTrip(null);
                continue;
            }
            Optional<Site> home = data.world().site(t.home()), dest = data.world().site(t.dest());
            ServerLevel level = home.map(s -> server.getLevel(ResourceKey.create(Registries.DIMENSION, s.dimension()))).orElse(null);
            if (home.isEmpty() || level == null) {
                // Sa ville n'existe plus : s'il était là, il s'en va aussi.
                ServerLevel where = p.inWorld() ? server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim())) : null;
                if (where != null) vanish(where, p);
                p.setTrip(null);
                data.setDirty();
                continue;
            }
            if (dest.isEmpty()) {
                // La ville visitée a disparu : il rentre chez lui, avec ce qu'il portait.
                giveBack(home.get(), t);
                vanish(level, p);
                p.setTrip(null);
                data.setDirty();
                continue;
            }
            long now = level.getGameTime();
            if (t.leader() != null) {
                follow(server, level, p, home.get(), dest.get(), t, now);
                continue;
            }
            switch (t.stage()) {
                case OUT -> {
                    road(server, level, p, home.get(), dest.get(), t, now);
                    if (now >= t.end()) arrive(server, level, p, home.get(), dest.get(), t, now);
                }
                case STAY -> stay(server, level, p, home.get(), dest.get(), t, now);
                case BACK -> {
                    road(server, level, p, dest.get(), home.get(), t, now);
                    if (now >= t.end()) comeHome(server, level, p, home.get(), dest.get(), t);
                }
            }
        }
        if (--sweepIn <= 0) {
            sweepIn = 5;
            sweepPacks(server);
        }
    }

    /** Un garde d'escorte suit son marchand : mêmes étapes, mêmes heures. */
    private static void follow(MinecraftServer server, ServerLevel level, Person p, Site home, Site dest, Trip t, long now) {
        Person lead = data(server).lineage().person(t.leader()).filter(Person::alive).orElse(null);
        Trip lt = lead == null ? null : lead.trip();
        if (lt == null) {
            // Son marchand est rentré, ou mort : il rentre aussi.
            comeBack(level, p, home);
            data(server).setDirty();
            return;
        }
        if (lt.stage() != t.stage() || lt.start() != t.start() || lt.end() != t.end()) {
            t = new Trip(t.home(), t.dest(), lt.stage(), lt.start(), lt.end(), null, 0, null, 0, 0, t.leader());
            p.setTrip(t);
        }
        switch (t.stage()) {
            case OUT -> road(server, level, p, home, dest, t, now);
            case STAY -> stay(server, level, p, home, dest, t, now);
            case BACK -> road(server, level, p, dest, home, t, now);
        }
    }

    private static void giveBack(Site home, Trip t) {
        if (t.good() != null && t.stage() == Trip.Stage.OUT) home.economy().add(t.good(), t.amount());
        if (t.earned() > 0) home.economy().addPurse(t.earned());
    }

    private static boolean playerNear(ServerLevel level, double x, double z, int radius) {
        for (ServerPlayer pl : level.players()) {
            double dx = pl.getX() - x, dz = pl.getZ() - z;
            if (dx * dx + dz * dz < (double) radius * radius) return true;
        }
        return false;
    }

    /** Sur la route : un point qui avance, sauf si un joueur est assez près pour le croiser. */
    private static void road(MinecraftServer server, ServerLevel level, Person p, Site from, Site to, Trip t, long now) {
        double f = t.progress(now);
        double x = from.anchor().getX() + (to.anchor().getX() - from.anchor().getX()) * f;
        double z = from.anchor().getZ() + (to.anchor().getZ() - from.anchor().getZ()) * f;
        Entity e = level.getEntity(p.id());
        if (e == null) {
            if (!playerNear(level, x, z, APPEAR) || !level.hasChunk((int) x >> 4, (int) z >> 4)) return;
            CityService.spawnTraveller(level, data(server).lineage(), p, surface(level, (int) x, (int) z));
            return;
        }
        // Il ne disparaît jamais sous les yeux de quelqu'un : seulement quand plus personne n'est près de la route, ni de lui.
        if (!playerNear(level, x, z, VANISH) && !playerNear(level, e.getX(), e.getZ(), UNSEEN)) {
            vanish(level, p);
            return;
        }
        if (!(e instanceof PathfinderMob mob) || CityScenes.busy(p.id())) return;
        double dx = mob.getX() - x, dz = mob.getZ() - z;
        boolean late = dx * dx + dz * dz > LAG * LAG, seen = playerNear(level, mob.getX(), mob.getZ(), UNSEEN), stuck = stuck(p.id(), mob, now);
        if (late && level.hasChunk((int) x >> 4, (int) z >> 4) && (!seen || stuck)) {
            BlockPos at = surface(level, (int) x, (int) z);
            mob.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            bringPack(level, mob, p);
            return;
        }
        // Il vise un point un peu plus loin sur la route ; les gardes marchent de part et d'autre.
        double g = Math.min(1, f + 24.0 / Math.max(1, Math.sqrt(from.anchor().distSqr(to.anchor()))));
        int side = t.leader() == null ? 0 : (p.id().hashCode() % 2 == 0 ? 3 : -3);
        int ax = (int) (from.anchor().getX() + (to.anchor().getX() - from.anchor().getX()) * g) + side, az = (int) (from.anchor().getZ() + (to.anchor().getZ() - from.anchor().getZ()) * g) - side;
        if (level.hasChunk(ax >> 4, az >> 4)) {
            BlockPos aim = surface(level, ax, az);
            // Il n'avance plus : un détour vers un point au hasard dans sa direction, sur la terre ferme, le remet en route.
            if (blocked(p.id(), now)) {
                net.minecraft.world.phys.Vec3 around = net.minecraft.world.entity.ai.util.LandRandomPos.getPosTowards(mob, 16, 7, net.minecraft.world.phys.Vec3.atBottomCenterOf(aim));
                if (around != null) aim = BlockPos.containing(around);
            }
            CityWork.toward(mob, aim, 2, ROAD_SPEED);
        }
        dress(level, mob, p, t);
        CityWork.roadTalk(server, level, from, p, mob, now);
    }

    private static boolean blocked(UUID id, long now) {
        long[] last = MOVED.get(id);
        return last != null && now - last[2] > DETOUR;
    }

    private static boolean stuck(UUID id, PathfinderMob mob, long now) {
        long[] last = MOVED.get(id);
        if (last == null || Math.abs(last[0] - mob.getBlockX()) + Math.abs(last[1] - mob.getBlockZ()) > 2) {
            MOVED.put(id, new long[]{mob.getBlockX(), mob.getBlockZ(), now});
            return false;
        }
        return now - last[2] > STUCK;
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Ce qui se voit d'un voyageur : le garde a son épée, le marchand sa marchandise et ses bêtes. */
    private static void dress(ServerLevel level, PathfinderMob mob, Person p, Trip t) {
        if (t.leader() != null) {
            if (!mob.getMainHandItem().is(Items.IRON_SWORD)) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
            }
            return;
        }
        CityGood cargo = t.stage() == Trip.Stage.BACK ? t.back() : t.good();
        int load = t.stage() == Trip.Stage.BACK ? t.backAmount() : t.amount();
        int beasts = cargo == null ? 0 : load >= LOAD ? 2 : 1;
        if (cargo != null && !mob.getMainHandItem().is(CityScenes.item(cargo))) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(CityScenes.item(cargo)));
            mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
        pack(level, mob, p, beasts);
    }

    // ------------------------------------------------------------------ les bêtes

    private static String packTag(UUID owner) {
        return PACK_TAG + ":" + owner;
    }

    /** Ses lamas, bâtés, en laisse ; il en a autant que sa charge en demande. */
    private static void pack(ServerLevel level, PathfinderMob owner, Person p, int count) {
        String tag = packTag(p.id());
        List<Llama> have = level.getEntitiesOfClass(Llama.class, owner.getBoundingBox().inflate(48), l -> l.getTags().contains(tag));
        for (int i = have.size(); i < count; i++) {
            Llama l = EntityType.LLAMA.create(level);
            if (l == null) break;
            l.moveTo(owner.getX() + (i == 0 ? 2 : -2), owner.getY(), owner.getZ() - 2, owner.getYRot(), 0);
            l.finalizeSpawn(level, level.getCurrentDifficultyAt(owner.blockPosition()), MobSpawnType.EVENT, null, null);
            l.setAge(0);
            l.setTamed(true);
            l.setChest(true);
            l.setPersistenceRequired();
            l.addTag(PACK_TAG);
            l.addTag(tag);
            carpet(l, carpetOf(p));
            if (level.addFreshEntity(l)) have.add(l);
        }
        for (Llama l : have) {
            if (l.isLeashed()) continue;
            if (l.distanceToSqr(owner) > 64) l.teleportTo(owner.getX() + 1.5, owner.getY(), owner.getZ() - 1.5);
            l.setLeashedTo(owner, true);
        }
    }

    /** Les bêtes suivent celui qui vient de franchir un obstacle. */
    private static void bringPack(ServerLevel level, PathfinderMob owner, Person p) {
        String tag = packTag(p.id());
        for (Llama l : level.getEntities(EntityType.LLAMA, x -> x.getTags().contains(tag))) l.teleportTo(owner.getX() + 1.5, owner.getY(), owner.getZ() - 1.5);
    }

    private static void carpet(Llama l, DyeColor color) {
        // Le tapis d'un lama passe par ses données enregistrées : il n'a pas d'autre porte d'entrée sans joueur.
        CompoundTag t = new CompoundTag();
        l.addAdditionalSaveData(t);
        t.put("DecorItem", new ItemStack(carpetItem(color)).save(new CompoundTag()));
        l.readAdditionalSaveData(t);
    }

    private static net.minecraft.world.item.Item carpetItem(DyeColor c) {
        return switch (c) {
            case RED -> Items.RED_CARPET;
            case BLUE -> Items.BLUE_CARPET;
            case GREEN -> Items.GREEN_CARPET;
            case YELLOW -> Items.YELLOW_CARPET;
            case PURPLE -> Items.PURPLE_CARPET;
            case ORANGE -> Items.ORANGE_CARPET;
            case BROWN -> Items.BROWN_CARPET;
            case CYAN -> Items.CYAN_CARPET;
            default -> Items.WHITE_CARPET;
        };
    }

    /** Une couleur qui ne change pas d'un trajet à l'autre pour un même marchand. */
    private static DyeColor carpetOf(Person p) {
        DyeColor[] all = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.YELLOW, DyeColor.PURPLE, DyeColor.ORANGE, DyeColor.BROWN, DyeColor.CYAN};
        return all[Math.floorMod(p.id().hashCode(), all.length)];
    }

    private static void unpack(ServerLevel level, UUID owner) {
        String tag = packTag(owner);
        for (Llama l : level.getEntities(EntityType.LLAMA, x -> x.getTags().contains(tag))) {
            l.dropLeash(true, false);
            l.discard();
        }
    }

    /** Un lama dont le marchand n'est plus en voyage (un arrêt brutal, un marchand mort) s'en va. */
    private static void sweepPacks(MinecraftServer server) {
        LineageState state = data(server).lineage();
        for (ServerLevel level : server.getAllLevels()) {
            for (Llama l : level.getEntities(EntityType.LLAMA, x -> x.getTags().contains(PACK_TAG))) {
                UUID owner = null;
                for (String tag : l.getTags()) {
                    if (!tag.startsWith(PACK_TAG + ":")) continue;
                    try {
                        owner = UUID.fromString(tag.substring(PACK_TAG.length() + 1));
                    } catch (IllegalArgumentException ignored) {
                        // Une étiquette illisible : la bête s'en va.
                    }
                }
                Person p = owner == null ? null : state.person(owner).filter(Person::alive).orElse(null);
                if (p == null || p.trip() == null) {
                    l.dropLeash(true, false);
                    l.discard();
                }
            }
        }
    }

    /** Il disparaît du monde, avec ses bêtes : le registre sait où il en est. */
    private static void vanish(ServerLevel level, Person p) {
        unpack(level, p.id());
        Entity e = level.getEntity(p.id());
        if (e != null) e.discard();
        p.setInWorld(null, null);
    }

    // ------------------------------------------------------------------ à l'arrivée

    /** Il arrive : il vend ce qu'il a, achète ce qu'il rapportera, et tout le monde le sait. */
    private static void arrive(MinecraftServer server, ServerLevel level, Person p, Site home, Site dest, Trip t, long now) {
        int earned = t.earned();
        CityGood back = null;
        int backAmount = 0;
        int price = fair(server, home, dest) ? PRICE : PRICE_FOREIGN;
        if (t.good() != null) {
            int want = Math.min(t.amount() * price, (int) Math.min(Integer.MAX_VALUE, dest.economy().purse()));
            earned += want;
            dest.economy().addPurse(-want);
            dest.economy().add(t.good(), t.amount());
            String goods = Fr.key(t.good().key() + ".du");
            CityRumors.add(server, dest, "marchand", p.name(), from(home), goods);
            CityJournal.add(server, dest, CityJournal.Kind.ROAD, Fr.key("crusadecraft.city.journal.sold", p.name(), from(home), goods, t.amount(), CityOrders.money(want)));
            CityPost.saleNews(server, home, dest, p, t.good(), t.amount(), want);
        } else if (earned == 0) {
            CityRumors.add(server, dest, "passage", p.name(), from(home));
        }
        // Ce qui manque chez lui et abonde ici : il en rapporte, en payant sur ce qu'il a.
        if (earned > 0) {
            for (CityGood g : CityGood.values()) {
                if (dest.economy().stock(g) > SURPLUS && home.economy().stock(g) < CityEconomy.LOW) {
                    int affordable = Math.min(LOAD, earned / price);
                    if (affordable <= 0) break;
                    back = g;
                    backAmount = dest.economy().take(g, affordable);
                    earned -= backAmount * price;
                    dest.economy().addPurse(backAmount * price);
                    CityJournal.add(server, dest, CityJournal.Kind.ROAD, Fr.key("crusadecraft.city.journal.bought", p.name(), Fr.key(g.key() + ".du"), backAmount, home.name()));
                    break;
                }
            }
        }
        long stay = STAY_MIN + level.random.nextInt(STAY_SPREAD);
        p.setTrip(t.next(Trip.Stage.STAY, now, now + stay, back, backAmount, earned));
        data(server).setDirty();
        // Si quelqu'un est là pour le voir, il va traiter avec le marché en personne.
        if (t.good() != null && t.amount() > 0 && level.getEntity(p.id()) instanceof PathfinderMob mob) CityScenes.wholesale(server, level, dest, p, mob, t.good(), t.amount());
    }

    /** « de Dolhimluith », « d'Ailleurs ». */
    private static String from(Site s) {
        return (LineageState.elides(s.name()) ? "d'" : "de ") + s.name();
    }

    /** Même royaume, ou traité de commerce : le juste prix. Sinon, on lui fait payer d'être étranger. */
    private static boolean fair(MinecraftServer server, Site home, Site dest) {
        if (home.owner().equals(dest.owner())) return true;
        Relation r = data(server).diplomacy().between(home.owner(), dest.owner());
        return r.has(Treaty.COMMERCE, RealmService.currentDay(server));
    }

    /** Sur place, il vit comme un visiteur. Son séjour fini, il repart. */
    private static void stay(MinecraftServer server, ServerLevel level, Person p, Site home, Site dest, Trip t, long now) {
        Entity e = level.getEntity(p.id());
        if (t.leader() == null && now >= t.end()) {
            int road = roadTicks(Math.sqrt(home.anchor().distSqr(dest.anchor())));
            p.setTrip(t.next(Trip.Stage.BACK, now, now + road, t.back(), t.backAmount(), t.earned()));
            CityJournal.add(server, dest, CityJournal.Kind.ROAD, Fr.key("crusadecraft.city.journal.leaves", p.name(), home.name()));
            data(server).setDirty();
            return;
        }
        if (!CityService.watched(level, dest)) {
            if (e != null) vanish(level, p);
            return;
        }
        if (e == null) {
            BlockPos at = CityService.plazaOf(dest);
            if (level.isLoaded(at)) CityService.spawnTraveller(level, data(server).lineage(), p, at);
            return;
        }
        if (e instanceof PathfinderMob mob) {
            if (t.leader() == null && t.good() != null) pack(level, mob, p, t.amount() >= LOAD ? 2 : 1);
            CityWork.visit(server, level, dest, home, p, mob);
        }
    }

    /** Il rentre : il remet à sa ville ce qu'il a gagné et ce qu'il rapporte, et il reprend sa vie. */
    private static void comeHome(MinecraftServer server, ServerLevel level, Person p, Site home, Site dest, Trip t) {
        home.economy().addPurse(t.earned());
        if (t.back() != null) home.economy().add(t.back(), t.backAmount());
        CityRumors.add(server, home, "retour", p.name(), dest.name());
        comeBack(level, p, home);
        // Il remet ce qu'il rapporte à quelqu'un, s'il est là pour ça.
        if (t.back() != null && t.backAmount() > 0 && level.getEntity(p.id()) instanceof PathfinderMob mob) CityScenes.wholesale(server, level, home, p, mob, t.back(), t.backAmount());
        data(server).setDirty();
    }

    /** S'il est là, il reprend sa vie où il en est, et sa maison redevient son point d'attache ; sinon, il reparaîtra chez lui. */
    private static void comeBack(ServerLevel level, Person p, Site home) {
        Entity e = level.getEntity(p.id());
        CityBuilding house = null;
        for (CityBuilding b : home.buildings()) if (b.occupants().contains(p.id())) house = b;
        unpack(level, p.id());
        p.setTrip(null);
        CityWork.afterScene(p.id());
        if (e != null && house != null) p.setInWorld(level.dimension().location(), house.spot());
        else vanish(level, p);
    }

    /** Pour les essais : le temps a passé, on tire les conséquences sans qu'aucun joueur ne soit là. */
    public static void advanceForTests(MinecraftServer server, Person p, long now) {
        Trip t = p.trip();
        p.setTrip(new Trip(t.home(), t.dest(), t.stage(), t.start(), now, t.good(), t.amount(), t.back(), t.backAmount(), t.earned(), t.leader()));
        tick(server);
    }

    // ------------------------------------------------------------------ ce qui se voit de loin

    /** Un voyageur, pour la carte et pour le panneau. */
    public record Pawn(UUID id, UUID home, UUID dest, String dim, int x, int z, int toX, int toZ, String name, String homeName, String destName, Trip.Stage stage, int percent,
                       boolean caravan, String cargo, boolean inWorld) {}

    /** Tous ceux qui sont partis ; une escorte n'a pas son propre point, elle marche avec son marchand. */
    public static List<Pawn> pawns(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        List<Pawn> out = new ArrayList<>();
        for (Person p : data.lineage().persons()) {
            Trip t = p.trip();
            if (t == null || !p.alive() || t.leader() != null) continue;
            Site home = data.world().site(t.home()).orElse(null), dest = data.world().site(t.dest()).orElse(null);
            if (home == null || dest == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension()));
            if (level == null) continue;
            long now = level.getGameTime();
            Site from = t.stage() == Trip.Stage.BACK ? dest : home, to = t.stage() == Trip.Stage.BACK ? home : dest;
            double f = t.stage() == Trip.Stage.STAY ? 1 : t.progress(now);
            double x = from.anchor().getX() + (to.anchor().getX() - from.anchor().getX()) * f, z = from.anchor().getZ() + (to.anchor().getZ() - from.anchor().getZ()) * f;
            Entity e = level.getEntity(p.id());
            if (e != null) {
                x = e.getX();
                z = e.getZ();
            }
            CityGood cargo = t.stage() == Trip.Stage.BACK ? t.back() : t.good();
            int load = t.stage() == Trip.Stage.BACK ? t.backAmount() : t.amount();
            out.add(new Pawn(p.id(), home.id(), dest.id(), home.dimension().toString(), (int) x, (int) z, to.anchor().getX(), to.anchor().getZ(), data.lineage().fullName(p), from.name(), to.name(), t.stage(),
                    (int) (f * 100), cargo != null, cargo == null ? "" : CityOrders.describe(cargo, load), e != null));
        }
        return out;
    }

    /** Aller voir ce voyageur : le joueur se retrouve à quelques blocs de lui. */
    public static boolean goSee(ServerPlayer player, UUID id) {
        for (Pawn pawn : pawns(player.server)) {
            if (!pawn.id().equals(id)) continue;
            ServerLevel level = player.serverLevel();
            level.getChunk(pawn.x() >> 4, pawn.z() >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pawn.x(), pawn.z());
            player.teleportTo(level, pawn.x() + 6.5, y + 4, pawn.z() + 6.5, 135f, 20f);
            return true;
        }
        return false;
    }
}
