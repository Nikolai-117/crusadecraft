package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.Journals;
import fr.crusadecraft.core.build.plan.Plan;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.city.CityStaff;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Le banc d'essai en monde réel de la phase 38 ({@code ./gradlew runWorldShots -Pshots_v5=true}) : les deux villes de
 * Romain (Dolhimluith, Grunbach) choisies au catalogue et posées au plan d'architecte, photographiées de haut et dans
 * leurs rues ; puis le bâton d'intendant : l'écran de la ville, les cadres des bâtiments, l'écran d'un bâtiment, et les
 * gens dans les rues. Les fichiers doivent être dans {@code run-worldshots/crusadecraft/plans/Levenzia} et {@code …/Varenhold}.
 */
final class WorldShotsV5 {
    record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot, BooleanSupplier until) {
        Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {
            this(name, server, client, ticks, shot, null);
        }
    }

    private record City(String label, String ref, int offset) {}

    private static final List<City> ALL = List.of(
            new City("dolhimluith", new PlanRef(PlanRef.Source.FILE, "plans", "Levenzia/dolhimluith.schem").encode(), 300),
            new City("grunbach", new PlanRef(PlanRef.Source.FILE, "plans", "Varenhold/grunbach.schem").encode(), 720));
    /** -Pshots_quick=true : Dolhimluith seule. */
    private static final List<City> CITIES = Boolean.getBoolean("crusadecraft.worldshots.v4quick") ? ALL.subList(0, 1) : ALL;

    private static List<Step> steps;
    private static int index = -1, wait, waited;
    private static boolean acted;
    private static BlockPos origin;
    private static final Map<String, BlockPos> GROUND = new HashMap<>();
    private static final Map<String, java.util.UUID> SITES = new HashMap<>();
    private static final Map<String, Long> STARTED = new HashMap<>();
    private static volatile boolean pasted;

    private WorldShotsV5() {}

    static boolean tick(Minecraft mc, BiConsumer<Minecraft, String> grab) {
        if (steps == null) steps = build();
        if (wait > 0) {
            wait--;
            return false;
        }
        if (index >= 0 && acted) {
            Step done = steps.get(index);
            if (done.until() != null && !done.until().getAsBoolean() && waited++ < 12_000) return false;
            if (done.shot() != null) grab.accept(mc, done.shot());
            acted = false;
        }
        index++;
        waited = 0;
        if (index >= steps.size()) return true;
        Step s = steps.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        CrusadeCraft.LOGGER.info("WorldShots v5 : étape {}", s.name());
        if (s.server() != null) server.execute(() -> {
            try {
                s.server().accept(server);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v5 : l'étape {} a échoué côté serveur", s.name(), e);
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

    private static void look(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.connection.teleport(x, y, z, yaw, pitch);
    }

    private static long memoryMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    private static Site site(MinecraftServer server, String label) {
        java.util.UUID id = SITES.get(label);
        return id == null ? null : CrusadeSavedData.get(server).world().site(id).orElse(null);
    }

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        out.add(new Step("depart", server -> {
            ServerPlayer p = player(server);
            WorldEditService.grantForTests(p.getUUID(), true);
            origin = p.blockPosition();
        }, mc -> mc.setScreen(null), 20, null));
        for (City c : CITIES) {
            out.add(new Step("choix_" + c.label(), server -> {
                ServerPlayer p = player(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PLAN.get()));
                long before = memoryMb(), t0 = System.nanoTime();
                String msg = PlanTool.choose(p, c.ref()).message().getString();
                Plan plan = PlanRef.decode(c.ref()).flatMap(r -> PlanLibrary.load(r, true)).orElse(null);
                CrusadeCraft.LOGGER.info("WorldShots v5 : {} — {} ; lu en {} ms, mémoire {} → {} Mo", c.label(), msg,
                        (System.nanoTime() - t0) / 1_000_000, before, memoryMb());
                if (plan != null) {
                    CrusadeCraft.LOGGER.info("WorldShots v5 : {} — {}×{}×{}, sol {}, {} poses, {} sortes remplacées, inconnues {}, ville={}", c.label(),
                            plan.sizeX(), plan.sizeY(), plan.sizeZ(), plan.groundY(), plan.placements().size(), plan.replaced(), plan.unknown(), plan.city());
                }
            }, null, 40, null));
            out.add(new Step("pose_" + c.label(), server -> {
                ServerPlayer p = player(server);
                ServerLevel level = p.serverLevel();
                int x = origin.getX() + c.offset(), z = origin.getZ() + 60;
                for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos ground = new BlockPos(x, y, z);
                GROUND.put(c.label(), ground);
                look(p, x + 0.5, y + 60, z + 120.5, 180f, 25f);
                pasted = false;
                STARTED.put(c.label(), System.nanoTime());
                PlanTool.paste(p, p.getMainHandItem(), ground);
                for (Site s : CrusadeSavedData.get(server).world().sites()) {
                    if (s.imported() && c.ref().equals(s.importedRef())) SITES.put(c.label(), s.id());
                }
                CrusadeCraft.LOGGER.info("WorldShots v5 : {} posée en {} (site {})", c.label(), ground, SITES.get(c.label()));
            }, mc -> {
                // Remis à zéro côté client aussi : la pose peut tarder (un terrain à générer) et la précédente est finie.
                pasted = false;
                SITES.remove(c.label());
            }, 40, null, () -> {
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                java.util.UUID id = SITES.get(c.label());
                if (server != null && id != null) server.execute(() -> pasted = !Journals.pasting(server, id));
                return pasted;
            }));
            out.add(new Step("fin_pose_" + c.label(), server -> {
                // Les mains vides : le HUD du plan ne cache plus la vue.
                player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Site s = site(server, c.label());
                CrusadeCraft.LOGGER.info("WorldShots v5 : {} achevée en {} s, mémoire {} Mo, {} bâtiments reconnus", c.label(),
                        (System.nanoTime() - STARTED.get(c.label())) / 1_000_000_000L, memoryMb(), s == null ? -1 : s.buildings().size());
                if (s != null) {
                    Map<BuildingFunction, Integer> count = new java.util.EnumMap<>(BuildingFunction.class);
                    for (CityBuilding b : s.buildings()) if (b.suggestion() != null) count.merge(b.suggestion(), 1, Integer::sum);
                    CrusadeCraft.LOGGER.info("WorldShots v5 : {} — propositions {}", c.label(), count);
                }
            }, null, 20, null));
            out.add(new Step("haut_" + c.label(), server -> {
                BlockPos g = GROUND.get(c.label());
                look(player(server), g.getX() + 0.5, g.getY() + 150, g.getZ() + 110.5, 180f, 52f);
            }, null, 300, "v5_" + c.label() + "_haut"));
            out.add(new Step("vue_" + c.label(), server -> {
                BlockPos g = GROUND.get(c.label());
                look(player(server), g.getX() - 60.5, g.getY() + 40, g.getZ() + 70.5, 220f, 22f);
            }, null, 200, "v5_" + c.label() + "_vue"));
            out.add(new Step("maison_" + c.label(), server -> {
                Site s = site(server, c.label());
                if (s == null || s.buildings().isEmpty()) return;
                // Le bâtiment le plus proche du centre, vu de sa porte, à hauteur d'homme.
                CityBuilding best = s.buildings().get(0);
                for (CityBuilding b : s.buildings()) if (b.spot().distSqr(s.anchor()) < best.spot().distSqr(s.anchor())) best = b;
                BlockPos sp = best.spot();
                look(player(server), sp.getX() + 0.5, sp.getY() + 4, best.max().getZ() + 9.5, 180f, 12f);
            }, null, 160, "v5_" + c.label() + "_maison"));
        }
        // Le bâton d'intendant, à Dolhimluith.
        out.add(new Step("baton_ville", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.INTENDANT.get()));
            BlockPos g = GROUND.get("dolhimluith");
            look(p, g.getX() + 0.5, g.getY() + 30, g.getZ() + 40.5, 180f, 30f);
            CityStaff.useInAir(p, p.getMainHandItem(), false);
        }, null, 60, "v5_baton_ville"));
        out.add(new Step("baton_accepte", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            CityStaff.cityAction(p, s.id(), CityStaff.ACCEPT_ALL);
        }, null, 60, "v5_baton_accepte"));
        out.add(new Step("baton_cadres", server -> {
            BlockPos g = GROUND.get("dolhimluith");
            look(player(server), g.getX() + 0.5, g.getY() + 55, g.getZ() + 60.5, 180f, 48f);
        }, mc -> mc.setScreen(null), 120, "v5_baton_cadres"));
        out.add(new Step("baton_batiment", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null || s.buildings().isEmpty()) return;
            CityBuilding pick = s.buildings().get(0);
            for (CityBuilding b : s.buildings()) if (b.function() == BuildingFunction.FORGE) pick = b;
            CityStaff.useOn(p, p.getMainHandItem(), pick.spot(), Direction.UP, false);
        }, null, 60, "v5_baton_batiment"));
        out.add(new Step("habitants", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            int n = CityService.populate(p.serverLevel(), s);
            CrusadeCraft.LOGGER.info("WorldShots v5 : {} habitants apparus à Dolhimluith ({} au registre)", n, CityService.people(server, s));
            // Le matin : les familles vont passer la journée sur la place, les gens de métier à leur ouvrage.
            p.serverLevel().setDayTime(1500);
        }, mc -> mc.setScreen(null), 900, null));
        out.add(new Step("place", server -> {
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            // Un habitant dehors, au milieu d'autres : on le regarde d'un peu haut.
            ServerLevel level = player(server).serverLevel();
            int[] b = CityService.box(s);
            var box = new net.minecraft.world.phys.AABB(b[0], b[1], b[2], b[3], b[4] + 40, b[5]);
            List<net.minecraft.world.entity.npc.Villager> all = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, box,
                    v -> v.getTags().contains(CityService.TAG));
            // Les plus entourés d'abord ; pour chacun, une place libre à l'air libre d'où on le voit sans obstacle.
            List<net.minecraft.world.entity.npc.Villager> order = new ArrayList<>(all);
            order.sort(java.util.Comparator.comparingInt(v -> {
                int near = 0;
                for (var o : all) if (o.distanceToSqr(v) < 100) near++;
                return -near;
            }));
            net.minecraft.world.entity.npc.Villager pick = null;
            double cx = 0, cy = 0, cz = 0;
            search:
            for (var v : order) {
                if (!level.canSeeSky(v.blockPosition().above())) continue;
                for (int[] o : new int[][]{{0, 5, 7}, {7, 5, 0}, {-7, 5, 0}, {0, 5, -7}, {5, 7, 5}, {-5, 7, -5}, {5, 7, -5}, {-5, 7, 5}}) {
                    BlockPos cam = BlockPos.containing(v.getX() + o[0], v.getY() + o[1], v.getZ() + o[2]);
                    if (!level.getBlockState(cam).isAir() || !level.getBlockState(cam.above()).isAir() || !level.canSeeSky(cam)) continue;
                    net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(v.getX() + o[0], v.getY() + o[1] + 1.62, v.getZ() + o[2]);
                    var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, v.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, v));
                    if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
                    pick = v;
                    cx = eye.x;
                    cy = v.getY() + o[1];
                    cz = eye.z;
                    break search;
                }
            }
            CrusadeCraft.LOGGER.info("WorldShots v5 : {} habitants dans la ville ; vue dégagée : {}", all.size(), pick != null);
            if (pick == null) return;
            double dx = pick.getX() - cx, dy = pick.getY() + 1 - (cy + 1.62), dz = pick.getZ() - cz;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            look(player(server), cx, cy, cz, yaw, pitch);
        }, null, 60, "v5_habitants"));
        out.add(new Step("fin", null, mc -> mc.setScreen(null), 10, null));
        return out;
    }
}
