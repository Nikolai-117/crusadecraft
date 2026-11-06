package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les réglages d'une ville (P60), tous réunis ici : ce qu'on peut changer dans l'atelier des villes. Chaque réglage est une ligne de cette table : l'écran,
 * l'enregistrement et les vérifications du serveur s'en déduisent. En ajouter un ne demande donc qu'une ligne, deux textes, et de s'en servir dans le générateur.
 *
 * <p>Inspiré de ce que proposent les générateurs de villes de Watabou et Fantasy Town Generator ; rien de leur code ni de leurs images.
 */
public final class UrbanParams {
    public enum Group {
        GENERAL, TRACE, QUARTIERS, DEFENSES, EAU, CAMPAGNE, BATIMENTS;

        public String key() {
            return "crusadecraft.urban.group." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Kind { SLIDER, TOGGLE, CHOICE }

    /**
     * Un réglage.
     *
     * @param choices le nombre de valeurs d'un choix ; 0 sinon
     * @param auto    un curseur dont la valeur la plus basse veut dire « au mieux » : le générateur décide
     */
    public record Def(String key, Group group, Kind kind, int min, int max, int step, int fallback, int choices, boolean auto) {
        public int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }

        public String label() {
            return "crusadecraft.urban.param." + key;
        }

        public String hint() {
            return label() + ".hint";
        }

        public String valueKey(int v) {
            return label() + "." + v;
        }

        /** Un seul réglage sait de lui-même nommer ses valeurs sans texte propre : la civilisation. */
        public boolean isStyle() {
            return key.equals("style");
        }
    }

    private static final Map<String, Def> ALL = new LinkedHashMap<>();

    private static Def slider(String key, Group g, int min, int max, int step, int fallback) {
        return put(new Def(key, g, Kind.SLIDER, min, max, step, fallback, 0, false));
    }

    private static Def auto(String key, Group g, int max, int step) {
        return put(new Def(key, g, Kind.SLIDER, -1, max, step, -1, 0, true));
    }

    private static Def toggle(String key, Group g, boolean fallback) {
        return put(new Def(key, g, Kind.TOGGLE, 0, 1, 1, fallback ? 1 : 0, 0, false));
    }

    private static Def choice(String key, Group g, int choices, int fallback) {
        return put(new Def(key, g, Kind.CHOICE, 0, choices - 1, 1, fallback, choices, false));
    }

    private static Def put(Def d) {
        ALL.put(d.key(), d);
        return d;
    }

    // ------------------------------------------------------------------ général
    public static final Def STYLE = choice("style", Group.GENERAL, NationStyle.values().length, 0);
    public static final Def HABITANTS = slider("habitants", Group.GENERAL, 10, 3000, 10, 300);
    public static final Def PAR_MAISON = slider("par_maison", Group.GENERAL, 2, 12, 1, 5);
    public static final Def RICHESSE = choice("richesse", Group.GENERAL, 6, 2);
    public static final Def NIVEAU_MIN = slider("niveau_min", Group.GENERAL, 1, 5, 1, 1);
    public static final Def NIVEAU_MAX = slider("niveau_max", Group.GENERAL, 1, 5, 1, 5);

    // ------------------------------------------------------------------ le tracé
    public static final Def MODELE = choice("modele", Group.TRACE, 6, 0);
    public static final Def RESEAU = choice("reseau", Group.TRACE, 3, 0);
    public static final Def ROUTES = choice("routes", Group.TRACE, 7, 0);
    public static final Def ANGLE = slider("angle", Group.TRACE, 0, 45, 1, 12);
    public static final Def SEGMENT = slider("segment", Group.TRACE, 6, 40, 1, 14);
    public static final Def BRANCHE = slider("branche", Group.TRACE, 1, 8, 1, 2);
    public static final Def NOUVELLE_ROUTE = slider("nouvelle_route", Group.TRACE, 0, 100, 5, 20);
    public static final Def QUARTIERS_NB = choice("quartiers_nb", Group.TRACE, 3, 1);
    public static final Def ILOT_MIN = slider("ilot_min", Group.TRACE, 30, 900, 10, 80);
    public static final Def ILOT_MAX = slider("ilot_max", Group.TRACE, 400, 8000, 100, 1800);
    public static final Def LARGEUR_GRANDE = slider("largeur_grande", Group.TRACE, 3, 11, 1, 5);
    public static final Def LARGEUR_RUE = slider("largeur_rue", Group.TRACE, 1, 7, 1, 3);
    public static final Def LARGEUR_RUELLE = slider("largeur_ruelle", Group.TRACE, 1, 3, 1, 1);
    public static final Def RUE_REMPARTS = toggle("rue_remparts", Group.TRACE, true);
    public static final Def PLACEMENT = choice("placement", Group.TRACE, 4, 1);
    public static final Def MARGE_RUE = slider("marge_rue", Group.TRACE, 0, 4, 1, 1);
    public static final Def REMPLISSAGE = slider("remplissage", Group.TRACE, 10, 100, 5, 95);
    public static final Def MATIERE_RUES = choice("matiere_rues", Group.TRACE, 7, 0);
    public static final Def LANTERNES = slider("lanternes", Group.TRACE, 0, 100, 5, 40);
    public static final Def ARBRES_RUES = slider("arbres_rues", Group.TRACE, 0, 100, 5, 15);

