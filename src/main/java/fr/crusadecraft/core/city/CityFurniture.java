package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Ce que la décoration fait pour de vrai dans une ville (P45). Les meubles de chaque bâtiment sont recomptés peu à peu
 * (ils font produire davantage, et les coffres donnent de la place), toute la ville est parcourue lentement pour mesurer
 * son charme (les fleurs, les lanternes et les tapis attirent du monde et de l'argent), et trois objets répondent à la
 * main : la cloche rassemble les habitants, le comptoir sert à commercer, le puits leur donne une corvée de plus.
 */
public final class CityFurniture {
    /** Les blocs parcourus par passage, pour le charme : assez peu pour ne jamais se sentir. */
    static final int BUDGET = 6000;
    /** Une cloche rassemble la ville tant de ticks. */
    static final int GATHER_TICKS = 1200;
    /** Un comptoir fait venir celui qui y travaille, s'il est à moins de tant de blocs : il arrive quand on l'appelle. */
    static final int COUNTER_REACH = 32;

    private static final class Scan {
        final int[] box;
        int x, y, z, decor;
        final List<BlockPos> water = new ArrayList<>();

        Scan(int[] box) {
            this.box = box;
            x = box[0];
            y = box[1];
            z = box[2];
        }
    }

    /** Un rassemblement : où, et jusqu'à quand. */
    public record Gathering(BlockPos at, long until) {}

    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_BUILDING = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> WATER = new HashMap<>();
    private static final Map<UUID, Gathering> GATHERINGS = new HashMap<>();
    private static final Map<UUID, List<BlockPos>> BEDS = new HashMap<>();
    private static final Map<Block, Furnishing.Kind> KINDS = new IdentityHashMap<>();
    private static final Random RANDOM = new Random();

    private CityFurniture() {}

    public static void onServerStopped() {
        SCANS.clear();
        NEXT_BUILDING.clear();
        WATER.clear();
        GATHERINGS.clear();
        BEDS.clear();
    }

    private static Furnishing.Kind kind(Block b) {
        Furnishing.Kind k = KINDS.get(b);
        if (k == null && !KINDS.containsKey(b)) {
            k = Furnishing.of(BuiltInRegistries.BLOCK.getKey(b).getPath());
            KINDS.put(b, k);
        }
        return k;
    }

    // ------------------------------------------------------------------ recompter

    /** À chaque passage dans une ville où un joueur se trouve : un bâtiment recompté, et un bout de la ville parcouru. */
    public static void tick(ServerLevel level, Site site) {
        recount(level, site);
        walk(level, site);
    }

    private static void recount(ServerLevel level, Site site) {
        if (site.buildings().isEmpty()) return;
        int i = NEXT_BUILDING.merge(site.id(), 1, Integer::sum);
        CityBuilding b = site.buildings().get(Math.floorMod(i, site.buildings().size()));
        if (!level.hasChunksAt(b.min(), b.max())) return;
        b.setStats(b.floor(), b.levels(), BuildingScan.furniture(level, b.min(), b.max()));
        BEDS.remove(b.id());
    }

    /** Le charme se mesure en parcourant la ville, un peu à chaque fois ; un tour fini, il change. */
    private static void walk(ServerLevel level, Site site) {
        Scan s = SCANS.computeIfAbsent(site.id(), k -> new Scan(CityService.box(site)));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int n = 0; n < BUDGET; n++) {
            if (s.y > s.box[4]) {
                finish(level, site, s);
                SCANS.remove(site.id());
                return;
            }
            m.set(s.x, s.y, s.z);
            if (level.isLoaded(m)) {
                BlockState st = level.getBlockState(m);
                if (!st.isAir()) {
                    Furnishing.Kind k = kind(st.getBlock());
                    if (k != null) {
                        if (k == Furnishing.Kind.EAU) {
                            if (s.water.size() < 12) s.water.add(m.immutable());
                        } else if (k != Furnishing.Kind.STOCKAGE) {
                            s.decor++;
                        }
                    }
                }
            }
            if (++s.x > s.box[3]) {
                s.x = s.box[0];
                if (++s.z > s.box[5]) {
                    s.z = s.box[2];
                    s.y++;
                }
            }
        }
    }

    private static void finish(ServerLevel level, Site site, Scan s) {
        int before = site.charm();
        int charm = Furnishing.charm(s.decor, site.buildings().size());
        site.setCharm(charm);
        WATER.put(site.id(), s.water);
        // La ville remarque quand elle a changé d'allure.
        if (charm / 25 > before / 25 && charm >= 25) CityRumors.add(level.getServer(), site, "charme");
        CrusadeSavedData.get(level.getServer()).setDirty();
    }

    /** Un puits, une fontaine ou un chaudron de la ville, pour la corvée d'eau. */
    static BlockPos water(Site site) {
        List<BlockPos> w = WATER.get(site.id());
        return w == null || w.isEmpty() ? null : w.get(RANDOM.nextInt(w.size()));
    }

    // ------------------------------------------------------------------ la cloche

    /** On sonne une cloche dans une ville : tout le monde vient, quelques instants. */
    public static void onBell(ServerPlayer player, BlockPos pos) {
        if (!CrusadeConfig.SERVER.usefulFurniture.get()) return;
        MinecraftServer server = player.server;
        Optional<Site> city = CityService.cityAt(server, player.level().dimension().location(), pos);
        if (city.isEmpty()) return;
        GATHERINGS.put(city.get().id(), new Gathering(pos.immutable(), player.level().getGameTime() + GATHER_TICKS));
        player.displayClientMessage(Component.translatable("crusadecraft.city.bell", city.get().name()), true);
    }

    /** Le rassemblement en cours dans cette ville, s'il y en a un. */
    public static Gathering gathering(Site site, long now) {
        Gathering g = GATHERINGS.get(site.id());
        if (g == null) return null;
        if (now >= g.until()) {
            GATHERINGS.remove(site.id());
            return null;
        }
        return g;
    }

    // ------------------------------------------------------------------ le comptoir

    /**
     * Clic droit, les mains vides, sur une table ou un comptoir d'un bâtiment où quelqu'un vend : son étal s'ouvre, sans
     * avoir à attraper le marchand au milieu de sa journée. Vrai si le geste est pris.
     */
    public static boolean onCounter(ServerPlayer player, BlockPos pos) {
        if (!CrusadeConfig.SERVER.usefulFurniture.get() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return false;
        ServerLevel level = player.serverLevel();
        if (kind(level.getBlockState(pos).getBlock()) != Furnishing.Kind.TABLE) return false;
        MinecraftServer server = player.server;
        Optional<Site> city = CityService.cityAt(server, level.dimension().location(), pos);
        if (city.isEmpty()) return false;
        Optional<CityBuilding> building = CityService.buildingAt(city.get(), pos);
        if (building.isEmpty() || building.get().function() == null) return false;
        Villager best = null;
        double bestD = COUNTER_REACH * COUNTER_REACH;
        for (UUID id : building.get().occupants()) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Villager v) || v.isTrading() || v.getOffers().isEmpty()) continue;
            double d = v.distanceToSqr(pos.getCenter());
            if (d < bestD) {
                best = v;
                bestD = d;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("crusadecraft.city.counter.nobody"), true);
            return true;
        }
        best.setTradingPlayer(player);
        best.openTradingScreen(player, best.getDisplayName(), best.getVillagerData().getLevel());
        return true;
    }

    // ------------------------------------------------------------------ les lits

    /** Le lit de cette personne dans son logis : un pour chacun, dans l'ordre où ils y vivent. */
    static BlockPos bed(ServerLevel level, CityBuilding b, Person p) {
        if (b.furniture().getOrDefault("lit", 0) <= 0) return null;
        List<BlockPos> beds = BEDS.get(b.id());
        if (beds == null) {
            beds = new ArrayList<>();
            for (BlockPos q : BlockPos.betweenClosed(b.min(), b.max())) {
                BlockState st = level.getBlockState(q);
                if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) beds.add(q.immutable());
            }
            BEDS.put(b.id(), beds);
        }
        int i = b.occupants().indexOf(p.id());
        return i < 0 || i >= beds.size() ? null : beds.get(i);
    }
}
