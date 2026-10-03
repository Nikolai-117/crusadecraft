package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.event.EventService;
import fr.crusadecraft.core.event.EventView;
import fr.crusadecraft.core.intrigue.IntrigueService;
import fr.crusadecraft.core.intrigue.IntrigueView;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
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

/** Paquets des événements à choix et des intrigues de la cour. */
public final class IntriguePackets {

    private IntriguePackets() {}

    private static final UUID NONE = new UUID(0, 0);

    /** Ouvre le premier événement en attente (ou celui demandé) ; s'il n'y en a plus, l'écran se ferme. */
    public static void sendEvent(ServerPlayer player, UUID id) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        Optional<EventView> view = EventService.view(player.server, realm.get(), id);
        CrusadeNetwork.toPlayer(player, new OpenEvent(view.orElse(null)));
    }

    public static void sendIntrigue(ServerPlayer player, boolean open) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenIntrigue(IntrigueService.view(player.server, realm.get()), open));
    }

    // ------------------------------------------------------------------ serveur → client

    /** {@code view} nul : plus rien n'attend, l'écran d'événement se ferme. */
    public record OpenEvent(EventView view) {
        public static void encode(OpenEvent p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.view != null);
            if (p.view != null) p.view.write(buf);
        }

        public static OpenEvent decode(FriendlyByteBuf buf) {
            return new OpenEvent(buf.readBoolean() ? EventView.read(buf) : null);
        }

        public static void handle(OpenEvent p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.event(p.view));
        }
    }

    public record OpenIntrigue(IntrigueView view, boolean open) {
        public static void encode(OpenIntrigue p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenIntrigue decode(FriendlyByteBuf buf) {
            return new OpenIntrigue(IntrigueView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenIntrigue p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.intrigue(p.view, p.open));
        }
    }

    /** Le livre des chroniques à ouvrir : nom du royaume, maison, et les faits du règne dans l'ordre des jours. */
    public record ChronicleBook(String realm, String house, java.util.List<WorldPackets.NewsLine> lines) {
        public static void encode(ChronicleBook p, FriendlyByteBuf buf) {
            buf.writeUtf(p.realm);
            buf.writeUtf(p.house);
            WorldPackets.writeLines(buf, p.lines);
        }

        public static ChronicleBook decode(FriendlyByteBuf buf) {
            return new ChronicleBook(buf.readUtf(), buf.readUtf(), WorldPackets.readLines(buf));
        }

        public static void handle(ChronicleBook p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.chronicleBook(p.realm, p.house, p.lines));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** Demander un événement en attente ({@code id} nul : le premier). */
    public record RequestEvent(UUID id) {
        public static void encode(RequestEvent p, FriendlyByteBuf buf) {
            buf.writeUUID(p.id == null ? NONE : p.id);
        }

        public static RequestEvent decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            return new RequestEvent(NONE.equals(id) ? null : id);
        }

        public static void handle(RequestEvent p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) sendEvent(player, p.id);
        }
    }

    /** Choisir une option ; l'événement suivant s'ouvre aussitôt, s'il y en a un. */
    public record AnswerEvent(UUID id, int option) {
        public static void encode(AnswerEvent p, FriendlyByteBuf buf) {
            buf.writeUUID(p.id);
            buf.writeVarInt(p.option);
        }

        public static AnswerEvent decode(FriendlyByteBuf buf) {
            return new AnswerEvent(buf.readUUID(), buf.readVarInt());
        }

        public static void handle(AnswerEvent p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome out = EventService.answer(player, p.id, p.option);
            CourtPackets.reply(player, out);
            sendEvent(player, null);
        }
    }

    /** Une action dans l'écran des intrigues : rafraîchir, faire chanter, révéler. */
    public record IntrigueAction(Kind kind, UUID secret) {
        public enum Kind { REFRESH, BLACKMAIL, EXPOSE }

        public static void encode(IntrigueAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeUUID(p.secret == null ? NONE : p.secret);
        }

        public static IntrigueAction decode(FriendlyByteBuf buf) {
            return new IntrigueAction(buf.readEnum(Kind.class), buf.readUUID());
        }

        public static void handle(IntrigueAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome out = switch (p.kind) {
                case BLACKMAIL -> IntrigueService.blackmail(player, p.secret);
                case EXPOSE -> IntrigueService.expose(player, p.secret);
                case REFRESH -> null;
            };
            if (out != null) CourtPackets.reply(player, out);
            sendIntrigue(player, p.kind == Kind.REFRESH);
        }
    }
}
