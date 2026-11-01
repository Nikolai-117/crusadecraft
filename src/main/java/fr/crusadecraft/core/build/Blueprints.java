package fr.crusadecraft.core.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Les plans des constructions (P14), dessinés par le code : médiévaux, en pierre et en sapin, aux bannières et aux
 * couleurs du royaume. Un plan est rejoué à l'identique (rien de tiré au hasard), si bien qu'un chantier interrompu
 * reprend où il en était.
 *
 * <p>Chaque plan est dessiné l'entrée au sud (+z), le sol du bâtiment au niveau du bloc visé (y = 0), puis tourné
 * vers celui qui le pose. Les premières étapes posent les fondations et dégagent la place.
 */
public final class Blueprints {

    /**
     * Une étape du chantier : un bloc à poser (relatif au point d'ancrage, avant rotation), ou une colonne de
     * fondation ({@code foundation}) qui comble le terrain sous le sol jusqu'à la roche.
     */
    public record Placement(int x, int y, int z, BlockState state, boolean foundation) {}

    private Blueprints() {}

    // ------------------------------------------------------------------ matériaux

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState STONE_WALL = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState OAK = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();
    private static final BlockState BARREL = Blocks.BARREL.defaultBlockState();
    private static final BlockState CAMPFIRE = Blocks.CAMPFIRE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SLAB = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

    private static BlockState ladder(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
    }

