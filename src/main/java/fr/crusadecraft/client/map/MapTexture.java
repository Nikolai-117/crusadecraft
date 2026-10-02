package fr.crusadecraft.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Une texture dynamique où l'on recopie l'image calculée par {@link MapRaster}. */
public final class MapTexture {
    private final String name;
    private DynamicTexture texture;
    private ResourceLocation id;
    private int width, height;

    public MapTexture(String name) {
        this.name = name;
    }

    public ResourceLocation id() {
        return id;
    }

    public boolean ready() {
        return texture != null;
    }

    public ResourceLocation upload(MapRaster.Result r) {
        if (texture == null || width != r.width() || height != r.height()) {
            release();
            texture = new DynamicTexture(r.width(), r.height(), false);
            id = Minecraft.getInstance().getTextureManager().register("crusadecraft_" + name, texture);
            width = r.width();
            height = r.height();
        }
        NativeImage image = texture.getPixels();
        if (image == null) return id;
        int[] px = r.pixels();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = px[y * width + x];
                // NativeImage range ses pixels en ABGR.
                int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        texture.upload();
        return id;
    }

    public void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
            id = null;
        }
    }
}
