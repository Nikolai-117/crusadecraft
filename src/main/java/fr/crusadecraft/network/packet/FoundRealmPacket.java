package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.core.realm.RealmService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Le joueur valide l'écran de fondation. Le serveur revérifie tout avant de fonder. */
public record FoundRealmPacket(BlockPos thronePos, String name, String motto, String house, int colorIndex,
                               boolean feminine) {

    // Bornes larges côté réseau ; le nettoyage précis se fait dans RealmService.
    private static final int WIRE_MAX = 256;

    public static void encode(FoundRealmPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.thronePos);
        buf.writeUtf(p.name, WIRE_MAX);
        buf.writeUtf(p.motto, WIRE_MAX);
        buf.writeUtf(p.house, WIRE_MAX);
        buf.writeVarInt(p.colorIndex);
        buf.writeBoolean(p.feminine);
    }

    public static FoundRealmPacket decode(FriendlyByteBuf buf) {
        return new FoundRealmPacket(buf.readBlockPos(), buf.readUtf(WIRE_MAX), buf.readUtf(WIRE_MAX),
                buf.readUtf(WIRE_MAX), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(FoundRealmPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        RealmService.found(player, new RealmService.FoundingRequest(p.thronePos, p.name, p.motto, p.house,
                        p.colorIndex, p.feminine))
                .ifPresent(realm -> RequestRealmPacket.sendView(player, realm, OpenRealmScreenPacket.Target.MENU));
    }

    /** Longueurs maximales acceptées par l'écran (les mêmes que le serveur appliquera). */
    public static int nameMax() { return TextRules.NAME_MAX; }
    public static int mottoMax() { return TextRules.MOTTO_MAX; }
    public static int houseMax() { return TextRules.HOUSE_MAX; }
}
