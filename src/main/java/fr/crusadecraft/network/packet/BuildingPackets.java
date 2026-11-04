package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.city.CityClient;
import fr.crusadecraft.core.city.CityStaff;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Le bâton d'intendant (P38) : les bâtiments d'une ville, leur fonction, et les cadres à dessiner. Tout est revérifié par {@link CityStaff}. */
public final class BuildingPackets {
    private static final int NAME_MAX = 64, LIST_MAX = 512;

    private BuildingPackets() {}

    /** Un bâtiment tel que l'écran le montre : sa boîte, sa fonction (−1 : aucune), la proposition, ses gens. */
    public record View(UUID id, BlockPos min, BlockPos max, BlockPos spot, int function, int suggestion, String name, int floor, int levels,
                       Map<String, Integer> furniture, List<String> people) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBlockPos(min);
            buf.writeBlockPos(max);
            buf.writeBlockPos(spot);
            buf.writeVarInt(function + 1);
            buf.writeVarInt(suggestion + 1);
            buf.writeUtf(name, NAME_MAX);
            buf.writeVarInt(floor);
            buf.writeVarInt(levels);
            buf.writeVarInt(furniture.size());
            for (Map.Entry<String, Integer> e : furniture.entrySet()) {
                buf.writeUtf(e.getKey(), NAME_MAX);
                buf.writeVarInt(e.getValue());
            }
            buf.writeVarInt(people.size());
            for (String p : people) buf.writeUtf(p, NAME_MAX * 2);
        }

        static View read(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            BlockPos min = buf.readBlockPos(), max = buf.readBlockPos(), spot = buf.readBlockPos();
            int function = buf.readVarInt() - 1, suggestion = buf.readVarInt() - 1;
            String name = buf.readUtf(NAME_MAX);
            int floor = buf.readVarInt(), levels = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 64);
            Map<String, Integer> furniture = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) furniture.put(buf.readUtf(NAME_MAX), buf.readVarInt());
            int m = Math.min(buf.readVarInt(), LIST_MAX);
            List<String> people = new ArrayList<>(m);
            for (int i = 0; i < m; i++) people.add(buf.readUtf(NAME_MAX * 2));
            return new View(id, min, max, spot, function, suggestion, name, floor, levels, furniture, people);
        }
    }

    /** Une ligne de l'écran de la ville. */
    public record Row(UUID id, int function, int suggestion, String name, int occupants, BlockPos spot) {}

    /** Un cadre à dessiner autour d'un bâtiment. */
    public record Outline(BlockPos min, BlockPos max, int function, int suggestion, String name, int occupants) {}

    /** Ouvrir l'écran d'un bâtiment ({@code draft} : reconnu à l'instant, pas encore dans la ville). */
    public record OpenBuilding(UUID site, String city, View view, boolean draft) {
        public static void encode(OpenBuilding p, FriendlyByteBuf buf) {
            buf.writeUUID(p.site);
            buf.writeUtf(p.city, NAME_MAX);
            p.view.write(buf);
            buf.writeBoolean(p.draft);
        }

        public static OpenBuilding decode(FriendlyByteBuf buf) {
            return new OpenBuilding(buf.readUUID(), buf.readUtf(NAME_MAX), View.read(buf), buf.readBoolean());
        }

        public static void handle(OpenBuilding p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClient.openBuilding(p.site, p.city, p.view, p.draft));
        }
    }

    /** Donner une fonction ({@code function} −1 : aucune) et un nom, ou retirer le bâtiment de la ville. */
    public record Decide(UUID site, UUID building, int function, String name, boolean forget) {
        public static void encode(Decide p, FriendlyByteBuf buf) {
            buf.writeUUID(p.site);
            buf.writeUUID(p.building);
            buf.writeVarInt(p.function + 1);
            buf.writeUtf(p.name, NAME_MAX);
            buf.writeBoolean(p.forget);
        }

        public static Decide decode(FriendlyByteBuf buf) {
            return new Decide(buf.readUUID(), buf.readUUID(), buf.readVarInt() - 1, buf.readUtf(NAME_MAX), buf.readBoolean());
        }

        public static void handle(Decide p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityStaff.decide(player, p.site, p.building, p.function, p.name, p.forget);
        }
    }

    /** L'écran de la ville : ses bâtiments, ses gens. */
    public record OpenCity(UUID site, String city, int people, int max, boolean canEdit, List<Row> rows, UUID nation) {
        public static void encode(OpenCity p, FriendlyByteBuf buf) {
            buf.writeUUID(p.site);
            buf.writeBoolean(p.nation != null);
            if (p.nation != null) buf.writeUUID(p.nation);
            buf.writeUtf(p.city, NAME_MAX);
            buf.writeVarInt(p.people);
            buf.writeVarInt(p.max);
            buf.writeBoolean(p.canEdit);
            buf.writeVarInt(p.rows.size());
            for (Row r : p.rows) {
                buf.writeUUID(r.id());
                buf.writeVarInt(r.function() + 1);
                buf.writeVarInt(r.suggestion() + 1);
                buf.writeUtf(r.name(), NAME_MAX);
                buf.writeVarInt(r.occupants());
                buf.writeBlockPos(r.spot());
            }
        }

        public static OpenCity decode(FriendlyByteBuf buf) {
            UUID site = buf.readUUID();
            UUID nation = buf.readBoolean() ? buf.readUUID() : null;
            String city = buf.readUtf(NAME_MAX);
            int people = buf.readVarInt(), max = buf.readVarInt();
            boolean canEdit = buf.readBoolean();
            int n = Math.min(buf.readVarInt(), LIST_MAX);
            List<Row> rows = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new Row(buf.readUUID(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUtf(NAME_MAX), buf.readVarInt(), buf.readBlockPos()));
            }
            return new OpenCity(site, city, people, max, canEdit, rows, nation);
        }

        public static void handle(OpenCity p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClient.openCity(p));
        }
    }

    /** Un geste de l'écran de la ville : le rouvrir, reconnaître les bâtiments, accepter les propositions. */
    public record CityAction(UUID site, int op) {
        public static void encode(CityAction p, FriendlyByteBuf buf) {
            buf.writeUUID(p.site);
            buf.writeVarInt(p.op);
        }

        public static CityAction decode(FriendlyByteBuf buf) {
            return new CityAction(buf.readUUID(), buf.readVarInt());
        }

        public static void handle(CityAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityStaff.cityAction(player, p.site, p.op);
        }
    }

    /** P39 : hors d'une ville, l'écran pour en fonder une là (son nom ; l'emprise, autour du joueur). */
    public record OpenFound(int radius) {
        public static void encode(OpenFound p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.radius);
        }

        public static OpenFound decode(FriendlyByteBuf buf) {
            return new OpenFound(buf.readVarInt());
        }

        public static void handle(OpenFound p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClient.openFound(p.radius));
        }
    }

    /** Fonder une ville là où l'on est. */
    public record Found(String name, int radius) {
        public static void encode(Found p, FriendlyByteBuf buf) {
            buf.writeUtf(p.name, NAME_MAX);
            buf.writeVarInt(p.radius);
        }

        public static Found decode(FriendlyByteBuf buf) {
            return new Found(buf.readUtf(NAME_MAX), buf.readVarInt());
        }

        public static void handle(Found p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) CityStaff.found(player, p.name, p.radius);
        }
    }

    /** Le bâton en main : les cadres de la ville où l'on est. */
    public record OutlinesRequest() {
        public static void encode(OutlinesRequest p, FriendlyByteBuf buf) {}

        public static OutlinesRequest decode(FriendlyByteBuf buf) {
            return new OutlinesRequest();
        }

        public static void handle(OutlinesRequest p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getMainHandItem().getItem() instanceof fr.crusadecraft.item.IntendantStaffItem
                    || player != null && player.getOffhandItem().getItem() instanceof fr.crusadecraft.item.IntendantStaffItem) {
                CityStaff.sendOutlines(player);
            }
        }
    }

    /** Les cadres des bâtiments de la ville où l'on est, et le brouillon en cours. */
    public record Outlines(String city, int people, List<Outline> outlines, BlockPos draftMin, BlockPos draftMax) {
        public static void encode(Outlines p, FriendlyByteBuf buf) {
            buf.writeUtf(p.city, NAME_MAX);
            buf.writeVarInt(p.people);
            buf.writeVarInt(p.outlines.size());
            for (Outline o : p.outlines) {
                buf.writeBlockPos(o.min());
                buf.writeBlockPos(o.max());
                buf.writeVarInt(o.function() + 1);
                buf.writeVarInt(o.suggestion() + 1);
                buf.writeUtf(o.name(), NAME_MAX);
                buf.writeVarInt(o.occupants());
            }
            buf.writeBoolean(p.draftMin != null);
            if (p.draftMin != null) {
                buf.writeBlockPos(p.draftMin);
                buf.writeBlockPos(p.draftMax);
            }
        }

        public static Outlines decode(FriendlyByteBuf buf) {
            String city = buf.readUtf(NAME_MAX);
            int people = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), LIST_MAX);
            List<Outline> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new Outline(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readUtf(NAME_MAX), buf.readVarInt()));
            }
            BlockPos a = null, b = null;
            if (buf.readBoolean()) {
                a = buf.readBlockPos();
                b = buf.readBlockPos();
            }
            return new Outlines(city, people, out, a, b);
        }

        public static void handle(Outlines p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CityClient.outlines(p));
        }
    }
}
