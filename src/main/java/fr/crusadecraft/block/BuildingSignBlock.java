package fr.crusadecraft.block;

import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CitySigns;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une enseigne (P44) : posée dans un bâtiment, elle lui donne sa fonction ; voir {@link CitySigns}. Un bloc par fonction,
 * pour qu'elle se reconnaisse sur un plan, dans une schématique, et se fabrique avec un panneau et l'objet du métier.
 */
public class BuildingSignBlock extends CourtFurnitureBlock {
    private static final double[][] BOARD = {{1, 1, 14, 15, 15, 16}};
    private final BuildingFunction function;

    public BuildingSignBlock(Properties properties, BuildingFunction function) {
        super(properties, BOARD);
        this.function = function;
    }

    public BuildingFunction function() {
        return function;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) return;
        var out = CitySigns.onPlaced(player, pos, function);
        Component message = out.message();
        player.displayClientMessage(message, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) CitySigns.onRemoved(server, pos, function);
        super.onRemove(state, level, pos, next, moving);
    }
}
