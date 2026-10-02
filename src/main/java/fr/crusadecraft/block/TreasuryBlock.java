package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.EconomyPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Le Coffre du Trésor : on y dépose l'or physique, converti en solde, et on l'en retire. Il appartient à la
 * couronne qui tient la colonie où il est posé ; seul son souverain l'ouvre.
 */
public class TreasuryBlock extends CourtFurnitureBlock {

    public TreasuryBlock(Properties properties) {
        super(properties, new double[][]{{1, 0, 1, 15, 14, 15}});
    }

    /** Le royaume auquel appartient un coffre posé à cet endroit, s'il y en a un. */
    public static Optional<Realm> owner(Level level, BlockPos pos) {
        if (level.getServer() == null) return Optional.empty();
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        return colony.flatMap(c -> CrusadeSavedData.get(level.getServer()).realmHolding(c));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        Optional<Realm> realm = owner(level, pos);
        if (realm.isEmpty()) {
            sp.displayClientMessage(Component.translatable("crusadecraft.treasury.no_realm_here"), true);
            return InteractionResult.CONSUME;
        }
        if (!realm.get().ruler().equals(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("crusadecraft.treasury.not_yours", realm.get().name()), true);
            return InteractionResult.CONSUME;
        }
        CrusadeNetwork.toPlayer(sp, new EconomyPackets.TreasuryData(TreasuryService.view(sp.server, realm.get()), pos));
        return InteractionResult.CONSUME;
    }
}
