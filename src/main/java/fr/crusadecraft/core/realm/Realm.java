package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CourtState;
import fr.crusadecraft.core.law.LawState;
import fr.crusadecraft.core.treasury.Ledger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un royaume : un enregistrement dans les données de CrusadeCraft, jamais dans MineColonies.
 *
 * <p>Il désigne ses colonies par {@link ColonyRef}. Fonder, perdre ou dissoudre un royaume ne
 * modifie donc aucune colonie, et retirer le mod les rend exactement comme avant.
 */
public final class Realm {
    private final UUID id;
    private String name;
    private String motto;
    private String house;
    private int color;
    private UUID ruler;
    private String rulerName;
    private boolean feminineTitle;
    private ColonyRef capital;
    private final List<ColonyRef> provinces = new ArrayList<>();
    private TitleRank rank;
    private RealmState state = RealmState.ACTIVE;
    private long foundedDay;

    /** Conseil royal : un titulaire par poste. */
    private final Map<Post, CitizenRef> council = new EnumMap<>(Post.class);
    /** Maires : un par colonie du royaume. */
    private final Map<ColonyRef, CitizenRef> mayors = new LinkedHashMap<>();
    /** Table du conseil, bureaux des maires, tâches, requêtes. */
    private CourtState court = new CourtState();

    /** Lois en vigueur. */
    private LawState laws = new LawState();
    /** Livre de comptes des derniers jours. */
    private Ledger ledger = new Ledger();

    // Ressources du souverain. L'or ne bouge que par TreasuryService, qui l'inscrit au livre de comptes ;
    // il peut être négatif (dette), jusqu'à la limite fixée par la configuration.
    private long gold;
    private int prestige;
    private int piety;
    private int dread;

    public Realm(UUID id, String name, String motto, String house, int color, UUID ruler, String rulerName,
                 boolean feminineTitle, ColonyRef capital, TitleRank rank, long foundedDay) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.motto = motto;
        this.house = house;
        this.color = color;
        this.ruler = Objects.requireNonNull(ruler);
        this.rulerName = rulerName;
        this.feminineTitle = feminineTitle;
        this.capital = Objects.requireNonNull(capital);
        this.rank = rank;
        this.foundedDay = foundedDay;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String motto() { return motto; }
    public String house() { return house; }
    public int color() { return color; }
    public UUID ruler() { return ruler; }
    public String rulerName() { return rulerName; }
    public boolean feminineTitle() { return feminineTitle; }
    public ColonyRef capital() { return capital; }
    public List<ColonyRef> provinces() { return Collections.unmodifiableList(provinces); }
    public TitleRank rank() { return rank; }
    public RealmState state() { return state; }
    public long foundedDay() { return foundedDay; }
    public long gold() { return gold; }
    public int prestige() { return prestige; }
    public int piety() { return piety; }
    public int dread() { return dread; }

    /** Capitale puis provinces, sans doublon. */
    public List<ColonyRef> allColonies() {
        List<ColonyRef> all = new ArrayList<>(provinces.size() + 1);
        all.add(capital);
        for (ColonyRef p : provinces) {
            if (!p.equals(capital)) all.add(p);
        }
        return all;
    }

    public Map<Post, CitizenRef> council() { return Collections.unmodifiableMap(council); }
    public Map<ColonyRef, CitizenRef> mayors() { return Collections.unmodifiableMap(mayors); }
    public CourtState court() { return court; }
    public LawState laws() { return laws; }
    public Ledger ledger() { return ledger; }

    /** Le poste qu'occupe ce citoyen dans ce royaume, s'il en a un. */
    public Post postOf(CitizenRef citizen) {
        for (Map.Entry<Post, CitizenRef> e : council.entrySet()) {
            if (e.getValue().equals(citizen)) return e.getKey();
        }
        return mayors.containsValue(citizen) ? Post.MAIRE : null;
    }

    /** Nomme {@code citizen} ; renvoie l'ancien titulaire éventuel. Le maire est celui de sa propre colonie. */
    public CitizenRef appoint(Post post, CitizenRef citizen) {
        vacate(citizen);
        return post == Post.MAIRE ? mayors.put(citizen.colony(), citizen) : council.put(post, citizen);
    }