    // ------------------------------------------------------------------ les quartiers
    public static final Def PLACE = toggle("place", Group.QUARTIERS, true);
    public static final Def TAILLE_PLACE = slider("taille_place", Group.QUARTIERS, 8, 64, 2, 22);
    public static final Def CENTRE = choice("centre", Group.QUARTIERS, 4, 0);
    public static final Def TEMPLE = toggle("temple", Group.QUARTIERS, true);
    public static final Def ESPACES_VERTS = toggle("espaces_verts", Group.QUARTIERS, true);
    public static final Def BIDONVILLE = toggle("bidonville", Group.QUARTIERS, false);
    public static final Def Q_MARCHE = slider("q_marche", Group.QUARTIERS, 0, 10, 1, 2);
    public static final Def Q_NOBLES = slider("q_nobles", Group.QUARTIERS, 0, 10, 1, 2);
    public static final Def Q_MARCHANDS = slider("q_marchands", Group.QUARTIERS, 0, 10, 1, 3);
    public static final Def Q_ARTISANS = slider("q_artisans", Group.QUARTIERS, 0, 10, 1, 3);
    public static final Def Q_HABITATIONS = slider("q_habitations", Group.QUARTIERS, 0, 10, 1, 6);
    public static final Def Q_RELIGIEUX = slider("q_religieux", Group.QUARTIERS, 0, 10, 1, 1);
    public static final Def Q_ADMIN = slider("q_admin", Group.QUARTIERS, 0, 10, 1, 1);
    public static final Def Q_MILITAIRE = slider("q_militaire", Group.QUARTIERS, 0, 10, 1, 1);
    public static final Def Q_PORT = slider("q_port", Group.QUARTIERS, 0, 10, 1, 2);
    public static final Def Q_PAUVRES = slider("q_pauvres", Group.QUARTIERS, 0, 10, 1, 2);
    public static final Def Q_PARC = slider("q_parc", Group.QUARTIERS, 0, 10, 1, 1);

    // ------------------------------------------------------------------ les défenses
    public static final Def MURS = toggle("murs", Group.DEFENSES, true);
    public static final Def TYPE_MUR = choice("type_mur", Group.DEFENSES, 6, 0);
    public static final Def FORME_MUR = choice("forme_mur", Group.DEFENSES, 3, 1);
    public static final Def PORTES = auto("portes", Group.DEFENSES, 8, 1);
    public static final Def TOUR_TOUS = slider("tour_tous", Group.DEFENSES, 8, 60, 2, 24);
    public static final Def HAUTEUR_MUR = slider("hauteur_mur", Group.DEFENSES, 3, 16, 1, 7);
    public static final Def EPAISSEUR_MUR = slider("epaisseur_mur", Group.DEFENSES, 1, 5, 1, 2);
    public static final Def DOUVES = toggle("douves", Group.DEFENSES, false);
    public static final Def CITADELLE = toggle("citadelle", Group.DEFENSES, false);
    public static final Def CHATEAU_EN_VILLE = toggle("chateau_en_ville", Group.DEFENSES, false);
    public static final Def TAILLE_CITADELLE = slider("taille_citadelle", Group.DEFENSES, 24, 96, 2, 44);
    public static final Def FAUBOURGS = slider("faubourgs", Group.DEFENSES, 0, 70, 5, 15);
    public static final Def DENSITE_FAUBOURGS = slider("densite_faubourgs", Group.DEFENSES, 10, 100, 5, 70);

