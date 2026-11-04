package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.EggScreen;
import fr.crusadecraft.client.screen.InhabitantScreen;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityEggs;
import fr.crusadecraft.core.city.CityTalk;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Parler à un habitant, et faire naître un habitant d'un œuf (P42). */
public final class InhabitantPackets {
    private static final int TEXT_MAX = 2048, LIST_MAX = 64;

    private InhabitantPackets() {}

    /** La fiche d'un habitant et ce qu'il vient de dire. */
    public record View(UUID id, int entity, String name, String job, String city, String realm, int age, List<Integer> traits, String bio,
                       List<String> family, int opinion, String line, boolean trades, int hunger) {}

    public record Open(View view) {
        public static void encode(Open p, FriendlyByteBuf b) {
            View v = p.view;
            b.writeUUID(v.id());
            b.writeVarInt(v.entity());
            b.writeUtf(v.name());
            b.writeUtf(v.job());
            b.writeUtf(v.city());
            b.writeUtf(v.realm());
            b.writeVarInt(v.age());
            b.writeVarInt(v.traits().size());
            for (int t : v.traits()) b.writeVarInt(t);
            b.writeUtf(v.bio(), TEXT_MAX);
            b.writeVarInt(v.family().size());
            for (String s : v.family()) b.writeUtf(s);
            b.writeVarInt(v.opinion() + 100);
            b.writeUtf(v.line(), TEXT_MAX);
            b.writeBoolean(v.trades());
            b.writeVarInt(v.hunger());
        }

        public static Open decode(FriendlyByteBuf b) {
            UUID id = b.readUUID();
            int entity = b.readVarInt();
            String name = b.readUtf(), job = b.readUtf(), city = b.readUtf(), realm = b.readUtf();
            int age = b.readVarInt();
            int n = Math.min(b.readVarInt(), LIST_MAX);
            List<Integer> traits = new ArrayList<>(n);
            for (int i = 0; i < n; i++) traits.add(b.readVarInt());
            String bio = b.readUtf(TEXT_MAX);
            n = Math.min(b.readVarInt(), LIST_MAX);
            List<String> family = new ArrayList<>(n);
            for (int i = 0; i < n; i++) family.add(b.readUtf());
            int opinion = b.readVarInt() - 100;
            String line = b.readUtf(TEXT_MAX);
            boolean trades = b.readBoolean();
            int hunger = b.readVarInt();
            return new Open(new View(id, entity, name, job, city, realm, age, traits, bio, family, opinion, line, trades, hunger));
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> InhabitantScreen.open(p.view));
        }
    }

    /** Aborder un sujet ; {@code topic} négatif : ouvrir son étal. */
    public record Say(UUID id, int topic) {
        public static void encode(Say p, FriendlyByteBuf b) {
            b.writeUUID(p.id);
            b.writeVarInt(p.topic + 1);
        }

        public static Say decode(FriendlyByteBuf b) {
            return new Say(b.readUUID(), b.readVarInt() - 1);
        }

        public static void handle(Say p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CityTalk.Topic[] all = CityTalk.Topic.values();
            if (p.topic < 0) CityTalk.trade(player, p.id);
            else if (p.topic < all.length) CityTalk.say(player, p.id, all[p.topic]);
        }
    }

    /** Une ville où faire naître quelqu'un. */
    public record CityRow(UUID id, String name, String realm, int color, int people, int max) {}

    /** L'écran de l'œuf : les villes, de la plus proche à la plus lointaine, et l'endroit visé. */
    public record OpenEgg(BlockPos at, List<CityRow> cities) {
        public static void encode(OpenEgg p, FriendlyByteBuf b) {
            b.writeBlockPos(p.at);
            b.writeVarInt(p.cities.size());
            for (CityRow c : p.cities) {
                b.writeUUID(c.id());
                b.writeUtf(c.name());
                b.writeUtf(c.realm());
                b.writeInt(c.color());
                b.writeVarInt(c.people());
                b.writeVarInt(c.max());
            }
        }

        public static OpenEgg decode(FriendlyByteBuf b) {
            BlockPos at = b.readBlockPos();
            int n = Math.min(b.readVarInt(), 512);
            List<CityRow> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new CityRow(b.readUUID(), b.readUtf(), b.readUtf(), b.readInt(), b.readVarInt(), b.readVarInt()));
            return new OpenEgg(at, out);
        }

        public static void handle(OpenEgg p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EggScreen.open(p.at, p.cities));
        }
    }

    /** Le choix fait dans l'écran de l'œuf : une ville, un métier ; l'habitant naît à l'endroit visé. */
    public record ChooseEgg(UUID site, int job, BlockPos at) {
        public static void encode(ChooseEgg p, FriendlyByteBuf b) {
            b.writeUUID(p.site);
            b.writeVarInt(p.job);
            b.writeBlockPos(p.at);
        }

        public static ChooseEgg decode(FriendlyByteBuf b) {
            return new ChooseEgg(b.readUUID(), b.readVarInt(), b.readBlockPos());
        }

        public static void handle(ChooseEgg p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BuildingFunction f = BuildingFunction.byOrdinal(p.job);
            if (f != null) player.displayClientMessage(CityEggs.choose(player, p.site, f, p.at).message(), true);
        }
    }
}
