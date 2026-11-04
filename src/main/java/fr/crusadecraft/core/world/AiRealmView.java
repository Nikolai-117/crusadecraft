package fr.crusadecraft.core.world;

import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La fiche d'un royaume IA découvert : ce que le héraut ou la carte en apprend au joueur. */
public record AiRealmView(UUID id, String place, TitleRank rank, int color, String house, AiPersonality personality,
                          Person ruler, List<Seat> council, List<Province> provinces, long gold, int prestige, int piety,
                          long discoveredDay, int distance, AiFaith faith, boolean nation) {

    public record Person(String name, boolean female, int age, int[] stats, List<Trait> traits) {
        static Person of(AiCharacter c) {
            return new Person(c.name(), c.female(), c.age(), c.stats().clone(), c.traits());
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(name);
            buf.writeBoolean(female);
            buf.writeVarInt(age);
            buf.writeVarIntArray(stats);
            buf.writeVarInt(traits.size());
            for (Trait t : traits) buf.writeEnum(t);
        }

        static Person read(FriendlyByteBuf buf) {
            String name = buf.readUtf();
            boolean female = buf.readBoolean();
            int age = buf.readVarInt();
            int[] stats = buf.readVarIntArray(Stat.COUNT);
            int n = buf.readVarInt();
            List<Trait> traits = new ArrayList<>(n);
            for (int i = 0; i < n; i++) traits.add(buf.readEnum(Trait.class));
            return new Person(name, female, age, stats, traits);
        }
    }

    public record Seat(Post post, Person person) {}

    public record Province(String name, boolean capital, AiProvince.Anchor anchor, int population, int development, int x, int z) {}

    public static AiRealmView of(AiRealm r, long discoveredDay, int distance) {
        List<Seat> council = new ArrayList<>();
        for (var e : r.council().entrySet()) council.add(new Seat(e.getKey(), Person.of(e.getValue())));
        List<Province> provinces = new ArrayList<>();
        for (AiProvince p : r.ownProvinces()) {
            provinces.add(new Province(p.name(), p.capital(), p.anchor(), p.population(), p.development(), p.seat().getX(), p.seat().getZ()));
        }
        return new AiRealmView(r.id(), r.place(), r.rank(), r.color(), r.house(), r.personality(), Person.of(r.ruler()),
                council, provinces, r.gold(), r.prestige(), r.piety(), discoveredDay, distance, r.faith(), !r.nation().isEmpty());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(place);
        buf.writeEnum(rank);
        buf.writeInt(color);
        buf.writeUtf(house);
        buf.writeEnum(personality);
        ruler.write(buf);
        buf.writeVarInt(council.size());
        for (Seat s : council) {
            buf.writeEnum(s.post());
            s.person().write(buf);
        }
        buf.writeVarInt(provinces.size());
        for (Province p : provinces) {
            buf.writeUtf(p.name());
            buf.writeBoolean(p.capital());
            buf.writeEnum(p.anchor());
            buf.writeVarInt(p.population());
            buf.writeVarInt(p.development());
            buf.writeVarInt(p.x());
            buf.writeVarInt(p.z());
        }
        buf.writeLong(gold);
        buf.writeVarInt(prestige);
        buf.writeVarInt(piety);
        buf.writeLong(discoveredDay);
        buf.writeVarInt(distance);
        buf.writeEnum(faith);
        buf.writeBoolean(nation);
    }

    public static AiRealmView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String place = buf.readUtf();
        TitleRank rank = buf.readEnum(TitleRank.class);
        int color = buf.readInt();
        String house = buf.readUtf();
        AiPersonality personality = buf.readEnum(AiPersonality.class);
        Person ruler = Person.read(buf);
        int n = buf.readVarInt();
        List<Seat> council = new ArrayList<>(n);
        for (int i = 0; i < n; i++) council.add(new Seat(buf.readEnum(Post.class), Person.read(buf)));
        n = buf.readVarInt();
        List<Province> provinces = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            provinces.add(new Province(buf.readUtf(), buf.readBoolean(), buf.readEnum(AiProvince.Anchor.class), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new AiRealmView(id, place, rank, color, house, personality, ruler, council, provinces, buf.readLong(),
                buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readEnum(AiFaith.class), buf.readBoolean());
    }
}
