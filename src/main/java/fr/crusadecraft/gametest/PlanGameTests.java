package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Blueprints;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.build.plan.BuildJournal;
import fr.crusadecraft.core.build.plan.Journals;
import fr.crusadecraft.core.build.plan.NationStyle;
import fr.crusadecraft.core.build.plan.Plan;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanPlacer;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.build.plan.RawPlan;
import fr.crusadecraft.core.build.plan.SchematicReader;
import fr.crusadecraft.core.build.plan.TownPlanner;
import fr.crusadecraft.core.build.plan.TownSize;
import fr.crusadecraft.core.build.plan.Varints;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.PlanItem;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les plans (P34) : les fichiers du joueur (.nbt, .schem) se lisent et se posent ; une ville de l'ordinateur se bâtit
 * avec les bâtiments du pack de son style, puis se défait jusqu'au dernier bloc ; le plan d'architecte pose et
 * défait. Chaque essai bâtit loin des autres, dans son propre lot.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanGameTests {

    private PlanGameTests() {}

    private static BlockPos ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static void reset(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
    }

    /** Un petit abri en .nbt : un sol de pierre 3×3, un poteau de chêne au milieu, de l'air autour. */
    private static CompoundTag structureFile() {
        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SchematicReader.dataVersion());
        ListTag size = new ListTag();
        for (int v : new int[]{3, 3, 3}) size.add(IntTag.valueOf(v));
        root.put("size", size);
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.OAK_LOG.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        root.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int y = 0; y < 3; y++) {
            for (int z = 0; z < 3; z++) {
                for (int x = 0; x < 3; x++) {
                    CompoundTag b = new CompoundTag();
                    ListTag pos = new ListTag();
                    pos.add(IntTag.valueOf(x));
                    pos.add(IntTag.valueOf(y));
                    pos.add(IntTag.valueOf(z));
                    b.put("pos", pos);
                    b.putInt("state", y == 0 ? 0 : (x == 1 && z == 1) ? 1 : 2);
                    blocks.add(b);
                }
            }
        }
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        return root;
    }

    /** Une schématique Sponge 2 : un socle de pierre 2×2 et un coffre à trois diamants dessus. */
    private static CompoundTag spongeFile() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", SchematicReader.dataVersion());
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 2);
        root.putShort("Length", (short) 2);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", 0);
        palette.putInt("minecraft:air", 1);
        palette.putInt("minecraft:chest[facing=north,type=single,waterlogged=false]", 2);
        palette.putInt("unmod:bloc_absent", 3);
        root.put("Palette", palette);
        root.putInt("PaletteMax", 4);
        // index = x + z*W + y*W*L : en bas toute la pierre, en haut le coffre en (0,0), un bloc absent en (1,1).
        root.putByteArray("BlockData", Varints.encode(new int[]{0, 0, 0, 0, 2, 1, 1, 3}));
        ListTag entities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putIntArray("Pos", new int[]{0, 1, 0});
        chest.putString("Id", "minecraft:chest");
        ListTag items = new ListTag();
        CompoundTag diamond = new CompoundTag();
        diamond.putByte("Slot", (byte) 0);
        diamond.putString("id", "minecraft:diamond");
        diamond.putByte("Count", (byte) 3);
        items.add(diamond);
        chest.put("Items", items);
        entities.add(chest);
        root.put("BlockEntities", entities);
        return root;
    }

    private static void write(Path file, CompoundTag tag) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(tag, out);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p34_fichiers", timeoutTicks = 200)
    public static void fichiersNbtEtSchem(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Path dir = PlanLibrary.roots().get("plans").resolve("essai");
        Path nbt = dir.resolve("abri.nbt"), schem = dir.resolve("coffre.schem");
        try {
            write(nbt, structureFile());
            write(schem, spongeFile());
            ServerLevel level = h.getLevel();
            var server = level.getServer();

            // Le .nbt se lit, entre au catalogue, et se pose ; le terrain revient ensuite.
            PlanRef ref = new PlanRef(PlanRef.Source.FILE, "plans", "essai/abri.nbt");
            w.check(PlanLibrary.fileEntries().stream().anyMatch(e -> e.ref().equals(ref.encode())), "le .nbt n'est pas au catalogue des fichiers");
            Optional<Plan> abri = PlanLibrary.load(ref, true);
            w.check(abri.isPresent(), "le .nbt ne se lit pas");
            w.check(abri.get().sizeX() == 3 && abri.get().sizeY() == 3 && abri.get().sizeZ() == 3, "mauvaise taille du .nbt");
            w.check(PlanLibrary.load(new PlanRef(PlanRef.Source.FILE, "plans", "../../options.txt"), true).isEmpty(), "un chemin qui sort du dossier est lu");
            BlockPos at = ground(level, w.center.getX() + 2500, w.center.getZ());
            BlockState before = level.getBlockState(at.above());
            UUID id = UUID.randomUUID();
            BuildJournal journal = Journals.journal(server, id, level.dimension().location());
            int[] r = PlanPlacer.advance(level, at, Rotation.NONE, abri.get().placements(), 0, 100_000, journal);
            w.check(r[0] == abri.get().placements().size(), "l'abri n'est pas posé en entier");
            w.check(level.getBlockState(at).is(Blocks.STONE), "le sol de l'abri n'est pas au niveau du bloc visé");
            w.check(level.getBlockState(at.above()).is(Blocks.OAK_LOG), "le poteau de l'abri manque");
            w.check(journal.size() > 0, "rien n'est noté au journal");
            Journals.undo(server, id);
            for (int i = 0; i < 50 && Journals.undoing(server, id); i++) Journals.tick(server);
            w.check(!Journals.undoing(server, id), "l'abri ne se défait pas");
            w.check(level.getBlockState(at.above()).equals(before), "le terrain n'est pas revenu sous l'abri");

            // Le .schem (Sponge 2) : socle, coffre plein, et un bloc d'un mod absent laissé de côté.
            RawPlan raw = SchematicReader.read(schem);
            w.check(raw.sizeX() == 2 && raw.sizeY() == 2 && raw.sizeZ() == 2, "mauvaise taille du .schem");
            w.check(raw.cells().size() == 7, "le bloc d'un mod absent n'est pas laissé de côté (" + raw.cells().size() + ")");
            RawPlan.Cell chest = raw.cells().stream().filter(c -> c.state().getBlock() instanceof ChestBlock).findFirst().orElse(null);
            w.check(chest != null && chest.tag() != null && chest.tag().getList("Items", 10).size() == 1, "le coffre perd son contenu");
            Optional<Plan> coffre = PlanLibrary.load(new PlanRef(PlanRef.Source.FILE, "plans", "essai/coffre.schem"), true);
            w.check(coffre.isPresent(), "le .schem ne se charge pas");
            BlockPos at2 = ground(level, w.center.getX() + 2520, w.center.getZ());
            UUID id2 = UUID.randomUUID();
            PlanPlacer.advance(level, at2, Rotation.NONE, coffre.get().placements(), 0, 100_000, Journals.journal(server, id2, level.dimension().location()));
            boolean found = false;
            for (BlockPos p : BlockPos.betweenClosed(at2.offset(-2, 0, -2), at2.offset(2, 2, 2))) {
                if (level.getBlockEntity(p) instanceof ChestBlockEntity c && c.getItem(0).getCount() == 3) found = true;
            }
            w.check(found, "le coffre posé n'a pas ses trois diamants");
            Journals.undo(server, id2);
            for (int i = 0; i < 50 && Journals.undoing(server, id2); i++) Journals.tick(server);
            h.succeed();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(nbt);
                Files.deleteIfExists(schem);
                Files.deleteIfExists(dir);
            } catch (java.io.IOException ignored) {
                // le dossier d'essai reste
            }
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p34_ville", timeoutTicks = 600)
    public static void villeDuStyleEtDefaite(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        ServerLevel level = h.getLevel();
        try {
            reset(w);
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            for (NationStyle s : NationStyle.values()) {
                CrusadeCraft.LOGGER.info("CrusadeCraft (essai) : style {} → pack {}", s, PlanLibrary.packFor(s).orElse("aucun"));
            }
            w.check(PlanLibrary.packFor(NationStyle.EUROPEENNE).isPresent(), "aucun pack médiéval de MineColonies n'est chargé");
            int x = w.center.getX() + 3500, z = w.center.getZ();
            AiRealm ai = WorldEditService.addRealm(server, level, "Cendrecourt", "", TitleRank.COMTE, 0x7B2D8B, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x + 2000, z);
            ai.setStyle(NationStyle.EUROPEENNE);
            BlockPos seat = ground(level, x, z);
            AiProvince city = WorldEditService.addCity(server, level, ai.id(), "Khalyos", WorldEditService.CitySize.HAMEAU, seat.getX(), seat.getZ())
                    .orElse(null);
            w.check(city != null, "la ville n'est pas créée");

            // Le plan de la ville : un hôtel de ville et des maisons du pack, sans aucune cabane MineColonies.
            Optional<fr.crusadecraft.core.build.plan.TownPlan> town = TownPlanner.plan(NationStyle.EUROPEENNE, TownSize.HAMEAU, 42);
            w.check(town.isPresent() && town.get().pieces().size() >= 3, "le hameau n'a pas au moins trois bâtiments");
            w.check(town.get().pieces().get(0).ref().contains("townhall"), "le centre n'est pas un hôtel de ville : " + town.get().pieces().get(0).ref());
            for (Blueprints.Placement p : TownPlanner.placements(town.get())) {
                String key = BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).toString();
                w.check(!key.startsWith("minecolonies:blockhut") && !key.startsWith("structurize:"), "un bloc de MineColonies reste dans la ville : " + key);
            }

            // L'éditeur la bâtit d'un coup.
            var out = SiteService.buildTown(w.ruler, city.id());
            w.check(out.success(), "ville refusée : " + out.message().getString());
            Site site = SiteService.townOf(server, city.id()).orElseThrow();
            // Des sondes sur toute l'emprise, du sous-sol au toit : ce qui change, puis ce qui revient.
            List<BlockPos> probes = new ArrayList<>();
            List<BlockState> before = new ArrayList<>();
            int half = Math.min(60, site.town().extent() / 2);
            for (int dx = -half; dx <= half; dx += 3) {
                for (int dz = -half; dz <= half; dz += 3) {
                    BlockPos g = ground(level, seat.getX() + dx, seat.getZ() + dz);
                    for (int dy = -1; dy <= 7; dy += 2) {
                        probes.add(g.above(dy));
                        before.add(level.getBlockState(g.above(dy)));
                    }
                }
            }
            for (int i = 0; i < 400 && !site.built(); i++) SiteService.advanceForTests(level, site, 20_000);
            w.check(site.built(), "la ville n'est pas achevée");
            int changed = 0;
            for (int i = 0; i < probes.size(); i++) if (!level.getBlockState(probes.get(i)).equals(before.get(i))) changed++;
            w.check(changed >= 30, "la ville n'a presque rien changé sur son emprise (" + changed + " sondes sur " + probes.size() + ")");
            w.check(Journals.journal(server, site.id(), level.dimension().location()).size() > 1000, "le journal de la ville est presque vide");
            w.check(site.community() != null && !data.lineage().residents(site.community()).isEmpty(), "la ville n'a pas d'habitants au registre");
            w.check(SiteService.buildTown(w.ruler, city.id()).success() == false, "une ville se bâtit deux fois");

            // Puis elle se défait : chaque bloc revient.
            w.check(SiteService.undoTown(w.ruler, site.id()).success(), "la ville ne se défait pas");
            for (int i = 0; i < 2000 && Journals.undoing(server, site.id()); i++) Journals.tick(server);
            w.check(!Journals.undoing(server, site.id()), "la ville n'a pas fini de se défaire");
            for (int i = 0; i < probes.size(); i++) {
                w.check(level.getBlockState(probes.get(i)).equals(before.get(i)), "le terrain n'est pas revenu en " + probes.get(i).toShortString());
            }
            w.check(SiteService.townOf(server, city.id()).isEmpty(), "la ville défaite reste au registre");
            w.check(ai.provinces().stream().anyMatch(p -> p.id() == city.id()), "la ville a disparu de la carte");
            h.succeed();
        } finally {
            for (var p : new ArrayList<>(data.lineage().persons())) {
                var e = level.getEntity(p.id());
                if (e != null) e.discard();
            }
            data.lineage().clear();
            data.world().clear();
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p34_outil", timeoutTicks = 400)
    public static void planArchitecte(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        try {
            ItemStack tool = new ItemStack(ModItems.PLAN.get());
            w.ruler.setItemInHand(InteractionHand.MAIN_HAND, tool);
            w.check(!PlanTool.choose(w.ruler, "x").success(), "un joueur sans droit choisit un plan");
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            String pack = PlanLibrary.packFor(NationStyle.EUROPEENNE).orElse(null);
            w.check(pack != null, "aucun pack médiéval");
            String well = PlanLibrary.entries(pack).stream().map(PlanLibrary.Entry::ref).filter(r -> r.contains("smallwell") || r.contains("small_well"))
                    .findFirst().orElse(null);
            w.check(well != null, "pas de petit puits dans le pack " + pack);
            var chosen = PlanTool.choose(w.ruler, well);
            w.check(chosen.success(), "choix refusé : " + chosen.message().getString());
            PlanItem.Choice c = PlanItem.choice(w.ruler.getMainHandItem()).orElseThrow();
            w.check(c.sizeX() > 0 && c.sizeZ() > 0, "l'outil ne connaît pas l'emprise du plan");
            PlanTool.rotate(w.ruler, w.ruler.getMainHandItem());
            w.check(PlanItem.choice(w.ruler.getMainHandItem()).orElseThrow().rotation() == 1, "l'outil ne tourne pas");

            BlockPos at = ground(level, w.center.getX() + 3000, w.center.getZ() - 3000);
            List<BlockState> before = new ArrayList<>();
            List<BlockPos> probes = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 3, 2))) {
                probes.add(p.immutable());
                before.add(level.getBlockState(p));
            }
            PlanTool.paste(w.ruler, w.ruler.getMainHandItem(), at);
            for (int i = 0; i < 100; i++) Journals.tick(server);
            int changed = 0;
            for (int i = 0; i < probes.size(); i++) if (!level.getBlockState(probes.get(i)).equals(before.get(i))) changed++;
            w.check(changed > 5, "le puits n'est pas posé (" + changed + " blocs changés)");
            w.check(PlanTool.undoLast(w.ruler).success(), "la pose ne se défait pas");
            for (int i = 0; i < 200; i++) Journals.tick(server);
            for (int i = 0; i < probes.size(); i++) {
                w.check(level.getBlockState(probes.get(i)).equals(before.get(i)), "le terrain n'est pas revenu en " + probes.get(i).toShortString());
            }
            w.check(!PlanTool.undoLast(w.ruler).success(), "une pose se défait deux fois");

            // Une ville entière posée à l'outil sur une terre libre : une nation nouvelle, sur la carte, qui se défait.
            var data = CrusadeSavedData.get(server);
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            String townRef = PlanTool.townRef(NationStyle.EUROPEENNE, TownSize.HAMEAU);
            w.check(PlanTool.towns().stream().anyMatch(e -> e.ref().equals(townRef)), "pas de hameau européen au catalogue");
            w.check(PlanTool.choose(w.ruler, townRef).success(), "le hameau n'est pas choisi");
            BlockPos townAt = ground(level, w.center.getX() + 3600, w.center.getZ() - 3600);
            PlanTool.paste(w.ruler, w.ruler.getMainHandItem(), townAt);
            Site townSite = data.world().sites().stream().filter(s -> s.town() != null).findFirst().orElse(null);
            w.check(townSite != null, "la ville posée n'est pas une ville du monde");
            AiRealm nation = data.world().realm(townSite.owner()).orElse(null);
            w.check(nation != null && nation.style() == NationStyle.EUROPEENNE, "la ville posée sur une terre libre ne fonde pas de nation de son style");
            w.check(nation.provinces().stream().anyMatch(p -> p.id() == townSite.province()), "la ville posée n'est pas sur la carte");
            for (int i = 0; i < 400; i++) Journals.tick(server);
            int journal = Journals.journal(server, townSite.id(), level.dimension().location()).size();
            w.check(journal > 500, "la ville posée n'a presque rien bâti (" + journal + ")");
            w.check(SiteService.undoTown(w.ruler, townSite.id()).success(), "la ville posée ne se défait pas");
            for (int i = 0; i < 2000 && Journals.undoing(server, townSite.id()); i++) Journals.tick(server);
            w.check(!Journals.undoing(server, townSite.id()), "la ville posée ne finit pas de se défaire");
            for (var person : new ArrayList<>(data.lineage().persons())) {
                var e = level.getEntity(person.id());
                if (e != null) e.discard();
            }
            data.lineage().clear();
            data.world().clear();
            h.succeed();
        } finally {
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }
}
