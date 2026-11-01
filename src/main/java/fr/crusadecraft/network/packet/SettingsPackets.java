package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.config.SettingsService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** L'écran de réglages (P30) : l'ouvrir, et changer un réglage. Tout est revérifié par {@link SettingsService}. */
public final class SettingsPackets {
    private SettingsPackets() {}

    public static void send(ServerPlayer player, boolean open) {
        CrusadeNetwork.toPlayer(player, new Open(SettingsService.view(player), open));
    }

    /** Les réglages ; {@code open} ouvre l'écran, sinon il ne fait que le rafraîchir. */
    public record Open(SettingsService.View view, boolean open) {
        public static void encode(Open p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static Open decode(FriendlyByteBuf buf) {
            return new Open(SettingsService.View.read(buf), buf.readBoolean());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.settings(p.view, p.open));
        }
    }

    /** La réponse à un changement, au pied de l'écran. */
    public record Result(boolean success, Component message) {
        public static void encode(Result p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.success);
            buf.writeComponent(p.message);
        }

        public static Result decode(FriendlyByteBuf buf) { return new Result(buf.readBoolean(), buf.readComponent()); }

        public static void handle(Result p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.settingsResult(p.success, p.message));
        }
    }

    /** Ouvrir l'écran ({@code path} vide), ou changer le réglage {@code path} pour {@code value}. */
    public record Request(String path, String value) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeUtf(p.path, 256);
            buf.writeUtf(p.value, 1024);
        }

        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readUtf(256), buf.readUtf(1024)); }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (p.path.isEmpty()) {
                send(player, true);
                return;
            }
            Outcome out = SettingsService.set(player, p.path, p.value);
            CrusadeNetwork.toPlayer(player, new Result(out.success(), out.message()));
            send(player, false);
        }
    }
}