    /** Retire ce citoyen de tout poste ; renvoie le poste libéré. */
    public Post vacate(CitizenRef citizen) {
        Post post = postOf(citizen);
        if (post == Post.MAIRE) mayors.values().remove(citizen);
        else if (post != null) council.remove(post);
        return post;
    }

    public boolean holds(ColonyRef colony) {
        return capital.equals(colony) || provinces.contains(colony);
    }

    public void setName(String name) { this.name = name; }
    public void setMotto(String motto) { this.motto = motto; }
    public void setHouse(String house) { this.house = house; }
    public void setColor(int color) { this.color = color; }
    public void setRulerName(String rulerName) { this.rulerName = rulerName; }
    public void setFeminineTitle(boolean feminineTitle) { this.feminineTitle = feminineTitle; }
    public void setRank(TitleRank rank) { this.rank = rank; }
    public void setState(RealmState state) { this.state = state; }
    public void setPrestige(int prestige) { this.prestige = prestige; }
    public void setPiety(int piety) { this.piety = piety; }
    public void setDread(int dread) { this.dread = dread; }
    /** Valeur brute ; passer par {@code TreasuryService} pour que le mouvement soit inscrit. */
    public void setGold(long gold) { this.gold = gold; }

    public void addProvince(ColonyRef colony) {
        if (!holds(colony)) provinces.add(colony);
    }

    public void removeProvince(ColonyRef colony) {
        provinces.remove(colony);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("motto", motto);
        tag.putString("house", house);
        tag.putInt("color", color);
        tag.putUUID("ruler", ruler);
        tag.putString("rulerName", rulerName);
        tag.putBoolean("feminine", feminineTitle);
        tag.put("capital", capital.save());
        ListTag list = new ListTag();
        for (ColonyRef p : provinces) list.add(p.save());
        tag.put("provinces", list);
        tag.putString("rank", rank.name());
        tag.putString("state", state.name());
        tag.putLong("founded", foundedDay);
        ListTag councilTag = new ListTag();
        for (Map.Entry<Post, CitizenRef> e : council.entrySet()) {
            CompoundTag p = e.getValue().save();
            p.putString("post", e.getKey().name());
            councilTag.add(p);
        }
        tag.put("council", councilTag);
        ListTag mayorTag = new ListTag();
        for (CitizenRef m : mayors.values()) mayorTag.add(m.save());
        tag.put("mayors", mayorTag);
        tag.put("court", court.save());
        tag.put("laws", laws.save());
        tag.put("ledger", ledger.save());
        tag.putLong("gold", gold);
        tag.putInt("prestige", prestige);
        tag.putInt("piety", piety);
        tag.putInt("dread", dread);
        return tag;
    }

    public static Realm load(CompoundTag tag) {
        Realm realm = new Realm(
                tag.getUUID("id"),
                tag.getString("name"),
                tag.getString("motto"),
                tag.getString("house"),
                tag.getInt("color"),
                tag.getUUID("ruler"),
                tag.getString("rulerName"),
                tag.getBoolean("feminine"),
                ColonyRef.load(tag.getCompound("capital")),
                TitleRank.byName(tag.getString("rank"), TitleRank.BARONNIE),
                tag.getLong("founded"));
        ListTag list = tag.getList("provinces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            realm.addProvince(ColonyRef.load(list.getCompound(i)));
        }
        realm.state = RealmState.byName(tag.getString("state"));
        ListTag councilTag = tag.getList("council", Tag.TAG_COMPOUND);
        for (int i = 0; i < councilTag.size(); i++) {
            CompoundTag p = councilTag.getCompound(i);
            Post post = Post.byName(p.getString("post"));
            if (post != null && post.isCouncil()) realm.council.put(post, CitizenRef.load(p));
        }
        realm.court = CourtState.load(tag.getCompound("court"));
        realm.laws = LawState.load(tag.getCompound("laws"));
        realm.ledger = Ledger.load(tag, "ledger");
        ListTag mayorTag = tag.getList("mayors", Tag.TAG_COMPOUND);
        for (int i = 0; i < mayorTag.size(); i++) {
            CitizenRef m = CitizenRef.load(mayorTag.getCompound(i));
            realm.mayors.put(m.colony(), m);
        }
        realm.gold = tag.getLong("gold");
        realm.prestige = tag.getInt("prestige");
        realm.piety = tag.getInt("piety");
        realm.dread = tag.getInt("dread");
        return realm;
    }
}
