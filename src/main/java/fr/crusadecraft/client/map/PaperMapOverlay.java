package fr.crusadecraft.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.MapView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Les royaumes sur la carte papier de Minecraft (P14), dessinés par-dessus par le client : leurs terres teintées,
 * leurs frontières, leurs capitales, villes et constructions, et le nom des royaumes à leur capitale.
 *
 * <p>Le client ne sait pas où se trouve une carte dans le monde : le serveur le lui dit ({@link #setCenter}). La
 * propriété des terres est celle de la carte du mod ({@link MapRaster}), avec son brouillard : seuls les royaumes
 * connus apparaissent, et seulement sur les parties déjà explorées de la carte. L'image est gardée et ne se
 * recalcule que si la carte ou les frontières changent.
 */
public final class PaperMapOverlay {
    private static final int SIZE = 128;
    /** On revérifie au plus toutes les deux secondes si la carte a été davantage explorée. */
    private static final long CHECK_MS = 2000;

    /** Où se trouve une carte : sa dimension, son centre, son échelle (1 à 16 blocs par point). */
    public record Center(ResourceLocation dimension, int x, int z, int scale) {}

    private static final class Cached {
        DynamicTexture texture;
        ResourceLocation id;
        int version = -1;
        int colorsHash;
        long checked;
        Center center;
        int[] owners;
    }

    private static final Map<Integer, Center> CENTERS = new HashMap<>();
    private static final Map<Integer, Cached> CACHE = new HashMap<>();

    private PaperMapOverlay() {}

    public static void setCenter(int mapId, Center center) {
        CENTERS.put(mapId, center);
    }

    public static Optional<Center> center(int mapId) {
        return Optional.ofNullable(CENTERS.get(mapId));
    }

    /** Une nouvelle partie : rien ne survit d'un monde à l'autre. */
    public static void clear() {
        for (Cached c : CACHE.values()) if (c.id != null) Minecraft.getInstance().getTextureManager().release(c.id);
        CACHE.clear();
        CENTERS.clear();
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, int mapId, MapItemSavedData data, int light) {
        Center center = CENTERS.get(mapId);
        if (center == null) return;
        ClientMapData.refreshIfStale(20_000);
        MapView view = ClientMapData.view();
        if (view == null || !view.dimension().equals(center.dimension())) return;
        Cached c = CACHE.computeIfAbsent(mapId, k -> new Cached());
        long now = System.currentTimeMillis();
        boolean stale = c.version != ClientMapData.version() || !center.equals(c.center);
        if (!stale && now - c.checked > CHECK_MS) {
            c.checked = now;
            stale = Arrays.hashCode(data.colors) != c.colorsHash;
        }
        if (stale || c.texture == null) rebuild(mapId, c, view, center, data);
        if (c.texture == null) return;
        Matrix4f m = pose.last().pose();
        VertexConsumer v = buffers.getBuffer(RenderType.text(c.id));
        // Entre le dessin de la carte (z = −0,01) et ses marqueurs.
        float z = -0.015f;
        v.vertex(m, 0, SIZE, z).color(255, 255, 255, 255).uv(0, 1).uv2(light).endVertex();
        v.vertex(m, SIZE, SIZE, z).color(255, 255, 255, 255).uv(1, 1).uv2(light).endVertex();
        v.vertex(m, SIZE, 0, z).color(255, 255, 255, 255).uv(1, 0).uv2(light).endVertex();
        v.vertex(m, 0, 0, z).color(255, 255, 255, 255).uv(0, 0).uv2(light).endVertex();
        names(pose, buffers, view, center, data, light);
    }

    private static void rebuild(int mapId, Cached c, MapView view, Center center, MapItemSavedData data) {
        c.version = ClientMapData.version();
        c.center = center;
        c.colorsHash = Arrays.hashCode(data.colors);
        c.checked = System.currentTimeMillis();
        MapRaster.Result r = MapRaster.render(view, SIZE, SIZE, center.x(), center.z(), center.scale(), false);
        int[] territory = new int[SIZE * SIZE];
        for (int i = 0; i < territory.length; i++) {
            int seat = r.owners()[i];
            boolean explored = data.colors[i] != 0;
            territory[i] = explored && seat >= 0 && seat < view.seats().size() ? view.seats().get(seat).territory() : -1;
        }
        c.owners = territory;
        if (c.texture == null) {
            c.texture = new DynamicTexture(SIZE, SIZE, true);
            c.id = Minecraft.getInstance().getTextureManager().register("crusadecraft_papermap_" + mapId, c.texture);
        }
        NativeImage img = c.texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int t = territory[y * SIZE + x];
                int argb = 0;
                if (t >= 0) {
                    int rgb = view.territories().get(t).color();
                    boolean edge = (x > 0 && territory[y * SIZE + x - 1] != t) || (x < SIZE - 1 && territory[y * SIZE + x + 1] != t)
                            || (y > 0 && territory[(y - 1) * SIZE + x] != t) || (y < SIZE - 1 && territory[(y + 1) * SIZE + x] != t);
                    argb = edge ? 0xD0000000 | darker(rgb) : 0x58000000 | rgb;
                }
                img.setPixelRGBA(x, y, abgr(argb));
            }
        }
        // Les sièges : capitales dorées, villes, constructions.
        for (MapView.Seat s : view.seats()) {
            int px = pixel(s.x(), center.x(), center.scale()), py = pixel(s.z(), center.z(), center.scale());
            if (px < 1 || py < 1 || px >= SIZE - 1 || py >= SIZE - 1 || data.colors[py * SIZE + px] == 0) continue;
            int ink = 0xFF3A2918;
            if (s.capital()) {
                square(img, px, py, 2, ink);
                square(img, px, py, 1, 0xFFD4A017);
            } else if (s.site() != null) {
                square(img, px, py, 1, ink);
                img.setPixelRGBA(px, py, abgr(0xFF000000 | view.territories().get(s.territory()).color()));
            } else {
                square(img, px, py, 1, ink);
                img.setPixelRGBA(px, py, abgr(0xFFEADFC4));
            }
        }
        c.texture.upload();
    }

    /** Le nom de chaque royaume à sa capitale, si elle est sur la carte et explorée. */
    private static void names(PoseStack pose, MultiBufferSource buffers, MapView view, Center center, MapItemSavedData data, int light) {
        Font font = Minecraft.getInstance().font;
        for (MapView.Seat s : view.seats()) {
            if (!s.capital()) continue;
            int px = pixel(s.x(), center.x(), center.scale()), py = pixel(s.z(), center.z(), center.scale());
            if (px < 0 || py < 0 || px >= SIZE || py >= SIZE || data.colors[py * SIZE + px] == 0) continue;
            MapView.Territory t = view.territories().get(s.territory());
            Component name = t.ai() ? AiNames.realm(t.rank(), t.label()) : Component.literal(t.label());
            float k = 0.5f;
            int w = font.width(name);
            pose.pushPose();
            pose.translate(px - w * k / 2f, py + 3, -0.025f);
            pose.scale(k, k, 1);
            font.drawInBatch(name, 0, 0, 0xFF3A2918, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x66E8D9B0, light);
            pose.popPose();
        }
    }

    /** Le point de la carte d'une coordonnée du monde. */
    static int pixel(int world, int center, int scale) {
        return (int) Math.floor((world - center) / (double) scale) + SIZE / 2;
    }

    /** Le propriétaire de l'endroit au centre de la carte, pour l'info-bulle (indice du royaume, ou −1). */
    public static int ownerAtCenter(MapView view, Center center) {
        MapRaster.Result r = MapRaster.render(view, 1, 1, center.x(), center.z(), 1, false);
        int seat = r.owners()[0];
        return seat >= 0 && seat < view.seats().size() ? view.seats().get(seat).territory() : -1;
    }

    private static void square(NativeImage img, int cx, int cy, int r, int argb) {
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++) {
            if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) img.setPixelRGBA(x, y, abgr(argb));
        }
    }

    private static int darker(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 6 / 10, g = ((rgb >> 8) & 0xFF) * 6 / 10, b = (rgb & 0xFF) * 6 / 10;
        return (r << 16) | (g << 8) | b;
    }

    /** NativeImage range ses pixels en ABGR. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }
}
