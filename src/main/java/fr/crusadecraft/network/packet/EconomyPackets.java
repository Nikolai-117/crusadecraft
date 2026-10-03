package fr.crusadecraft.network.packet;

import fr.crusadecraft.block.TreasuryBlock;
import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawService;
import fr.crusadecraft.core.law.LawsView;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Paquets du trésor et des lois. */
public final class EconomyPackets {

    /** Distance maximale au Coffre du Trésor pour y déposer ou retirer. */
    private static final double CHEST_REACH_SQR = 8 * 8;

    private EconomyPackets() {}

    static void sendTreasury(ServerPlayer player, BlockPos chest) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new TreasuryData(TreasuryService.view(player.server, realm.get()), chest));
    }

    static void sendLaws(ServerPlayer player) {
        RealmService.realmOf(player.server, player.getUUID()).ifPresent(realm ->
                CrusadeNetwork.toPlayer(player, new LawsData(LawService.view(player.server, realm))));
    }

    // ------------------------------------------------------------------ serveur → client

    /** Le trésor ; {@code chest} est le coffre ouvert (les dépôts et retraits n'y sont possibles que là). */
    public record TreasuryData(TreasuryView view, BlockPos chest) {
        public static void encode(TreasuryData p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.chest != null);
            if (p.chest != null) buf.writeBlockPos(p.chest);
        }

        public static TreasuryData decode(FriendlyByteBuf buf) {
            TreasuryView view = TreasuryView.read(buf);
            return new TreasuryData(view, buf.readBoolean() ? buf.readBlockPos() : null);
        }

        public static void handle(TreasuryData p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.treasury(p.view, p.chest));
        }
    }

    public record LawsData(LawsView view) {
        public static void encode(LawsData p, FriendlyByteBuf buf) { p.view.write(buf); }
        public static LawsData decode(FriendlyByteBuf buf) { return new LawsData(LawsView.read(buf)); }
        public static void handle(LawsData p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.laws(p.view));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** Demande les données d'un onglet du menu royal. */
    public record RequestTab(Tab tab) {
        public enum Tab { TREASURY, LAWS, DIPLOMACY, VASSALS }

        public static void encode(RequestTab p, FriendlyByteBuf buf) { buf.writeEnum(p.tab); }
        public static RequestTab decode(FriendlyByteBuf buf) { return new RequestTab(buf.readEnum(Tab.class)); }

        public static void handle(RequestTab p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (p.tab) {
                case TREASURY -> sendTreasury(player, null);
                case LAWS -> sendLaws(player);
                case DIPLOMACY -> DiplomacyPackets.sendDiplomacy(player, false);
                case VASSALS -> DiplomacyPackets.sendChancery(player, false);
            }
        }
    }

    /** Dépôt ou retrait au Coffre du Trésor. */
    public record TreasuryAction(BlockPos chest, Kind kind, ResourceLocation item, int count) {
        public enum Kind { DEPOSIT_ALL, WITHDRAW }

        public static void encode(TreasuryAction p, FriendlyByteBuf buf) {
            buf.writeBlockPos(p.chest);
            buf.writeEnum(p.kind);
            buf.writeResourceLocation(p.item);
            buf.writeVarInt(p.count);
        }

        public static TreasuryAction decode(FriendlyByteBuf buf) {
            return new TreasuryAction(buf.readBlockPos(), buf.readEnum(Kind.class), buf.readResourceLocation(), buf.readVarInt());
        }

        public static void handle(TreasuryAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Le client ne fait que demander : on revérifie le coffre, la distance et le propriétaire.
            boolean near = player.distanceToSqr(p.chest.getX() + 0.5, p.chest.getY() + 0.5, p.chest.getZ() + 0.5) <= CHEST_REACH_SQR;
            boolean chest = player.level().isLoaded(p.chest) && player.level().getBlockState(p.chest).is(ModBlocks.TREASURY.get());
            Optional<Realm> owner = chest ? TreasuryBlock.owner(player.level(), p.chest) : Optional.empty();
            if (!near || owner.isEmpty() || !owner.get().ruler().equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("crusadecraft.treasury.too_far"), true);
                return;
            }
            CouncilActions.Outcome outcome = p.kind == Kind.DEPOSIT_ALL
                    ? TreasuryService.depositAll(player)
                    : TreasuryService.withdraw(player, p.item, Math.max(1, Math.min(640, p.count)));
            CourtPackets.reply(player, outcome);
            sendTreasury(player, p.chest);
        }
    }

    /** Proposer une loi au conseil ; {@code force} passe outre un vote contraire. */
    public record ProposeLaw(Law law, int level, boolean force) {
        public static void encode(ProposeLaw p, FriendlyByteBuf buf) {
            buf.writeEnum(p.law);
            buf.writeVarInt(p.level);
            buf.writeBoolean(p.force);
        }

        public static ProposeLaw decode(FriendlyByteBuf buf) {
            return new ProposeLaw(buf.readEnum(Law.class), buf.readVarInt(), buf.readBoolean());
        }

        public static void handle(ProposeLaw p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome outcome = LawService.propose(player, p.law, p.level, p.force);
            CourtPackets.reply(player, outcome);
            if (outcome.success()) {
                CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM,
                        Component.translatable("crusadecraft.toast.law.title"), outcome.message()));
            }
            sendLaws(player);
        }
    }
}
