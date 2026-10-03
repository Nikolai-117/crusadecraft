package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/** Un complot ourdi par un courtisan : il avance chaque jour, jusqu'à aboutir ou être éventé. */
public final class Plot {

    public enum Kind {
        /** Détourner une part du trésor. */
        DETOURNEMENT,
        /** Saboter l'armée. */
        SABOTAGE,
        /** Salir le nom du souverain. */
        DIFFAMATION;

        public String key() {
            return "crusadecraft.plot." + name().toLowerCase(Locale.ROOT);
        }

        public static Kind byName(String name) {
            try {
                return Kind.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final UUID id;
    private final CitizenRef plotter;
    private final Kind kind;
    private final long startDay;
    private int progress;
    private boolean discovered;

    public Plot(UUID id, CitizenRef plotter, Kind kind, long startDay) {
        this.id = id;
        this.plotter = plotter;
        this.kind = kind;
        this.startDay = startDay;
    }

    public UUID id() { return id; }
    public CitizenRef plotter() { return plotter; }
    public Kind kind() { return kind; }
    public long startDay() { return startDay; }
    public int progress() { return progress; }
    public void setProgress(int progress) { this.progress = Math.max(0, Math.min(100, progress)); }
    public boolean discovered() { return discovered; }
    public void setDiscovered(boolean discovered) { this.discovered = discovered; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.put("plotter", plotter.save());
        t.putString("kind", kind.name());
        t.putLong("start", startDay);
        t.putInt("progress", progress);
        t.putBoolean("discovered", discovered);
        return t;
    }

    public static Plot load(CompoundTag t) {
        Kind kind = Kind.byName(t.getString("kind"));
        if (kind == null) return null;
        Plot p = new Plot(t.getUUID("id"), CitizenRef.load(t.getCompound("plotter")), kind, t.getLong("start"));
        p.progress = t.getInt("progress");
        p.discovered = t.getBoolean("discovered");
        return p;
    }
}
