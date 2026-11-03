package fr.crusadecraft.core.city;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reconnaître un bâtiment (P38) : depuis sa porte, on suit l'intérieur (l'air sous un toit) pièce après pièce et
 * étage après étage ; ce qu'on a parcouru, élargi d'un bloc pour les murs, est sa boîte. On y compte les meubles,
 * d'où une fonction proposée (une enclume et un fourneau : une forge). Rien n'est décidé ici : le joueur choisit.
 */
public final class BuildingScan {
    /** Au-delà, ce n'est plus un bâtiment mais une halle ouverte ou une rue couverte : on renonce. */
    public static final int MAX_CELLS = 12_000;
    /** Hauteur où l'on cherche un toit au-dessus d'une case. */
    static final int ROOF_SEARCH = 24;
    /** Une boîte tracée à la main ne dépasse pas ceci. */
    public static final int MAX_SIDE = 96, MAX_HEIGHT = 64;

    /** Ce qu'on a reconnu : la boîte, où se tenir, le sol, les étages, les meubles. */
    public record Result(BlockPos min, BlockPos max, BlockPos spot, int floor, int levels, Map<String, Integer> furniture) {}

    private BuildingScan() {}

    // ------------------------------------------------------------------ depuis une porte

    /**
     * Le bâtiment d'une porte (ou celui dont on vise un bloc depuis l'intérieur) : on essaie les deux côtés d'une
     * porte, et l'on garde l'intérieur le plus grand.
     */
    public static Optional<Result> fromClick(Level level, BlockPos clicked, Direction face) {
        BlockState st = level.getBlockState(clicked);
        BlockPos[] starts;
        if (st.getBlock() instanceof DoorBlock) {
            BlockPos lower = st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? clicked.below() : clicked;
            Direction f = st.getValue(DoorBlock.FACING);
            starts = new BlockPos[]{lower.relative(f), lower.relative(f.getOpposite())};
        } else {
            starts = new BlockPos[]{clicked.relative(face), clicked.above()};
        }
        LongOpenHashSet best = null;
        for (BlockPos s : starts) {
            LongOpenHashSet cells = flood(level, s);
            if (cells != null && (best == null || cells.size() > best.size())) best = cells;
        }
        return best == null ? Optional.empty() : Optional.of(result(level, best));
    }

    /** Le bâtiment où l'on se tient (P39, pour la commande) : son intérieur suivi depuis là. */
    public static Optional<Result> fromInside(Level level, BlockPos at) {
        LongOpenHashSet cells = flood(level, at);
        if (cells == null) cells = flood(level, at.above());
        return cells == null ? Optional.empty() : Optional.of(result(level, cells));
    }

