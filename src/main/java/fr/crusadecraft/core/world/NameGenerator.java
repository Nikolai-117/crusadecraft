package fr.crusadecraft.core.world;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Noms des royaumes IA, de leurs lieux et de leurs personnages, dans une veine médiévale française.
 * Tirage déterministe : même graine, mêmes noms. Les lieux déjà donnés ne reviennent pas.
 */
public final class NameGenerator {

    private static final List<String> PREFIXES = List.of(
            "Val", "Haute", "Pierre", "Mont", "Roche", "Beau", "Clair", "Fort", "Bois", "Lac", "Gris", "Noir",
            "Blanc", "Aube", "Bel", "Chant", "Port", "Grand", "Vieil", "Ronce", "Brise", "Cendre", "Saule",
            "Ormes", "Lune", "Givre", "Ambre", "Corbe", "Hêtre", "Marbre");
    private static final List<String> SUFFIXES = List.of(
            "brume", "marche", "feu", "fort", "court", "mont", "ville", "lieu", "vent", "garde", "champ", "val",
            "roc", "mer", "lande", "castel", "bourg", "fond", "combe", "rive", "pré", "tour", "gué", "vallon",
            "sombre", "clair", "haut", "plaine", "forêt", "pont");
    private static final List<String> MEN = List.of(
            "Aldric", "Bérenger", "Godefroy", "Thibaut", "Enguerrand", "Baudouin", "Raoul", "Gauvain", "Hugues",
            "Arnaud", "Guillaume", "Lothaire", "Amaury", "Foulques", "Geoffroy", "Renaud", "Tancrède", "Roland",
            "Olivier", "Aymeric", "Gérard", "Josselin", "Mathieu", "Eudes", "Bertrand", "Anseau", "Hardouin");
    private static final List<String> WOMEN = List.of(
            "Mathilde", "Aliénor", "Blanche", "Isabeau", "Ermengarde", "Adélaïde", "Béatrice", "Clémence",
            "Yolande", "Constance", "Mahaut", "Sibylle", "Agnès", "Jehanne", "Margot", "Ysolde", "Alix",
            "Héloïse", "Pernelle", "Guillemette", "Oriane", "Bertrade", "Aude", "Douce", "Garsende");

    private final Random random;
    private final Set<String> used = new HashSet<>();

    public NameGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** Un nom de lieu inédit (« Valbrume », « Rocheforêt »…). */
    public String place() {
        for (int attempt = 0; attempt < 200; attempt++) {
            String p = PREFIXES.get(random.nextInt(PREFIXES.size()));
            String s = SUFFIXES.get(random.nextInt(SUFFIXES.size()));
            if (p.toLowerCase().startsWith(s) || p.toLowerCase().endsWith(s)) continue;
            String name = join(p, s);
            if (used.add(name)) return name;
        }
        // Tout est pris : on numérote, ce qui n'arrive qu'avec des centaines de lieux.
        String name = PREFIXES.get(random.nextInt(PREFIXES.size())) + SUFFIXES.get(random.nextInt(SUFFIXES.size()));
        int n = 2;
        while (!used.add(name + " " + toRoman(n))) n++;
        return name + " " + toRoman(n);
    }

    /** Évite les rencontres disgracieuses (« Haute » + « es… ») en élidant le « e » final. */
    static String join(String prefix, String suffix) {
        if (prefix.endsWith("e") && "aeiouéè".indexOf(suffix.charAt(0)) >= 0) {
            return prefix.substring(0, prefix.length() - 1) + suffix;
        }
        return prefix + suffix;
    }

    public String personName(boolean female) {
        List<String> pool = female ? WOMEN : MEN;
        return pool.get(random.nextInt(pool.size()));
    }

    public Random random() {
        return random;
    }

    static String toRoman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n < r.length ? r[n] : String.valueOf(n);
    }
}
