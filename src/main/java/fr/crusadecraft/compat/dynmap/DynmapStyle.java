package fr.crusadecraft.compat.dynmap;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * La feuille de style de la carte fantasy (P32), posée par la voie que Dynmap prévoit pour cela : un fichier
 * {@code css/override.css} dans son dossier web, et la ligne qui le charge, livrée en commentaire dans sa page, rendue
 * active. Dynmap filtre le style des étiquettes envoyées par les mods (il garde police, couleur et halo) ; seule cette
 * feuille peut retirer le cadre sombre qu'il pose derrière, et centrer le nom d'un royaume sur son point.
 *
 * <p>Si un {@code override.css} existe déjà, on y ajoute notre bloc sans toucher au reste ; à chaque démarrage, on
 * remet notre bloc à jour (Dynmap réécrit sa page quand il change de version). Réglable : {@code carteWeb.styleFantasy}.
 */
final class DynmapStyle {
    static final String BEGIN = "/* crusadecraft : carte fantasy (début) */", END = "/* crusadecraft : carte fantasy (fin) */";
    private static final String COMMENTED = "<!-- <link rel=\"stylesheet\" type=\"text/css\" href=\"css/override.css\" media=\"screen\" /> -->";
    private static final String LINK = "<link rel=\"stylesheet\" type=\"text/css\" href=\"css/override.css\" media=\"screen\" />";

    static final String CSS = BEGIN + "\n"
            + ".dynmap .mapMarker .markerName-show.markerName_crusadecraft\\.noms,\n"
            + ".dynmap .mapMarker .markerName-show.markerName_crusadecraft\\.lieux,\n"
            + ".dynmap .mapMarker:hover .markerName_crusadecraft\\.noms,\n"
            + ".dynmap .mapMarker:hover .markerName_crusadecraft\\.lieux {\n"
            + "  background: none !important;\n  padding: 0 !important;\n  border-radius: 0 !important;\n  box-shadow: none !important;\n}\n"
            + ".dynmap .mapMarker .markerName_crusadecraft\\.noms {\n"
            + "  top: 4px !important;\n  left: 4px !important;\n  transform: translate(-50%, -50%);\n  pointer-events: none;\n}\n"
            + END + "\n";

    private DynmapStyle() {}

    /** Pose ou met à jour la feuille. Renvoie {@code true} si la page de Dynmap la charge. */
    static boolean install(MinecraftServer server) {
        if (!CrusadeConfig.SERVER.webMapFantasyStyle.get()) return false;
        Path web = server.getServerDirectory().toPath().resolve("dynmap").resolve("web");
        Path index = web.resolve("index.html");
        if (!Files.isRegularFile(index)) return false;
        try {
            Path css = web.resolve("css").resolve("override.css");
            Files.createDirectories(css.getParent());
            String current = Files.isRegularFile(css) ? Files.readString(css, StandardCharsets.UTF_8) : "";
            String next = merge(current);
            if (!next.equals(current)) Files.writeString(css, next, StandardCharsets.UTF_8);
            String page = Files.readString(index, StandardCharsets.UTF_8);
            if (!page.contains(LINK) || page.contains(COMMENTED)) {
                String fixed = page.contains(COMMENTED) ? page.replace(COMMENTED, LINK) : page.replace("</head>", "\t" + LINK + "\n</head>");
                if (!fixed.equals(page)) Files.writeString(index, fixed, StandardCharsets.UTF_8);
            }
            CrusadeCraft.LOGGER.info("CrusadeCraft : feuille de style de la carte fantasy posée dans {}", css);
            return true;
        } catch (IOException | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : feuille de style de la carte fantasy non posée", e);
            return false;
        }
    }

    /** Notre bloc, remplacé s'il y est déjà, ajouté sinon ; le reste du fichier ne bouge pas. Logique pure. */
    static String merge(String current) {
        int a = current.indexOf(BEGIN), b = current.indexOf(END);
        if (a >= 0 && b > a) {
            int end = b + END.length();
            if (end < current.length() && current.charAt(end) == '\n') end++;
            return current.substring(0, a) + CSS + current.substring(end);
        }
        if (current.isEmpty()) return CSS;
        return current + (current.endsWith("\n") ? "" : "\n") + CSS;
    }
}
