package fr.crusadecraft.compat.xaero;

import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

/**
 * P63 : où Xaero's World Map range la carte du monde ouvert, d'après lui-même (ses noms de dossiers dépendent du serveur,
 * du monde solo, de la dimension et du sous-monde qu'il a reconnu). Seulement des lectures, sur le fil du jeu ; à n'appeler
 * que si le mod est chargé.
 */
public final class XaeroWorldMapIds {
    private XaeroWorldMapIds() {}

    /** {monde, dimension, sous-monde, version du cache détaillé}, ou {@code null} tant que Xaero n'a pas reconnu le monde. */
    public static String[] current() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) return null;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) return null;
        return new String[]{processor.getCurrentWorldId(), processor.getCurrentDimId(), processor.getCurrentMWId(),
                String.valueOf(processor.getGlobalVersion())};
    }
}
