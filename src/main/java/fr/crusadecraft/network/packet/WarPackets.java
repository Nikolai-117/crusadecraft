package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.WarRules;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.war.WarView;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Paquets de la guerre : la Table de guerre et ses ordres. */
public final class WarPackets {

    private WarPackets() {}

    private static final UUID NONE = new UUID(0, 0);

    public static void sendWar(ServerPlayer player, boolean open) {
        if (RealmService.realmOf(player.server, player.getUUID()).isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenWar(WarService.view(player), open));
    }

    // ------------------------------------------------------------------ serveur → client

    /** {@code open} : ouvrir la Table de guerre ; sinon, rafraîchir l'écran ou l'onglet ouvert. */
    public record OpenWar(WarView view, boolean open) {
        public static void encode(OpenWar p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenWar decode(FriendlyByteBuf buf) {
            return new OpenWar(WarView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenWar p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.war(p.view, p.open));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** Un ordre donné à la Table de guerre. */
    public record WarAction(Kind kind, UUID target, CasusBelli cb, int province, WarRules.Peace peace) {
        public enum Kind { REFRESH, DECLARE, RAISE, DISBAND, PEACE }

        public static WarAction of(Kind kind) {
            return new WarAction(kind, null, null, -1, null);
        }

        public static void encode(WarAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeUUID(p.target == null ? NONE : p.target);
            buf.writeBoolean(p.cb != null);
            if (p.cb != null) buf.writeEnum(p.cb);
            buf.writeInt(p.province);
            buf.writeBoolean(p.peace != null);
            if (p.peace != null) buf.writeEnum(p.peace);
        }

        public static WarAction decode(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            UUID target = buf.readUUID();
            CasusBelli cb = buf.readBoolean() ? buf.readEnum(CasusBelli.class) : null;
            int province = buf.readInt();
            WarRules.Peace peace = buf.readBoolean() ? buf.readEnum(WarRules.Peace.class) : null;
            return new WarAction(kind, target, cb, province, peace);
        }

        public static void handle(WarAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome out = switch (p.kind) {
                case DECLARE -> p.cb == null ? null : WarService.declare(player, p.target, p.cb, p.province);
                case RAISE -> WarService.raise(player, true);
                case DISBAND -> WarService.raise(player, false);
                case PEACE -> p.peace == null ? null : WarService.proposePeace(player, p.target, p.peace);
                case REFRESH -> null;
            };
            if (out != null) CourtPackets.reply(player, out);
            sendWar(player, false);
        }
    }
}
