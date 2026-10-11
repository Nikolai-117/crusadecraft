package fr.crusadecraft.core.world.terrain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Une tuile de la carte en terrain (P18b) : {@link #SIZE} × {@link #SIZE} points, espacés de {@link #spacing} blocs
 * selon le niveau de détail. Chaque point garde sa teinte (sans ombre) et son altitude ; l'ombre se calcule à
 * l'affichage, à partir des voisins.
 *
 * @param colors  la teinte de chaque point (RGB, opaque), ligne par ligne du nord au sud ; 0 : inconnu
 * @param heights l'altitude de chaque point, pour l'ombre du relief
 */
public record TerrainTile(int lod, int tx, int tz, int[] colors, short[] heights) {
    public static final int SIZE = 32;
    /** Écart entre deux points selon le niveau de détail : un chunk, quatre chunks, seize chunks. */
    public static final int[] SPACING = {16, 64, 256};
    public static final int LODS = SPACING.length;

    public static int spacing(int lod) {
        return SPACING[Math.max(0, Math.min(LODS - 1, lod))];
    }

    /** Le côté d'une tuile, en blocs. */
    public static int span(int lod) {
        return spacing(lod) * SIZE;
    }

    /** La clé d'une tuile : niveau de détail sur 2 bits, puis ses coordonnées. */
    public static long key(int lod, int tx, int tz) {
        return ((long) lod << 62) | ((long) (tx & 0x7FFFFFFF) << 31) | (tz & 0x7FFFFFFFL);
    }

    public static int lodOf(long key) {
        return (int) (key >>> 62);
    }

    public static int txOf(long key) {
        return (int) ((key << 2) >> 33);
    }

    public static int tzOf(long key) {
        return (int) ((key << 33) >> 33);
    }

    public long key() {
        return key(lod, tx, tz);
    }

    /** La tuile qui contient ce bloc, à ce niveau de détail. */
    public static int tileOf(double block, int lod) {
        return Math.floorDiv((int) Math.floor(block), span(lod));
    }

    /** Le point de la grille (indice global) qui contient ce bloc. */
    public static int sampleOf(double block, int lod) {
        return Math.floorDiv((int) Math.floor(block), spacing(lod));
    }

    /** Le bloc où l'on mesure le point {@code sample} : le milieu de sa case. */
    public static int blockOf(int sample, int lod) {
        return sample * spacing(lod) + spacing(lod) / 2;
    }

    public int index(int localX, int localZ) {
        return localZ * SIZE + localX;
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeByte(lod);
        out.writeInt(tx);
        out.writeInt(tz);
        for (int i = 0; i < SIZE * SIZE; i++) {
            out.writeInt(colors[i]);
            out.writeShort(heights[i]);
        }
    }

    public static TerrainTile read(DataInputStream in) throws IOException {
        int lod = in.readByte(), tx = in.readInt(), tz = in.readInt();
        int[] colors = new int[SIZE * SIZE];
        short[] heights = new short[SIZE * SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) {
            colors[i] = in.readInt();
            heights[i] = in.readShort();
        }
        return new TerrainTile(lod, tx, tz, colors, heights);
    }
}