    /** Un peu de mousse et de fissures dans la pierre, toujours aux mêmes endroits. */
    private static BlockState weathered(int x, int y, int z) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        int r = Math.floorMod(h, 11);
        return r == 0 ? MOSSY : r == 1 ? CRACKED : STONE;
    }

    /** Un plan en cours de dessin. */
    private static final class Plan {
        final List<Placement> foundation = new ArrayList<>();
        final List<Placement> clearing = new ArrayList<>();
        final List<Placement> blocks = new ArrayList<>();
        final List<Placement> last = new ArrayList<>();

        void set(int x, int y, int z, BlockState s) {
            blocks.add(new Placement(x, y, z, s, false));
        }

        /** Posé à la fin : bannières, lanternes, portes, lits (ce qui demande un support déjà en place). */
        void finish(int x, int y, int z, BlockState s) {
            last.add(new Placement(x, y, z, s, false));
        }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) set(x, y, z, s);
        }

        /** Les murs d'un rectangle (son pourtour), de y1 à y2. */
        void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, s);
                }
            }
        }

        /** Des murs de pierre patinée. */
        void stoneWalls(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) set(x, y, z, weathered(x, y, z));
                }
            }
        }

        /** Des créneaux : un merlon sur deux le long du pourtour. */
        void crenels(int x1, int y, int z1, int x2, int z2, BlockState s) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                if ((x == x1 || x == x2 || z == z1 || z == z2) && Math.floorMod(x + z, 2) == 0) set(x, y, z, s);
            }
        }

        void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) clearing.add(new Placement(x, y, z, AIR, false));
        }

        void found(int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) foundation.add(new Placement(x, -1, z, COBBLE, true));
        }

        /** Retire du dégagement ce que le plan pose de toute façon : on ne vide pas un bloc pour le remplir aussitôt. */
        List<Placement> all() {
            java.util.Set<Long> placed = new java.util.HashSet<>();
            for (Placement p : blocks) placed.add(BlockPos.asLong(p.x(), p.y(), p.z()));
            for (Placement p : last) placed.add(BlockPos.asLong(p.x(), p.y(), p.z()));
            List<Placement> out = new ArrayList<>(foundation);
            for (Placement p : clearing) if (!placed.contains(BlockPos.asLong(p.x(), p.y(), p.z()))) out.add(p);
            List<Placement> sorted = new ArrayList<>(blocks);
            sorted.sort((a, b) -> Integer.compare(a.y(), b.y()));
            out.addAll(sorted);
            out.addAll(last);
            return Collections.unmodifiableList(out);
        }
    }

    // ------------------------------------------------------------------ plans

    /** Le plan d'une construction, l'entrée au sud, aux couleurs de {@code dye}. */
    public static List<Placement> plan(SiteKind kind, DyeColor dye) {
        Plan p = new Plan();
        switch (kind) {
            case BORNE -> borne(p, dye);
            case TOUR -> tower(p, dye);
            case CAMP -> camp(p, dye);
            case AVANT_POSTE -> outpost(p, dye);
            case FORT -> fort(p, dye);
            case DONJON -> keep(p, dye);
            case VILLAGE -> village(p, dye);
            case QUARTIER -> quarter(p, dye);
        }
        return p.all();
    }

    private static void borne(Plan p, DyeColor dye) {
        p.found(0, 0, 0, 0);
        p.clear(0, 1, 0, 0, 4, 0);
        p.set(0, 0, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(0, 1, 0, STONE);
        p.set(0, 2, 0, STONE);
        p.finish(0, 3, 0, Banners.standing(dye, Direction.SOUTH));
    }

    private static void tower(Plan p, DyeColor dye) {
        p.found(-2, -2, 2, 2);
        p.clear(-2, 1, -2, 2, 12, 2);
        p.box(-2, 0, -2, 2, 0, 2, COBBLE);
        p.stoneWalls(-2, 1, -2, 2, 7, 2);
        for (int y = 1; y <= 7; y++) for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) p.set(c[0], y, c[1], LOG);
        // L'entrée au sud, des meurtrières, l'échelle contre le mur nord.
        p.set(0, 1, 2, AIR);
        p.set(0, 2, 2, AIR);
        for (int[] w : new int[][]{{0, -2}, {-2, 0}, {2, 0}}) p.set(w[0], 4, w[1], GLASS);
        p.box(-1, 8, -1, 1, 8, 1, PLANKS);
        p.walls(-2, 8, -2, 2, 8, 2, STONE);
        for (int y = 1; y <= 8; y++) p.set(0, y, -1, ladder(Direction.SOUTH));
        p.crenels(-2, 9, -2, 2, 2, STONE_WALL);
        p.finish(0, 9, 0, Banners.standing(dye, Direction.SOUTH));
        p.finish(0, 5, 3, Banners.wall(dye, Direction.SOUTH));
        p.finish(1, 9, 1, LANTERN);
    }

    private static void tent(Plan p, int x0, int z1, int z2, DyeColor dye) {
        BlockState wool = Banners.wool(dye);
        for (int z = z1; z <= z2; z++) {
            p.set(x0 - 1, 1, z, wool);
            p.set(x0 + 1, 1, z, wool);
            p.set(x0, 2, z, wool);
        }
    }

    private static void camp(Plan p, DyeColor dye) {
        p.clear(-5, 1, -5, 5, 4, 5);
        tent(p, -3, -4, -1, dye);
        tent(p, 3, -4, -1, dye);
        tent(p, -3, 2, 4, dye);
        p.set(0, 1, 0, CAMPFIRE);
        p.set(-1, 1, 1, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        p.set(1, 1, -1, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        p.set(4, 1, 3, HAY);
        p.set(4, 2, 3, HAY);
        p.set(4, 1, 4, BARREL);
        p.set(3, 1, 4, BARREL);
        p.set(0, 1, 4, LOG);
        p.set(0, 2, 4, LOG);
        p.finish(0, 3, 4, Banners.standing(dye, Direction.SOUTH));
        p.set(5, 1, 0, FENCE);
        p.finish(5, 2, 0, LANTERN);
    }

    private static void outpost(Plan p, DyeColor dye) {
        p.clear(-7, 1, -7, 7, 9, 7);
        // La palissade, la porte au sud.
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            if (x != -7 && x != 7 && z != -7 && z != 7) continue;
            boolean gate = z == 7 && Math.abs(x) <= 1;
            p.found(x, z, x, z);
            if (gate) continue;
            p.box(x, 0, z, x, 3, z, LOG);
            p.set(x, 4, z, Math.abs(x) == 2 && z == 7 ? LOG : FENCE);
        }
        p.finish(-2, 5, 7, Banners.standing(dye, Direction.SOUTH));
        p.finish(2, 5, 7, Banners.standing(dye, Direction.SOUTH));
        for (int z = 1; z <= 7; z++) p.set(0, 0, z, PATH);
        // La tour de guet, au nord.
        p.found(-1, -6, 1, -4);
        for (int[] c : new int[][]{{-1, -6}, {1, -6}, {-1, -4}, {1, -4}}) p.box(c[0], 0, c[1], c[0], 4, c[1], LOG);
        for (int y = 1; y <= 4; y++) {
            p.set(0, y, -6, PLANKS);
            p.set(-1, y, -5, PLANKS);
            p.set(1, y, -5, PLANKS);
            p.set(0, y, -4, PLANKS);
        }
        p.box(-1, 5, -6, 1, 5, -4, PLANKS);
        for (int y = 1; y <= 5; y++) p.set(0, y, -3, ladder(Direction.SOUTH));
        for (int[] f : new int[][]{{-1, -6}, {0, -6}, {1, -6}, {-1, -5}, {1, -5}, {-1, -4}, {1, -4}}) p.set(f[0], 6, f[1], FENCE);
        p.finish(0, 6, -5, Banners.standing(dye, Direction.SOUTH));
        // La vie du camp.
        p.set(0, 1, 0, CAMPFIRE);
        p.set(5, 1, 5, BARREL);
        p.set(5, 1, 4, BARREL);
        p.set(-5, 1, 5, HAY);
        p.set(-5, 1, 0, FENCE);
        p.finish(-5, 2, 0, LANTERN);
        p.set(5, 1, -1, FENCE);
        p.finish(5, 2, -1, LANTERN);
        tent(p, 4, -5, -3, dye);
    }

    private static void fort(Plan p, DyeColor dye) {
        p.found(-12, -12, 12, 12);
        p.clear(-12, 1, -12, 12, 14, 12);
        // Les remparts, la porte au sud.
        p.stoneWalls(-12, 0, -12, 12, 5, 12);
        p.crenels(-12, 6, -12, 12, 12, STONE_WALL);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) p.set(x, y, 12, AIR);
        p.finish(-2, 4, 13, Banners.wall(dye, Direction.SOUTH));
        p.finish(2, 4, 13, Banners.wall(dye, Direction.SOUTH));
        // Les tours d'angle.
        for (int[] c : new int[][]{{-12, -12}, {8, -12}, {-12, 8}, {8, 8}}) {
            p.stoneWalls(c[0], 0, c[1], c[0] + 4, 8, c[1] + 4);
            p.crenels(c[0], 9, c[1], c[0] + 4, c[1] + 4, STONE_WALL);
            p.box(c[0] + 1, 6, c[1] + 1, c[0] + 3, 6, c[1] + 3, PLANKS);
        }
        // Le donjon central.
        p.box(-3, 0, -3, 3, 0, 3, COBBLE);
        p.stoneWalls(-3, 1, -3, 3, 9, 3);
        for (int y = 1; y <= 9; y++) for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) p.set(c[0], y, c[1], LOG);
        p.set(0, 1, 3, AIR);
        p.set(0, 2, 3, AIR);
        p.box(-2, 5, -2, 2, 5, 2, PLANKS);
        p.box(-2, 10, -2, 2, 10, 2, PLANKS);
        p.walls(-3, 10, -3, 3, 10, 3, STONE);
        for (int y = 1; y <= 10; y++) p.set(0, y, -2, ladder(Direction.SOUTH));
        p.crenels(-3, 11, -3, 3, 3, STONE_WALL);
        for (int[] w : new int[][]{{0, -3}, {-3, 0}, {3, 0}}) {
            p.set(w[0], 3, w[1], GLASS);
            p.set(w[0], 7, w[1], GLASS);
        }
        p.finish(0, 11, 0, Banners.standing(dye, Direction.SOUTH));
        p.finish(0, 6, 4, Banners.wall(dye, Direction.SOUTH));
        // La cour : un chemin de la porte au donjon, des lanternes, des réserves.
        for (int z = 4; z <= 11; z++) p.set(0, 0, z, PATH);
        for (int[] l : new int[][]{{-6, 6}, {6, 6}, {-6, -6}, {6, -6}}) {
            p.set(l[0], 1, l[1], FENCE);
            p.finish(l[0], 2, l[1], LANTERN);
        }
        p.set(-9, 1, 2, HAY);
        p.set(-9, 2, 2, HAY);
        p.set(9, 1, 2, BARREL);
        p.set(9, 1, 3, BARREL);
    }

    private static void keep(Plan p, DyeColor dye) {
        p.found(-5, -5, 5, 5);
        p.clear(-5, 1, -5, 5, 17, 5);
        p.box(-5, 0, -5, 5, 0, 5, STONE);
        p.stoneWalls(-5, 1, -5, 5, 13, 5);
        for (int y = 1; y <= 3; y++) p.set(0, y, 5, AIR);
        p.box(-4, 5, -4, 4, 5, 4, PLANKS);
        p.box(-4, 10, -4, 4, 10, 4, PLANKS);
        p.box(-4, 14, -4, 4, 14, 4, STONE);
        p.walls(-5, 14, -5, 5, 14, 5, STONE);
        for (int y = 1; y <= 14; y++) p.set(0, y, -4, ladder(Direction.SOUTH));
        p.crenels(-5, 15, -5, 5, 5, STONE_WALL);
        for (int y : new int[]{3, 8, 12}) {
            for (int[] w : new int[][]{{0, -5}, {-5, 0}, {5, 0}}) p.set(w[0], y, w[1], GLASS);
            if (y > 3) p.set(0, y, 5, GLASS);
        }
        // Le trône, au fond de la salle, et un tapis aux couleurs du royaume depuis la porte.
        p.set(0, 1, -3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        for (int z = -2; z <= 4; z++) p.finish(0, 1, z, Banners.carpet(dye));
        p.finish(-2, 1, -3, Banners.standing(dye, Direction.SOUTH));
        p.finish(2, 1, -3, Banners.standing(dye, Direction.SOUTH));
        for (int[] l : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) p.finish(l[0], 6, l[1], LANTERN);
        p.finish(0, 9, 6, Banners.wall(dye, Direction.SOUTH));
        p.finish(0, 9, -6, Banners.wall(dye, Direction.NORTH));
        p.finish(6, 9, 0, Banners.wall(dye, Direction.EAST));
        p.finish(-6, 9, 0, Banners.wall(dye, Direction.WEST));
        p.finish(0, 15, 0, Banners.standing(dye, Direction.SOUTH));
    }

    private static void house(Plan p, int hx, int hz, Direction door, DyeColor dye) {
        p.found(hx - 2, hz - 2, hx + 2, hz + 2);
        p.clear(hx - 2, 1, hz - 2, hx + 2, 6, hz + 2);
        p.box(hx - 2, 0, hz - 2, hx + 2, 0, hz + 2, PLANKS);
        p.walls(hx - 2, 1, hz - 2, hx + 2, 3, hz + 2, OAK);
        for (int y = 1; y <= 3; y++) for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) p.set(hx + c[0], y, hz + c[1], LOG);
        int dx = door == Direction.EAST ? 2 : door == Direction.WEST ? -2 : 0, dz = door == Direction.SOUTH ? 2 : door == Direction.NORTH ? -2 : 0;
        p.set(hx + dx, 1, hz + dz, AIR);
        p.set(hx + dx, 2, hz + dz, AIR);
        BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, door.getOpposite())
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        p.finish(hx + dx, 1, hz + dz, lower);
        p.finish(hx + dx, 2, hz + dz, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Des fenêtres sur les trois autres côtés.
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            if (d == door) continue;
            p.set(hx + d.getStepX() * 2, 2, hz + d.getStepZ() * 2, GLASS);
        }
        p.box(hx - 2, 4, hz - 2, hx + 2, 4, hz + 2, PLANKS);
        p.box(hx - 1, 5, hz - 1, hx + 1, 5, hz + 1, Banners.wool(dye));
        p.set(hx, 6, hz, SLAB);
        // Un lit, la tête au nord, et un tonneau surmonté d'une lanterne.
        Block bed = Banners.bed(dye);
        int bx = door == Direction.EAST ? hx - 1 : hx + 1;
        p.finish(bx, 1, hz + 1, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
        p.finish(bx, 1, hz, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
        int ox = door == Direction.EAST ? hx - 1 : hx + 1;
        p.set(ox, 1, hz - 1, BARREL);
        p.finish(ox, 2, hz - 1, LANTERN);
    }

    private static void village(Plan p, DyeColor dye) {
        p.clear(-12, 1, -12, 12, 3, 12);
        // Les chemins en croix et le puits au centre.
        for (int i = -11; i <= 11; i++) {
            if (Math.abs(i) <= 1) continue;
            p.set(0, 0, i, PATH);
            p.set(i, 0, 0, PATH);
        }
        p.found(-1, -1, 1, 1);
        p.box(-1, 0, -1, 1, 0, 1, COBBLE);
        p.set(0, -1, 0, COBBLE);
        p.set(0, 0, 0, WATER);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) p.box(c[0], 1, c[1], c[0], 2, c[1], FENCE);
        for (int[] e : new int[][]{{0, -1}, {-1, 0}, {1, 0}, {0, 1}}) p.set(e[0], 1, e[1], COBBLE);
        p.box(-1, 3, -1, 1, 3, 1, SLAB);
        // Quatre maisons qui ouvrent sur les chemins.
        house(p, -7, -7, Direction.EAST, dye);
        house(p, 7, -7, Direction.WEST, dye);
        house(p, -7, 7, Direction.EAST, dye);
        house(p, 7, 7, Direction.WEST, dye);
        for (int[] l : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            p.box(l[0], 1, l[1], l[0], 2, l[1], FENCE);
            p.finish(l[0], 3, l[1], LANTERN);
        }
        p.set(2, 1, 4, LOG);
        p.set(2, 2, 4, LOG);
        p.finish(2, 3, 4, Banners.standing(dye, Direction.SOUTH));
    }

    /** Un quartier (P28) : deux maisons côte à côte qui ouvrent sur une rue, et une lanterne au bout. */
    private static void quarter(Plan p, DyeColor dye) {
        p.clear(-6, 1, -6, 6, 3, 6);
        for (int x = -5; x <= 5; x++) p.set(x, 0, 4, PATH);
        for (int z = 5; z <= 6; z++) p.set(0, 0, z, PATH);
        house(p, -3, 0, Direction.SOUTH, dye);
        house(p, 3, 0, Direction.SOUTH, dye);
        p.box(-6, 1, 5, -6, 2, 5, FENCE);
        p.finish(-6, 3, 5, LANTERN);
        p.box(6, 1, 5, 6, 2, 5, FENCE);
        p.finish(6, 3, 5, LANTERN);
    }

    // ------------------------------------------------------------------ rotation

    /** La rotation qui tourne l'entrée (dessinée au sud) vers {@code front}. */
    public static Rotation rotation(Direction front) {
        return switch (front) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** La position dans le monde d'une étape, une fois le plan tourné et posé à l'ancrage. */
    public static BlockPos place(BlockPos anchor, Placement p, Rotation r) {
        int x = p.x(), z = p.z();
        int rx = switch (r) {
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
            default -> x;
        };
        int rz = switch (r) {
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
            default -> z;
        };
        return anchor.offset(rx, p.y(), rz);
    }
}
