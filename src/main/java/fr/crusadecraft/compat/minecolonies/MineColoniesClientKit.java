package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyRef;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/** Outils côté client pour le banc d'essai en monde réel : ouvrir la fenêtre de l'Hôtel de ville d'une colonie. */
public final class MineColoniesClientKit {

    private MineColoniesClientKit() {}

    /** Ouvre la fenêtre du bâtiment au centre de la colonie (l'Hôtel de ville) ; faux si la vue n'est pas encore là. */
    public static boolean openTownHall(ColonyRef ref) {
        try {
            var dim = ResourceKey.create(Registries.DIMENSION, ref.dimension());
            IColonyView view = IColonyManager.getInstance().getColonyView(ref.colonyId(), dim);
            if (view == null) return false;
            IBuildingView building = IColonyManager.getInstance().getBuildingView(dim, view.getCenter());
            if (building == null) return false;
            building.openGui(false);
            return true;
        } catch (RuntimeException | LinkageError e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : fenêtre de l'Hôtel de ville impossible à ouvrir ({})", e.toString());
            return false;
        }
    }
}
