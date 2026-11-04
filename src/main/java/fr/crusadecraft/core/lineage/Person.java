package fr.crusadecraft.core.lineage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Une personne du registre des lignées : un membre d'une maison, son conjoint venu d'ailleurs, un habitant d'une
 * communauté. Ce n'est qu'une donnée tant qu'on ne la fait pas apparaître ; avec Comes Alive, le villageois qui
 * l'incarne porte le même identifiant, et l'arbre généalogique de Comes Alive le reconnaît.
 *
 * <p>L'âge est en années et ne bouge pas tout seul : les générations se composent à la main ou par le générateur.
 */
public final class Person {
    private final UUID id;
    private String name;
    /** Le nom de famille d'un roturier (« Lefèvre ») ; les nobles portent celui de leur maison. */
    private String surname = "";
    private boolean female;
    private int age;
    private boolean alive = true;
    private Role role = Role.AUCUN;
    /** La maison à laquelle elle appartient par le sang (ou par choix) ; {@code null} pour un roturier. */
    private UUID house;
    private UUID father, mother, spouse;
    /** Le royaume qu'elle sert (IA ou joueur), s'il y en a un. */
    private UUID realm;
    /** Ses traits de caractère (P20), sans contraires ; et ce que Romain écrit de lui. */
    private final java.util.List<fr.crusadecraft.core.character.Trait> traits = new java.util.ArrayList<>();
    private String bio = "";
    private UUID community;
    /** Un villageois l'incarne dans le monde (Comes Alive) : dernière position connue. */
    private ResourceLocation worldDim;
    private BlockPos worldPos;
    /** P38 : où elle passe la journée (sa boutique, la place de sa ville), au lieu de la cour ; {@code null} : la cour. */
    private BlockPos work;

    /** P42 : son métier dans sa ville (le nom d'une {@code BuildingFunction}), vide s'il n'en a pas. */
    private String job = "";
    /** P42 : la ville où il vit (identifiant de la construction), s'il vit dans une ville. */
    private UUID site;
    /** P42 : la clé de son personnage écrit d'avance (« levanzia/dolhimluith/ilaria »), vide pour un habitant ordinaire. */
    private String notable = "";
    /** P42 : ce que cette personne pense de chaque joueur, de -100 à 100. */
    private final java.util.Map<UUID, Integer> opinions = new java.util.HashMap<>();
    /** P43 : ses économies, et sa faim (0 : repu ; 100 : affamé). */
    private int coins;
    private int hunger;

    public String job() { return job; }
    public void setJob(String job) { this.job = job == null ? "" : job; }
    public UUID site() { return site; }
    public void setSite(UUID site) { this.site = site; }
    public String notable() { return notable; }
    public void setNotable(String notable) { this.notable = notable == null ? "" : notable; }
    public int coins() { return coins; }
    public void setCoins(int coins) { this.coins = Math.max(0, coins); }
    public int hunger() { return hunger; }
    public void setHunger(int hunger) { this.hunger = Math.max(0, Math.min(100, hunger)); }

    /** Ce qu'elle pense de ce joueur. */
    public int opinionOf(UUID player) {
        return opinions.getOrDefault(player, 0);
    }

    /** Elle change d'avis sur ce joueur. */
    public void addOpinion(UUID player, int delta) {
        int v = Math.max(-100, Math.min(100, opinionOf(player) + delta));
        if (v == 0) opinions.remove(player);
        else opinions.put(player, v);
    }

    /** Le temps passe : les rancunes et les amitiés s'effacent peu à peu. */
    public void forgetABit() {
        opinions.replaceAll((k, v) -> v > 0 ? v - 1 : v < 0 ? v + 1 : 0);
        opinions.values().removeIf(v -> v == 0);
    }

    /** Traits au plus, et longueur de la biographie. */
    public static final int MAX_TRAITS = 4, BIO_MAX = 300;

