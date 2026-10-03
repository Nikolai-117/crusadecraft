package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.event.PendingEvent;
import fr.crusadecraft.core.world.WorldNews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce qui se trame dans un royaume, et ce qu'il en reste : factions, complots, secrets, provinces en révolte,
 * courtisans tenus par le chantage, événements qui attendent une décision, et la chronique du règne.
 */
public final class IntrigueState {
    public static final int CHRONICLE_KEPT = 200;
    public static final int PENDING_MAX = 3;

    private final Map<Faction.Kind, Faction> factions = new EnumMap<>(Faction.Kind.class);
    private final List<Plot> plots = new ArrayList<>();
    private final List<Secret> secrets = new ArrayList<>();
    /** Provinces en révolte (texte de la colonie → jour de fin) : elles ne paient pas l'impôt. */
    private final Map<String, Long> revolts = new HashMap<>();
    /** Courtisans tenus par un chantage (texte du citoyen → jour de fin) : ils ne rejoignent aucune faction. */
    private final Map<String, Long> hooks = new HashMap<>();
    private final List<PendingEvent> pending = new ArrayList<>();
    private final Map<String, Long> eventCooldowns = new HashMap<>();
    private final List<WorldNews> chronicle = new ArrayList<>();

    public Map<Faction.Kind, Faction> factions() { return factions; }
    public List<Plot> plots() { return plots; }
    public List<Secret> secrets() { return secrets; }
    public List<PendingEvent> pending() { return pending; }
    public Map<String, Long> eventCooldowns() { return eventCooldowns; }
    public List<WorldNews> chronicle() { return chronicle; }

    public Optional<Plot> plot(UUID id) {
        for (Plot p : plots) if (p.id().equals(id)) return Optional.of(p);
        return Optional.empty();
    }

    public Optional<Secret> secret(UUID id) {
        for (Secret s : secrets) if (s.id().equals(id)) return Optional.of(s);
        return Optional.empty();
    }

    public Optional<PendingEvent> pendingEvent(UUID id) {
        for (PendingEvent e : pending) if (e.id().equals(id)) return Optional.of(e);
        return Optional.empty();
    }

    public boolean inRevolt(ColonyRef colony, long today) {
        Long until = revolts.get(colony.toString());
        return until != null && today < until;
    }

    public void setRevolt(ColonyRef colony, long until) {
        revolts.put(colony.toString(), until);
    }

    public boolean hooked(CitizenRef citizen, long today) {
        Long until = hooks.get(citizen.toString());
        return until != null && today < until;
    }

    public void hook(CitizenRef citizen, long until) {
        hooks.put(citizen.toString(), until);
    }

    public void record(WorldNews entry) {
        chronicle.add(0, entry);
        while (chronicle.size() > CHRONICLE_KEPT) chronicle.remove(chronicle.size() - 1);
    }

    /** Oublie un personnage (mort, parti) : il quitte les factions, ses complots et ses secrets disparaissent. */
    public void forget(CitizenRef citizen) {
        for (Faction f : factions.values()) f.members().remove(citizen);
        plots.removeIf(p -> p.plotter().equals(citizen));
        secrets.removeIf(s -> s.holder().equals(citizen));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        ListTag f = new ListTag();
        for (Faction x : factions.values()) f.add(x.save());
        t.put("factions", f);
        ListTag p = new ListTag();
        for (Plot x : plots) p.add(x.save());
        t.put("plots", p);
        ListTag s = new ListTag();
        for (Secret x : secrets) s.add(x.save());
        t.put("secrets", s);
        t.put("revolts", days(revolts));
        t.put("hooks", days(hooks));
        ListTag e = new ListTag();
        for (PendingEvent x : pending) e.add(x.save());
        t.put("pending", e);
        t.put("cooldowns", days(eventCooldowns));
        ListTag c = new ListTag();
        for (WorldNews x : chronicle) c.add(x.save());
        t.put("chronicle", c);
        return t;
    }

    private static CompoundTag days(Map<String, Long> map) {
        CompoundTag t = new CompoundTag();
        for (Map.Entry<String, Long> e : map.entrySet()) t.putLong(e.getKey(), e.getValue());
        return t;
    }

    private static void readDays(CompoundTag t, Map<String, Long> into) {
        for (String k : t.getAllKeys()) into.put(k, t.getLong(k));
    }

    public static IntrigueState load(CompoundTag t) {
        IntrigueState s = new IntrigueState();
        ListTag f = t.getList("factions", Tag.TAG_COMPOUND);
        for (int i = 0; i < f.size(); i++) {
            Faction x = Faction.load(f.getCompound(i));
            if (x != null) s.factions.put(x.kind(), x);
        }
        ListTag p = t.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            Plot x = Plot.load(p.getCompound(i));
            if (x != null) s.plots.add(x);
        }
        ListTag sc = t.getList("secrets", Tag.TAG_COMPOUND);
        for (int i = 0; i < sc.size(); i++) {
            Secret x = Secret.load(sc.getCompound(i));
            if (x != null) s.secrets.add(x);
        }
        readDays(t.getCompound("revolts"), s.revolts);
        readDays(t.getCompound("hooks"), s.hooks);
        ListTag e = t.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < e.size(); i++) {
            try {
                s.pending.add(PendingEvent.load(e.getCompound(i)));
            } catch (RuntimeException ignored) {
                // événement illisible : oublié
            }
        }
        readDays(t.getCompound("cooldowns"), s.eventCooldowns);
        ListTag c = t.getList("chronicle", Tag.TAG_COMPOUND);
        for (int i = 0; i < c.size(); i++) s.chronicle.add(WorldNews.load(c.getCompound(i)));
        return s;
    }
}
