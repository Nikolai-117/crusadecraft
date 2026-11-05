package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Un voyage en cours (P48) : d'où il part, où il va, où il en est, et ce qu'il transporte.
 *
 * @param stage  à l'aller, sur place, ou au retour
 * @param start  le tick où cette étape a commencé
 * @param end    le tick où elle s'achève
 * @param good   la marchandise qu'il emporte à l'aller ; {@code null} s'il ne fait que passer
 * @param back   celle qu'il rapporte
 * @param earned ce que sa vente a rapporté, qu'il remettra à sa ville en rentrant
 * @param leader le marchand qu'il escorte (P50) ; {@code null} s'il voyage pour lui-même
 * @param line   la ligne de commerce qu'il sert (P51) ; {@code null} s'il va où bon lui semble
 */
public record Trip(UUID home, UUID dest, Stage stage, long start, long end, CityGood good, int amount, CityGood back, int backAmount, int earned, UUID leader, UUID line) {
    public enum Stage { OUT, STAY, BACK }

    /** Un voyage à son propre compte, qui ne suit personne. */
    public Trip(UUID home, UUID dest, Stage stage, long start, long end, CityGood good, int amount, CityGood back, int backAmount, int earned) {
        this(home, dest, stage, start, end, good, amount, back, backAmount, earned, null, null);
    }

    /** Un garde qui suit son marchand. */
    public Trip(UUID home, UUID dest, Stage stage, long start, long end, CityGood good, int amount, CityGood back, int backAmount, int earned, UUID leader) {
        this(home, dest, stage, start, end, good, amount, back, backAmount, earned, leader, null);
    }

    /** Où il en est sur la route : de 0 (au départ) à 1 (arrivé). */
    public double progress(long now) {
        if (end <= start) return 1;
        return Math.max(0, Math.min(1, (double) (now - start) / (end - start)));
    }

    public Trip next(Stage stage, long start, long end, CityGood back, int backAmount, int earned) {
        return new Trip(home, dest, stage, start, end, good, amount, back, backAmount, earned, leader, line);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("home", home);
        t.putUUID("dest", dest);
        t.putString("stage", stage.name());
        t.putLong("start", start);
        t.putLong("end", end);
        if (good != null) t.putString("good", good.name());
        t.putInt("amount", amount);
        if (back != null) t.putString("back", back.name());
        t.putInt("backAmount", backAmount);
        t.putInt("earned", earned);
        if (leader != null) t.putUUID("leader", leader);
        if (line != null) t.putUUID("line", line);
        return t;
    }

    public static Trip load(CompoundTag t) {
        Stage stage;
        try {
            stage = Stage.valueOf(t.getString("stage"));
        } catch (IllegalArgumentException e) {
            stage = Stage.BACK;
        }
        return new Trip(t.getUUID("home"), t.getUUID("dest"), stage, t.getLong("start"), t.getLong("end"), good(t.getString("good")), t.getInt("amount"),
                good(t.getString("back")), t.getInt("backAmount"), t.getInt("earned"), t.hasUUID("leader") ? t.getUUID("leader") : null, t.hasUUID("line") ? t.getUUID("line") : null);
    }

    private static CityGood good(String name) {
        try {
            return name.isEmpty() ? null : CityGood.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
