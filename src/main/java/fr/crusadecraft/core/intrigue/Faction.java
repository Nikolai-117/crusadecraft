package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Une faction de la cour : des mécontents qui veulent la même chose. Elle grossit en silence, jusqu'à ce que le
 * Maître-espion la découvre ou qu'elle pèse assez pour poser un ultimatum.
 */
public final class Faction {

    public enum Kind {
        /** Baisser les impôts d'un cran. */
        BAISSE_IMPOTS,
        /** Une province veut quitter le royaume. */
        INDEPENDANCE,
        /** Chasser un conseiller honni. */
        CHANGEMENT;

        public String key() {
            return "crusadecraft.faction." + name().toLowerCase(Locale.ROOT);
        }

        public static Kind byName(String name) {
            try {
                return Kind.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Kind kind;
    private final Set<CitizenRef> members = new LinkedHashSet<>();
    private final long formedDay;
    private boolean discovered;
    private long lastUltimatumDay = Long.MIN_VALUE;
    /** La province qui veut partir (indépendance). */
    private ColonyRef colony;
    /** Le conseiller qu'on veut chasser (changement). */
    private CitizenRef target;

    public Faction(Kind kind, long formedDay) {
        this.kind = kind;
        this.formedDay = formedDay;
    }

    public Kind kind() { return kind; }
    public Set<CitizenRef> members() { return members; }
    public long formedDay() { return formedDay; }
    public boolean discovered() { return discovered; }
    public void setDiscovered(boolean discovered) { this.discovered = discovered; }
    public long lastUltimatumDay() { return lastUltimatumDay; }
    public void setLastUltimatumDay(long day) { this.lastUltimatumDay = day; }
    public ColonyRef colony() { return colony; }
    public void setColony(ColonyRef colony) { this.colony = colony; }
    public CitizenRef target() { return target; }
    public void setTarget(CitizenRef target) { this.target = target; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind.name());
        t.putLong("formed", formedDay);
        t.putBoolean("discovered", discovered);
        t.putLong("ultimatum", lastUltimatumDay);
        ListTag m = new ListTag();
        for (CitizenRef c : members) m.add(c.save());
        t.put("members", m);
        if (colony != null) t.put("colony", colony.save());
        if (target != null) t.put("target", target.save());
        return t;
    }

    public static Faction load(CompoundTag t) {
        Kind kind = Kind.byName(t.getString("kind"));
        if (kind == null) return null;
        Faction f = new Faction(kind, t.getLong("formed"));
        f.discovered = t.getBoolean("discovered");
        f.lastUltimatumDay = t.contains("ultimatum") ? t.getLong("ultimatum") : Long.MIN_VALUE;
        ListTag m = t.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < m.size(); i++) f.members.add(CitizenRef.load(m.getCompound(i)));
        if (t.contains("colony")) f.colony = ColonyRef.load(t.getCompound("colony"));
        if (t.contains("target")) f.target = CitizenRef.load(t.getCompound("target"));
        return f;
    }
}
