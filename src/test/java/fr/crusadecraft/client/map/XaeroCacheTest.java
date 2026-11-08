package fr.crusadecraft.client.map;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** La carte de Xaero (P63) : lire ses images en cache, au format de Xaero's World Map 1.39. */
class XaeroCacheTest {

    /** Un cache comme Xaero l'écrit : en-tête, images présentes, palette des biomes, puis chaque image et ce qui la suit. */
    private static byte[] cache(boolean leaf, int[][] textures) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x00010018);
        if (leaf) for (int i = 0; i < 5; i++) out.writeInt(0x7FFFFFFF - i);
        for (int[] t : textures) {
            out.write(t[0] << 4 | t[1]);
            out.writeInt(12345);
        }
        out.write(255);
        out.writeInt(2);
        out.write(0);
        out.writeUTF("minecraft:plains");
        out.write(255);
        for (int[] t : textures) {
            out.write(t[0] << 4 | t[1]);
            out.write(0);
            out.writeInt(0x8058);
            out.writeInt(64 * 64 * 4);
            for (int k = 0; k < 64 * 64; k++) {
                out.write(0);
                out.write(t[2] >> 16 & 0xFF);
                out.write(t[2] >> 8 & 0xFF);
                out.write(t[2] & 0xFF);
            }
            // Ce que Xaero range après l'image (lumière, hauteurs, biomes) : le lecteur n'a pas à le comprendre.
            out.writeBoolean(false);
            for (int k = 0; k < 1664; k++) out.writeLong(k * 31L);
            out.writeInt(0);
        }
        out.write(255);
        return bytes.toByteArray();
    }

    @Test
    void lesImagesSeRangentALeurPlace() throws IOException {
        int[] px = XaeroCache.parse(cache(true, new int[][]{{0, 0, 0x336699}, {3, 5, 0x88AA22}}), true);
        assertNotNull(px);
        assertEquals(0xFF336699, px[0]);
        assertEquals(0xFF336699, px[63 * 512 + 63]);
        // L'image (3, 5) : x de 192 à 255, z de 320 à 383.
        assertEquals(0xFF88AA22, px[320 * 512 + 192]);
        assertEquals(0xFF88AA22, px[383 * 512 + 255]);
        // Ce que Xaero n'a pas dessiné reste vide.
        assertEquals(0, px[100 * 512 + 100]);
    }

    @Test
    void unZoomLointainNAPasLEnTeteDuDetail() throws IOException {
        int[] px = XaeroCache.parse(cache(false, new int[][]{{7, 7, 0x112233}}), false);
        assertNotNull(px);
        assertEquals(0xFF112233, px[511 * 512 + 511]);
    }

    @Test
    void unAutreFormatNeSeLitPas() throws IOException {
        byte[] data = cache(true, new int[][]{{0, 0, 0x336699}});
        data[1] = 2;
        assertNull(XaeroCache.parse(data, true));
        assertNull(XaeroCache.parse(new byte[3], true));
        assertNull(XaeroCache.parse(null, true));
    }
}
