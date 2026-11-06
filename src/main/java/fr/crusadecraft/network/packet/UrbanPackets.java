package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.screen.UrbanScreen;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.build.plan.UrbanGenerator;
import fr.crusadecraft.core.build.plan.UrbanParams;
import fr.crusadecraft.core.build.plan.UrbanPlan;
import fr.crusadecraft.core.build.plan.UrbanService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** L'atelier des villes (P60) : l'écran demande, le serveur dresse le plan et répond. Il ne croit rien de ce que dit le client : les réglages sont rebornés, le plan refait. */
public final class UrbanPackets {
    public static final int OPEN = 0, PREVIEW = 1, TAKE = 2, SAVE = 3, LOAD = 4, DELETE = 5;
    private static final int TEXT_MAX = 64;

    private UrbanPackets() {}

    /** Ce que l'écran demande. */
    public record Request(int action, CompoundTag params, String text) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeVarInt(p.action);
            b.writeNbt(p.params);
            b.writeUtf(p.text, TEXT_MAX);
        }

        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readVarInt(), b.readNbt(), b.readUtf(TEXT_MAX));
        }

        public static void handle(Request p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!PlanTool.allowed(player)) {
                player.displayClientMessage(Component.translatable("crusadecraft.plan.denied"), true);
                return;
            }
            UrbanParams params = p.params == null ? new UrbanParams() : UrbanParams.load(p.params);
            switch (p.action) {
                case OPEN -> CrusadeNetwork.toPlayer(player, new Saved(UrbanService.savedNames(), true));
                case PREVIEW -> {
                    if (UrbanService.mayPreview(player)) send(player, params);
                }
                case TAKE -> {
                    Outcome out = UrbanService.take(player, params);
                    player.displayClientMessage(out.message(), true);
                    if (out.success()) CrusadeNetwork.toPlayer(player, new Taken());
                }
                case SAVE -> {
                    Outcome out = UrbanService.save(player, p.text, params);
                    player.displayClientMessage(out.message(), true);
                    CrusadeNetwork.toPlayer(player, new Saved(UrbanService.savedNames(), false));
                }
                case LOAD -> UrbanService.loadSaved(p.text).ifPresent(loaded -> CrusadeNetwork.toPlayer(player, new Loaded(loaded.save())));
                case DELETE -> {
                    Outcome out = UrbanService.delete(player, p.text);
                    player.displayClientMessage(out.message(), true);
                    CrusadeNetwork.toPlayer(player, new Saved(UrbanService.savedNames(), false));
                }
                default -> {
                    // Une demande inconnue ne mérite pas de réponse.
                }
            }
        }

        private static void send(ServerPlayer player, UrbanParams params) {
            Optional<UrbanPlan> plan = UrbanService.plan(params);
            if (plan.isEmpty()) {
                CrusadeNetwork.toPlayer(player, Preview.none());
                return;
            }
            CrusadeNetwork.toPlayer(player, Preview.of(plan.get()));
        }
    }

    /** Ce que l'écran dessine. */
    public record Preview(boolean ok, int half, byte[] view, byte[] lotGrid, int[] stats, int[] buildings, List<String> names, int[] lots) {
        static Preview none() {
            return new Preview(false, 0, new byte[0], new byte[0], new int[0], new int[0], List.of(), new int[0]);
        }

        static Preview of(UrbanPlan plan) {
            UrbanPlan.Stats s = plan.stats();
            int[] stats = {s.people(), s.houses(), s.housesWanted(), s.buildings(), s.lots(), s.gates(), s.towers(), s.bridges(), s.noRoom(), s.size(), s.noModel()};
            List<UrbanGenerator.Placed> placed = plan.placed();
            int[] buildings = new int[placed.size() * 7];
            List<String> names = new ArrayList<>(placed.size());
            int i = 0;
            for (UrbanGenerator.Placed b : placed) {
                buildings[i++] = b.slot();
                buildings[i++] = b.rect().x0();
                buildings[i++] = b.rect().z0();
                buildings[i++] = b.rect().x1();
                buildings[i++] = b.rect().z1();
                buildings[i++] = b.role().ordinal();
                buildings[i++] = b.lot();
                names.add(label(b.pick().ref()));
            }
            List<UrbanGenerator.Lot> lots = plan.lots();
            int[] lotData = new int[lots.size() * 5];
            int j = 0;
            for (UrbanGenerator.Lot l : lots) {
                lotData[j++] = l.id;
                lotData[j++] = l.ward.ordinal();
                lotData[j++] = l.area;
                lotData[j++] = l.houses;
                lotData[j++] = l.citadel ? 1 : 0;
            }
            byte[] grid = new byte[plan.lotGrid().length * 2];
            for (int k = 0; k < plan.lotGrid().length; k++) {
                grid[k * 2] = (byte) (plan.lotGrid()[k] >> 8);
                grid[k * 2 + 1] = (byte) plan.lotGrid()[k];
            }
            return new Preview(true, plan.half(), deflate(plan.view()), deflate(grid), stats, buildings, names, lotData);
        }

        private static String label(String ref) {
            int slash = Math.max(ref.lastIndexOf('/'), ref.lastIndexOf('|'));
            String s = slash >= 0 ? ref.substring(slash + 1) : ref;
            int dot = s.indexOf('.');
            return dot > 0 ? s.substring(0, dot) : s;
        }

        public static void encode(Preview p, FriendlyByteBuf b) {
            b.writeBoolean(p.ok);
            b.writeVarInt(p.half);
            b.writeByteArray(p.view);
            b.writeByteArray(p.lotGrid);
            b.writeVarIntArray(p.stats);
            b.writeVarIntArray(p.buildings);
            b.writeCollection(p.names, (buf, s) -> buf.writeUtf(s, 96));
            b.writeVarIntArray(p.lots);
        }

        public static Preview decode(FriendlyByteBuf b) {
            return new Preview(b.readBoolean(), b.readVarInt(), b.readByteArray(), b.readByteArray(), b.readVarIntArray(), b.readVarIntArray(), b.readList(buf -> buf.readUtf(96)), b.readVarIntArray());
        }

        public static void handle(Preview p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UrbanScreen.onPreview(p));
        }
    }

    /** La grille compressée : presque tout y est répété, elle tient alors dans un paquet, même pour une capitale. */
    private static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    public static byte[] inflate(byte[] packed, int size) {
        byte[] raw = new byte[size];
        Inflater i = new Inflater();
        i.setInput(packed);
        try {
            int at = 0;
            while (!i.finished() && at < size) {
                int n = i.inflate(raw, at, size - at);
                if (n == 0 && i.needsInput()) break;
                at += n;
            }
        } catch (DataFormatException e) {
            return new byte[0];
        } finally {
            i.end();
        }
        return raw;
    }

    /** Les réglages enregistrés ; à l'ouverture, l'écran s'ouvre avec. */
    public record Saved(List<String> names, boolean open) {
        public static void encode(Saved p, FriendlyByteBuf b) {
            b.writeCollection(p.names, (buf, s) -> buf.writeUtf(s, TEXT_MAX));
            b.writeBoolean(p.open);
        }

        public static Saved decode(FriendlyByteBuf b) {
            return new Saved(b.readList(buf -> buf.readUtf(TEXT_MAX)), b.readBoolean());
        }

        public static void handle(Saved p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UrbanScreen.onSaved(p.names, p.open));
        }
    }

    /** Un réglage enregistré, qu'on rouvre. */
    public record Loaded(CompoundTag params) {
        public static void encode(Loaded p, FriendlyByteBuf b) {
            b.writeNbt(p.params);
        }

        public static Loaded decode(FriendlyByteBuf b) {
            return new Loaded(b.readNbt());
        }

        public static void handle(Loaded p, Supplier<NetworkEvent.Context> ctx) {
            if (p.params != null) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UrbanScreen.onLoaded(p.params));
        }
    }

    /** Le plan est dans la main : on peut fermer l'écran et aller le poser. */
    public record Taken() {
        public static void encode(Taken p, FriendlyByteBuf b) {
            // Rien à dire de plus.
        }

        public static Taken decode(FriendlyByteBuf b) {
            return new Taken();
        }

        public static void handle(Taken p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> UrbanScreen::onTaken);
        }
    }
}
