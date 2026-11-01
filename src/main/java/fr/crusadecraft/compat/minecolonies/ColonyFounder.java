package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.plan.TownColony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fait d'une ville posée d'après les plans une vraie colonie MineColonies (P35) : la colonie naît sur son hôtel de
 * ville, puis chaque bloc de cabane est reposé et son bâtiment enregistré avec son pack, son plan et son niveau — tel
 * que le bâtisseur l'aurait laissé. Les colons arrivent ensuite comme dans toute colonie.
 */
public final class ColonyFounder {

    private ColonyFounder() {}

    /** Le nombre de bâtiments enregistrés, et la colonie ; vide si MineColonies refuse. */
    public record Founded(ColonyRef colony, int buildings) {}

    public static Optional<Founded> found(ServerLevel level, ServerPlayer owner, String name, String pack, List<TownColony.HutSpot> huts) {
        if (!MineColoniesBridge.isReady() || huts.isEmpty()) return Optional.empty();
        try {
            TownColony.HutSpot hall = huts.stream().filter(TownColony.HutSpot::townHall).findFirst().orElse(null);
            if (hall == null) return Optional.empty();
            IColonyManager manager = IMinecoloniesAPI.getInstance().getColonyManager();
            if (manager.getColonyByPosFromWorld(level, hall.pos()) != null) return Optional.empty();
            IColony colony = manager.createColony(level, hall.pos(), owner, name, pack);
            if (colony == null) return Optional.empty();
            List<TownColony.HutSpot> ordered = new ArrayList<>();
            ordered.add(hall);
            for (TownColony.HutSpot h : huts) if (h != hall) ordered.add(h);
            int registered = 0;
            for (TownColony.HutSpot h : ordered) {
                level.setBlock(h.pos(), h.state(), Block.UPDATE_ALL);
                BlockEntity be = level.getBlockEntity(h.pos());
                if (!(be instanceof AbstractTileEntityColonyBuilding te)) continue;
                // Le pack et le plan passent par le bâtiment, qui les recopie sur son entité.
                IBuilding building = colony.getServerBuildingManager().addNewBuilding(te, level);
                if (building == null) continue;
                building.setStructurePack(h.pack());
                building.setBlueprintPath(h.path());
                building.setBuildingLevel(Math.max(1, h.level()));
                building.calculateCorners();
                registered++;
            }
            colony.markDirty();
            CrusadeCraft.LOGGER.info("CrusadeCraft : la ville {} devient la colonie {} ({} bâtiments)", name, colony.getID(), registered);
            return Optional.of(new Founded(new ColonyRef(colony.getDimension().location(), colony.getID()), registered));
        } catch (LinkageError | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : MineColonies refuse de fonder la colonie {}", name, e);
            return Optional.empty();
        }
    }
}
