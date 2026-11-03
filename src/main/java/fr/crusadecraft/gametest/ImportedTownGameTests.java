package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.build.plan.Journals;
import fr.crusadecraft.core.build.plan.Plan;
import fr.crusadecraft.core.build.plan.PlanBuilder;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanPlacer;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.build.plan.RawPlan;
import fr.crusadecraft.core.build.plan.SchematicReader;
import fr.crusadecraft.core.build.plan.Varints;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.BuildingScan;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * P38 : les villes importées et le bâton d'intendant. Une schématique bâtie avec un mod qu'on n'a pas se lit avec ses
 * équivalents de même forme ; une très grande se pose sans être tenue en mémoire, à l'identique ; un bâtiment se
 * reconnaît depuis sa porte, reçoit une fonction, et ses gens arrivent avec leur métier et leurs échanges.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ImportedTownGameTests {
    private ImportedTownGameTests() {}

    private static BlockPos ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static void write(Path file, CompoundTag tag) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    /** Une schématique Sponge 3 : sa palette (clé → numéro), ses cases (x + z·W + y·W·L). */
    private static CompoundTag sponge3(int w, int h, int l, List<String> palette, int[] cells) {
        CompoundTag root = new CompoundTag();
        CompoundTag s = new CompoundTag();
        s.putInt("Version", 3);
        s.putInt("DataVersion", 3955);
        s.putShort("Width", (short) w);
        s.putShort("Height", (short) h);
        s.putShort("Length", (short) l);
        CompoundTag blocks = new CompoundTag();
        CompoundTag pal = new CompoundTag();
        for (int i = 0; i < palette.size(); i++) pal.putInt(palette.get(i), i);
        blocks.put("Palette", pal);
        blocks.putByteArray("Data", Varints.encode(cells));
        s.put("Blocks", blocks);
        root.put("Schematic", s);
        return root;
    }

    private static boolean dawnOfTime() {
        return BuiltInRegistries.BLOCK.containsKey(new net.minecraft.resources.ResourceLocation("dawnoftimebuilder", "charred_spruce_planks_plate"));
    }

    // ------------------------------------------------------------------ les équivalences

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p38_equivalences", timeoutTicks = 100)
    public static void blocsDUnAutreModMemeForme(GameTestHelper h) {
        Path file = PlanLibrary.roots().get("plans").resolve("essai_p38").resolve("middle_earth.schem");
        try {
            List<String> palette = List.of("minecraft:air",
                    "me:black_lebethron_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                    "me:black_lebethron_vertical_slab[double=false,facing=north,shape=straight,waterlogged=false]",
                    "me:wattle_and_white_daub_cross",
                    "minecraft:short_grass",
                    "minecraft:stone",
                    "me:black_lebethron_vertical_slab[double=true,facing=north,shape=straight,waterlogged=false]",
                    "me:forge[facing=north,lit=false,part=top]",
                    "me:lebethron_leaves[distance=3,persistent=false,waterlogged=false]",
                    "unmod:bloc_absent");
            // 3 × 1 × 3 : un colombage entre deux pierres (mur est–ouest), puis le reste.
            int[] cells = {5, 3, 5, 1, 2, 6, 4, 7, 8};
            write(file, sponge3(3, 1, 3, palette, cells));
            RawPlan raw = SchematicReader.read(file);
            if (raw.replaced() != 6) h.fail("sortes remplacées : " + raw.replaced());
            // Un bloc sans aucun équivalent est laissé de côté, et nommé.
            if (!raw.unknown().equals(List.of("unmod:bloc_absent"))) h.fail("inconnus : " + raw.unknown());
            BlockState[] at = new BlockState[9];
            CompoundTag[] tags = new CompoundTag[9];
            for (RawPlan.Cell c : raw.cells()) {
                at[c.x() + c.z() * 3] = c.state();
                tags[c.x() + c.z() * 3] = c.tag();
            }
            boolean dot = dawnOfTime();
            // L'escalier reste un escalier, tourné pareil.
            String stairs = BuiltInRegistries.BLOCK.getKey(at[3].getBlock()).toString();
            if (!stairs.equals(dot ? "dawnoftimebuilder:charred_spruce_planks_stairs" : "minecraft:dark_oak_stairs")) h.fail("escalier : " + stairs);
            if (at[3].getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.EAST) h.fail("l'escalier ne regarde plus l'est");
            // La demi-dalle verticale devient une plaque, du côté qu'elle occupait (le nord de Middle-earth est le sud ici).
            if (dot) {
                if (!BuiltInRegistries.BLOCK.getKey(at[4].getBlock()).getPath().equals("charred_spruce_planks_plate")) h.fail("pas de plaque : " + at[4]);
                if (at[4].getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.SOUTH) h.fail("la plaque n'est pas retournée : " + at[4]);
                if (!BuiltInRegistries.BLOCK.getKey(at[5].getBlock()).getPath().equals("charred_spruce_planks")) h.fail("la demi-dalle double n'est pas pleine");
            }
            // Le colombage de Domum Ornamentum garde son motif, son bois, son torchis, et regarde le nord (mur est–ouest).
            String frame = BuiltInRegistries.BLOCK.getKey(at[1].getBlock()).toString();
            if (!frame.equals("domum_ornamentum:double_crossed")) h.fail("colombage : " + frame);
            if (tags[1] == null || !"minecraft:calcite".equals(tags[1].getCompound("textureData").getString("minecraft:block/dark_oak_planks"))) {
                h.fail("le torchis du colombage manque : " + tags[1]);
            }
            if (at[1].hasProperty(BlockStateProperties.FACING) && at[1].getValue(BlockStateProperties.FACING) != Direction.NORTH) {
                h.fail("le colombage n'est pas tourné selon son mur : " + at[1]);
            }
            if (!at[6].is(Blocks.GRASS)) h.fail("l'herbe courte de 1.21 n'est pas devenue de l'herbe");
            if (at[8].getBlock() != Blocks.DARK_OAK_LEAVES || !at[8].getValue(BlockStateProperties.PERSISTENT)) h.fail("feuillage : " + at[8]);
            if (dot && !BuiltInRegistries.BLOCK.getKey(at[7].getBlock()).getPath().contains("chimney")) h.fail("haut de la forge : " + at[7]);
            h.succeed();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.getParent());
            } catch (java.io.IOException ignored) {
                // le dossier d'essai reste
            }
        }
    }

    // ------------------------------------------------------------------ une très grande schématique

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p38_grande", timeoutTicks = 200)
    public static void grandeSchematiquePoseeALIdentique(GameTestHelper h) {
        Path file = PlanLibrary.roots().get("plans").resolve("essai_p38").resolve("colline.schem");
        int before = SchematicReader.setLazyVolume(10);
        try {
            // 6 × 4 × 6 : de la terre, de l'herbe dessus (le sol, en y = 1), un poteau de chêne au milieu, de l'air.
            int w = 6, hh = 4, l = 6;
            List<String> palette = List.of("minecraft:air", "minecraft:dirt", "minecraft:grass_block[snowy=false]", "minecraft:oak_planks");
            int[] cells = new int[w * hh * l];
            for (int y = 0; y < hh; y++) {
                for (int z = 0; z < l; z++) {
                    for (int x = 0; x < w; x++) {
                        int v = y == 0 ? 1 : y == 1 ? 2 : (x == 3 && z == 3 ? 3 : 0);
                        cells[x + z * w + y * w * l] = v;
                    }
                }
            }
            write(file, sponge3(w, hh, l, palette, cells));
            RawPlan lazy = SchematicReader.read(file);
            if (!lazy.cells().getClass().getSimpleName().equals("GridCells")) h.fail("les cases ne sont pas lues à la demande");
            if (lazy.groundY() != 1) h.fail("le sol n'est pas sur la couche d'herbe : " + lazy.groundY());
            SchematicReader.setLazyVolume(before);
            RawPlan eager = SchematicReader.read(file);
            if (eager.cells().getClass().getSimpleName().equals("GridCells")) h.fail("le petit plan est lu à la demande");
            PlanRef ref = new PlanRef(PlanRef.Source.FILE, "plans", "essai_p38/colline.schem");
            Plan a = PlanBuilder.build(ref, lazy, true), b = PlanBuilder.build(ref, eager, true);
            if (a.placements().size() != b.placements().size()) h.fail("pas le même nombre de poses : " + a.placements().size() + " / " + b.placements().size());
            Set<String> sa = new HashSet<>(), sb = new HashSet<>();
            for (var p : a.placements()) sa.add(p.x() + "," + p.y() + "," + p.z() + "," + p.state() + "," + p.foundation());
            for (var p : b.placements()) sb.add(p.x() + "," + p.y() + "," + p.z() + "," + p.state() + "," + p.foundation());
            if (!sa.equals(sb)) h.fail("la lecture à la demande ne donne pas les mêmes poses");
            // Posée : l'herbe au niveau du bloc visé, le poteau au-dessus.
            ServerLevel level = h.getLevel();
            BlockPos at = ground(level, h.absolutePos(BlockPos.ZERO).getX() + 3300, h.absolutePos(BlockPos.ZERO).getZ());
            UUID id = UUID.randomUUID();
            PlanPlacer.advance(level, at, Rotation.NONE, a.placements(), 0, 100_000, Journals.journal(level.getServer(), id, level.dimension().location()));
            if (!level.getBlockState(at).is(Blocks.GRASS_BLOCK)) h.fail("le sol de la colline n'est pas au niveau visé : " + level.getBlockState(at));
            if (!level.getBlockState(at.offset(0, 1, 0)).is(Blocks.OAK_PLANKS)) h.fail("le poteau manque");
            Journals.undo(level.getServer(), id);
            for (int i = 0; i < 60 && Journals.undoing(level.getServer(), id); i++) Journals.tick(level.getServer());
            if (Journals.undoing(level.getServer(), id)) h.fail("la colline ne se défait pas");
            h.succeed();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            SchematicReader.setLazyVolume(before);
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.getParent());
            } catch (java.io.IOException ignored) {
                // le dossier d'essai reste
            }
        }
    }

    // ------------------------------------------------------------------ une ville entière devient une ville

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p38_ville", timeoutTicks = 1200)
    public static void villeImporteeDevientVilleDuMonde(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Varenhold");
        Path file = PlanLibrary.roots().get("plans").resolve("Varenhold").resolve("grunbach_essai.schem");
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        UUID id = null;
        try {
            int s = Plan.CITY, hh = 3;
            List<String> palette = List.of("minecraft:air", "minecraft:dirt", "minecraft:grass_block[snowy=false]");
            int[] cells = new int[s * hh * s];
            for (int y = 0; y < 2; y++) for (int i = 0; i < s * s; i++) cells[i + y * s * s] = y + 1;
            write(file, sponge3(s, hh, s, palette, cells));
            // Le sous-dossier fait l'étagère du catalogue.
            PlanRef ref = new PlanRef(PlanRef.Source.FILE, "plans", "Varenhold/grunbach_essai.schem");
            if (PlanLibrary.fileEntries().stream().noneMatch(e -> e.ref().equals(ref.encode()) && e.category().equals("Varenhold"))) {
                h.fail("la ville n'est pas rangée sous Varenhold");
            }
            Plan plan = PlanLibrary.load(ref, true).orElseThrow();
            if (!plan.city()) h.fail("un plan de " + s + " de côté n'est pas une ville entière");
            BlockPos at = ground(level, w.center.getX() + 3600, w.center.getZ());
            id = Journals.paste(w.ruler, at, 0, ref.encode(), null, plan.name());
            SiteService.registerImportedTown(w.ruler, id, at, plan, ref.encode());
            Optional<Site> site = data.world().site(id);
            if (site.isEmpty() || !site.get().imported()) h.fail("la ville importée n'est pas au registre");
            if (!site.get().name().equals("Grunbach essai")) h.fail("nom : " + site.get().name());
            if (site.get().extent() != s) h.fail("emprise : " + site.get().extent());
            for (int i = 0; i < 400 && Journals.pasting(server, id); i++) Journals.tick(server);
            if (Journals.pasting(server, id)) h.fail("la ville n'est pas posée");
            if (!CityService.cityAt(server, level.dimension().location(), at).map(x -> x.id().equals(site.get().id())).orElse(false)) {
                h.fail("le centre de la ville n'est pas dans la ville");
            }
            h.succeed();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (id != null) {
                Journals.undo(server, id);
                for (int i = 0; i < 400 && Journals.undoing(server, id); i++) Journals.tick(server);
            }
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(file.getParent());
            } catch (java.io.IOException ignored) {
                // le dossier d'essai reste
            }
            w.cleanup();
        }
    }

    // ------------------------------------------------------------------ P39 : fonder sur place, commandes, fantôme

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p39_fonder", timeoutTicks = 400)
    public static void fonderSurPlaceEtCommandes(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Fondeville");
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        BlockPos base = ground(level, w.center.getX() + 4300, w.center.getZ());
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            smithy(level, base);
            // Devant la forge : on fonde la ville sur ce qui est bâti.
            w.ruler.setPos(base.getX() + 3.5, base.getY() + 1, base.getZ() + 9.5);
            Outcome out = CityService.found(w.ruler, "Fondeville", 24);
            if (!out.success()) h.fail("fondation refusée : " + out.message().getString());
            Optional<Site> site = CityService.cityAt(server, level.dimension().location(), w.ruler.blockPosition());
            if (site.isEmpty()) h.fail("la ville fondée n'est pas là");
            if (site.get().buildings().size() != 1 || site.get().buildings().get(0).suggestion() != BuildingFunction.FORGE) {
                h.fail("bâtiments reconnus : " + site.get().buildings().size());
            }
            if (CityService.found(w.ruler, "Encore", 24).success()) h.fail("une ville fondée dans une autre");
            // Dans la forge : la commande lui donne une fonction et un nom.
            w.ruler.setPos(base.getX() + 3.5, base.getY() + 1, base.getZ() + 3.5);
            Outcome f = fr.crusadecraft.core.city.CityCommands.function(w.ruler, "taverne", "Au Lion d'or");
            if (!f.success()) h.fail("commande fonction : " + f.message().getString());
            CityBuilding b = site.get().buildings().get(0);
            if (b.function() != BuildingFunction.TAVERNE || !b.name().equals("Au Lion d'or") || b.occupants().size() != 1) h.fail("la taverne n'est pas faite");
            if (fr.crusadecraft.core.city.CityCommands.info(w.ruler).size() < 4) h.fail("l'info de la ville est courte");
            if (!fr.crusadecraft.core.city.CityCommands.function(w.ruler, "port", "").message().getString().contains("port")) h.fail("une fonction inconnue passe");
            // Le fantôme d'une ville entière : borné, et d'abord ce qui est bâti.
            List<fr.crusadecraft.core.build.Blueprints.Placement> town = fr.crusadecraft.core.build.plan.PlanTool.ghostPlacements("town:EUROPEENNE:VILLAGE:7");
            if (town == null || town.isEmpty()) h.fail("pas de poses pour le fantôme d'une ville");
            int[][] g = fr.crusadecraft.core.build.plan.PlanTool.ghost(town);
            if (g[0].length == 0 || g[0].length > fr.crusadecraft.core.build.plan.PlanTool.GHOST_LIMIT || g[0].length != g[1].length) {
                h.fail("fantôme de la ville : " + g[0].length);
            }
            h.succeed();
        } finally {
            CityService.cityAt(server, level.dimension().location(), base).ifPresent(s -> {
                for (CityBuilding b : s.buildings()) {
                    for (UUID u : b.occupants()) {
                        Entity e = level.getEntity(u);
                        if (e != null) e.discard();
                    }
                }
            });
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(8, 9, 8))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }

    // ------------------------------------------------------------------ les villes livrées

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p38_livrees", timeoutTicks = 200)
    public static void villesDeRomainLivreesAvecLeMod(GameTestHelper h) {
        PlanLibrary.installBundled();
        for (String[] c : new String[][]{{"Levenzia", "Levenzia/dolhimluith.schem"}, {"Varenhold", "Varenhold/grunbach.schem"}}) {
            PlanRef ref = new PlanRef(PlanRef.Source.FILE, "plans", c[1]);
            if (PlanLibrary.fileEntries().stream().noneMatch(e -> e.ref().equals(ref.encode()) && e.category().equals(c[0]))) {
                h.fail(c[1] + " n'est pas au catalogue sous " + c[0]);
            }
            Optional<Plan> plan = PlanLibrary.load(ref, true);
            if (plan.isEmpty()) h.fail(c[1] + " ne se lit pas");
            if (!plan.get().city()) h.fail(c[1] + " n'est pas une ville entière");
            if (!plan.get().unknown().isEmpty()) h.fail(c[1] + " : sans équivalent " + plan.get().unknown());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ le bâton d'intendant

    /** Une petite forge de pierre : 7 × 7, porte au sud, une enclume et un haut fourneau dedans. Renvoie la porte. */
    private static BlockPos smithy(ServerLevel level, BlockPos base) {
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 1, -2), base.offset(8, 9, 8))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                level.setBlock(base.offset(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(base.offset(x, 5, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                for (int y = 1; y <= 4; y++) if (x == 0 || x == 6 || z == 0 || z == 6) level.setBlock(base.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        BlockPos door = base.offset(3, 1, 6);
        BlockState d = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(door, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(door.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        level.setBlock(base.offset(1, 1, 1), Blocks.ANVIL.defaultBlockState(), 2);
        level.setBlock(base.offset(2, 1, 1), Blocks.BLAST_FURNACE.defaultBlockState(), 2);
        return door;
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p38_baton", timeoutTicks = 400)
    public static void batonIntendantForgeEtForgeron(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Levenzia");
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        BlockPos base = ground(level, w.center.getX() + 3900, w.center.getZ());
        // La zone reste chargée : un villageois posé dans un tronçon à peine chargé n'y est pas encore visible.
        force(level, base, true);
        BlockPos door = smithy(level, base);
        h.runAfterDelay(10, () -> batonBody(h, w, level, server, data, base, door));
    }

    private static void force(ServerLevel level, BlockPos base, boolean on) {
        for (int dx = 0; dx <= 8; dx += 8) for (int dz = 0; dz <= 8; dz += 8) level.setChunkForced((base.getX() + dx) >> 4, (base.getZ() + dz) >> 4, on);
    }

    /**
     * En trois temps : un villageois posé n'est visible du monde qu'au tick suivant (parfois quelques-uns plus tard dans un
     * tronçon à peine chargé) ; on le cherche donc un peu après.
     */
    private static void batonBody(GameTestHelper h, TestWorld w, ServerLevel level, net.minecraft.server.MinecraftServer server, CrusadeSavedData data,
                                  BlockPos base, BlockPos door) {
        Site site = new Site(UUID.randomUUID(), SiteKind.VILLAGE, "Essaiville", w.realm.id(), level.dimension().location(), base.offset(3, 0, 3),
                Direction.SOUTH, 0x8C2A2A, true, RealmService.currentDay(server));
        Runnable cleanup = () -> {
            for (CityBuilding b : site.buildings()) {
                for (UUID u : b.occupants()) {
                    Entity e = level.getEntity(u);
                    if (e != null) e.discard();
                }
            }
            data.world().removeSite(site.id());
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(8, 9, 8))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            force(level, base, false);
            w.cleanup();
        };
        try {
            site.setBuilt(true);
            data.world().add(site);

            // Depuis la porte : l'intérieur 5 × 5, les meubles, une forge proposée.
            Optional<BuildingScan.Result> r = BuildingScan.fromClick(level, door, Direction.NORTH);
            if (r.isEmpty()) h.fail("le bâtiment n'est pas reconnu depuis sa porte");
            // 25 colonnes où l'on tient debout (sur l'enclume et le fourneau aussi), un seul étage.
            if (r.get().floor() != 25) h.fail("sol : " + r.get().floor());
            if (r.get().levels() != 1) h.fail("étages : " + r.get().levels());
            if (!r.get().min().equals(base) || r.get().max().getX() != base.getX() + 6 || r.get().max().getZ() != base.getZ() + 6) {
                h.fail("boîte : " + r.get().min() + " → " + r.get().max());
            }
            CityBuilding draft = CityService.draft(r.get());
            if (draft.suggestion() != BuildingFunction.FORGE) h.fail("proposé : " + draft.suggestion());
            // Rien n'entre dans la ville sans décision.
            if (!site.buildings().isEmpty()) h.fail("le brouillon est entré dans la ville");

            Outcome out = CityService.define(w.ruler, site.id(), draft, BuildingFunction.FORGE, "Forge d'essai");
            if (!out.success()) h.fail("la forge est refusée : " + out.message().getString());
            CityBuilding b = site.building(draft.id()).orElseThrow();
            if (b.occupants().size() != 1) h.fail("gens de la forge : " + b.occupants().size());
            Person smith = data.lineage().person(b.occupants().get(0)).orElseThrow();
            if (smith.work() == null || !smith.work().equals(b.spot())) h.fail("le forgeron ne travaille pas à sa forge");
            List<Entity> spawned = CityService.populateEntities(level, site);
            if (spawned.size() != 1) h.fail("le forgeron n'apparaît pas : " + spawned.size());
            if (!(spawned.get(0) instanceof Villager v)) {
                h.fail("le forgeron n'est pas un villageois : " + spawned.get(0));
                return;
            }
            if (!v.getUUID().equals(smith.id())) h.fail("le villageois n'est pas le forgeron du registre");
            if (v.getVillagerData().getProfession() != VillagerProfession.WEAPONSMITH) h.fail("métier : " + v.getVillagerData().getProfession());
            if (v.getOffers().isEmpty()) h.fail("le forgeron ne vend rien");

            // Une autre fonction : le forgeron s'en va (même pas encore visible du monde), le boulanger arrive avec son pain.
            CityService.define(w.ruler, site.id(), b, BuildingFunction.BOULANGERIE, "");
            if (!v.isRemoved()) h.fail("le forgeron est resté");
            if (data.lineage().person(smith.id()).isPresent()) h.fail("le forgeron est resté au registre");
            List<Entity> bakers = CityService.populateEntities(level, site);
            Entity baker = bakers.isEmpty() ? null : bakers.get(0);
            if (!(baker instanceof Villager bv) || bv.getOffers().stream().noneMatch(o -> o.getResult().is(Items.BREAD))) h.fail("le boulanger ne vend pas de pain : " + baker);

            // Retirée de la ville, puis retrouvée d'après sa porte.
            CityService.forget(w.ruler, site.id(), b.id());
            if (!site.buildings().isEmpty()) h.fail("le bâtiment n'est pas retiré");
            if (baker != null && !baker.isRemoved()) h.fail("le boulanger est resté");
            int found = CityService.recognize(level, site);
            if (found != 1 || site.buildings().get(0).suggestion() != BuildingFunction.FORGE) h.fail("reconnaissance : " + found);
            h.succeed();
            cleanup.run();
        } catch (RuntimeException | AssertionError ex) {
            cleanup.run();
            throw ex;
        }
    }
}
