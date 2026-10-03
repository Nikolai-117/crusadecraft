package fr.crusadecraft.core.court;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * La Lice : le prix du tournoi, ce qui l'empêche, les jouteurs attendus (les {@code own} premiers sont de votre
 * cour) et le tableau du dernier tournoi.
 */
public record TournamentView(int cost, long gold, String problem, long cooldownLeft, List<String> entrants, int own, List<Component> last) {

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(cost);
        buf.writeLong(gold);
        buf.writeUtf(problem);
        buf.writeVarLong(cooldownLeft);
        buf.writeVarInt(entrants.size());
        for (String e : entrants) buf.writeUtf(e);
        buf.writeVarInt(own);
        buf.writeVarInt(last.size());
        for (Component c : last) buf.writeComponent(c);
    }

    public static TournamentView read(FriendlyByteBuf buf) {
        int cost = buf.readVarInt();
        long gold = buf.readLong();
        String problem = buf.readUtf();
        long cd = buf.readVarLong();
        int n = buf.readVarInt();
        List<String> entrants = new ArrayList<>(n);
        for (int i = 0; i < n; i++) entrants.add(buf.readUtf());
        int own = buf.readVarInt();
        n = buf.readVarInt();
        List<Component> last = new ArrayList<>(n);
        for (int i = 0; i < n; i++) last.add(buf.readComponent());
        return new TournamentView(cost, gold, problem, cd, entrants, own, last);
    }
}
