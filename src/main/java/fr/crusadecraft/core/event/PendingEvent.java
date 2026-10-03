package fr.crusadecraft.core.event;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Un événement qui attend la décision du souverain. {@code subject} est le personnage en cause (complot, faction),
 * {@code place} le lieu (province révoltée), {@code ref} ce qu'il faut retrouver pour appliquer la décision
 * (le complot, la faction). Passé {@code expires}, l'option par défaut s'applique d'elle-même.
 */
public record PendingEvent(UUID id, String event, long day, long expires, CitizenRef subject, String subjectName, String place,
                           String ref) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("event", event);
        t.putLong("day", day);
        t.putLong("expires", expires);
        if (subject != null) t.put("subject", subject.save());
        t.putString("subjectName", subjectName);
        t.putString("place", place);
        t.putString("ref", ref);
        return t;
    }

    public static PendingEvent load(CompoundTag t) {
        return new PendingEvent(t.getUUID("id"), t.getString("event"), t.getLong("day"), t.getLong("expires"),
                t.contains("subject") ? CitizenRef.load(t.getCompound("subject")) : null, t.getString("subjectName"),
                t.getString("place"), t.getString("ref"));
    }
}
