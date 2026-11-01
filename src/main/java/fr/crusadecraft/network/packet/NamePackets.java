package fr.crusadecraft.network.packet;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.NameService;
import fr.crusadecraft.core.realm.RealmService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Renommer (P32) : son royaume depuis le menu royal, une ville depuis l'écran des villes. Le serveur revérifie tout. */
public final class NamePackets {
    private NamePackets() {}

    public record Rename(Kind kind, ColonyRef colony, String name) {
        public enum Kind { REALM, CITY }

        public static void encode(Rename p, FriendlyByteBuf buf) {
            buf.writeEnum(p.kind);
            buf.writeBoolean(p.colony != null);
            if (p.colony != null) p.colony.write(buf);
            buf.writeUtf(p.name, 128);
        }

        public static Rename decode(FriendlyByteBuf buf) {
            Kind kind = buf.readEnum(Kind.class);
            ColonyRef colony = buf.readBoolean() ? ColonyRef.read(buf) : null;
            return new Rename(kind, colony, buf.readUtf(128));
        }

        public static void handle(Rename p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (p.kind == Kind.CITY && p.colony != null) {
                Outcome out = NameService.renameColony(player, p.colony, p.name);
                CityPackets.send(player, false, out);
                return;
            }
            Outcome out = NameService.renameRealm(player, p.name);
            player.displayClientMessage(out.message(), true);
            RealmService.realmOf(player.server, player.getUUID())
                    .ifPresent(r -> RequestRealmPacket.sendView(player, r, OpenRealmScreenPacket.Target.MENU));
        }
    }
}
