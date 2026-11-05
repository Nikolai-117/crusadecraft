package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Une ligne de commerce (P51) : tant de telle marchandise, de telle ville à telle autre, à tel rythme. Une ligne qu'un joueur crée et une
 * ligne que l'ordinateur décide sont la même chose ; seules celles du joueur se modifient à la main.
 */
public final class CityLine {
    public final UUID id;
    public final UUID from, to;
    /** Ce qu'elle emporte ; {@code null} : le surplus du moment. */
    public CityGood good;
    public int amount = 24;
    /** Ce qu'elle rapporte ; {@code null} : ce qui manque le plus au départ. */
    public CityGood back;
    /** Un départ tous les tant de quarts de journée (1 : quatre fois par jour ; 4 : une fois). */
    public int every = 4;
    public int escort = 2;
    public boolean active = true;
    /** Décidée par l'ordinateur. */
    public boolean ai;
    /** Les caravanes tracent un chemin au sol. */
    public boolean pave;
    /** Quarts de journée depuis le dernier départ. */
    public int waited;
    public int trips;
    public long moved, earned;
    /** Ce qu'il en est, à montrer dans le panneau (une clé de langue). */
    public String status = "";
    /** Le jour où elle a servi pour la dernière fois, pour que l'ordinateur oublie celles qui ne servent plus. */
    public long lastDay;

    public CityLine(UUID id, UUID from, UUID to) {
        this.id = id;
        this.from = from;
        this.to = to;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("from", from);
        t.putUUID("to", to);
        if (good != null) t.putString("good", good.name());
        t.putInt("amount", amount);
        if (back != null) t.putString("back", back.name());
        t.putInt("every", every);
        t.putInt("escort", escort);
        t.putBoolean("active", active);
        t.putBoolean("ai", ai);
        t.putBoolean("pave", pave);
        t.putInt("waited", waited);
        t.putInt("trips", trips);
        t.putLong("moved", moved);
        t.putLong("earned", earned);
        t.putString("status", status);
        t.putLong("lastDay", lastDay);
        return t;
    }

    private static CityGood good(String name) {
        try {
            return name.isEmpty() ? null : CityGood.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static CityLine load(CompoundTag t) {
        CityLine l = new CityLine(t.getUUID("id"), t.getUUID("from"), t.getUUID("to"));
        l.good = good(t.getString("good"));
        l.amount = t.getInt("amount");
        l.back = good(t.getString("back"));
        l.every = Math.max(1, t.getInt("every"));
        l.escort = t.getInt("escort");
        l.active = t.getBoolean("active");
        l.ai = t.getBoolean("ai");
        l.pave = t.getBoolean("pave");
        l.waited = t.getInt("waited");
        l.trips = t.getInt("trips");
        l.moved = t.getLong("moved");
        l.earned = t.getLong("earned");
        l.status = t.getString("status");
        l.lastDay = t.getLong("lastDay");
        return l;
    }
}
