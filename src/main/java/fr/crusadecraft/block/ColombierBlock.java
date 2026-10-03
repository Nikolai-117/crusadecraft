package fr.crusadecraft.block;

import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le Colombier royal : d'ici partent les lettres aux autres souverains, et ici arrivent leurs réponses. Un clic
 * droit ouvre la diplomatie et remet le courrier.
 */
public class ColombierBlock extends CourtFurnitureBlock {

    public ColombierBlock(Properties properties) {
        super(properties, new double[][]{{2, 0, 2, 14, 12, 14}, {1, 12, 1, 15, 16, 15}});
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (RealmService.realmOf(sp.server, sp.getUUID()).isEmpty()) {
            sp.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return InteractionResult.CONSUME;
        }
        CrusadeNetwork.toPlayer(sp, new DiplomacyPackets.OpenDiplomacy(DiplomacyService.view(sp), true));
        return InteractionResult.CONSUME;
    }
}
