package fr.crusadecraft.core.city;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Pour le banc d'essai : savoir si l'on tient debout quelque part. */
public final class BuildingScanAccess {
    private BuildingScanAccess() {}

    public static java.util.List<BlockPos> outdoors(net.minecraft.server.level.ServerLevel level, fr.crusadecraft.core.build.Site site) {
        return CityService.outdoorSpots(level, site);
    }

    public static boolean standable(Level level, BlockPos p) {
        return BuildingScan.standable(level, p);
    }
}
