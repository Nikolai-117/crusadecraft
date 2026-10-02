package fr.crusadecraft.core.council;

import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Une requête en attente de réponse.
 *
 * @param count  le nombre concerné (sans-logis, malades…), affiché dans le texte
 * @param place  la colonie concernée, affichée dans le texte
 */
public record Request(UUID id, RequestType type, CitizenRef from, String place, int count, long createdDay) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type.name());
        t.put("from", from.save());
        t.putString("place", place);
        t.putInt("count", count);
        t.putLong("day", createdDay);
        return t;
    }

    public static Request load(CompoundTag t) {
        RequestType type = RequestType.byName(t.getString("type"));
        if (type == null) return null;
        return new Request(t.getUUID("id"), type, CitizenRef.load(t.getCompound("from")), t.getString("place"),
                t.getInt("count"), t.getLong("day"));
    }
}
