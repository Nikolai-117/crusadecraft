package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * La journée des gens d'une ville (P43) : tant qu'un joueur est là pour le voir, chacun fait vraiment son ouvrage. Le
 * forgeron va de l'enclume au four, le fermier moissonne et ressème les champs qu'il trouve, le boulanger porte son pain
 * à la taverne, les gardes font leur ronde, les habitants vont au marché et paient ce qu'ils achètent, et tout ce monde
 * parle. Loin des joueurs, la ville continue en comptes (voir {@link CityLife}) : aucun villageois n'y marche pour rien.
 */
public final class CityWork {
    /** On passe voir chacun toutes les deux secondes. */
    public static final int EVERY = 40;
    /** Assez près pour travailler. */
    static final int AT = 2;
    /** Une tâche occupe de tant à tant de ticks, avant qu'il ne passe à la suivante. */
    static final int TASK_MIN = 300, TASK_SPREAD = 500;
    /** Les joueurs qui entendent ce qui se dit. */
    static final int HEARING = 14;
    /** Au plus une parole par personne en tant de ticks, et une pour toute une ville en tant d'autres. */
    static final int SPEAK_PERSON = 1400, SPEAK_CITY = 120;
    /** Au-delà, il marche par étapes. */
    static final int FAR_STEP = 30;
    /** Sans bouger depuis tant de ticks, il est coincé ; à plus de tant de blocs d'un joueur, personne ne le voit. */
    static final int STUCK = 400, UNSEEN = 24;
    /** Le rayon où un fermier cherche ses champs. */
    static final int FIELD_RADIUS = 24, FIELD_DY = 5;

    /** Ce qu'un habitant va acheter quand ce n'est pas pour manger. */
    private static final CityGood[] SHOPPING = {CityGood.OUTILS, CityGood.CUIR, CityGood.LIVRE, CityGood.BOIS, CityGood.PAIN, CityGood.VIANDE};

    /** Ce qu'il est en train de faire : où il va, jusqu'à quand, et ce qu'il porte s'il livre. */
    private static final class State {
        BlockPos target;
        long until;
        Errand errand;
        long lastSpoke = Long.MIN_VALUE / 2;
        long lastMeal = Long.MIN_VALUE / 2;
        BlockPos lastPos = BlockPos.ZERO;
        long lastMoved;
        boolean seatTried;
        CityRules.Activity activity;
        Item held;
    }

    /** Une livraison : vers quel bâtiment, avec quoi, et ce que ça rapporte à la ville à l'arrivée. */
    private record Errand(BlockPos to, Item carried, CityGood good, int amount) {}

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> POINTS = new HashMap<>();
    private static final Map<UUID, Long> CITY_SPOKE = new HashMap<>();
    private static final Random RANDOM = new Random();

    private CityWork() {}

    public static void onServerStopped() {
        STATES.clear();
        POINTS.clear();
        CITY_SPOKE.clear();
    }

    /** Les bâtiments ont changé : on recherchera leurs meubles. */
    public static void forgetPoints(UUID building) {
        POINTS.remove(building);
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ la tournée

    public static void tick(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.routines.get()) return;
        CrusadeSavedData data = data(server);
        LineageState state = data.lineage();
        for (Site site : new ArrayList<>(data.world().sites())) {
            if (site.buildings().isEmpty()) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            if (level == null || !CityService.watched(level, site)) continue;
            boolean called = data.wars().atWar(site.owner()) || fr.crusadecraft.core.court.FeastService.feasting(server, site.owner())
                    || fr.crusadecraft.core.court.FeastService.funeral(server, site.owner());
            if (called) continue;
            for (CityBuilding b : site.buildings()) {
                if (b.function() == null) continue;
                for (UUID id : b.occupants()) {
                    Person p = state.person(id).orElse(null);
                    if (p == null || !p.alive() || !p.inWorld() || p.trip() != null) continue;
                    Entity e = level.getEntity(id);
                    if (e instanceof PathfinderMob mob && mob.isAlive()) live(server, level, site, b, p, mob);
                }
            }
            CityFurniture.tick(level, site);
        }
    }

