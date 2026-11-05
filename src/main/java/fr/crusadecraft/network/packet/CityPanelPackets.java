package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.CityPanelScreen;
import fr.crusadecraft.core.city.CityPanel;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Le panneau d'une ville (P46). Tout passe en NBT : une clé présente est une chose à changer ; le serveur revérifie tout. */
public final class CityPanelPackets {
    private CityPanelPackets() {}

    public record Open(CompoundTag view) {
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeNbt(p.view);
        }

        public static Open decode(FriendlyByteBuf b) {
            return new Open(b.readNbt());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            if (p.view != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityPanelScreen.open(p.view));
        }
    }

    /** P50 : ce qui se passe à l'instant dans cette ville, à afficher dans l'onglet « En direct ». */
    public record Live(CompoundTag view) {
        public static void encode(Live p, FriendlyByteBuf b) {
            b.writeNbt(p.view);
        }

        public static Live decode(FriendlyByteBuf b) {
            return new Live(b.readNbt());
        }

        public static void handle(Live p, Supplier<NetworkEvent.Context> ctx) {
            if (p.view != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityPanelScreen.onLive(p.view));
        }
    }

    /** P50 : l'onglet « En direct » est ouvert : qu'est-ce qui se passe ? */
    public record AskLive(UUID site) {
        public static void encode(AskLive p, FriendlyByteBuf b) {
            b.writeUUID(p.site);
        }

        public static AskLive decode(FriendlyByteBuf b) {
            return new AskLive(b.readUUID());
        }

        public static void handle(AskLive p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityPanel.sendLive(player, p.site);
        }
    }

    public record Edit(UUID site, CompoundTag change) {
        public static void encode(Edit p, FriendlyByteBuf b) {
            b.writeUUID(p.site);
            b.writeNbt(p.change);
        }

        public static Edit decode(FriendlyByteBuf b) {
            return new Edit(b.readUUID(), b.readNbt());
        }

        public static void handle(Edit p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || p.change == null) return;
            Outcome out = CityPanel.edit(player, p.site, p.change);
            player.displayClientMessage(out.message().copy().withStyle(out.success() ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        }
    }
}
