package fr.crusadecraft.compat.minecolonies;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Outils réservés aux GameTests : ils créent de vraies colonies MineColonies dans le monde jetable
 * du serveur de test, par l'API officielle.
 *
 * <p><b>Jamais appelé en jeu.</b> Seules les classes de {@code fr.crusadecraft.gametest} s'en servent,
 * et le serveur de test ne charge aucun monde de joueur.
 */
public final class MineColoniesTestKit {
    private static final ResourceLocation TOWN_HALL = ResourceLocation.tryParse("minecolonies:blockhuttownhall");

    private MineColoniesTestKit() {}

    /** Crée une colonie appartenant à {@code owner}, avec un Hôtel de ville au niveau voulu (0 = aucun). */
    public static ColonyRef createColony(ServerLevel level, BlockPos center, ServerPlayer owner, String name, int townHallLevel) {
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().createColony(level, center, owner, name, "Medieval Oak");
        if (colony == null) throw new IllegalStateException("MineColonies a refusé de créer la colonie de test");
        if (townHallLevel > 0) {
            Block hut = ForgeRegistries.BLOCKS.getValue(TOWN_HALL);
            level.setBlock(center, hut.defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity be = level.getBlockEntity(center);
            if (!(be instanceof AbstractTileEntityColonyBuilding te)) {
                throw new IllegalStateException("Pas d'entité de bâtiment sur l'Hôtel de ville de test : " + be);
            }
            IBuilding building = colony.getServerBuildingManager().addNewBuilding(te, level);
            if (building == null) throw new IllegalStateException("MineColonies n'a pas enregistré l'Hôtel de ville de test");
            building.setBuildingLevel(townHallLevel);
        }
        return new ColonyRef(colony.getDimension().location(), colony.getID());
    }

    private static IColony colony(ColonyRef ref) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ref.dimension());
        return IMinecoloniesAPI.getInstance().getColonyManager().getColonyByDimension(ref.colonyId(), key);
    }

    /** Ajoute un citoyen adulte (données seules, sans entité), comme MineColonies le fait à l'arrivée d'un colon. */
    public static CitizenRef addCitizen(ColonyRef ref) {
        ICitizenData data = colony(ref).getCitizenManager().createAndRegisterCivilianData();
        return new CitizenRef(ref, data.getId());
    }

    /** Retire un citoyen de la colonie (départ, renvoi). */
    public static void removeCitizen(CitizenRef ref) {
        IColony c = colony(ref.colony());
        ICitizenData data = c.getCitizenManager().getCivilian(ref.citizenId());
        if (data != null) c.getCitizenManager().removeCivilian(data);
    }

    /** Fait apparaître l'entité d'un citoyen à une position (comme à l'arrivée d'un colon). */
    public static java.util.Optional<net.minecraft.world.entity.Entity> spawnCitizen(CitizenRef ref, ServerLevel level, BlockPos pos) {
        IColony c = colony(ref.colony());
        ICitizenData data = c.getCitizenManager().getCivilian(ref.citizenId());
        if (data == null) return java.util.Optional.empty();
        c.getCitizenManager().spawnOrCreateCivilian(data, level, java.util.List.of(pos), true);
        return data.getEntity().map(e -> (net.minecraft.world.entity.Entity) e);
    }

    public static void deleteColony(ColonyRef ref) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ref.dimension());
        IMinecoloniesAPI.getInstance().getColonyManager().deleteColonyByDimension(ref.colonyId(), false, key);
    }
}
