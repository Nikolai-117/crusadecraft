package fr.crusadecraft.client.city;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.crusadecraft.client.screen.BuildingScreen;
import fr.crusadecraft.client.screen.TownScreen;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityStaff;
import fr.crusadecraft.item.IntendantStaffItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.Util;
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
import java.util.UUID;

/**
 * Le bâton d'intendant côté client (P38) : tant qu'on le tient, les cadres des bâtiments de la ville où l'on est,
 * colorés selon leur fonction (en gris pâle, ceux qui n'en ont pas encore), le bâtiment reconnu en brouillon en or,
 * et le premier coin d'une boîte tracée à la main. Les cadres sont redemandés toutes les secondes et demie.
 */
public final class CityClient {
    private static final long ASK_EVERY = 1500;
    private static BuildingPackets.Outlines current;
    private static long askedAt;

    private CityClient() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (IntendantStaffItem.held(mc.player).isEmpty()) {
            current = null;
            return;
        }
        long now = Util.getMillis();
        if (now - askedAt < ASK_EVERY) return;
        askedAt = now;
        CrusadeNetwork.toServer(new BuildingPackets.OutlinesRequest());
    }

    public static void outlines(BuildingPackets.Outlines p) {
        current = p;
    }

    public static Optional<BuildingPackets.Outlines> current() {
        return Optional.ofNullable(current);
    }

    /** Redemander tout de suite (après une décision). */
    public static void refresh() {
        askedAt = 0;
    }

    public static void openBuilding(UUID site, String city, BuildingPackets.View view, boolean draft) {
        Minecraft.getInstance().setScreen(new BuildingScreen(site, city, view, draft));
    }

    /** P39 : fonder une ville ici — son nom, puis le serveur fait le reste. */
    public static void openFound(int radius) {
        Minecraft.getInstance().setScreen(new fr.crusadecraft.client.screen.NameForm(null, net.minecraft.network.chat.Component.translatable(
                "crusadecraft.city.found.title", radius), "", name -> {
                    CrusadeNetwork.toServer(new BuildingPackets.Found(name, radius));
                    refresh();
                }));
    }

    public static void openCity(BuildingPackets.OpenCity p) {
        Minecraft.getInstance().setScreen(new TownScreen(p));
    }

    /** Le bâtiment visé (le plus petit qui contient le bloc regardé), pour le HUD. */
    public static Optional<BuildingPackets.Outline> aimed() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return Optional.empty();
        BlockPos p = hit.getBlockPos();
        BuildingPackets.Outline best = null;
        long bestVol = Long.MAX_VALUE;
        for (BuildingPackets.Outline o : current.outlines()) {
            if (p.getX() < o.min().getX() || p.getX() > o.max().getX() || p.getY() < o.min().getY() || p.getY() > o.max().getY()
                    || p.getZ() < o.min().getZ() || p.getZ() > o.max().getZ()) continue;
            long vol = (long) (o.max().getX() - o.min().getX() + 1) * (o.max().getY() - o.min().getY() + 1) * (o.max().getZ() - o.min().getZ() + 1);
            if (vol < bestVol) {
                best = o;
                bestVol = vol;
            }
        }
        return Optional.ofNullable(best);
    }

    /** La couleur d'une fonction (gris : aucune). */
    public static int color(int function) {
        BuildingFunction f = BuildingFunction.byOrdinal(function);
        return f == null ? 0x9A9A9A : f.color();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Optional<ItemStack> staff = IntendantStaffItem.held(mc.player);
        if (staff.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        Camera cam = event.getCamera();
        pose.pushPose();
        pose.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BuildingPackets.Outline aimed = aimed().orElse(null);
        if (current != null) {
            for (BuildingPackets.Outline o : current.outlines()) {
                int rgb = o.function() >= 0 ? color(o.function()) : 0xB8B8B8;
                float a = o.function() >= 0 ? 1f : 0.55f;
                AABB box = box(o.min(), o.max(), o == aimed ? 0.06 : 0.02);
                LevelRenderer.renderLineBox(pose, lines, box, r(rgb), g(rgb), b(rgb), a);
                if (o == aimed) LevelRenderer.renderLineBox(pose, lines, box.inflate(0.05), r(rgb), g(rgb), b(rgb), a);
            }
            if (current.draftMin() != null) {
                LevelRenderer.renderLineBox(pose, lines, box(current.draftMin(), current.draftMax(), 0.08), 0.95f, 0.8f, 0.35f, 1f);
            }
        }
        CityStaff.cornerA(staff.get()).ifPresent(c -> {
            LevelRenderer.renderLineBox(pose, lines, new AABB(c).inflate(0.03), 0.95f, 0.8f, 0.35f, 1f);
            // P39.2 : la boîte suit le viseur jusqu'au second coin, comme l'outil de scan de MineColonies.
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos b = hit.getBlockPos();
                BlockPos lo = new BlockPos(Math.min(c.getX(), b.getX()), Math.min(c.getY(), b.getY()), Math.min(c.getZ(), b.getZ()));
                BlockPos hi = new BlockPos(Math.max(c.getX(), b.getX()), Math.max(c.getY(), b.getY()), Math.max(c.getZ(), b.getZ()));
                LevelRenderer.renderLineBox(pose, lines, box(lo, hi, 0.04), 0.95f, 0.8f, 0.35f, 0.8f);
            }
        });
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static AABB box(BlockPos min, BlockPos max, double grow) {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1).inflate(grow);
    }

    private static float r(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }

    private static float g(int rgb) { return ((rgb >> 8) & 0xFF) / 255f; }

    private static float b(int rgb) { return (rgb & 0xFF) / 255f; }
}