    /** Une personne, à ce moment de sa journée. */
    public static void live(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob) {
        if (mob instanceof Villager v && v.isTrading()) return;
        // P50 : il est en train de traiter avec quelqu'un.
        if (CityScenes.busy(p.id())) return;
        State s = STATES.computeIfAbsent(p.id(), k -> new State());
        long now = level.getGameTime();
        CityRules.Activity a = activity(level.getDayTime(), p);
        if (a != s.activity) {
            s.activity = a;
            s.until = 0;
            s.errand = null;
            s.target = null;
            s.seatTried = false;
            if (CitySeats.seated(mob)) mob.stopRiding();
            hold(mob, s, a == CityRules.Activity.WORK ? tool(b.function()) : null);
        }
        if (mob.isSleeping() && a != CityRules.Activity.SLEEP) mob.stopSleeping();
        unstick(level, mob, s, now);
        // P45 : la cloche a sonné : tout le monde y va, et on verra ensuite.
        CityFurniture.Gathering called = CityFurniture.gathering(site, now);
        if (called != null) {
            if (CitySeats.seated(mob)) mob.stopRiding();
            if (mob.isSleeping()) mob.stopSleeping();
            s.target = called.at();
            s.errand = null;
            s.until = 0;
            s.activity = null;
            hold(mob, s, null);
            if (!toward(mob, called.at(), 6)) mob.getLookControl().setLookAt(called.at().getX() + 0.5, called.at().getY() + 0.5, called.at().getZ() + 0.5);
            if (RANDOM.nextInt(12) == 0 && now - s.lastSpoke > SPEAK_CITY) say(server, level, site, p, mob, s, Repliques.Moment.CLOCHE, now);
            return;
        }
        // Un joueur tout près : on le regarde, et il arrive qu'on lui dise quelque chose.
        listen(server, level, site, p, mob, s, a, now);
        // P47 : celui qu'on a réglé pour rester à son poste, ou pour faire des rondes, le fait.
        NpcSettings.Behavior behavior = p.hasSettings() ? p.settings().behavior : NpcSettings.Behavior.SCHEDULE;
        if (behavior == NpcSettings.Behavior.STATIONARY) {
            BlockPos post = p.work() == null ? b.spot() : p.work();
            s.target = post;
            toward(mob, post, 1);
            return;
        }
        if (behavior == NpcSettings.Behavior.PATROL && a != CityRules.Activity.SLEEP) {
            patrol(level, site, b, p, mob, s, now);
            return;
        }
        switch (a) {
            case SLEEP -> sleep(level, b, p, mob);
            case WORK -> work(server, level, site, b, p, mob, s, now);
            case MEAL -> meal(server, level, site, p, mob, s, now);
            case LEISURE -> leisure(server, level, site, b, p, mob, s, now);
            case PLAY -> play(level, site, p, mob, s, now);
        }
    }

    /** Ce qu'il fait à cette heure ; ses heures de travail à lui, s'il en a. */
    static CityRules.Activity activity(long dayTime, Person p) {
        CityRules.Activity a = CityRules.activity(dayTime, p.job(), p.age(), p.traits(), p.id());
        if (!p.hasSettings()) return a;
        return CityRules.withHours(a, dayTime, p.settings().workStart, p.settings().workEnd);
    }

    /**
     * Coincé (un toit, une clôture, un chemin que les villageois ne savent pas prendre) : sans témoin, il se retrouve à
     * destination, comme les colons de MineColonies ; sous les yeux d'un joueur, il change d'idée plutôt que de se téléporter.
     */
    private static void unstick(ServerLevel level, PathfinderMob mob, State s, long now) {
        BlockPos pos = mob.blockPosition();
        if (s.target == null || CitySeats.seated(mob) || mob.isSleeping() || pos.distSqr(s.lastPos) > 2) {
            s.lastPos = pos;
            s.lastMoved = now;
            return;
        }
        if (now - s.lastMoved < STUCK || pos.distSqr(s.target) <= 36) return;
        s.lastMoved = now;
        if (level.getNearestPlayer(mob, UNSEEN) == null) {
            mob.teleportTo(s.target.getX() + 0.5, s.target.getY(), s.target.getZ() + 0.5);
            mob.getNavigation().stop();
        } else {
            s.target = null;
            s.errand = null;
            s.until = 0;
        }
    }

    /** Il rentre, et s'il a un lit, il s'y couche. */
    private static void sleep(ServerLevel level, CityBuilding b, Person p, PathfinderMob mob) {
        if (mob.isSleeping()) return;
        BlockPos bed = CrusadeConfig.SERVER.usefulFurniture.get() ? CityFurniture.bed(level, b, p) : null;
        if (bed == null) {
            toward(mob, p.worldPos() == null ? b.spot() : p.worldPos(), 3);
            return;
        }
        if (toward(mob, bed, 2)) return;
        BlockState st = level.getBlockState(bed);
        if (st.getBlock() instanceof net.minecraft.world.level.block.BedBlock && !st.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) mob.startSleeping(bed);
    }

    /** Arrivé là où il voulait, il s'assied s'il trouve un siège libre : une fois par halte. */
    private static void sitDown(ServerLevel level, PathfinderMob mob, State s) {
        if (s.seatTried || CitySeats.seated(mob) || !CrusadeConfig.SERVER.usefulFurniture.get()) return;
        s.seatTried = true;
        BlockPos seat = CitySeats.findNear(level, mob.blockPosition());
        if (seat != null) CitySeats.sit(level, mob, seat);
    }

