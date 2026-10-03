package fr.crusadecraft.core.intrigue;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le souverain sait de ce qui se trame : seulement ce que son Maître-espion a découvert. {@code hidden} : les
 * factions dont on murmure l'existence sans les connaître (avec un maître-espion seulement).
 */
public record IntrigueView(String spymaster, boolean hunting, boolean order, List<FactionLine> factions, int hidden, List<PlotLine> plots,
                           List<SecretLine> secrets, List<String> revolts, int pendingEvents, int dread) {

    public record FactionLine(Faction.Kind kind, List<String> members, int power, String place) {}

    public record PlotLine(String plotter, Plot.Kind kind, int progress) {}

    public record SecretLine(UUID id, String holder, Secret.Kind kind) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(spymaster);
        buf.writeBoolean(hunting);
        buf.writeBoolean(order);
        buf.writeVarInt(factions.size());
        for (FactionLine f : factions) {
            buf.writeEnum(f.kind());
            strings(buf, f.members());
            buf.writeVarInt(f.power());
            buf.writeUtf(f.place());
        }
        buf.writeVarInt(hidden);
        buf.writeVarInt(plots.size());
        for (PlotLine p : plots) {
            buf.writeUtf(p.plotter());
            buf.writeEnum(p.kind());
            buf.writeVarInt(p.progress());
        }
        buf.writeVarInt(secrets.size());
        for (SecretLine s : secrets) {
            buf.writeUUID(s.id());
            buf.writeUtf(s.holder());
            buf.writeEnum(s.kind());
        }
        strings(buf, revolts);
        buf.writeVarInt(pendingEvents);
        buf.writeVarInt(dread);
    }

    private static void strings(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    private static List<String> strings(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    public static IntrigueView read(FriendlyByteBuf buf) {
        String spy = buf.readUtf();
        boolean hunting = buf.readBoolean(), order = buf.readBoolean();
        int n = buf.readVarInt();
        List<FactionLine> factions = new ArrayList<>(n);
        for (int i = 0; i < n; i++) factions.add(new FactionLine(buf.readEnum(Faction.Kind.class), strings(buf), buf.readVarInt(), buf.readUtf()));
        int hidden = buf.readVarInt();
        n = buf.readVarInt();
        List<PlotLine> plots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) plots.add(new PlotLine(buf.readUtf(), buf.readEnum(Plot.Kind.class), buf.readVarInt()));
        n = buf.readVarInt();
        List<SecretLine> secrets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) secrets.add(new SecretLine(buf.readUUID(), buf.readUtf(), buf.readEnum(Secret.Kind.class)));
        List<String> revolts = strings(buf);
        return new IntrigueView(spy, hunting, order, factions, hidden, plots, secrets, revolts, buf.readVarInt(), buf.readVarInt());
    }
}
