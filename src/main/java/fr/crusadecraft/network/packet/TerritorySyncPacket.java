package fr.crusadecraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Les terres d'une dimension, chunk par chunk, pour les cartes du client (P17 : Xaero) : les royaumes que le joueur
 * connaît, et leurs chunks en rectangles (bien plus courts qu'une liste de chunks).
 */
public record TerritorySyncPacket(ResourceLocation dimension, List<Owner> owners, int[] rects) {

    /** Un royaume : son identifiant, sa couleur, son nom. */
    public record Owner(UUID id, int color, Component name) {}

    /** {@code rects} : par rectangle, cinq entiers — indice du royaume, x0, z0, x1, z1 (en chunks, bornes incluses). */
    public static void encode(TerritorySyncPacket p, FriendlyByteBuf buf) {
        buf.writeResourceLocation(p.dimension);
        buf.writeVarInt(p.owners.size());
        for (Owner o : p.owners) {
            buf.writeUUID(o.id());
            buf.writeInt(o.color());
            buf.writeComponent(o.name());
        }
        buf.writeVarInt(p.rects.length / 5);
        for (int i = 0; i < p.rects.length; i += 5) {
            buf.writeVarInt(p.rects[i]);
            buf.writeInt(p.rects[i + 1]);
            buf.writeInt(p.rects[i + 2]);
            buf.writeVarInt(p.rects[i + 3] - p.rects[i + 1]);
            buf.writeVarInt(p.rects[i + 4] - p.rects[i + 2]);
        }
    }

    public static TerritorySyncPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        int n = buf.readVarInt();
        List<Owner> owners = new ArrayList<>(n);
        for (int i = 0; i < n; i++) owners.add(new Owner(buf.readUUID(), buf.readInt(), buf.readComponent()));
        int r = buf.readVarInt();
        int[] rects = new int[r * 5];
        for (int i = 0; i < rects.length; i += 5) {
            rects[i] = buf.readVarInt();
            rects[i + 1] = buf.readInt();
            rects[i + 2] = buf.readInt();
            rects[i + 3] = rects[i + 1] + buf.readVarInt();
            rects[i + 4] = rects[i + 2] + buf.readVarInt();
        }
        return new TerritorySyncPacket(dim, owners, rects);
    }

    public static void handle(TerritorySyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.crusadecraft.client.map.ClientTerritory.set(p));
    }
}
