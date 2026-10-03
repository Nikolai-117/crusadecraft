package fr.crusadecraft.core.diplomacy;

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

/** Toutes les relations entre royaumes, joueurs et IA confondus. */
public final class Diplomacy {
    private final Map<String, Relation> relations = new LinkedHashMap<>();

    public Collection<Relation> all() {
        return relations.values();
    }

    /** La relation entre deux royaumes, créée neutre si elle n'existe pas encore. */
    public Relation between(UUID x, UUID y) {
        return relations.computeIfAbsent(Relation.key(x, y), k -> new Relation(x, y));
    }

    public Optional<Relation> find(UUID x, UUID y) {
        return Optional.ofNullable(relations.get(Relation.key(x, y)));
    }

    /** Les vassaux directs d'un royaume. */
    public List<UUID> vassalsOf(UUID lord) {
        List<UUID> out = new ArrayList<>();
        for (Relation r : relations.values()) {
            if (lord.equals(r.suzerain())) out.add(r.other(lord));
        }
        return out;
    }

    public Optional<UUID> suzerainOf(UUID vassal) {
        for (Relation r : relations.values()) {
            if (r.suzerain() != null && r.involves(vassal) && !r.suzerain().equals(vassal)) return Optional.of(r.suzerain());
        }
        return Optional.empty();
    }

    /** Oublie un royaume disparu (dissous, régénéré). */
    public void forget(UUID realm) {
        relations.values().removeIf(r -> r.involves(realm));
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Relation r : relations.values()) list.add(r.save());
        return list;
    }

    public static Diplomacy load(CompoundTag parent, String key) {
        Diplomacy d = new Diplomacy();
        ListTag list = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Relation r = Relation.load(list.getCompound(i));
                d.relations.put(Relation.key(r.a(), r.b()), r);
            } catch (RuntimeException ignored) {
                // relation illisible : ignorée
            }
        }
        return d;
    }
}
