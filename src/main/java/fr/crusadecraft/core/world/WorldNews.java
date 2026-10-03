package fr.crusadecraft.core.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une nouvelle du monde : guerre, paix, frontière qui bouge, alliance, coalition. Les arguments sont gardés en
 * composants sérialisés, pour que les noms de royaumes se traduisent chez chaque joueur. {@code realms} dit de
 * qui il s'agit : un joueur n'apprend que ce qui touche des royaumes qu'il connaît.
 */
public record WorldNews(long day, String key, List<String> args, List<UUID> realms) {
    public static final int KEPT = 40;

    public static WorldNews of(long day, String key, List<UUID> realms, Component... args) {
        List<String> json = new ArrayList<>();
        for (Component c : args) json.add(Component.Serializer.toJson(c));
        return new WorldNews(day, key, json, List.copyOf(realms));
    }

    /** Le texte de la nouvelle, prêt à afficher. */
    public Component text() {
        Object[] parts = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Component c = null;
            try {
                c = Component.Serializer.fromJson(args.get(i));
            } catch (RuntimeException ignored) {
                // argument illisible
            }
            parts[i] = c == null ? Component.literal("?") : c;
        }
        return Component.translatable(key, parts);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("day", day);
        t.putString("key", key);
        ListTag a = new ListTag();
        for (String s : args) a.add(StringTag.valueOf(s));
        t.put("args", a);
        ListTag r = new ListTag();
        for (UUID u : realms) r.add(StringTag.valueOf(u.toString()));
        t.put("realms", r);
        return t;
    }

    public static WorldNews load(CompoundTag t) {
        List<String> args = new ArrayList<>();
        ListTag a = t.getList("args", Tag.TAG_STRING);
        for (int i = 0; i < a.size(); i++) args.add(a.getString(i));
        List<UUID> realms = new ArrayList<>();
        ListTag r = t.getList("realms", Tag.TAG_STRING);
        for (int i = 0; i < r.size(); i++) {
            try {
                realms.add(UUID.fromString(r.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // identifiant illisible
            }
        }
        return new WorldNews(t.getLong("day"), t.getString("key"), args, realms);
    }
}