    /** Une boîte tracée à la main, deux coins. */
    public static Optional<Result> fromBox(Level level, BlockPos a, BlockPos b) {
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        if (max.getX() - min.getX() >= MAX_SIDE || max.getZ() - min.getZ() >= MAX_SIDE || max.getY() - min.getY() >= MAX_HEIGHT) return Optional.empty();
        LongOpenHashSet walk = new LongOpenHashSet();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) if (standable(level, p)) walk.add(p.asLong());
        return Optional.of(stats(level, min, max, walk));
    }

    /**
     * L'intérieur à partir d'une case : l'air (et ce qui ne gêne pas le passage) sous un toit, portes intérieures
     * comprises. {@code null} si la case n'est pas à l'abri, ou si l'on déborde (une halle, une rue couverte).
     */
    static LongOpenHashSet flood(Level level, BlockPos start) {
        if (!passable(level, start) || !covered(level, start)) return null;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start.asLong());
        queue.enqueue(start.asLong());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            long cur = queue.dequeueLong();
            int x = BlockPos.getX(cur), y = BlockPos.getY(cur), z = BlockPos.getZ(cur);
            for (Direction d : Direction.values()) {
                m.set(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                long key = m.asLong();
                if (seen.contains(key) || level.isOutsideBuildHeight(m) || !level.isLoaded(m)) continue;
                if (!passable(level, m) || !covered(level, m)) continue;
                seen.add(key);
                if (seen.size() > MAX_CELLS) return null;
                queue.enqueue(key);
            }
        }
        return seen.size() >= 4 ? seen : null;
    }

    /** On y passe : l'air, une porte, une trappe, un portillon, un tapis, une plante, une torche. */
    static boolean passable(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return true;
        Block b = s.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return true;
        if (!s.getFluidState().isEmpty()) return false;
        VoxelShape shape = s.getCollisionShape(level, p);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.2;
    }

    /** Un toit au-dessus (un bloc qui n'est ni feuillage ni plante), dans les {@link #ROOF_SEARCH} blocs. */
    static boolean covered(Level level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        for (int dy = 1; dy <= ROOF_SEARCH; dy++) {
            m.setY(p.getY() + dy);
            if (level.isOutsideBuildHeight(m)) return false;
            BlockState s = level.getBlockState(m);
            if (s.isAir() || s.getBlock() instanceof LeavesBlock) continue;
            if (s.getCollisionShape(level, m).isEmpty()) continue;
            return true;
        }
        return false;
    }

    /** On y tient debout : deux cases de passage sur un sol. */
    public static boolean standable(Level level, BlockPos p) {
        if (!passable(level, p) || !passable(level, p.above())) return false;
        BlockState below = level.getBlockState(p.below());
        return !below.getCollisionShape(level, p.below()).isEmpty() && !(below.getBlock() instanceof DoorBlock);
    }

    /** La boîte de l'intérieur parcouru, élargie d'un bloc (les murs, le plancher), et le toit au-dessus. */
    static Result result(Level level, LongOpenHashSet cells) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        LongOpenHashSet walk = new LongOpenHashSet();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : cells) {
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            // La porte est dans le mur : elle ne pousse pas la boîte d'un bloc au-dehors.
            if (level.getBlockState(m.set(x, y, z)).getBlock() instanceof DoorBlock) continue;
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
            m.set(x, y, z);
            if (cells.contains(BlockPos.asLong(x, y + 1, z)) && standable(level, m)) walk.add(c);
        }
        BlockPos min = new BlockPos(x0 - 1, y0 - 1, z0 - 1);
        int top = y1 + 1;
        // Le toit : on monte tant qu'une bonne part de l'emprise est couverte.
        int area = (x1 - x0 + 3) * (z1 - z0 + 3);
        for (int y = y1 + 1; y <= y1 + 12; y++) {
            int filled = 0;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) if (!level.getBlockState(m.set(x, y, z)).isAir()) filled++;
            }
            if (filled * 4 < area) break;
            top = y;
        }
        BlockPos max = new BlockPos(x1 + 1, top, z1 + 1);
        return stats(level, min, max, walk.isEmpty() ? cells : walk);
    }

    /** Le sol, les étages, les meubles d'une boîte, et l'endroit où se tenir (au plus près du milieu, en bas). */
    static Result stats(Level level, BlockPos min, BlockPos max, LongOpenHashSet walk) {
        LongOpenHashSet columns = new LongOpenHashSet();
        java.util.Set<Integer> floors = new java.util.HashSet<>();
        double cx = (min.getX() + max.getX()) / 2.0, cz = (min.getZ() + max.getZ()) / 2.0;
        long spot = Long.MIN_VALUE;
        int spotY = Integer.MAX_VALUE;
        double spotD = Double.MAX_VALUE;
        for (long c : walk) {
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            columns.add(BlockPos.asLong(x, 0, z));
            floors.add(y);
            double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
            if (y < spotY || (y == spotY && d < spotD)) {
                spot = c;
                spotY = y;
                spotD = d;
            }
        }
        BlockPos at = spot == Long.MIN_VALUE ? BlockPos.containing(cx, min.getY() + 1, cz) : BlockPos.of(spot);
        return new Result(min, max, at, columns.size(), countLevels(floors), furniture(level, min, max));
    }

    /** Des hauteurs où l'on tient debout, les étages : deux hauteurs à moins de trois blocs sont un même étage. */
    static int countLevels(java.util.Set<Integer> heights) {
        java.util.List<Integer> sorted = new java.util.ArrayList<>(heights);
        sorted.sort(Integer::compare);
        int n = 0, last = 0;
        for (int y : sorted) {
            if (n == 0 || y - last >= 3) {
                n++;
                last = y;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ les meubles

    /** Les meubles d'une boîte, par sorte. */
    static Map<String, Integer> furniture(Level level, BlockPos min, BlockPos max) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            String k = furnitureKind(level.getBlockState(p));
            if (k != null) out.merge(k, 1, Integer::sum);
        }
        return out;
    }

    /** La sorte d'un meuble, ou {@code null} pour un bloc ordinaire. */
    static String furnitureKind(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof BedBlock) return s.getValue(BedBlock.PART) == BedPart.HEAD ? "lit" : null;
        if (b == Blocks.BLAST_FURNACE) return "haut_fourneau";
        if (b == Blocks.SMOKER) return "fumoir";
        if (b == Blocks.FURNACE) return "four";
        if (b instanceof AnvilBlock) return "enclume";
        if (b == Blocks.SMITHING_TABLE || b == Blocks.GRINDSTONE) return "forge";
        if (b instanceof LecternBlock) return "lutrin";
        if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF) return "livres";
        if (b instanceof BrewingStandBlock) return "alambic";
        if (b instanceof EnchantmentTableBlock) return "autel";
        if (b instanceof BellBlock) return "cloche";
        if (b instanceof BarrelBlock) return "tonneau";
        if (b instanceof ChestBlock) return "coffre";
        if (b == Blocks.HAY_BLOCK) return "foin";
        if (b instanceof ComposterBlock) return "composteur";
        if (b instanceof AbstractCauldronBlock) return "chaudron";
        if (b == Blocks.STONECUTTER || b == Blocks.LOOM || b == Blocks.FLETCHING_TABLE || b == Blocks.CARTOGRAPHY_TABLE) return "etabli_metier";
        if (b == Blocks.CRAFTING_TABLE) return "etabli";
        if (b instanceof CandleBlock) return "bougie";
        if (b == Blocks.TARGET) return "cible";
        return null;
    }

    /**
     * La fonction qu'on propose d'après les meubles (P38) : une cloche fait la mairie, une enclume près d'un fourneau
     * la forge, un alambic ou des bougies le temple… et, sans indice, une maison. Une proposition, jamais un choix.
     */
    public static BuildingFunction suggest(Map<String, Integer> f, int floor, int levels) {
        int beds = n(f, "lit");
        if (n(f, "cloche") > 0) return BuildingFunction.MAIRIE;
        int smith = n(f, "enclume") + n(f, "haut_fourneau") + n(f, "forge");
        if (n(f, "enclume") > 0 && smith >= 2 || n(f, "haut_fourneau") >= 2) return BuildingFunction.FORGE;
        if (n(f, "alambic") > 0 || n(f, "autel") > 0 || n(f, "bougie") >= 4) return BuildingFunction.TEMPLE;
        if (n(f, "livres") >= 8 || n(f, "lutrin") > 0 && n(f, "livres") >= 3) return BuildingFunction.BIBLIOTHEQUE;
        if (n(f, "cible") > 0) return BuildingFunction.CASERNE;
        if (n(f, "fumoir") > 0 && beds == 0) return BuildingFunction.BOUCHERIE;
        if (n(f, "four") >= 2 && beds == 0) return BuildingFunction.BOULANGERIE;
        if (n(f, "foin") >= 6) return BuildingFunction.ECURIE;
        if (n(f, "foin") >= 2 || n(f, "composteur") > 0) return BuildingFunction.FERME;
        if (n(f, "etabli_metier") > 0) return BuildingFunction.ATELIER;
        if (n(f, "chaudron") > 0 && n(f, "tonneau") >= 2 && beds == 0) return BuildingFunction.TAVERNE;
        if (n(f, "tonneau") + n(f, "coffre") >= 8 && beds == 0) return BuildingFunction.ENTREPOT;
        if (floor >= 300 && levels >= 3) return BuildingFunction.CHATEAU;
        return BuildingFunction.MAISON;
    }

    private static int n(Map<String, Integer> f, String k) {
        return f.getOrDefault(k, 0);
    }
}
