package fr.crusadecraft.core.treasury;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Le livre de comptes : chaque mouvement d'or est inscrit au jour où il a lieu, par poste. On garde les
 * derniers jours seulement, de quoi afficher l'historique de l'onglet Trésor.
 */
public final class Ledger {
    public static final int DAYS_KEPT = 10;

    /** Les mouvements d'un jour, par poste. Les dépenses sont négatives. */
    public static final class Day {
        private final long day;
        private final Map<LedgerCategory, Long> amounts = new EnumMap<>(LedgerCategory.class);

        public Day(long day) {
            this.day = day;
        }

        public long day() { return day; }
        public Map<LedgerCategory, Long> amounts() { return Collections.unmodifiableMap(amounts); }

        public long amount(LedgerCategory c) {
            return amounts.getOrDefault(c, 0L);
        }

        void add(LedgerCategory c, long delta) {
            long v = amounts.getOrDefault(c, 0L) + delta;
            if (v == 0) amounts.remove(c);
            else amounts.put(c, v);
        }

        public long income() {
            long sum = 0;
            for (Map.Entry<LedgerCategory, Long> e : amounts.entrySet()) if (e.getValue() > 0) sum += e.getValue();
            return sum;
        }

        public long expenses() {
            long sum = 0;
            for (Map.Entry<LedgerCategory, Long> e : amounts.entrySet()) if (e.getValue() < 0) sum += e.getValue();
            return sum;
        }

        public long net() {
            return income() + expenses();
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("day", day);
            CompoundTag a = new CompoundTag();
            for (Map.Entry<LedgerCategory, Long> e : amounts.entrySet()) a.putLong(e.getKey().name(), e.getValue());
            t.put("amounts", a);
            return t;
        }

        static Day load(CompoundTag t) {
            Day d = new Day(t.getLong("day"));
            CompoundTag a = t.getCompound("amounts");
            for (String k : a.getAllKeys()) {
                LedgerCategory c = LedgerCategory.byName(k);
                if (c != null) d.add(c, a.getLong(k));
            }
            return d;
        }
    }

    /** Du plus ancien au plus récent. */
    private final List<Day> days = new ArrayList<>();

    public List<Day> days() { return Collections.unmodifiableList(days); }

    /** Inscrit un mouvement ; {@code delta} est négatif pour une dépense. */
    public void record(long day, LedgerCategory category, long delta) {
        if (delta == 0) return;
        dayOf(day).add(category, delta);
    }

    public Day dayOf(long day) {
        for (int i = days.size() - 1; i >= 0; i--) {
            Day d = days.get(i);
            if (d.day() == day) return d;
            if (d.day() < day) {
                Day fresh = new Day(day);
                days.add(i + 1, fresh);
                trim();
                return fresh;
            }
        }
        Day fresh = new Day(day);
        days.add(0, fresh);
        trim();
        return fresh;
    }

    /** Le jour demandé s'il a été inscrit, sinon un jour vide (non ajouté). */
    public Day peek(long day) {
        for (Day d : days) if (d.day() == day) return d;
        return new Day(day);
    }

    private void trim() {
        while (days.size() > DAYS_KEPT) days.remove(0);
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Day d : days) list.add(d.save());
        return list;
    }

    public static Ledger load(ListTag list) {
        Ledger l = new Ledger();
        for (int i = 0; i < list.size(); i++) {
            Day d = Day.load(list.getCompound(i));
            l.days.add(d);
        }
        l.days.sort((a, b) -> Long.compare(a.day(), b.day()));
        l.trim();
        return l;
    }

    public static Ledger load(CompoundTag parent, String key) {
        return load(parent.getList(key, Tag.TAG_COMPOUND));
    }
}
