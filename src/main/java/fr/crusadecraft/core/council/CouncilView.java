package fr.crusadecraft.core.council;

import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** L'écran du conseil, calculé par le serveur. */
public record CouncilView(String realmName, boolean hasTable, String tableColony, int meetingHour, boolean canConvene,
                          boolean meetingBonus, List<Seat> seats, List<RequestSummary> requests, Post focus,
                          long gold, int piety, double happinessFactor) {

    /** Un poste du conseil, son titulaire et sa tâche. */
    public record Seat(Post post, CitizenRef holder, String name, boolean female, int stat, int opinion,
                       CharacterStatus status, CouncilTask task) {}

    /** Une requête en attente, telle qu'elle apparaît dans les listes. */
    public record RequestSummary(java.util.UUID id, RequestType type, String fromName, String place, int count, long day) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeEnum(type);
            buf.writeUtf(fromName);
            buf.writeUtf(place);
            buf.writeVarInt(count);
            buf.writeVarLong(day);
        }

        public static RequestSummary read(FriendlyByteBuf buf) {
            return new RequestSummary(buf.readUUID(), buf.readEnum(RequestType.class), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarLong());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(realmName);
        buf.writeBoolean(hasTable);
        buf.writeUtf(tableColony);
        buf.writeVarInt(meetingHour);
        buf.writeBoolean(canConvene);
        buf.writeBoolean(meetingBonus);
        buf.writeVarInt(seats.size());
        for (Seat s : seats) {
            buf.writeEnum(s.post());
            buf.writeBoolean(s.holder() != null);
            if (s.holder() != null) s.holder().write(buf);
            buf.writeUtf(s.name());
            buf.writeBoolean(s.female());
            buf.writeVarInt(s.stat());
            buf.writeVarInt(s.opinion());
            buf.writeEnum(s.status());
            buf.writeBoolean(s.task() != null);
            if (s.task() != null) buf.writeEnum(s.task());
        }
        buf.writeVarInt(requests.size());
        for (RequestSummary r : requests) r.write(buf);
        buf.writeBoolean(focus != null);
        if (focus != null) buf.writeEnum(focus);
        buf.writeVarLong(gold);
        buf.writeVarInt(piety);
        buf.writeDouble(happinessFactor);
    }

    public static CouncilView read(FriendlyByteBuf buf) {
        String realm = buf.readUtf();
        boolean hasTable = buf.readBoolean();
        String tableColony = buf.readUtf();
        int hour = buf.readVarInt();
        boolean canConvene = buf.readBoolean();
        boolean bonus = buf.readBoolean();
        int n = buf.readVarInt();
        List<Seat> seats = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Post post = buf.readEnum(Post.class);
            CitizenRef holder = buf.readBoolean() ? CitizenRef.read(buf) : null;
            String name = buf.readUtf();
            boolean female = buf.readBoolean();
            int stat = buf.readVarInt();
            int opinion = buf.readVarInt();
            CharacterStatus status = buf.readEnum(CharacterStatus.class);
            CouncilTask task = buf.readBoolean() ? buf.readEnum(CouncilTask.class) : null;
            seats.add(new Seat(post, holder, name, female, stat, opinion, status, task));
        }
        int nr = buf.readVarInt();
        List<RequestSummary> requests = new ArrayList<>(nr);
        for (int i = 0; i < nr; i++) requests.add(RequestSummary.read(buf));
        Post focus = buf.readBoolean() ? buf.readEnum(Post.class) : null;
        return new CouncilView(realm, hasTable, tableColony, hour, canConvene, bonus, seats, requests, focus,
                buf.readVarLong(), buf.readVarInt(), buf.readDouble());
    }
}
