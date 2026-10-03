package fr.crusadecraft.core.lineage;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Une maison, une lignée dynastique : « Aubépine ». Elle peut régner sur un royaume (celle d'un royaume IA porte le
 * nom de maison de son souverain) ou n'être qu'une famille noble du monde.
 */
public final class House {
    private final UUID id;
    private String name;
    private int color;
    private UUID realm;
    private UUID head;

    public House(UUID id, String name, int color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public int color() { return color; }
    /** Le royaume sur lequel elle règne, s'il y en a un. */
    public UUID realm() { return realm; }
    /** Le chef de la maison. */
    public UUID head() { return head; }

    public void setName(String name) { this.name = name; }
    public void setColor(int color) { this.color = color; }
    public void setRealm(UUID realm) { this.realm = realm; }
    public void setHead(UUID head) { this.head = head; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("color", color);
        if (realm != null) t.putUUID("realm", realm);
        if (head != null) t.putUUID("head", head);
        return t;
    }

    public static House load(CompoundTag t) {
        House h = new House(t.getUUID("id"), t.getString("name"), t.getInt("color"));
        if (t.hasUUID("realm")) h.realm = t.getUUID("realm");
        if (t.hasUUID("head")) h.head = t.getUUID("head");
        return h;
    }
}
