package fr.crusadecraft.core.character;

import net.minecraft.nbt.CompoundTag;

/**
 * Un motif d'opinion daté et enregistré.
 *
 * @param value      valeur au moment où il naît (peut être multipliée par un trait)
 * @param createdDay jour du monde où il naît
 * @param expiresDay jour où il disparaît
 */
public record OpinionModifier(OpinionReason reason, int value, long createdDay, long expiresDay, boolean decaying) {

    public static OpinionModifier of(OpinionReason reason, long today, double multiplier) {
        int v = (int) Math.round(reason.value() * multiplier);
        return new OpinionModifier(reason, v, today, today + reason.durationDays(), reason.decaying());
    }

    public boolean expired(long today) {
        return today >= expiresDay;
    }

    /** Valeur à la date donnée : entière, ou dégressive jusqu'à zéro. */
    public int valueAt(long today) {
        if (expired(today)) return 0;
        if (!decaying) return value;
        long span = Math.max(1, expiresDay - createdDay);
        double left = (expiresDay - today) / (double) span;
        return (int) Math.round(value * Math.max(0, Math.min(1, left)));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("reason", reason.name());
        t.putInt("value", value);
        t.putLong("from", createdDay);
        t.putLong("to", expiresDay);
        t.putBoolean("decay", decaying);
        return t;
    }

    public static OpinionModifier load(CompoundTag t) {
        OpinionReason r = OpinionReason.byName(t.getString("reason"));
        if (r == null) return null;
        return new OpinionModifier(r, t.getInt("value"), t.getLong("from"), t.getLong("to"), t.getBoolean("decay"));
    }
}
