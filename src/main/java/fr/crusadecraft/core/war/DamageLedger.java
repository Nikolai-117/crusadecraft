package fr.crusadecraft.core.war;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le registre des dégâts de guerre : l'état d'origine de chaque bloc touché (explosion, bannière posée,
 * échelle d'assaut), pour tout remettre en place à la paix. Il est enregistré avec le monde : une
 * restauration interrompue reprend au démarrage suivant.
 */
public final class DamageLedger {

    /** Un bloc d'origine : sa guerre, sa place, son état, et les données de son entité (coffre…). */
    public record Entry(UUID war, ResourceLocation dimension, BlockPos pos, CompoundTag state, CompoundTag blockEntity) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("war", war);
            t.putString("dim", dimension.toString());
            t.put("pos", NbtUtils.writeBlockPos(pos));
            t.put("state", state);
            if (blockEntity != null) t.put("be", blockEntity);
            return t;
        }

        static Entry load(CompoundTag t) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            return new Entry(t.getUUID("war"), dim == null ? ResourceLocation.tryParse("minecraft:overworld") : dim,
                    NbtUtils.readBlockPos(t.getCompound("pos")), t.getCompound("state"),
                    t.contains("be") ? t.getCompound("be") : null);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<Entry> restoring = new ArrayList<>();

    private static String key(ResourceLocation dim, BlockPos pos) {
        return dim + "|" + pos.asLong();
    }

    public boolean has(ResourceLocation dim, BlockPos pos) {
        return entries.containsKey(key(dim, pos));
    }

    /** Note l'état d'origine d'un bloc ; seule la première atteinte compte. Renvoie vrai si c'est nouveau. */
    public boolean record(UUID war, ResourceLocation dim, BlockPos pos, BlockState state, CompoundTag blockEntity) {
        String k = key(dim, pos);
        if (entries.containsKey(k)) return false;
        entries.put(k, new Entry(war, dim, pos.immutable(), NbtUtils.writeBlockState(state), blockEntity));
        return true;
    }

    public int size() {
        return entries.size();
    }

    public int pending() {
        return restoring.size();
    }

    public int count(UUID war) {
        int n = 0;
        for (Entry e : entries.values()) if (e.war().equals(war)) n++;
        return n;
    }

    /** Retire et renvoie l'entrée d'un bloc (pour le remettre tout de suite), ou {@code null}. */
    public Entry remove(ResourceLocation dim, BlockPos pos) {
        return entries.remove(key(dim, pos));
    }

    /** La guerre est finie : ses blocs passent dans la file de restauration. */
    public void release(UUID war) {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            if (e.war().equals(war)) {
                restoring.add(e);
                it.remove();
            }
        }
    }

    /** Les prochains blocs à remettre (retirés de la file). */
    public List<Entry> take(int n) {
        List<Entry> out = new ArrayList<>();
        while (!restoring.isEmpty() && out.size() < n) out.add(restoring.remove(0));
        return out;
    }

    /** Remet en file un bloc qu'on n'a pas pu restaurer (chunk indisponible). */
    public void retry(Entry e) {
        restoring.add(e);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag a = new ListTag();
        for (Entry e : entries.values()) a.add(e.save());
        t.put("entries", a);
        ListTag r = new ListTag();
        for (Entry e : restoring) r.add(e.save());
        t.put("restoring", r);
        return t;
    }

    public static DamageLedger load(CompoundTag t) {
        DamageLedger d = new DamageLedger();
        ListTag a = t.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < a.size(); i++) {
            Entry e = Entry.load(a.getCompound(i));
            d.entries.put(key(e.dimension(), e.pos()), e);
        }
        ListTag r = t.getList("restoring", Tag.TAG_COMPOUND);
        for (int i = 0; i < r.size(); i++) d.restoring.add(Entry.load(r.getCompound(i)));
        return d;
    }
}
