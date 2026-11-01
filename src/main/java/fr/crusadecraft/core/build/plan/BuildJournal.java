package fr.crusadecraft.core.build.plan;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le journal d'une construction posée d'après un plan (P34) : ce qu'il y avait à chaque bloc remplacé, entité
 * comprise (le contenu d'un coffre), pour tout défaire. Une ville entière remplace cent mille blocs : le journal est
 * compact (positions et numéros dans une palette) et vit dans son propre fichier, à part des données du monde.
 */
public final class BuildJournal extends SavedData {
    private final LongArrayList positions = new LongArrayList();
    private final IntArrayList states = new IntArrayList();
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Integer> index = new HashMap<>();
    private final Map<Integer, CompoundTag> entities = new HashMap<>();
    private final LongOpenHashSet seen = new LongOpenHashSet();

    public BuildJournal() {}

    public int size() {
        return positions.size();
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    /** Note l'état d'origine d'un bloc avant qu'on le remplace ; seule la première fois compte. */
    public void record(ServerLevel level, BlockPos pos, BlockState before) {
        long key = pos.asLong();
        if (!seen.add(key)) return;
        Integer i = index.get(before);
        if (i == null) {
            i = palette.size();
            palette.add(before);
            index.put(before, i);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) entities.put(positions.size(), be.saveWithFullMetadata());
        positions.add(key);
        states.add(i.intValue());
        setDirty();
    }

    /**
     * Remet jusqu'à {@code budget} blocs, du dernier posé au premier. Renvoie le nombre remis ; le journal est vide
     * quand tout est revenu.
     */
    public int undo(ServerLevel level, int budget) {
        int n = 0;
        while (n < budget && !positions.isEmpty()) {
            int last = positions.size() - 1;
            BlockPos pos = BlockPos.of(positions.getLong(last));
            BlockState state = palette.get(states.getInt(last));
            CompoundTag be = entities.remove(last);
            positions.removeLong(last);
            states.removeInt(last);
            seen.remove(pos.asLong());
            if (!level.isLoaded(pos)) level.getChunk(pos);
            level.removeBlockEntity(pos);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (be != null) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity != null) {
                    entity.load(be);
                    entity.setChanged();
                }
            }
            n++;
        }
        if (n > 0) setDirty();
        return n;
    }

    public void clear() {
        positions.clear();
        states.clear();
        palette.clear();
        index.clear();
        entities.clear();
        seen.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putLongArray("pos", positions.toLongArray());
        t.putIntArray("state", states.toIntArray());
        ListTag p = new ListTag();
        for (BlockState s : palette) p.add(NbtUtils.writeBlockState(s));
        t.put("palette", p);
        ListTag e = new ListTag();
        for (Map.Entry<Integer, CompoundTag> en : entities.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putInt("i", en.getKey());
            c.put("be", en.getValue());
            e.add(c);
        }
        t.put("entities", e);
        return t;
    }

    public static BuildJournal load(CompoundTag t) {
        BuildJournal j = new BuildJournal();
        ListTag p = t.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            BlockState s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), p.getCompound(i));
            j.palette.add(s);
            j.index.putIfAbsent(s, i);
        }
        long[] pos = t.getLongArray("pos");
        int[] st = t.getIntArray("state");
        for (int i = 0; i < Math.min(pos.length, st.length); i++) {
            if (st[i] < 0 || st[i] >= j.palette.size()) continue;
            j.positions.add(pos[i]);
            j.states.add(st[i]);
            j.seen.add(pos[i]);
        }
        ListTag e = t.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < e.size(); i++) j.entities.put(e.getCompound(i).getInt("i"), e.getCompound(i).getCompound("be"));
        return j;
    }
}
