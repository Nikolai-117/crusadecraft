package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilService;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.core.council.MayorService;
import fr.crusadecraft.core.council.MayorView;
import fr.crusadecraft.core.council.RequestService;
import fr.crusadecraft.core.council.RequestType;
import fr.crusadecraft.core.council.RequestView;
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

/** Paquets de la cour : conseil, bureau du maire, requêtes. */
public final class CourtPackets {

    private CourtPackets() {}

    static void reply(ServerPlayer player, CouncilActions.Outcome outcome) {
        player.displayClientMessage(outcome.message(), true);
    }

    static void sendCouncil(ServerPlayer player, Post focus) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenCouncil(CouncilService.view(player.server, realm.get(), focus)));
    }

    // ------------------------------------------------------------------ serveur → client

    public record OpenCouncil(CouncilView view) {
        public static void encode(OpenCouncil p, FriendlyByteBuf buf) { p.view.write(buf); }
        public static OpenCouncil decode(FriendlyByteBuf buf) { return new OpenCouncil(CouncilView.read(buf)); }
        public static void handle(OpenCouncil p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openCouncil(p.view));
        }
    }

    public record OpenMayor(MayorView view) {
        public static void encode(OpenMayor p, FriendlyByteBuf buf) { p.view.write(buf); }
        public static OpenMayor decode(FriendlyByteBuf buf) { return new OpenMayor(MayorView.read(buf)); }
        public static void handle(OpenMayor p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openMayor(p.view));
        }
    }

    public record OpenRequest(RequestView view) {
        public static void encode(OpenRequest p, FriendlyByteBuf buf) { p.view.write(buf); }
        public static OpenRequest decode(FriendlyByteBuf buf) { return new OpenRequest(RequestView.read(buf)); }
        public static void handle(OpenRequest p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openRequest(p.view));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** Actions à la Table du Conseil. */
    public record CouncilAction(Kind kind, Post post, CouncilTask task, UUID requestId) {
        public enum Kind { OPEN, SET_TASK, CONVENE, OPEN_REQUEST }

        public static void encode(CouncilAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeBoolean(p.post != null);
            if (p.post != null) buf.writeEnum(p.post);
            buf.writeBoolean(p.task != null);
            if (p.task != null) buf.writeEnum(p.task);
            buf.writeBoolean(p.requestId != null);
            if (p.requestId != null) buf.writeUUID(p.requestId);
        }

        public static CouncilAction decode(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            Post post = buf.readBoolean() ? buf.readEnum(Post.class) : null;
            CouncilTask task = buf.readBoolean() ? buf.readEnum(CouncilTask.class) : null;
            UUID id = buf.readBoolean() ? buf.readUUID() : null;
            return new CouncilAction(kind, post, task, id);
        }

        public static void handle(CouncilAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (p.kind) {
                case OPEN -> sendCouncil(player, p.post);
                case SET_TASK -> {
                    reply(player, CouncilService.setTask(player, p.post, p.task));
                    sendCouncil(player, p.post);
                }
                case CONVENE -> {
                    reply(player, CouncilService.convene(player));
                    sendCouncil(player, null);
                }
                case OPEN_REQUEST -> {
                    if (p.requestId != null) RequestService.open(player, p.requestId);
                }
            }
        }
    }

    /** Actions au Bureau du Maire. */
    public record MayorAction(ColonyRef colony, CitizenRef candidate, boolean openRequest, UUID requestId) {
        public static void encode(MayorAction p, FriendlyByteBuf buf) {
            p.colony.write(buf);
            buf.writeBoolean(p.candidate != null);
            if (p.candidate != null) p.candidate.write(buf);
            buf.writeBoolean(p.openRequest);
            buf.writeBoolean(p.requestId != null);
            if (p.requestId != null) buf.writeUUID(p.requestId);
        }

        public static MayorAction decode(FriendlyByteBuf buf) {
            ColonyRef colony = ColonyRef.read(buf);
            CitizenRef cand = buf.readBoolean() ? CitizenRef.read(buf) : null;
            boolean open = buf.readBoolean();
            UUID id = buf.readBoolean() ? buf.readUUID() : null;
            return new MayorAction(colony, cand, open, id);
        }

        public static void handle(MayorAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (p.openRequest && p.requestId != null) {
                RequestService.open(player, p.requestId);
                return;
            }
            if (p.candidate != null) {
                if (!p.candidate.colony().equals(p.colony)) return;
                CharacterService.Outcome out = CharacterService.act(player, p.candidate, CharacterService.Action.APPOINT, Post.MAIRE);
                player.displayClientMessage(out.message(), true);
            }
            CrusadeNetwork.toPlayer(player, new OpenMayor(MayorService.view(player.server, player, p.colony)));
        }
    }

    /** Réponse à une requête. */
    public record AnswerRequest(UUID requestId, RequestType.Choice choice) {
        public static void encode(AnswerRequest p, FriendlyByteBuf buf) {
            buf.writeUUID(p.requestId);
            buf.writeEnum(p.choice);
        }

        public static AnswerRequest decode(FriendlyByteBuf buf) {
            return new AnswerRequest(buf.readUUID(), buf.readEnum(RequestType.Choice.class));
        }

        public static void handle(AnswerRequest p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            reply(player, RequestService.answer(player, p.requestId, p.choice));
        }
    }

    /** « Donner un ordre » dans le dialogue : ouvre le conseil (sur ce poste) ou le bureau du maire. */
    public record OpenOffice(CitizenRef ref) {
        public static void encode(OpenOffice p, FriendlyByteBuf buf) { p.ref.write(buf); }
        public static OpenOffice decode(FriendlyByteBuf buf) { return new OpenOffice(CitizenRef.read(buf)); }

        public static void handle(OpenOffice p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
            Post post = realm.map(r -> r.postOf(p.ref)).orElse(null);
            if (post == null) {
                player.displayClientMessage(Component.translatable("crusadecraft.dialogue.order.none"), true);
            } else if (post == Post.MAIRE) {
                CrusadeNetwork.toPlayer(player, new OpenMayor(MayorService.view(player.server, player, p.ref.colony())));
            } else {
                sendCouncil(player, post);
            }
        }
    }
}
