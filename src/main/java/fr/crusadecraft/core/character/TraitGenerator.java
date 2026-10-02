package fr.crusadecraft.core.character;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Tire les traits de personnalité d'un citoyen.
 *
 * <p>Le tirage est <b>déterministe</b> : la même graine donne toujours les mêmes traits. Un citoyen
 * garde donc sa personnalité même si nos données étaient perdues, sans que rien ne soit écrit chez
 * MineColonies. Les compétences du citoyen orientent le tirage (voir {@link Trait#affinity()}).
 */
public final class TraitGenerator {

    private TraitGenerator() {}

    /** Graine stable pour un citoyen d'un monde donné. */
    public static long seed(long worldSeed, String dimension, int colonyId, int citizenId) {
        long h = worldSeed;
        h = h * 31 + dimension.hashCode();
        h = h * 1_000_003L + colonyId;
        h = h * 7919L + citizenId;
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static List<Trait> roll(long seed, int[] skills, int count) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Trait> pool = new ArrayList<>();
        for (Trait t : Trait.values()) {
            if (t.kind() == Trait.Kind.PERSONALITY) pool.add(t);
        }
        List<Trait> chosen = new ArrayList<>(count);
        while (chosen.size() < count && !pool.isEmpty()) {
            double total = 0;
            double[] weights = new double[pool.size()];
            for (int i = 0; i < pool.size(); i++) {
                weights[i] = weight(pool.get(i), skills);
                total += weights[i];
            }
            double pick = random.nextDouble() * total;
            int index = pool.size() - 1;
            for (int i = 0; i < pool.size(); i++) {
                pick -= weights[i];
                if (pick <= 0) {
                    index = i;
                    break;
                }
            }
            Trait t = pool.get(index);
            chosen.add(t);
            pool.removeIf(other -> other.conflictsWith(t));
        }
        return chosen;
    }

    /** Poids de base 1, augmenté jusqu'à 3 par la compétence associée au trait. */
    private static double weight(Trait t, int[] skills) {
        if (t.affinity() == null || skills == null || t.affinity().ordinal() >= skills.length) return 1.0;
        int level = skills[t.affinity().ordinal()];
        return 1.0 + Math.min(2.0, level / 20.0);
    }
}
