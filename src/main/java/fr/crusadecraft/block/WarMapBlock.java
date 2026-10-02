package fr.crusadecraft.block;

import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cadre de la Table de guerre : neuf cadres accrochés au mur en carré de 3 × 3 affichent la carte du
 * royaume en grand. Un clic droit ouvre la carte.
 */
public class WarMapBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape NORTH = Block.box(0, 0, 15, 16, 16, 16);
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 1);
    private static final VoxelShape EAST = Block.box(0, 0, 0, 1, 16, 16);
    private static final VoxelShape WEST = Block.box(15, 0, 0, 16, 16, 16);

    public WarMapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Accroché à la face cliquée, comme un cadre ; posé au sol, il fait face au joueur. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            CrusadeNetwork.toPlayer(sp, new WorldPackets.MapData(WorldService.mapView(sp), true));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WarMapBlockEntity(pos, state);
    }
}
