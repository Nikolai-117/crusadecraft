package fr.crusadecraft.core.law;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/** Les lois en vigueur dans un royaume, et le jour de leur dernier changement. */
public final class LawState {
    private final Map<Law, Integer> levels = new EnumMap<>(Law.class);
    private final Map<Law, Long> changedDay = new EnumMap<>(Law.class);

    public int level(Law law) {
        return law.clamp(levels.getOrDefault(law, law.defaultLevel()));
    }

    public void set(Law law, int level, long day) {
        levels.put(law, law.clamp(level));
        changedDay.put(law, day);
    }

    /** Jour du dernier changement, ou {@link Long#MIN_VALUE} si la loi n'a jamais changé. */
    public long changedDay(Law law) {
        return changedDay.getOrDefault(law, Long.MIN_VALUE);
    }

    /** Jours restant avant de pouvoir rechanger cette loi (0 = possible). */
    public long cooldownLeft(Law law, long today, int cooldownDays) {
        long last = changedDay(law);
        if (last == Long.MIN_VALUE) return 0;
        return Math.max(0, last + cooldownDays - today);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<Law, Integer> e : levels.entrySet()) {
            CompoundTag l = new CompoundTag();
            l.putInt("level", e.getValue());
            Long day = changedDay.get(e.getKey());
            if (day != null) l.putLong("day", day);
            t.put(e.getKey().name(), l);
        }
        return t;
    }

    public static LawState load(CompoundTag t) {
        LawState s = new LawState();
        for (String key : t.getAllKeys()) {
            Law law = Law.byName(key);
            if (law == null) continue;
            CompoundTag l = t.getCompound(key);
            s.levels.put(law, law.clamp(l.getInt("level")));
            if (l.contains("day")) s.changedDay.put(law, l.getLong("day"));
        }
        return s;
    }
}
