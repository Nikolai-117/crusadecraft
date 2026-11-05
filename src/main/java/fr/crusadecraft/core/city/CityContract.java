package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Un contrat (P56) : ce qu'une ville demande à un joueur, à quel prix, et où il en est. */
public final class CityContract {
    public enum Kind { COURIER, ESCORT }

    public final UUID id;
    public final Kind kind;
    public final UUID from, to;
    public final int reward;
    public final long createdDay;
    /** Le dernier jour : pour une offre, celui où elle tombe ; pour un pli accepté, celui où il doit être arrivé. */
    public long expiresDay;
    /** Le joueur qui l'a accepté. */
    public UUID taker;
    /** Pour une escorte : la ligne, et le marchand qu'on accompagne. */
    public UUID line, merchant;
    /** Combien de fois on a regardé, et combien de fois il était à côté. */
    public long near, total;

    public CityContract(UUID id, Kind kind, UUID from, UUID to, int reward, long createdDay, long expiresDay) {
        this.id = id;
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.reward = reward;
        this.createdDay = createdDay;
        this.expiresDay = expiresDay;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.name());
        t.putUUID("from", from);
        t.putUUID("to", to);
        t.putInt("reward", reward);
        t.putLong("created", createdDay);
        t.putLong("expires", expiresDay);
        if (taker != null) t.putUUID("taker", taker);
        if (line != null) t.putUUID("line", line);
        if (merchant != null) t.putUUID("merchant", merchant);
        t.putLong("near", near);
        t.putLong("total", total);
        return t;
    }

    public static CityContract load(CompoundTag t) {
        Kind kind;
        try {
            kind = Kind.valueOf(t.getString("kind"));
        } catch (IllegalArgumentException e) {
            kind = Kind.COURIER;
        }
        CityContract c = new CityContract(t.getUUID("id"), kind, t.getUUID("from"), t.getUUID("to"), t.getInt("reward"), t.getLong("created"), t.getLong("expires"));
        if (t.hasUUID("taker")) c.taker = t.getUUID("taker");
        if (t.hasUUID("line")) c.line = t.getUUID("line");
        if (t.hasUUID("merchant")) c.merchant = t.getUUID("merchant");
        c.near = t.getLong("near");
        c.total = t.getLong("total");
        return c;
    }
}
