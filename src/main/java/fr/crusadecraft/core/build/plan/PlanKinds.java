package fr.crusadecraft.core.build.plan;

import java.util.List;
import java.util.Locale;

/**
 * La sorte d'un plan, d'après son dossier et son nom (P35) : bâtiment, muraille, route ou ouvrage, décoration — et son
 * rôle (segment, angle, porte, tour, puits, lanterne…). Logique pure : c'est ce que le catalogue filtre, et ce que
 * l'inventaire {@code docs/catalogue-plans.md} range.
 */
public final class PlanKinds {
    public static final int BATIMENT = 0, MURAILLE = 1, ROUTE = 2, DECORATION = 3, AUTRE = 4;

    private record Rule(List<String> words, String role) {}

    private static final List<Rule> WALLS = List.of(
            new Rule(List.of("tcorner", "tee", "threeway", "tjunction"), "raccord_t"),
            new Rule(List.of("innercorner", "insidecorner", "insideturn", "cornerin"), "angle_rentrant"),
            new Rule(List.of("corner", "outsideturn", "turn", "diag"), "angle"),
            new Rule(List.of("gatehouse", "gate", "door"), "porte"),
            new Rule(List.of("walltower", "towerbig", "towersmall", "tower", "lookout", "bastion"), "tour"),
            new Rule(List.of("stair", "incline", "oneup", "twoup", "threeup", "fourup", "slope"), "pente"),
            new Rule(List.of("footing"), "fondation"),
            new Rule(List.of("moat"), "douve"),
            new Rule(List.of("fence"), "cloture"),
            new Rule(List.of("palisade"), "palissade"),
            new Rule(List.of("castle"), "chateau"),
            new Rule(List.of("coastal"), "cotiere"),
            new Rule(List.of("segment", "wall", "long", "short", "flat", "straight", "main", "endcap", "offset"), "segment"));
    private static final List<Rule> ROADS = List.of(
            new Rule(List.of("cross", "xfour", "intersection"), "carrefour"),
            new Rule(List.of("tee", "xthree", "tjunction"), "embranchement"),
            new Rule(List.of("corner", "turn", "curve", "bend"), "virage"),
            new Rule(List.of("end", "cap"), "bout"),
            new Rule(List.of("bridge"), "pont"),
            new Rule(List.of("forum", "square", "plaza"), "place"),
            new Rule(List.of("harbor", "harbour", "dock", "pier"), "port"),
            new Rule(List.of("canal", "waterway", "sewer", "aqueduct"), "eau"),
            new Rule(List.of("stair", "incline", "slope", "terrace", "retaining"), "pente"),
            new Rule(List.of("mineshaft", "tunnel"), "mine"),
            new Rule(List.of("straight", "road", "avenue", "boulevard", "street", "path", "rail"), "droit"));
    private static final List<Rule> DECOR = List.of(
            new Rule(List.of("well"), "puits"),
            new Rule(List.of("fountain", "pool", "pond"), "fontaine"),
            new Rule(List.of("lamp", "lantern", "light", "torch"), "lanterne"),
            new Rule(List.of("bench", "seat"), "banc"),
            new Rule(List.of("stall", "cart", "market", "stand"), "etal"),
            new Rule(List.of("statue", "obelisk", "memorial", "monument", "pillar", "column", "wonder"), "statue"),
            new Rule(List.of("shrine", "torii", "altar"), "sanctuaire"),
            new Rule(List.of("garden", "planter", "flower", "hedge", "bush", "tree", "green", "park"), "jardin"),
            new Rule(List.of("square", "plaza", "center", "court"), "place"),
            new Rule(List.of("camp", "supply", "supplies", "ship", "boat"), "camp"),
            new Rule(List.of("cage", "gallows", "pillory", "headsman"), "justice"),
            new Rule(List.of("aqueduct", "duct", "cistern"), "aqueduc"));

    private PlanKinds() {}

    /** La sorte d'un plan, d'après le premier dossier de son chemin dans le pack. */
    public static int kind(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        String top = p.contains("/") ? p.substring(0, p.indexOf('/')) : "";
        return switch (top) {
            case "walls" -> MURAILLE;
            case "infrastructure" -> ROUTE;
            case "decorations" -> DECORATION;
            case "fundamentals", "agriculture", "craftsmanship", "education", "military", "mystic" -> BATIMENT;
            default -> AUTRE;
        };
    }

    /** Le rôle d'un plan dans sa sorte (clé de traduction « crusadecraft.plan.role.… »), vide pour un bâtiment. */
    public static String role(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        String name = PlanRules.base(PlanRef.label(p));
        String folder = p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : "";
        List<Rule> rules = switch (kind(p)) {
            case MURAILLE -> WALLS;
            case ROUTE -> ROADS;
            case DECORATION -> DECOR;
            default -> List.of();
        };
        if (rules.isEmpty()) return "";
        for (String text : new String[]{name, folder}) {
            for (Rule r : rules) for (String w : r.words()) if (text.contains(w)) return r.role();
        }
        return kind(p) == DECORATION ? "decor" : kind(p) == ROUTE ? "ouvrage" : "piece";
    }
}
