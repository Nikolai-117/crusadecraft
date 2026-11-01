package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Les demandes de l'éditeur de la carte (P12). Chacune est revérifiée par {@link WorldEditService}, qui répond par
 * un message et une carte à jour.
 */
public final class MapEditPackets {
    private static final int TEXT_MAX = 64;

    private MapEditPackets() {}

    private static void run(Supplier<NetworkEvent.Context> ctx, Function<ServerPlayer, Outcome> action) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        Outcome out = action.apply(player);
        CrusadeNetwork.toPlayer(player, new Result(out.success(), out.message()));
        CrusadeNetwork.toPlayer(player, new WorldPackets.MapData(WorldService.mapView(player, true), false));
    }

    static TitleRank toRank(int i) {
        TitleRank[] all = TitleRank.values();
        return all[Math.floorMod(i, all.length)];
    }

    static AiFaith toFaith(int i) {
        AiFaith[] all = AiFaith.values();
        return all[Math.floorMod(i, all.length)];
    }

    static AiPersonality toPersonality(int i) {
        AiPersonality[] all = AiPersonality.values();
        return all[Math.floorMod(i, all.length)];
    }

    // ------------------------------------------------------------------ client → serveur

    public record CreateRealm(String name, String ruler, int rank, int color, int faith, int personality, int x, int z) {
        public static void encode(CreateRealm p, FriendlyByteBuf buf) {
            buf.writeUtf(p.name, TEXT_MAX);
            buf.writeUtf(p.ruler, TEXT_MAX);
            buf.writeVarInt(p.rank);
            buf.writeVarInt(p.color);
            buf.writeVarInt(p.faith);
            buf.writeVarInt(p.personality);
            buf.writeInt(p.x);
            buf.writeInt(p.z);
        }

        public static CreateRealm decode(FriendlyByteBuf buf) {
            return new CreateRealm(buf.readUtf(TEXT_MAX), buf.readUtf(TEXT_MAX), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(CreateRealm p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.createRealm(pl, p.name, p.ruler, toRank(p.rank), p.color, toFaith(p.faith),
                    toPersonality(p.personality), p.x, p.z));
        }
    }

    public record EditRealm(UUID realm, String name, int rank, int color, int faith, int personality) {
        public static void encode(EditRealm p, FriendlyByteBuf buf) {
            buf.writeUUID(p.realm);
            buf.writeUtf(p.name, TEXT_MAX);
            buf.writeVarInt(p.rank);
            buf.writeVarInt(p.color);
            buf.writeVarInt(p.faith);
            buf.writeVarInt(p.personality);
        }

        public static EditRealm decode(FriendlyByteBuf buf) {
            return new EditRealm(buf.readUUID(), buf.readUtf(TEXT_MAX), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(EditRealm p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.editRealm(pl, p.realm, p.name, toRank(p.rank), p.color, toFaith(p.faith), toPersonality(p.personality)));
        }
    }

    public record DeleteRealm(UUID realm) {
        public static void encode(DeleteRealm p, FriendlyByteBuf buf) { buf.writeUUID(p.realm); }
        public static DeleteRealm decode(FriendlyByteBuf buf) { return new DeleteRealm(buf.readUUID()); }

        public static void handle(DeleteRealm p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.deleteRealm(pl, p.realm));
        }
    }

    /** P34 : le style d'un royaume de l'ordinateur (les packs de ses villes bâties). */
    public record SetStyle(UUID realm, int style) {
        public static void encode(SetStyle p, FriendlyByteBuf buf) {
            buf.writeUUID(p.realm);
            buf.writeVarInt(p.style);
        }

        public static SetStyle decode(FriendlyByteBuf buf) { return new SetStyle(buf.readUUID(), buf.readVarInt()); }

        public static void handle(SetStyle p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.setStyle(pl, p.realm, fr.crusadecraft.core.build.plan.NationStyle.byOrdinal(p.style)));
        }
    }

    /**
     * P34 : bâtir une ville de la carte d'après les plans de son style ; défaire la ville bâtie ; P35 : en faire sa
     * colonie, ou y fonder son royaume.
     */
    public record TownAction(int province, UUID site, int op) {
        public static final int BUILD = 0, UNDO = 1, COLONY = 2, REALM = 3;

        public static void encode(TownAction p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.province);
            buf.writeBoolean(p.site != null);
            if (p.site != null) buf.writeUUID(p.site);
            buf.writeVarInt(p.op);
        }

        public static TownAction decode(FriendlyByteBuf buf) {
            int province = buf.readVarInt();
            UUID site = buf.readBoolean() ? buf.readUUID() : null;
            return new TownAction(province, site, buf.readVarInt());
        }

        public static void handle(TownAction p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> switch (p.op) {
                case UNDO -> fr.crusadecraft.core.build.SiteService.undoTown(pl, p.site);
                case COLONY -> fr.crusadecraft.core.build.plan.TownColony.makeColony(pl, p.site, false);
                case REALM -> fr.crusadecraft.core.build.plan.TownColony.makeColony(pl, p.site, true);
                default -> fr.crusadecraft.core.build.SiteService.buildTown(pl, p.province);
            });
        }
    }

    /** P33 : un royaume de l'ordinateur passe sous un autre (fusion). */
    public record MergeRealm(UUID from, UUID into) {
        public static void encode(MergeRealm p, FriendlyByteBuf buf) {
            buf.writeUUID(p.from);
            buf.writeUUID(p.into);
        }

        public static MergeRealm decode(FriendlyByteBuf buf) { return new MergeRealm(buf.readUUID(), buf.readUUID()); }

        public static void handle(MergeRealm p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> fr.crusadecraft.core.world.ZoneService.merge(pl, p.from, p.into));
        }
    }

    public record CreateCity(UUID owner, String name, int size, int x, int z) {
        public static void encode(CreateCity p, FriendlyByteBuf buf) {
            buf.writeUUID(p.owner);
            buf.writeUtf(p.name, TEXT_MAX);
            buf.writeVarInt(p.size);
            buf.writeInt(p.x);
            buf.writeInt(p.z);
        }

        public static CreateCity decode(FriendlyByteBuf buf) {
            return new CreateCity(buf.readUUID(), buf.readUtf(TEXT_MAX), buf.readVarInt(), buf.readInt(), buf.readInt());
        }

        public static void handle(CreateCity p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.createCity(pl, p.owner, p.name, WorldEditService.CitySize.byOrdinal(p.size), p.x, p.z));
        }
    }

    /** Une retouche sur une ville déjà posée. */
    public record CityAction(int province, Op op, String name, int x, int z, UUID holder) {
        public enum Op { RENAME, MOVE, GIVE, DELETE }

        public static CityAction rename(int province, String name) { return new CityAction(province, Op.RENAME, name, 0, 0, null); }
        public static CityAction move(int province, int x, int z) { return new CityAction(province, Op.MOVE, "", x, z, null); }
        public static CityAction give(int province, UUID holder) { return new CityAction(province, Op.GIVE, "", 0, 0, holder); }
        public static CityAction delete(int province) { return new CityAction(province, Op.DELETE, "", 0, 0, null); }

        public static void encode(CityAction p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.province);
            buf.writeEnum(p.op);
            buf.writeUtf(p.name, TEXT_MAX);
            buf.writeInt(p.x);
            buf.writeInt(p.z);
            buf.writeBoolean(p.holder != null);
            if (p.holder != null) buf.writeUUID(p.holder);
        }

        public static CityAction decode(FriendlyByteBuf buf) {
            return new CityAction(buf.readVarInt(), buf.readEnum(Op.class), buf.readUtf(TEXT_MAX), buf.readInt(), buf.readInt(),
                    buf.readBoolean() ? buf.readUUID() : null);
        }

        public static void handle(CityAction p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> switch (p.op) {
                case RENAME -> WorldEditService.renameCity(pl, p.province, p.name);
                case MOVE -> WorldEditService.moveCity(pl, p.province, p.x, p.z);
                case GIVE -> p.holder == null ? Outcome.no("crusadecraft.mapedit.realm.unknown") : WorldEditService.giveCity(pl, p.province, p.holder);
                case DELETE -> WorldEditService.deleteCity(pl, p.province);
            });
        }
    }

    /** Un coup de pinceau : pour un royaume, en terre libre ({@code neutral}), ou à la gomme (ni l'un ni l'autre). */
    public record Paint(UUID owner, boolean neutral, long[] cells) {
        public static void encode(Paint p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.owner != null);
            if (p.owner != null) buf.writeUUID(p.owner);
            buf.writeBoolean(p.neutral);
            buf.writeLongArray(p.cells);
        }

        public static Paint decode(FriendlyByteBuf buf) {
            UUID owner = buf.readBoolean() ? buf.readUUID() : null;
            boolean neutral = buf.readBoolean();
            return new Paint(owner, neutral, buf.readLongArray(null, WorldEditService.MAX_CELLS));
        }

        public static void handle(Paint p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> WorldEditService.paint(pl, p.owner, p.neutral, p.cells));
        }
    }

    /** Une construction (P14) : la donner à un autre royaume, ou l'effacer du registre ({@code delete}). */
    public record SiteAction(UUID site, boolean delete, UUID holder) {
        public static void encode(SiteAction p, FriendlyByteBuf buf) {
            buf.writeUUID(p.site);
            buf.writeBoolean(p.delete);
            buf.writeBoolean(p.holder != null);
            if (p.holder != null) buf.writeUUID(p.holder);
        }

        public static SiteAction decode(FriendlyByteBuf buf) {
            return new SiteAction(buf.readUUID(), buf.readBoolean(), buf.readBoolean() ? buf.readUUID() : null);
        }

        public static void handle(SiteAction p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, pl -> p.delete ? fr.crusadecraft.core.build.SiteService.remove(pl, p.site)
                    : p.holder == null ? Outcome.no("crusadecraft.mapedit.realm.unknown") : fr.crusadecraft.core.build.SiteService.give(pl, p.site, p.holder));
        }
    }

    /** « Monde vierge » : les royaumes générés disparaissent, ceux posés à la main restent. */
    public record Wipe() {
        public static void encode(Wipe p, FriendlyByteBuf buf) {}
        public static Wipe decode(FriendlyByteBuf buf) { return new Wipe(); }

        public static void handle(Wipe p, Supplier<NetworkEvent.Context> ctx) {
            run(ctx, WorldEditService::wipe);
        }
    }

    // ------------------------------------------------------------------ serveur → client

    /** La réponse du serveur, affichée au pied de la carte. */
    public record Result(boolean success, Component message) {
        public static void encode(Result p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.success);
            buf.writeComponent(p.message);
        }

        public static Result decode(FriendlyByteBuf buf) { return new Result(buf.readBoolean(), buf.readComponent()); }

        public static void handle(Result p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.mapEditResult(p.success, p.message));
        }
    }
}
