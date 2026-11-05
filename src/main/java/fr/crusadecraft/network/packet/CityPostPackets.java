package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.PostScreen;
import fr.crusadecraft.core.city.CityPost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Le courrier des habitants (P49) : la boîte du joueur, et ce qu'il en fait. */
public final class CityPostPackets {
    public static final int READ = 0, DELETE = 1, CLEAR = 2, OPEN = 3;
    private static final int TEXT_MAX = 2048;

    private CityPostPackets() {}

    public record Open(List<CityPost.Letter> letters) {
        public static void encode(Open p, FriendlyByteBuf b) {
            b.writeVarInt(p.letters.size());
            for (CityPost.Letter l : p.letters) {
                b.writeEnum(l.kind());
                b.writeUtf(l.from());
                b.writeUtf(l.city());
                b.writeUtf(l.text(), TEXT_MAX);
                b.writeLong(l.day());
                b.writeBoolean(l.read());
            }
        }

        public static Open decode(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 256);
            List<CityPost.Letter> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new CityPost.Letter(b.readEnum(CityPost.Kind.class), b.readUtf(), b.readUtf(), b.readUtf(TEXT_MAX), b.readLong(), b.readBoolean()));
            return new Open(out);
        }

        public static void handle(Open p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PostScreen.open(p.letters));
        }
    }

    public record Act(int op, int index) {
        public static void encode(Act p, FriendlyByteBuf b) {
            b.writeVarInt(p.op);
            b.writeVarInt(p.index);
        }

        public static Act decode(FriendlyByteBuf b) {
            return new Act(b.readVarInt(), b.readVarInt());
        }

        public static void handle(Act p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityPost.act(player, p.op, p.index);
        }
    }
}
