package fr.crusadecraft.core.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tout ce qu'on peut régler sur un habitant (P47), comme dans l'éditeur d'un mod de PNJ : son allure, ses statistiques, sa
 * façon de vivre, ses répliques et ses échanges. Rien n'est enregistré tant que rien n'est changé.
 */
public final class NpcSettings {
    /** Dans ses répliques : à tout moment, quand rien n'est écrit pour celui-là. */
    public static final String ANY = "*";

    /** Comment il occupe ses journées. */
    public enum Behavior { SCHEDULE, STATIONARY, PATROL }

    /** Son titre, après son nom (« la Boulangère »). */
    public String title = "";
    /** Son allure : vide, celle de Comes Alive ; {@code skin:<empreinte>}, un skin importé ; sinon, une tenue de Comes Alive. */
    public String look = "";
    /** Sa coiffure de Comes Alive ; vide, la sienne. */
    public String hair = "";
    /** Sa taille, de 0 à 1 ; négative, celle qu'il a de naissance. */
    public float size = -1f;
    public boolean showName = true;
    /** Ses statistiques ; zéro, celles de son espèce. */
    public double maxHealth, speed, damage;
    public boolean invincible;
    public Behavior behavior = Behavior.SCHEDULE;
    /** Ses heures de travail (en ticks du jour) ; négatives, celles de tout le monde. */
    public int workStart = -1, workEnd = -1;
    public boolean canTravel = true, canMarry = true, canLeave = true;
    /** Sa parole : 0 muet, 1 normal, 2 bavard. */
    public int chatty = 1;
    /** Ses répliques à lui, par moment. */
    public final Map<String, List<String>> lines = new LinkedHashMap<>();
    /** Ses échanges sur mesure ; {@code null}, ceux de son métier. */
    public CompoundTag offers;

    public boolean isDefault() {
        return title.isEmpty() && look.isEmpty() && hair.isEmpty() && size < 0 && showName && maxHealth == 0 && speed == 0 && damage == 0 && !invincible
                && behavior == Behavior.SCHEDULE && workStart < 0 && workEnd < 0 && canTravel && canMarry && canLeave && chatty == 1 && lines.isEmpty() && offers == null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (!title.isEmpty()) t.putString("title", title);
        if (!look.isEmpty()) t.putString("look", look);
        if (!hair.isEmpty()) t.putString("hair", hair);
        if (size >= 0) t.putFloat("size", size);
        if (!showName) t.putBoolean("hideName", true);
        if (maxHealth != 0) t.putDouble("maxHealth", maxHealth);
        if (speed != 0) t.putDouble("speed", speed);
        if (damage != 0) t.putDouble("damage", damage);
        if (invincible) t.putBoolean("invincible", true);
        if (behavior != Behavior.SCHEDULE) t.putString("behavior", behavior.name());
        if (workStart >= 0) t.putInt("workStart", workStart);
        if (workEnd >= 0) t.putInt("workEnd", workEnd);
        if (!canTravel) t.putBoolean("noTravel", true);
        if (!canMarry) t.putBoolean("noMarry", true);
        if (!canLeave) t.putBoolean("noLeave", true);
        if (chatty != 1) t.putInt("chatty", chatty);
        if (!lines.isEmpty()) {
            CompoundTag l = new CompoundTag();
            for (Map.Entry<String, List<String>> e : lines.entrySet()) {
                ListTag list = new ListTag();
                for (String s : e.getValue()) list.add(StringTag.valueOf(s));
                l.put(e.getKey(), list);
            }
            t.put("lines", l);
        }
        if (offers != null) t.put("offers", offers.copy());
        return t;
    }

    public static NpcSettings load(CompoundTag t) {
        NpcSettings s = new NpcSettings();
        s.title = t.getString("title");
        s.look = t.getString("look");
        s.hair = t.getString("hair");
        s.size = t.contains("size") ? t.getFloat("size") : -1f;
        s.showName = !t.getBoolean("hideName");
        s.maxHealth = t.getDouble("maxHealth");
        s.speed = t.getDouble("speed");
        s.damage = t.getDouble("damage");
        s.invincible = t.getBoolean("invincible");
        try {
            if (t.contains("behavior")) s.behavior = Behavior.valueOf(t.getString("behavior"));
        } catch (IllegalArgumentException ignored) {
            // un mode disparu d'une version à l'autre : celui de tout le monde
        }
        s.workStart = t.contains("workStart") ? t.getInt("workStart") : -1;
        s.workEnd = t.contains("workEnd") ? t.getInt("workEnd") : -1;
        s.canTravel = !t.getBoolean("noTravel");
        s.canMarry = !t.getBoolean("noMarry");
        s.canLeave = !t.getBoolean("noLeave");
        s.chatty = t.contains("chatty") ? t.getInt("chatty") : 1;
        CompoundTag l = t.getCompound("lines");
        for (String k : l.getAllKeys()) {
            List<String> out = new ArrayList<>();
            ListTag list = l.getList(k, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
            s.lines.put(k, out);
        }
        if (t.contains("offers")) s.offers = t.getCompound("offers").copy();
        return s;
    }
}
