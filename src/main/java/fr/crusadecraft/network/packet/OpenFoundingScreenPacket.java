package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.ColonyInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Le serveur a validé la position du trône : le client affiche l'écran de fondation. */
public record OpenFoundingScreenPacket(BlockPos thronePos, ColonyInfo colony, String playerName) {

    public static void encode(OpenFoundingScreenPacket p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.thronePos);
        p.colony.write(buf);
        buf.writeUtf(p.playerName);
    }

    public static OpenFoundingScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenFoundingScreenPacket(buf.readBlockPos(), ColonyInfo.read(buf), buf.readUtf());
    }

    public static void handle(OpenFoundingScreenPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openFounding(p.thronePos, p.colony, p.playerName));
    }
}
