package fr.crusadecraft.core.world;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le territoire au chunk (P16) : un chunk a un seul maître, décidé par son centre ; la peinture l'emporte. */
class ChunkTerritoryTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");
    private static final UUID A = new UUID(1, 1), B = new UUID(1, 2);

    private static Territory.Snapshot snap(TerritoryMap map, Territory.Circle... circles) {
        return new Territory.Snapshot(map, OW, List.of(circles));
    }

    @Test
    void unChunkSeDecideParSonCentre() {
        // Un cercle de 20 blocs centré en (8,5 ; 8,5) : le chunk (1, 0) a son centre à 15,5 blocs, il est pris ;
        // le chunk (2, 0), à 31,5 blocs, ne l'est pas.
        Territory.Snapshot s = snap(new TerritoryMap(), new Territory.Circle(8.5, 8.5, 400, A, Territory.Kind.SITE));
        assertTrue(s.ownerOfCell(TerritoryMap.key(1, 0)).map(A::equals).orElse(false));
        assertFalse(s.ownerOfCell(TerritoryMap.key(2, 0)).isPresent());
        // Le bloc (30, 8) est hors du cercle (21,5 blocs), mais dans le chunk (1, 0) : il est à A, comme tout son chunk.
        assertTrue(s.ownerAt(30, 8).map(A::equals).orElse(false), "un bloc suit son chunk, pas le cercle");
        assertTrue(s.ownerAt(16, 0).equals(s.ownerAt(31, 15)), "deux blocs du même chunk ont le même maître");
    }

    @Test
    void laPeintureLEmporte() {
        TerritoryMap map = new TerritoryMap();
        map.paint(OW, new long[]{TerritoryMap.key(0, 0)}, B);
        map.paint(OW, new long[]{TerritoryMap.key(1, 0)}, TerritoryMap.NEUTRAL);
        Territory.Snapshot s = snap(map, new Territory.Circle(8.5, 8.5, 400, A, Territory.Kind.COLONY));
        assertTrue(s.ownerAt(3, 3).map(B::equals).orElse(false), "le chunk peint pour B est à B, cercle de A ou non");
        assertFalse(s.ownerAt(20, 3).isPresent(), "un chunk rendu à la terre libre n'est à personne");
        assertEquals(1, s.claimedBy(B));
    }

    @Test
    void lesChunksDeChacun() {
        TerritoryMap map = new TerritoryMap();
        map.paint(OW, new long[]{TerritoryMap.key(10, 10), TerritoryMap.key(11, 10)}, B);
        Territory.Snapshot s = snap(map, new Territory.Circle(8.5, 8.5, 32 * 32, A, Territory.Kind.PROVINCE));
        var chunks = s.chunksByOwner();
        assertEquals(2, chunks.get(B).size());
        // Un cercle de 32 blocs : les chunks dont le centre est à moins de 32 blocs de (8,5 ; 8,5).
        int expected = 0;
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) {
            double dx = cx * 16 + 8 - 8.5, dz = cz * 16 + 8 - 8.5;
            if (dx * dx + dz * dz <= 32 * 32) expected++;
        }
        assertEquals(expected, chunks.get(A).size());
        for (long k : chunks.get(A)) assertTrue(s.ownerOfCell(k).map(A::equals).orElse(false));
    }

    @Test
    void leCarreDesCommandes() {
        assertEquals(1, ClaimService.square(0, 0, 0).length);
        assertEquals(9, ClaimService.square(5, -5, 1).length);
        assertEquals(49, ClaimService.square(0, 0, 3).length);
    }

    @Test
    void uneCouleurSombreSeLitQuandMeme() {
        int dark = EntryTitles.readable(0x101020);
        int r = (dark >> 16) & 0xFF, g = (dark >> 8) & 0xFF, b = dark & 0xFF;
        assertTrue(0.2126 * r + 0.7152 * g + 0.0722 * b >= 60, "un bleu nuit devient lisible");
        assertEquals(0xD4A017, EntryTitles.readable(0xD4A017), "une couleur claire ne change pas");
    }
}
