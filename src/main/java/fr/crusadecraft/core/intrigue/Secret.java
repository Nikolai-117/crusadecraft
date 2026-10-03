package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

/** Un secret qu'un courtisan cache, et que le Maître-espion peut mettre au jour : de quoi le faire chanter ou l'exposer. */
public final class Secret {

    public enum Kind {
        DETTES_DE_JEU,
        LIAISON,
        HERESIE,
        CORRUPTION;

        public String key() {
            return "crusadecraft.secret." + name().toLowerCase(Locale.ROOT);
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
    private final CitizenRef holder;
    private final Kind kind;
    private boolean known;

    public Secret(UUID id, CitizenRef holder, Kind kind) {
        this.id = id;
        this.holder = holder;
        this.kind = kind;
    }

    public UUID id() { return id; }
    public CitizenRef holder() { return holder; }
    public Kind kind() { return kind; }
    public boolean known() { return known; }
    public void setKnown(boolean known) { this.known = known; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.put("holder", holder.save());
        t.putString("kind", kind.name());
        t.putBoolean("known", known);
        return t;
    }

    public static Secret load(CompoundTag t) {
        Kind kind = Kind.byName(t.getString("kind"));
        if (kind == null) return null;
        Secret s = new Secret(t.getUUID("id"), CitizenRef.load(t.getCompound("holder")), kind);
        s.known = t.getBoolean("known");
        return s;
    }
}
