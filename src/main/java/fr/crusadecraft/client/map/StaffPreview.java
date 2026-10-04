package fr.crusadecraft.client.map;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.item.BornageStaffItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

import java.util.List;

/**
 * L'aperçu du bâton de bornage (P14), côté client : des particules aux couleurs du royaume le long du tracé en cours
 * (le côté qui fermera le polygone en pointillés), autour de la case où l'on marche, ou sur l'emprise de la
 * construction visée. Quelques dizaines de particules quatre fois par seconde : rien qui pèse.
 */
public final class StaffPreview {
    private static final int EVERY = 5;
    private static final int MAX_PARTICLES = 240;
    private static int tick;

    private StaffPreview() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || ++tick % EVERY != 0) return;
        ItemStack staff = BornageStaffItem.held(mc.player).orElse(ItemStack.EMPTY);
        if (staff.isEmpty()) return;
        int rgb = BornageStaffItem.color(staff);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.2f);
        int[] budget = {MAX_PARTICLES};
        switch (BornageStaffItem.mode(staff)) {
            case POLYGONE, RECTANGLE -> {
                List<BlockPos> pts = BornageStaffItem.points(staff);
                if (pts.isEmpty()) return;
                if (BornageStaffItem.mode(staff) == BornageStaffItem.Mode.RECTANGLE && pts.size() == 1) {
                    BlockPos a = pts.get(0), b = target(mc, a);
                    rectangle(mc, dust, a.getX(), a.getZ(), b.getX(), b.getZ(), budget);
                    return;
                }
                for (int i = 0; i + 1 < pts.size(); i++) line(mc, dust, pts.get(i), pts.get(i + 1), 1.0, budget);
                for (BlockPos p : pts) pillar(mc, dust, p, budget);
                // Le côté qui fermera le polygone, en pointillés, et le côté vers le bloc visé.
                if (pts.size() >= 2) line(mc, dust, pts.get(pts.size() - 1), pts.get(0), 3.0, budget);
                line(mc, dust, pts.get(pts.size() - 1), target(mc, pts.get(pts.size() - 1)), 2.0, budget);
            }
            case MARCHE -> {
                int cx = TerritoryMap.cellOf(mc.player.getX()), cz = TerritoryMap.cellOf(mc.player.getZ());
                rectangle(mc, dust, cx * 16, cz * 16, cx * 16 + 15, cz * 16 + 15, budget);
            }
            case CONSTRUIRE -> {
                if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
                SiteKind kind = BornageStaffItem.build(staff);
                int half = kind.size() / 2;
                BlockPos c = hit.getBlockPos();
                rectangle(mc, dust, c.getX() - half, c.getZ() - half, c.getX() + half, c.getZ() + half, budget);
            }
        }
    }

    /** Le bloc visé, ou à défaut le dernier coin. */
    private static BlockPos target(Minecraft mc, BlockPos fallback) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : fallback;
    }

    private static double ground(Minecraft mc, double x, double z) {
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)) + 0.2;
    }

    private static void line(Minecraft mc, DustParticleOptions dust, BlockPos a, BlockPos b, double step, int[] budget) {
        double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
        int n = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 0; i <= n && budget[0] > 0; i++) {
            double t = i / (double) n;
            double x = a.getX() + 0.5 + (b.getX() - a.getX()) * t, z = a.getZ() + 0.5 + (b.getZ() - a.getZ()) * t;
            mc.level.addParticle(dust, x, ground(mc, x, z), z, 0, 0, 0);
            budget[0]--;
        }
    }

    private static void rectangle(Minecraft mc, DustParticleOptions dust, int x1, int z1, int x2, int z2, int[] budget) {
        BlockPos a = new BlockPos(Math.min(x1, x2), 0, Math.min(z1, z2)), b = new BlockPos(Math.max(x1, x2), 0, Math.max(z1, z2));
        BlockPos c = new BlockPos(a.getX(), 0, b.getZ()), d = new BlockPos(b.getX(), 0, a.getZ());
        line(mc, dust, a, d, 1.0, budget);
        line(mc, dust, d, b, 1.0, budget);
        line(mc, dust, b, c, 1.0, budget);
        line(mc, dust, c, a, 1.0, budget);
    }

    /** Un coin posé : une petite colonne de particules, pour le retrouver de loin. */
    private static void pillar(Minecraft mc, DustParticleOptions dust, BlockPos p, int[] budget) {
        for (int y = 1; y <= 3 && budget[0] > 0; y++) {
            mc.level.addParticle(dust, p.getX() + 0.5, p.getY() + y, p.getZ() + 0.5, 0, 0, 0);
            budget[0]--;
        }
    }
}
