package fr.crusadecraft.client.skin;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.crusadecraft.core.city.SkinService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Le vestiaire de Comes Alive, tel que le client le voit (P47) : ses tenues et ses coiffures, lues dans les ressources du mod.
 * Rien n'est copié : si Comes Alive n'est pas là, la liste est vide et seuls les skins importés restent. Et de quoi les montrer :
 * une petite figure de face, dessinée d'après la texture, comme sur un patron de couture.
 */
public final class Wardrobe {
    private static List<String> clothes, hair;
    /** Le corps nu derrière une tenue : les tenues de Comes Alive n'habillent que ce qu'elles couvrent. */
    private static final int BODY = 0xFFC9A98C;

    private Wardrobe() {}

    private static List<String> list(String folder) {
        List<String> out = new ArrayList<>();
        var resources = Minecraft.getInstance().getResourceManager().listResources(folder, rl -> rl.getPath().endsWith(".png"));
        for (ResourceLocation rl : resources.keySet()) if (rl.getNamespace().equals("mca")) out.add(rl.toString());
        out.sort(Wardrobe::compare);
        return out;
    }

    /** Dans l'ordre des dossiers, puis des numéros : 2.png avant 10.png. */
    private static int compare(String a, String b) {
        int sa = a.lastIndexOf('/'), sb = b.lastIndexOf('/');
        int folder = a.substring(0, sa).compareTo(b.substring(0, sb));
        if (folder != 0) return folder;
        return Integer.compare(number(a.substring(sa + 1)), number(b.substring(sb + 1)));
    }

    private static int number(String file) {
        try {
            return Integer.parseInt(file.replace(".png", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Les tenues : {@code mca:skins/clothing/normal/female/guard/3.png}. */
    public static List<String> clothes() {
        if (clothes == null) clothes = list("skins/clothing/normal");
        return clothes;
    }

    public static List<String> hair() {
        if (hair == null) hair = list("skins/hair");
        return hair;
    }

    public static void onResourcesReloaded() {
        clothes = null;
        hair = null;
    }

    /** Les tenues de ce sexe et de ce métier ({@code null} : peu importe). */
    public static List<String> filter(Boolean female, String job) {
        List<String> out = new ArrayList<>();
        for (String c : clothes()) {
            String[] parts = c.split("/");
            if (parts.length < 6) continue;
            if (female != null && !parts[3].equals(female ? "female" : "male")) continue;
            if (job != null && !parts[4].equals(job)) continue;
            out.add(c);
        }
        return out;
    }

    /** Les dossiers de métiers qu'il a, sans doublons. */
    public static List<String> jobs() {
        List<String> out = new ArrayList<>();
        for (String c : clothes()) {
            String[] parts = c.split("/");
            if (parts.length >= 6 && !out.contains(parts[4])) out.add(parts[4]);
        }
        return out;
    }

    /** Un nom à montrer : le métier et le numéro d'une tenue, le nom d'un skin. */
    public static String label(String look) {
        if (look == null || look.isEmpty()) return "";
        String hash = SkinService.hashOf(look);
        if (!hash.isEmpty()) {
            for (SkinService.Entry e : SkinClient.library()) if (e.hash().equals(hash)) return e.name();
            return hash.substring(0, 8);
        }
        String[] parts = look.split("/");
        if (parts.length < 2) return look;
        return parts[parts.length - 2] + " " + parts[parts.length - 1].replace(".png", "");
    }

    /** La texture d'une allure : celle d'un skin importé (demandée s'il faut), ou celle de la tenue. */
    public static ResourceLocation texture(String look) {
        if (look == null || look.isEmpty()) return null;
        String hash = SkinService.hashOf(look);
        if (!hash.isEmpty()) return SkinClient.texture(hash);
        return ResourceLocation.tryParse(look);
    }

    /** Une petite figure de face, de {@code scale} pixels par pixel de texture : 16 × 32 à l'échelle 1. */
    public static void doll(GuiGraphics g, String look, int x, int y, int scale) {
        ResourceLocation tex = texture(look);
        boolean skin = !SkinService.hashOf(look == null ? "" : look).isEmpty();
        if (!skin) {
            g.fill(x + 4 * scale, y, x + 12 * scale, y + 8 * scale, BODY);
            g.fill(x + 4 * scale, y + 8 * scale, x + 12 * scale, y + 20 * scale, BODY);
            g.fill(x, y + 8 * scale, x + 4 * scale, y + 20 * scale, BODY);
            g.fill(x + 12 * scale, y + 8 * scale, x + 16 * scale, y + 20 * scale, BODY);
            g.fill(x + 4 * scale, y + 20 * scale, x + 12 * scale, y + 32 * scale, BODY);
        }
        if (tex == null) return;
        RenderSystem.enableBlend();
        part(g, tex, x + 4 * scale, y, 8, 8, scale, 8, 8);
        part(g, tex, x + 4 * scale, y + 8 * scale, 8, 12, scale, 20, 20);
        part(g, tex, x, y + 8 * scale, 4, 12, scale, 44, 20);
        part(g, tex, x + 12 * scale, y + 8 * scale, 4, 12, scale, 36, 52);
        part(g, tex, x + 4 * scale, y + 20 * scale, 4, 12, scale, 4, 20);
        part(g, tex, x + 8 * scale, y + 20 * scale, 4, 12, scale, 20, 52);
        // La seconde couche : chapeau, veste, manches, jambes.
        part(g, tex, x + 4 * scale, y, 8, 8, scale, 40, 8);
        part(g, tex, x + 4 * scale, y + 8 * scale, 8, 12, scale, 20, 36);
        part(g, tex, x, y + 8 * scale, 4, 12, scale, 44, 36);
        part(g, tex, x + 12 * scale, y + 8 * scale, 4, 12, scale, 52, 52);
        part(g, tex, x + 4 * scale, y + 20 * scale, 4, 12, scale, 4, 36);
        part(g, tex, x + 8 * scale, y + 20 * scale, 4, 12, scale, 4, 52);
        RenderSystem.disableBlend();
    }

    private static void part(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int scale, int u, int v) {
        g.blit(tex, x, y, w * scale, h * scale, u, v, w, h, 64, 64);
    }
}
