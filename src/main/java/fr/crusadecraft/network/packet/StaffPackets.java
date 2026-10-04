package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.TerritoryService;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.BornageStaffItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Les demandes du menu du bâton de bornage (P14) ; le serveur revérifie tout et modifie le bâton tenu. */
public final class StaffPackets {

    private StaffPackets() {}

    public record Action(Op op, UUID owner, String special, int a, boolean b) {
        public enum Op { SET_OWNER, SET_SPECIAL, SET_MODE, VALIDATE, CLEAR, UNDO, SET_BUILD, SET_LIEU }

        public static Action of(Op op) {
            return new Action(op, null, "", 0, false);
        }

        public static void encode(Action p, FriendlyByteBuf buf) {
            buf.writeEnum(p.op);
            buf.writeBoolean(p.owner != null);
            if (p.owner != null) buf.writeUUID(p.owner);
            buf.writeUtf(p.special, 16);
            buf.writeVarInt(p.a);
            buf.writeBoolean(p.b);
        }

        public static Action decode(FriendlyByteBuf buf) {
            Op op = buf.readEnum(Op.class);
            UUID owner = buf.readBoolean() ? buf.readUUID() : null;
            return new Action(op, owner, buf.readUtf(16), buf.readVarInt(), buf.readBoolean());
        }

        public static void handle(Action p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Outcome out = run(player, p);
            if (out != null) player.displayClientMessage(out.message(), true);
        }
    }

    private static Outcome run(ServerPlayer player, Action p) {
        if (p.op == Action.Op.UNDO) return TerritoryService.undo(player);
        Optional<ItemStack> held = BornageStaffItem.held(player);
        if (held.isEmpty()) return Outcome.no("crusadecraft.bornage.not_held");
        ItemStack staff = held.get();
        boolean editor = WorldEditService.canEdit(player);
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        switch (p.op) {
            case SET_OWNER -> {
                if (p.owner == null) return Outcome.no("crusadecraft.mapedit.realm.unknown");
                Optional<AiRealm> ai = data.world().realm(p.owner);
                var human = data.realm(p.owner);
                if (ai.isEmpty() && human.isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.unknown");
                // En survie, on ne trace que pour son propre royaume.
                if (!editor && RealmService.realmOf(player.server, player.getUUID()).map(r -> !r.id().equals(p.owner)).orElse(true)) {
                    return Outcome.no("crusadecraft.bornage.only_yours");
                }
                int color = ai.map(AiRealm::color).orElseGet(() -> human.get().color());
                Component name = DiplomacyService.name(player.server, p.owner);
                BornageStaffItem.setOwner(staff, p.owner, color, name.getString());
                return Outcome.ok(Component.translatable("crusadecraft.bornage.owner_set", name));
            }
            case SET_SPECIAL -> {
                boolean neutral = BornageStaffItem.NEUTRAL.equals(p.special);
                if (!neutral && !BornageStaffItem.ERASE.equals(p.special)) return null;
                Component name = Component.translatable(neutral ? "crusadecraft.mapedit.brush.neutral" : "crusadecraft.mapedit.brush.erase");
                BornageStaffItem.setSpecial(staff, p.special, name.getString());
                return Outcome.ok(Component.translatable("crusadecraft.bornage.owner_set", name));
            }
            case SET_MODE -> {
                BornageStaffItem.Mode[] all = BornageStaffItem.Mode.values();
                BornageStaffItem.Mode mode = all[Math.floorMod(p.a, all.length)];
                BornageStaffItem.setMode(staff, mode);
                return Outcome.ok(Component.translatable("crusadecraft.bornage.mode_set", Component.translatable(mode.key())));
            }
            case VALIDATE -> {
                TerritoryService.validate(player, staff);
                return null;
            }
            case CLEAR -> {
                BornageStaffItem.setPoints(staff, java.util.List.of());
                return Outcome.ok(Component.translatable("crusadecraft.bornage.cleared"));
            }
            case SET_BUILD -> {
                SiteKind kind = SiteKind.byOrdinal(p.a);
                if (!editor && !kind.survival()) return Outcome.no("crusadecraft.site.editor_only", Component.translatable(kind.key()));
                BornageStaffItem.setBuild(staff, kind, editor && p.b);
                return Outcome.ok(Component.translatable("crusadecraft.bornage.build_set", Component.translatable(kind.key())));
            }
            case SET_LIEU -> {
                fr.crusadecraft.core.place.LieuKind kind = fr.crusadecraft.core.place.LieuKind.byOrdinal(p.a);
                if (!kind.placed()) return null;
                BornageStaffItem.setLieu(staff, kind);
                return Outcome.ok(Component.translatable("crusadecraft.bornage.lieu_set", Component.translatable(kind.key())));
            }
            default -> {
                return null;
            }
        }
    }

}
