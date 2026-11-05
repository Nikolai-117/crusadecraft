package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.EconomyScreen;
import fr.crusadecraft.core.city.WorldEconomy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** L'écran d'économie du monde (P54). */
public final class WorldEconomyPackets {
    private WorldEconomyPackets() {}

    /** @param refresh une simple mise à jour : si l'écran a été fermé entre-temps, on ne le rouvre pas */
    public record Open(CompoundTag view, boolean refresh) {
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeNbt(p.view);
            b.writeBoolean(p.refresh);
        }

        public static Open decode(FriendlyByteBuf b) {
            return new Open(b.readNbt(), b.readBoolean());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            if (p.view != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EconomyScreen.open(p.view, p.refresh));
        }
    }

    /** P55 : un geste depuis l'écran. Le serveur revérifie tout. */
    public record Action(CompoundTag change) {
        public static void encode(Action p, FriendlyByteBuf b) {
            b.writeNbt(p.change);
        }

        public static Action decode(FriendlyByteBuf b) {
            return new Action(b.readNbt());
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || p.change == null) return;
            fr.crusadecraft.core.council.CouncilActions.Outcome out = WorldEconomy.act(player, p.change);
            player.displayClientMessage(out.message().copy().withStyle(out.success() ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED), true);
            WorldEconomy.send(player, true);
        }
    }

    public record Request(boolean refresh) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeBoolean(p.refresh);
        }

        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readBoolean());
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) WorldEconomy.send(player, p.refresh);
        }
    }
}
