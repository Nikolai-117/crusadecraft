package fr.crusadecraft.core.court;

import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La maison telle que l'écran la montre : membres, citoyens qu'on peut y accueillir, partis possibles chez les IA. */
public record HouseView(int size, int max, int prestige, int adoptCost, List<Member> members, List<Candidate> candidates, List<Match> matches) {

    /** Un membre : son conjoint (de sa colonie, ou dynastique), ses enfants, s'il peut encore épouser. */
    public record Member(CitizenRef ref, String name, boolean female, boolean child, boolean heir, String spouse, int children,
                         boolean eligible) {}

    /** Un conseiller ou un maire qu'on pourrait accueillir. {@code postKey} : son poste. */
    public record Candidate(CitizenRef ref, String name, String postKey) {}

    /** Une maison IA, et la réponse qu'elle ferait à une demande en mariage. */
    public record Match(UUID realm, String place, TitleRank rank, int color, String house, AiFaith faith, boolean accepts, String blocked,
                        int score) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(size);
        buf.writeVarInt(max);
        buf.writeVarInt(prestige);
        buf.writeVarInt(adoptCost);
        buf.writeVarInt(members.size());
        for (Member m : members) {
            m.ref().write(buf);
            buf.writeUtf(m.name());
            buf.writeBoolean(m.female());
            buf.writeBoolean(m.child());
            buf.writeBoolean(m.heir());
            buf.writeUtf(m.spouse());
            buf.writeVarInt(m.children());
            buf.writeBoolean(m.eligible());
        }
        buf.writeVarInt(candidates.size());
        for (Candidate c : candidates) {
            c.ref().write(buf);
            buf.writeUtf(c.name());
            buf.writeUtf(c.postKey());
        }
        buf.writeVarInt(matches.size());
        for (Match m : matches) {
            buf.writeUUID(m.realm());
            buf.writeUtf(m.place());
            buf.writeEnum(m.rank());
            buf.writeInt(m.color());
            buf.writeUtf(m.house());
            buf.writeEnum(m.faith());
            buf.writeBoolean(m.accepts());
            buf.writeUtf(m.blocked());
            buf.writeInt(m.score());
        }
    }

    public static HouseView read(FriendlyByteBuf buf) {
        int size = buf.readVarInt(), max = buf.readVarInt(), prestige = buf.readVarInt(), cost = buf.readVarInt();
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new Member(CitizenRef.read(buf), buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                    buf.readVarInt(), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<Candidate> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) candidates.add(new Candidate(CitizenRef.read(buf), buf.readUtf(), buf.readUtf()));
        n = buf.readVarInt();
        List<Match> matches = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            matches.add(new Match(buf.readUUID(), buf.readUtf(), buf.readEnum(TitleRank.class), buf.readInt(), buf.readUtf(),
                    buf.readEnum(AiFaith.class), buf.readBoolean(), buf.readUtf(), buf.readInt()));
        }
        return new HouseView(size, max, prestige, cost, members, candidates, matches);
    }
}
