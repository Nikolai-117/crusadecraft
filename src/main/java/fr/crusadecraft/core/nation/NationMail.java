package fr.crusadecraft.core.nation;

import fr.crusadecraft.core.diplomacy.Treaty;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Une lettre reçue par le souverain d'une nation (P41). Une offre de traité attend une réponse ; un présent est déjà
 * versé ; une simple nouvelle se lit et s'efface.
 *
 * @param treaty le traité proposé, ou {@code null}
 * @param gold   l'or qu'elle apporte
 */
public record NationMail(UUID id, UUID from, String sender, String subject, String body, Treaty treaty, int gold, long day, long expires) {

    public boolean offer() {
        return treaty != null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("from", from);
        t.putString("sender", sender);
        t.putString("subject", subject);
        t.putString("body", body);
        if (treaty != null) t.putString("treaty", treaty.name());
        t.putInt("gold", gold);
        t.putLong("day", day);
        t.putLong("expires", expires);
        return t;
    }

    public static NationMail load(CompoundTag t) {
        Treaty treaty = null;
        if (t.contains("treaty")) {
            try {
                treaty = Treaty.valueOf(t.getString("treaty"));
            } catch (IllegalArgumentException ignored) {
                // Un traité disparu d'une version à l'autre : il ne reste qu'une lettre.
            }
        }
        return new NationMail(t.getUUID("id"), t.getUUID("from"), t.getString("sender"), t.getString("subject"), t.getString("body"), treaty,
                t.getInt("gold"), t.getLong("day"), t.getLong("expires"));
    }
}