    public Person(UUID id, String name, boolean female, int age) {
        this.id = id;
        this.name = name;
        this.female = female;
        this.age = Math.max(0, age);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String surname() { return surname; }
    public boolean female() { return female; }
    public int age() { return age; }
    public boolean alive() { return alive; }
    public Role role() { return role; }
    public UUID house() { return house; }
    public UUID father() { return father; }
    public UUID mother() { return mother; }
    public UUID spouse() { return spouse; }
    public UUID realm() { return realm; }
    public java.util.List<fr.crusadecraft.core.character.Trait> traits() { return java.util.Collections.unmodifiableList(traits); }
    public String bio() { return bio; }

    /** Les traits, en écartant un trait contraire à un trait déjà retenu, et au plus {@code max}. */
    public void setTraits(java.util.Collection<fr.crusadecraft.core.character.Trait> wanted, int max) {
        traits.clear();
        for (fr.crusadecraft.core.character.Trait t : wanted) {
            if (traits.size() >= max) break;
            boolean clash = false;
            for (fr.crusadecraft.core.character.Trait k : traits) if (k.conflictsWith(t)) clash = true;
            if (!clash) traits.add(t);
        }
    }

    public void setBio(String bio) { this.bio = bio == null ? "" : bio; }

    /** Les traits en masque de bits (rang dans {@code Trait.values()}), pour l'écran. */
    public int traitMask() {
        int m = 0;
        for (fr.crusadecraft.core.character.Trait t : traits) m |= 1 << t.ordinal();
        return m;
    }
    public UUID community() { return community; }
    public boolean inWorld() { return worldPos != null; }
    public ResourceLocation worldDim() { return worldDim; }
    public BlockPos worldPos() { return worldPos; }
    public BlockPos work() { return work; }
    public void setWork(BlockPos work) { this.work = work == null ? null : work.immutable(); }

    public void setName(String name) { this.name = name; }
    public void setSurname(String surname) { this.surname = surname == null ? "" : surname; }
    public void setFemale(boolean female) { this.female = female; }
    public void setAge(int age) { this.age = Math.max(0, Math.min(120, age)); }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setRole(Role role) { this.role = role; }
    public void setHouse(UUID house) { this.house = house; }
    public void setFather(UUID father) { this.father = father; }
    public void setMother(UUID mother) { this.mother = mother; }
    public void setSpouse(UUID spouse) { this.spouse = spouse; }
    public void setRealm(UUID realm) { this.realm = realm; }
    public void setCommunity(UUID community) { this.community = community; }

    /** Un villageois l'incarne désormais à cet endroit ; {@code null} : plus personne ne l'incarne. */
    public void setInWorld(ResourceLocation dim, BlockPos pos) {
        this.worldDim = pos == null ? null : dim;
        this.worldPos = pos == null ? null : pos.immutable();
    }

    public boolean isChildOf(UUID parent) {
        return parent != null && (parent.equals(father) || parent.equals(mother));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        if (!surname.isEmpty()) t.putString("surname", surname);
        t.putBoolean("female", female);
        t.putInt("age", age);
        t.putBoolean("alive", alive);
        t.putString("role", role.name());
        put(t, "house", house);
        put(t, "father", father);
        put(t, "mother", mother);
        put(t, "spouse", spouse);
        put(t, "realm", realm);
        put(t, "community", community);
        if (!traits.isEmpty()) {
            net.minecraft.nbt.ListTag tl = new net.minecraft.nbt.ListTag();
            for (fr.crusadecraft.core.character.Trait k : traits) tl.add(net.minecraft.nbt.StringTag.valueOf(k.name()));
            t.put("traits", tl);
        }
        if (!bio.isEmpty()) t.putString("bio", bio);
        if (worldPos != null) {
            t.putString("worldDim", worldDim.toString());
            t.put("worldPos", NbtUtils.writeBlockPos(worldPos));
        }
        if (work != null) t.put("work", NbtUtils.writeBlockPos(work));
        if (!job.isEmpty()) t.putString("job", job);
        put(t, "site", site);
        if (!notable.isEmpty()) t.putString("notable", notable);
        if (coins != 0) t.putInt("coins", coins);
        if (hunger != 0) t.putInt("hunger", hunger);
        if (!opinions.isEmpty()) {
            CompoundTag o = new CompoundTag();
            for (java.util.Map.Entry<UUID, Integer> e : opinions.entrySet()) o.putInt(e.getKey().toString(), e.getValue());
            t.put("opinions", o);
        }
        return t;
    }

    private static void put(CompoundTag t, String key, UUID value) {
        if (value != null) t.putUUID(key, value);
    }

    private static UUID get(CompoundTag t, String key) {
        return t.hasUUID(key) ? t.getUUID(key) : null;
    }

    public static Person load(CompoundTag t) {
        Person p = new Person(t.getUUID("id"), t.getString("name"), t.getBoolean("female"), t.getInt("age"));
        p.surname = t.getString("surname");
        p.alive = !t.contains("alive") || t.getBoolean("alive");
        p.role = Role.byName(t.getString("role"), Role.AUCUN);
        p.house = get(t, "house");
        p.father = get(t, "father");
        p.mother = get(t, "mother");
        p.spouse = get(t, "spouse");
        p.realm = get(t, "realm");
        p.community = get(t, "community");
        net.minecraft.nbt.ListTag tl = t.getList("traits", net.minecraft.nbt.Tag.TAG_STRING);
        java.util.List<fr.crusadecraft.core.character.Trait> wanted = new java.util.ArrayList<>();
        for (int i = 0; i < tl.size(); i++) {
            try {
                wanted.add(fr.crusadecraft.core.character.Trait.valueOf(tl.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // Un trait disparu d'une version à l'autre : on l'oublie.
            }
        }
        p.setTraits(wanted, MAX_TRAITS);
        p.bio = t.getString("bio");
        if (t.contains("worldPos")) {
            p.worldDim = ResourceLocation.tryParse(t.getString("worldDim"));
            p.worldPos = NbtUtils.readBlockPos(t.getCompound("worldPos"));
        }
        if (t.contains("work")) p.work = NbtUtils.readBlockPos(t.getCompound("work"));
        p.job = t.getString("job");
        p.site = get(t, "site");
        p.notable = t.getString("notable");
        p.coins = t.getInt("coins");
        p.hunger = t.getInt("hunger");
        CompoundTag o = t.getCompound("opinions");
        for (String k : o.getAllKeys()) {
            try {
                p.opinions.put(UUID.fromString(k), o.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // clé illisible : ignorée
            }
        }
        return p;
    }
}
