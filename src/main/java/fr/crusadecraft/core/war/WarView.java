package fr.crusadecraft.core.war;

import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La Table de guerre : votre armée, les guerres en cours, et les royaumes qu'on pourrait attaquer avec les
 * motifs ouverts contre chacun. Tout est calculé par le serveur.
 */
public record WarView(int men, int maxMen, boolean raised, int upkeep, long gold, int prestige, boolean sealOk, String sealProblem,
                      List<WarLine> wars, List<Target> targets) {

    /** Un royaume nommé : nom complet (joueur) ou nom de lieu (IA, à composer avec le rang). */
    public record Party(UUID id, boolean ai, String label, TitleRank rank, int color) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeBoolean(ai);
            buf.writeUtf(label);
            buf.writeEnum(rank);
            buf.writeInt(color);
        }

        static Party read(FriendlyByteBuf buf) {
            return new Party(buf.readUUID(), buf.readBoolean(), buf.readUtf(), buf.readEnum(TitleRank.class), buf.readInt());
        }
    }

    /**
     * Une guerre vue par le joueur.
     *
     * @param side   +1 si le joueur est dans le camp de l'attaquant, −1 sinon
     * @param leader le joueur mène-t-il son camp (seul le chef négocie la paix)
     * @param score  score vu de l'attaquant (−100 à +100)
     */
    public record WarLine(UUID id, Party attacker, Party defender, List<String> attackerAllies, List<String> defenderAllies,
                          CasusBelli cb, String target, int side, boolean leader, int score, int myStrength, int enemyStrength,
                          int myExhaustion, int enemyExhaustion, List<String> occupied, long days, List<War.Event> events,
                          Map<WarRules.Peace, WarRules.Verdict> peace) {}

    /** Une province qu'un motif peut viser. */
    public record ProvinceOption(int id, String name) {}

    /** Un motif de guerre contre ce royaume : ouvert ou non, et les provinces visables. */
    public record CbOption(CasusBelli cb, String problem, List<ProvinceOption> provinces) {}

    /** Un royaume qu'on pourrait attaquer. {@code problem} : ce qui interdit toute déclaration. */
    public record Target(Party party, DiploStatus status, int strength, String problem, List<CbOption> cbs) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(men);
        buf.writeVarInt(maxMen);
        buf.writeBoolean(raised);
        buf.writeVarInt(upkeep);
        buf.writeLong(gold);
        buf.writeVarInt(prestige);
        buf.writeBoolean(sealOk);
        buf.writeUtf(sealProblem);
        buf.writeVarInt(wars.size());
        for (WarLine w : wars) {
            buf.writeUUID(w.id());
            w.attacker().write(buf);
            w.defender().write(buf);
            strings(buf, w.attackerAllies());
            strings(buf, w.defenderAllies());
            buf.writeEnum(w.cb());
            buf.writeUtf(w.target());
            buf.writeInt(w.side());
            buf.writeBoolean(w.leader());
            buf.writeInt(w.score());
            buf.writeVarInt(w.myStrength());
            buf.writeVarInt(w.enemyStrength());
            buf.writeVarInt(w.myExhaustion());
            buf.writeVarInt(w.enemyExhaustion());
            strings(buf, w.occupied());
            buf.writeVarLong(w.days());
            buf.writeVarInt(w.events().size());
            for (War.Event e : w.events()) {
                buf.writeLong(e.day());
                buf.writeUtf(e.key());
                strings(buf, e.args());
                buf.writeInt(e.delta());
            }
            buf.writeVarInt(w.peace().size());
            for (Map.Entry<WarRules.Peace, WarRules.Verdict> p : w.peace().entrySet()) {
                buf.writeEnum(p.getKey());
                buf.writeBoolean(p.getValue().accepted());
                buf.writeVarInt(p.getValue().factors().size());
                for (WarRules.Factor f : p.getValue().factors()) {
                    buf.writeUtf(f.key());
                    buf.writeInt(f.value());
                }
            }
        }
        buf.writeVarInt(targets.size());
        for (Target t : targets) {
            t.party().write(buf);
            buf.writeEnum(t.status());
            buf.writeVarInt(t.strength());
            buf.writeUtf(t.problem() == null ? "" : t.problem());
            buf.writeVarInt(t.cbs().size());
            for (CbOption c : t.cbs()) {
                buf.writeEnum(c.cb());
                buf.writeUtf(c.problem() == null ? "" : c.problem());
                buf.writeVarInt(c.provinces().size());
                for (ProvinceOption p : c.provinces()) {
                    buf.writeVarInt(p.id());
                    buf.writeUtf(p.name());
                }
            }
        }
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

    public static WarView read(FriendlyByteBuf buf) {
        int men = buf.readVarInt(), max = buf.readVarInt();
        boolean raised = buf.readBoolean();
        int upkeep = buf.readVarInt();
        long gold = buf.readLong();
        int prestige = buf.readVarInt();
        boolean seal = buf.readBoolean();
        String problem = buf.readUtf();
        int n = buf.readVarInt();
        List<WarLine> wars = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            Party a = Party.read(buf), d = Party.read(buf);
            List<String> aa = strings(buf), da = strings(buf);
            CasusBelli cb = buf.readEnum(CasusBelli.class);
            String target = buf.readUtf();
            int side = buf.readInt();
            boolean leader = buf.readBoolean();
            int score = buf.readInt();
            int ms = buf.readVarInt(), es = buf.readVarInt(), me = buf.readVarInt(), ee = buf.readVarInt();
            List<String> occupied = strings(buf);
            long days = buf.readVarLong();
            int m = buf.readVarInt();
            List<War.Event> events = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                long day = buf.readLong();
                String key = buf.readUtf();
                List<String> args = strings(buf);
                events.add(new War.Event(day, key, args, buf.readInt()));
            }
            m = buf.readVarInt();
            Map<WarRules.Peace, WarRules.Verdict> peace = new EnumMap<>(WarRules.Peace.class);
            for (int j = 0; j < m; j++) {
                WarRules.Peace p = buf.readEnum(WarRules.Peace.class);
                boolean ok = buf.readBoolean();
                int k = buf.readVarInt();
                List<WarRules.Factor> f = new ArrayList<>(k);
                for (int x = 0; x < k; x++) f.add(new WarRules.Factor(buf.readUtf(), buf.readInt()));
                peace.put(p, new WarRules.Verdict(ok, f));
            }
            wars.add(new WarLine(id, a, d, aa, da, cb, target, side, leader, score, ms, es, me, ee, occupied, days, events, peace));
        }
        n = buf.readVarInt();
        List<Target> targets = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Party p = Party.read(buf);
            DiploStatus status = buf.readEnum(DiploStatus.class);
            int strength = buf.readVarInt();
            String prob = buf.readUtf();
            int m = buf.readVarInt();
            List<CbOption> cbs = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                CasusBelli cb = buf.readEnum(CasusBelli.class);
                String cp = buf.readUtf();
                int k = buf.readVarInt();
                List<ProvinceOption> provinces = new ArrayList<>(k);
                for (int x = 0; x < k; x++) provinces.add(new ProvinceOption(buf.readVarInt(), buf.readUtf()));
                cbs.add(new CbOption(cb, cp.isEmpty() ? null : cp, provinces));
            }
            targets.add(new Target(p, status, strength, prob.isEmpty() ? null : prob, cbs));
        }
        return new WarView(men, max, raised, upkeep, gold, prestige, seal, problem, wars, targets);
    }
}
