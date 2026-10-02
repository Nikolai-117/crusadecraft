package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.OpenFoundingScreenPacket;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Le Trône royal. Posé dans l'Hôtel de ville de sa colonie, il fonde le royaume ; ensuite, il ouvre
 * le menu royal.
 *
 * <p>Tout se décide côté serveur : le client ne fait qu'afficher ce que le serveur lui envoie.
 */
public class ThroneBlock extends HorizontalDirectionalBlock {

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // Modèle orienté vers le nord : le dossier est au sud (z 13 à 16).
        for (Direction d : Direction.Plane.HORIZONTAL) {
            SHAPES.put(d, Shapes.or(
                    rotated(d, 0, 0, 0, 16, 8, 16),
                    rotated(d, 2, 8, 13, 14, 16, 16),
                    rotated(d, 1, 8, 2, 3, 12, 13),
                    rotated(d, 13, 8, 2, 15, 12, 13)));
        }
    }

    public ThroneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        // Un trône dans une colonie d'un royaume ouvre le menu de ce royaume.
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, pos);
        Optional<Realm> holder = colony.flatMap(c -> CrusadeSavedData.get(sp.server).realmHolding(c));
        if (holder.isPresent()) {
            RequestRealmPacket.sendView(sp, holder.get(), OpenRealmScreenPacket.Target.MENU);
            return InteractionResult.CONSUME;
        }

        RealmService.FoundingCheck check = RealmService.checkFounding(sp, pos);
        if (check.verdict().ok()) {
            CrusadeNetwork.toPlayer(sp, new OpenFoundingScreenPacket(pos, MineColoniesBridge.info(check.colony()),
                    sp.getGameProfile().getName()));
        } else if (check.verdict() == fr.crusadecraft.core.logic.FoundingRules.Verdict.ALREADY_RULER) {
            // Le souverain qui s'assoit sur un trône hors de ses terres consulte quand même son royaume.
            RealmService.realmOf(sp.server, sp.getUUID())
                    .ifPresent(r -> RequestRealmPacket.sendView(sp, r, OpenRealmScreenPacket.Target.MENU));
        } else {
            sp.sendSystemMessage(Component.translatable(check.verdict().messageKey(), check.minLevel()));
        }
        return InteractionResult.CONSUME;
    }

    /** Tourne une boîte définie pour l'orientation nord vers l'orientation demandée. */
    private static VoxelShape rotated(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
