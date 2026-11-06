package fr.crusadecraft.core.build.plan;

import java.util.Random;

/** Un bruit doux (P60), pour que les côtes, les rivières et les bords n'aient pas l'air tirés à la règle. Déterminé par sa graine, comme tout le plan. */
final class UrbanNoise {
    private static final int SIZE = 512;
    private final double[] values = new double[SIZE];

    UrbanNoise(long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < SIZE; i++) values[i] = r.nextDouble();
    }

    private double lattice(int i) {
        return values[Math.floorMod(i, SIZE)];
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Une valeur entre 0 et 1, le long d'une ligne ; {@code salt} en fait une autre ligne. */
    double at(double t, int salt) {
        int i = (int) Math.floor(t);
        double f = smooth(t - i);
        return lattice(i + salt * 37) * (1 - f) + lattice(i + 1 + salt * 37) * f;
    }

    /** Une valeur entre 0 et 1, sur un plan. */
    double at2(double x, double z, int salt) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = lattice(ix + iz * 57 + salt * 37), b = lattice(ix + 1 + iz * 57 + salt * 37);
        double c = lattice(ix + (iz + 1) * 57 + salt * 37), d = lattice(ix + 1 + (iz + 1) * 57 + salt * 37);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }
}
