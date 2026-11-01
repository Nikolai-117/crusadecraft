package fr.crusadecraft.compat.dynmap;

import fr.crusadecraft.CrusadeCraft;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Les icônes de la carte fantasy (P32), dessinées ici plutôt que prises parmi celles de Dynmap : un sceau doré pour une
 * capitale, un point cerclé pour une ville, un point pour un village, une tour, une croix, une ruine — l'encre brune
 * des cartes anciennes — et une icône vide pour les noms de royaumes, qui ne sont que du texte.
 */
final class DynmapIcons {
    static final String VIDE = "crusadecraft_vide", CAPITALE = "crusadecraft_capitale", VILLE = "crusadecraft_ville",
            VILLAGE = "crusadecraft_village", FORT = "crusadecraft_fort", SAINT = "crusadecraft_saint", RUINE = "crusadecraft_ruine";

    private static final Color INK = new Color(0x2B, 0x1D, 0x0E), PAPER = new Color(0xF3, 0xE7, 0xC9), GOLD = new Color(0xE3, 0xB3, 0x41);
    private static boolean registered;

    private DynmapIcons() {}

    /** Déclare les icônes auprès de Dynmap, une fois (et met leur dessin à jour si elles existaient déjà). */
    static void ensure(MarkerAPI api) {
        if (registered) return;
        registered = true;
        put(api, VIDE, "Nom", 8, g -> {});
        put(api, CAPITALE, "Capitale", 16, g -> {
            g.setColor(INK);
            g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
            g.setColor(GOLD);
            g.fill(new Ellipse2D.Double(3.5, 3.5, 9, 9));
            g.setColor(INK);
            g.fill(star(8, 8, 3.6, 1.6));
        });
        put(api, VILLE, "Ville", 16, g -> {
            g.setColor(PAPER);
            g.fill(new Ellipse2D.Double(3.5, 3.5, 9, 9));
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.8f));
            g.draw(new Ellipse2D.Double(4, 4, 8, 8));
            g.fill(new Ellipse2D.Double(6.2, 6.2, 3.6, 3.6));
        });
        put(api, VILLAGE, "Village", 8, g -> {
            g.setColor(PAPER);
            g.fill(new Ellipse2D.Double(0.5, 0.5, 7, 7));
            g.setColor(INK);
            g.fill(new Ellipse2D.Double(1.5, 1.5, 5, 5));
        });
        put(api, FORT, "Place forte", 16, g -> {
            g.setColor(PAPER);
            g.fillRect(3, 4, 10, 11);
            g.setColor(INK);
            g.fillRect(4, 7, 8, 8);
            for (int x = 4; x <= 10; x += 3) g.fillRect(x, 4, 2, 3);
            g.setColor(PAPER);
            g.fillRect(7, 11, 2, 4);
        });
        put(api, SAINT, "Lieu saint", 16, g -> {
            g.setColor(PAPER);
            g.fillRect(5, 1, 6, 14);
            g.fillRect(1, 4, 14, 6);
            g.setColor(INK);
            g.fillRect(6, 2, 4, 12);
            g.fillRect(2, 5, 12, 4);
        });
        put(api, RUINE, "Ruine", 16, g -> {
            g.setColor(INK);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(3, 14, 3, 6);
            g.drawLine(3, 6, 7, 3);
            g.drawLine(13, 14, 13, 9);
            g.drawLine(1, 14, 15, 14);
        });
    }

    private static Polygon star(double cx, double cy, double outer, double inner) {
        Polygon p = new Polygon();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5, r = i % 2 == 0 ? outer : inner;
            p.addPoint((int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy - Math.sin(a) * r));
        }
        return p;
    }

    private static void put(MarkerAPI api, String id, String label, int size, Consumer<Graphics2D> draw) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        draw.accept(g);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : icône {} non dessinée", id, e);
            return;
        }
        MarkerIcon icon = api.getMarkerIcon(id);
        if (icon == null) api.createMarkerIcon(id, label, new ByteArrayInputStream(out.toByteArray()));
        else icon.setMarkerIconImage(new ByteArrayInputStream(out.toByteArray()));
    }
}
