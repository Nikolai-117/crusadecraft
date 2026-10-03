package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.core.character.OpinionModifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La relation entre deux royaumes (joueurs ou IA) : traités en cours, hostilité, vassalité, tribut, et ce
 * que chacun pense de l'autre. Une seule relation par paire, quel que soit l'ordre.
 */
public final class Relation {
    private final UUID a, b;
    private final Map<Treaty, Long> treaties = new EnumMap<>(Treaty.class);
    private boolean hostile;
    private boolean war;
    /** Le suzerain si l'un est vassal de l'autre, sinon {@code null}. */
    private UUID suzerain;
    private int vassalTax = 20;
    private int vassalLevy = 20;
    /** Tribut : qui paie, combien par jour, jusqu'à quand. */
    private UUID tributePayer;
    private int tributePerDay;
    private long tributeUntil;
    /** Opinion que chacun porte à l'autre (clé : celui qui juge). */
    private final Map<UUID, List<OpinionModifier>> opinion = new HashMap<>();
    private long lastAiLetterDay = Long.MIN_VALUE;
    /** Pas de guerre entre eux avant ce jour (après une paix). */
    private long truceUntil = Long.MIN_VALUE;

    public Relation(UUID x, UUID y) {
        if (x.compareTo(y) <= 0) {
            a = x;
            b = y;
        } else {
            a = y;
            b = x;
        }
    }

    public static String key(UUID x, UUID y) {
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }

    public UUID a() { return a; }
    public UUID b() { return b; }

    public UUID other(UUID self) {
        return self.equals(a) ? b : a;
    }

    public boolean involves(UUID realm) {
        return a.equals(realm) || b.equals(realm);
    }

    // ------------------------------------------------------------------ traités

    public boolean has(Treaty t, long today) {
        Long until = treaties.get(t);
        return until != null && until > today;
    }

    public long treatyUntil(Treaty t) {
        return treaties.getOrDefault(t, Long.MIN_VALUE);
    }

    public Map<Treaty, Long> treaties() { return treaties; }

    public void sign(Treaty t, long today) {
        treaties.put(t, t.expiry(today));
        hostile = false;
        // L'alliance totale englobe l'alliance défensive.
        if (t == Treaty.ALLIANCE) treaties.remove(Treaty.ALLIANCE_DEFENSIVE);
    }

    public void breakTreaty(Treaty t) {
        treaties.remove(t);
    }

    /** Retire les traités échus ; renvoie ceux qui viennent d'expirer. */
    public List<Treaty> expire(long today) {
        List<Treaty> out = new ArrayList<>();
        treaties.entrySet().removeIf(e -> {
            boolean gone = e.getValue() <= today;
            if (gone) out.add(e.getKey());
            return gone;
        });
        return out;
    }

    // ------------------------------------------------------------------ hostilité, guerre, vassalité

    public boolean hostile() { return hostile; }
    public void setHostile(boolean hostile) { this.hostile = hostile; }
    public boolean war() { return war; }
    public void setWar(boolean war) { this.war = war; }

    public UUID suzerain() { return suzerain; }
    public int vassalTax() { return vassalTax; }
    public int vassalLevy() { return vassalLevy; }

    public boolean isVassalOf(UUID vassal, UUID lord) {
        return lord.equals(suzerain) && involves(vassal) && !vassal.equals(lord);
    }

    public void makeVassal(UUID lord, int tax, int levy) {
        this.suzerain = lord;
        this.vassalTax = clampRate(tax);
        this.vassalLevy = clampRate(levy);
        this.hostile = false;
        treaties.remove(Treaty.NON_AGRESSION);
    }

    public void setContract(int tax, int levy) {
        this.vassalTax = clampRate(tax);
        this.vassalLevy = clampRate(levy);
    }

    public void freeVassal() {
        this.suzerain = null;
    }

    public static int clampRate(int rate) {
        return Math.max(0, Math.min(50, rate));
    }

    // ------------------------------------------------------------------ tribut

    public UUID tributePayer() { return tributePayer; }
    public int tributePerDay() { return tributePerDay; }
    public long tributeUntil() { return tributeUntil; }

    public boolean paysTribute(UUID payer, long today) {
        return payer.equals(tributePayer) && tributeUntil > today;
    }

    public void setTribute(UUID payer, int perDay, long until) {
        this.tributePayer = payer;
        this.tributePerDay = perDay;
        this.tributeUntil = until;
    }

    // ------------------------------------------------------------------ opinion

