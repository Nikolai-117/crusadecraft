package fr.crusadecraft.core.world;

import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un royaume IA : une donnée, jamais une colonie MineColonies. Il a un souverain, un conseil et une maison
 * virtuels, une personnalité, des provinces aux sièges ancrés dans le monde, et une économie simulée.
 */
public final class AiRealm {
    private final UUID id;
    private final String place;
    private final TitleRank rank;
    private final int color;
    private final String house;
    private final AiPersonality personality;
    private AiCharacter ruler;
    private final Map<Post, AiCharacter> council = new EnumMap<>(Post.class);
    private final List<AiProvince> provinces = new ArrayList<>();
    private long gold;
    private int prestige;
    private int piety;
    /** Revenu du dernier jour simulé (sert aux tributs et aux impôts des vassaux). */
    private long lastIncome;
    private final long foundedDay;
    /** Joueurs qui ont découvert ce royaume, et le jour de la découverte. */
    private final Map<UUID, Long> discoveredBy = new LinkedHashMap<>();

    public AiRealm(UUID id, String place, TitleRank rank, int color, String house, AiPersonality personality,
                   AiCharacter ruler, long foundedDay) {
        this.id = id;
        this.place = place;
        this.rank = rank;
        this.color = color;
        this.house = house;
        this.personality = personality;
        this.ruler = ruler;
        this.foundedDay = foundedDay;
    }

    public UUID id() { return id; }
    /** Le nom de lieu qui donne son nom au royaume (« Valcourt » pour « Comté de Valcourt »). */
    public String place() { return place; }
    public TitleRank rank() { return rank; }
    public int color() { return color; }
    public String house() { return house; }
    public AiPersonality personality() { return personality; }
    public AiCharacter ruler() { return ruler; }
    public Map<Post, AiCharacter> council() { return council; }
    public List<AiProvince> provinces() { return provinces; }
    public long gold() { return gold; }
    public int prestige() { return prestige; }
    public int piety() { return piety; }
    public long foundedDay() { return foundedDay; }
    public Map<UUID, Long> discoveredBy() { return Collections.unmodifiableMap(discoveredBy); }

    public void setRuler(AiCharacter ruler) { this.ruler = ruler; }
    public void setGold(long gold) { this.gold = gold; }
    public void setPrestige(int prestige) { this.prestige = prestige; }
    public void setPiety(int piety) { this.piety = piety; }
    public long lastIncome() { return lastIncome; }
    public void setLastIncome(long income) { this.lastIncome = income; }

    public boolean discoveredBy(UUID player) {
        return discoveredBy.containsKey(player);
    }

    /** Renvoie vrai si c'est une nouvelle découverte. */
    public boolean discover(UUID player, long day) {
        return discoveredBy.putIfAbsent(player, day) == null;
    }

    public AiProvince capital() {
        for (AiProvince p : provinces) if (p.capital()) return p;
        return provinces.isEmpty() ? null : provinces.get(0);
    }

    /** Les provinces qu'il tient encore (les autres ont été conquises). */
    public java.util.List<AiProvince> ownProvinces() {
        java.util.List<AiProvince> out = new java.util.ArrayList<>();
        for (AiProvince p : provinces) if (p.holder() == null || p.holder().equals(id)) out.add(p);
        return out;
    }

    /** Un royaume sans plus aucune terre. */
    public boolean landless() {
        return ownProvinces().isEmpty();
    }

    public int population() {
        int sum = 0;
        for (AiProvince p : ownProvinces()) sum += p.population();
        return sum;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("place", place);
        t.putString("rank", rank.name());
        t.putInt("color", color);
        t.putString("house", house);
        t.putString("personality", personality.name());
        t.put("ruler", ruler.save());
        CompoundTag c = new CompoundTag();
        for (Map.Entry<Post, AiCharacter> e : council.entrySet()) c.put(e.getKey().name(), e.getValue().save());
        t.put("council", c);
        ListTag p = new ListTag();
        for (AiProvince prov : provinces) p.add(prov.save());
        t.put("provinces", p);
        t.putLong("gold", gold);
        t.putInt("prestige", prestige);
        t.putInt("piety", piety);
        t.putLong("income", lastIncome);
        t.putLong("founded", foundedDay);
        CompoundTag d = new CompoundTag();
        for (Map.Entry<UUID, Long> e : discoveredBy.entrySet()) d.putLong(e.getKey().toString(), e.getValue());
        t.put("discovered", d);
        return t;
    }

    public static AiRealm load(CompoundTag t) {
        AiRealm r = new AiRealm(t.getUUID("id"), t.getString("place"), TitleRank.byName(t.getString("rank"), TitleRank.COMTE),
                t.getInt("color"), t.getString("house"), AiPersonality.byName(t.getString("personality")),
                AiCharacter.load(t.getCompound("ruler")), t.getLong("founded"));
        CompoundTag c = t.getCompound("council");
        for (String k : c.getAllKeys()) {
            Post post = Post.byName(k);
            if (post != null) r.council.put(post, AiCharacter.load(c.getCompound(k)));
        }
        ListTag p = t.getList("provinces", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) r.provinces.add(AiProvince.load(p.getCompound(i)));
        r.gold = t.getLong("gold");
        r.prestige = t.getInt("prestige");
        r.piety = t.getInt("piety");
        r.lastIncome = t.getLong("income");
        CompoundTag d = t.getCompound("discovered");
        for (String k : d.getAllKeys()) {
            try {
                r.discoveredBy.put(UUID.fromString(k), d.getLong(k));
            } catch (IllegalArgumentException ignored) {
                // clé illisible : ignorée
            }
        }
        return r;
    }
}
