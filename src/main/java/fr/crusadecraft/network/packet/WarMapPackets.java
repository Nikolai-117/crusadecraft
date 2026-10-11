package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.war.WarMap;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** La carte de guerre (P18) : les gestes de l'écran, revérifiés par {@link WarMapService}, puis la carte renvoyée. */
public final class WarMapPackets {
    private WarMapPackets() {}

    /**
     * Un geste sur la carte de guerre. {@code kind} : le rang de la zone (−1 : gomme) ou du repère ; {@code cells} : les
     * chunks peints ; {@code x0..z1} : la flèche, ou le repère en (x0, z0) ; {@code target} : ce qu'on retire.
     */
    public record Edit(Op op, int kind, long[] cells, int x0, int z0, int x1, int z1, String note, UUID target) {
        public enum Op { PAINT, ARROW, PIN, REMOVE, CLEAR }

        public static Edit paint(int kind, long[] cells) {
            return new Edit(Op.PAINT, kind, cells, 0, 0, 0, 0, "", null);
        }

        public static Edit arrow(int x0, int z0, int x1, int z1) {
            return new Edit(Op.ARROW, 0, new long[0], x0, z0, x1, z1, "", null);
        }

        public static Edit pin(int kind, int x, int z, String note) {
            return new Edit(Op.PIN, kind, new long[0], x, z, 0, 0, note == null ? "" : note, null);
        }

        public static Edit remove(UUID target) {
            return new Edit(Op.REMOVE, 0, new long[0], 0, 0, 0, 0, "", target);
        }

        public static Edit clear() {
            return new Edit(Op.CLEAR, 0, new long[0], 0, 0, 0, 0, "", null);
        }

        public static void encode(Edit p, FriendlyByteBuf buf) {
            buf.writeEnum(p.op);
            buf.writeVarInt(p.kind + 1);
            buf.writeLongArray(p.cells);
            buf.writeInt(p.x0);
            buf.writeInt(p.z0);
            buf.writeInt(p.x1);
            buf.writeInt(p.z1);
            buf.writeUtf(p.note, WarMap.MAX_NOTE * 4);
            buf.writeBoolean(p.target != null);
            if (p.target != null) buf.writeUUID(p.target);
        }

        public static Edit decode(FriendlyByteBuf buf) {
            return new Edit(buf.readEnum(Op.class), buf.readVarInt() - 1, buf.readLongArray(null, WarMapService.MAX_CELLS), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readUtf(WarMap.MAX_NOTE * 4), buf.readBoolean() ? buf.readUUID() : null);
        }

        public static void handle(Edit p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            WarMap.Zone[] zones = WarMap.Zone.values();
            WarMap.Mark[] marks = WarMap.Mark.values();
            Outcome out = switch (p.op) {
                case PAINT -> WarMapService.paint(player, p.kind < 0 || p.kind >= zones.length ? null : zones[p.kind], p.cells);
                case ARROW -> WarMapService.arrow(player, p.x0, p.z0, p.x1, p.z1);
                case PIN -> WarMapService.pin(player, p.x0, p.z0, marks[Math.floorMod(p.kind, marks.length)], p.note);
                case REMOVE -> p.target == null ? Outcome.no("crusadecraft.warmap.nothing") : WarMapService.remove(player, p.target);
                case CLEAR -> WarMapService.clear(player);
            };
            CrusadeNetwork.toPlayer(player, new MapEditPackets.Result(out.success(), out.message()));
            CrusadeNetwork.toPlayer(player, new WorldPackets.MapData(WorldService.mapView(player, false), false));
        }
    }
}
