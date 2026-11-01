package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduit un plan lu ({@link RawPlan}) en plan prêt à poser ({@link Plan}) (P34) : les blocs propres à MineColonies
 * deviennent des blocs ordinaires ({@link PlanRules}), l'entrée est tournée au sud, les fondations comblent le terrain
 * sous les bords, et la place est dégagée au-dessus du toit.
 */
public final class PlanBuilder {
    /** Couches dégagées au-dessus du toit, pour un bâtiment posé à flanc de colline. */
    static final int CLEARING = 6;

    private PlanBuilder() {}

    /**
     * @param keepHuts garder les blocs de cabane (une cabane qu'un joueur fondera) au lieu de les remplacer par des
     *                 tonneaux (une ville de l'ordinateur, sans colonie)
     */
    public static Plan build(PlanRef ref, RawPlan raw, boolean keepHuts) {
        Direction front = front(raw);
        Rotation r0 = inverse(Blueprints.rotation(front));
        int ox = raw.sizeX() / 2, oz = raw.sizeZ() / 2;
        boolean swap = r0 == Rotation.CLOCKWISE_90 || r0 == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? raw.sizeZ() : raw.sizeX(), sz = swap ? raw.sizeX() : raw.sizeZ();

        List<Blueprints.Placement> blocks = new ArrayList<>();
        Map<Long, Integer> lowest = new HashMap<>();
        Map<Long, BlockState> lowestState = new HashMap<>();
        int huts = 0;
        for (RawPlan.Cell c : raw.cells()) {
            BlockState state = c.state();
            CompoundTag tag = c.tag();
            switch (PlanRules.classify(c.id())) {
                case SKIP -> state = null;
                case AIR -> {
                    state = Blocks.AIR.defaultBlockState();
                    tag = null;
                }
                case SOLID -> {
                    state = c.y() == raw.groundY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    tag = null;
                }
                case FLUID -> {
                    state = Blocks.WATER.defaultBlockState();
                    tag = null;
                }
                case TAG -> {
                    CompoundTag rep = tag == null ? null : tag.getCompound("replacement");
                    if (rep == null || !rep.contains("b")) {
                        state = null;
                    } else {
                        state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), rep.getCompound("b"));
                        tag = rep.contains("e") ? rep.getCompound("e") : null;
                        if (state.isAir() && !rep.getCompound("b").getString("Name").endsWith("air")) state = null;
                    }
                }
                case HUT -> {
                    huts++;
                    tag = null;
                    if (!keepHuts) state = Blocks.BARREL.defaultBlockState();
                }
                case COLONY -> {
                    if (state.hasBlockEntity()) {
                        state = Blocks.BARREL.defaultBlockState();
                        tag = null;
                    }
                }
                case KEEP -> {
                    // tel quel
                }
            }
            if (state == null) continue;
            int[] p = turn(c.x() - ox, c.z() - oz, r0);
            int y = c.y() - raw.groundY();
            CompoundTag copy = tag == null ? null : tag.copy();
            blocks.add(new Blueprints.Placement(p[0], y, p[1], state.rotate(r0), false, copy));
            if (!state.isAir()) {
                long key = columnKey(p[0], p[1]);
                Integer low = lowest.get(key);
                if (low == null || y < low) {
                    lowest.put(key, y);
                    lowestState.put(key, state);
                }
            }
        }

        List<Blueprints.Placement> out = new ArrayList<>(blocks.size() + lowest.size() * (CLEARING + 1));
        // Les fondations d'abord : sous le bloc le plus bas de chaque colonne, le terrain est comblé jusqu'à la roche.
        for (Map.Entry<Long, Integer> e : lowest.entrySet()) {
            if (e.getValue() > 0) continue;
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            out.add(new Blueprints.Placement(x, e.getValue() - 1, z, fill(lowestState.get(e.getKey())), true));
        }
        // Puis la place, dégagée au-dessus du toit.
        int top = raw.sizeY() - raw.groundY();
        for (long key : lowest.keySet()) {
            int x = (int) (key >> 32), z = (int) key;
            for (int y = top; y < top + CLEARING; y++) out.add(new Blueprints.Placement(x, y, z, Blocks.AIR.defaultBlockState(), false));
        }
        out.addAll(blocks);
        return new Plan(ref, raw.name(), sx, raw.sizeY(), sz, raw.groundY(), front, huts, List.copyOf(out));
    }

    /** La matière des fondations : de la terre sous le naturel, du grès sous le sable, des pavés sous le bâti. */
    static BlockState fill(BlockState lowest) {
        Block b = lowest.getBlock();
        if (b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.DIRT_PATH || b == Blocks.PODZOL || b == Blocks.COARSE_DIRT
                || b == Blocks.ROOTED_DIRT || b == Blocks.MUD || b == Blocks.FARMLAND) return Blocks.DIRT.defaultBlockState();
        if (b == Blocks.SAND || b == Blocks.SANDSTONE || b == Blocks.SMOOTH_SANDSTONE) return Blocks.SANDSTONE.defaultBlockState();
        if (b == Blocks.RED_SAND || b == Blocks.RED_SANDSTONE) return Blocks.RED_SANDSTONE.defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    /**
     * Le côté de l'entrée : celui dont les portes du rez-de-chaussée sont les plus proches. Sans porte, le plan est
     * pris tel qu'il a été dessiné (entrée au sud).
     */
    static Direction front(RawPlan raw) {
        double[] votes = new double[4];
        for (RawPlan.Cell c : raw.cells()) {
            if (c.y() < raw.groundY() || c.y() > raw.groundY() + 3) continue;
            if (!(c.state().getBlock() instanceof DoorBlock) || c.state().getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            int dn = c.z(), ds = raw.sizeZ() - 1 - c.z(), dw = c.x(), de = raw.sizeX() - 1 - c.x();
            int min = Math.min(Math.min(dn, ds), Math.min(dw, de));
            double w = 1.0 / (1 + min);
            if (dn == min) votes[0] += w;
            if (ds == min) votes[1] += w;
            if (dw == min) votes[2] += w;
            if (de == min) votes[3] += w;
        }
        int best = 1;
        for (int i = 0; i < 4; i++) if (votes[i] > votes[best] + 1e-9) best = i;
        return switch (best) {
            case 0 -> Direction.NORTH;
            case 2 -> Direction.WEST;
            case 3 -> Direction.EAST;
            default -> Direction.SOUTH;
        };
    }

    static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }

    /** (x, z) tourné comme le fait {@link Blueprints#place}. */
    static int[] turn(int x, int z, Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> new int[]{-z, x};
            case CLOCKWISE_180 -> new int[]{-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
