package fr.crusadecraft.network.packet;

import fr.crusadecraft.client.ClientPackets;
import fr.crusadecraft.core.character.CitizenRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Liste complète des dignitaires et de leur titre (« Aldric, Maréchal de Valbrume »), pour l'affichage
 * au-dessus des têtes. Le nom MineColonies du citoyen n'est jamais modifié : seul le client remplace
 * le texte affiché.
 */
public record SyncTitlesPacket(List<Entry> entries) {

    public record Entry(CitizenRef ref, Component title) {}

    public static void encode(SyncTitlesPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            e.ref().write(buf);
            buf.writeComponent(e.title());
        }
    }

    public static SyncTitlesPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Entry(CitizenRef.read(buf), buf.readComponent()));
        return new SyncTitlesPacket(list);
    }

    public static void handle(SyncTitlesPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPackets.syncTitles(p.entries));
    }
}
