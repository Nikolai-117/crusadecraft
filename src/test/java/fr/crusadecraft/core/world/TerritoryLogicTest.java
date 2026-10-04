package fr.crusadecraft.core.world;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le bornage et les constructions (P14) : les cases d'un tracé, les règles de survie, la propriété des terres. */
class TerritoryLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    private static LongOpenHashSet set(long[] keys) {
        return new LongOpenHashSet(keys);
    }

    @Test
    void rectangleDeCases() {
        long[] cells = TerritoryRules.rectangle(0, 0, 47, 31);
        assertEquals(3 * 2, cells.length, "48 × 32 blocs : trois cases sur deux");
        assertTrue(set(cells).contains(TerritoryMap.key(2, 1)));
        assertEquals(1, TerritoryRules.rectangle(5, 5, 5, 5).length, "un seul bloc : une case");
        assertEquals(4, TerritoryRules.rectangle(-1, -1, 1, 1).length, "à cheval sur l'origine : quatre cases");
        assertNull(TerritoryRules.rectangle(0, 0, 100_000, 100_000), "démesuré : refusé");
    }

    @Test
    void polygoneInterieurEtContour() {
        // Un triangle rectangle de 160 blocs de côté.
        long[] cells = TerritoryRules.polygon(List.of(new int[]{0, 0}, new int[]{160, 0}, new int[]{0, 160}));
        LongOpenHashSet s = set(cells);
        assertTrue(s.contains(TerritoryMap.key(1, 1)), "le cœur du triangle");
        assertTrue(s.contains(TerritoryMap.key(9, 0)), "un coin, sur le contour");
        assertFalse(s.contains(TerritoryMap.key(8, 8)), "au-delà de l'hypoténuse");
        // Un tracé très fin, le long d'une rivière : il prend quand même ses cases.
        long[] thin = TerritoryRules.polygon(List.of(new int[]{0, 0}, new int[]{200, 2}, new int[]{0, 4}));
        assertTrue(thin.length >= 12, "un tracé fin garde ses cases : " + thin.length);
        assertEquals(2, TerritoryRules.polygon(List.of(new int[]{0, 0}, new int[]{20, 0})).length, "deux coins : un rectangle");
    }

    private static Optional<UUID> owner(long key, long[] mine, UUID me, long[] theirs, UUID them) {
        for (long k : mine) if (k == key) return Optional.of(me);
        for (long k : theirs) if (k == key) return Optional.of(them);
        return Optional.empty();
    }

    @Test
    void reglesDeSurvie() {
        UUID me = UUID.randomUUID(), them = UUID.randomUUID();
        long[] mine = {TerritoryMap.key(0, 0)};
        long[] theirs = {TerritoryMap.key(3, 0)};
        List<double[]> anchors = List.<double[]>of(new double[]{8, 8});
        // Trois cases à l'est de la mienne : la troisième est à un autre.
        long[] stroke = {TerritoryMap.key(1, 0), TerritoryMap.key(2, 0), TerritoryMap.key(3, 0), TerritoryMap.key(0, 0)};
        TerritoryRules.Verdict v = TerritoryRules.survival(me, stroke, k -> owner(k, mine, me, theirs, them), anchors, 256, 5, 100);
        assertTrue(v.ok(), "refusé : " + v.refusal());
        assertEquals(2, v.cells().length, "deux nouvelles cases");
        assertEquals(1, v.taken(), "la case d'un autre est écartée");
        assertEquals(1, v.owned(), "la case déjà à moi ne compte pas");
        assertEquals(10, v.cost(), "cinq or par case");
        // Trop loin de mes colonies.
        long far = TerritoryMap.key(40, 0);
        assertEquals("crusadecraft.bornage.far", TerritoryRules.survival(me, new long[]{far}, k -> owner(k, mine, me, theirs, them), anchors, 256, 5, 100).refusal());
        // Détaché : à portée, mais sans toucher mes terres.
        long detached = TerritoryMap.key(0, 5);
        assertEquals("crusadecraft.bornage.detached",
                TerritoryRules.survival(me, new long[]{detached}, k -> owner(k, mine, me, theirs, them), anchors, 256, 5, 100).refusal());
        // Que des terres d'autrui.
        assertEquals("crusadecraft.bornage.taken", TerritoryRules.survival(me, theirs, k -> owner(k, mine, me, theirs, them), anchors, 256, 5, 100).refusal());
        // Trop de cases d'un coup.
        assertEquals("crusadecraft.bornage.too_many", TerritoryRules.survival(me, new long[]{TerritoryMap.key(1, 0), TerritoryMap.key(1, 1)},
                k -> owner(k, mine, me, theirs, them), anchors, 256, 5, 1).refusal());
    }

    @Test
    void lesCasesPeintesPuisLeCercleLePlusProche() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        TerritoryMap borders = new TerritoryMap();
        Territory.Snapshot snap = new Territory.Snapshot(borders, OW, List.of(
                new Territory.Circle(0, 0, 100 * 100, a, Territory.Kind.PROVINCE),
                new Territory.Circle(150, 0, 100 * 100, b, Territory.Kind.SITE)));
        assertEquals(a, snap.ownerAt(40, 0).orElseThrow(), "dans le seul cercle de a");
        assertEquals(b, snap.ownerAt(90, 0).orElseThrow(), "dans les deux cercles : le plus proche, b");
        assertTrue(snap.ownerAt(400, 0).isEmpty(), "hors de tout cercle : à personne");
        borders.paint(OW, new long[]{TerritoryMap.key(2, 0)}, b);
        assertEquals(b, snap.ownerAt(40, 0).orElseThrow(), "une case peinte l'emporte");
        borders.paint(OW, new long[]{TerritoryMap.key(0, 0)}, TerritoryMap.NEUTRAL);
        assertTrue(snap.ownerAt(5, 5).isEmpty(), "terre libre au cœur d'un cercle");
        assertEquals(a, snap.circleOwner(5, 5).orElseThrow(), "le cercle, lui, n'a pas bougé");
    }

    @Test
    void uneConstructionSauvegardee() {
        UUID owner = UUID.randomUUID();
        Site s = new Site(UUID.randomUUID(), SiteKind.FORT, "Valcourt", owner, OW, new BlockPos(100, 64, -40), Direction.WEST, 0xB3261E, false, 12);
        assertEquals(0, s.radius(), "un chantier ne tient encore rien");
        s.setProgress(120);
        s.setProvince(4);
        s.setCommunity(UUID.randomUUID());
        Site back = Site.load(s.save());
        assertEquals(SiteKind.FORT, back.kind());
        assertEquals(Direction.WEST, back.facing());
        assertEquals(120, back.progress());
        assertEquals(4, back.province());
        assertEquals(s.community(), back.community());
        back.setBuilt(true);
        assertEquals(SiteKind.FORT.radius(), back.radius(), "bâtie, elle étend ses terres");
        WorldState world = new WorldState();
        world.add(back);
        assertEquals(1, WorldState.load(world.save()).sites().size(), "les constructions suivent le monde");
        assertTrue(WorldState.load(new net.minecraft.nbt.CompoundTag()).sites().isEmpty(), "un monde de la 1.2 n'en a aucune");
    }

    @Test
    void lesConstructionsVoyagentSurLaCarte() {
        MapView.Territory t = new MapView.Territory(UUID.randomUUID(), true, false, "Valcourt", fr.crusadecraft.core.realm.TitleRank.COMTE,
                0xB3261E, "Godefroy", false, AiPersonality.MARCHAND, 40);
        UUID siteId = UUID.randomUUID();
        List<MapView.Seat> seats = List.of(new MapView.Seat(0, "Valcourt", 10, 20, false, 0, false, false, -1, -1,
                        new MapView.SiteInfo(siteId, SiteKind.TOUR.ordinal(), 64, 45, 3)),
                new MapView.Seat(0, "Valbrume", 0, 0, false, 0, false, false, -1, -1,
                        new MapView.SiteInfo(null, MapView.SiteInfo.GUARD_POST, 80, 100, 2)));
        MapView v = new MapView(OW, 0, 0, 0, 0, 160, 110, List.of(t), seats, List.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        v.write(buf);
        MapView back = MapView.read(buf);
        assertEquals(siteId, back.seats().get(0).site().id());
        assertEquals(45, back.seats().get(0).site().percent());
        assertNull(back.seats().get(1).site().id(), "un poste de garde MineColonies n'a pas d'identifiant");
        assertEquals(MapView.SiteInfo.GUARD_POST, back.seats().get(1).site().kind());
        // Sur la carte, un chantier ne colore aucune terre ; un poste de garde, si.
        fr.crusadecraft.client.map.MapRaster.Result r = fr.crusadecraft.client.map.MapRaster.render(back, 100, 100, 0, 0, 1, false);
        assertEquals(1, r.ownerAt(50, 50), "au pied du poste de garde : son territoire");
    }

    @Test
    void naturesDesConstructions() {
        assertTrue(SiteKind.FORT.capturable() && !SiteKind.BORNE.capturable() && !SiteKind.VILLAGE.capturable());
        assertTrue(SiteKind.VILLAGE.settlement() && SiteKind.DONJON.settlement());
        assertFalse(SiteKind.DONJON.survival() || SiteKind.VILLAGE.survival(), "donjons et villages se posent depuis l'éditeur");
        assertTrue(SiteKind.FORT.radius() > SiteKind.TOUR.radius());
    }
}
