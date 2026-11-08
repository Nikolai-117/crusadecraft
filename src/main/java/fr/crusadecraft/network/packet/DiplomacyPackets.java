package fr.crusadecraft.network.packet;

import fr.crusadecraft.block.ChanceryBlock;
import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.diplomacy.ChanceryView;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.DiplomacyView;
import fr.crusadecraft.core.diplomacy.TitleService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.network.CrusadeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Paquets de la diplomatie : Colombier, lettres, Chancery Table. */
public final class DiplomacyPackets {

    private DiplomacyPackets() {}

    private static final UUID NONE = new UUID(0, 0);

    static void sendDiplomacy(ServerPlayer player, boolean open) {
        if (RealmService.realmOf(player.server, player.getUUID()).isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenDiplomacy(DiplomacyService.view(player), open));
    }

    static void sendChancery(ServerPlayer player, boolean open) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) {
            player.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            return;
        }
        CrusadeNetwork.toPlayer(player, new OpenChancery(TitleService.view(player), open));
    }

    // ------------------------------------------------------------------ serveur → client

    /** {@code open} : ouvrir l'écran du Colombier ; sinon, rafraîchir l'écran ou l'onglet ouvert. */
    public record OpenDiplomacy(DiplomacyView view, boolean open) {
        public static void encode(OpenDiplomacy p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenDiplomacy decode(FriendlyByteBuf buf) {
            return new OpenDiplomacy(DiplomacyView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenDiplomacy p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.diplomacy(p.view, p.open));
        }
    }

    public record OpenChancery(ChanceryView view, boolean open) {
        public static void encode(OpenChancery p, FriendlyByteBuf buf) {
            p.view.write(buf);
            buf.writeBoolean(p.open);
        }

        public static OpenChancery decode(FriendlyByteBuf buf) {
            return new OpenChancery(ChanceryView.read(buf), buf.readBoolean());
        }

        public static void handle(OpenChancery p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.chancery(p.view, p.open));
        }
    }

    // ------------------------------------------------------------------ client → serveur

    /** Une action au Colombier. */
    public record DiploAction(Kind kind, UUID target, DiplomacyRules.Proposal proposal, Treaty treaty, int amount, int tax, int levy) {
        public enum Kind { REFRESH, PROPOSE, GIFT, INSULT, CHANCELLOR, BREAK, TAKE_MAIL }

        public static DiploAction of(Kind kind, UUID target) {
            return new DiploAction(kind, target, null, null, 0, 20, 20);
        }

        public static void encode(DiploAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeUUID(p.target == null ? NONE : p.target);
            buf.writeBoolean(p.proposal != null);
            if (p.proposal != null) buf.writeEnum(p.proposal);
            buf.writeBoolean(p.treaty != null);
            if (p.treaty != null) buf.writeEnum(p.treaty);
            buf.writeVarInt(p.amount);
            buf.writeVarInt(p.tax);
            buf.writeVarInt(p.levy);
        }

        public static DiploAction decode(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            UUID target = buf.readUUID();
            DiplomacyRules.Proposal proposal = buf.readBoolean() ? buf.readEnum(DiplomacyRules.Proposal.class) : null;
            Treaty treaty = buf.readBoolean() ? buf.readEnum(Treaty.class) : null;
            return new DiploAction(kind, target, proposal, treaty, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(DiploAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CouncilActions.Outcome out = switch (p.kind) {
                case PROPOSE -> p.proposal == null ? null : DiplomacyService.propose(player, p.target, p.proposal, p.tax, p.levy);
                case GIFT -> {
                    int amount = 0;
                    for (int g : DiplomacyService.GIFTS) if (g == p.amount) amount = g;
                    yield DiplomacyService.gift(player, p.target, amount);
                }
                case INSULT -> DiplomacyService.insult(player, p.target);
                case CHANCELLOR -> DiplomacyService.sendChancellor(player, p.target);
                case BREAK -> p.treaty == null ? null : DiplomacyService.breakTreaty(player, p.target, p.treaty);
                case TAKE_MAIL -> DiplomacyService.takeMail(player);
                case REFRESH -> null;
            };
            if (out != null) CourtPackets.reply(player, out);
            sendDiplomacy(player, false);
        }
    }

    /** Répondre à une lettre (choix numéro {@code choice}). */
    public record AnswerLetter(UUID letter, int choice) {
        public static void encode(AnswerLetter p, FriendlyByteBuf buf) {
            buf.writeUUID(p.letter);
            buf.writeVarInt(p.choice);
        }

        public static AnswerLetter decode(FriendlyByteBuf buf) {
            return new AnswerLetter(buf.readUUID(), buf.readVarInt());
        }

        public static void handle(AnswerLetter p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            CourtPackets.reply(player, DiplomacyService.answer(player, p.letter, p.choice));
        }
    }

    /** Une action à la Chancery Table. */
    public record ChanceryAction(Kind kind, UUID target, int province, int tax, int levy) {
        public enum Kind { REFRESH, CREATE_TITLE, SET_CONTRACT, RELEASE, CLAIM, GRANT_TITLE }

        public static void encode(ChanceryAction p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeUUID(p.target == null ? NONE : p.target);
            buf.writeInt(p.province);
            buf.writeVarInt(p.tax);
            buf.writeVarInt(p.levy);
        }

        public static ChanceryAction decode(FriendlyByteBuf buf) {
            return new ChanceryAction(buf.readEnum(Kind.class), buf.readUUID(), buf.readInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(ChanceryAction p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
            if (realm.isEmpty()) return;
            Optional<Component> lock = ChanceryBlock.locked(realm.get());
            if (lock.isPresent() && p.kind != Kind.REFRESH && !TitleService.freeHand(player)) {
                player.displayClientMessage(lock.get(), true);
                return;
            }
            CouncilActions.Outcome out = switch (p.kind) {
                case CREATE_TITLE -> TitleService.createTitle(player);
                case SET_CONTRACT -> TitleService.setContract(player, p.target, p.tax, p.levy);
                case RELEASE -> TitleService.release(player, p.target);
                case CLAIM -> TitleService.fabricateClaim(player, p.province);
                // P62 : en créatif, un cran de la frise des titres ; le rang voyage par son rang dans l'énumération.
                case GRANT_TITLE -> TitleService.grant(player, fr.crusadecraft.core.realm.TitleRank.values()[
                        Math.max(0, Math.min(fr.crusadecraft.core.realm.TitleRank.values().length - 1, p.province))]);
                case REFRESH -> null;
            };
            if (out != null) CourtPackets.reply(player, out);
            sendChancery(player, false);
        }
    }
}
