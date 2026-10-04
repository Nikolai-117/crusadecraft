package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.place.LieuKind;
import fr.crusadecraft.core.place.LieuView;
import fr.crusadecraft.core.place.PlaceService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Les lieux (P15) : l'écran, ses demandes (toutes revérifiées par {@link PlaceService}), et la pose d'un lieu au bâton. */
public final class LieuPackets {
    private LieuPackets() {}

    public static void send(ServerPlayer player, UUID selected, boolean open, Component status, boolean ok) {
        CrusadeNetwork.toPlayer(player, new Open(PlaceService.view(player, selected, status, ok), open));
    }

    private static void writeNullable(FriendlyByteBuf buf, UUID id) {
        buf.writeBoolean(id != null);
        if (id != null) buf.writeUUID(id);
    }

    private static UUID readNullable(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    // ------------------------------------------------------------------ serveur → client

    /** L'écran des lieux ; {@code open} l'ouvre, sinon il ne fait que rafraîchir l'écran ouvert. */
    public record Open(LieuView view, boolean open) {
        public static void encode(Open p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(LieuView.read(buf), buf.readBoolean());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.lieux(p.view, p.open));
        }
    }

    /** Le bâton a visé le sol en mode « Lieu » : le formulaire pour le nommer s'ouvre sur ce point. */
    public record OpenForm(int kind, BlockPos pos) {
        public static void encode(OpenForm p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.kind);
            buf.writeBlockPos(p.pos);
        }

        public static OpenForm decode(FriendlyByteBuf buf) {
            return new OpenForm(buf.readVarInt(), buf.readBlockPos());
        }

        public static void handle(OpenForm p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.lieuForm(LieuKind.byOrdinal(p.kind), p.pos));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /**
     * Ouvre l'écran. {@code select} : le lieu à montrer ; à défaut, celui qui renvoie à une chose de la carte
     * ({@code refType} ≥ 0 : un royaume ou une construction par {@code uuid}, une province par {@code index}).
     */
    public record Request(UUID select, int refType, UUID uuid, int index) {
        public static Request of(UUID select) {
            return new Request(select, -1, null, 0);
        }

        public static void encode(Request p, FriendlyByteBuf buf) {
            writeNullable(buf, p.select);
            buf.writeVarInt(p.refType + 1);
            writeNullable(buf, p.uuid);
            buf.writeVarInt(p.index);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(readNullable(buf), buf.readVarInt() - 1, readNullable(buf), buf.readVarInt());
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            UUID select = p.select;
            Lieu.RefType[] types = Lieu.RefType.values();
            if (select == null && p.refType >= 0 && p.refType < types.length) {
                PlaceService.sync(player.server);
                var places = CrusadeSavedData.get(player.server).places();
                Lieu.RefType type = types[p.refType];
                java.util.Optional<Lieu> found = switch (type) {
                    case AI_PROVINCE -> places.byRef(Lieu.Ref.aiProvince(p.index));
                    case SITE -> places.byRef(Lieu.Ref.site(p.uuid));
                    case AI_REALM, PLAYER_REALM -> {
                        var ai = places.byRef(Lieu.Ref.aiRealm(p.uuid));
                        yield ai.isPresent() ? ai : places.byRef(Lieu.Ref.playerRealm(p.uuid));
                    }
                    case COLONY -> java.util.Optional.empty();
                };
                select = found.map(Lieu::id).orElse(null);
            }
            send(player, select, true, null, true);
        }
    }

    /** Une demande de l'écran. Les champs servent selon l'opération. */
    public record Action(Op op, UUID target, String text, int kind, UUID other, BlockPos pos, int radius) {
        public enum Op { CREATE, RENAME, TEXT, PARENT, HOLDER, DELETE }

        public static Action of(Op op, UUID target, String text, UUID other) {
            return new Action(op, target, text == null ? "" : text, 0, other, null, 0);
        }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeEnum(p.op);
            writeNullable(buf, p.target);
            buf.writeUtf(p.text, Lieu.MAX_TEXT);
            buf.writeVarInt(p.kind);
            writeNullable(buf, p.other);
            buf.writeBoolean(p.pos != null);
            if (p.pos != null) buf.writeBlockPos(p.pos);
            buf.writeVarInt(p.radius);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Op.class), readNullable(buf), buf.readUtf(Lieu.MAX_TEXT), buf.readVarInt(), readNullable(buf),
                    buf.readBoolean() ? buf.readBlockPos() : null, buf.readVarInt());
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlaceService.Result r = switch (p.op) {
                case CREATE -> {
                    // Une place se pose où le bâton a visé, sinon aux pieds du joueur ; un titre n'a pas de position.
                    BlockPos pos = p.pos;
                    if (pos != null && pos.distSqr(player.blockPosition()) > 128 * 128) pos = player.blockPosition();
                    yield PlaceService.create(player, LieuKind.byOrdinal(p.kind), p.text, p.other, pos == null ? player.blockPosition() : pos, p.radius);
                }
                case RENAME -> PlaceService.rename(player, p.target, p.text);
                case TEXT -> PlaceService.setText(player, p.target, p.text);
                case PARENT -> PlaceService.setParent(player, p.target, p.other);
                case HOLDER -> PlaceService.setHolder(player, p.target, p.other);
                case DELETE -> PlaceService.delete(player, p.target);
            };
            UUID select = r.select() != null ? r.select() : p.target;
            send(player, select, p.op == Op.CREATE, r.outcome().message(), r.outcome().success());
        }
    }
}
