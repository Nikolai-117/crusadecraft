package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.NpcEditorScreen;
import fr.crusadecraft.core.city.CityNpc;
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

/** L'éditeur d'un habitant (P47). Tout passe en NBT : une clé présente est une chose à changer ; le serveur revérifie tout. */
public final class NpcPackets {
    private NpcPackets() {}

    public record Open(CompoundTag view) {
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeNbt(p.view);
        }

        public static Open decode(FriendlyByteBuf b) {
            return new Open(b.readNbt());
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            if (p.view != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NpcEditorScreen.open(p.view));
        }
    }

    /** Ouvrir l'éditeur de cette personne. */
    public record Request(UUID id) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
        }

        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readUUID());
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityNpc.open(player, p.id);
        }
    }

    public record Edit(UUID id, CompoundTag change) {
        public static void encode(Edit p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
            b.writeNbt(p.change);
        }

        public static Edit decode(FriendlyByteBuf b) {
            return new Edit(b.readUUID(), b.readNbt());
        }

        public static void handle(Edit p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || p.change == null) return;
            Outcome out = CityNpc.edit(player, p.id, p.change);
            player.displayClientMessage(out.message().copy().withStyle(out.success() ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        }
    }
}
