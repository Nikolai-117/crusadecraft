package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.NationScreen;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.nation.NationView;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** L'écran d'une nation (P41) : le demander, l'ouvrir, et y agir. */
public final class NationPackets {
    private NationPackets() {}

    /** Envoie à ce joueur l'écran de cette nation. */
    public static void send(ServerPlayer player, AiRealm realm) {
        CrusadeNetwork.toPlayer(player, new Open(NationService.view(player.server, realm, player.getUUID())));
    }

    /** Ouvrir l'écran d'une nation, ou celle dont on est le souverain ({@code realm} nul). */
    public record Request(UUID realm) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.realm != null);
            if (p.realm != null) buf.writeUUID(p.realm);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readBoolean() ? buf.readUUID() : null);
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Optional<AiRealm> realm = p.realm == null ? NationService.ruledBy(player.server, player.getUUID())
                    : CrusadeSavedData.get(player.server).world().realm(p.realm).filter(r -> !r.nation().isEmpty() || r.discoveredBy(player.getUUID()));
            if (realm.isEmpty()) {
                player.displayClientMessage(Component.translatable("crusadecraft.nation.none"), true);
                return;
            }
            send(player, realm.get());
        }
    }

    public record Open(NationView view) {
        public static void encode(Open p, FriendlyByteBuf buf) {
            p.view.write(buf);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(NationView.read(buf));
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NationScreen.open(p.view));
        }
    }

    /** Un geste du souverain : voir {@code NationService.OP_*}. */
    public record Act(UUID target, int op, int arg, UUID mail) {
        private static final UUID NONE = new UUID(0, 0);

        public static void encode(Act p, FriendlyByteBuf buf) {
            buf.writeUUID(p.target == null ? NONE : p.target);
            buf.writeVarInt(p.op);
            buf.writeVarInt(p.arg + 1);
            buf.writeUUID(p.mail == null ? NONE : p.mail);
        }

        public static Act decode(FriendlyByteBuf buf) {
            UUID target = buf.readUUID();
            int op = buf.readVarInt(), arg = buf.readVarInt() - 1;
            UUID mail = buf.readUUID();
            return new Act(target.equals(NONE) ? null : target, op, arg, mail.equals(NONE) ? null : mail);
        }

        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Outcome out = NationService.act(player, p.target, p.op, p.arg, p.mail);
            player.displayClientMessage(out.message(), true);
            NationService.ruledBy(player.server, player.getUUID()).ifPresent(r -> send(player, r));
        }
    }
}
