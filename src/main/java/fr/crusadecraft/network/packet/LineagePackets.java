package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageService;
import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Le registre des lignées (P13) : l'ouvrir, et les demandes de l'écran, toutes revérifiées par {@link LineageService}. */
public final class LineagePackets {
    private static final int TEXT_MAX = 64;

    private LineagePackets() {}

    public static void send(ServerPlayer player, UUID selected, boolean open) {
        CrusadeNetwork.toPlayer(player, new Open(LineageService.view(player, selected), open));
    }

    private static void writeNullable(FriendlyByteBuf buf, UUID id) {
        buf.writeBoolean(id != null);
        if (id != null) buf.writeUUID(id);
    }

    private static UUID readNullable(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUUID() : null;
    }

    // ------------------------------------------------------------------ serveur → client

    /** Le registre ; {@code open} l'ouvre, sinon il ne fait que rafraîchir l'écran ouvert. */
    public record Open(LineageView view, boolean open) {
        public static void encode(Open p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(LineageView.read(buf), buf.readBoolean());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.lineage(p.view, p.open));
        }
    }

    /** La réponse à une demande, affichée au pied du registre. */
    public record Result(boolean success, Component message) {
        public static void encode(Result p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.success);
            buf.writeComponent(p.message);
        }

        public static Result decode(FriendlyByteBuf buf) { return new Result(buf.readBoolean(), buf.readComponent()); }

        public static void handle(Result p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.lineageResult(p.success, p.message));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /**
     * Ouvre le registre. {@code select} : la maison ou la communauté à afficher ; {@code realm} : afficher la maison
     * qui règne sur ce royaume (depuis l'éditeur de la carte).
     */
    public record Request(UUID select, UUID realm) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            writeNullable(buf, p.select);
            writeNullable(buf, p.realm);
        }

        public static Request decode(FriendlyByteBuf buf) { return new Request(readNullable(buf), readNullable(buf)); }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            UUID select = p.select;
            if (p.realm != null) {
                // La vue crée les cours manquantes : on la demande une fois, puis on retrouve la maison du royaume.
                LineageService.view(player, null);
                select = CrusadeSavedData.get(player.server).lineage().houseOfRealm(p.realm).map(h -> h.id()).orElse(null);
            }
            send(player, select, true);
        }
    }

    /**
     * Une demande du registre. Les champs servent selon l'opération ; {@code shown} est la maison ou la communauté que
     * l'écran affichait, pour qu'il y reste.
     */
    public record Action(Op op, UUID target, String text, int a, int b, UUID realm, UUID shown) {
        public enum Op {
            NEW_HOUSE, NEW_COMMUNITY, ADD_CHILD, MARRY, RENAME, ROLE, TOGGLE_ALIVE, REMOVE, GROW, SPAWN, SYNC_TREE, ADOPT
        }

        public static Action of(Op op, UUID target, UUID shown) {
            return new Action(op, target, "", 0, 0, null, shown);
        }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeEnum(p.op);
            writeNullable(buf, p.target);
            buf.writeUtf(p.text, TEXT_MAX);
            buf.writeVarInt(p.a);
            buf.writeVarInt(p.b);
            writeNullable(buf, p.realm);
            writeNullable(buf, p.shown);
        }

        public static Action decode(FriendlyByteBuf buf) {
            return new Action(buf.readEnum(Op.class), readNullable(buf), buf.readUtf(TEXT_MAX), buf.readVarInt(), buf.readVarInt(),
                    readNullable(buf), readNullable(buf));
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = CrusadeSavedData.get(player.server).lineage();
            UUID shown = p.shown;
            Outcome out = switch (p.op) {
                case NEW_HOUSE -> LineageService.createHouse(player, p.text, p.a, p.realm, p.b);
                case NEW_COMMUNITY -> {
                    CommunityKind[] kinds = CommunityKind.values();
                    yield LineageService.createCommunity(player, p.text, kinds[Math.floorMod(p.a, kinds.length)], p.realm, p.b);
                }
                case ADD_CHILD -> LineageService.addChild(player, p.target);
                case MARRY -> LineageService.marry(player, p.target);
                case RENAME -> LineageService.rename(player, p.target, p.text);
                case ROLE -> {
                    Role[] roles = Role.values();
                    yield LineageService.setRole(player, p.target, roles[Math.floorMod(p.a, roles.length)]);
                }
                case TOGGLE_ALIVE -> LineageService.toggleAlive(player, p.target);
                case REMOVE -> LineageService.remove(player, p.target);
                case GROW -> LineageService.grow(player, p.target);
                case SPAWN -> LineageService.spawn(player, p.target);
                case SYNC_TREE -> LineageService.syncTree(player, p.target);
                case ADOPT -> LineageService.adoptNearby(player, p.target);
            };
            // Une maison ou une communauté qu'on vient de créer s'ouvre aussitôt.
            if (out.success() && p.op == Op.NEW_HOUSE) shown = last(state.houses());
            if (out.success() && p.op == Op.NEW_COMMUNITY) shown = last(state.communities());
            CrusadeNetwork.toPlayer(player, new Result(out.success(), out.message()));
            send(player, shown, false);
        }

        private static UUID last(java.util.Collection<?> items) {
            Object last = null;
            for (Object o : items) last = o;
            if (last instanceof fr.crusadecraft.core.lineage.House h) return h.id();
            if (last instanceof fr.crusadecraft.core.lineage.Community c) return c.id();
            return null;
        }
    }
}