    // ------------------------------------------------------------------ l'eau
    public static final Def RIVIERE = toggle("riviere", Group.EAU, false);
    public static final Def LARGEUR_RIVIERE = slider("largeur_riviere", Group.EAU, 3, 25, 1, 7);
    public static final Def MEANDRES = slider("meandres", Group.EAU, 0, 100, 5, 40);
    public static final Def PONTS = auto("ponts", Group.EAU, 8, 1);
    public static final Def COTE = choice("cote", Group.EAU, 3, 0);
    public static final Def PART_MER = slider("part_mer", Group.EAU, 10, 70, 5, 30);
    public static final Def DIRECTION_MER = choice("direction_mer", Group.EAU, 9, 0);
    public static final Def PORT = toggle("port", Group.EAU, true);
    public static final Def CREUSER = toggle("creuser", Group.EAU, true);

    // ------------------------------------------------------------------ la campagne
    public static final Def CHAMPS = choice("champs", Group.CAMPAGNE, 4, 1);
    public static final Def SURFACE_CHAMPS = slider("surface_champs", Group.CAMPAGNE, 0, 100, 5, 30);
    public static final Def CULTURE = choice("culture", Group.CAMPAGNE, 5, 4);
    public static final Def FORETS = slider("forets", Group.CAMPAGNE, 0, 100, 5, 20);
    public static final Def CLIMAT = choice("climat", Group.CAMPAGNE, 6, 0);
    public static final Def ROUTES_CAMPAGNE = slider("routes_campagne", Group.CAMPAGNE, 0, 200, 10, 60);
    public static final Def CIMETIERE = toggle("cimetiere", Group.CAMPAGNE, true);

    // ------------------------------------------------------------------ combien de chaque bâtiment
    private static final Map<TownSize.Role, Def> COUNTS = new java.util.EnumMap<>(TownSize.Role.class);

    static {
        for (TownSize.Role r : TownSize.Role.values()) {
            if (r == TownSize.Role.PLAZA || r == TownSize.Role.TOWNHALL) continue;
            COUNTS.put(r, auto("b_" + r.name().toLowerCase(java.util.Locale.ROOT), Group.BATIMENTS, r == TownSize.Role.HOUSE ? 400 : 60, 1));
        }
    }

    /** Le réglage du nombre de bâtiments de ce genre. */
    public static Def count(TownSize.Role role) {
        return COUNTS.get(role);
    }

    /** Le même, pour écrire court dans les préréglages. */
    static Def b(TownSize.Role role) {
        return COUNTS.get(role);
    }

    public static List<Def> defs() {
        return new ArrayList<>(ALL.values());
    }

    public static List<Def> defs(Group g) {
        List<Def> out = new ArrayList<>();
        for (Def d : ALL.values()) if (d.group() == g) out.add(d);
        return out;
    }

    public static Def byKey(String key) {
        return ALL.get(key);
    }

    // ------------------------------------------------------------------ les valeurs

    public static final int NAME_MAX = 40;
    private final Map<String, Integer> values = new HashMap<>();
    private String name = "";
    private long seed = 1;
    /** P60 : ce qu'on a corrigé à la main sur le plan : la nature d'un îlot, les bâtiments retirés ou tirés de nouveau. */
    private final Map<Integer, Integer> lotWards = new HashMap<>();
    private final Set<Integer> removed = new HashSet<>();
    private final Map<Integer, Integer> rerolls = new HashMap<>();

    public int get(Def d) {
        Integer v = values.get(d.key());
        return v == null ? d.fallback() : v;
    }

    public boolean on(Def d) {
        return get(d) != 0;
    }

    public UrbanParams set(Def d, int v) {
        values.put(d.key(), d.clamp(v));
        return this;
    }

    public String name() {
        return name;
    }

    public UrbanParams name(String n) {
        name = n == null ? "" : n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n;
        return this;
    }

    public long seed() {
        return seed;
    }

    public UrbanParams seed(long s) {
        seed = s;
        return this;
    }

    public NationStyle style() {
        return NationStyle.values()[Math.max(0, Math.min(NationStyle.values().length - 1, get(STYLE)))];
    }

    public Map<Integer, Integer> lotWards() {
        return Collections.unmodifiableMap(lotWards);
    }

    public Set<Integer> removed() {
        return Collections.unmodifiableSet(removed);
    }

    public Map<Integer, Integer> rerolls() {
        return Collections.unmodifiableMap(rerolls);
    }

