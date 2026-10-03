package fr.crusadecraft.core.court;

import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.realm.TitleRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Les règles du tournoi, sans rien lire du jeu : la valeur d'un jouteur, le tableau à élimination directe, le prix
 * d'un tournoi et le prestige qu'il rapporte.
 */
public final class TournamentRules {
    /** Jours entre deux tournois d'un même royaume. */
    public static final int COOLDOWN_DAYS = 15;
    public static final int MAX_ENTRANTS = 8;

    private TournamentRules() {}

    /** Un jouteur : son nom, s'il vient de votre cour, son Martial et ses traits. {@code index} le désigne dans le tableau. */
    public record Entrant(int index, String name, boolean mine, int martial, List<Trait> traits) {}

    /** Une passe d'armes : deux jouteurs, le vainqueur, et si le vaincu y a laissé des plumes. */
    public record Bout(int round, Entrant a, Entrant b, Entrant winner, boolean wounded) {}

    public record Result(List<Bout> bouts, Entrant champion) {}

    /** La valeur d'un jouteur : son Martial, ses traits (bravoure, expérience, gloire, blessures), et la chance du jour. */
    public static double strength(Entrant e, double roll) {
        double base = Math.max(1, e.martial());
        for (Trait t : e.traits()) {
            base += switch (t) {
                case BRAVE -> 2;
                case VETERAN -> 3;
                case CHAMPION -> 5;
                case BLESSE -> -4;
                case LACHE -> -3;
                default -> 0;
            };
        }
        return Math.max(0.5, base) * (0.7 + roll * 0.6);
    }

    /** Le tableau : à chaque tour, les jouteurs vont par paires ; un nombre impair laisse un exempt. */
    public static Result run(List<Entrant> entrants, Random random) {
        List<Entrant> left = new ArrayList<>(entrants);
        Collections.shuffle(left, random);
        List<Bout> bouts = new ArrayList<>();
        int round = 1;
        while (left.size() > 1) {
            List<Entrant> next = new ArrayList<>();
            for (int i = 0; i + 1 < left.size(); i += 2) {
                Entrant a = left.get(i), b = left.get(i + 1);
                boolean aWins = strength(a, random.nextDouble()) >= strength(b, random.nextDouble());
                Entrant winner = aWins ? a : b;
                bouts.add(new Bout(round, a, b, winner, random.nextDouble() < 0.1));
                next.add(winner);
            }
            if (left.size() % 2 == 1) next.add(left.get(left.size() - 1));
            left = next;
            round++;
        }
        return new Result(bouts, left.isEmpty() ? null : left.get(0));
    }

    /** Ce que coûte un tournoi : plus le titre est haut, plus la fête doit l'être. */
    public static int cost(TitleRank rank) {
        return 50 + 50 * rank.ordinal();
    }

    /** Le prestige de l'hôte, selon son rang et le nombre de jouteurs venus. */
    public static int hostPrestige(TitleRank rank, int entrants) {
        return 10 + 5 * rank.ordinal() + entrants;
    }
}
