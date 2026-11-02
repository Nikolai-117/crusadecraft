package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * Les poses d'une très grande schématique (P38), calculées à la demande dans le même ordre que {@link PlanBuilder} :
 * les fondations, le dégagement au-dessus, puis les cases couche par couche. La ville garde l'orientation de son
 * dessin ; le joueur la tourne à l'outil.
 */
final class GridPlacements extends AbstractList<Blueprints.Placement> implements RandomAccess {
    private static final byte KEEP = 0, SKIP = 1, SOLID = 2, TAG = 3;
    private final GridCells grid;
    private final int groundY, ox, oz, top;
    private final BlockState[] out;
    private final byte[] kind;
    private final boolean[] dropTag;
    private final int[] cells, foundCols, foundY, clearCols;
    private final BlockState[] foundState;
    final int huts;

    GridPlacements(GridCells grid, int groundY, boolean keepHuts) {
        this.grid = grid;
        this.groundY = groundY;
        this.ox = grid.w / 2;
        this.oz = grid.l / 2;
        this.top = grid.h - groundY;
        int n = grid.states.length;
        out = new BlockState[n];
        kind = new byte[n];
        dropTag = new boolean[n];
        boolean[] hut = new boolean[n];
        for (int i = 0; i < n; i++) {
            BlockState st = grid.states[i];
            if (st == null) {
                kind[i] = SKIP;
                continue;
            }
            switch (PlanRules.classify(grid.ids[i])) {
                case SKIP -> kind[i] = SKIP;
                case AIR -> {
                    out[i] = Blocks.AIR.defaultBlockState();
                    dropTag[i] = true;
                }
                case SOLID -> {
                    kind[i] = SOLID;
                    dropTag[i] = true;
                }
                case FLUID -> {
                    out[i] = Blocks.WATER.defaultBlockState();
                    dropTag[i] = true;
                }
                case TAG -> kind[i] = TAG;
                case HUT -> {
                    hut[i] = true;
                    dropTag[i] = true;
                    out[i] = keepHuts ? st : Blocks.BARREL.defaultBlockState();
                }
                case COLONY -> {
                    if (st.hasBlockEntity()) {
                        out[i] = Blocks.BARREL.defaultBlockState();
                        dropTag[i] = true;
                    } else {
                        out[i] = st;
                    }
                }
                case KEEP -> out[i] = st;
            }
        }
        int columns = grid.w * grid.l;
        int[] lowest = new int[columns];
        java.util.Arrays.fill(lowest, Integer.MIN_VALUE);
        BlockState[] lowState = new BlockState[columns];
        IntArrayList kept = new IntArrayList(grid.kept.length);
        int hutCount = 0;
        for (int cell : grid.kept) {
            BlockState st = state(cell);
            if (st == null) continue;
            kept.add(cell);
            if (hut[grid.indices[cell]]) hutCount++;
            int col = cell % columns;
            // Les cases viennent de bas en haut : le premier bloc d'une colonne est son plus bas.
            if (!st.isAir() && lowest[col] == Integer.MIN_VALUE) {
                lowest[col] = grid.y(cell);
                lowState[col] = st;
            }
        }
        cells = kept.toIntArray();
        huts = hutCount;
        IntArrayList fc = new IntArrayList(), fy = new IntArrayList(), cc = new IntArrayList();
        java.util.List<BlockState> fs = new java.util.ArrayList<>();
        for (int col = 0; col < columns; col++) {
            if (lowest[col] == Integer.MIN_VALUE) continue;
            cc.add(col);
            if (lowest[col] - groundY > 0) continue;
            fc.add(col);
            fy.add(lowest[col] - groundY - 1);
            fs.add(PlanBuilder.fill(lowState[col]));
        }
        foundCols = fc.toIntArray();
        foundY = fy.toIntArray();
        foundState = fs.toArray(new BlockState[0]);
        clearCols = cc.toIntArray();
    }

    /** Ce que devient une case (rien : laissée de côté). */
    private BlockState state(int cell) {
        int idx = grid.indices[cell];
        return switch (kind[idx]) {
            case SKIP -> null;
            case SOLID -> grid.y(cell) == groundY ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            case TAG -> replacement(grid.tag(cell));
            default -> out[idx];
        };
    }

    /** Le bloc qu'une balise de Structurize remplace, ou rien. */
    private static BlockState replacement(CompoundTag tag) {
        CompoundTag rep = tag == null ? null : tag.getCompound("replacement");
        if (rep == null || !rep.contains("b")) return null;
        BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), rep.getCompound("b"));
        return st.isAir() && !rep.getCompound("b").getString("Name").endsWith("air") ? null : st;
    }

    @Override
    public Blueprints.Placement get(int i) {
        if (i < foundCols.length) {
            int col = foundCols[i];
            return new Blueprints.Placement(col % grid.w - ox, foundY[i], col / grid.w - oz, foundState[i], true);
        }
        i -= foundCols.length;
        if (i < clearCols.length * PlanBuilder.CLEARING) {
            int col = clearCols[i / PlanBuilder.CLEARING];
            return new Blueprints.Placement(col % grid.w - ox, top + i % PlanBuilder.CLEARING, col / grid.w - oz, Blocks.AIR.defaultBlockState(), false);
        }
        i -= clearCols.length * PlanBuilder.CLEARING;
        int cell = cells[i];
        int idx = grid.indices[cell];
        BlockState st = state(cell);
        CompoundTag tag;
        if (kind[idx] == TAG) {
            CompoundTag rep = grid.tag(cell).getCompound("replacement");
            tag = rep.contains("e") ? rep.getCompound("e").copy() : null;
        } else {
            CompoundTag t = dropTag[idx] ? null : grid.tag(cell);
            tag = t == null ? null : t.copy();
        }
        return new Blueprints.Placement(grid.x(cell) - ox, grid.y(cell) - groundY, grid.z(cell) - oz, st, false, tag);
    }

    @Override
    public int size() {
        return foundCols.length + clearCols.length * PlanBuilder.CLEARING + cells.length;
    }
}
