package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/** Le joueur demande la fiche d'un citoyen (sceptre, dialogue, menu royal). */
public record RequestCharacterPacket(CitizenRef ref, Mode mode) {

    /** Fiche simple, dialogue d'un dignitaire, ou fiche ouverte sur la nomination. */
    public enum Mode { SHEET, DIALOGUE, APPOINT }

    public static void encode(RequestCharacterPacket p, FriendlyByteBuf buf) {
        p.ref.write(buf);
        buf.writeEnum(p.mode);
    }

    public static RequestCharacterPacket decode(FriendlyByteBuf buf) {
        return new RequestCharacterPacket(CitizenRef.read(buf), buf.readEnum(Mode.class));
    }

    public static void handle(RequestCharacterPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        send(player, p.ref, p.mode);
    }

    public static void send(ServerPlayer player, CitizenRef ref, Mode mode) {
        Optional<CharacterView> view = CharacterService.view(player.server, ref, player);
        if (view.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.character.missing"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenCharacterPacket(view.get(), mode));
    }
}
