package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ce qu'on règle pour toute une ville (P46), dans son panneau : ce qui y est permis, ses noms, ses tenues. */
public final class CitySettings {
    /** Toute la ville, dans les réserves de tenues (les autres clés sont des noms de {@code BuildingFunction}). */
    public static final String ALL = "*";

    public boolean canMarry = true, canTravel = true, canLeave = true, canSpeak = true;
    /** Au plus, tant de gens ; zéro, le plafond du mod. */
    public int peopleMax;
    /** Ses propres prénoms et noms de famille ; vides, ceux de la culture de sa nation. */
    public final List<String> men = new ArrayList<>(), women = new ArrayList<>(), families = new ArrayList<>();
    /** Les tenues et les skins permis, par métier (ou pour toute la ville, sous {@link #ALL}). */
    public final Map<String, List<String>> pools = new LinkedHashMap<>();

    public boolean isDefault() {
        return canMarry && canTravel && canLeave && canSpeak && peopleMax == 0 && men.isEmpty() && women.isEmpty() && families.isEmpty() && pools.isEmpty();
    }

    /** Ce que porte quelqu'un de ce métier : les tenues de son métier, à défaut celles de toute la ville. */
    public List<String> poolFor(String job) {
        List<String> own = job == null || job.isEmpty() ? null : pools.get(job);
        if (own != null && !own.isEmpty()) return own;
        return pools.getOrDefault(ALL, List.of());
    }

    private static ListTag list(List<String> values) {
        ListTag out = new ListTag();
        for (String s : values) out.add(StringTag.valueOf(s));
        return out;
    }

    private static void read(ListTag from, List<String> into) {
        for (int i = 0; i < from.size(); i++) into.add(from.getString(i));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("marry", canMarry);
        t.putBoolean("travel", canTravel);
        t.putBoolean("leave", canLeave);
        t.putBoolean("speak", canSpeak);
        t.putInt("max", peopleMax);
        t.put("men", list(men));
        t.put("women", list(women));
        t.put("families", list(families));
        CompoundTag p = new CompoundTag();
        for (Map.Entry<String, List<String>> e : pools.entrySet()) p.put(e.getKey(), list(e.getValue()));
        t.put("pools", p);
        return t;
    }

    public static CitySettings load(CompoundTag t) {
        CitySettings s = new CitySettings();
        s.canMarry = !t.contains("marry") || t.getBoolean("marry");
        s.canTravel = !t.contains("travel") || t.getBoolean("travel");
        s.canLeave = !t.contains("leave") || t.getBoolean("leave");
        s.canSpeak = !t.contains("speak") || t.getBoolean("speak");
        s.peopleMax = t.getInt("max");
        read(t.getList("men", Tag.TAG_STRING), s.men);
        read(t.getList("women", Tag.TAG_STRING), s.women);
        read(t.getList("families", Tag.TAG_STRING), s.families);
        CompoundTag p = t.getCompound("pools");
        for (String k : p.getAllKeys()) {
            List<String> out = new ArrayList<>();
            read(p.getList(k, Tag.TAG_STRING), out);
            s.pools.put(k, out);
        }
        return s;
    }
}
