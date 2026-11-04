package fr.crusadecraft.core.city;

import java.util.Map;
import java.util.Set;

/**
 * À quoi sert un meuble ou un objet de décoration (P45). On le reconnaît à son nom, ce qui marche pour Minecraft comme pour
 * Dawn of Time, Decorative Blocks, MineColonies ou tout autre mod : un « oak_chair » est une chaise d'où qu'il vienne. Tout
 * ce qui est reconnu ici a un effet réel sur la ville : on s'y assied, on y dort, on y range, on y produit davantage, et ce
 * qui ne sert qu'à faire joli rend la ville plus attirante.
 */
public final class Furnishing {
    /** Ce qu'il est, et donc à quoi il sert. Le nom sert de clé dans les meubles comptés d'un bâtiment. */
    public enum Kind {
        SIEGE, TABLE, STOCKAGE, LUMIERE, PLANTE, TAPISSERIE, EAU;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // Des mots entiers, jamais des bouts de mots : « table » est dans « stable », « etal » dans « pink_petals ».
    private static final Set<String> SEATS = Set.of("chair", "armchair", "stool", "bench", "seat", "sofa", "pew");
    private static final Set<String> TABLES = Set.of("table", "counter", "stall", "desk", "market");
    /** Des « tables » qui ont déjà leur propre usage : on ne s'en mêle pas. */
    private static final Set<String> WORKTABLES = Set.of("crafting", "cartography", "fletching", "smithing", "enchanting", "council", "war", "mayor");
    private static final Set<String> STORAGE = Set.of("barrel", "crate", "shelf", "cupboard", "wardrobe", "drawer", "cabinet", "sack");
    private static final Set<String> LIGHTS = Set.of("lantern", "torch", "candle", "chandelier", "sconce", "brazier", "campfire", "lamp");
    private static final Set<String> PLANTS = Set.of("flower", "sunflower", "potted", "tulip", "rose", "poppy", "dandelion", "orchid", "lilac", "peony", "azalea", "lily", "allium",
            "cornflower", "daisy", "ivy", "planter", "hedge", "bonsai", "petal", "bluet", "torchflower", "blossom");
    private static final Set<String> ART = Set.of("banner", "carpet", "tapestry", "painting", "statue", "curtain", "rug", "flag", "vase", "bust", "pedestal");
    private static final Set<String> WATER = Set.of("well", "fountain", "cauldron", "trough");

    private Furnishing() {}

    /** Ce qu'est ce bloc d'après son identifiant (sans son espace de noms), ou {@code null} : un bloc ordinaire. */
    public static Kind of(String path) {
        if (path == null) return null;
        // Une chaudière ou un coffre-fort d'un autre mod n'a rien d'un tapis ; on évite les faux amis les plus courants.
        if (path.endsWith("_slab") || path.endsWith("_stairs") || path.endsWith("_wall") || path.endsWith("_planks") || path.endsWith("_log") || path.startsWith("stripped_")) return null;
        if (any(path, SEATS)) return Kind.SIEGE;
        if (any(path, WATER)) return Kind.EAU;
        if (any(path, LIGHTS) && !path.contains("redstone")) return Kind.LUMIERE;
        if (any(path, STORAGE)) return Kind.STOCKAGE;
        if (any(path, ART)) return Kind.TAPISSERIE;
        if (any(path, PLANTS)) return Kind.PLANTE;
        if (any(path, TABLES) && !any(path, WORKTABLES)) return Kind.TABLE;
        return null;
    }

    /** Un des mots de ce nom, au singulier ou au pluriel, est-il dans cette liste ? */
    private static boolean any(String path, Set<String> words) {
        for (String w : path.split("_")) {
            if (words.contains(w) || (w.endsWith("s") && words.contains(w.substring(0, w.length() - 1)))) return true;
        }
        return false;
    }

    /** Peut-on s'y asseoir ? Un siège, ou un escalier posé à l'endroit : c'est le banc de tous les bâtisseurs. */
    public static boolean sittable(String path, boolean bottomStairs) {
        return of(path) == Kind.SIEGE || bottomStairs;
    }

    /** Ce que ce meuble rapporte à ce métier : un pourcentage de plus de ce qu'on y produit. */
    public static int bonus(BuildingFunction f, Map<String, Integer> furniture) {
        return switch (f) {
            case BOULANGERIE -> tier(n(furniture, "four") + n(furniture, "fumoir"), 25, 100);
            case FORGE -> tier(n(furniture, "enclume") + n(furniture, "haut_fourneau") + n(furniture, "forge"), 25, 100);
            case BOUCHERIE -> tier(n(furniture, "fumoir") + n(furniture, "table"), 20, 80);
            case TAVERNE -> tier(n(furniture, "siege") + n(furniture, "table") + n(furniture, "tonneau") + n(furniture, "chaudron"), 5, 100);
            case FERME -> tier(n(furniture, "composteur") + n(furniture, "foin"), 10, 60);
            case ECURIE -> tier(n(furniture, "foin"), 10, 60);
            case BIBLIOTHEQUE, ECOLE -> tier(n(furniture, "livres") + n(furniture, "lutrin"), 10, 100);
            case ATELIER -> tier(n(furniture, "etabli_metier") + n(furniture, "etabli"), 25, 75);
            case MOULIN -> tier(n(furniture, "stockage") + n(furniture, "tonneau"), 10, 50);
            case MARCHE -> tier(n(furniture, "table") + n(furniture, "tonneau"), 5, 50);
            default -> 0;
        };
    }

    private static int tier(int count, int each, int max) {
        return Math.min(max, count * each);
    }

    private static int n(Map<String, Integer> m, String k) {
        return m.getOrDefault(k, 0);
    }

    /** Ce que tout ce qui se range offre de place en plus à la ville. */
    public static int storage(Map<String, Integer> furniture) {
        return (n(furniture, "tonneau") + n(furniture, "coffre") + n(furniture, "stockage")) * 8;
    }

    /** Le charme d'une ville, de 0 à 100 : ce qui fait joli, rapporté à sa taille. */
    public static int charm(int decor, int buildings) {
        if (decor <= 0) return 0;
        return Math.min(100, decor * 100 / (40 + 8 * Math.max(0, buildings)));
    }

    /** Les visiteurs qu'attire le charme d'une ville : autant de pièces en plus par jour. */
    public static int visitors(int charm) {
        return charm / 4;
    }
}
