package fr.crusadecraft.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.crusadecraft.CrusadeCraft;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Le fantôme du plan d'architecte (P35), comme dans Schematica : les blocs du plan dessinés en transparence là où il
 * sera posé. Les blocs (la surface seulement) arrivent du serveur au choix du plan ; le dessin est préparé une fois par
 * orientation, puis seulement déplacé à chaque image.
 */
public final class GhostRenderer {
    private static String ref = "";
    private static int[] positions = new int[0];
    private static BlockState[] states = new BlockState[0];
    private static VertexBuffer buffer;
    private static int builtRotation = -1;

    private GhostRenderer() {}

    /** Les blocs d'un plan, reçus du serveur (positions relatives, entrée au sud). */
    public static void set(String newRef, int[] packed, BlockState[] blockStates) {
        ref = newRef;
        positions = packed;
        states = blockStates;
        dispose();
    }

    public static String ref() {
        return ref;
    }

    public static int size() {
        return positions.length;
    }

    public static void dispose() {
        if (buffer != null) buffer.close();
        buffer = null;
        builtRotation = -1;
    }

    static int unpackX(int p) {
        return ((p >> 20) & 0x3FF) - 512;
    }

    static int unpackY(int p) {
        return ((p >> 10) & 0x3FF) - 512;
    }

    static int unpackZ(int p) {
        return (p & 0x3FF) - 512;
    }

    public static int pack(int x, int y, int z) {
        return ((x + 512) & 0x3FF) << 20 | ((y + 512) & 0x3FF) << 10 | ((z + 512) & 0x3FF);
    }

    private static int[] turn(int x, int z, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    /** Prépare le dessin pour cette orientation : chaque face cachée par un autre bloc du plan est sautée. */
    private static void build(int rotation, BlockPos tintAt) {
        dispose();
        Minecraft mc = Minecraft.getInstance();
        Rotation rot = Rotation.values()[Math.floorMod(rotation, 4)];
        Long2ObjectOpenHashMap<BlockState> map = new Long2ObjectOpenHashMap<>(positions.length);
        for (int i = 0; i < positions.length; i++) {
            int[] xz = turn(unpackX(positions[i]), unpackZ(positions[i]), rotation);
            map.put(BlockPos.asLong(xz[0], unpackY(positions[i]), xz[1]), states[i].rotate(rot));
        }
        Ghost level = new Ghost(map, mc.level, tintAt);
        BufferBuilder bb = new BufferBuilder(Math.max(4096, map.size() * 96));
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack ps = new PoseStack();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        RandomSource random = RandomSource.create(42);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (var e : map.long2ObjectEntrySet()) {
            BlockState st = e.getValue();
            if (st.getRenderShape() != RenderShape.MODEL) continue;
            pos.set(e.getLongKey());
            ps.pushPose();
            ps.translate(pos.getX(), pos.getY(), pos.getZ());
            try {
                dispatcher.renderBatched(st, pos, level, ps, bb, true, random, ModelData.EMPTY, null);
            } catch (RuntimeException ignored) {
                // un modèle qui refuse d'être dessiné hors du monde : sauté
            }
            ps.popPose();
        }
        BufferBuilder.RenderedBuffer rendered = bb.end();
        buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        builtRotation = Math.floorMod(rotation, 4);
        CrusadeCraft.LOGGER.debug("CrusadeCraft : fantôme de {} préparé ({} blocs)", ref, map.size());
    }

    /** Dessine le fantôme posé en {@code ground} (le sol du plan au niveau de ce bloc), tourné ainsi. */
    public static void render(PoseStack pose, Matrix4f projection, Camera cam, BlockPos ground, int rotation) {
        if (positions.length == 0) return;
        if (buffer == null || builtRotation != Math.floorMod(rotation, 4)) build(rotation, ground);
        if (buffer == null) return;
        pose.pushPose();
        pose.translate(ground.getX() - cam.getPosition().x, ground.getY() - cam.getPosition().y, ground.getZ() - cam.getPosition().z);
        RenderType type = RenderType.translucent();
        type.setupRenderState();
        RenderSystem.setShaderColor(0.85f, 0.95f, 1.0f, 0.55f);
        buffer.bind();
        buffer.drawWithShader(pose.last().pose(), projection, GameRenderer.getRendertypeTranslucentShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        type.clearRenderState();
        pose.popPose();
    }

    /** Le monde du fantôme : ses propres blocs, la lumière du jour pleine, les teintes du lieu où il sera posé. */
    private record Ghost(Long2ObjectOpenHashMap<BlockState> blocks, BlockAndTintGetter world, BlockPos tintAt) implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState s = blocks.get(pos.asLong());
            return s == null ? Blocks.AIR.defaultBlockState() : s;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public float getShade(Direction d, boolean shade) {
            return world.getShade(d, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return world.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return layer == LightLayer.SKY ? 15 : 0;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15 - darkening;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return world.getBlockTint(tintAt, resolver);
        }

        @Override
        public int getHeight() {
            return world.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return world.getMinBuildHeight();
        }
    }
}
