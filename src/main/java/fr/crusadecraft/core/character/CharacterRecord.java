package fr.crusadecraft.core.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ce que CrusadeCraft retient d'un personnage, en plus de ce que MineColonies sait déjà du citoyen.
 *
 * <p>Ses compétences, son nom ou son métier ne sont pas copiés ici : ils sont relus dans MineColonies à
 * chaque affichage, et suivent donc la vie du citoyen. On ne garde que ce qui n'existe que chez nous :
 * traits, opinion datée, stress, captivité, souvenirs.
 */
public final class CharacterRecord {
    public static final int HISTORY_MAX = 30;
    public static final int STRESS_MAX = 300;

    private final CitizenRef ref;
    private String lastKnownName;
    private final List<Trait> traits = new ArrayList<>();
    private final List<OpinionModifier> modifiers = new ArrayList<>();
    private final List<HistoryEntry> history = new ArrayList<>();
    private int stress;
    private CharacterStatus status = CharacterStatus.LIBRE;
    private long statusUntilDay = -1;
    private boolean deceased;

    public CharacterRecord(CitizenRef ref, String name, List<Trait> traits) {
        this.ref = ref;
        this.lastKnownName = name;
        this.traits.addAll(traits);
    }

    public CitizenRef ref() { return ref; }
    public String lastKnownName() { return lastKnownName; }
    public List<Trait> traits() { return Collections.unmodifiableList(traits); }
    public List<OpinionModifier> modifiers() { return modifiers; }
    public List<HistoryEntry> history() { return Collections.unmodifiableList(history); }
    public int stress() { return stress; }
    public CharacterStatus status() { return status; }
    public long statusUntilDay() { return statusUntilDay; }
    public boolean deceased() { return deceased; }

    public void setLastKnownName(String name) { this.lastKnownName = name; }
    public void setDeceased(boolean deceased) { this.deceased = deceased; }

    public void setStatus(CharacterStatus status, long untilDay) {
        this.status = status;
        this.statusUntilDay = untilDay;
    }

    public void addStress(int amount) {
        stress = Math.max(0, Math.min(STRESS_MAX, stress + amount));
    }

    /** Niveau de stress façon CK3 : 0 à 3. */
    public int stressLevel() {
        return Math.min(3, stress / 100);
    }

    public boolean addTrait(Trait trait) {
        for (Trait t : traits) {
            if (t.conflictsWith(trait)) return false;
        }
        traits.add(trait);
        return true;
    }

    public void removeTrait(Trait trait) {
        traits.remove(trait);
    }

    public void addModifier(OpinionModifier m) {
        // Un même motif ne s'empile pas : le plus récent remplace l'ancien.
        modifiers.removeIf(old -> old.reason() == m.reason());
        modifiers.add(m);
    }

    public void remember(long day, String key, String... args) {
        history.add(0, new HistoryEntry(day, key, List.of(args)));
        while (history.size() > HISTORY_MAX) history.remove(history.size() - 1);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("ref", ref.save());
        tag.putString("name", lastKnownName);
        ListTag tl = new ListTag();
        for (Trait t : traits) tl.add(net.minecraft.nbt.StringTag.valueOf(t.name()));
        tag.put("traits", tl);
        ListTag ml = new ListTag();
        for (OpinionModifier m : modifiers) ml.add(m.save());
        tag.put("modifiers", ml);
        ListTag hl = new ListTag();
        for (HistoryEntry h : history) hl.add(h.save());
        tag.put("history", hl);
        tag.putInt("stress", stress);
        tag.putString("status", status.name());
        tag.putLong("statusUntil", statusUntilDay);
        tag.putBoolean("deceased", deceased);
        return tag;
    }

    public static CharacterRecord load(CompoundTag tag) {
        List<Trait> traits = new ArrayList<>();
        ListTag tl = tag.getList("traits", Tag.TAG_STRING);
        for (int i = 0; i < tl.size(); i++) {
            Trait t = Trait.byName(tl.getString(i));
            if (t != null) traits.add(t);
        }
        CharacterRecord r = new CharacterRecord(CitizenRef.load(tag.getCompound("ref")), tag.getString("name"), traits);
        ListTag ml = tag.getList("modifiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < ml.size(); i++) {
            OpinionModifier m = OpinionModifier.load(ml.getCompound(i));
            if (m != null) r.modifiers.add(m);
        }
        ListTag hl = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) r.history.add(HistoryEntry.load(hl.getCompound(i)));
        r.stress = tag.getInt("stress");
        r.status = CharacterStatus.byName(tag.getString("status"));
        r.statusUntilDay = tag.contains("statusUntil") ? tag.getLong("statusUntil") : -1;
        r.deceased = tag.getBoolean("deceased");
        return r;
    }
}
