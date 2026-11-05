package fr.crusadecraft.client.skin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Un PNJ qui porte un skin importé (P47) est dessiné comme un joueur : même modèle, mêmes gestes, bras fins pour un skin
 * « Alex ». On prend simplement la place du dessin de Comes Alive, qui compose ses villageois de couches et ne saurait pas
 * afficher un skin entier ; il reprend la main dès que le skin est retiré.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkinRender {
    private static Skinned wide, slim;

    private SkinRender() {}

    private static final class Skinned extends HumanoidMobRenderer<Mob, PlayerModel<Mob>> {
        Skinned(EntityRendererProvider.Context ctx, boolean thin) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(thin ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), thin), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(Mob entity) {
            String hash = SkinClient.hashOf(entity.getUUID());
            ResourceLocation rl = hash == null ? null : SkinClient.texture(hash);
            return rl == null ? new ResourceLocation("textures/entity/player/wide/steve.png") : rl;
        }

        @Override
        protected void scale(Mob entity, PoseStack pose, float partial) {
            pose.scale(0.9375f, 0.9375f, 0.9375f);
        }
    }

    private static Skinned renderer(boolean thin) {
        if ((thin ? slim : wide) == null) {
            Minecraft mc = Minecraft.getInstance();
            EntityRendererProvider.Context ctx = new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderer(),
                    mc.getEntityRenderDispatcher().getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font);
            if (thin) slim = new Skinned(ctx, true);
            else wide = new Skinned(ctx, false);
        }
        return thin ? slim : wide;
    }

    /** Les modèles changent avec les ressources : on les refera à la demande. */
    public static void onResourcesReloaded() {
        wide = null;
        slim = null;
        Wardrobe.onResourcesReloaded();
    }

    /** Écoute le rechargement des ressources (F3+T, changement de pack). */
    @Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reloads {
        private Reloads() {}

        @SubscribeEvent
        public static void onRegister(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> onResourcesReloaded());
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getRenderer() instanceof Skinned) return;
        LivingEntity e = event.getEntity();
        if (!(e instanceof Villager v)) return;
        String hash = SkinClient.hashOf(e.getUUID());
        if (hash == null || SkinClient.texture(hash) == null) return;
        event.setCanceled(true);
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yRotO, e.getYRot());
        renderer(SkinClient.slim(hash)).render(v, yaw, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
