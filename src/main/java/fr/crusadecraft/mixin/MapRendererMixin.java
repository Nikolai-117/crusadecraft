package fr.crusadecraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.crusadecraft.client.map.PaperMapOverlay;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Le seul mixin de CrusadeCraft (P14) : après que Minecraft a dessiné une carte papier (en main ou dans un cadre),
 * on dessine par-dessus les royaumes connus. Les marqueurs de la carte vanilla ne sont pas extensibles en 1.20.1.
 */
@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void crusadecraft$drawRealms(PoseStack pose, MultiBufferSource buffers, int mapId, MapItemSavedData data, boolean active, int light,
                                         CallbackInfo ci) {
        PaperMapOverlay.draw(pose, buffers, mapId, data, light);
    }
}
