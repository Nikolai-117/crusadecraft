package fr.crusadecraft.core.nation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Une nation écrite d'avance (P41) : un royaume qui existe dès le départ, avec ce qu'on sait de lui. C'est un simple
 * fichier JSON, dans {@code crusadecraft/nations/} : le joueur le corrige comme il veut, et une nouvelle version du mod
 * ne l'écrase jamais.
 *
 * @param key     sa clé, stable (« levanzia »)
 * @param name    le nom de lieu qui lui donne son nom (« Levànzia » pour « Royaume de Levànzia »)
 * @param folder  le dossier de plans dont les villes lui reviennent (« Levenzia ») ; vide : aucun
 * @param player  c'est le royaume d'un joueur : le premier qui y pose une ville en prend la couronne
 * @param ties    ses liens avec les autres nations, signés à sa création
 * @param history son histoire, en paragraphes
 * @param cities  ce qu'on sait de ses villes, par nom
 */
public record NationDef(String key, String name, String folder, TitleRank rank, int color, AiPersonality personality, AiFaith faith,
                        boolean player, String house, Ruler ruler, List<Tie> ties, Culture culture, List<String> history,
                        Map<String, CityLore> cities) {

    /** Son souverain, pour un royaume de l'ordinateur. */
    public record Ruler(String name, boolean female, int age) {}

    /** Un lien avec une autre nation : les traités signés d'emblée, et leur amitié (en multiple de l'amitié ancienne). */
    public record Tie(String with, List<Treaty> treaties, double friendship) {}

    /** Sa culture : d'où ses habitants tirent leurs noms. Des listes vides : les noms ordinaires du mod. */
    public record Culture(String name, String faith, List<String> men, List<String> women, List<String> families) {
        public static final Culture NONE = new Culture("", "", List.of(), List.of(), List.of());
    }

    /** Ce qu'on sait d'une ville : son histoire, et les gens qu'on y connaît. */
    public record CityLore(List<String> history, List<Notable> notables) {}

    /**
     * Quelqu'un qu'on connaît dans une ville : il prend le premier poste de sa fonction (le boulanger de Dolhimluith
     * est toujours le même), avec son nom, ses traits, sa vie et ses répliques à lui.
     */
    public record Notable(BuildingFunction function, String first, String last, boolean female, int age, List<Trait> traits, String bio,
                          List<String> lines) {}

    /** Lit un fichier. Une erreur (clé absente, valeur inconnue) rejette le fichier en entier, avec un message. */
    public static NationDef parse(JsonObject o) {
        String key = o.get("cle").getAsString().strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) throw new IllegalArgumentException("clé vide");
        String name = o.get("nom").getAsString().strip();
        if (name.isEmpty()) throw new IllegalArgumentException("nom vide");
        TitleRank rank = TitleRank.byName(string(o, "rang", "ROYAUME").toUpperCase(Locale.ROOT), TitleRank.ROYAUME);
        int color = Integer.parseInt(string(o, "couleur", "7B2D8B").replace("#", ""), 16) & 0xFFFFFF;
        AiPersonality personality = AiPersonality.valueOf(string(o, "temperament", "OPPORTUNISTE").toUpperCase(Locale.ROOT));
        AiFaith faith = AiFaith.valueOf(string(o, "foi", "FIDELE").toUpperCase(Locale.ROOT));
        Ruler ruler = null;
        if (o.has("souverain")) {
            JsonObject r = o.getAsJsonObject("souverain");
            ruler = new Ruler(r.get("nom").getAsString().strip(), r.has("femme") && r.get("femme").getAsBoolean(), r.has("age") ? r.get("age").getAsInt() : 40);
        }
        List<Tie> ties = new ArrayList<>();
        if (o.has("liens")) {
            for (JsonElement e : o.getAsJsonArray("liens")) {
                JsonObject t = e.getAsJsonObject();
                List<Treaty> treaties = new ArrayList<>();
                if (t.has("traites")) for (JsonElement x : t.getAsJsonArray("traites")) treaties.add(Treaty.valueOf(x.getAsString().strip().toUpperCase(Locale.ROOT)));
                ties.add(new Tie(t.get("avec").getAsString().strip().toLowerCase(Locale.ROOT), treaties, t.has("amitie") ? t.get("amitie").getAsDouble() : 1));
            }
        }
        Culture culture = Culture.NONE;
        if (o.has("culture")) {
            JsonObject c = o.getAsJsonObject("culture");
            culture = new Culture(string(c, "nom", ""), string(c, "foi", ""), strings(c, "hommes"), strings(c, "femmes"), strings(c, "familles"));
        }
        Map<String, CityLore> cities = new LinkedHashMap<>();
        if (o.has("villes")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("villes").entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                List<Notable> notables = new ArrayList<>();
                if (c.has("notables")) for (JsonElement n : c.getAsJsonArray("notables")) notables.add(notable(n.getAsJsonObject()));
                cities.put(e.getKey().strip().toLowerCase(Locale.ROOT), new CityLore(strings(c, "histoire"), notables));
            }
        }
        return new NationDef(key, name, string(o, "dossier", ""), rank, color, personality, faith, o.has("joueur") && o.get("joueur").getAsBoolean(),
                string(o, "maison", name), ruler, ties, culture, strings(o, "histoire"), cities);
    }

    private static Notable notable(JsonObject n) {
        BuildingFunction f = BuildingFunction.byName(n.get("fonction").getAsString().strip().toUpperCase(Locale.ROOT));
        if (f == null) throw new IllegalArgumentException("fonction inconnue : " + n.get("fonction").getAsString());
        List<Trait> traits = new ArrayList<>();
        if (n.has("traits")) for (JsonElement t : n.getAsJsonArray("traits")) traits.add(Trait.valueOf(t.getAsString().strip().toUpperCase(Locale.ROOT)));
        return new Notable(f, n.get("prenom").getAsString().strip(), string(n, "nom", ""), n.has("femme") && n.get("femme").getAsBoolean(),
                n.has("age") ? n.get("age").getAsInt() : 35, traits, string(n, "vie", ""), strings(n, "repliques"));
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString().strip() : fallback;
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (!o.has(key)) return out;
        JsonArray a = o.getAsJsonArray(key);
        for (JsonElement e : a) if (!e.getAsString().isBlank()) out.add(e.getAsString().strip());
        return out;
    }

    /** Ce qu'on sait de cette ville, si on en sait quelque chose. */
    public CityLore city(String cityName) {
        return cityName == null ? null : cities.get(cityName.strip().toLowerCase(Locale.ROOT));
    }
}
