package fr.crusadecraft.client.dev;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.Journals;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityEggs;
import fr.crusadecraft.core.city.CityEconomy;
import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.core.city.CityLife;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.city.CityStaff;
import fr.crusadecraft.core.city.CityTalk;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.packet.NationPackets;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Le banc d'essai en monde réel des phases 41 à 43 ({@code ./gradlew runWorldShots -Pshots_v6=true}) : Dolhimluith et
 * Grunbach rejoignent leurs nations ; leurs bâtiments reçoivent une fonction ; on regarde une vraie journée des habitants
 * (où ils sont, ce qu'ils font, ce qu'ils disent), l'écran de la nation, la conversation, l'œuf, puis plusieurs jours en comptes.
 */
final class WorldShotsV6 {
    record Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot, BooleanSupplier until) {
        Step(String name, Consumer<MinecraftServer> server, Consumer<Minecraft> client, int ticks, String shot) {
            this(name, server, client, ticks, shot, null);
        }
    }

    private record City(String label, String ref, int offset) {}

    private static final List<City> CITIES = List.of(
            new City("dolhimluith", new PlanRef(PlanRef.Source.FILE, "plans", "Levenzia/dolhimluith.schem").encode(), 300),
            new City("grunbach", new PlanRef(PlanRef.Source.FILE, "plans", "Varenhold/grunbach.schem").encode(), 720));
    /** Des fonctions variées : la reconnaissance propose surtout des maisons, ces villes n'ayant ni lit ni enclume. */
    private static final BuildingFunction[] MIX = {BuildingFunction.MARCHE, BuildingFunction.BOULANGERIE, BuildingFunction.TAVERNE, BuildingFunction.FORGE,
            BuildingFunction.MAIRIE, BuildingFunction.TEMPLE, BuildingFunction.FERME, BuildingFunction.CASERNE};

    private static List<Step> steps;
    private static int index = -1, wait, waited;
    private static boolean acted;
    private static BlockPos origin;
    private static final Map<String, BlockPos> GROUND = new HashMap<>();
    private static final Map<String, UUID> SITES = new HashMap<>();
    private static volatile boolean pasted;

    private WorldShotsV6() {}

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
        CrusadeCraft.LOGGER.info("WorldShots v6 : étape {}", s.name());
        if (s.server() != null) server.execute(() -> {
            try {
                s.server().accept(server);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("WorldShots v6 : l'étape {} a échoué côté serveur", s.name(), e);
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

    private static Site site(MinecraftServer server, String label) {
        UUID id = SITES.get(label);
        return id == null ? null : CrusadeSavedData.get(server).world().site(id).orElse(null);
    }

    private static List<Villager> people(ServerLevel level, Site s) {
        int[] b = CityService.box(s);
        return level.getEntitiesOfClass(Villager.class, new AABB(b[0], b[1], b[2], b[3], b[4] + 40, b[5]), v -> v.getTags().contains(CityService.TAG));
    }

    /** Où en sont les gens : le compte de ceux qui sont près de leur poste, en marche, en main. */
    private static void census(MinecraftServer server, String label, String moment) {
        Site s = site(server, label);
        if (s == null) return;
        ServerLevel level = player(server).serverLevel();
        var state = CrusadeSavedData.get(server).lineage();
        int total = 0, atWork = 0, moving = 0, holding = 0;
        for (Villager v : people(level, s)) {
            Person p = state.person(v.getUUID()).orElse(null);
            if (p == null) continue;
            total++;
            if (p.work() != null && v.blockPosition().distSqr(p.work()) < 64) atWork++;
            if (v.getNavigation().isInProgress() || v.getDeltaMovement().horizontalDistanceSqr() > 0.0005) moving++;
            if (!v.getMainHandItem().isEmpty()) holding++;
        }
        CrusadeCraft.LOGGER.info("WorldShots v6 : {} {} : {} dans le monde, {} près de leur poste, {} en marche, {} un outil en main", label, moment, total, atWork, moving, holding);
    }

    private static String stock(CityEconomy e) {
        StringBuilder out = new StringBuilder("bourse " + e.purse() + ", famine " + e.famine());
        for (CityGood g : CityGood.values()) out.append(' ').append(g.name()).append('=').append(e.stock(g));
        return out.toString();
    }

    /** La vue de quelqu'un de cette fonction, dehors, pour voir sa bulle et son geste. */
    private static void watchSomeone(MinecraftServer server, String label) {
        Site s = site(server, label);
        if (s == null) return;
        ServerPlayer p = player(server);
        ServerLevel level = p.serverLevel();
        List<Villager> all = people(level, s);
        all.sort(java.util.Comparator.comparingInt(v -> {
            int near = 0;
            for (Villager o : all) if (o.distanceToSqr(v) < 144) near++;
            return -near;
        }));
        for (Villager v : all) {
            if (!level.canSeeSky(v.blockPosition().above())) continue;
            for (int[] o : new int[][]{{0, 4, 7}, {7, 4, 0}, {-7, 4, 0}, {0, 4, -7}, {5, 6, 5}, {-5, 6, -5}, {5, 6, -5}, {-5, 6, 5}}) {
                BlockPos cam = BlockPos.containing(v.getX() + o[0], v.getY() + o[1], v.getZ() + o[2]);
                if (!level.getBlockState(cam).isAir() || !level.getBlockState(cam.above()).isAir() || !level.canSeeSky(cam)) continue;
                var eye = new net.minecraft.world.phys.Vec3(v.getX() + o[0], v.getY() + o[1] + 1.62, v.getZ() + o[2]);
                var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, v.getEyePosition(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, v));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
                double dx = v.getX() - eye.x, dy = v.getY() + 1 - eye.y, dz = v.getZ() - eye.z;
                look(p, eye.x, v.getY() + o[1], eye.z, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
                return;
            }
        }
    }

    private static List<Step> build() {
        List<Step> out = new ArrayList<>();
        out.add(new Step("depart", server -> {
            ServerPlayer p = player(server);
            WorldEditService.grantForTests(p.getUUID(), true);
            origin = p.blockPosition();
            for (City c : CITIES) {
                for (Site s : CrusadeSavedData.get(server).world().sites()) {
                    if (s.imported() && c.ref().equals(s.importedRef())) {
                        SITES.put(c.label(), s.id());
                        GROUND.put(c.label(), s.anchor());
                    }
                }
            }
            for (var n : NationBook.all()) CrusadeCraft.LOGGER.info("WorldShots v6 : nation {} ({}), {} repliques propres", n.key(), n.name(), n.lines().size());
            for (AiRealm r : CrusadeSavedData.get(server).world().realms()) {
                if (!r.nation().isEmpty()) CrusadeCraft.LOGGER.info("WorldShots v6 : {} existe, sans terre : {}, trésor {}", r.place(), r.landless(), r.gold());
            }
        }, mc -> mc.setScreen(null), 20, null));
        for (City c : CITIES) {
            out.add(new Step("pose_" + c.label(), server -> {
                if (site(server, c.label()) != null) {
                    pasted = true;
                    return;
                }
                ServerPlayer p = player(server);
                ServerLevel level = p.serverLevel();
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PLAN.get()));
                CrusadeCraft.LOGGER.info("WorldShots v6 : {}", PlanTool.choose(p, c.ref()).message().getString());
                int x = origin.getX() + c.offset(), z = origin.getZ() + 60;
                for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) level.getChunk((x >> 4) + dx, (z >> 4) + dz);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockPos ground = new BlockPos(x, y, z);
                GROUND.put(c.label(), ground);
                look(p, x + 0.5, y + 60, z + 120.5, 180f, 25f);
                pasted = false;
                PlanTool.paste(p, p.getMainHandItem(), ground);
                for (Site s : CrusadeSavedData.get(server).world().sites()) if (s.imported() && c.ref().equals(s.importedRef())) SITES.put(c.label(), s.id());
                Site s = site(server, c.label());
                CrusadeCraft.LOGGER.info("WorldShots v6 : {} posée, royaume : {}", c.label(), s == null ? "?" : CrusadeSavedData.get(server).world().realm(s.owner()).map(AiRealm::place).orElse("?"));
            }, mc -> {
                mc.setScreen(null);
                fr.crusadecraft.client.map.PlanControl.reset();
                pasted = false;
            }, 40, null, () -> {
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                UUID id = SITES.get(c.label());
                if (server != null && id != null) server.execute(() -> pasted = !Journals.pasting(server, id));
                return pasted;
            }));
            out.add(new Step("fonctions_" + c.label(), server -> {
                ServerPlayer p = player(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Site s = site(server, c.label());
                if (s == null) return;
                int i = 0;
                for (CityBuilding b : s.buildings()) {
                    BuildingFunction f = i < MIX.length ? MIX[i] : BuildingFunction.MAISON;
                    i++;
                    CityService.define(p, s.id(), b, f, "");
                }
                int n = CityService.populate(p.serverLevel(), s);
                var state = CrusadeSavedData.get(server).lineage();
                CrusadeCraft.LOGGER.info("WorldShots v6 : {} : {} bâtiments, {} au registre, {} apparus", c.label(), s.buildings().size(), CityService.people(server, s), n);
                StringBuilder names = new StringBuilder();
                for (CityBuilding b : s.buildings()) for (UUID id : b.occupants()) state.person(id).ifPresent(x -> names.append(state.fullName(x)).append(x.notable().isEmpty() ? "" : "*").append(" (").append(x.job()).append("), "));
                CrusadeCraft.LOGGER.info("WorldShots v6 : {} : {}", c.label(), names);
                p.serverLevel().setDayTime(1000);
            }, mc -> mc.setScreen(null), 40, null));
        }
        // Une journée à Dolhimluith.
        out.add(new Step("matin", server -> {
            BlockPos g = GROUND.get("dolhimluith");
            look(player(server), g.getX() + 0.5, g.getY() + 40, g.getZ() + 40.5, 180f, 40f);
        }, null, 800, null));
        out.add(new Step("matin_vue", server -> {
            census(server, "dolhimluith", "le matin");
            watchSomeone(server, "dolhimluith");
        }, null, 200, "v9_matin"));
        out.add(new Step("grunbach_rue", server -> {
            census(server, "grunbach", "le matin");
            watchSomeone(server, "grunbach");
        }, null, 200, "v9_grunbach_rue"));
        out.add(new Step("midi", server -> {
            player(server).serverLevel().setDayTime(5500);
            census(server, "dolhimluith", "avant midi");
            BlockPos g = GROUND.get("dolhimluith");
            look(player(server), g.getX() + 0.5, g.getY() + 40, g.getZ() + 40.5, 180f, 40f);
        }, null, 700, null));
        out.add(new Step("midi_vue", server -> {
            census(server, "dolhimluith", "à midi");
            CrusadeCraft.LOGGER.info("WorldShots v6 : {} bulles", fr.crusadecraft.core.city.CityBubbles.active());
            watchSomeone(server, "dolhimluith");
        }, null, 200, "v9_midi"));
        out.add(new Step("soir", server -> {
            player(server).serverLevel().setDayTime(11300);
            census(server, "dolhimluith", "fin de journée");
        }, null, 600, null));
        out.add(new Step("soir_vue", server -> {
            census(server, "dolhimluith", "le soir");
            CrusadeCraft.LOGGER.info("WorldShots v6 : {} bulles", fr.crusadecraft.core.city.CityBubbles.active());
            watchSomeone(server, "dolhimluith");
        }, null, 200, "v9_soir"));
        // La conversation : la boulangère de Dolhimluith, puis quelqu'un d'ordinaire.
        out.add(new Step("parler", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            player(server).serverLevel().setDayTime(3000);
            if (s == null) return;
            var state = CrusadeSavedData.get(server).lineage();
            Villager pick = null;
            for (Villager v : people(p.serverLevel(), s)) {
                Person x = state.person(v.getUUID()).orElse(null);
                if (x != null && "BOULANGERIE".equals(x.job())) pick = v;
            }
            if (pick == null && !people(p.serverLevel(), s).isEmpty()) pick = people(p.serverLevel(), s).get(0);
            if (pick == null) return;
            look(p, pick.getX() + 2, pick.getY() + 1, pick.getZ() + 2, 135f, 10f);
            CrusadeCraft.LOGGER.info("WorldShots v6 : on parle à {} : {}", pick.getName().getString(), CityTalk.open(p, pick));
        }, null, 60, "v9_conversation"));
        out.add(new Step("parler_nouvelles", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            for (Villager v : people(p.serverLevel(), s)) {
                if (v.distanceToSqr(p) < 400) {
                    CityTalk.say(p, v.getUUID(), CityTalk.Topic.METIER);
                    break;
                }
            }
        }, null, 40, "v9_conversation_metier"));
        // Commercer, depuis sa fiche et au comptoir ; un joueur qui s'assied.
        out.add(new Step("commerce", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            Villager v = people(p.serverLevel(), s).stream().filter(x -> !x.getOffers().isEmpty()).findFirst().orElse(null);
            if (v == null) {
                CrusadeCraft.LOGGER.info("WorldShots v6 : personne ne vend");
                return;
            }
            look(p, v.getX() + 2, v.getY() + 1, v.getZ() + 2, 135f, 10f);
            CityTalk.trade(p, v.getUUID());
            CrusadeCraft.LOGGER.info("WorldShots v6 : étal de {} : {} offres, en cours {}", v.getName().getString(), v.getOffers().size(), v.isTrading());
        }, mc -> mc.setScreen(null), 40, "v9_commerce"));
        out.add(new Step("comptoir", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            if (p.containerMenu != p.inventoryMenu) p.closeContainer();
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            for (Villager x : people(level, s)) if (x.isTrading()) x.setTradingPlayer(null);
            net.minecraft.world.level.block.Block table = null;
            for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
                if (fr.crusadecraft.core.city.Furnishing.of(path) == fr.crusadecraft.core.city.Furnishing.Kind.TABLE) {
                    table = b;
                    break;
                }
            }
            for (CityBuilding b : s.buildings()) {
                if (b.function() == BuildingFunction.MARCHE || b.function() == BuildingFunction.FORGE) {
                    for (java.util.UUID id : b.occupants()) {
                        if (!(level.getEntity(id) instanceof Villager v) || v.getOffers().isEmpty()) continue;
                        BlockPos at = BlockPos.containing(v.getX(), v.getY(), v.getZ()).offset(1, 0, 0);
                        if (table != null && !b.contains(at)) at = b.spot();
                        if (table != null) level.setBlock(at, table.defaultBlockState(), 3);
                        look(p, at.getX() + 2.5, at.getY() + 1, at.getZ() + 2.5, 135f, 10f);
                        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        var found = CityService.buildingAt(s, at).orElse(null);
                        CrusadeCraft.LOGGER.info("WorldShots v6 : comptoir {} en {} ; on y lit {} ; {} à {} blocs, en vente : {}", table, at, found == null ? "rien" : found.function(), v.getName().getString(),
                                (int) Math.sqrt(v.distanceToSqr(at.getCenter())), v.getOffers().size());
                        CrusadeCraft.LOGGER.info("WorldShots v6 : comptoir pris {} ; étal ouvert : {}", fr.crusadecraft.core.city.CityFurniture.onCounter(p, at), v.isTrading());
                        return;
                    }
                }
            }
        }, mc -> mc.setScreen(null), 40, "v9_comptoir"));
        out.add(new Step("joueur_assis", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            if (p.containerMenu != p.inventoryMenu) p.closeContainer();
            for (Villager x : people(level, site(server, "dolhimluith"))) if (x.isTrading()) x.setTradingPlayer(null);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos seat = p.blockPosition().offset(1, 0, 0);
            level.setBlock(seat, fr.crusadecraft.registry.ModBlocks.COUNCIL_SEAT.get().defaultBlockState(), 3);
            level.setBlock(seat.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            p.getAbilities().flying = false;
            p.teleportTo(seat.getX() + 1.5, seat.getY(), seat.getZ() + 0.5);
            boolean sat = fr.crusadecraft.core.city.CitySeats.onUse(p, seat);
            CrusadeCraft.LOGGER.info("WorldShots v6 : le joueur s'assied : {} ({})", sat, p.isPassenger());
        }, mc -> mc.setScreen(null), 40, null));
        out.add(new Step("joueur_debout", server -> {
            ServerPlayer p = player(server);
            p.stopRiding();
        }, null, 40, null));
        out.add(new Step("joueur_debout2", server -> {
            int left = player(server).serverLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, player(server).getBoundingBox().inflate(16),
                    a -> a.getTags().contains(fr.crusadecraft.core.city.CitySeats.TAG) && a.getPassengers().isEmpty()).size();
            CrusadeCraft.LOGGER.info("WorldShots v6 : supports vides qui restent : {}", left);
        }, null, 10, null));
        // Une bulle qu'on voit de près : celle d'un habitant qu'on regarde.
        out.add(new Step("bulle", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            Villager v = people(p.serverLevel(), s).stream().findFirst().orElse(null);
            if (v == null) return;
            look(p, v.getX() + 3, v.getY() + 1, v.getZ() + 3, 135f, 8f);
            fr.crusadecraft.core.city.CityBubbles.say(p.serverLevel(), v, "Le pain n'attend pas, et moi non plus.");
        }, mc -> mc.setScreen(null), 30, "v9_bulle"));
        // L'enseigne : une école dans une maison, et l'écran d'un bâtiment avec ses dix-neuf fonctions.
        out.add(new Step("enseigne", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            StringBuilder functions = new StringBuilder();
            CityBuilding house = null;
            for (CityBuilding b : s.buildings()) {
                functions.append(b.function()).append(' ');
                if (b.function() == BuildingFunction.MAISON || b.function() == BuildingFunction.ECOLE) house = b;
            }
            CrusadeCraft.LOGGER.info("WorldShots v6 : fonctions : {}", functions);
            if (house == null) house = s.buildings().get(s.buildings().size() - 1);
            BlockPos at = house.spot();
            ServerLevel level = p.serverLevel();
            level.setBlock(at, fr.crusadecraft.registry.ModBlocks.SIGNS.get(BuildingFunction.ECOLE).get().defaultBlockState()
                    .setValue(fr.crusadecraft.block.BuildingSignBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
            var res = fr.crusadecraft.core.city.CitySigns.onPlaced(p, at, BuildingFunction.ECOLE);
            CrusadeCraft.LOGGER.info("WorldShots v6 : enseigne : {} ; le bâtiment est {}", res.message().getString(), house.function());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SIGNS.get(BuildingFunction.BOULANGERIE).get()));
            // Pour les voir : trois enseignes dehors, à l'air libre.
            List<BlockPos> spots = fr.crusadecraft.core.city.BuildingScanAccess.outdoors(level, s);
            if (!spots.isEmpty()) {
                BlockPos o = spots.get(0).above();
                BuildingFunction[] show = {BuildingFunction.BOULANGERIE, BuildingFunction.ECOLE, BuildingFunction.FORGE};
                for (int k = 0; k < show.length; k++) {
                    level.setBlock(o.offset(k - 1, 0, 0), fr.crusadecraft.registry.ModBlocks.SIGNS.get(show[k]).get().defaultBlockState()
                            .setValue(fr.crusadecraft.block.BuildingSignBlock.FACING, net.minecraft.core.Direction.SOUTH), 2);
                }
                look(p, o.getX() + 0.5, o.getY() - 1, o.getZ() + 2.8, 180f, 10f);
            }
        }, mc -> mc.setScreen(null), 60, "v9_enseigne"));
        out.add(new Step("ecran_batiment", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.INTENDANT.get()));
            Site s = site(server, "dolhimluith");
            if (s == null || s.buildings().isEmpty()) return;
            CityBuilding b = s.buildings().get(0);
            CityStaff.useOn(p, p.getMainHandItem(), b.spot(), net.minecraft.core.Direction.UP, false);
        }, null, 40, "v9_batiment"));
        // S'asseoir, se coucher, sonner la cloche.
        out.add(new Step("assis", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ServerLevel level = p.serverLevel();
            level.setDayTime(11600);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            Villager v = people(level, s).stream().findFirst().orElse(null);
            if (v == null) return;
            BlockPos seat = v.blockPosition().offset(1, 0, 0);
            level.setBlock(seat, fr.crusadecraft.registry.ModBlocks.COUNCIL_SEAT.get().defaultBlockState(), 3);
            boolean ok = fr.crusadecraft.core.city.CitySeats.sit(level, v, seat);
            CrusadeCraft.LOGGER.info("WorldShots v6 : {} s'assied : {}", v.getName().getString(), ok);
            look(p, seat.getX() + 3.5, seat.getY() + 1.2, seat.getZ() + 3.5, 135f, 12f);
        }, mc -> mc.setScreen(null), 30, "v9_assis"));
        out.add(new Step("lit", server -> {
            ServerPlayer p = player(server);
            ServerLevel level = p.serverLevel();
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            var state = CrusadeSavedData.get(server).lineage();
            for (Villager v : people(level, s)) if (v.isPassenger()) v.stopRiding();
            // Sous un toit : deux cases libres à l'intérieur d'un bâtiment habité, et son habitant juste à côté.
            search:
            for (CityBuilding b : s.buildings()) {
                if (b.function() == null || b.occupants().isEmpty()) continue;
                net.minecraft.world.entity.Entity e = level.getEntity(b.occupants().get(0));
                if (!(e instanceof Villager v)) continue;
                for (BlockPos q : BlockPos.betweenClosed(b.min(), b.max().offset(0, -1, -1))) {
                    if (level.canSeeSky(q) || !fr.crusadecraft.core.city.BuildingScanAccess.standable(level, q) || !fr.crusadecraft.core.city.BuildingScanAccess.standable(level, q.south())) continue;
                    BlockPos foot = q.immutable(), head = foot.south();
                    var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
                    level.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
                    level.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
                    b.setStats(b.floor(), b.levels(), Map.of("lit", 1));
                    v.teleportTo(foot.getX() + 0.5, foot.getY() + 0.6, foot.getZ() + 0.5);
                    CrusadeCraft.LOGGER.info("WorldShots v6 : un lit en {} pour {} ({})", foot, state.person(v.getUUID()).map(state::fullName).orElse("?"), b.function());
                    look(p, foot.getX() + 0.5, foot.getY() + 0.3, foot.getZ() + 3.5, 180f, 12f);
                    break search;
                }
            }
            level.setDayTime(18000);
        }, mc -> mc.setScreen(null), 240, "v9_lit"));
        out.add(new Step("lit_compte", server -> {
            ServerLevel level = player(server).serverLevel();
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            CrusadeCraft.LOGGER.info("WorldShots v6 : la nuit, {} couchés sur {}", people(level, s).stream().filter(Villager::isSleeping).count(), people(level, s).size());
        }, null, 300, null));
        out.add(new Step("lit_matin", server -> {
            ServerLevel level = player(server).serverLevel();
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            CrusadeCraft.LOGGER.info("WorldShots v6 : cinq secondes plus tard, {} couchés", people(level, s).stream().filter(Villager::isSleeping).count());
            level.setDayTime(3000);
        }, null, 80, null));
        out.add(new Step("lit_leve", server -> {
            ServerLevel level = player(server).serverLevel();
            Site s = site(server, "dolhimluith");
            if (s != null) CrusadeCraft.LOGGER.info("WorldShots v6 : le matin, {} couchés", people(level, s).stream().filter(Villager::isSleeping).count());
        }, null, 10, null));
        out.add(new Step("cloche", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            BlockPos at = CityService.plazaOf(s);
            p.serverLevel().setBlock(at.above(2), net.minecraft.world.level.block.Blocks.BELL.defaultBlockState(), 3);
            fr.crusadecraft.core.city.CityFurniture.onBell(p, at);
            look(p, at.getX() + 0.5, at.getY() + 24, at.getZ() + 14.5, 180f, 58f);
        }, null, 1000, "v9_cloche"));
        out.add(new Step("cloche_compte", server -> {
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            BlockPos at = CityService.plazaOf(s);
            ServerLevel level = player(server).serverLevel();
            long near = people(level, s).stream().filter(v -> v.blockPosition().distSqr(at) < 144).count();
            CrusadeCraft.LOGGER.info("WorldShots v6 : à la cloche, {} sur {} autour d'elle", near, people(level, s).size());
        }, null, 10, null));
        // L'œuf.
        out.add(new Step("oeuf", server -> {
            ServerPlayer p = player(server);
            ItemStack egg = new ItemStack(ModItems.OEUF_HABITANT.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, egg);
            Site s = site(server, "dolhimluith");
            if (s != null) CityEggs.use(p, egg, CityService.plazaOf(s));
        }, mc -> mc.setScreen(null), 40, "v9_oeuf_ecran"));
        out.add(new Step("oeuf_nait", server -> {
            ServerPlayer p = player(server);
            Site s = site(server, "dolhimluith");
            if (s == null) return;
            BlockPos at = CityService.plazaOf(s);
            var out2 = CityEggs.choose(p, s.id(), BuildingFunction.FORGE, at);
            CrusadeCraft.LOGGER.info("WorldShots v6 : œuf : {}", out2.message().getString());
            look(p, at.getX() + 5.5, at.getY() + 3, at.getZ() + 5.5, 135f, 15f);
        }, mc -> mc.setScreen(null), 80, "v9_oeuf_ne"));
        // Vingt jours en comptes : les réserves, les rumeurs, le courrier.
        out.add(new Step("vingt_jours", server -> {
            ServerPlayer p = player(server);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Random random = new Random(42);
            for (String label : List.of("dolhimluith", "grunbach")) {
                Site s = site(server, label);
                if (s == null) continue;
                CrusadeCraft.LOGGER.info("WorldShots v6 : {} au début : {}", label, stock(s.economy()));
                for (int i = 0; i < 20; i++) {
                    CityEconomy.Day d = CityLife.day(server, s, random);
                    if (i == 0 || i == 19) CrusadeCraft.LOGGER.info("WorldShots v6 : {} jour {} : faim {}, manque {}, impôt {}, importé {}, exporté {}", label, i, d.hungry(), d.missing(), d.tax(), d.imported(), d.exported());
                }
                CrusadeCraft.LOGGER.info("WorldShots v6 : {} après 20 jours : {} ; {} au registre", label, stock(s.economy()), CityService.people(server, s));
                for (Site.Rumor r : s.rumors()) CrusadeCraft.LOGGER.info("WorldShots v6 : {} rumeur : {}", label, r.text());
                var talk = new Random(7);
                var st = CrusadeSavedData.get(server).lineage();
                for (CityBuilding b : s.buildings()) {
                    for (UUID id : b.occupants()) {
                        st.person(id).filter(Person::alive).ifPresent(x -> {
                            if (talk.nextInt(5) != 0) return;
                            CrusadeCraft.LOGGER.info("WorldShots v6 : {} ({}) : {} | {}", st.fullName(x), x.age(),
                                    fr.crusadecraft.core.city.CityVoice.text(server, s, x, fr.crusadecraft.core.lineage.Repliques.Moment.VILLE, p, talk).orElse("?"),
                                    fr.crusadecraft.core.city.CityVoice.text(server, s, x, fr.crusadecraft.core.lineage.Repliques.Moment.RUMEUR_VRAIE, p, talk).orElse("?"));
                        });
                    }
                }
            }
            for (long d = 1; d <= 30; d++) NationService.onNewDay(server, d);
            NationService.realm(server, "levanzia").ifPresent(r -> {
                CrusadeCraft.LOGGER.info("WorldShots v6 : Levànzia : souverain {}, trésor {}, {} lettres", r.sovereignName(), r.gold(), r.mail().size());
                r.mail().forEach(m -> CrusadeCraft.LOGGER.info("WorldShots v6 : lettre de {} : {}", m.sender(), m.body()));
                NationPackets.send(p, r);
            });
        }, null, 40, "v9_nation_histoire"));
        out.add(new Step("nation_relations", null, mc -> {
            if (mc.screen instanceof fr.crusadecraft.client.screen.NationScreen s) s.showForShots(2);
        }, 30, "v9_nation_relations"));
        out.add(new Step("nation_villes", null, mc -> {
            if (mc.screen instanceof fr.crusadecraft.client.screen.NationScreen s) s.showForShots(1);
        }, 30, "v9_nation_villes"));
        out.add(new Step("nation_courrier", null, mc -> {
            if (mc.screen instanceof fr.crusadecraft.client.screen.NationScreen s) s.showForShots(3);
        }, 30, "v9_nation_courrier"));
        out.add(new Step("varenhold", server -> NationService.realm(server, "varenhold").ifPresent(r -> NationPackets.send(player(server), r)), mc -> mc.setScreen(null), 30, "v9_varenhold"));
        out.add(new Step("fin", server -> {
            StringBuilder perf = new StringBuilder();
            CrusadeCraft.LOGGER.info("WorldShots v6 : terminé {}", perf);
        }, mc -> mc.setScreen(null), 10, null));
        return out;
    }
}
