package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Notification façon CK3, affichée en haut de l'écran du joueur. */
public record ToastPacket(Kind kind, Component title, Component body) {

    /** Le type choisit l'icône et la couleur du liseré. */
    public enum Kind {
        REALM, ALERT, LETTER, EVENT, WAR, COUNCIL
    }

    public static ToastPacket of(Kind kind, Component title, Component body) {
        return new ToastPacket(kind, title, body);
    }

    public static void encode(ToastPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.kind);
        buf.writeComponent(p.title);
        buf.writeComponent(p.body);
    }

    public static ToastPacket decode(FriendlyByteBuf buf) {
        return new ToastPacket(buf.readEnum(Kind.class), buf.readComponent(), buf.readComponent());
    }

    public static void handle(ToastPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.showToast(p.kind, p.title, p.body));
    }
}
