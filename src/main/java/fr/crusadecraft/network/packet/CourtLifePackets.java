package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.court.HouseService;
import fr.crusadecraft.core.court.HouseView;
import fr.crusadecraft.core.court.TournamentService;
import fr.crusadecraft.core.court.TournamentView;
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

/** Paquets de la vie de cour : la Lice de tournoi et la maison du souverain. */
public final class CourtLifePackets {

    private CourtLifePackets() {}

    private static final UUID NONE = new UUID(0, 0);

    private static Optional<Realm> realm(ServerPlayer player) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
        return realm;
    }

    public static void sendTournament(ServerPlayer player, boolean open) {
        realm(player).ifPresent(r -> CrusadeNetwork.toPlayer(player, new OpenTournament(TournamentService.view(player.server, r, player), open)));
    }

    public static void sendHouse(ServerPlayer player, boolean open) {
        realm(player).ifPresent(r -> CrusadeNetwork.toPlayer(player, new OpenHouse(HouseService.view(player.server, r, player), open)));
    }

    // ------------------------------------------------------------------ serveur → client

    public record OpenTournament(TournamentView view, boolean open) {
        public static void encode(OpenTournament p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenTournament decode(FriendlyByteBuf buf) {
            return new OpenTournament(TournamentView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenTournament p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.tournament(p.view, p.open));
        }
    }

    public record OpenHouse(HouseView view, boolean open) {
        public static void encode(OpenHouse p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenHouse decode(FriendlyByteBuf buf) {
            return new OpenHouse(HouseView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenHouse p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.house(p.view, p.open));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** À la Lice : rafraîchir, ou organiser le tournoi. */
    public record TournamentAction(boolean host) {
        public static void encode(TournamentAction p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.host);
        }

        public static TournamentAction decode(FriendlyByteBuf buf) {
            return new TournamentAction(buf.readBoolean());
        }

        public static void handle(TournamentAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (p.host) CourtPackets.reply(player, TournamentService.host(player));
            sendTournament(player, false);
        }
    }

    /** Dans la maison : ouvrir, accueillir un citoyen, désigner l'héritier, demander un mariage. */
    public record HouseAction(Kind kind, CitizenRef citizen, UUID realm) {
        public enum Kind { REFRESH, ADOPT, HEIR, PROPOSE, FEAST }

        public static HouseAction of(Kind kind) {
            return new HouseAction(kind, null, null);
        }

        public static void encode(HouseAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeBoolean(p.citizen != null);
            if (p.citizen != null) p.citizen.write(buf);
            buf.writeUUID(p.realm == null ? NONE : p.realm);
        }

        public static HouseAction decode(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            CitizenRef citizen = buf.readBoolean() ? CitizenRef.read(buf) : null;
            UUID realm = buf.readUUID();
            return new HouseAction(kind, citizen, NONE.equals(realm) ? null : realm);
        }

        public static void handle(HouseAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome out = switch (p.kind) {
                case ADOPT -> p.citizen == null ? null : HouseService.adopt(player, p.citizen);
                case HEIR -> p.citizen == null ? null : HouseService.designateHeir(player, p.citizen);
                case PROPOSE -> p.citizen == null || p.realm == null ? null : HouseService.propose(player, p.citizen, p.realm);
                case FEAST -> fr.crusadecraft.core.court.FeastService.hold(player);
                case REFRESH -> null;
            };
            if (out != null) CourtPackets.reply(player, out);
            sendHouse(player, p.kind == Kind.REFRESH);
        }
    }
}
