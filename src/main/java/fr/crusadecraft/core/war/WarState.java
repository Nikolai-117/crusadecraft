package fr.crusadecraft.core.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Les guerres en cours et le registre des dégâts. */
public final class WarState {
    private final Map<UUID, War> wars = new LinkedHashMap<>();
    private DamageLedger damage = new DamageLedger();

    public Collection<War> all() {
        return wars.values();
    }

    public Optional<War> get(UUID id) {
        return Optional.ofNullable(wars.get(id));
    }

    public void add(War w) {
        wars.put(w.id(), w);
    }

    public void remove(UUID id) {
        wars.remove(id);
    }

    public DamageLedger damage() {
        return damage;
    }

    /** La guerre où ces deux royaumes s'affrontent, s'il y en a une. */
    public Optional<War> between(UUID a, UUID b) {
        for (War w : wars.values()) if (w.opposes(a, b)) return Optional.of(w);
        return Optional.empty();
    }

    public List<War> involving(UUID realm) {
        List<War> out = new ArrayList<>();
        for (War w : wars.values()) if (w.involves(realm)) out.add(w);
        return out;
    }

    public boolean atWar(UUID realm) {
        for (War w : wars.values()) if (w.involves(realm)) return true;
        return false;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (War w : wars.values()) l.add(w.save());
        t.put("wars", l);
        t.put("damage", damage.save());
        return t;
    }

    public static WarState load(CompoundTag t) {
        WarState s = new WarState();
        ListTag l = t.getList("wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            try {
                War w = War.load(l.getCompound(i));
                s.wars.put(w.id(), w);
            } catch (RuntimeException ignored) {
                // guerre illisible : abandonnée
            }
        }
        s.damage = DamageLedger.load(t.getCompound("damage"));
        return s;
    }
}
