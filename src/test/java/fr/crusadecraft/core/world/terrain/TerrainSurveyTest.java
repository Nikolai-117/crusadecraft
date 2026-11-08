package fr.crusadecraft.core.world.terrain;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le relevé du monde (P62) : quels chunks un fichier de région contient, d'après son en-tête. */
class TerrainSurveyTest {

    /** Une entrée de la table des emplacements : un décalage en secteurs sur trois octets, puis leur nombre. */
    private static void put(byte[] header, int index, int offset, int sectors) {
        int at = index * 4;
        header[at] = (byte) (offset >> 16);
        header[at + 1] = (byte) (offset >> 8);
        header[at + 2] = (byte) offset;
        header[at + 3] = (byte) sectors;
    }

    @Test
    void unEnTeteVideNeDonneAucunChunk() {
        assertTrue(TerrainSurvey.chunksIn(0, 0, new byte[4096]).isEmpty());
    }

    @Test
    void lesChunksPresentsSeLisentDansLOrdreDeLaTable() {
        byte[] header = new byte[4096];
        put(header, 0, 2, 1);            // x 0, z 0
        put(header, 31, 3, 2);           // x 31, z 0
        put(header, 32 * 5 + 7, 5, 1);   // x 7, z 5
        put(header, 1023, 6, 1);         // x 31, z 31
        // Une région négative : r.-1.2.mca commence au chunk (-32, 64).
        LongArrayList chunks = TerrainSurvey.chunksIn(-1, 2, header);
        assertEquals(4, chunks.size());
        assertEquals(ChunkPos.asLong(-32, 64), chunks.getLong(0));
        assertEquals(ChunkPos.asLong(-1, 64), chunks.getLong(1));
        assertEquals(ChunkPos.asLong(-25, 69), chunks.getLong(2));
        assertEquals(ChunkPos.asLong(-1, 95), chunks.getLong(3));
    }

    @Test
    void seulsLesFichiersDeRegionSontRetenus() {
        assertArrayEquals(new int[]{-3, 12}, TerrainSurvey.regionOf("r.-3.12.mca"));
        assertArrayEquals(new int[]{0, 0}, TerrainSurvey.regionOf("r.0.0.mca"));
        assertNull(TerrainSurvey.regionOf("r.0.0.mcc"));
        assertNull(TerrainSurvey.regionOf("r.a.0.mca"));
        assertNull(TerrainSurvey.regionOf("level.dat"));
        assertNull(TerrainSurvey.regionOf("r.0.0.mca.tmp"));
    }
}
