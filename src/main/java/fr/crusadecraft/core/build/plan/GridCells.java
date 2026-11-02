package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.AbstractList;
import java.util.Map;
import java.util.RandomAccess;

/**
 * Les cases d'une très grande schématique (P38), lues à la demande au lieu d'être tenues une à une : une ville de
 * 253 × 92 × 239 fait cinq millions et demi de cases, qui coûteraient un demi-gigaoctet en objets. Ici, un numéro de
 * palette par case et la palette elle-même. L'ordre est celui des couches, de bas en haut.
 */
final class GridCells extends AbstractList<RawPlan.Cell> implements RandomAccess {
    final int w, h, l;
    /** Le numéro de palette de chaque case, x + z·w + y·w·l. */
    final int[] indices;
    final BlockState[] states;
    final String[] ids;
    /** Les données d'entité d'une sorte de bloc (un colombage), recopiées sur chaque case qui n'a pas les siennes. */
    final CompoundTag[] extra;
    final Map<Integer, CompoundTag> entities;
    /** Les cases gardées (un bloc connu, air compris), dans l'ordre. */
    final int[] kept;

    GridCells(int w, int h, int l, int[] indices, BlockState[] states, String[] ids, CompoundTag[] extra, Map<Integer, CompoundTag> entities) {
        this.w = w;
        this.h = h;
        this.l = l;
        this.indices = indices;
        this.states = states;
        this.ids = ids;
        this.extra = extra;
        this.entities = entities;
        int n = 0;
        for (int idx : indices) if (idx >= 0 && idx < states.length && states[idx] != null) n++;
        this.kept = new int[n];
        int k = 0;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            if (idx >= 0 && idx < states.length && states[idx] != null) kept[k++] = i;
        }
    }

    int x(int cell) { return cell % w; }

    int z(int cell) { return (cell / w) % l; }

    int y(int cell) { return cell / (w * l); }

    BlockState state(int cell) {
        return states[indices[cell]];
    }

    String id(int cell) {
        return ids[indices[cell]];
    }

    CompoundTag tag(int cell) {
        CompoundTag own = entities.get(cell);
        return own != null ? own : extra[indices[cell]];
    }

    @Override
    public RawPlan.Cell get(int k) {
        int cell = kept[k];
        return new RawPlan.Cell(x(cell), y(cell), z(cell), state(cell), id(cell), tag(cell));
    }

    @Override
    public int size() {
        return kept.length;
    }
}
