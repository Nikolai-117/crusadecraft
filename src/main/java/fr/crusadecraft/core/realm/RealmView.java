package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le client a besoin de savoir d'un royaume pour l'afficher (menu royal, registre).
 *
 * <p>Construit par le serveur à la demande ; le client n'a jamais d'autorité sur ces valeurs.
 */
public record RealmView(UUID id, String name, String motto, String house, int color, String rulerName,
                        boolean feminineTitle, TitleRank rank, RealmState state, long foundedDay, long currentDay,
                        long gold, int prestige, int piety, int dread, List<ColonyInfo> colonies, List<Seat> seats, int pendingEvents) {

    /**
     * Un siège de dignitaire : un poste du conseil (ou un maire) et son titulaire. {@code holder} est nul
     * pour un siège vacant ; {@code place} est la colonie pour un maire.
     */
    public record Seat(Post post, CitizenRef holder, String name, boolean female, int opinion, String place) {
        void write(FriendlyByteBuf buf) {
            buf.writeEnum(post);
            buf.writeBoolean(holder != null);
            if (holder != null) holder.write(buf);
            buf.writeUtf(name);
            buf.writeBoolean(female);
            buf.writeVarInt(opinion);
            buf.writeUtf(place);
        }

        static Seat read(FriendlyByteBuf buf) {
            Post post = buf.readEnum(Post.class);
            CitizenRef holder = buf.readBoolean() ? CitizenRef.read(buf) : null;
            return new Seat(post, holder, buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readUtf());
        }
    }

    public ColonyInfo capital() {
        return colonies.isEmpty() ? null : colonies.get(0);
    }

    public int totalCitizens() {
        int total = 0;
        for (ColonyInfo c : colonies) total += c.citizens();
        return total;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name);
        buf.writeUtf(motto);
        buf.writeUtf(house);
        buf.writeInt(color);
        buf.writeUtf(rulerName);
        buf.writeBoolean(feminineTitle);
        buf.writeEnum(rank);
        buf.writeEnum(state);
        buf.writeVarLong(foundedDay);
        buf.writeVarLong(currentDay);
        buf.writeVarLong(gold);
        buf.writeVarInt(prestige);
        buf.writeVarInt(piety);
        buf.writeVarInt(dread);
        buf.writeVarInt(colonies.size());
        for (ColonyInfo c : colonies) c.write(buf);
        buf.writeVarInt(seats.size());
        for (Seat s : seats) s.write(buf);
        buf.writeVarInt(pendingEvents);
    }

    public static RealmView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String name = buf.readUtf();
        String motto = buf.readUtf();
        String house = buf.readUtf();
        int color = buf.readInt();
        String rulerName = buf.readUtf();
        boolean feminine = buf.readBoolean();
        TitleRank rank = buf.readEnum(TitleRank.class);
        RealmState state = buf.readEnum(RealmState.class);
        long founded = buf.readVarLong();
        long current = buf.readVarLong();
        long gold = buf.readVarLong();
        int prestige = buf.readVarInt();
        int piety = buf.readVarInt();
        int dread = buf.readVarInt();
        int n = buf.readVarInt();
        List<ColonyInfo> colonies = new ArrayList<>(n);
        for (int i = 0; i < n; i++) colonies.add(ColonyInfo.read(buf));
        int ns = buf.readVarInt();
        List<Seat> seats = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) seats.add(Seat.read(buf));
        return new RealmView(id, name, motto, house, color, rulerName, feminine, rank, state, founded, current,
                gold, prestige, piety, dread, colonies, seats, buf.readVarInt());
    }
}
