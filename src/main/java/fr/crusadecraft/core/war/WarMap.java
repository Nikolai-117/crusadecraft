package fr.crusadecraft.core.war;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La carte de guerre (P18) : ce que chaque royaume y a tracé, partagé avec ses alliés. Des zones peintes chunk par chunk
 * (offensive, défense, objectif), des flèches d'attaque et des repères annotés. L'état de la guerre lui-même (sièges,
 * places occupées) ne se range pas ici : il se lit dans le monde, à chaque fois.
 */
public final class WarMap {
    /** Ce que dit une zone. L'ordre compte : il est sauvegardé par son rang. */
    public enum Zone { OFFENSIVE, DEFENSE, OBJECTIF }

    /** Ce que montre un repère. */
    public enum Mark { OBJECTIF, RASSEMBLEMENT, DANGER, NOTE }

    /** Une flèche d'attaque, en blocs. */
    public record Arrow(UUID id, ResourceLocation dim, int x0, int z0, int x1, int z1) {}

    /** Un repère, avec sa note. */
    public record Pin(UUID id, ResourceLocation dim, int x, int z, Mark kind, String note) {}

    public static final int MAX_ARROWS = 64, MAX_PINS = 64, MAX_NOTE = 120;

    /** Le plan d'un royaume. */
    public static final class Plan {
        private final Map<ResourceLocation, Long2ByteOpenHashMap> zones = new LinkedHashMap<>();
        private final List<Arrow> arrows = new ArrayList<>();
        private final List<Pin> pins = new ArrayList<>();

        /** Les zones d'une dimension : chunk → rang de {@link Zone}. */
        public Long2ByteMap zones(ResourceLocation dim) {
            Long2ByteOpenHashMap m = zones.get(dim);
            return m == null ? new Long2ByteOpenHashMap() : m;
        }

        public Collection<ResourceLocation> dimensions() {
            return zones.keySet();
        }

        public List<Arrow> arrows() {
            return Collections.unmodifiableList(arrows);
        }

        public List<Pin> pins() {
            return Collections.unmodifiableList(pins);
        }

        /** Peint ces chunks ({@code zone} nulle : les efface). Renvoie le nombre de chunks changés. */
        public int paint(ResourceLocation dim, long[] cells, Zone zone) {
            Long2ByteOpenHashMap m = zones.computeIfAbsent(dim, d -> new Long2ByteOpenHashMap());
            int changed = 0;
            for (long k : cells) {
                if (zone == null) {
                    if (m.containsKey(k)) {
                        m.remove(k);
                        changed++;
                    }
                } else if (!m.containsKey(k) || m.get(k) != (byte) zone.ordinal()) {
                    m.put(k, (byte) zone.ordinal());
                    changed++;
                }
            }
            if (m.isEmpty()) zones.remove(dim);
            return changed;
        }

        public boolean addArrow(Arrow a) {
            if (arrows.size() >= MAX_ARROWS) return false;
            arrows.add(a);
            return true;
        }

        public boolean addPin(Pin p) {
            if (pins.size() >= MAX_PINS) return false;
            pins.add(p);
            return true;
        }

        /** Retire la flèche ou le repère de cet identifiant. */
        public boolean remove(UUID id) {
            return arrows.removeIf(a -> a.id().equals(id)) | pins.removeIf(p -> p.id().equals(id));
        }

        public void clear() {
            zones.clear();
            arrows.clear();
            pins.clear();
        }

