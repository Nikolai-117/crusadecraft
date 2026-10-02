package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.character.CharacterView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Ouvre la fiche (ou le dialogue) d'un personnage, calculée par le serveur. */
public record OpenCharacterPacket(CharacterView view, RequestCharacterPacket.Mode mode) {

    public static void encode(OpenCharacterPacket p, FriendlyByteBuf buf) {
        p.view.write(buf);
        buf.writeEnum(p.mode);
    }

    public static OpenCharacterPacket decode(FriendlyByteBuf buf) {
        return new OpenCharacterPacket(CharacterView.read(buf), buf.readEnum(RequestCharacterPacket.Mode.class));
    }

    public static void handle(OpenCharacterPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.openCharacter(p.view, p.mode));
    }
}