    public List<OpinionModifier> opinionOf(UUID holder) {
        return opinion.computeIfAbsent(holder, k -> new ArrayList<>());
    }

    public void addOpinion(UUID holder, OpinionModifier m) {
        opinionOf(holder).add(m);
    }

    public void prune(long today) {
        for (List<OpinionModifier> list : opinion.values()) list.removeIf(m -> m.expired(today));
    }

    public long truceUntil() { return truceUntil; }
    public void setTruceUntil(long day) { this.truceUntil = day; }

    public boolean truce(long today) {
        return truceUntil > today;
    }

    public long lastAiLetterDay() { return lastAiLetterDay; }
    public void setLastAiLetterDay(long day) { this.lastAiLetterDay = day; }

    /** Le statut vu par {@code viewer}. */
    public DiploStatus status(UUID viewer, long today) {
        if (suzerain != null) return suzerain.equals(viewer) ? DiploStatus.SUZERAIN : DiploStatus.VASSAL;
        if (war) return DiploStatus.GUERRE;
        if (has(Treaty.ALLIANCE, today)) return DiploStatus.ALLIANCE;
        if (has(Treaty.ALLIANCE_DEFENSIVE, today)) return DiploStatus.ALLIANCE_DEFENSIVE;
        if (has(Treaty.NON_AGRESSION, today)) return DiploStatus.NON_AGRESSION;
        if (has(Treaty.COMMERCE, today)) return DiploStatus.COMMERCE;
        if (hostile) return DiploStatus.HOSTILE;
        return DiploStatus.NEUTRE;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("a", a);
        t.putUUID("b", b);
        CompoundTag tr = new CompoundTag();
        for (Map.Entry<Treaty, Long> e : treaties.entrySet()) tr.putLong(e.getKey().name(), e.getValue());
        t.put("treaties", tr);
        t.putBoolean("hostile", hostile);
        t.putBoolean("war", war);
        if (suzerain != null) t.putUUID("suzerain", suzerain);
        t.putInt("tax", vassalTax);
        t.putInt("levy", vassalLevy);
        if (tributePayer != null) {
            t.putUUID("tributePayer", tributePayer);
            t.putInt("tributePerDay", tributePerDay);
            t.putLong("tributeUntil", tributeUntil);
        }
        CompoundTag op = new CompoundTag();
        for (Map.Entry<UUID, List<OpinionModifier>> e : opinion.entrySet()) {
            ListTag list = new ListTag();
            for (OpinionModifier m : e.getValue()) list.add(m.save());
            op.put(e.getKey().toString(), list);
        }
        t.put("opinion", op);
        t.putLong("aiLetter", lastAiLetterDay);
        t.putLong("truce", truceUntil);
        return t;
    }

    public static Relation load(CompoundTag t) {
        Relation r = new Relation(t.getUUID("a"), t.getUUID("b"));
        CompoundTag tr = t.getCompound("treaties");
        for (String k : tr.getAllKeys()) {
            Treaty treaty = Treaty.byName(k);
            if (treaty != null) r.treaties.put(treaty, tr.getLong(k));
        }
        r.hostile = t.getBoolean("hostile");
        r.war = t.getBoolean("war");
        if (t.hasUUID("suzerain")) r.suzerain = t.getUUID("suzerain");
        r.vassalTax = t.contains("tax") ? t.getInt("tax") : 20;
        r.vassalLevy = t.contains("levy") ? t.getInt("levy") : 20;
        if (t.hasUUID("tributePayer")) {
            r.tributePayer = t.getUUID("tributePayer");
            r.tributePerDay = t.getInt("tributePerDay");
            r.tributeUntil = t.getLong("tributeUntil");
        }
        CompoundTag op = t.getCompound("opinion");
        for (String k : op.getAllKeys()) {
            try {
                UUID holder = UUID.fromString(k);
                ListTag list = op.getList(k, Tag.TAG_COMPOUND);
                List<OpinionModifier> mods = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    OpinionModifier m = OpinionModifier.load(list.getCompound(i));
                    if (m != null) mods.add(m);
                }
                r.opinion.put(holder, mods);
            } catch (IllegalArgumentException ignored) {
                // clé illisible : ignorée
            }
        }
        r.lastAiLetterDay = t.contains("aiLetter") ? t.getLong("aiLetter") : Long.MIN_VALUE;
        r.truceUntil = t.contains("truce") ? t.getLong("truce") : Long.MIN_VALUE;
        return r;
    }
}