        public boolean isEmpty() {
            return zones.isEmpty() && arrows.isEmpty() && pins.isEmpty();
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            ListTag z = new ListTag();
            for (Map.Entry<ResourceLocation, Long2ByteOpenHashMap> e : zones.entrySet()) {
                long[] keys = e.getValue().keySet().toLongArray();
                Arrays.sort(keys);
                byte[] kinds = new byte[keys.length];
                for (int i = 0; i < keys.length; i++) kinds[i] = e.getValue().get(keys[i]);
                CompoundTag d = new CompoundTag();
                d.putString("dim", e.getKey().toString());
                d.put("keys", new LongArrayTag(keys));
                d.putByteArray("kinds", kinds);
                z.add(d);
            }
            t.put("zones", z);
            ListTag a = new ListTag();
            for (Arrow x : arrows) {
                CompoundTag d = new CompoundTag();
                d.putUUID("id", x.id());
                d.putString("dim", x.dim().toString());
                d.putIntArray("pts", new int[]{x.x0(), x.z0(), x.x1(), x.z1()});
                a.add(d);
            }
            t.put("arrows", a);
            ListTag p = new ListTag();
            for (Pin x : pins) {
                CompoundTag d = new CompoundTag();
                d.putUUID("id", x.id());
                d.putString("dim", x.dim().toString());
                d.putInt("x", x.x());
                d.putInt("z", x.z());
                d.putString("kind", x.kind().name());
                d.putString("note", x.note());
                p.add(d);
            }
            t.put("pins", p);
            return t;
        }

        static Plan load(CompoundTag t) {
            Plan plan = new Plan();
            ListTag z = t.getList("zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < z.size(); i++) {
                CompoundTag d = z.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(d.getString("dim"));
                if (dim == null) continue;
                long[] keys = d.getLongArray("keys");
                byte[] kinds = d.getByteArray("kinds");
                Long2ByteOpenHashMap m = new Long2ByteOpenHashMap();
                for (int k = 0; k < keys.length && k < kinds.length; k++) {
                    if (kinds[k] >= 0 && kinds[k] < Zone.values().length) m.put(keys[k], kinds[k]);
                }
                if (!m.isEmpty()) plan.zones.put(dim, m);
            }
            ListTag a = t.getList("arrows", Tag.TAG_COMPOUND);
            for (int i = 0; i < a.size(); i++) {
                CompoundTag d = a.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(d.getString("dim"));
                int[] pts = d.getIntArray("pts");
                if (dim != null && pts.length == 4) plan.arrows.add(new Arrow(d.getUUID("id"), dim, pts[0], pts[1], pts[2], pts[3]));
            }
            ListTag p = t.getList("pins", Tag.TAG_COMPOUND);
            for (int i = 0; i < p.size(); i++) {
                CompoundTag d = p.getCompound(i);
                ResourceLocation dim = ResourceLocation.tryParse(d.getString("dim"));
                Mark kind;
                try {
                    kind = Mark.valueOf(d.getString("kind"));
                } catch (IllegalArgumentException e) {
                    kind = Mark.NOTE;
                }
                if (dim != null) plan.pins.add(new Pin(d.getUUID("id"), dim, d.getInt("x"), d.getInt("z"), kind, d.getString("note")));
            }
            return plan;
        }
    }

    private final Map<UUID, Plan> plans = new HashMap<>();

    /** Le plan de ce royaume (créé s'il manque). */
    public Plan plan(UUID realm) {
        return plans.computeIfAbsent(realm, k -> new Plan());
    }

    public Optional<Plan> find(UUID realm) {
        return Optional.ofNullable(plans.get(realm));
    }

    public Map<UUID, Plan> all() {
        return Collections.unmodifiableMap(plans);
    }

    /** Un royaume disparu : son plan aussi. */
    public void forget(UUID realm) {
        plans.remove(realm);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Plan> e : plans.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag d = e.getValue().save();
            d.putUUID("realm", e.getKey());
            list.add(d);
        }
        t.put("plans", list);
        return t;
    }

    public static WarMap load(CompoundTag t) {
        WarMap m = new WarMap();
        ListTag list = t.getList("plans", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag d = list.getCompound(i);
            if (d.hasUUID("realm")) m.plans.put(d.getUUID("realm"), Plan.load(d));
        }
        return m;
    }
}
