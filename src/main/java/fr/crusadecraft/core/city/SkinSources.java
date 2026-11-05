package fr.crusadecraft.core.city;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * D'où vient un skin (P47), en logique pure : un pseudo Minecraft, un lien direct vers l'image, ou la page d'un site connu,
 * dont on déduit le lien de l'image. Et ce qu'on accepte d'en faire entrer dans le jeu.
 */
public final class SkinSources {
    /** Un skin pèse quelques kilo-octets ; au-delà d'un demi-méga-octet, ce n'en est pas un, et il ne tiendrait plus dans un paquet. */
    public static final int MAX_BYTES = 1 << 19;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern NAMEMC = Pattern.compile("https?://(?:www\\.)?namemc\\.com/skin/([0-9a-fA-F]{8,64}).*");
    private static final Pattern SKINDEX = Pattern.compile("https?://(?:www\\.)?minecraftskins\\.com/skin/(\\d+)(?:/.*)?");
    private static final Pattern NOVA = Pattern.compile("https?://minecraft\\.novaskin\\.me/skin/(\\d+)(?:/.*)?");

    private SkinSources() {}

    /** Ce qu'on a tapé est-il un pseudo plutôt qu'un lien ? */
    public static boolean isName(String input) {
        return input != null && NAME.matcher(input.strip()).matches();
    }

    public static boolean isUrl(String input) {
        if (input == null) return false;
        String s = input.strip().toLowerCase(Locale.ROOT);
        return s.startsWith("https://") || s.startsWith("http://");
    }

    /** Le lien de l'image, quand on a collé l'adresse de la page d'un site de skins connu ; sinon, le lien tel quel. */
    public static String directUrl(String url) {
        String s = url.strip();
        Matcher m = NAMEMC.matcher(s);
        if (m.matches()) return "https://s.namemc.com/i/" + m.group(1) + ".png";
        m = SKINDEX.matcher(s);
        if (m.matches()) return "https://www.minecraftskins.com/skin/download/" + m.group(1) + "/";
        m = NOVA.matcher(s);
        if (m.matches()) return "https://novask.in/" + m.group(1) + ".png";
        return s;
    }

    /** Une image acceptée : sa taille, et si ses bras sont fins (le modèle « Alex »). */
    public record Checked(int width, int height, boolean slim) {}

    /** Ce fichier est-il bien un skin ? Une image PNG, carrée (ou deux fois plus large que haute, l'ancien format), de 64 à 512 pixels. {@code null} sinon. */
    public static Checked check(byte[] data) {
        if (data == null || data.length < 32 || data.length > MAX_BYTES) return null;
        // La signature d'abord : on ne confie pas n'importe quoi au décodeur.
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        for (int i = 0; i < png.length; i++) if (data[i] != png[i]) return null;
        int w = ((data[16] & 255) << 24) | ((data[17] & 255) << 16) | ((data[18] & 255) << 8) | (data[19] & 255);
        int h = ((data[20] & 255) << 24) | ((data[21] & 255) << 16) | ((data[22] & 255) << 8) | (data[23] & 255);
        if (!(w == 64 || w == 128 || w == 256 || w == 512) || !(h == w || h == w / 2)) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null || img.getWidth() != w || img.getHeight() != h) return null;
            return new Checked(w, h, slim(img));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Sur un skin « Alex », le bras fait trois pixels de large : la quatrième colonne est vide. */
    private static boolean slim(BufferedImage img) {
        if (img.getHeight() != img.getWidth()) return false;
        int s = img.getWidth() / 64;
        return (img.getRGB(54 * s, 20 * s) >>> 24) == 0 && (img.getRGB(55 * s, 31 * s) >>> 24) == 0;
    }
}
