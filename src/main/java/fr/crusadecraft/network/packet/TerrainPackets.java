package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.world.terrain.TerrainService;
import fr.crusadecraft.core.world.terrain.TerrainTile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** La carte en terrain (P18b) : le client demande les tuiles qu'il voit, le serveur les envoie quand elles sont prêtes. */
public final class TerrainPackets {
    private TerrainPackets() {}

    /** Les tuiles d'un niveau de détail que la carte ouverte affiche. */
    public record Request(int lod, long[] keys) {
        public static void encode(Request p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.lod);
            buf.writeLongArray(p.keys);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readLongArray(null, TerrainService.MAX_REQUEST));
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && p.lod >= 0 && p.lod < TerrainTile.LODS) TerrainService.request(player, p.lod, p.keys);
        }
    }

    /** Une tuile prête, pour une dimension. */
    public record Tile(ResourceLocation dimension, TerrainTile tile) {
        public static void encode(Tile p, FriendlyByteBuf buf) {
            buf.writeResourceLocation(p.dimension);
            TerrainTile t = p.tile;
            buf.writeByte(t.lod());
            buf.writeVarInt(t.tx());
            buf.writeVarInt(t.tz());
            for (int i = 0; i < TerrainTile.SIZE * TerrainTile.SIZE; i++) {
                // L'opacité ne voyage pas : 0 reste « inconnu », le reste redevient opaque à l'arrivée (un noir pur
                // devient presque noir, pour ne pas passer pour inconnu).
                int c = t.colors()[i];
                buf.writeMedium(c == 0 ? 0 : Math.max(1, c & 0xFFFFFF));
                buf.writeShort(t.heights()[i]);
            }
        }

        public static Tile decode(FriendlyByteBuf buf) {
            ResourceLocation dim = buf.readResourceLocation();
            int lod = buf.readByte(), tx = buf.readVarInt(), tz = buf.readVarInt();
            int[] colors = new int[TerrainTile.SIZE * TerrainTile.SIZE];
            short[] heights = new short[TerrainTile.SIZE * TerrainTile.SIZE];
            for (int i = 0; i < colors.length; i++) {
                int c = buf.readUnsignedMedium();
                colors[i] = c == 0 ? 0 : 0xFF000000 | c;
                heights[i] = buf.readShort();
            }
            return new Tile(dim, new TerrainTile(lod, tx, tz, colors, heights));
        }

        public static void handle(Tile p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.terrainTile(p.dimension, p.tile));
        }
    }
}
