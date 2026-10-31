package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.CityService;
import fr.crusadecraft.core.realm.CityView;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Les villes du royaume (P19) : ouvrir l'écran, rattacher, confier, reprendre. Le serveur revérifie tout. */
public final class CityPackets {
    private CityPackets() {}

    private static void send(ServerPlayer player, boolean open, Outcome out) {
        CityService.view(player).ifPresentOrElse(
                v -> CrusadeNetwork.toPlayer(player, new Open(v, open, out == null ? null : out.message(), out != null && out.success())),
                () -> player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true));
    }

    /** Ouvre l'écran des villes. */
    public record Request() {
        public static void encode(Request p, FriendlyByteBuf buf) {}

        public static Request decode(FriendlyByteBuf buf) {
            return new Request();
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) send(player, true, null);
        }
    }

    /** L'écran, avec la réponse au dernier geste s'il y en a une. */
    public record Open(CityView view, boolean open, Component message, boolean success) {
        public static void encode(Open p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
            buf.writeBoolean(p.message != null);
            if (p.message != null) buf.writeComponent(p.message);
            buf.writeBoolean(p.success);
        }

        public static Open decode(FriendlyByteBuf buf) {
            CityView view = CityView.read(buf);
            boolean open = buf.readBoolean();
            Component message = buf.readBoolean() ? buf.readComponent() : null;
            return new Open(view, open, message, buf.readBoolean());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.cities(p.view, p.open, p.message, p.success));
        }
    }

    /** Un geste sur une ville. {@code governor} nul : la reprendre en direct. */
    public record Action(Op op, ColonyRef colony, UUID governor, int taxShare, int levyShare) {
        public enum Op { ATTACH_HERE, ENTRUST, RECALL }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeEnum(p.op);
            buf.writeBoolean(p.colony != null);
            if (p.colony != null) {
                buf.writeResourceLocation(p.colony.dimension());
                buf.writeVarInt(p.colony.colonyId());
            }
            buf.writeBoolean(p.governor != null);
            if (p.governor != null) buf.writeUUID(p.governor);
            buf.writeVarInt(p.taxShare);
            buf.writeVarInt(p.levyShare);
        }

        public static Action decode(FriendlyByteBuf buf) {
            Op op = buf.readEnum(Op.class);
            ColonyRef colony = buf.readBoolean() ? new ColonyRef(buf.readResourceLocation(), buf.readVarInt()) : null;
            UUID governor = buf.readBoolean() ? buf.readUUID() : null;
            return new Action(op, colony, governor, buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Outcome out = switch (p.op) {
                case ATTACH_HERE -> CityService.attachHere(player);
                case ENTRUST -> CityService.entrust(player, p.colony, p.governor, p.taxShare, p.levyShare);
                case RECALL -> CityService.recall(player, p.colony);
            };
            send(player, false, out);
        }
    }
}
