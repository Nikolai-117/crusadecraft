package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Le souverain agit sur un personnage. Le serveur revérifie ses droits, puis renvoie la fiche à jour. */
public record CharacterActionPacket(CitizenRef ref, CharacterService.Action action, Post post,
                                    RequestCharacterPacket.Mode reopen) {

    public static void encode(CharacterActionPacket p, FriendlyByteBuf buf) {
        p.ref.write(buf);
        buf.writeEnum(p.action);
        buf.writeBoolean(p.post != null);
        if (p.post != null) buf.writeEnum(p.post);
        buf.writeEnum(p.reopen);
    }

    public static CharacterActionPacket decode(FriendlyByteBuf buf) {
        CitizenRef ref = CitizenRef.read(buf);
        CharacterService.Action action = buf.readEnum(CharacterService.Action.class);
        Post post = buf.readBoolean() ? buf.readEnum(Post.class) : null;
        return new CharacterActionPacket(ref, action, post, buf.readEnum(RequestCharacterPacket.Mode.class));
    }

    public static void handle(CharacterActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        CharacterService.Outcome outcome = CharacterService.act(player, p.ref, p.action, p.post);
        player.displayClientMessage(outcome.message(), true);
        RequestCharacterPacket.send(player, p.ref, p.reopen == RequestCharacterPacket.Mode.APPOINT
                ? RequestCharacterPacket.Mode.SHEET : p.reopen);
    }
}
