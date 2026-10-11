package fr.crusadecraft.core.world.terrain;

import fr.crusadecraft.core.world.terrain.TerrainPalette.Kind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carte en terrain (P18b) : les couleurs du paysage, l'ombre du relief, et les tuiles. */
class TerrainTest {
    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    @Test
    void laMerSAssombritAuLarge() {
        int shore = TerrainPalette.color(Kind.OCEAN, 60, 2, 63);
        int open = TerrainPalette.color(Kind.OCEAN, 30, 33, 63);
        int deep = TerrainPalette.color(Kind.DEEP_OCEAN, 20, 43, 63);
        assertTrue(brightness(shore) > brightness(open), "le rivage est plus clair que le large");
        assertTrue(brightness(open) >= brightness(deep), "le grand large est le plus sombre");
        // De l'eau sur une terre (un lac) reste de l'eau, pas de l'herbe.
        int lake = TerrainPalette.color(Kind.PLAINS, 62, 3, 63);
        assertTrue((lake & 0xFF) > ((lake >> 8) & 0xFF), "un lac est bleu");
    }

    @Test
    void lesSommetsBlanchissent() {
        int valley = TerrainPalette.color(Kind.FOREST, 70, 0, 63);
        int summit = TerrainPalette.color(Kind.FOREST, 240, 0, 63);
        assertEquals(Kind.FOREST.color, valley, "en plaine, la couleur du biome");
        assertTrue(brightness(summit) > brightness(valley) + 200, "un sommet est presque blanc");
        assertEquals(Kind.DESERT.color, TerrainPalette.color(Kind.DESERT, 140, 0, 63), "un désert ne verdit ni ne grisonne");
    }

    @Test
    void leReliefSOmbreCommeAuSoleilDuNordOuest() {
        int c = 0x808080;
        assertEquals(c, TerrainPalette.shade(c, 70, 70, 16), "plat : pas d'ombre");
        assertTrue(brightness(TerrainPalette.shade(c, 80, 70, 16)) > brightness(c), "une pente tournée vers la lumière s'éclaire");
        assertTrue(brightness(TerrainPalette.shade(c, 70, 80, 16)) < brightness(c), "l'autre versant s'assombrit");
        // La même pente garde la même ombre, quel que soit l'écart entre les points.
        assertEquals(TerrainPalette.shade(c, 74, 70, 16), TerrainPalette.shade(c, 86, 70, 64));
    }

    @Test
    void lesClesDeTuilesSontReversibles() {
        int[][] cases = {{0, 0, 0}, {1, -1, 5}, {2, -70000, 123456}, {0, 1_000_000, -1_000_000}};
        for (int[] c : cases) {
            long key = TerrainTile.key(c[0], c[1], c[2]);
            assertEquals(c[0], TerrainTile.lodOf(key));
            assertEquals(c[1], TerrainTile.txOf(key));
            assertEquals(c[2], TerrainTile.tzOf(key));
        }
        assertNotEquals(TerrainTile.key(0, 1, 2), TerrainTile.key(1, 1, 2), "deux niveaux de détail, deux tuiles");
    }

    @Test
    void lesPointsTombentDansLaBonneTuile() {
        assertEquals(0, TerrainTile.tileOf(0, 0));
        assertEquals(0, TerrainTile.tileOf(511.9, 0));
        assertEquals(1, TerrainTile.tileOf(512, 0));
        assertEquals(-1, TerrainTile.tileOf(-0.5, 0));
        assertEquals(-1, TerrainTile.tileOf(-8192, 2));
        assertEquals(-2, TerrainTile.sampleOf(-17, 0));
        assertEquals(8, TerrainTile.blockOf(0, 0), "un point se mesure au milieu de sa case");
        assertEquals(-32, TerrainTile.blockOf(-1, 1), "la case −1 de 64 blocs va de −64 à −1");
    }

    @Test
    void uneTuileSEcritEtSeRelit() throws IOException {
        int n = TerrainTile.SIZE * TerrainTile.SIZE;
        int[] colors = new int[n];
        short[] heights = new short[n];
        for (int i = 0; i < n; i++) {
            colors[i] = 0xFF000000 | (i * 2654435761L > 0 ? i * 97 : i);
            heights[i] = (short) (i % 400 - 64);
        }
        TerrainTile t = new TerrainTile(1, -3, 7, colors, heights);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        t.write(new DataOutputStream(bytes));
        TerrainTile back = TerrainTile.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(t.key(), back.key());
        assertArrayEquals(colors, back.colors());
        assertArrayEquals(heights, back.heights());
    }
}
