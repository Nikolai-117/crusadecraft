package fr.crusadecraft.core.city;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un bâtiment d'une ville (P38), délimité au bâton d'intendant ou reconnu depuis sa porte : sa boîte, l'endroit où
 * ses gens se tiennent, sa fonction (ou la fonction proposée d'après ses meubles, tant qu'on n'a rien choisi) et les
 * personnes du registre qui l'occupent.
 */
public final class CityBuilding {
    private final UUID id;
    private BlockPos min, max;
    private BlockPos spot;
    private BuildingFunction function;
    private BuildingFunction suggestion;
    private String name = "";
    /** Cases de sol où l'on tient debout, et nombre d'étages. */
    private int floor, levels;
    /** Les meubles comptés, par sorte (« enclume » → 1). */
    private final Map<String, Integer> furniture = new LinkedHashMap<>();
    private final List<UUID> occupants = new ArrayList<>();

    public CityBuilding(UUID id, BlockPos min, BlockPos max, BlockPos spot) {
        this.id = id;
        setBox(min, max);
        this.spot = spot.immutable();
    }

    public UUID id() { return id; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public BlockPos spot() { return spot; }
    public BuildingFunction function() { return function; }
    public BuildingFunction suggestion() { return suggestion; }
    public String name() { return name; }
    public int floor() { return floor; }
    public int levels() { return levels; }
    public Map<String, Integer> furniture() { return furniture; }
    public List<UUID> occupants() { return occupants; }

    public void setBox(BlockPos a, BlockPos b) {
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public void setSpot(BlockPos spot) { this.spot = spot.immutable(); }
    public void setFunction(BuildingFunction function) { this.function = function; }
    public void setSuggestion(BuildingFunction suggestion) { this.suggestion = suggestion; }
    public void setName(String name) { this.name = name == null ? "" : name; }

    public void setStats(int floor, int levels, Map<String, Integer> furniture) {
        this.floor = floor;
        this.levels = levels;
        this.furniture.clear();
        this.furniture.putAll(furniture);
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    /** La part de la plus petite des deux boîtes qui est commune aux deux (au sol). */
    public double overlap(BlockPos omin, BlockPos omax) {
        int x = Math.min(max.getX(), omax.getX()) - Math.max(min.getX(), omin.getX()) + 1;
        int z = Math.min(max.getZ(), omax.getZ()) - Math.max(min.getZ(), omin.getZ()) + 1;
        int y = Math.min(max.getY(), omax.getY()) - Math.max(min.getY(), omin.getY()) + 1;
        if (x <= 0 || z <= 0 || y <= 0) return 0;
        long a = (long) (max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1);
        long b = (long) (omax.getX() - omin.getX() + 1) * (omax.getZ() - omin.getZ() + 1);
        return (double) x * z / Math.max(1, Math.min(a, b));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.put("min", NbtUtils.writeBlockPos(min));
        t.put("max", NbtUtils.writeBlockPos(max));
        t.put("spot", NbtUtils.writeBlockPos(spot));
        if (function != null) t.putString("function", function.name());
        if (suggestion != null) t.putString("suggestion", suggestion.name());
        if (!name.isEmpty()) t.putString("name", name);
        t.putInt("floor", floor);
        t.putInt("levels", levels);
        CompoundTag f = new CompoundTag();
        for (Map.Entry<String, Integer> e : furniture.entrySet()) f.putInt(e.getKey(), e.getValue());
        t.put("furniture", f);
        ListTag o = new ListTag();
        for (UUID u : occupants) o.add(NbtUtils.createUUID(u));
        t.put("occupants", o);
        return t;
    }

    public static CityBuilding load(CompoundTag t) {
        CityBuilding b = new CityBuilding(t.getUUID("id"), NbtUtils.readBlockPos(t.getCompound("min")), NbtUtils.readBlockPos(t.getCompound("max")),
                NbtUtils.readBlockPos(t.getCompound("spot")));
        b.function = BuildingFunction.byName(t.getString("function"));
        b.suggestion = BuildingFunction.byName(t.getString("suggestion"));
        b.name = t.getString("name");
        b.floor = t.getInt("floor");
        b.levels = t.getInt("levels");
        CompoundTag f = t.getCompound("furniture");
        for (String k : f.getAllKeys()) b.furniture.put(k, f.getInt(k));
        for (Tag u : t.getList("occupants", Tag.TAG_INT_ARRAY)) b.occupants.add(NbtUtils.loadUUID(u));
        return b;
    }
}
