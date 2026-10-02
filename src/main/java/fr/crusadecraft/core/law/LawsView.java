package fr.crusadecraft.core.law;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * L'onglet Lois, calculé par le serveur : chaque loi, son niveau, et pour chaque autre niveau le vote
 * que le conseil émettrait aujourd'hui. Le vote est déterministe : la prévision est le résultat.
 */
public record LawsView(int prestige, int councilSize, List<LawLine> laws) {

    public record LawLine(Law law, int level, long cooldownLeft, List<LevelOption> options) {}

    /**
     * Un niveau possible : ouvert ou non (recherche), et le vote prévu.
     *
     * @param forceCost prestige à dépenser si le conseil rejette ce changement (0 s'il l'approuve)
     */
    public record LevelOption(int level, boolean unlocked, List<String> approve, List<String> reject, int forceCost) {
        public boolean passes() {
            return approve.size() >= reject.size();
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(prestige);
        buf.writeVarInt(councilSize);
        buf.writeVarInt(laws.size());
        for (LawLine l : laws) {
            buf.writeEnum(l.law());
            buf.writeVarInt(l.level());
            buf.writeVarLong(l.cooldownLeft());
            buf.writeVarInt(l.options().size());
            for (LevelOption o : l.options()) {
                buf.writeVarInt(o.level());
                buf.writeBoolean(o.unlocked());
                writeNames(buf, o.approve());
                writeNames(buf, o.reject());
                buf.writeVarInt(o.forceCost());
            }
        }
    }

    public static LawsView read(FriendlyByteBuf buf) {
        int prestige = buf.readVarInt();
        int council = buf.readVarInt();
        int n = buf.readVarInt();
        List<LawLine> laws = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Law law = buf.readEnum(Law.class);
            int level = buf.readVarInt();
            long cooldown = buf.readVarLong();
            int m = buf.readVarInt();
            List<LevelOption> options = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                options.add(new LevelOption(buf.readVarInt(), buf.readBoolean(), readNames(buf), readNames(buf), buf.readVarInt()));
            }
            laws.add(new LawLine(law, level, cooldown, options));
        }
        return new LawsView(prestige, council, laws);
    }

    private static void writeNames(FriendlyByteBuf buf, List<String> names) {
        buf.writeVarInt(names.size());
        for (String s : names) buf.writeUtf(s);
    }

    private static List<String> readNames(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }
}
