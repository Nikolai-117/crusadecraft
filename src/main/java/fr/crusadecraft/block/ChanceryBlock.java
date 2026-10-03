package fr.crusadecraft.block;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.diplomacy.TitleService;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.Realm;
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

import java.util.Optional;

/**
 * La Chancery Table : on y crée les titres, on y règle les contrats des vassaux, on y fait fabriquer les
 * revendications. Elle s'ouvre avec la recherche « Chancellerie » de l'Université.
 */
public class ChanceryBlock extends CourtFurnitureBlock {

    public ChanceryBlock(Properties properties) {
        super(properties, new double[][]{{0, 12, 0, 16, 15, 16}, {1, 0, 1, 4, 12, 4}, {12, 0, 1, 15, 12, 4},
                {1, 0, 12, 4, 12, 15}, {12, 0, 12, 15, 12, 15}});
    }

    /** Pourquoi la Chancellerie reste fermée à ce royaume ; vide si elle est ouverte. */
    public static Optional<Component> locked(Realm realm) {
        if (CrusadeConfig.SERVER.chanceryNeedsResearch.get()
                && MineColoniesBridge.researchEffect(realm.capital(), Law.Research.CHANCELLERIE) <= 0) {
            return Optional.of(Component.translatable("crusadecraft.chancery.locked"));
        }
        return Optional.empty();
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
        Optional<Component> lock = locked(realm.get());
        if (lock.isPresent()) {
            sp.displayClientMessage(lock.get(), true);
            return InteractionResult.CONSUME;
        }
        CrusadeNetwork.toPlayer(sp, new DiplomacyPackets.OpenChancery(TitleService.view(sp), true));
        return InteractionResult.CONSUME;
    }
}
