package fr.crusadecraft.core.lineage;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Un groupe de gens qui vivent ou servent ensemble : un village, une guilde, un ordre, une compagnie, un monastère. */
public final class Community {
    private final UUID id;
    private String name;
    private CommunityKind kind;
    private UUID realm;

    public Community(UUID id, String name, CommunityKind kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public CommunityKind kind() { return kind; }
    public UUID realm() { return realm; }

    public void setName(String name) { this.name = name; }
    public void setKind(CommunityKind kind) { this.kind = kind; }
    public void setRealm(UUID realm) { this.realm = realm; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("kind", kind.name());
        if (realm != null) t.putUUID("realm", realm);
        return t;
    }

    public static Community load(CompoundTag t) {
        Community c = new Community(t.getUUID("id"), t.getString("name"), CommunityKind.byName(t.getString("kind"), CommunityKind.VILLAGE));
        if (t.hasUUID("realm")) c.realm = t.getUUID("realm");
        return c;
    }
}