    // ------------------------------------------------------------------ le travail

    private static void work(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob, State s, long now) {
        BuildingFunction f = b.function();
        if (f.dwelling() || p.job().isEmpty()) {
            chores(server, level, site, b, p, mob, s, now);
            return;
        }
        if (s.errand != null) {
            deliver(server, level, site, p, mob, s, b);
            return;
        }
        switch (f) {
            case FERME -> {
                if (!harvest(server, level, site, b, mob, s, now)) atPoints(server, level, site, b, p, mob, s, now);
            }
            case CASERNE, CHATEAU -> patrol(level, site, b, p, mob, s, now);
            case TOUR -> watch(level, b, mob, s, now);
            default -> atPoints(server, level, site, b, p, mob, s, now);
        }
    }

    /** Il va d'un meuble à l'autre de son atelier, et y travaille : gestes, bruits, fumée. De temps en temps, il part livrer. */
    private static void atPoints(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob, State s, long now) {
        if (s.target == null || now >= s.until) {
            // P50 : l'artisan va chercher ce qu'il lui faut chez celui qui le produit.
            CityGood need = RANDOM.nextInt(8) == 0 ? CityScenes.supply(b.function(), RANDOM.nextBoolean()) : null;
            if (need != null && CityScenes.supplyRun(server, level, site, p, mob, need)) return;
            if (s.target != null && RANDOM.nextInt(5) == 0) {
                Optional<Errand> e = errand(site, b.function());
                if (e.isPresent()) {
                    s.errand = e.get();
                    hold(mob, s, e.get().carried());
                    s.target = e.get().to();
                    return;
                }
            }
            List<BlockPos> points = points(level, b);
            s.target = points.get(RANDOM.nextInt(points.size()));
            s.until = now + TASK_MIN + RANDOM.nextInt(TASK_SPREAD);
            hold(mob, s, tool(b.function()));
        }
        if (toward(mob, s.target, AT)) return;
        gesture(level, mob, b.function(), s.target);
    }

    /** Vers qui il livre, selon ce qu'il fait. */
    private static Optional<Errand> errand(Site site, BuildingFunction from) {
        return switch (from) {
            case BOULANGERIE -> to(site, BuildingFunction.TAVERNE, BuildingFunction.MARCHE, Items.BREAD, CityGood.PAIN, 2);
            case BOUCHERIE -> to(site, BuildingFunction.TAVERNE, BuildingFunction.MARCHE, Items.BEEF, CityGood.VIANDE, 2);
            case FORGE -> to(site, BuildingFunction.MARCHE, BuildingFunction.ENTREPOT, Items.IRON_AXE, CityGood.OUTILS, 1);
            case ATELIER -> to(site, BuildingFunction.ENTREPOT, BuildingFunction.MARCHE, Items.STONE_BRICKS, CityGood.PIERRE, 2);
            case ECURIE -> to(site, BuildingFunction.MARCHE, BuildingFunction.ENTREPOT, Items.LEATHER, CityGood.CUIR, 1);
            case MARCHE -> to(site, BuildingFunction.ENTREPOT, BuildingFunction.MAIRIE, Items.BARREL, CityGood.BOIS, 1);
            case ENTREPOT -> to(site, BuildingFunction.MARCHE, BuildingFunction.TAVERNE, Items.BARREL, CityGood.BOIS, 1);
            case BIBLIOTHEQUE -> to(site, BuildingFunction.ECOLE, BuildingFunction.MAIRIE, Items.BOOK, CityGood.LIVRE, 0);
            case FERME -> to(site, BuildingFunction.MOULIN, BuildingFunction.BOULANGERIE, Items.WHEAT, CityGood.BLE, 2);
            case MOULIN -> to(site, BuildingFunction.BOULANGERIE, BuildingFunction.MARCHE, Items.WHEAT, CityGood.BLE, 2);
            case MAIRIE -> to(site, BuildingFunction.MARCHE, BuildingFunction.CHATEAU, Items.PAPER, CityGood.LIVRE, 0);
            default -> Optional.empty();
        };
    }

    private static Optional<Errand> to(Site site, BuildingFunction first, BuildingFunction second, Item item, CityGood good, int amount) {
        for (BuildingFunction f : new BuildingFunction[]{first, second}) {
            for (CityBuilding b : site.buildings()) if (b.function() == f) return Optional.of(new Errand(b.spot(), item, good, amount));
        }
        return Optional.empty();
    }

