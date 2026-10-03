package fr.crusadecraft.core.court;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La maison du souverain : les citoyens qu'il a fait entrer dans sa dynastie, leurs conjoints et leurs enfants
 * (venus de la généalogie MineColonies), l'héritier désigné, et les mariages conclus avec les royaumes IA.
 */
public final class HouseState {
    public static final int MAX_ADOPTED = 8;

    /** Un mariage dynastique : un membre de la maison uni à la famille d'un souverain IA. */
    public record Marriage(CitizenRef member, UUID realm, String spouse, long day) {
        CompoundTag save() {
            CompoundTag t = member.save();
            t.putUUID("realm", realm);
            t.putString("spouse", spouse);
            t.putLong("day", day);
            return t;
        }

        static Marriage load(CompoundTag t) {
            return new Marriage(CitizenRef.load(t), t.getUUID("realm"), t.getString("spouse"), t.getLong("day"));
        }
    }

    private final Set<CitizenRef> members = new LinkedHashSet<>();
    private CitizenRef heir;
    private final List<Marriage> marriages = new ArrayList<>();
    /** Les mariages acceptés par un royaume IA et qui attendent le sceau : royaume → membre promis. */
    private final Map<UUID, CitizenRef> promised = new HashMap<>();

    public Set<CitizenRef> members() { return members; }
    public CitizenRef heir() { return heir; }
    public void setHeir(CitizenRef heir) { this.heir = heir; }
    public List<Marriage> marriages() { return marriages; }
    public Map<UUID, CitizenRef> promised() { return promised; }

    public Optional<Marriage> marriageOf(CitizenRef member) {
        for (Marriage m : marriages) if (m.member().equals(member)) return Optional.of(m);
        return Optional.empty();
    }

    public boolean marriedInto(UUID realm) {
        for (Marriage m : marriages) if (m.realm().equals(realm)) return true;
        return false;
    }

    public void remove(CitizenRef member) {
        members.remove(member);
        if (member.equals(heir)) heir = null;
        promised.values().removeIf(member::equals);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag m = new ListTag();
        for (CitizenRef c : members) m.add(c.save());
        t.put("members", m);
        if (heir != null) t.put("heir", heir.save());
        ListTag mar = new ListTag();
        for (Marriage x : marriages) mar.add(x.save());
        t.put("marriages", mar);
        ListTag p = new ListTag();
        for (Map.Entry<UUID, CitizenRef> e : promised.entrySet()) {
            CompoundTag x = e.getValue().save();
            x.putUUID("realm", e.getKey());
            p.add(x);
        }
        t.put("promised", p);
        return t;
    }

    public static HouseState load(CompoundTag t) {
        HouseState s = new HouseState();
        ListTag m = t.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < m.size(); i++) s.members.add(CitizenRef.load(m.getCompound(i)));
        if (t.contains("heir")) s.heir = CitizenRef.load(t.getCompound("heir"));
        ListTag mar = t.getList("marriages", Tag.TAG_COMPOUND);
        for (int i = 0; i < mar.size(); i++) s.marriages.add(Marriage.load(mar.getCompound(i)));
        ListTag p = t.getList("promised", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) s.promised.put(p.getCompound(i).getUUID("realm"), CitizenRef.load(p.getCompound(i)));
        return s;
    }
}
