package fr.crusadecraft.core.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Une guerre : qui attaque qui, pour quel motif, avec quels alliés, où en sont les combats. Le score est vu du
 * côté de l'attaquant : +100 = victoire totale de l'attaquant, −100 = sa déroute.
 */
public final class War {
    public static final int EVENTS_KEPT = 12;

    /** Un fait d'armes, pour le journal de la guerre. */
    public record Event(long day, String key, List<String> args, int delta) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("day", day);
            t.putString("key", key);
            ListTag a = new ListTag();
            for (String s : args) a.add(StringTag.valueOf(s));
            t.put("args", a);
            t.putInt("delta", delta);
            return t;
        }

        static Event load(CompoundTag t) {
            List<String> args = new ArrayList<>();
            ListTag a = t.getList("args", Tag.TAG_STRING);
            for (int i = 0; i < a.size(); i++) args.add(a.getString(i));
            return new Event(t.getLong("day"), t.getString("key"), args, t.getInt("delta"));
        }
    }

    private final UUID id;
    private final UUID attacker;
    private final UUID defender;
    private final CasusBelli cb;
    /** Province visée (identifiant de province IA), ou −1 pour un motif qui vise le royaume. */
    private final int targetProvince;
    private final long startDay;
    private final Set<UUID> attackerAllies = new LinkedHashSet<>();
    private final Set<UUID> defenderAllies = new LinkedHashSet<>();
    /** Provinces IA occupées par le camp de l'attaquant. */
    private final Set<Integer> occupied = new LinkedHashSet<>();
    /** Colonies de joueurs occupées par le camp adverse (guerres entre joueurs), sous forme de texte. */
    private final Set<String> occupiedColonies = new LinkedHashSet<>();
    /** Points gagnés en bataille (escarmouches, sièges, raids), vus de l'attaquant. */
    private int battleScore;
    /** Jours passés à tenir la province visée. */
    private int goalTicks;
    private int attackerExhaustion;
    private int defenderExhaustion;
    private final List<Event> events = new ArrayList<>();
    private long lastRaidTick = Long.MIN_VALUE;
    private long lastAiOfferDay = Long.MIN_VALUE;
    /** Rangs MineColonies d'avant-guerre (« colonie|joueur » → rang), rendus à la paix. */
    private final java.util.Map<String, String> savedRanks = new java.util.LinkedHashMap<>();

    public War(UUID id, UUID attacker, UUID defender, CasusBelli cb, int targetProvince, long startDay) {
        this.id = id;
        this.attacker = attacker;
        this.defender = defender;
        this.cb = cb;
        this.targetProvince = targetProvince;
        this.startDay = startDay;
    }

    public UUID id() { return id; }
    public UUID attacker() { return attacker; }
    public UUID defender() { return defender; }
    public CasusBelli cb() { return cb; }
    public int targetProvince() { return targetProvince; }
    public long startDay() { return startDay; }
    public Set<UUID> attackerAllies() { return attackerAllies; }
    public Set<UUID> defenderAllies() { return defenderAllies; }
    public Set<Integer> occupied() { return occupied; }
    public Set<String> occupiedColonies() { return occupiedColonies; }
    public int battleScore() { return battleScore; }
    public int goalTicks() { return goalTicks; }
    public int attackerExhaustion() { return attackerExhaustion; }
    public int defenderExhaustion() { return defenderExhaustion; }
    public List<Event> events() { return events; }
    public long lastRaidTick() { return lastRaidTick; }
    public long lastAiOfferDay() { return lastAiOfferDay; }

    public java.util.Map<String, String> savedRanks() { return savedRanks; }
    public void setLastRaidTick(long t) { this.lastRaidTick = t; }
    public void setLastAiOfferDay(long d) { this.lastAiOfferDay = d; }

    /** Le camp d'un royaume : +1 attaquant, −1 défenseur, 0 étranger à la guerre. */
    public int side(UUID realm) {
        if (attacker.equals(realm) || attackerAllies.contains(realm)) return 1;
        if (defender.equals(realm) || defenderAllies.contains(realm)) return -1;
        return 0;
    }

    public boolean involves(UUID realm) {
        return side(realm) != 0;
    }

    /** Les deux royaumes s'affrontent-ils dans cette guerre ? */
    public boolean opposes(UUID a, UUID b) {
        int sa = side(a), sb = side(b);
        return sa != 0 && sb != 0 && sa != sb;
    }

    public void addBattle(int delta) {
        battleScore = WarRules.clamp(battleScore + delta, -WarRules.BATTLE_CAP, WarRules.BATTLE_CAP);
    }

    public void tickGoal() {
        goalTicks = Math.min(WarRules.GOAL_TICK_CAP, goalTicks + 1);
    }

    public void tire(int attacker, int defender) {
        attackerExhaustion = WarRules.clamp(attackerExhaustion + attacker, 0, 100);
        defenderExhaustion = WarRules.clamp(defenderExhaustion + defender, 0, 100);
    }

    public void log(long day, String key, int delta, String... args) {
        events.add(0, new Event(day, key, List.of(args), delta));
        while (events.size() > EVENTS_KEPT) events.remove(events.size() - 1);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("attacker", attacker);
        t.putUUID("defender", defender);
        t.putString("cb", cb.name());
        t.putInt("target", targetProvince);
        t.putLong("start", startDay);
        t.put("attackerAllies", uuids(attackerAllies));
        t.put("defenderAllies", uuids(defenderAllies));
        t.putIntArray("occupied", occupied.stream().mapToInt(Integer::intValue).toArray());
        ListTag oc = new ListTag();
        for (String s : occupiedColonies) oc.add(StringTag.valueOf(s));
        t.put("occupiedColonies", oc);
        t.putInt("battle", battleScore);
        t.putInt("goal", goalTicks);
        t.putInt("exA", attackerExhaustion);
        t.putInt("exD", defenderExhaustion);
        ListTag ev = new ListTag();
        for (Event e : events) ev.add(e.save());
        t.put("events", ev);
        t.putLong("raid", lastRaidTick);
        t.putLong("offer", lastAiOfferDay);
        CompoundTag ranks = new CompoundTag();
        for (java.util.Map.Entry<String, String> e : savedRanks.entrySet()) ranks.putString(e.getKey(), e.getValue());
        t.put("ranks", ranks);
        return t;
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag l = new ListTag();
        for (UUID u : set) l.add(StringTag.valueOf(u.toString()));
        return l;
    }

    private static void readUuids(ListTag l, Set<UUID> into) {
        for (int i = 0; i < l.size(); i++) {
            try {
                into.add(UUID.fromString(l.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // identifiant illisible
            }
        }
    }

    public static War load(CompoundTag t) {
        CasusBelli cb = CasusBelli.byName(t.getString("cb"));
        War w = new War(t.getUUID("id"), t.getUUID("attacker"), t.getUUID("defender"), cb == null ? CasusBelli.HUMILIATE : cb,
                t.getInt("target"), t.getLong("start"));
        readUuids(t.getList("attackerAllies", Tag.TAG_STRING), w.attackerAllies);
        readUuids(t.getList("defenderAllies", Tag.TAG_STRING), w.defenderAllies);
        for (int p : t.getIntArray("occupied")) w.occupied.add(p);
        ListTag oc = t.getList("occupiedColonies", Tag.TAG_STRING);
        for (int i = 0; i < oc.size(); i++) w.occupiedColonies.add(oc.getString(i));
        w.battleScore = t.getInt("battle");
        w.goalTicks = t.getInt("goal");
        w.attackerExhaustion = t.getInt("exA");
        w.defenderExhaustion = t.getInt("exD");
        ListTag ev = t.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < ev.size(); i++) w.events.add(Event.load(ev.getCompound(i)));
        w.lastRaidTick = t.contains("raid") ? t.getLong("raid") : Long.MIN_VALUE;
        w.lastAiOfferDay = t.contains("offer") ? t.getLong("offer") : Long.MIN_VALUE;
        CompoundTag ranks = t.getCompound("ranks");
        for (String k : ranks.getAllKeys()) w.savedRanks.put(k, ranks.getString(k));
        return w;
    }
}