    private static void deliver(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, CityBuilding home) {
        Errand e = s.errand;
        if (toward(mob, e.to(), 3)) return;
        // P50 : il le remet à quelqu'un, s'il y a quelqu'un ; la scène le comptera en réserve au moment où ça change de mains.
        if (e.amount() > 0 && CityScenes.delivery(server, level, site, p, mob, e.to(), e.good(), e.amount())) {
            s.errand = null;
            s.target = null;
            s.until = 0;
            return;
        }
        site.economy().add(e.good(), e.amount());
        mob.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, mob.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.5f, 1f);
        say(server, level, site, p, mob, s, Repliques.Moment.LIVRAISON, level.getGameTime());
        s.errand = null;
        s.target = null;
        s.until = 0;
        hold(mob, s, tool(home.function()));
        data(server).setDirty();
    }

    /** Il cherche un champ mûr autour de la ferme, le moissonne et ressème : le blé va aux réserves de la ville. */
    private static boolean harvest(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, PathfinderMob mob, State s, long now) {
        if (s.target == null || !mature(level, s.target)) {
            s.target = findCrop(level, b, mob.blockPosition());
            if (s.target == null) return false;
        }
        if (toward(mob, s.target, AT)) return true;
        BlockState st = level.getBlockState(s.target);
        if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
            mob.getLookControl().setLookAt(s.target.getX() + 0.5, s.target.getY() + 0.5, s.target.getZ() + 0.5);
            mob.swing(InteractionHand.MAIN_HAND);
            level.destroyBlockProgress(mob.getId(), s.target, -1);
            level.levelEvent(2001, s.target, Block.getId(st));
            level.setBlock(s.target, crop.getStateForAge(0), Block.UPDATE_ALL);
            site.economy().add(CityGood.BLE, 1 + RANDOM.nextInt(3));
            data(server).setDirty();
        }
        s.target = null;
        return true;
    }

    private static boolean mature(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState st = level.getBlockState(p);
        return st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st);
    }

    private static BlockPos findCrop(ServerLevel level, CityBuilding b, BlockPos near) {
        BlockPos c = b.spot();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // Une poignée d'essais au hasard suffit : un champ mûr se trouve vite, et aucun tronçon n'est chargé pour ça.
        for (int i = 0; i < 80; i++) {
            m.set(c.getX() - FIELD_RADIUS + RANDOM.nextInt(FIELD_RADIUS * 2 + 1), c.getY() - FIELD_DY + RANDOM.nextInt(FIELD_DY * 2 + 1),
                    c.getZ() - FIELD_RADIUS + RANDOM.nextInt(FIELD_RADIUS * 2 + 1));
            if (mature(level, m)) return m.immutable();
        }
        return null;
    }

    /** La ronde des gardes : d'un coin de rue à l'autre. */
    private static void patrol(ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob, State s, long now) {
        if (s.target == null || now >= s.until || !toward(mob, s.target, 3)) {
            if (s.target == null || now >= s.until) {
                List<BlockPos> spots = CityService.outdoorSpots(level, site);
                s.target = spots.isEmpty() ? b.spot() : spots.get(RANDOM.nextInt(spots.size()));
                s.until = now + TASK_MIN * 2 + RANDOM.nextInt(TASK_SPREAD);
            }
        }
    }

    /** L'archer reste à son poste et regarde au loin. */
    private static void watch(ServerLevel level, CityBuilding b, PathfinderMob mob, State s, long now) {
        if (s.target == null) {
            List<BlockPos> points = points(level, b);
            BlockPos best = b.spot();
            for (BlockPos q : points) if (q.getY() > best.getY()) best = q;
            s.target = best;
        }
        if (!toward(mob, s.target, AT) && RANDOM.nextInt(3) == 0) {
            mob.getLookControl().setLookAt(mob.getX() + RANDOM.nextInt(41) - 20, mob.getY() - 2, mob.getZ() + RANDOM.nextInt(41) - 20);
        }
    }

    /** Ceux qui n'ont pas de boutique ont tout de même à faire : l'eau, les courses, les voisins. */
    private static void chores(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob, State s, long now) {
        if (s.target == null || now >= s.until) {
            BlockPos market = building(site, BuildingFunction.MARCHE);
            int roll = RANDOM.nextInt(4);
            if (roll == 0 && market != null) {
                s.target = market;
                s.errand = null;
            } else if (roll == 1 && CityFurniture.water(site) != null) {
                // P45 : la corvée d'eau, au puits ou à la fontaine.
                s.target = CityFurniture.water(site);
            } else if (roll == 1 || p.work() == null) {
                s.target = p.worldPos() == null ? b.spot() : p.worldPos();
            } else {
                List<BlockPos> spots = CityService.outdoorSpots(level, site);
                s.target = spots.isEmpty() ? p.work() : spots.get(RANDOM.nextInt(spots.size()));
            }
            s.until = now + TASK_MIN + RANDOM.nextInt(TASK_SPREAD * 2);
        }
        if (toward(mob, s.target, 3)) return;
        BlockPos market = building(site, BuildingFunction.MARCHE);
        if (market != null && s.target.equals(market) && now - s.lastMeal > 6000) {
            s.lastMeal = now;
            buy(server, level, site, p, mob, s, Repliques.Moment.MARCHE, now);
            return;
        }
        // P50 : il arrive qu'on ait besoin d'autre chose que de pain, et qu'on s'arrête pour causer.
        if (RANDOM.nextInt(30) == 0 && now - s.lastMeal > 3000) {
            CityGood want = SHOPPING[RANDOM.nextInt(SHOPPING.length)];
            if (CityScenes.sale(server, level, site, p, mob, want, false)) {
                s.lastMeal = now;
                return;
            }
        }
        if (RANDOM.nextInt(6) == 0) CityScenes.chat(server, level, site, p, mob);
    }

    // ------------------------------------------------------------------ le reste de la journée

    private static void meal(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, long now) {
        if (s.target == null) {
            BlockPos table = building(site, BuildingFunction.TAVERNE);
            if (table == null || RANDOM.nextBoolean()) {
                BlockPos market = building(site, BuildingFunction.MARCHE);
                if (market != null) table = market;
            }
            s.target = table == null ? (p.worldPos() == null ? mob.blockPosition() : p.worldPos()) : table;
            hold(mob, s, null);
        }
        if (CitySeats.seated(mob)) return;
        if (toward(mob, s.target, 4)) return;
        sitDown(level, mob, s);
        if (now - s.lastMeal > 6000) {
            s.lastMeal = now;
            buy(server, level, site, p, mob, s, Repliques.Moment.REPAS, now);
        }
    }

    /** Il paie et il mange. S'il n'y a plus rien, il le dit, et toute la rue l'entend. */
    private static void buy(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, Repliques.Moment moment, long now) {
        CityEconomy eco = site.economy();
        CityGood food = eco.stock(CityGood.REPAS) > 0 ? CityGood.REPAS : eco.stock(CityGood.PAIN) > 0 ? CityGood.PAIN : eco.stock(CityGood.VIANDE) > 0 ? CityGood.VIANDE : null;
        if (food == null) {
            say(server, level, site, p, mob, s, Repliques.Moment.PENURIE, now);
            CityRumors.add(server, site, "famine");
            return;
        }
        // P50 : il va le chercher auprès de celui qui le vend, s'il est là ; sinon, il se sert au marché comme avant.
        if (CityScenes.sale(server, level, site, p, mob, food, true)) return;
        eco.take(food, 1);
        if (p.coins() > 0) {
            p.setCoins(p.coins() - 1);
            eco.addPurse(1);
        }
        p.setHunger(Math.max(0, p.hunger() - 50));
        mob.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.5f, 1f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 2, 0.2, 0.2, 0.2, 0);
        say(server, level, site, p, mob, s, moment, now);
        data(server).setDirty();
    }

    private static void leisure(MinecraftServer server, ServerLevel level, Site site, CityBuilding b, Person p, PathfinderMob mob, State s, long now) {
        if (s.target == null || now >= s.until) {
            hold(mob, s, null);
            if (CitySeats.seated(mob)) mob.stopRiding();
            s.seatTried = false;
            BlockPos tavern = building(site, BuildingFunction.TAVERNE), temple = building(site, BuildingFunction.TEMPLE);
            boolean pious = p.traits().contains(fr.crusadecraft.core.character.Trait.PIEUX) || p.traits().contains(fr.crusadecraft.core.character.Trait.ZELE);
            if (pious && temple != null) s.target = temple;
            else if (tavern != null && RANDOM.nextBoolean() && p.age() >= 16) s.target = tavern;
            else {
                List<BlockPos> spots = CityService.outdoorSpots(level, site);
                s.target = spots.isEmpty() ? CityService.plaza(site) : spots.get(RANDOM.nextInt(spots.size()));
            }
            s.until = now + TASK_MIN * 2 + RANDOM.nextInt(TASK_SPREAD);
        }
        if (CitySeats.seated(mob) || toward(mob, s.target, 4)) return;
        // P50 : le soir, on s'arrête pour causer avec le voisin.
        if (RANDOM.nextInt(4) == 0 && CityScenes.chat(server, level, site, p, mob)) return;
        sitDown(level, mob, s);
    }

    private static void play(ServerLevel level, Site site, Person p, PathfinderMob mob, State s, long now) {
        // P44 : le matin, les enfants vont à l'école, s'il y en a une.
        BlockPos school = building(site, BuildingFunction.ECOLE);
        if (school != null && p.age() >= 5 && Math.floorMod(level.getDayTime(), 24000L) < 5500) {
            if (!toward(mob, school, 4)) sitDown(level, mob, s);
            if (RANDOM.nextInt(40) == 0 && now - s.lastSpoke > SPEAK_PERSON) say(level.getServer(), level, site, p, mob, s, Repliques.Moment.ECOLE, now);
            return;
        }
        if (CitySeats.seated(mob)) mob.stopRiding();
        if (s.target == null || now >= s.until || !toward(mob, s.target, 2)) {
            if (s.target == null || now >= s.until) {
                List<BlockPos> spots = CityService.outdoorSpots(level, site);
                s.target = spots.isEmpty() ? CityService.plaza(site) : spots.get(RANDOM.nextInt(spots.size()));
                s.until = now + 200 + RANDOM.nextInt(300);
            }
        }
    }

    // ------------------------------------------------------------------ parler

    private static void listen(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, CityRules.Activity a, long now) {
        if (!CrusadeConfig.SERVER.repliques.get()) return;
        ServerPlayer nearest = null;
        double best = HEARING * HEARING;
        for (ServerPlayer pl : level.players()) {
            double d = pl.distanceToSqr(mob);
            if (d < best) {
                best = d;
                nearest = pl;
            }
        }
        if (nearest == null) return;
        if (best < 16) mob.getLookControl().setLookAt(nearest, 30f, 30f);
        // P47 : un bavard parle trois fois plus souvent, un muet jamais.
        boolean chatty = p.hasSettings() && p.settings().chatty == 2;
        if (now - s.lastSpoke < (chatty ? SPEAK_PERSON / 3 : SPEAK_PERSON) || RANDOM.nextInt(chatty ? 2 : 6) != 0) return;
        Repliques.Moment m;
        boolean thunder = level.isThundering(), rain = level.isRaining() && level.canSeeSky(mob.blockPosition());
        if (thunder && level.canSeeSky(mob.blockPosition())) m = Repliques.Moment.ORAGE;
        else if (rain && RANDOM.nextInt(3) == 0) m = Repliques.Moment.PLUIE;
        else if (p.hunger() > 50) m = Repliques.Moment.PENURIE;
        else if (best < 25) m = Repliques.Moment.ACCUEIL;
        else if (a == CityRules.Activity.WORK && !p.job().isEmpty()) m = Repliques.Moment.TRAVAIL;
        else m = CityRules.hourMoment(level.getDayTime());
        speak(server, level, site, p, mob, s, m, nearest, now);
    }

    /** Une parole qui se voit au-dessus de sa tête, si quelqu'un est là pour la lire. */
    private static void say(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, Repliques.Moment m, long now) {
        if (!CrusadeConfig.SERVER.repliques.get()) return;
        ServerPlayer nearest = null;
        double best = HEARING * HEARING;
        for (ServerPlayer pl : level.players()) {
            double d = pl.distanceToSqr(mob);
            if (d < best) {
                best = d;
                nearest = pl;
            }
        }
        if (nearest != null) speak(server, level, site, p, mob, s, m, nearest, now);
    }

    private static void speak(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, Repliques.Moment m, ServerPlayer to, long now) {
        if (!site.settings().canSpeak || (p.hasSettings() && p.settings().chatty == 0)) return;
        Long last = CITY_SPOKE.get(site.id());
        if (last != null && now - last < SPEAK_CITY) return;
        Optional<String> line = CityVoice.text(server, site, p, m, to, RANDOM);
        if (line.isEmpty()) return;
        s.lastSpoke = now;
        CITY_SPOKE.put(site.id(), now);
        CityBubbles.say(level, mob, line.get());
    }

    // ------------------------------------------------------------------ les voyageurs

    /** P50 : la scène est finie : il reprend ce qu'il faisait, comme s'il venait de changer de tâche. */
    static void afterScene(UUID id) {
        State s = STATES.get(id);
        if (s == null) return;
        s.activity = null;
        s.target = null;
        s.errand = null;
        s.until = 0;
    }

    /** P48 : un voyageur sur place : il va d'un lieu à l'autre, s'assied à la taverne, achète au marché, et parle. */
    static void visit(MinecraftServer server, ServerLevel level, Site site, Site home, Person p, PathfinderMob mob) {
        if ((mob instanceof Villager v && v.isTrading()) || CityScenes.busy(p.id())) return;
        State s = STATES.computeIfAbsent(p.id(), k -> new State());
        long now = level.getGameTime();
        unstick(level, mob, s, now);
        CityFurniture.Gathering called = CityFurniture.gathering(site, now);
        if (called != null) {
            if (CitySeats.seated(mob)) mob.stopRiding();
            s.target = called.at();
            s.until = 0;
            toward(mob, called.at(), 6);
            return;
        }
        speakAsVisitor(server, level, site, p, mob, s, now);
        if (s.target == null || now >= s.until) {
            if (CitySeats.seated(mob)) mob.stopRiding();
            s.seatTried = false;
            BlockPos tavern = building(site, BuildingFunction.TAVERNE), market = building(site, BuildingFunction.MARCHE);
            int roll = RANDOM.nextInt(3);
            if (roll == 0 && tavern != null) s.target = tavern;
            else if (roll == 1 && market != null) s.target = market;
            else {
                List<BlockPos> spots = CityService.outdoorSpots(level, site);
                s.target = spots.isEmpty() ? CityService.plaza(site) : spots.get(RANDOM.nextInt(spots.size()));
            }
            s.until = now + TASK_MIN * 2 + RANDOM.nextInt(TASK_SPREAD);
        }
        if (CitySeats.seated(mob) || toward(mob, s.target, 4)) return;
        sitDown(level, mob, s);
    }

    private static void speakAsVisitor(MinecraftServer server, ServerLevel level, Site site, Person p, PathfinderMob mob, State s, long now) {
        if (!CrusadeConfig.SERVER.repliques.get() || now - s.lastSpoke < SPEAK_PERSON || RANDOM.nextInt(6) != 0) return;
        ServerPlayer nearest = null;
        double best = HEARING * HEARING;
        for (ServerPlayer pl : level.players()) {
            double d = pl.distanceToSqr(mob);
            if (d < best) {
                best = d;
                nearest = pl;
            }
        }
        if (nearest != null) speak(server, level, site, p, mob, s, Repliques.Moment.VOYAGE, nearest, now);
    }

    /** P48 : sur la route, il salue celui qu'il croise. */
    static void roadTalk(MinecraftServer server, ServerLevel level, Site home, Person p, PathfinderMob mob, long now) {
        State s = STATES.computeIfAbsent(p.id(), k -> new State());
        speakAsVisitor(server, level, home, p, mob, s, now);
    }

    // ------------------------------------------------------------------ outils

    /** Un but de marche, renouvelé à chaque passage ; vrai tant qu'il n'est pas arrivé. */
    static boolean toward(PathfinderMob mob, BlockPos target, int close) {
        return toward(mob, target, close, 0.55f);
    }

    /** La même chose, à cette allure. */
    static boolean toward(PathfinderMob mob, BlockPos target, int close, float speed) {
        double d = mob.blockPosition().distSqr(target);
        if (d <= close * close) return false;
        // Un villageois ne trouve pas son chemin au-delà d'une trentaine de blocs : de loin, il y va par étapes, comme ceux de
        // Minecraft quand ils rentrent chez eux. C'est ce qui manquait pour qu'une cloche fasse venir toute une ville.
        if (d > FAR_STEP * FAR_STEP) {
            net.minecraft.world.phys.Vec3 step = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosTowards(mob, 24, 7,
                    net.minecraft.world.phys.Vec3.atBottomCenterOf(target), Math.PI / 2);
            if (step != null) target = BlockPos.containing(step);
        }
        if (mob instanceof Villager villager && villager.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(target), speed, Math.max(0, close - 1)));
        } else {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.7);
        }
        return true;
    }

    private static BlockPos building(Site site, BuildingFunction f) {
        for (CityBuilding b : site.buildings()) if (b.function() == f) return b.spot();
        return null;
    }

    /** L'outil de son métier, en main ; rien hors de ses heures. Il ne le lâche pas en mourant. */
    private static void hold(PathfinderMob mob, State s, Item item) {
        // On regarde ce qu'il a vraiment en main : une scène ou un redémarrage ont pu y laisser autre chose.
        if (mob.getMainHandItem().getItem() == (item == null ? Items.AIR : item)) return;
        s.held = item;
        mob.setItemSlot(EquipmentSlot.MAINHAND, item == null ? ItemStack.EMPTY : new ItemStack(item));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    static Item tool(BuildingFunction f) {
        return switch (f) {
            case BOULANGERIE -> Items.BREAD;
            case TAVERNE -> Items.MUSHROOM_STEW;
            case FORGE -> Items.IRON_PICKAXE;
            case MARCHE -> Items.EMERALD;
            case BOUCHERIE -> Items.IRON_AXE;
            case FERME -> Items.IRON_HOE;
            case ECURIE -> Items.SADDLE;
            case BIBLIOTHEQUE -> Items.BOOK;
            case ATELIER -> Items.STONE_PICKAXE;
            case ENTREPOT, MAIRIE -> Items.PAPER;
            case TEMPLE -> Items.CANDLE;
            case CASERNE, CHATEAU -> Items.IRON_SWORD;
            case TOUR -> Items.BOW;
            case ECOLE -> Items.WRITABLE_BOOK;
            case MOULIN -> Items.WHEAT;
            case HOSPICE -> Items.GLASS_BOTTLE;
            case MAISON -> null;
        };
    }

    /** Le geste du métier : le bras, le bruit, la fumée. */
    private static void gesture(ServerLevel level, PathfinderMob mob, BuildingFunction f, BlockPos at) {
        if (RANDOM.nextInt(3) == 0) return;
        mob.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        mob.swing(InteractionHand.MAIN_HAND);
        SoundEvent sound = switch (f) {
            case FORGE -> SoundEvents.ANVIL_USE;
            case BOULANGERIE -> SoundEvents.SMOKER_SMOKE;
            case TAVERNE -> SoundEvents.BREWING_STAND_BREW;
            case BOUCHERIE -> SoundEvents.VILLAGER_WORK_BUTCHER;
            case ATELIER -> SoundEvents.VILLAGER_WORK_MASON;
            case ECURIE -> SoundEvents.VILLAGER_WORK_LEATHERWORKER;
            case BIBLIOTHEQUE, ECOLE -> SoundEvents.VILLAGER_WORK_LIBRARIAN;
            case TEMPLE, HOSPICE -> SoundEvents.VILLAGER_WORK_CLERIC;
            case MOULIN -> SoundEvents.VILLAGER_WORK_FARMER;
            case MAIRIE -> SoundEvents.VILLAGER_WORK_CARTOGRAPHER;
            case MARCHE -> SoundEvents.VILLAGER_TRADE;
            case ENTREPOT -> SoundEvents.BARREL_OPEN;
            default -> null;
        };
        if (sound != null) level.playSound(null, at, sound, SoundSource.NEUTRAL, f == BuildingFunction.FORGE ? 0.25f : 0.5f, 0.9f + RANDOM.nextFloat() * 0.2f);
        double x = at.getX() + 0.5, y = at.getY() + 1.1, z = at.getZ() + 0.5;
        switch (f) {
            case FORGE -> level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.2, 0.1, 0.2, 0);
            case BOULANGERIE, TAVERNE -> level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
            case BIBLIOTHEQUE -> level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 6, 0.3, 0.3, 0.3, 0.2);
            case TEMPLE -> level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.2, 0.2, 0.2, 0.01);
            case ATELIER -> level.sendParticles(ParticleTypes.POOF, x, y - 0.5, z, 2, 0.2, 0.1, 0.2, 0.01);
            default -> {
            }
        }
    }

    /** Où travailler dans ce bâtiment : à côté de ses meubles, à défaut sur son sol. Cherché une fois, puis gardé. */
    static List<BlockPos> points(ServerLevel level, CityBuilding b) {
        List<BlockPos> known = POINTS.get(b.id());
        if (known != null) return known;
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = b.min().getY(); y <= b.max().getY() && out.size() < 12; y++) {
            for (int z = b.min().getZ(); z <= b.max().getZ() && out.size() < 12; z++) {
                for (int x = b.min().getX(); x <= b.max().getX() && out.size() < 12; x++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m) || !furniture(level.getBlockState(m))) continue;
                    BlockPos stand = beside(level, m.immutable());
                    if (stand != null && !out.contains(stand)) out.add(stand);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(b.spot());
            for (int i = 0; i < 40 && out.size() < 5; i++) {
                int x = b.min().getX() + RANDOM.nextInt(b.max().getX() - b.min().getX() + 1), z = b.min().getZ() + RANDOM.nextInt(b.max().getZ() - b.min().getZ() + 1);
                for (int y = b.min().getY(); y <= b.max().getY(); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.isLoaded(p) && BuildingScan.standable(level, p)) {
                        out.add(p);
                        break;
                    }
                }
            }
        }
        POINTS.put(b.id(), out);
        return out;
    }

    private static BlockPos beside(ServerLevel level, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos q = p.relative(d);
            if (BuildingScan.standable(level, q)) return q;
            if (BuildingScan.standable(level, q.below())) return q.below();
        }
        return null;
    }

    /** Un meuble où l'on travaille, de Minecraft ou d'un mod : d'après son nom, pour les reconnaître tous. */
    static boolean furniture(BlockState st) {
        if (st.isAir()) return false;
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        for (String w : FURNITURE) if (id.contains(w)) return true;
        return false;
    }

    private static final String[] FURNITURE = {"furnace", "smoker", "anvil", "crafting", "barrel", "chest", "cauldron", "lectern", "bookshelf", "brewing", "composter",
            "loom", "cartography", "fletching", "stonecutter", "grindstone", "smithing", "hay", "oven", "stove", "counter", "table", "workbench", "kitchen", "shelf", "crate"};
}
