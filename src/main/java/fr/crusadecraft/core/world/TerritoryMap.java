package fr.crusadecraft.core.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les frontières peintes à la main sur la carte : pour chaque chunk peint, le royaume qui le tient, ou
 * {@link #NEUTRAL} (« terre libre », qui efface un territoire automatique). Un chunk qui n'est pas peint garde le
 * territoire automatique, un cercle autour de chaque siège.
 *
 * <p>Seize blocs de côté, c'est deux points à l'échelle habituelle de la carte : assez fin pour suivre une rivière,
 * assez grossier pour qu'un royaume entier tienne en quelques milliers d'entrées.
 */
public final class TerritoryMap {
    /** Une terre peinte comme n'appartenant à personne. */
    public static final UUID NEUTRAL = new UUID(0L, 0L);
    /** Côté d'une case, en blocs. */
    public static final int CELL = 16;

    private final Map<ResourceLocation, Long2ObjectOpenHashMap<UUID>> cells = new LinkedHashMap<>();

    public static long key(int cellX, int cellZ) {
        return (long) cellX & 0xFFFFFFFFL | ((long) cellZ & 0xFFFFFFFFL) << 32;
    }

    public static int cellX(long key) {
        return (int) key;
    }

    public static int cellZ(long key) {
        return (int) (key >>> 32);
    }

    /** La case d'une coordonnée de bloc. */
    public static int cellOf(double block) {
        return (int) Math.floor(block / CELL);
    }

    /** Ce qui est peint sous ce bloc, s'il l'est : un royaume ou {@link #NEUTRAL}. */
    public Optional<UUID> at(ResourceLocation dim, double x, double z) {
        Long2ObjectOpenHashMap<UUID> m = cells.get(dim);
        if (m == null) return Optional.empty();
        return Optional.ofNullable(m.get(key(cellOf(x), cellOf(z))));
    }

    /** Peint ces cases ; {@code owner} nul les efface (retour au territoire automatique). Renvoie le nombre de cases changées. */
    public int paint(ResourceLocation dim, long[] keys, UUID owner) {
        Long2ObjectOpenHashMap<UUID> m = cells.computeIfAbsent(dim, d -> new Long2ObjectOpenHashMap<>());
        int changed = 0;
        for (long k : keys) {
            UUID before = owner == null ? m.remove(k) : m.put(k, owner);
            if (owner == null ? before != null : !owner.equals(before)) changed++;
        }
        if (m.isEmpty()) cells.remove(dim);
        return changed;
    }

    /** Efface tout ce qu'un royaume a de peint (royaume supprimé). */
    public int forget(UUID owner) {
        int removed = 0;
        for (Long2ObjectOpenHashMap<UUID> m : cells.values()) {
            var it = m.long2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                if (owner.equals(it.next().getValue())) {
                    it.remove();
                    removed++;
                }
            }
        }
        cells.values().removeIf(Map::isEmpty);
        return removed;
    }

    /** Les cases peintes d'une dimension (à ne pas modifier). */
    public Long2ObjectMap<UUID> cells(ResourceLocation dim) {
        Long2ObjectOpenHashMap<UUID> m = cells.get(dim);
        return m == null ? new Long2ObjectOpenHashMap<>() : m;
    }

    public int size() {
        int n = 0;
        for (Long2ObjectOpenHashMap<UUID> m : cells.values()) n += m.size();
        return n;
    }

    public Collection<ResourceLocation> dimensions() {
        return cells.keySet();
    }

    /** Une liste par dimension : la palette des royaumes, les clés des cases et l'indice de leur royaume dans la palette. */
    public ListTag save() {
        ListTag out = new ListTag();
        for (Map.Entry<ResourceLocation, Long2ObjectOpenHashMap<UUID>> e : cells.entrySet()) {
            List<UUID> palette = new ArrayList<>();
            Map<UUID, Integer> index = new HashMap<>();
            long[] keys = new long[e.getValue().size()];
            int[] owners = new int[keys.length];
            int i = 0;
            for (Long2ObjectMap.Entry<UUID> c : e.getValue().long2ObjectEntrySet()) {
                keys[i] = c.getLongKey();
                owners[i] = index.computeIfAbsent(c.getValue(), u -> {
                    palette.add(u);
                    return palette.size() - 1;
                });
                i++;
            }
            CompoundTag t = new CompoundTag();
            t.putString("dim", e.getKey().toString());
            ListTag p = new ListTag();
            for (UUID u : palette) {
                CompoundTag pt = new CompoundTag();
                pt.putUUID("id", u);
                p.add(pt);
            }
            t.put("palette", p);
            t.put("keys", new LongArrayTag(keys));
            t.put("owners", new IntArrayTag(owners));
            out.add(t);
        }
        return out;
    }

    public static TerritoryMap load(ListTag list) {
        TerritoryMap map = new TerritoryMap();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null) continue;
            ListTag p = t.getList("palette", Tag.TAG_COMPOUND);
            List<UUID> palette = new ArrayList<>();
            for (int j = 0; j < p.size(); j++) palette.add(p.getCompound(j).getUUID("id"));
            long[] keys = t.getLongArray("keys");
            int[] owners = t.getIntArray("owners");
            Long2ObjectOpenHashMap<UUID> m = new Long2ObjectOpenHashMap<>();
            for (int j = 0; j < Math.min(keys.length, owners.length); j++) {
                if (owners[j] >= 0 && owners[j] < palette.size()) m.put(keys[j], palette.get(owners[j]));
            }
            if (!m.isEmpty()) map.cells.put(dim, m);
        }
        return map;
    }
}
