package fr.crusadecraft.client;

import fr.crusadecraft.client.screen.FoundingScreen;
import fr.crusadecraft.client.screen.RealmScreen;
import fr.crusadecraft.client.screen.RegistreScreen;
import fr.crusadecraft.client.toast.CrusadeToast;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Réactions du client aux paquets du serveur. Classe chargée uniquement côté client
 * (appelée à travers {@code DistExecutor}).
 */
public final class ClientPackets {

    private ClientPackets() {}

    public static void showToast(ToastPacket.Kind kind, Component title, Component body) {
        if (!CrusadeConfig.CLIENT.showToasts.get()) return;
        Minecraft.getInstance().getToasts().addToast(new CrusadeToast(kind, title, body));
    }

    public static void openFounding(BlockPos thronePos, ColonyInfo colony, String playerName) {
        Minecraft.getInstance().setScreen(new FoundingScreen(thronePos, colony, playerName));
    }

    public static void openRealm(RealmView view, OpenRealmScreenPacket.Target target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == OpenRealmScreenPacket.Target.REGISTRE) {
            mc.setScreen(new RegistreScreen(view));
        } else if (mc.screen instanceof RealmScreen open && open.isFor(view)) {
            open.refresh(view);
        } else {
            mc.setScreen(new RealmScreen(view));
        }
    }
}
