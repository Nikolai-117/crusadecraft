package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Le catalogue du plan d'architecte (P34) : les étagères, leurs plans, et le choix. Tout est revérifié par {@link PlanTool}. */
public final class PlanPackets {
    private static final int REF_MAX = 512;

    private PlanPackets() {}

    /** Ouvrir le catalogue ({@code source} vide : les étagères), ou lister une étagère. */
    public record Request(String source) {
        public static void encode(Request p, FriendlyByteBuf buf) { buf.writeUtf(p.source, REF_MAX); }

        public static Request decode(FriendlyByteBuf buf) { return new Request(buf.readUtf(REF_MAX)); }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PlanTool.allowed(player)) {
                player.displayClientMessage(Component.translatable("crusadecraft.plan.denied"), true);
                return;
            }
            if (p.source.isEmpty()) CrusadeNetwork.toPlayer(player, new Catalog(PlanTool.sources(), PlanLibrary.folderForDisplay()));
            else CrusadeNetwork.toPlayer(player, new Entries(p.source, PlanTool.entries(p.source)));
        }
    }

    /** Les étagères du catalogue, et le dossier où déposer ses fichiers. */
    public record Catalog(List<PlanLibrary.Source> sources, String folder) {
        public static void encode(Catalog p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.sources.size());
            for (PlanLibrary.Source s : p.sources) {
                buf.writeUtf(s.id(), REF_MAX);
                buf.writeUtf(s.label(), REF_MAX);
                buf.writeVarInt(s.count());
            }
            buf.writeUtf(p.folder, 1024);
        }

        public static Catalog decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<PlanLibrary.Source> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new PlanLibrary.Source(buf.readUtf(REF_MAX), buf.readUtf(REF_MAX), buf.readVarInt()));
            return new Catalog(out, buf.readUtf(1024));
        }

        public static void handle(Catalog p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.planCatalog(p.sources, p.folder));
        }
    }

    /** Les plans d'une étagère. */
    public record Entries(String source, List<PlanLibrary.Entry> entries) {
        public static void encode(Entries p, FriendlyByteBuf buf) {
            buf.writeUtf(p.source, REF_MAX);
            buf.writeVarInt(p.entries.size());
            for (PlanLibrary.Entry e : p.entries) {
                buf.writeUtf(e.ref(), REF_MAX);
                buf.writeUtf(e.label(), REF_MAX);
                buf.writeUtf(e.category(), REF_MAX);
            }
        }

        public static Entries decode(FriendlyByteBuf buf) {
            String source = buf.readUtf(REF_MAX);
            int n = buf.readVarInt();
            List<PlanLibrary.Entry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(new PlanLibrary.Entry(buf.readUtf(REF_MAX), buf.readUtf(REF_MAX), buf.readUtf(REF_MAX)));
            return new Entries(source, out);
        }

        public static void handle(Entries p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.planEntries(p.source, p.entries));
        }
    }

    /** Demander ce qu'on sait d'un plan (taille, aperçu vu du ciel), pour le panneau du catalogue. */
    public record DetailRequest(String ref) {
        public static void encode(DetailRequest p, FriendlyByteBuf buf) { buf.writeUtf(p.ref, REF_MAX); }

        public static DetailRequest decode(FriendlyByteBuf buf) { return new DetailRequest(buf.readUtf(REF_MAX)); }

        public static void handle(DetailRequest p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlanTool.detail(player, p.ref).ifPresent(d -> CrusadeNetwork.toPlayer(player, new Detail(d)));
        }
    }

    /** Ce qu'on sait d'un plan. */
    public record Detail(PlanTool.Detail detail) {
        public static void encode(Detail p, FriendlyByteBuf buf) {
            PlanTool.Detail d = p.detail;
            buf.writeUtf(d.ref(), REF_MAX);
            buf.writeUtf(d.name(), REF_MAX);
            buf.writeVarInt(d.sizeX());
            buf.writeVarInt(d.sizeY());
            buf.writeVarInt(d.sizeZ());
            buf.writeVarInt(d.pieces());
            buf.writeVarInt(d.huts());
            buf.writeBoolean(d.walled());
            buf.writeVarInt(d.gridW());
            buf.writeVarInt(d.gridH());
            buf.writeVarIntArray(d.colors());
        }

        public static Detail decode(FriendlyByteBuf buf) {
            return new Detail(new PlanTool.Detail(buf.readUtf(REF_MAX), buf.readUtf(REF_MAX), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(PlanTool.PREVIEW * PlanTool.PREVIEW * 4)));
        }

        public static void handle(Detail p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.planDetail(p.detail));
        }
    }

    /** L'avancement d'une pose de l'outil, pour la barre du HUD. */
    public record Progress(String name, int percent) {
        public static void encode(Progress p, FriendlyByteBuf buf) {
            buf.writeUtf(p.name, REF_MAX);
            buf.writeVarInt(p.percent);
        }

        public static Progress decode(FriendlyByteBuf buf) { return new Progress(buf.readUtf(REF_MAX), buf.readVarInt()); }

        public static void handle(Progress p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.crusadecraft.client.hud.PlanHud.progress(p.name, p.percent));
        }
    }

    /** Choisir le plan que posera l'outil tenu. */
    public record Choose(String ref) {
        public static void encode(Choose p, FriendlyByteBuf buf) { buf.writeUtf(p.ref, REF_MAX); }

        public static Choose decode(FriendlyByteBuf buf) { return new Choose(buf.readUtf(REF_MAX)); }

        public static void handle(Choose p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Outcome out = PlanTool.choose(player, p.ref);
            player.displayClientMessage(out.message(), true);
        }
    }
}
