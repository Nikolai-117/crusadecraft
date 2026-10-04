package fr.crusadecraft.core.place;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Les lieux du monde (P15), rangés par identifiant et retrouvables par ce à quoi ils renvoient. */
public final class PlaceState {
    private final Map<UUID, Lieu> lieux = new LinkedHashMap<>();
    private final Map<Lieu.Ref, UUID> byRef = new HashMap<>();

    public Collection<Lieu> all() {
        return Collections.unmodifiableCollection(lieux.values());
    }

    public int size() {
        return lieux.size();
    }

    public Optional<Lieu> get(UUID id) {
        return Optional.ofNullable(id == null ? null : lieux.get(id));
    }

    public Optional<Lieu> byRef(Lieu.Ref ref) {
        UUID id = byRef.get(ref);
        return id == null ? Optional.empty() : get(id);
    }

    public void add(Lieu lieu) {
        lieux.put(lieu.id(), lieu);
        if (lieu.ref() != null) byRef.put(lieu.ref(), lieu.id());
    }

    /** Retire un lieu ; ceux qui en relevaient à la main retombent sur le parent que le monde leur donne. */
    public Optional<Lieu> remove(UUID id) {
        Lieu l = lieux.remove(id);
        if (l == null) return Optional.empty();
        if (l.ref() != null) byRef.remove(l.ref());
        for (Lieu other : lieux.values()) if (id.equals(other.parent())) other.setParent(null);
        return Optional.of(l);
    }

    /** La chose renvoyée a disparu, mais le lieu a une fiche : il reste, posé à la main. */
    public void detach(Lieu lieu) {
        if (lieu.ref() != null) byRef.remove(lieu.ref());
        lieu.detach();
    }

    public void clear() {
        lieux.clear();
        byRef.clear();
    }

    // ------------------------------------------------------------------ hiérarchie (logique pure)

    /**
     * Rattacher {@code child} à {@code parent} ferait-il une boucle ? {@code parentOf} donne le parent effectif de
     * chaque lieu (choisi à la main, ou donné par le monde).
     */
    public static boolean wouldCycle(UUID child, UUID parent, Function<UUID, UUID> parentOf) {
        Set<UUID> seen = new HashSet<>();
        for (UUID at = parent; at != null; at = parentOf.apply(at)) {
            if (at.equals(child) || !seen.add(at)) return true;
        }
        return false;
    }

    /** Une ligne de l'arbre : le lieu et sa profondeur. */
    public record Row(UUID id, int depth) {}

    /**
     * L'arbre à plat, racines d'abord, chaque lieu suivi de ses enfants (triés par {@code order}). Un parent inconnu
     * ou une boucle font du lieu une racine : aucun lieu ne disparaît de la liste.
     */
    public static List<Row> tree(Collection<UUID> ids, Function<UUID, UUID> parentOf, Comparator<UUID> order) {
        Set<UUID> known = new HashSet<>(ids);
        Map<UUID, List<UUID>> children = new HashMap<>();
        List<UUID> roots = new ArrayList<>();
        for (UUID id : ids) {
            UUID p = parentOf.apply(id);
            if (p == null || !known.contains(p) || wouldCycle(id, p, parentOf)) roots.add(id);
            else children.computeIfAbsent(p, k -> new ArrayList<>()).add(id);
        }
        roots.sort(order);
        for (List<UUID> c : children.values()) c.sort(order);
        List<Row> out = new ArrayList<>(ids.size());
        Set<UUID> placed = new HashSet<>();
        for (UUID r : roots) walk(r, 0, children, out, placed);
        return out;
    }

    private static void walk(UUID id, int depth, Map<UUID, List<UUID>> children, List<Row> out, Set<UUID> placed) {
        if (!placed.add(id)) return;
        out.add(new Row(id, depth));
        for (UUID c : children.getOrDefault(id, List.of())) walk(c, depth + 1, children, out, placed);
    }

    // ------------------------------------------------------------------ sauvegarde

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Lieu l : lieux.values()) list.add(l.save());
        t.put("lieux", list);
        return t;
    }

    public static PlaceState load(CompoundTag t) {
        PlaceState s = new PlaceState();
        ListTag list = t.getList("lieux", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                s.add(Lieu.load(list.getCompound(i)));
            } catch (RuntimeException e) {
                fr.crusadecraft.CrusadeCraft.LOGGER.error("CrusadeCraft : lieu illisible ignoré (index {})", i, e);
            }
        }
        return s;
    }
}
