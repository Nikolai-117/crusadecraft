package fr.crusadecraft.core.build;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Les couleurs d'un royaume dans le monde : bannières, laine, tapis et lits à la teinture la plus proche. */
public final class Banners {

    private Banners() {}

    /** La teinture de Minecraft la plus proche d'une couleur de royaume. */
    public static DyeColor dye(int rgb) {
        DyeColor best = DyeColor.WHITE;
        long bestD = Long.MAX_VALUE;
        for (DyeColor c : DyeColor.values()) {
            int o = c.getTextColor();
            long dr = ((o >> 16) & 0xFF) - ((rgb >> 16) & 0xFF), dg = ((o >> 8) & 0xFF) - ((rgb >> 8) & 0xFF), db = (o & 0xFF) - (rgb & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** Une bannière debout, tournée vers {@code facing} (le côté qu'on voit de face). */
    public static BlockState standing(DyeColor dye, Direction facing) {
        int rotation = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 4;
            case NORTH -> 8;
            default -> 12;
        };
        return BannerBlock.byColor(dye).defaultBlockState().setValue(BannerBlock.ROTATION, rotation);
    }

    /** Une bannière accrochée à un mur, qui pend du côté {@code facing}. */
    public static BlockState wall(DyeColor dye, Direction facing) {
        return colored(dye, "wall_banner", Blocks.WHITE_WALL_BANNER).setValue(WallBannerBlock.FACING, facing);
    }

    public static BlockState wool(DyeColor dye) {
        return colored(dye, "wool", Blocks.WHITE_WOOL);
    }

    public static BlockState carpet(DyeColor dye) {
        return colored(dye, "carpet", Blocks.WHITE_CARPET);
    }

    public static Block bed(DyeColor dye) {
        return colored(dye, "bed", Blocks.WHITE_BED).getBlock();
    }

    private static BlockState colored(DyeColor dye, String suffix, Block fallback) {
        Block b = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("minecraft:" + dye.getName() + "_" + suffix));
        return (b == null || b == Blocks.AIR ? fallback : b).defaultBlockState();
    }
}
