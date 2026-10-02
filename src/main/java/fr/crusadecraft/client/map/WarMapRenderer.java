package fr.crusadecraft.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.crusadecraft.block.WarMapBlock;
import fr.crusadecraft.block.WarMapBlockEntity;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * La Table de guerre : quand neuf cadres forment un carré de 3 × 3 sur un mur, chacun dessine son neuvième
 * de la carte du royaume, centrée sur la capitale. Un cadre isolé garde son simple parchemin.
 */
public class WarMapRenderer implements BlockEntityRenderer<WarMapBlockEntity> {
    private static final int SIZE = 192;
    private static final MapTexture TEXTURE = new MapTexture("war_map");
    private static int builtVersion = -1;

    public WarMapRenderer(BlockEntityRendererProvider.Context context) {}

    /** Position du cadre dans son carré : {colonne depuis la gauche, rangée depuis le bas}, ou null. */
    static int[] slot(Level level, BlockPos pos, Direction facing) {
        Direction right = facing.getCounterClockWise();
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                BlockPos origin = pos.relative(right, -dx).below(dy);
                if (complete(level, origin, right, facing)) return new int[]{dx, dy};
            }
        }
        return null;
    }

    private static boolean complete(Level level, BlockPos origin, Direction right, Direction facing) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                BlockState s = level.getBlockState(origin.relative(right, i).above(j));
                if (!s.is(ModBlocks.WAR_MAP.get()) || s.getValue(WarMapBlock.FACING) != facing) return false;
            }
        }
        return true;
    }

    private static boolean ensureTexture() {
        MapView view = ClientMapData.view();
        ClientMapData.refreshIfStale(60_000);
        if (view == null) return false;
        if (builtVersion != ClientMapData.version() || !TEXTURE.ready()) {
            double scale = MapRaster.fit(view, view.focusX(), view.focusZ(), SIZE, SIZE);
            TEXTURE.upload(MapRaster.render(view, SIZE, SIZE, view.focusX(), view.focusZ(), scale, false));
            builtVersion = ClientMapData.version();
        }
        return true;
    }

    @Override
    public void render(WarMapBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        Direction facing = be.getBlockState().getValue(WarMapBlock.FACING);
        int[] slot = slot(level, be.getBlockPos(), facing);
        if (slot == null || !ensureTexture()) return;
        float u0 = slot[0] / 3f, u1 = (slot[0] + 1) / 3f;
        float v0 = (2 - slot[1]) / 3f, v1 = (3 - slot[1]) / 3f;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180));
        // Face avant du cadre : à 1/16 du mur, un soupçon devant pour ne pas scintiller.
        pose.translate(0, 0, 0.5 - 1 / 16f - 0.002);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE.id()));
        float h = 0.5f - 1 / 64f;
        vertex(vc, m, n, -h, -h, u1, v1, light);
        vertex(vc, m, n, h, -h, u0, v1, light);
        vertex(vc, m, n, h, h, u0, v0, light);
        vertex(vc, m, n, -h, h, u1, v0, light);
        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int light) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0, 0, -1).endVertex();
    }
}
