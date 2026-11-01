package fr.crusadecraft.core.build.plan;

/** Les entiers à longueur variable des schématiques Sponge (P34) : sept bits par octet, le huitième dit « la suite ». */
public final class Varints {
    private Varints() {}

    /** Décode {@code count} entiers ; s'il en manque, le reste vaut 0. */
    public static int[] decode(byte[] data, int count) {
        int[] out = new int[count];
        int i = 0, n = 0;
        while (n < count && i < data.length) {
            int value = 0, shift = 0;
            while (i < data.length) {
                int b = data[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 28) break;
            }
            out[n++] = value;
        }
        return out;
    }

    public static byte[] encode(int[] values) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int v : values) {
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }
}
