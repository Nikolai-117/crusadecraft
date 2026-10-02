package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.council.CouncilService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * La Table du Conseil : on y règle les tâches des conseillers et l'on y répond aux requêtes. Les
 * conseillers viennent s'y réunir chaque jour, sur les Sièges du conseil posés autour.
 */
public class CouncilTableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 15, 16),
            Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15));

    public CouncilTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof ServerPlayer player && CourtBlocks.checkTable(player, level, pos).isEmpty()) {
            CourtBlocks.registerTable(player, level, pos);
            player.displayClientMessage(Component.translatable("crusadecraft.court.table_placed"), true);
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
        Optional<Realm> realm = RealmService.realmOf(sp.server, sp.getUUID());
        if (realm.isEmpty()) {
            sp.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return InteractionResult.CONSUME;
        }
        var court = realm.get().court();
        boolean isThisTable = court.hasTable() && court.tablePos().equals(pos)
                && court.tableDimension().equals(level.dimension().location());
        if (!isThisTable && CourtBlocks.checkTable(sp, level, pos).isEmpty()) {
            // Table posée avant la fondation, ou dont l'inscription s'était perdue : on la reconnaît.
            CourtBlocks.registerTable(sp, level, pos);
            isThisTable = true;
        }
        if (!isThisTable) {
            boolean inRealm = MineColoniesBridge.colonyAt(level, pos).map(realm.get()::holds).orElse(false);
            sp.displayClientMessage(Component.translatable(inRealm ? "crusadecraft.court.not_the_table" : "crusadecraft.court.outside_realm"), true);
            return InteractionResult.CONSUME;
        }
        CrusadeNetwork.toPlayer(sp, new CourtPackets.OpenCouncil(CouncilService.view(sp.server, realm.get(), null)));
        CrusadeSavedData.get(sp.server).setDirty();
        return InteractionResult.CONSUME;
    }
}
