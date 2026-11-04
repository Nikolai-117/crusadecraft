package fr.crusadecraft.core.nation;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ce que l'écran d'une nation montre (P41).
 *
 * @param mine le joueur en est le souverain : il peut agir
 */
public record NationView(UUID id, String title, int color, boolean mine, String ruler, String culture, String faith, long gold, int prestige,
                         List<String> history, List<CityRow> cities, List<Rel> relations, List<Mail> mail) {

    /** Une ville : son nom, sa population (au sens du monde), ses gens et son histoire. */
    public record CityRow(String name, boolean capital, int population, int people, int x, int z, List<String> history) {}

    /** Une relation : le statut (rang dans {@code DiploStatus}), l'opinion qu'on a de nous, les traités en cours (rangs dans {@code Treaty}). */
    public record Rel(UUID id, String name, int color, int status, int opinion, List<Integer> treaties, boolean atWar, boolean canWar) {}

    public record Mail(UUID id, String sender, String subject, String body, int treaty, int gold, long day) {}

    private static final int TEXT_MAX = 8192;
    private static final int LIST_MAX = 512;

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(title);
        buf.writeInt(color);
        buf.writeBoolean(mine);
        buf.writeUtf(ruler);
        buf.writeUtf(culture);
        buf.writeUtf(faith);
        buf.writeLong(gold);
        buf.writeVarInt(prestige);
        strings(buf, history);
        buf.writeVarInt(cities.size());
        for (CityRow c : cities) {
            buf.writeUtf(c.name());
            buf.writeBoolean(c.capital());
            buf.writeVarInt(c.population());
            buf.writeVarInt(c.people());
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.z());
            strings(buf, c.history());
        }
        buf.writeVarInt(relations.size());
        for (Rel r : relations) {
            buf.writeUUID(r.id());
            buf.writeUtf(r.name());
            buf.writeInt(r.color());
            buf.writeVarInt(r.status());
            buf.writeVarInt(r.opinion());
            buf.writeVarInt(r.treaties().size());
            for (int t : r.treaties()) buf.writeVarInt(t);
            buf.writeBoolean(r.atWar());
            buf.writeBoolean(r.canWar());
        }
        buf.writeVarInt(mail.size());
        for (Mail m : mail) {
            buf.writeUUID(m.id());
            buf.writeUtf(m.sender());
            buf.writeUtf(m.subject());
            buf.writeUtf(m.body(), TEXT_MAX);
            buf.writeVarInt(m.treaty() + 1);
            buf.writeVarInt(m.gold());
            buf.writeLong(m.day());
        }
    }

    public static NationView read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String title = buf.readUtf();
        int color = buf.readInt();
        boolean mine = buf.readBoolean();
        String ruler = buf.readUtf(), culture = buf.readUtf(), faith = buf.readUtf();
        long gold = buf.readLong();
        int prestige = buf.readVarInt();
        List<String> history = strings(buf);
        int n = Math.min(buf.readVarInt(), LIST_MAX);
        List<CityRow> cities = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            cities.add(new CityRow(buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), strings(buf)));
        }
        n = Math.min(buf.readVarInt(), LIST_MAX);
        List<Rel> relations = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID rid = buf.readUUID();
            String name = buf.readUtf();
            int c = buf.readInt(), status = buf.readVarInt(), opinion = buf.readVarInt();
            int k = Math.min(buf.readVarInt(), LIST_MAX);
            List<Integer> treaties = new ArrayList<>(k);
            for (int j = 0; j < k; j++) treaties.add(buf.readVarInt());
            relations.add(new Rel(rid, name, c, status, opinion, treaties, buf.readBoolean(), buf.readBoolean()));
        }
        n = Math.min(buf.readVarInt(), LIST_MAX);
        List<Mail> mail = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            mail.add(new Mail(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readUtf(TEXT_MAX), buf.readVarInt() - 1, buf.readVarInt(), buf.readLong()));
        }
        return new NationView(id, title, color, mine, ruler, culture, faith, gold, prestige, history, cities, relations, mail);
    }

    private static void strings(FriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s, TEXT_MAX);
    }

    private static List<String> strings(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), LIST_MAX);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(TEXT_MAX));
        return out;
    }
}
