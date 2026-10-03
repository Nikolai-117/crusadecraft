package fr.crusadecraft.block;

import fr.crusadecraft.network.packet.CourtLifePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** La Lice de tournoi : une barrière de joute aux couleurs du royaume. Un clic droit ouvre l'organisation du tournoi. */
public class TournamentBlock extends CourtFurnitureBlock {

    public TournamentBlock(Properties properties) {
        super(properties, new double[][]{{0, 0, 7, 16, 12, 9}, {7, 0, 6, 9, 16, 10}});
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) CourtLifePackets.sendTournament(sp, true);
        return InteractionResult.CONSUME;
    }
}
