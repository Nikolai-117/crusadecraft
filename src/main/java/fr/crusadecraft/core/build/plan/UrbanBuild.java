package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Ce qu'une ville de l'atelier (P60) pose de plus qu'une ville ordinaire : l'eau d'abord, les jardins, les champs, les arbres, puis les ponts et les lanternes. Tout se fait
 * en blocs, comme le reste du chantier, et passe donc par son journal : défaire la ville rend le terrain.
 */
final class UrbanBuild {
    /** On ne creuse pas une montagne pour une rivière : au-delà, la colonne garde son terrain. */
    private static final int MAX_DIG = 14;
    private static final int NATURAL = Integer.MIN_VALUE;

    private UrbanBuild() {}

    private static int[] heights(ServerLevel level, BlockPos anchor, long[] cells, Rotation rot) {
        int[] ys = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            int[] t = PlanBuilder.turn((int) (cells[i] >> 32), (int) cells[i], rot);
            ys[i] = TownPlanner.surface(level, anchor.getX() + t[0], anchor.getZ() + t[1]) - anchor.getY();
        }
        return ys;
    }

    /** L'eau déjà là : on la laisse, et le reste se règle sur elle. */
    private static boolean naturalWater(ServerLevel level, int x, int z, int surface) {
        if (level.hasChunk(x >> 4, z >> 4)) return !level.getBlockState(new BlockPos(x, surface, z)).getFluidState().isEmpty();
        return surface < level.getSeaLevel();
    }

    /** Le relevé du terrain : une seule fois, gardé avec le plan. */
    static void survey(ServerLevel level, BlockPos anchor, UrbanExtras e, Rotation rot) {
        e.fieldY = heights(level, anchor, e.fields, rot);
        e.gardenY = heights(level, anchor, e.gardens, rot);
        e.treeY = heights(level, anchor, e.trees, rot);
        e.lampY = heights(level, anchor, e.lamps, rot);
        e.lintelY = heights(level, anchor, e.lintels, rot);
        e.bridgeY = heights(level, anchor, e.bridges, rot);
        int[] raw = heights(level, anchor, e.water, rot);
        e.waterY = new int[raw.length];
        // Au-dessus de la roche : sur un monde plat, il n'y a que deux ou trois blocs de terre.
        e.floor = level.getMinBuildHeight() - anchor.getY() + 1;
        // Par parcelles de huit blocs : un seul niveau pour toute une ville enterrerait l'eau sous les collines. Ici, elle descend par paliers, comme une vraie rivière en pente.
        java.util.Map<Long, Integer> lowest = new java.util.HashMap<>(), still = new java.util.HashMap<>();
        for (int i = 0; i < raw.length; i++) {
            int x = (int) (e.water[i] >> 32), z = (int) e.water[i];
            int[] t = PlanBuilder.turn(x, z, rot);
            boolean natural = naturalWater(level, anchor.getX() + t[0], anchor.getZ() + t[1], raw[i] + anchor.getY());
            e.waterY[i] = natural ? NATURAL : raw[i];
            lowest.merge(tile(x >> TILE, z >> TILE), raw[i], Math::min);
            if (natural) still.merge(tile(x >> TILE, z >> TILE), raw[i], Math::min);
        }
        for (int i = 0; i < e.bridges.length; i++) lowest.merge(tile((int) (e.bridges[i] >> 32) >> TILE, (int) e.bridges[i] >> TILE), e.bridgeY[i], Math::min);
        e.waterLevels = new int[raw.length];
        e.waterLevel = Integer.MIN_VALUE;
        for (int i = 0; i < raw.length; i++) {
            e.waterLevels[i] = levelAt(e, lowest, still, (int) (e.water[i] >> 32), (int) e.water[i]);
            e.waterLevel = Math.max(e.waterLevel, e.waterLevels[i]);
        }
        e.bridgeLevels = new int[e.bridges.length];
        for (int i = 0; i < e.bridges.length; i++) e.bridgeLevels[i] = levelAt(e, lowest, still, (int) (e.bridges[i] >> 32), (int) e.bridges[i]);
    }

    /** Le niveau de l'eau ici : celui de l'eau déjà là si elle est tout près, sinon un bloc sous la terre la plus basse des parcelles voisines. */
    private static int levelAt(UrbanExtras e, java.util.Map<Long, Integer> lowest, java.util.Map<Long, Integer> still, int x, int z) {
        int tx = x >> TILE, tz = z >> TILE;
        int low = Integer.MAX_VALUE, calm = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Integer l = lowest.get(tile(tx + dx, tz + dz)), c = still.get(tile(tx + dx, tz + dz));
                if (l != null) low = Math.min(low, l);
                if (c != null) calm = Math.min(calm, c);
            }
        }
        int level = calm != Integer.MAX_VALUE ? calm : low - 1;
        return Math.max(e.floor + 1, level);
    }

    private static final int TILE = 3;

    private static long tile(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Une case d'eau déjà là n'a pas besoin d'un pont creusé : elle y est. */
    private static boolean naturalAt(UrbanExtras e, int i) {
        return i < e.bridgeLevels.length && e.bridgeLevels[i] >= e.bridgeY[i];
    }

    /** Les blocs du dessous : l'eau, le sol, les arbres. Le reste de la ville se pose par-dessus. */
    static void before(TownPlan town, List<Blueprints.Placement> out) {
        UrbanExtras e = town.extras();
        NationStyle style = NationStyle.byName(town.style(), NationStyle.EUROPEENNE);
        for (int i = 0; i < e.water.length && i < e.waterY.length && i < e.waterLevels.length; i++) {
            if (e.waterY[i] != NATURAL) waterColumn(e, (int) (e.water[i] >> 32), (int) e.water[i], e.waterY[i], e.waterLevels[i], out);
        }
        for (int i = 0; i < e.bridges.length && i < e.bridgeY.length && i < e.bridgeLevels.length; i++) {
            if (!e.dryBridges() && !naturalAt(e, i)) waterColumn(e, (int) (e.bridges[i] >> 32), (int) e.bridges[i], e.bridgeY[i], e.bridgeLevels[i], out);
        }
        for (int i = 0; i < e.gardens.length && i < e.gardenY.length; i++) {
            int x = (int) (e.gardens[i] >> 32), z = (int) e.gardens[i];
            out.add(new Blueprints.Placement(x, e.gardenY[i], z, Blocks.GRASS_BLOCK.defaultBlockState(), false));
            int h = hash(x, z);
            if (h % 14 == 0) out.add(new Blueprints.Placement(x, e.gardenY[i] + 1, z, flower(h), false));
        }
        for (int i = 0; i < e.fields.length && i < e.fieldY.length; i++) {
            int x = (int) (e.fields[i] >> 32), z = (int) e.fields[i];
            fieldCell(e, x, z, e.fieldY[i], out);
        }
        for (int i = 0; i < e.trees.length && i < e.treeY.length; i++) {
            int x = (int) (e.trees[i] >> 32), z = (int) e.trees[i];
            tree(treeKind(e, style, x, z), x, e.treeY[i] + 1, z, hash(x, z), out);
        }
    }

    /** Les blocs du dessus : les ponts et les pontons, les lanternes. */
    static void after(TownPlan town, List<Blueprints.Placement> out) {
        UrbanExtras e = town.extras();
        BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int i = 0; i < e.bridges.length && i < e.bridgeY.length; i++) {
            int x = (int) (e.bridges[i] >> 32), z = (int) e.bridges[i];
            // Le tablier au niveau de la rue qu'il prolonge : au-dessus de l'eau déjà là, sinon à la hauteur du sol d'avant.
            int y = naturalAt(e, i) ? e.bridgeY[i] + 1 : e.bridgeY[i];
            out.add(new Blueprints.Placement(x, y, z, deck, false));
            out.add(new Blueprints.Placement(x, y + 1, z, Blocks.AIR.defaultBlockState(), false));
            out.add(new Blueprints.Placement(x, y + 2, z, Blocks.AIR.defaultBlockState(), false));
        }
        for (int i = 0; i < e.lamps.length && i < e.lampY.length; i++) {
            int x = (int) (e.lamps[i] >> 32), z = (int) e.lamps[i];
            int y = e.lampY[i] + 1;
            out.add(new Blueprints.Placement(x, y, z, Blocks.SPRUCE_FENCE.defaultBlockState(), false));
            out.add(new Blueprints.Placement(x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState(), false));
            out.add(new Blueprints.Placement(x, y + 2, z, Blocks.LANTERN.defaultBlockState(), false));
        }
    }

    private static void waterColumn(UrbanExtras e, int x, int z, int surface, int level, List<Blueprints.Placement> out) {
        // Une colonne trop haute reste de la terre : mieux vaut une rive abrupte qu'un canyon.
        if (surface - level > MAX_DIG) return;
        int depth = Math.max(1, Math.min(2, level - e.floor));
        for (int y = surface; y > level; y--) out.add(new Blueprints.Placement(x, y, z, Blocks.AIR.defaultBlockState(), false));
        for (int d = 0; d < depth; d++) out.add(new Blueprints.Placement(x, level - d, z, Blocks.WATER.defaultBlockState(), false));
        out.add(new Blueprints.Placement(x, level - depth, z, (x + z & 1) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState(), false));
    }

    private static void fieldCell(UrbanExtras e, int x, int z, int y, List<Blueprints.Placement> out) {
        // Un filet d'eau tous les neuf rangs : c'est ce qui garde la terre humide.
        if (Math.floorMod(x, 9) == 4) {
            out.add(new Blueprints.Placement(x, y, z, Blocks.WATER.defaultBlockState(), false));
            out.add(new Blueprints.Placement(x, y + 1, z, Blocks.AIR.defaultBlockState(), false));
            return;
        }
        out.add(new Blueprints.Placement(x, y, z, Blocks.FARMLAND.defaultBlockState(), false));
        out.add(new Blueprints.Placement(x, y + 1, z, crop(e.crop, x, z), false));
    }

    private static BlockState crop(int choice, int x, int z) {
        int kind = choice == 4 ? Math.floorMod(Math.floorDiv(x, 18) * 3 + Math.floorDiv(z, 24), 4) : choice;
        Block block = switch (kind) {
            case 1 -> Blocks.CARROTS;
            case 2 -> Blocks.POTATOES;
            case 3 -> Blocks.BEETROOTS;
            default -> Blocks.WHEAT;
        };
        if (block instanceof CropBlock c) {
            int age = c instanceof BeetrootBlock ? 3 : 7;
            return c.getStateForAge(Math.max(age - Math.floorMod(x * 31 + z * 17, 3), 0));
        }
        return block.defaultBlockState();
    }

    private static int hash(int x, int z) {
        return Math.floorMod((x * 73856093) ^ (z * 19349663), 1000);
    }

    private static BlockState flower(int h) {
        return switch (h % 5) {
            case 0 -> Blocks.POPPY.defaultBlockState();
            case 1 -> Blocks.DANDELION.defaultBlockState();
            case 2 -> Blocks.OXEYE_DAISY.defaultBlockState();
            case 3 -> Blocks.CORNFLOWER.defaultBlockState();
            default -> Blocks.AZURE_BLUET.defaultBlockState();
        };
    }

    /** L'essence d'un arbre, d'après le climat choisi : 0 selon la civilisation, 1 feuillus, 2 résineux, 3 mêlés, 4 désert, 5 grand froid. */
    private static int treeKind(UrbanExtras e, NationStyle style, int x, int z) {
        int climate = e.treeKind;
        if (climate == 0) climate = style == NationStyle.EGYPTIENNE ? 4 : style == NationStyle.NORDIQUE ? 2 : 1;
        return switch (climate) {
            case 2, 5 -> 1;
            case 3 -> hash(x, z) % 2 == 0 ? 0 : 1;
            case 4 -> 2;
            default -> hash(x, z) % 5 == 0 ? 3 : 0;
        };
    }

    /** Un arbre de quelques blocs : un chêne, un sapin, un acacia, un bouleau. Pas de feuilles qui se défont : elles sont posées pour rester. */
    private static void tree(int kind, int x, int y, int z, int h, List<Blueprints.Placement> out) {
        BlockState log = switch (kind) {
            case 1 -> Blocks.SPRUCE_LOG.defaultBlockState();
            case 2 -> Blocks.ACACIA_LOG.defaultBlockState();
            case 3 -> Blocks.BIRCH_LOG.defaultBlockState();
            default -> Blocks.OAK_LOG.defaultBlockState();
        };
        BlockState leaves = (switch (kind) {
            case 1 -> Blocks.SPRUCE_LEAVES;
            case 2 -> Blocks.ACACIA_LEAVES;
            case 3 -> Blocks.BIRCH_LEAVES;
            default -> Blocks.OAK_LEAVES;
        }).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int trunk = kind == 1 ? 6 + h % 3 : kind == 2 ? 3 + h % 2 : 4 + h % 2;
        if (kind == 1) {
            for (int dy = 2; dy <= trunk + 1; dy++) {
                int r = Math.max(0, (trunk + 2 - dy) / 2);
                r = Math.min(r, 2);
                disc(x, y + dy, z, r, leaves, out);
            }
        } else if (kind == 2) {
            disc(x, y + trunk, z, 3, leaves, out);
            disc(x, y + trunk + 1, z, 2, leaves, out);
        } else {
            disc(x, y + trunk - 1, z, 2, leaves, out);
            disc(x, y + trunk, z, 2, leaves, out);
            disc(x, y + trunk + 1, z, 1, leaves, out);
            out.add(new Blueprints.Placement(x, y + trunk + 2, z, leaves, false));
        }
        for (int dy = 0; dy < trunk; dy++) out.add(new Blueprints.Placement(x, y + dy, z, log, false));
    }

    private static void disc(int x, int y, int z, int r, BlockState state, List<Blueprints.Placement> out) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r + 1) out.add(new Blueprints.Placement(x + dx, y, z + dz, state, false));
            }
        }
    }

    /** La matière d'une rue, selon le réglage : 0 celle de la civilisation. */
    static BlockState street(UrbanExtras e, NationStyle style, int x, int z) {
        int h = hash(x, z) % 100;
        return switch (e.streetMaterial) {
            case 1 -> h < 80 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            case 2 -> h < 75 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case 3 -> h < 85 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            case 4 -> h < 85 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
            case 5 -> h < 70 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
            case 6 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
            default -> style.street(x, z);
        };
    }

    /** La matière d'un mur : 0 des pièces de pack (ses murs de pierre ne servent alors qu'à une citadelle), 1 pierre, 2 pierre taillée, 3 grès, 4 bois, 5 terre. */
    static BlockState wall(UrbanExtras e, int x, int z) {
        int h = hash(x, z) % 100;
        return switch (e.wallType) {
            case 2 -> h < 80 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case 3 -> h < 80 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
            case 4 -> Blocks.SPRUCE_LOG.defaultBlockState();
            case 5 -> Blocks.DIRT.defaultBlockState();
            default -> h < 75 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        };
    }

    /** L'enceinte en blocs : les murs, les tours plus hautes, et au-dessus des portes un linteau laissant passer. */
    static void walls(TownPlan town, List<Blueprints.Placement> out) {
        UrbanExtras e = town.extras();
        long[] walls = town.walls();
        int[] ys = town.wallY();
        int h = Math.max(3, e.wallHeight);
        boolean earth = e.wallType == 5;
        java.util.Set<Long> towers = new java.util.HashSet<>();
        for (long t : e.towers) towers.add(t);
        for (int i = 0; i < walls.length && i < ys.length; i++) {
            int x = (int) (walls[i] >> 32), z = (int) walls[i];
            BlockState st = wall(e, x, z);
            boolean tower = towers.contains(walls[i]);
            int top = tower ? h + 3 : h;
            out.add(new Blueprints.Placement(x, ys[i], z, st, true));
            for (int y = 1; y <= top; y++) out.add(new Blueprints.Placement(x, ys[i] + y, z, earth && y == top ? Blocks.GRASS_BLOCK.defaultBlockState() : st, false));
            if (!earth && Math.floorMod(x + z, 2) == 0) out.add(new Blueprints.Placement(x, ys[i] + top + 1, z, st, false));
        }
        // Le linteau d'une porte : le mur reprend au-dessus du passage.
        for (int i = 0; i < e.lintels.length && i < e.lintelY.length; i++) {
            int x = (int) (e.lintels[i] >> 32), z = (int) e.lintels[i];
            BlockState st = wall(e, x, z);
            for (int y = Math.min(h - 1, 4); y <= h; y++) out.add(new Blueprints.Placement(x, e.lintelY[i] + y, z, st, false));
        }
    }

}
