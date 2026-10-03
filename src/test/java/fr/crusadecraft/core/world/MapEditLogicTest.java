package fr.crusadecraft.core.world;

import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.core.realm.TitleRank;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'éditeur de la carte (P12) : frontières peintes, royaumes faits main, et ce que la carte en montre. */
class MapEditLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.tryParse("minecraft:the_nether");

    @Test
    void casesDeSeizeBlocsMemeEnNegatif() {
        assertEquals(0, TerritoryMap.cellOf(0));
        assertEquals(0, TerritoryMap.cellOf(15.9));
        assertEquals(-1, TerritoryMap.cellOf(-0.5));
        assertEquals(-1, TerritoryMap.cellOf(-16));
        assertEquals(-2, TerritoryMap.cellOf(-16.5));
        long k = TerritoryMap.key(-3, 7);
        assertEquals(-3, TerritoryMap.cellX(k));
        assertEquals(7, TerritoryMap.cellZ(k));
        long k2 = TerritoryMap.key(5, -9);
        assertEquals(5, TerritoryMap.cellX(k2));
        assertEquals(-9, TerritoryMap.cellZ(k2));
    }

    @Test
    void peindreGommerOublier() {
        TerritoryMap map = new TerritoryMap();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long[] three = {TerritoryMap.key(0, 0), TerritoryMap.key(1, 0), TerritoryMap.key(-1, -1)};
        assertEquals(3, map.paint(OW, three, a));
        assertEquals(0, map.paint(OW, three, a), "repeindre de la même couleur ne change rien");
        assertEquals(a, map.at(OW, 20, 3).orElseThrow());
        assertEquals(a, map.at(OW, -5, -5).orElseThrow());
        assertTrue(map.at(OW, 40, 3).isEmpty(), "hors des cases peintes : rien");
        assertTrue(map.at(NETHER, 5, 5).isEmpty(), "chaque dimension a ses frontières");
        assertEquals(1, map.paint(OW, new long[]{TerritoryMap.key(1, 0)}, TerritoryMap.NEUTRAL));
        assertEquals(TerritoryMap.NEUTRAL, map.at(OW, 20, 3).orElseThrow(), "terre libre");
        map.paint(OW, new long[]{TerritoryMap.key(5, 5)}, b);
        assertEquals(1, map.paint(OW, new long[]{TerritoryMap.key(0, 0)}, null), "la gomme efface");
        assertTrue(map.at(OW, 1, 1).isEmpty());
        assertEquals(1, map.forget(a), "un royaume supprimé emporte ses cases");
        assertEquals(2, map.size(), "restent la terre libre et la case de b");
    }

    @Test
    void frontieresSauvegardees() {
        TerritoryMap map = new TerritoryMap();
        UUID a = UUID.randomUUID();
        map.paint(OW, new long[]{TerritoryMap.key(2, 3), TerritoryMap.key(-4, 9)}, a);
        map.paint(NETHER, new long[]{TerritoryMap.key(0, 0)}, TerritoryMap.NEUTRAL);
        TerritoryMap back = TerritoryMap.load(map.save());
        assertEquals(3, back.size());
        assertEquals(a, back.at(OW, -60, 150).orElseThrow());
        assertEquals(TerritoryMap.NEUTRAL, back.at(NETHER, 3, 3).orElseThrow());
    }

    private static AiRealm realm(boolean handMade, int provinceId, int x) {
        AiRealm r = new WorldPlanner(WorldPlanner.Settings.defaults(), provinceId * 31L + x, (px, pz) -> 64, OW)
                .handMade("Valcourt" + provinceId, TitleRank.COMTE, 0xB3261E, AiFaith.PAIEN, AiPersonality.MARCHAND, "Godefroy",
                        provinceId, new BlockPos(x, 64, 0), 0, 40, 3);
        r.setHandMade(handMade);
        return r;
    }

    @Test
    void royaumeFaitMainComplet() {
        AiRealm r = realm(true, 7, 500);
        assertEquals("Godefroy", r.ruler().name(), "le souverain nommé par le joueur");
        assertEquals(AiFaith.PAIEN, r.faith());
        assertEquals(AiPersonality.MARCHAND, r.personality());
        assertEquals(5, r.council().size(), "un conseil complet");
        assertEquals(7, r.capital().id());
        assertEquals(new BlockPos(500, 64, 0), r.capital().seat());
        assertTrue(r.capital().capital());
        AiRealm back = AiRealm.load(r.save());
        assertTrue(back.handMade(), "la marque survit à la sauvegarde");
        assertEquals("Valcourt7", back.place());
        assertFalse(AiRealm.load(realm(false, 1, 0).save()).handMade());
    }

    @Test
    void regenererGardeLesRoyaumesFaitsMain() {
        WorldState world = new WorldState();
        AiRealm mine = realm(true, 0, 0);
        AiRealm generated = realm(false, 1, 900);
        world.add(mine);
        world.add(generated);
        assertEquals(2, world.nextProvinceId());
        AiRealm fresh = realm(false, world.nextProvinceId(), 2000);
        world.replace(List.of(fresh), List.of(), 0, 0, 5);
        assertTrue(world.realm(mine.id()).isPresent(), "le royaume du joueur reste");
        assertTrue(world.realm(generated.id()).isEmpty(), "l'ancien royaume généré part");
        assertTrue(world.realm(fresh.id()).isPresent());
        assertEquals(List.of(mine), world.handMade());
        world.borders().paint(OW, new long[]{TerritoryMap.key(0, 0)}, mine.id());
        WorldState back = WorldState.load(world.save());
        assertEquals(mine.id(), back.borders().at(OW, 1, 1).orElseThrow(), "les frontières suivent le monde");
        assertTrue(back.realm(mine.id()).orElseThrow().handMade());
    }

    private static MapView view(MapView.Borders borders) {
        MapView.Territory a = new MapView.Territory(UUID.randomUUID(), true, false, "Valcourt", TitleRank.COMTE,
                0xB3261E, "Godefroy", false, AiPersonality.MARCHAND, 40, AiFaith.FIDELE, true);
        MapView.Territory b = new MapView.Territory(UUID.randomUUID(), true, false, "Ormesval", TitleRank.BARONNIE,
                0x1F4E9C, "Aude", true, AiPersonality.PACIFIQUE, 20, AiFaith.HERETIQUE, false);
        List<MapView.Seat> seats = List.of(new MapView.Seat(0, "Valcourt", 0, 0, true, 0, false, true, 3, 0),
                new MapView.Seat(1, "Ormesval", 1000, 0, true, 0, false, true, 4, 1));
        return new MapView(OW, 0, 0, 0, 0, 160, 110, List.of(a, b), seats, List.of(), true, true, borders);
    }

    @Test
    void lesCasesPeintesLEmportentSurLesCercles() {
        // Une case au cœur du cercle de Valcourt, peinte pour Ormesval ; une autre en terre libre.
        MapView.Borders borders = new MapView.Borders(new long[]{TerritoryMap.key(2, 0), TerritoryMap.key(-3, 0)},
                new int[]{1, MapView.Borders.NEUTRAL});
        MapView v = view(borders);
        // Un point par bloc, centré sur l'origine : le pixel (x, y) est le bloc (x - 100, y - 50).
        MapRaster.Result r = MapRaster.render(v, 200, 100, 0, 0, 1, false);
        assertEquals(0, r.ownerAt(100 + 10, 50), "sans peinture, le cercle de Valcourt");
        assertEquals(1, r.ownerAt(100 + 40, 50), "la case peinte revient à Ormesval, malgré la distance");
        assertEquals(MapRaster.NONE, r.ownerAt(100 - 40, 50), "terre libre au milieu du cercle");
        // Une retouche pas encore confirmée par le serveur : la gomme rend le cercle.
        Long2IntOpenHashMap overlay = new Long2IntOpenHashMap();
        overlay.put(TerritoryMap.key(2, 0), MapRaster.ERASED);
        assertEquals(0, MapRaster.render(v, 200, 100, 0, 0, 1, false, overlay).ownerAt(100 + 40, 50));
        // Dans l'éditeur, aucune terre n'est dans le brouillard.
        MapRaster.Result far = MapRaster.render(v, 200, 100, 5000, 5000, 1, false);
        assertEquals(MapRaster.NONE, far.ownerAt(100, 50), "loin de tout, mais sans brouillard dans l'éditeur");
    }

    @Test
    void laCarteTransporteFrontieresEtEdition() {
        MapView v = view(new MapView.Borders(new long[]{TerritoryMap.key(-7, 2)}, new int[]{1}));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        v.write(buf);
        MapView back = MapView.read(buf);
        assertTrue(back.canEdit());
        assertTrue(back.editing());
        assertEquals(1, back.borders().size());
        assertEquals(TerritoryMap.key(-7, 2), back.borders().keys()[0]);
        assertEquals(1, back.borders().owners()[0]);
        assertEquals(AiFaith.HERETIQUE, back.territories().get(1).faith());
        assertTrue(back.territories().get(0).handMade());
        assertEquals(4, back.seats().get(1).provinceId());
        assertEquals(1, back.seats().get(1).origin());
    }

    @Test
    void lesAnciennesSauvegardesSansFrontieres() {
        WorldState back = WorldState.load(new CompoundTag());
        assertEquals(0, back.borders().size(), "un monde d'avant l'éditeur n'a aucune frontière peinte");
    }
}
