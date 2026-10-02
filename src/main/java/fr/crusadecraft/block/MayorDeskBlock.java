package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.MayorService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Le Bureau du Maire, un par colonie : on y nomme le maire et on y lit les rapports de la ville. Le maire
 * y travaille à ses heures de bureau.
 */
public class MayorDeskBlock extends CourtFurnitureBlock {

    public MayorDeskBlock(Properties properties) {
        super(properties, new double[][]{{0, 12, 2, 16, 14, 14}, {0, 0, 2, 4, 12, 14}, {12, 0, 2, 16, 12, 14}});
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer player && CourtBlocks.checkDesk(player, level, pos).isEmpty()) {
            CourtBlocks.registerDesk(player, level, pos);
            player.displayClientMessage(Component.translatable("crusadecraft.court.desk_placed"), true);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            CourtBlocks.unregister(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        if (colony.isEmpty()) {
            sp.displayClientMessage(Component.translatable("crusadecraft.court.no_colony"), true);
            return InteractionResult.CONSUME;
        }
        if (CourtBlocks.checkDesk(sp, level, pos).isEmpty()) CourtBlocks.registerDesk(sp, level, pos);
        CrusadeNetwork.toPlayer(sp, new CourtPackets.OpenMayor(MayorService.view(sp.server, sp, colony.get())));
        return InteractionResult.CONSUME;
    }
}
