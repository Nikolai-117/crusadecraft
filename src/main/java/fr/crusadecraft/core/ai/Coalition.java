package fr.crusadecraft.core.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Une coalition de royaumes IA contre un joueur trop menaçant : qui la forme, depuis quand. Si le joueur attaque
 * l'un des membres, tous le défendent ; si elle pèse assez, elle attaque elle-même.
 */
public final class Coalition {
    private final UUID target;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final long formedDay;
    /** Annoncée au joueur (à partir de deux membres). */
    private boolean announced;

    public Coalition(UUID target, long formedDay) {
        this.target = target;
        this.formedDay = formedDay;
    }

    public UUID target() { return target; }
    public Set<UUID> members() { return members; }
    public long formedDay() { return formedDay; }
    public boolean announced() { return announced; }
    public void setAnnounced(boolean announced) { this.announced = announced; }

    /** Une coalition n'existe vraiment qu'à deux. */
    public boolean active() {
        return members.size() >= 2;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("target", target);
        t.putLong("formed", formedDay);
        t.putBoolean("announced", announced);
        ListTag m = new ListTag();
        for (UUID u : members) m.add(StringTag.valueOf(u.toString()));
        t.put("members", m);
        return t;
    }

    public static Coalition load(CompoundTag t) {
        Coalition c = new Coalition(t.getUUID("target"), t.getLong("formed"));
        c.announced = t.getBoolean("announced");
        ListTag m = t.getList("members", Tag.TAG_STRING);
        for (int i = 0; i < m.size(); i++) {
            try {
                c.members.add(UUID.fromString(m.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // identifiant illisible
            }
        }
        return c;
    }
}
