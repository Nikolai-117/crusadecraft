package fr.crusadecraft.compat.dynmap;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.world.WebMap;
import net.minecraft.server.MinecraftServer;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Le seul endroit qui connaît Dynmap (P17) : il recopie le modèle de {@link WebMap} dans trois couches de marqueurs,
 * « Royaumes » (les chunks de chaque royaume, en aplats et en frontières), « Lieux » (villes, forts, lieux saints…) et
 * « Carte de guerre » (P18 : zones, flèches, repères, sièges), en mettant à jour ce qui existe, en créant ce qui manque
 * et en retirant ce qui a disparu.
 *
 * <p>Les marqueurs ne sont pas gardés par Dynmap d'une partie à l'autre : ils sont redessinés au démarrage, si bien
 * qu'aucun royaume disparu ne traîne sur la carte.
 */
final class DynmapCompat {
    static final String REALMS = "crusadecraft.royaumes", PLACES = "crusadecraft.lieux", WAR = "crusadecraft.guerre", TRADE = "crusadecraft.commerce";
    private static volatile MarkerAPI markers;
    private static int lastHash;

    private DynmapCompat() {}

    static void register() {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                markers = api.getMarkerAPI();
                lastHash = 0;
                CrusadeCraft.LOGGER.info("CrusadeCraft : Dynmap prêt, les royaumes seront dessinés sur la carte web");
            }

            @Override
            public void apiDisabled(DynmapCommonAPI api) {
                markers = null;
            }
        });
    }

    static boolean ready() {
        return markers != null;
    }

    static void reset() {
        lastHash = 0;
    }

    /** Redessine si quelque chose a changé ; renvoie vrai si la carte a été touchée. */
    static boolean update(MinecraftServer server) {
        MarkerAPI api = markers;
        if (api == null) return false;
        WebMap.Model model = WebMap.build(server);
        if (model.hash() == lastHash) return false;
        lastHash = model.hash();
        apply(api, model);
        return true;
    }

    private static MarkerSet set(MarkerAPI api, String id, String label, int priority) {
        MarkerSet s = api.getMarkerSet(id);
        if (s == null) s = api.createMarkerSet(id, label, null, false);
        s.setMarkerSetLabel(label);
        s.setLayerPriority(priority);
        s.setHideByDefault(false);
        return s;
    }

    private static void apply(MarkerAPI api, WebMap.Model model) {
        MarkerSet realms = set(api, REALMS, "Royaumes", 10);
        MarkerSet places = set(api, PLACES, "Lieux", 20);
        MarkerSet war = set(api, WAR, "Carte de guerre", 30);
        MarkerSet trade = set(api, TRADE, "Routes commerciales", 25);
        MarkerSet[] all = {realms, places, war, trade};
        Map<String, AreaMarker> oldAreas = new HashMap<>();
        for (MarkerSet s : all) for (AreaMarker a : s.getAreaMarkers()) oldAreas.put(a.getMarkerID(), a);
        Set<String> keepAreas = new HashSet<>();
        for (WebMap.Area a : model.areas()) {
            MarkerSet target = a.id().startsWith("lieu_") ? places : a.id().startsWith("guerre_") ? war : realms;
            AreaMarker m = oldAreas.get(a.id());
            if (m == null || !a.world().equals(m.getWorld()) || m.getMarkerSet() != target) {
                if (m != null) m.deleteMarker();
                m = target.createAreaMarker(a.id(), a.label(), false, a.world(), a.xs(), a.zs(), false);
                if (m == null) continue;
            } else {
                m.setCornerLocations(a.xs(), a.zs());
                m.setLabel(a.label(), false);
            }
            m.setDescription(a.html());
            m.setFillStyle(a.opacity(), a.fill());
            m.setLineStyle(a.weight(), a.weight() == 0 ? 0.0 : 0.8, a.line());
            keepAreas.add(a.id());
        }
        for (Map.Entry<String, AreaMarker> e : oldAreas.entrySet()) if (!keepAreas.contains(e.getKey())) e.getValue().deleteMarker();

        Map<String, PolyLineMarker> oldLines = new HashMap<>();
        for (MarkerSet s : all) for (PolyLineMarker l : s.getPolyLineMarkers()) oldLines.put(l.getMarkerID(), l);
        Set<String> keepLines = new HashSet<>();
        for (WebMap.Line l : model.lines()) {
            double[] ys = new double[l.xs().length];
            java.util.Arrays.fill(ys, 64);
            MarkerSet target = l.id().startsWith("guerre_") ? war : l.id().startsWith("commerce_") ? trade : realms;
            PolyLineMarker m = oldLines.get(l.id());
            if (m == null || !l.world().equals(m.getWorld()) || m.getMarkerSet() != target) {
                if (m != null) m.deleteMarker();
                m = target.createPolyLineMarker(l.id(), l.label(), false, l.world(), l.xs(), ys, l.zs(), false);
                if (m == null) continue;
            } else {
                m.setCornerLocations(l.xs(), ys, l.zs());
                m.setLabel(l.label(), false);
            }
            m.setDescription(l.html());
            m.setLineStyle(l.weight(), 0.9, l.color());
            keepLines.add(l.id());
        }
        for (Map.Entry<String, PolyLineMarker> e : oldLines.entrySet()) if (!keepLines.contains(e.getKey())) e.getValue().deleteMarker();

        Map<String, Marker> oldPoints = new HashMap<>();
        for (MarkerSet s : all) for (Marker p : s.getMarkers()) oldPoints.put(p.getMarkerID(), p);
        Set<String> keepPoints = new HashSet<>();
        for (WebMap.Point p : model.points()) {
            MarkerIcon icon = api.getMarkerIcon(p.icon());
            if (icon == null) icon = api.getMarkerIcon(MarkerIcon.DEFAULT);
            MarkerSet target = p.id().startsWith("guerre_") ? war : places;
            Marker m = oldPoints.get(p.id());
            if (m != null && m.getMarkerSet() != target) {
                m.deleteMarker();
                m = null;
            }
            if (m == null) {
                m = target.createMarker(p.id(), p.label(), false, p.world(), p.x(), p.y(), p.z(), icon, false);
                if (m == null) continue;
            } else {
                m.setLocation(p.world(), p.x(), p.y(), p.z());
                m.setMarkerIcon(icon);
                m.setLabel(p.label(), false);
            }
            m.setDescription(p.html());
            keepPoints.add(p.id());
        }
        for (Map.Entry<String, Marker> e : oldPoints.entrySet()) if (!keepPoints.contains(e.getKey())) e.getValue().deleteMarker();
        CrusadeCraft.LOGGER.debug("CrusadeCraft : carte web — {} aplats, {} frontières, {} lieux", keepAreas.size(), keepLines.size(), keepPoints.size());
    }
}
