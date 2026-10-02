package fr.crusadecraft.core.council;

import fr.crusadecraft.core.character.Post;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Une requête présentée au souverain, comme une fenêtre d'événement de CK3.
 *
 * @param cost  coût affiché pour chaque choix (« 10 or »), vide s'il est gratuit
 */
public record RequestView(UUID id, RequestType type, String fromName, boolean fromFemale, Post post, String place,
                          int count, int entityId, boolean rulerFeminine, List<ChoiceView> choices) {

    public record ChoiceView(RequestType.Choice choice, boolean enabled, int goldCost, int prestigeCost) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeEnum(type);
        buf.writeUtf(fromName);
        buf.writeBoolean(fromFemale);
        buf.writeBoolean(post != null);
        if (post != null) buf.writeEnum(post);
        buf.writeUtf(place);
        buf.writeVarInt(count);
        buf.writeVarInt(entityId);
        buf.writeBoolean(rulerFeminine);
        buf.writeVarInt(choices.size());
        for (ChoiceView c : choices) {
            buf.writeEnum(c.choice());
            buf.writeBoolean(c.enabled());
            buf.writeVarInt(c.goldCost());
            buf.writeVarInt(c.prestigeCost());
        }
    }

    public static RequestView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        RequestType type = buf.readEnum(RequestType.class);
        String from = buf.readUtf();
        boolean female = buf.readBoolean();
        Post post = buf.readBoolean() ? buf.readEnum(Post.class) : null;
        String place = buf.readUtf();
        int count = buf.readVarInt();
        int entityId = buf.readVarInt();
        boolean rulerFeminine = buf.readBoolean();
        int n = buf.readVarInt();
        List<ChoiceView> choices = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            choices.add(new ChoiceView(buf.readEnum(RequestType.Choice.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        }
        return new RequestView(id, type, from, female, post, place, count, entityId, rulerFeminine, choices);
    }
}
