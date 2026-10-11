package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.terrain.TerrainService;
import fr.crusadecraft.core.world.terrain.TerrainTile;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La carte en terrain (P18b) : estimer sans rien générer, relever les vraies couleurs, livrer les tuiles ; et l'état de la guerre. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerrainGameTests {

    private TerrainGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18b_estimation", timeoutTicks = 200)
    public static void lEstimationNeGenereAucunChunk(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // Une tuile très loin de tout : 16 384 blocs de côté, à 200 000 blocs.
        int tx = 200_000 / TerrainTile.span(2), tz = -200_000 / TerrainTile.span(2);
        int cx = (tx * TerrainTile.span(2)) >> 4, cz = (tz * TerrainTile.span(2)) >> 4;
        if (level.getChunkSource().getChunkNow(cx, cz) != null) h.fail("le chunk témoin est déjà chargé");
        int loaded = level.getChunkSource().getLoadedChunksCount();
        long t0 = System.nanoTime();
        TerrainTile tile = TerrainService.compute(level, 2, tx, tz);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        CrusadeCraft.LOGGER.info("Carte en terrain : une tuile de {} points en {} ms", TerrainTile.SIZE * TerrainTile.SIZE, ms);
        for (int c : tile.colors()) if (c == 0) h.fail("un point de la tuile est resté inconnu");
        if (level.getChunkSource().getChunkNow(cx, cz) != null) h.fail("l'estimation a chargé un chunk");
        if (level.getChunkSource().getLoadedChunksCount() > loaded) h.fail("l'estimation a chargé des chunks");
        h.succeed();
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18b_parcours", timeoutTicks = 200)
    public static void leParcoursDonneLesVraiesCouleurs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        // Un chunk à l'écart des autres essais, chargé pour l'occasion, sa surface couverte de laine rouge là où on la lit.
        int cx = (origin.getX() >> 4) + 40, cz = (origin.getZ() >> 4) + 40;
        level.getChunk(cx, cz);
        List<BlockPos> wool = new ArrayList<>();
        List<BlockState> before = new ArrayList<>();
        try {
            for (int dx = 4; dx < 16; dx += 8) {
                for (int dz = 4; dz < 16; dz += 8) {
                    int x = (cx << 4) + dx, z = (cz << 4) + dz;
                    BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
                    wool.add(p);
                    before.add(level.getBlockState(p));
                    level.setBlockAndUpdate(p, Blocks.RED_WOOL.defaultBlockState());
                }
            }
            if (!TerrainService.exploreNow(level, cx, cz)) h.fail("le chunk chargé n'est pas relevé");
            if (TerrainService.exploreNow(level, cx + 5000, cz)) h.fail("un chunk absent est relevé (il aurait fallu le charger)");
            int lod = 0, sx = TerrainTile.sampleOf((cx << 4) + 8, lod), sz = TerrainTile.sampleOf((cz << 4) + 8, lod);
            int tx = Math.floorDiv(sx, TerrainTile.SIZE), tz = Math.floorDiv(sz, TerrainTile.SIZE);
            TerrainTile estimate = TerrainService.compute(level, lod, tx, tz);
            TerrainTile shown = TerrainService.compose(level, estimate);
            int i = estimate.index(Math.floorMod(sx, TerrainTile.SIZE), Math.floorMod(sz, TerrainTile.SIZE));
            int red = net.minecraft.world.level.material.MapColor.COLOR_RED.col;
            if ((shown.colors()[i] & 0xFFFFFF) != red) {
                h.fail("la vraie couleur n'est pas montrée : " + Integer.toHexString(shown.colors()[i]) + " au lieu de " + Integer.toHexString(red));
            }
            if (estimate.colors()[i] == shown.colors()[i]) h.fail("l'estimation n'a pas cédé la place");
            h.succeed();
        } finally {
            for (int k = 0; k < wool.size(); k++) level.setBlockAndUpdate(wool.get(k), before.get(k));
        }
    }

    // Le serveur de tests enchaîne les ticks sans attendre : la marge doit couvrir quelques secondes de calcul réel.
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18b_livraison", timeoutTicks = 12000)
    public static void lesTuilesSePreparentAPart(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var player = TestWorld.fakePlayer(h, "cartographe");
        player.setPos(h.absolutePos(BlockPos.ZERO).getX(), 0, h.absolutePos(BlockPos.ZERO).getZ());
        int tx = 1234, tz = -987;
        long key = TerrainTile.key(1, tx, tz);
        long started = level.getGameTime();
        TerrainService.request(player, 1, new long[]{key, TerrainTile.key(2, 0, 0)});
        // La demande d'un mauvais niveau est ignorée, et rien ne s'est calculé sur le fil du serveur.
        if (TerrainService.ready(level, key)) h.fail("la tuile était prête avant d'avoir été calculée");
        h.succeedWhen(() -> {
            if (!TerrainService.ready(level, key)) throw new net.minecraft.gametest.framework.GameTestAssertException("tuile pas encore prête");
            var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("crusadecraft_terrain");
            if (!Files.isDirectory(file)) h.fail("le cache sur disque n'a pas été écrit");
            CrusadeCraft.LOGGER.info("Carte en terrain : tuile livrée en {} ticks", level.getGameTime() - started);
        });
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18b_front", timeoutTicks = 200)
    public static void laGuerreSeDessineToute(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        UUID warId = UUID.randomUUID();
        AiRealm enemy = null;
        try {
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            enemy = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 900, w.center.getZ());
            War war = new War(warId, w.realm.id(), enemy.id(), CasusBelli.HUMILIATE, -1, RealmService.currentDay(server));
            int province = enemy.capital().id();
            war.occupied().add(province);
            data.wars().add(war);
            MapView.War view = WarMapService.view(w.ruler, h.getLevel().dimension().location());
            w.check(view.friends().contains(w.realm.id()) && view.foes().contains(enemy.id()), "les deux camps ne sont pas dits");
            w.check(view.occupied().stream().anyMatch(o -> o.province() == province && o.color() == (w.realm.color() & 0xFFFFFF)),
                    "la province occupée n'apparaît pas à la couleur de l'occupant");
            // Elle voyage jusqu'au client.
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            fr.crusadecraft.core.world.WorldService.mapView(w.ruler, false).write(buf);
            MapView back = MapView.read(buf);
            w.check(back.war().occupied().size() == 1 && back.war().foes().contains(enemy.id()), "l'état de la guerre s'abîme en route");
            h.succeed();
        } finally {
            data.wars().remove(warId);
            if (enemy != null) WorldEditService.removeRealm(server, enemy.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }

    /**
     * La carte en terrain dessinée hors de tout écran, comme la carte du jeu la fondra : un PNG à regarder
     * ({@code run-gametest/apercu_terrain.png}), et quelques garde-fous (rien d'inconnu, du relief, des teintes variées).
     */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18b_apercu", timeoutTicks = 400)
    public static void laCarteEnTerrainSeDessine(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var dim = level.dimension().location();
        int size = 256, bpp = 12, lod = fr.crusadecraft.client.map.ClientTerrain.lodFor(bpp);
        fr.crusadecraft.client.map.ClientTerrain.clear(dim);
        int min = -size / 2 * bpp, max = size / 2 * bpp;
        for (int l = lod; l < TerrainTile.LODS; l++) {
            for (int tx = TerrainTile.tileOf(min, l) - 1; tx <= TerrainTile.tileOf(max, l) + 1; tx++) {
                for (int tz = TerrainTile.tileOf(min, l) - 1; tz <= TerrainTile.tileOf(max, l) + 1; tz++) {
                    fr.crusadecraft.client.map.ClientTerrain.put(dim, TerrainService.compute(level, l, tx, tz));
                }
            }
        }
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Set<Integer> tints = new java.util.HashSet<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int c = fr.crusadecraft.client.map.ClientTerrain.color(dim, min + x * bpp, min + y * bpp, lod);
                if (c == 0) h.fail("un pixel de la carte reste inconnu en " + (min + x * bpp) + ", " + (min + y * bpp));
                img.setRGB(x, y, c & 0xFFFFFF);
                tints.add(c & 0xF0F0F0);
            }
        }
        try {
            javax.imageio.ImageIO.write(img, "png", level.getServer().getServerDirectory().toPath().resolve("apercu_terrain.png").toFile());
        } catch (java.io.IOException e) {
            h.fail("aperçu non écrit : " + e);
        }
        fr.crusadecraft.client.map.ClientTerrain.clear(null);
        if (tints.size() < 12) h.fail("la carte est presque d'une seule teinte (" + tints.size() + ")");
        h.succeed();
    }
}
