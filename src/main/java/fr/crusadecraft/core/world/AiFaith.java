package fr.crusadecraft.core.world;

import java.util.Locale;
import java.util.UUID;

/**
 * La foi d'un royaume IA. Les royaumes des joueurs partagent la foi commune ; les païens et les hérétiques sont
 * les cibles des croisades, et ne s'y joignent jamais.
 */
public enum AiFaith {
    FIDELE, PAIEN, HERETIQUE;

    public String key() {
        return "crusadecraft.faith." + name().toLowerCase(Locale.ROOT);
    }

    public boolean infidel() {
        return this != FIDELE;
    }

    /** La foi d'un royaume, tirée de son identifiant : un sur huit païen, un sur huit hérétique, toujours la même. */
    public static AiFaith of(UUID realm) {
        return switch (Math.floorMod(realm.hashCode(), 8)) {
            case 0 -> PAIEN;
            case 1 -> HERETIQUE;
            default -> FIDELE;
        };
    }

    public static AiFaith byName(String name, AiFaith fallback) {
        try {
            return AiFaith.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
