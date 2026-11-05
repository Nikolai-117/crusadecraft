package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.skin.SkinClient;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Les skins importés (P47) : les envoyer au serveur, les demander, savoir qui les porte. */
public final class SkinPackets {
    /** Un paquet du client vers le serveur ne dépasse pas 32 Ko : un gros skin part en morceaux. */
    public static final int PART = 30_000;

    private SkinPackets() {}

    public record Upload(int part, boolean last, byte[] bytes, String name, String source, UUID npc) {
        public static void encode(Upload p, FriendlyByteBuf b) {
            b.writeVarInt(p.part);
            b.writeBoolean(p.last);
            b.writeByteArray(p.bytes);
            b.writeUtf(p.name, 64);
            b.writeUtf(p.source, 256);
            b.writeBoolean(p.npc != null);
            if (p.npc != null) b.writeUUID(p.npc);
        }

        public static Upload decode(FriendlyByteBuf b) {
            return new Upload(b.readVarInt(), b.readBoolean(), b.readByteArray(PART + 16), b.readUtf(64), b.readUtf(256), b.readBoolean() ? b.readUUID() : null);
        }

        public static void handle(Upload p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) SkinService.onUploadPart(player, p.part, p.last, p.bytes, p.name, p.source, p.npc);
        }
    }

    /** Demander la bibliothèque ({@code hash} vide), ou l'image d'un skin. */
    public record Request(String hash) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeUtf(p.hash, 64);
        }

        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readUtf(64));
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (p.hash.isEmpty()) CrusadeNetwork.toPlayer(player, new Library(SkinService.entries()));
            else SkinService.send(player, p.hash);
        }
    }

    public record Library(List<SkinService.Entry> entries) {
        public static void encode(Library p, FriendlyByteBuf b) {
            b.writeVarInt(p.entries.size());
            for (SkinService.Entry e : p.entries) {
                b.writeUtf(e.hash());
                b.writeUtf(e.name());
                b.writeUtf(e.source());
                b.writeBoolean(e.slim());
            }
        }

        public static Library decode(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 1024);
            List<SkinService.Entry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new SkinService.Entry(b.readUtf(), b.readUtf(), b.readUtf(), b.readBoolean()));
            return new Library(out);
        }

        public static void handle(Library p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.onLibrary(p.entries));
        }
    }

    public record Data(String hash, boolean slim, byte[] bytes) {
        public static void encode(Data p, FriendlyByteBuf b) {
            b.writeUtf(p.hash);
            b.writeBoolean(p.slim);
            b.writeByteArray(p.bytes);
        }

        public static Data decode(FriendlyByteBuf b) {
            return new Data(b.readUtf(), b.readBoolean(), b.readByteArray(fr.crusadecraft.core.city.SkinSources.MAX_BYTES));
        }

        public static void handle(Data p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.onData(p.hash, p.slim, p.bytes));
        }
    }

    /** Qui porte quoi ; une empreinte vide : plus de skin. {@code full} : la liste entière, à l'arrivée d'un joueur. */
    public record Assign(boolean full, Map<UUID, String> skins) {
        public static void encode(Assign p, FriendlyByteBuf b) {
            b.writeBoolean(p.full);
            b.writeVarInt(p.skins.size());
            for (Map.Entry<UUID, String> e : p.skins.entrySet()) {
                b.writeUUID(e.getKey());
                b.writeUtf(e.getValue());
            }
        }

        public static Assign decode(FriendlyByteBuf b) {
            boolean full = b.readBoolean();
            int n = b.readVarInt();
            Map<UUID, String> out = new HashMap<>();
            for (int i = 0; i < n; i++) out.put(b.readUUID(), b.readUtf());
            return new Assign(full, out);
        }

        public static void handle(Assign p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.onAssign(p.full, p.skins));
        }
    }
}
