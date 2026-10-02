package fr.crusadecraft.core.world;

import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Un personnage d'un royaume IA : il n'existe que dans nos données (aucune entité), avec les mêmes
 * aptitudes et traits que les citoyens des royaumes des joueurs.
 */
public record AiCharacter(String name, boolean female, int age, int[] stats, List<Trait> traits) {

    public AiCharacter {
        if (stats.length != Stat.COUNT) throw new IllegalArgumentException("aptitudes : " + stats.length);
        traits = List.copyOf(traits);
    }

    public int stat(Stat s) {
        return stats[s.ordinal()];
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putBoolean("female", female);
        t.putInt("age", age);
        t.putIntArray("stats", stats);
        ListTag tr = new ListTag();
        for (Trait trait : traits) tr.add(StringTag.valueOf(trait.name()));
        t.put("traits", tr);
        return t;
    }

    public static AiCharacter load(CompoundTag t) {
        int[] stats = t.getIntArray("stats");
        if (stats.length != Stat.COUNT) stats = new int[Stat.COUNT];
        List<Trait> traits = new ArrayList<>();
        ListTag tr = t.getList("traits", Tag.TAG_STRING);
        for (int i = 0; i < tr.size(); i++) {
            Trait trait = Trait.byName(tr.getString(i));
            if (trait != null) traits.add(trait);
        }
        return new AiCharacter(t.getString("name"), t.getBoolean("female"), t.getInt("age"), stats, traits);
    }
}
