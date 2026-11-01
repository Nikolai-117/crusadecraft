package fr.crusadecraft.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.Optional;

/**
 * L'aperçu du plan d'architecte (P34), côté client : le cadre de la boîte du plan là où le clic le posera, son sol
 * au niveau du bloc visé, et un liseré du côté de l'entrée. Le cadre seul, pas les blocs : le plan vit sur le
 * serveur.
 */
public final class PlanPreview {
    private PlanPreview() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack tool = PlanItem.held(mc.player).orElse(ItemStack.EMPTY);
        if (tool.isEmpty()) return;
        Optional<PlanItem.Choice> c = PlanItem.choice(tool);
        if (c.isEmpty() || c.get().sizeX() <= 0) return;
        Optional<BlockHitResult> aimed = PlanItem.aim(mc.player);
        if (aimed.isEmpty()) return;
        BlockHitResult hit = aimed.get();
        PlanItem.Choice choice = c.get();
        BlockPos ground = hit.getBlockPos();
        int[] box = bounds(choice.sizeX(), choice.sizeZ(), choice.rotation());
        double y0 = ground.getY() - choice.groundY(), y1 = y0 + choice.sizeY();
        AABB all = new AABB(ground.getX() + box[0], y0, ground.getZ() + box[1], ground.getX() + box[2] + 1, y1, ground.getZ() + box[3] + 1);
        int[] front = frontEdge(choice.sizeX(), choice.sizeZ(), choice.rotation());
        AABB door = new AABB(ground.getX() + front[0], ground.getY() + 1, ground.getZ() + front[1],
                ground.getX() + front[2] + 1, ground.getY() + 1.15, ground.getZ() + front[3] + 1);

        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        pose.pushPose();
        pose.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines, all, 0.79f, 0.65f, 0.36f, 1.0f);
        LevelRenderer.renderLineBox(pose, lines, door, 0.95f, 0.35f, 0.30f, 1.0f);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** {x0, z0, x1, z1} de l'emprise tournée, relatifs au bloc visé (comme les plans : milieu, entrée au sud avant rotation). */
    static int[] bounds(int sx, int sz, int rotation) {
        int minX = -(sx / 2), maxX = sx - 1 - sx / 2, minZ = -(sz / 2), maxZ = sz - 1 - sz / 2;
        int[] a = turn(minX, minZ, rotation), b = turn(maxX, maxZ, rotation);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /** Le bord de l'entrée (le côté sud avant rotation), une rangée de blocs. */
    static int[] frontEdge(int sx, int sz, int rotation) {
        int minX = -(sx / 2), maxX = sx - 1 - sx / 2, maxZ = sz - 1 - sz / 2;
        int[] a = turn(minX, maxZ, rotation), b = turn(maxX, maxZ, rotation);
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1])};
    }

    /** Comme les plans : quart de tour horaire (x, z) → (−z, x). */
    static int[] turn(int x, int z, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }
}
