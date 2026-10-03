package fr.crusadecraft.core.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Un événement à choix, lu dans un datapack ({@code data/<mod>/crusade_events/<nom>.json}). Il peut surgir au
 * hasard ({@code trigger: "random"}) ou être appelé par une situation précise (ultimatum d'une faction, complot
 * éventé, révolte). Les textes sont des clés de traduction, ou du texte brut ; {@code %1$s} y désigne le
 * personnage en cause, {@code %2$s} le lieu, {@code %3$s} le nom du royaume.
 */
public record EventDef(ResourceLocation id, String trigger, int weight, int cooldown, String title, String text, ResourceLocation icon,
                       Conditions conditions, List<Option> options, int defaultOption, int expires) {

    public static final String RANDOM = "random";

    /** Ce qu'il faut pour que l'événement surgisse, ou qu'une option soit possible. Tout est facultatif. */
    public record Conditions(Integer minCitizens, Long minGold, Long maxGold, Integer minPrestige, Boolean atWar, Post hasPost,
                             TitleRank minRank, Integer minAge, Double minHappiness, Double maxHappiness, Map<Law, Integer> minLaw,
                             Map<Law, Integer> maxLaw, Integer minDread, Integer minPiety) {
        public static final Conditions NONE = new Conditions(null, null, null, null, null, null, null, null, null, null, Map.of(), Map.of(),
                null, null);

        public boolean test(EventContext c) {
            if (minCitizens != null && c.citizens() < minCitizens) return false;
            if (minGold != null && c.gold() < minGold) return false;
            if (maxGold != null && c.gold() > maxGold) return false;
            if (minPrestige != null && c.prestige() < minPrestige) return false;
            if (minDread != null && c.dread() < minDread) return false;
            if (minPiety != null && c.piety() < minPiety) return false;
            if (atWar != null && c.atWar() != atWar) return false;
            if (hasPost != null && !c.posts().contains(hasPost)) return false;
            if (minRank != null && !c.rank().isAtLeast(minRank)) return false;
            if (minAge != null && c.age() < minAge) return false;
            if (minHappiness != null && c.happiness() < minHappiness) return false;
            if (maxHappiness != null && c.happiness() > maxHappiness) return false;
            for (Map.Entry<Law, Integer> e : minLaw.entrySet()) if (c.laws().getOrDefault(e.getKey(), 0) < e.getValue()) return false;
            for (Map.Entry<Law, Integer> e : maxLaw.entrySet()) if (c.laws().getOrDefault(e.getKey(), 0) > e.getValue()) return false;
            return true;
        }

        static Conditions parse(JsonObject o) {
            if (o == null) return NONE;
            return new Conditions(optInt(o, "min_citizens"), optLong(o, "min_gold"), optLong(o, "max_gold"), optInt(o, "min_prestige"),
                    o.has("at_war") ? GsonHelper.getAsBoolean(o, "at_war") : null,
                    o.has("has_post") ? Post.byName(GsonHelper.getAsString(o, "has_post").toUpperCase(Locale.ROOT)) : null,
                    o.has("min_rank") ? TitleRank.byName(GsonHelper.getAsString(o, "min_rank").toUpperCase(Locale.ROOT), null) : null,
                    optInt(o, "min_age"), optDouble(o, "min_happiness"), optDouble(o, "max_happiness"),
                    laws(o, "min_law"), laws(o, "max_law"), optInt(o, "min_dread"), optInt(o, "min_piety"));
        }
    }

    /** Ce qu'il faut savoir du royaume pour juger des conditions. */
    public record EventContext(int citizens, long gold, int prestige, int dread, int piety, boolean atWar, Set<Post> posts,
                               TitleRank rank, long age, double happiness, Map<Law, Integer> laws) {}

    /**
     * Les effets d'une option. {@code army} est un pourcentage des hommes ; les opinions s'appliquent au conseil
     * entier, à un poste, ou au personnage en cause ; {@code faction} et {@code plot} règlent la situation qui a
     * appelé l'événement (céder, refuser, pardonner, emprisonner…).
     */
    public record Effects(int gold, int prestige, int piety, int dread, int threat, int army, int councilOpinion, Post post,
                          int postOpinion, int subjectOpinion, CharacterStatus subjectStatus, int statusDays, Trait subjectTrait,
                          Law lawDown, String faction, String plot, int revoltDays, String chronicle) {
        public static final Effects NONE = new Effects(0, 0, 0, 0, 0, 0, 0, null, 0, 0, null, 0, null, null, null, null, 0, null);

        static Effects parse(JsonObject o) {
            if (o == null) return NONE;
            Post post = null;
            int postOpinion = 0;
            if (o.has("post_opinion")) {
                JsonObject p = GsonHelper.getAsJsonObject(o, "post_opinion");
                post = Post.byName(GsonHelper.getAsString(p, "post").toUpperCase(Locale.ROOT));
                postOpinion = GsonHelper.getAsInt(p, "value");
            }
            CharacterStatus status = null;
            int days = 0;
            if (o.has("subject_status")) {
                String[] parts = GsonHelper.getAsString(o, "subject_status").split(":");
                status = CharacterStatus.byName(parts[0].toUpperCase(Locale.ROOT));
                days = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 20;
            }
            Trait trait = null;
            if (o.has("subject_trait")) {
                try {
                    trait = Trait.valueOf(GsonHelper.getAsString(o, "subject_trait").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // trait inconnu : ignoré
                }
            }
            Law law = null;
            if (o.has("law_down")) {
                try {
                    law = Law.valueOf(GsonHelper.getAsString(o, "law_down").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // loi inconnue : ignorée
                }
            }
            return new Effects(GsonHelper.getAsInt(o, "gold", 0), GsonHelper.getAsInt(o, "prestige", 0), GsonHelper.getAsInt(o, "piety", 0),
                    GsonHelper.getAsInt(o, "dread", 0), GsonHelper.getAsInt(o, "threat", 0), GsonHelper.getAsInt(o, "army", 0),
                    GsonHelper.getAsInt(o, "council_opinion", 0), post, postOpinion, GsonHelper.getAsInt(o, "subject_opinion", 0), status, days,
                    trait, law, o.has("faction") ? GsonHelper.getAsString(o, "faction") : null,
                    o.has("plot") ? GsonHelper.getAsString(o, "plot") : null, GsonHelper.getAsInt(o, "revolt_days", 0),
                    o.has("chronicle") ? GsonHelper.getAsString(o, "chronicle") : null);
        }

        /** Les effets lisibles, pour l'infobulle d'une option : clé de libellé et valeur signée. */
        public List<Line> lines() {
            List<Line> out = new ArrayList<>();
            add(out, "crusadecraft.event.fx.gold", gold);
            add(out, "crusadecraft.event.fx.prestige", prestige);
            add(out, "crusadecraft.event.fx.piety", piety);
            add(out, "crusadecraft.event.fx.dread", dread);
            add(out, "crusadecraft.event.fx.threat", threat);
            add(out, "crusadecraft.event.fx.army", army);
            add(out, "crusadecraft.event.fx.council", councilOpinion);
            if (post != null && postOpinion != 0) out.add(new Line("crusadecraft.event.fx.post", post.key(false), postOpinion));
            add(out, "crusadecraft.event.fx.subject", subjectOpinion);
            if (subjectStatus != null) out.add(new Line("crusadecraft.event.fx.status", subjectStatus.key(), statusDays));
            if (subjectTrait != null) out.add(new Line("crusadecraft.event.fx.trait", subjectTrait.key(), 0));
            if (lawDown != null) out.add(new Line("crusadecraft.event.fx.law_down", lawDown.key(), 0));
            add(out, "crusadecraft.event.fx.revolt", revoltDays);
            return out;
        }

        private static void add(List<Line> out, String key, int v) {
            if (v != 0) out.add(new Line(key, null, v));
        }
    }

    /** Une ligne d'effet : libellé, argument traduisible éventuel, valeur. */
    public record Line(String key, String arg, int value) {}

    public record Option(String text, Conditions requires, Effects effects) {}

    // ------------------------------------------------------------------ lecture

    public static EventDef parse(ResourceLocation id, JsonObject o) {
        List<Option> options = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(o, "options");
        for (JsonElement e : arr) {
            JsonObject opt = e.getAsJsonObject();
            options.add(new Option(GsonHelper.getAsString(opt, "text"),
                    Conditions.parse(opt.has("requires") ? GsonHelper.getAsJsonObject(opt, "requires") : null),
                    Effects.parse(opt.has("effects") ? GsonHelper.getAsJsonObject(opt, "effects") : null)));
        }
        if (options.isEmpty()) throw new IllegalArgumentException("aucune option");
        int def = Math.max(0, Math.min(options.size() - 1, GsonHelper.getAsInt(o, "default", 0)));
        ResourceLocation icon = ResourceLocation.tryParse(GsonHelper.getAsString(o, "icon", "minecraft:book"));
        return new EventDef(id, GsonHelper.getAsString(o, "trigger", RANDOM), Math.max(0, GsonHelper.getAsInt(o, "weight", 10)),
                Math.max(0, GsonHelper.getAsInt(o, "cooldown", 20)), GsonHelper.getAsString(o, "title"), GsonHelper.getAsString(o, "text"),
                icon == null ? ResourceLocation.tryParse("minecraft:book") : icon,
                Conditions.parse(o.has("conditions") ? GsonHelper.getAsJsonObject(o, "conditions") : null), List.copyOf(options), def,
                Math.max(1, GsonHelper.getAsInt(o, "expires", 3)));
    }

    private static Integer optInt(JsonObject o, String k) {
        return o.has(k) ? GsonHelper.getAsInt(o, k) : null;
    }

    private static Long optLong(JsonObject o, String k) {
        return o.has(k) ? GsonHelper.getAsLong(o, k) : null;
    }

    private static Double optDouble(JsonObject o, String k) {
        return o.has(k) ? GsonHelper.getAsDouble(o, k) : null;
    }

    private static Map<Law, Integer> laws(JsonObject o, String k) {
        if (!o.has(k)) return Map.of();
        Map<Law, Integer> out = new EnumMap<>(Law.class);
        JsonObject l = GsonHelper.getAsJsonObject(o, k);
        for (String name : l.keySet()) {
            try {
                out.put(Law.valueOf(name.toUpperCase(Locale.ROOT)), GsonHelper.getAsInt(l, name));
            } catch (IllegalArgumentException ignored) {
                // loi inconnue : ignorée
            }
        }
        return out;
    }
}
