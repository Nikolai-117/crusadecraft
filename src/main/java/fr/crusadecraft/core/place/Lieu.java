package fr.crusadecraft.core.place;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * Un lieu du monde : un titre (royaume, duché, comté…) ou une place (ville, fort, lieu saint…), rangé sous un autre.
 *
 * <p>La plupart des lieux <b>renvoient</b> à ce qui existe déjà ({@link Ref}) : un royaume IA, une province, un
 * royaume de joueur, une colonie, une construction. Leur nom, leur position et leur tenancier se lisent alors là-bas,
 * à chaque fois : le lieu n'en garde qu'une copie, pour le cas où la chose disparaîtrait. Ce que le lieu ajoute, c'est
 * sa place dans la hiérarchie et sa <b>fiche</b>, le texte qu'écrit le joueur.
 *
 * <p>Les autres sont posés <b>à la main</b> (lieu saint, ruine, région, titre qui regroupe d'autres lieux) et portent
 * eux-mêmes leur nom, leur position et leur tenancier.
 */
public final class Lieu {
    /** Longueur maximale d'une fiche. */
    public static final int MAX_TEXT = 2000;
    public static final int MAX_NAME = 48;

    /** Ce à quoi un lieu renvoie. */
    public enum RefType { AI_REALM, AI_PROVINCE, PLAYER_REALM, COLONY, SITE }

    /**
     * Un renvoi. Les royaumes et les constructions se désignent par leur identifiant ; une province par son numéro ;
     * une colonie par sa dimension et son numéro (les numéros de colonie ne sont uniques que par dimension).
     */
    public record Ref(RefType type, UUID uuid, int index, ResourceLocation dim) {
        public static Ref aiRealm(UUID id) { return new Ref(RefType.AI_REALM, id, 0, null); }
        public static Ref aiProvince(int id) { return new Ref(RefType.AI_PROVINCE, null, id, null); }
        public static Ref playerRealm(UUID id) { return new Ref(RefType.PLAYER_REALM, id, 0, null); }
        public static Ref colony(ResourceLocation dim, int id) { return new Ref(RefType.COLONY, null, id, dim); }
        public static Ref site(UUID id) { return new Ref(RefType.SITE, id, 0, null); }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("type", type.name());
            if (uuid != null) t.putUUID("uuid", uuid);
            t.putInt("index", index);
            if (dim != null) t.putString("dim", dim.toString());
            return t;
        }

        static Ref load(CompoundTag t) {
            RefType type;
            try {
                type = RefType.valueOf(t.getString("type"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return new Ref(type, t.hasUUID("uuid") ? t.getUUID("uuid") : null, t.getInt("index"),
                    t.contains("dim") ? ResourceLocation.tryParse(t.getString("dim")) : null);
        }
    }

    private final UUID id;
    private LieuKind kind;
    private Ref ref;
    /** Le nom (posé à la main), ou la dernière copie du nom de la chose renvoyée. */
    private String name;
    /** Le lieu dont il relève, choisi à la main ; {@code null} : celui que le monde donne (ou aucun). */
    private UUID parent;
    /** Le royaume qui tient un lieu posé à la main ; {@code null} : personne. */
    private UUID holder;
    private ResourceLocation dimension;
    private BlockPos anchor;
    /** Le rayon d'une région, en blocs. */
    private int radius;
    private String text = "";
    private long foundedDay;
    /** Le nom est un nom de lieu à composer avec le genre (« Normandie » → « Duché de Normandie »). */
    private boolean composed;
    /** La chose renvoyée a disparu ; le lieu ne reste que pour sa fiche. */
    private boolean detached;

    public Lieu(UUID id, LieuKind kind, Ref ref, String name) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.ref = ref;
        this.name = name == null ? "" : name;
    }

    public UUID id() { return id; }
    public LieuKind kind() { return kind; }
    public Ref ref() { return ref; }
    public boolean linked() { return ref != null; }
    public String name() { return name; }
    public UUID parent() { return parent; }
    public UUID holder() { return holder; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos anchor() { return anchor; }
    public int radius() { return radius; }
    public String text() { return text; }
    public long foundedDay() { return foundedDay; }
    public boolean composed() { return composed; }
    public boolean detached() { return detached; }

    public void setKind(LieuKind kind) { this.kind = Objects.requireNonNull(kind); }
    public void setName(String name) { this.name = clip(name, MAX_NAME); }
    public void setParent(UUID parent) { this.parent = parent; }
    public void setHolder(UUID holder) { this.holder = holder; }
    public void setRadius(int radius) { this.radius = Math.max(0, Math.min(2048, radius)); }
    public void setText(String text) { this.text = clip(text, MAX_TEXT); }
    public void setFoundedDay(long day) { this.foundedDay = day; }
    public void setComposed(boolean composed) { this.composed = composed; }

    public void setPlace(ResourceLocation dimension, BlockPos anchor) {
        this.dimension = dimension;
        this.anchor = anchor == null ? null : anchor.immutable();
    }

    /** La chose renvoyée a disparu : le lieu devient un lieu posé à la main, qui garde son nom et sa fiche. */
    public void detach() {
        this.ref = null;
        this.detached = true;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        String t = s.replace('§', ' ');
        return t.length() > max ? t.substring(0, max) : t;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.name());
        if (ref != null) t.put("ref", ref.save());
        t.putString("name", name);
        if (parent != null) t.putUUID("parent", parent);
        if (holder != null) t.putUUID("holder", holder);
        if (dimension != null) t.putString("dim", dimension.toString());
        if (anchor != null) t.put("anchor", NbtUtils.writeBlockPos(anchor));
        if (radius > 0) t.putInt("radius", radius);
        if (!text.isEmpty()) t.putString("text", text);
        t.putLong("founded", foundedDay);
        if (composed) t.putBoolean("composed", true);
        if (detached) t.putBoolean("detached", true);
        return t;
    }

    public static Lieu load(CompoundTag t) {
        Ref ref = t.contains("ref") ? Ref.load(t.getCompound("ref")) : null;
        Lieu l = new Lieu(t.getUUID("id"), LieuKind.byName(t.getString("kind")), ref, t.getString("name"));
        if (t.hasUUID("parent")) l.parent = t.getUUID("parent");
        if (t.hasUUID("holder")) l.holder = t.getUUID("holder");
        if (t.contains("dim")) l.dimension = ResourceLocation.tryParse(t.getString("dim"));
        if (t.contains("anchor")) l.anchor = NbtUtils.readBlockPos(t.getCompound("anchor"));
        l.radius = t.getInt("radius");
        l.text = t.getString("text");
        l.foundedDay = t.getLong("founded");
        l.composed = t.getBoolean("composed");
        l.detached = t.getBoolean("detached");
        return l;
    }
}
