package fr.crusadecraft.core.city;

import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.lineage.Repliques;

import java.util.List;
import java.util.UUID;

/** Ce que fait un habitant à telle heure, et ce qui décide de sa vie (P43), en logique pure. */
public final class CityRules {
    private CityRules() {}

    /** Ce qui l'occupe. */
    public enum Activity { SLEEP, WORK, MEAL, LEISURE, PLAY }

    /** Un monde de Minecraft commence à 6 h : 0 est l'aube, 6000 midi, 12000 le soir, 18000 minuit. */
    static final int DAY = 24000;
    /** Au plus, un habitant travaille à cet âge ; après, il profite de la place. */
    public static final int RETIREMENT = 65;
    /** En dessous, un enfant joue. */
    public static final int CHILD = 13;

    /** Un nombre stable pour chaque personne : tout le monde ne se lève pas à la même minute. */
    static int stagger(UUID id, int range) {
        return Math.floorMod(id.hashCode(), range);
    }

    /** Un garde de nuit : un sur trois, à la caserne et à la tour. */
    public static boolean nightShift(String job, UUID id) {
        return (job.equals("CASERNE") || job.equals("TOUR")) && stagger(id, 3) == 0;
    }

    /** Ce qu'il fait à cette heure du jour. */
    public static Activity activity(long dayTime, String job, int age, List<Trait> traits, UUID id) {
        long t = Math.floorMod(dayTime, (long) DAY);
        int shift = stagger(id, 600);
        if (age < CHILD) return t >= 500 + shift && t < 11800 ? Activity.PLAY : Activity.SLEEP;
        if (nightShift(job, id)) {
            if (t >= 12500 + shift || t < 500) return Activity.WORK;
            return t < 2500 ? Activity.MEAL : Activity.SLEEP;
        }
        if (job.equals("TAVERNE")) {
            if (t >= 1800 + shift && t < 14800) return Activity.WORK;
            return t >= 800 + shift && t < 1800 + shift ? Activity.MEAL : Activity.SLEEP;
        }
        int start = 500 + shift;
        if (traits.contains(Trait.DILIGENT)) start -= 400;
        if (traits.contains(Trait.PARESSEUX)) start += 600;
        int end = 11000 + shift / 2;
        if (traits.contains(Trait.DILIGENT)) end += 300;
        if (traits.contains(Trait.PARESSEUX)) end -= 400;
        if (t < Math.max(0, start)) return Activity.SLEEP;
        if (t >= 13200 + shift) return Activity.SLEEP;
        if (t >= end) return Activity.LEISURE;
        int lunch = 5400 + shift;
        if (t >= lunch && t < lunch + 900) return Activity.MEAL;
        if (age >= RETIREMENT || age < 16) return Activity.LEISURE;
        return Activity.WORK;
    }

    /** Ce qu'il dit à cette heure, s'il ne travaille pas. */
    public static Repliques.Moment hourMoment(long dayTime) {
        long t = Math.floorMod(dayTime, (long) DAY);
        if (t < 3000) return Repliques.Moment.MATIN;
        if (t >= 5000 && t < 7000) return Repliques.Moment.MIDI;
        if (t >= 10500 && t < 13000) return Repliques.Moment.SOIR;
        if (t >= 13000) return Repliques.Moment.NUIT;
        return Repliques.Moment.TRAVAIL;
    }

    /** Peuvent-ils se marier ? Deux adultes libres, de sexes différents, à moins de quinze ans d'écart. */
    public static boolean canMarry(int ageA, boolean femaleA, boolean freeA, int ageB, boolean femaleB, boolean freeB) {
        return freeA && freeB && femaleA != femaleB && ageA >= 18 && ageB >= 18 && Math.abs(ageA - ageB) <= 15;
    }

    /** Peuvent-ils avoir un enfant ? Un couple, elle de moins de 45 ans, moins de six enfants vivants. */
    public static boolean canHaveChild(int motherAge, int children, int people, int max) {
        return motherAge >= 18 && motherAge < 45 && children < 6 && people < max;
    }

    /** Celui qui peut reprendre un poste vacant : un adulte sans métier, en âge de travailler. */
    public static boolean canTakePost(int age, String job, boolean notable) {
        return age >= 16 && age < RETIREMENT && job.isEmpty() && !notable;
    }
}
