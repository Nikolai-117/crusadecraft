package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.realm.RealmView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Ouvre le menu royal ou le registre, avec l'état du royaume calculé par le serveur. */
public record OpenRealmScreenPacket(RealmView view, Target target) {

    public enum Target {
        MENU, REGISTRE
    }

    public static void encode(OpenRealmScreenPacket p, FriendlyByteBuf buf) {
        p.view.write(buf);
        buf.writeEnum(p.target);
    }

    public static OpenRealmScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenRealmScreenPacket(RealmView.read(buf), buf.readEnum(Target.class));
    }

    public static void handle(OpenRealmScreenPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openRealm(p.view, p.target));
    }
}
