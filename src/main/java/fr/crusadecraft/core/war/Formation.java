package fr.crusadecraft.core.war;

import java.util.Locale;

/**
 * Les formations de l'armée en chair et en os (P27), en logique pure : où se tient le soldat n° {@code index} sur
 * {@code count}, autour du point visé, face à la direction {@code facing} (en radians, 0 = vers les x croissants).
 */
public enum Formation {
    /** En masse, serrés autour du point : la façon d'avant. */
    MASSE,
    /** En ligne de front, sur deux rangs, perpendiculaire à la marche. */
    LIGNE,
    /** En colonne de marche, par deux, derrière le point. */
    COLONNE,
    /** En carré, le point au centre. */
    CARRE;

    /** L'écart entre deux soldats. */
    public static final double SPACING = 2.0;

    public String key() {
        return "crusadecraft.army.formation." + name().toLowerCase(Locale.ROOT);
    }

    public Formation next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Formation byOrdinal(int i) {
        Formation[] all = values();
        return all[Math.floorMod(i, all.length)];
    }

    /** Le décalage {x, z} du soldat par rapport au point visé. */
    public double[] offset(int index, int count, double facing) {
        double forward, side;
        switch (this) {
            case LIGNE -> {
                int perRank = Math.max(1, (count + 1) / 2);
                int rank = index / perRank, file = index % perRank;
                side = (file - (Math.min(perRank, count - rank * perRank) - 1) / 2.0) * SPACING;
                forward = -rank * SPACING;
            }
            case COLONNE -> {
                int row = index / 2;
                side = (index % 2 == 0 ? -0.5 : 0.5) * SPACING;
                forward = -row * SPACING;
            }
            case CARRE -> {
                int edge = (int) Math.ceil(Math.sqrt(count));
                int row = index / edge, col = index % edge;
                side = (col - (edge - 1) / 2.0) * SPACING;
                forward = -(row - (edge - 1) / 2.0) * SPACING;
            }
            default -> {
                // La spirale d'or : serrés, sans se marcher dessus.
                double a = index * 2.399963, r = Math.sqrt(index) * SPACING * 0.8;
                return new double[]{Math.cos(a) * r, Math.sin(a) * r};
            }
        }
        double cos = Math.cos(facing), sin = Math.sin(facing);
        // En avant le long de la direction, sur le côté à sa perpendiculaire.
        return new double[]{forward * cos - side * sin, forward * sin + side * cos};
    }
}
