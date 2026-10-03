package fr.crusadecraft.core.lineage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Le registre des lignées (P13) : les personnes, les maisons et les communautés du monde. */
public final class LineageState {
    private final Map<UUID, Person> persons = new LinkedHashMap<>();
    private final Map<UUID, House> houses = new LinkedHashMap<>();
    private final Map<UUID, Community> communities = new LinkedHashMap<>();

    public Collection<Person> persons() { return persons.values(); }
    public Collection<House> houses() { return houses.values(); }
    public Collection<Community> communities() { return communities.values(); }

    public Optional<Person> person(UUID id) { return Optional.ofNullable(id == null ? null : persons.get(id)); }
    public Optional<House> house(UUID id) { return Optional.ofNullable(id == null ? null : houses.get(id)); }
    public Optional<Community> community(UUID id) { return Optional.ofNullable(id == null ? null : communities.get(id)); }

    public void add(Person p) { persons.put(p.id(), p); }

    /** Vide le registre (banc d'essai). */
    public void clear() {
        persons.clear();
        houses.clear();
        communities.clear();
    }
    public void add(House h) { houses.put(h.id(), h); }
    public void add(Community c) { communities.put(c.id(), c); }

    /** La maison qui règne sur ce royaume, s'il y en a une. */
    public Optional<House> houseOfRealm(UUID realm) {
        for (House h : houses.values()) if (realm != null && realm.equals(h.realm())) return Optional.of(h);
        return Optional.empty();
    }

    /** Les membres d'une maison par le sang (sans les conjoints venus d'ailleurs). */
    public List<Person> members(UUID house) {
        List<Person> out = new ArrayList<>();
        for (Person p : persons.values()) if (house.equals(p.house())) out.add(p);
        return out;
    }

    public List<Person> residents(UUID community) {
        List<Person> out = new ArrayList<>();
        for (Person p : persons.values()) if (community.equals(p.community())) out.add(p);
        return out;
    }

    public List<Person> children(UUID parent) {
        List<Person> out = new ArrayList<>();
        for (Person p : persons.values()) if (p.isChildOf(parent)) out.add(p);
        return out;
    }

    /**
     * La maison rangée par générations : la première est celle des ancêtres sans parents connus dans la maison, chaque
     * enfant vient une génération après le plus jeune de ses parents, un conjoint venu d'ailleurs se range avec son
     * époux. Dans chaque génération, les aînés d'abord.
     */
    public java.util.NavigableMap<Integer, List<Person>> generations(UUID house) {
        Map<UUID, Integer> gen = new HashMap<>();
        List<Person> members = members(house);
        for (Person p : members) generation(p, house, gen, 0);
        TreeMap<Integer, List<Person>> out = new TreeMap<>();
        for (Person p : members) {
            out.computeIfAbsent(gen.get(p.id()), k -> new ArrayList<>()).add(p);
            person(p.spouse()).filter(s -> !house.equals(s.house()))
                    .ifPresent(s -> out.computeIfAbsent(gen.get(p.id()), k -> new ArrayList<>()).add(s));
        }
        for (List<Person> row : out.values()) row.sort(Comparator.comparingInt(Person::age).reversed());
        return out;
    }

    private int generation(Person p, UUID house, Map<UUID, Integer> gen, int depth) {
        Integer known = gen.get(p.id());
        if (known != null) return known;
        int g = 0;
        if (depth < 32) {
            for (UUID parentId : new UUID[]{p.father(), p.mother()}) {
                Optional<Person> parent = person(parentId);
                if (parent.isPresent() && house.equals(parent.get().house())) g = Math.max(g, generation(parent.get(), house, gen, depth + 1) + 1);
            }
        }
        gen.put(p.id(), g);
        return g;
    }

    /** « Aude d'Aubépine », « Raoul Lefèvre », ou le seul prénom. */
    public String fullName(Person p) {
        Optional<House> h = house(p.house());
        if (h.isPresent()) return p.name() + (elides(h.get().name()) ? " d'" : " de ") + h.get().name();
        return p.surname().isEmpty() ? p.name() : p.name() + " " + p.surname();
    }

    static boolean elides(String place) {
        if (place == null || place.isEmpty()) return false;
        char c = Character.toLowerCase(place.charAt(0));
        return "aeiouyéèêâîôûh".indexOf(c) >= 0 && !place.toLowerCase(java.util.Locale.ROOT).startsWith("hau")
                && !place.toLowerCase(java.util.Locale.ROOT).startsWith("hêt");
    }

    /** Retire une personne et tous les liens qui la désignent. */
    public void remove(UUID id) {
        if (persons.remove(id) == null) return;
        for (Person p : persons.values()) {
            if (id.equals(p.spouse())) p.setSpouse(null);
            if (id.equals(p.father())) p.setFather(null);
            if (id.equals(p.mother())) p.setMother(null);
        }
        for (House h : houses.values()) if (id.equals(h.head())) h.setHead(null);
    }

    /** Retire une maison, ses membres et les conjoints qui n'appartiennent à rien d'autre. Renvoie les personnes retirées. */
    public List<UUID> removeHouse(UUID house) {
        List<UUID> gone = new ArrayList<>();
        if (houses.remove(house) == null) return gone;
        for (Person p : members(house)) {
            gone.add(p.id());
            person(p.spouse()).filter(s -> s.house() == null && s.community() == null).ifPresent(s -> gone.add(s.id()));
        }
        for (UUID id : gone) remove(id);
        return gone;
    }

    public List<UUID> removeCommunity(UUID community) {
        List<UUID> gone = new ArrayList<>();
        if (communities.remove(community) == null) return gone;
        for (Person p : residents(community)) gone.add(p.id());
        for (UUID id : gone) remove(id);
        return gone;
    }

    /** Un royaume a disparu : sa maison et ses communautés ne règnent plus sur rien, ses gens ne servent plus personne. */
    public void forgetRealm(UUID realm) {
        for (House h : houses.values()) if (realm.equals(h.realm())) h.setRealm(null);
        for (Community c : communities.values()) if (realm.equals(c.realm())) c.setRealm(null);
        for (Person p : persons.values()) if (realm.equals(p.realm())) p.setRealm(null);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag p = new ListTag();
        for (Person x : persons.values()) p.add(x.save());
        t.put("persons", p);
        ListTag h = new ListTag();
        for (House x : houses.values()) h.add(x.save());
        t.put("houses", h);
        ListTag c = new ListTag();
        for (Community x : communities.values()) c.add(x.save());
        t.put("communities", c);
        return t;
    }

    public static LineageState load(CompoundTag t) {
        LineageState s = new LineageState();
        ListTag p = t.getList("persons", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) s.add(Person.load(p.getCompound(i)));
        ListTag h = t.getList("houses", Tag.TAG_COMPOUND);
        for (int i = 0; i < h.size(); i++) s.add(House.load(h.getCompound(i)));
        ListTag c = t.getList("communities", Tag.TAG_COMPOUND);
        for (int i = 0; i < c.size(); i++) s.add(Community.load(c.getCompound(i)));
        return s;
    }
}
