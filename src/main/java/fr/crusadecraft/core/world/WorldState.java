package fr.crusadecraft.core.world;

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

/**
 * Le monde autour des royaumes des joueurs : les royaumes IA et les royaumes de jure (les grands titres
 * historiques, plus vastes que les États du moment, qui donnent la vue « de jure » de la carte).
 */
public final class WorldState {

    /** Un royaume de jure : un nom de lieu et une couleur. */
    public record DejureKingdom(String place, int color, int centerX, int centerZ) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("place", place);
            t.putInt("color", color);
            t.putInt("x", centerX);
            t.putInt("z", centerZ);
            return t;
        }

        static DejureKingdom load(CompoundTag t) {
            return new DejureKingdom(t.getString("place"), t.getInt("color"), t.getInt("x"), t.getInt("z"));
        }
    }

    /** 1 : royaumes IA (P5) ; 2 : avec les seigneuries indépendantes (P6). */
    private int version = 2;
    private boolean generated;
    private long generatedDay;
    private int centerX, centerZ;
    private final Map<UUID, AiRealm> realms = new LinkedHashMap<>();
    private final List<DejureKingdom> dejure = new ArrayList<>();
    private long lastEconomyDay = Long.MIN_VALUE;

    public boolean generated() { return generated; }
    public int version() { return version; }
    public void setVersion(int version) { this.version = version; }

    public void add(AiRealm realm) {
        realms.put(realm.id(), realm);
    }
    public long generatedDay() { return generatedDay; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public Collection<AiRealm> realms() { return realms.values(); }
    public List<DejureKingdom> dejure() { return dejure; }
    public long lastEconomyDay() { return lastEconomyDay; }

    public Optional<AiRealm> realm(UUID id) {
        return Optional.ofNullable(realms.get(id));
    }

    public void setLastEconomyDay(long day) { this.lastEconomyDay = day; }

    /** Remplace tout le monde généré (génération, ou régénération par un administrateur). */
    public void replace(List<AiRealm> newRealms, List<DejureKingdom> newDejure, int cx, int cz, long day) {
        realms.clear();
        for (AiRealm r : newRealms) realms.put(r.id(), r);
        dejure.clear();
        dejure.addAll(newDejure);
        centerX = cx;
        centerZ = cz;
        generatedDay = day;
        generated = true;
    }

    public void clear() {
        realms.clear();
        dejure.clear();
        generated = false;
    }

    /** La province IA dont le siège est le plus proche, dans la dimension donnée. */
    public Optional<Map.Entry<AiRealm, AiProvince>> nearestProvince(net.minecraft.resources.ResourceLocation dim, double x, double z) {
        Map.Entry<AiRealm, AiProvince> best = null;
        double bestD = Double.MAX_VALUE;
        for (AiRealm r : realms.values()) {
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(dim)) continue;
                double d = p.distanceSqr(x, z);
                if (d < bestD) {
                    bestD = d;
                    best = Map.entry(r, p);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("generated", generated);
        t.putInt("version", version);
        t.putLong("day", generatedDay);
        t.putInt("cx", centerX);
        t.putInt("cz", centerZ);
        ListTag r = new ListTag();
        for (AiRealm realm : realms.values()) r.add(realm.save());
        t.put("realms", r);
        ListTag d = new ListTag();
        for (DejureKingdom k : dejure) d.add(k.save());
        t.put("dejure", d);
        t.putLong("economyDay", lastEconomyDay);
        return t;
    }

    public static WorldState load(CompoundTag t) {
        WorldState s = new WorldState();
        s.generated = t.getBoolean("generated");
        s.version = t.contains("version") ? t.getInt("version") : 1;
        s.generatedDay = t.getLong("day");
        s.centerX = t.getInt("cx");
        s.centerZ = t.getInt("cz");
        ListTag r = t.getList("realms", Tag.TAG_COMPOUND);
        for (int i = 0; i < r.size(); i++) {
            AiRealm realm = AiRealm.load(r.getCompound(i));
            s.realms.put(realm.id(), realm);
        }
        ListTag d = t.getList("dejure", Tag.TAG_COMPOUND);
        for (int i = 0; i < d.size(); i++) s.dejure.add(DejureKingdom.load(d.getCompound(i)));
        s.lastEconomyDay = t.contains("economyDay") ? t.getLong("economyDay") : Long.MIN_VALUE;
        return s;
    }
}