    /** Ce qu'on a corrigé à la main ne vaut que pour ce tracé : on l'oublie dès que le tracé change. */
    public void clearEdits() {
        lotWards.clear();
        removed.clear();
        rerolls.clear();
    }

    public void setLotWard(int lot, int ward) {
        lotWards.put(lot, ward);
    }

    public void toggleRemoved(int slot) {
        if (!removed.remove(slot)) removed.add(slot);
    }

    public void reroll(int slot) {
        rerolls.merge(slot, 1, Integer::sum);
    }

    public UrbanParams copy() {
        return load(save());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putLong("seed", seed);
        CompoundTag v = new CompoundTag();
        values.forEach(v::putInt);
        t.put("values", v);
        if (!lotWards.isEmpty()) {
            int[] flat = new int[lotWards.size() * 2];
            int i = 0;
            for (Map.Entry<Integer, Integer> e : lotWards.entrySet()) {
                flat[i++] = e.getKey();
                flat[i++] = e.getValue();
            }
            t.putIntArray("lots", flat);
        }
        if (!removed.isEmpty()) t.putIntArray("removed", removed.stream().mapToInt(Integer::intValue).toArray());
        if (!rerolls.isEmpty()) {
            int[] flat = new int[rerolls.size() * 2];
            int i = 0;
            for (Map.Entry<Integer, Integer> e : rerolls.entrySet()) {
                flat[i++] = e.getKey();
                flat[i++] = e.getValue();
            }
            t.putIntArray("rerolls", flat);
        }
        return t;
    }

    /** Lit des réglages, d'où qu'ils viennent : tout est borné, un réglage inconnu est ignoré (le serveur ne fait confiance à personne). */
    public static UrbanParams load(CompoundTag t) {
        UrbanParams p = new UrbanParams();
        p.name(t.getString("name"));
        p.seed = t.contains("seed", Tag.TAG_LONG) ? t.getLong("seed") : 1;
        CompoundTag v = t.getCompound("values");
        for (String k : v.getAllKeys()) {
            Def d = ALL.get(k);
            if (d != null) p.values.put(k, d.clamp(v.getInt(k)));
        }
        int[] lots = t.getIntArray("lots");
        for (int i = 0; i + 1 < lots.length && i < 4000; i += 2) p.lotWards.put(lots[i], Math.floorMod(lots[i + 1], UrbanWard.values().length));
        int[] gone = t.getIntArray("removed");
        for (int i = 0; i < gone.length && i < 4000; i++) p.removed.add(gone[i]);
        int[] again = t.getIntArray("rerolls");
        for (int i = 0; i + 1 < again.length && i < 4000; i += 2) p.rerolls.put(again[i], again[i + 1]);
        return p;
    }

    /** Un modèle de tracé règle d'un coup ce qui fait sa forme. « Personnalisé » ne touche à rien. */
    public void applyModel(int model) {
        set(MODELE, model);
        switch (model) {
            case 0 -> values(RESEAU, 0, ANGLE, 12, SEGMENT, 14, BRANCHE, 2, NOUVELLE_ROUTE, 20, ROUTES, 0);
            case 1 -> values(RESEAU, 0, ANGLE, 28, SEGMENT, 9, BRANCHE, 2, NOUVELLE_ROUTE, 35, ROUTES, 0);
            case 2 -> values(RESEAU, 2, ANGLE, 0, SEGMENT, 16, BRANCHE, 2, NOUVELLE_ROUTE, 0, ROUTES, 4, FORME_MUR, 0);
            case 3 -> values(RESEAU, 1, ANGLE, 6, SEGMENT, 12, BRANCHE, 3, NOUVELLE_ROUTE, 10, ROUTES, 5, FORME_MUR, 2);
            case 4 -> {
                values(RESEAU, 1, ANGLE, 6, SEGMENT, 12, BRANCHE, 3, NOUVELLE_ROUTE, 10, ROUTES, 5, FORME_MUR, 2);
                set(FAUBOURGS, 0);
            }
            default -> {
                // Personnalisé : on garde tout.
            }
        }
    }

    private void values(Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) set((Def) pairs[i], (Integer) pairs[i + 1]);
    }

    /** Vrai si passer de ce réglage à un autre change le tracé : les retouches à la main n'ont alors plus de sens. */
    public static boolean touchesLayout(Def d) {
        return d.group() != Group.GENERAL || d == HABITANTS || d == PAR_MAISON;
    }
}
