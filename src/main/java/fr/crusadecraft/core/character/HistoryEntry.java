package fr.crusadecraft.core.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Un souvenir de la vie d'un personnage : un jour, une clé de traduction et ses arguments. */
public record HistoryEntry(long day, String key, List<String> args) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("day", day);
        t.putString("key", key);
        ListTag l = new ListTag();
        for (String a : args) l.add(StringTag.valueOf(a));
        t.put("args", l);
        return t;
    }

    public static HistoryEntry load(CompoundTag t) {
        List<String> args = new ArrayList<>();
        ListTag l = t.getList("args", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) args.add(l.getString(i));
        return new HistoryEntry(t.getLong("day"), t.getString("key"), args);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(day);
        buf.writeUtf(key);
        buf.writeVarInt(args.size());
        for (String a : args) buf.writeUtf(a);
    }

    public static HistoryEntry read(FriendlyByteBuf buf) {
        long day = buf.readVarLong();
        String key = buf.readUtf();
        int n = buf.readVarInt();
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) args.add(buf.readUtf());
        return new HistoryEntry(day, key, args);
    }
}
