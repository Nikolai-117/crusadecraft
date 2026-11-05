package fr.crusadecraft.core.lineage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.character.Trait;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Les répliques des gens du registre (P26), lues dans les datapacks ({@code data/<espace>/crusadecraft_repliques/*.json})
 * au démarrage et à chaque {@code /reload}. Un fichier dit à quel moment ses répliques servent ({@code fete},
 * {@code deuil}, {@code guerre}, {@code cour}), et peut les réserver à certains rôles ou à ceux qui portent un des
 * traits donnés. Une réplique est un texte, ou {@code {"key": "..."}} pour une clé de traduction.
 *
 * <pre>{@code
 * { "moment": "fete", "roles": ["SOUVERAIN"], "traits": ["GENEREUX"], "lines": ["À la santé du royaume !"] }
 * }</pre>
 */
public final class Repliques extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    /** Le moment où l'on parle. */
    public enum Moment {
        FETE, DEUIL, GUERRE, COUR,
        /** P41 : ce qu'écrivent les souverains à une nation. */
        LETTRE_OFFRE, LETTRE_PRESENT, LETTRE_MERCI, LETTRE_OUTRAGE, LETTRE_GUERRE, LETTRE_ACCEPTE, LETTRE_REFUS,
        /** P42 : ce que disent les habitants d'une ville. */
        ACCUEIL, TRAVAIL, METIER, VILLE, ROYAUME, RUMEUR, RUMEUR_VRAIE, FAMILLE, AVIS, ADIEU, MATIN, MIDI, SOIR, NUIT, PLUIE, ORAGE, DOULEUR, MERCI, PENURIE, REPAS, MARCHE, LIVRAISON,
        /** P42 : de quoi composer la vie d'un habitant. */
        VIE_ORIGINE, VIE_METIER, VIE_TIC,
        /** P42 : les plus petits ne répondent pas comme les grands. */
        ENFANT, BEBE, ECOLE,
        /** P45 : quand la cloche sonne. */
        CLOCHE,
        /** P48 : un voyageur, et P49 : ce qu'ils écrivent aux joueurs. */
        VOYAGE, MESSAGE_AMI, MESSAGE_COMMANDE, MESSAGE_MERCI, MESSAGE_PLAINTE, MESSAGE_VOYAGE, MESSAGE_VENTE,
        /** P50 : deux habitants, face à face. */
        ECHANGE_DEMANDE, ECHANGE_REPONSE, REMISE_LIVRE, REMISE_RECOIT, GROS_VEND, GROS_ACHETE, CAUSETTE, CAUSETTE_REPONSE;

        static Moment byName(String name) {
            return Moment.valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** Un lot de répliques et à qui il convient. Des ensembles vides : tout le monde. */
    public record Entry(Moment moment, Set<Role> roles, Set<Trait> traits, List<Component> lines, Filter filter) {
        public Entry(Moment moment, Set<Role> roles, Set<Trait> traits, List<Component> lines) {
            this(moment, roles, traits, lines, Filter.ANY);
        }

        boolean fits(Role role, List<Trait> held) {
            return fits(new Who(role, held, "", "", "", -1, null, 0));
        }

        boolean fits(Who who) {
            if (!roles.isEmpty() && !roles.contains(who.role())) return false;
            if (!filter.fits(who)) return false;
            if (traits.isEmpty()) return true;
            for (Trait t : who.traits()) if (traits.contains(t)) return true;
            return false;
        }

        /** Un lot pour tout le monde ? Les autres passent avant lui, quand il y en a qui conviennent. */
        boolean general() {
            return roles.isEmpty() && traits.isEmpty() && filter == Filter.ANY;
        }
    }

    /** P42 : qui parle : son rôle, ses traits, son métier, sa nation, sa ville, son âge, son sexe, ce qu'il pense de celui qui écoute. */
    public record Who(Role role, List<Trait> traits, String job, String nation, String city, int age, Boolean female, int opinion) {}

    /**
     * P42 : d'autres façons de réserver un lot, toutes facultatives : à certains métiers ({@code metiers}), nations
     * ({@code nations}), villes ({@code villes}), à un âge ({@code age_min}, {@code age_max}), à un sexe
     * ({@code femme}), à une opinion de celui qui écoute ({@code avis_min}, {@code avis_max}).
     */
    public record Filter(Set<String> jobs, Set<String> nations, Set<String> cities, int minAge, int maxAge, Boolean female, int minOpinion, int maxOpinion) {
        public static final Filter ANY = new Filter(Set.of(), Set.of(), Set.of(), Integer.MIN_VALUE, Integer.MAX_VALUE, null, Integer.MIN_VALUE, Integer.MAX_VALUE);

        boolean fits(Who w) {
            if (this == ANY) return true;
            if (!jobs.isEmpty() && !jobs.contains(w.job().toUpperCase(Locale.ROOT))) return false;
            if (!nations.isEmpty() && !nations.contains(w.nation().toLowerCase(Locale.ROOT))) return false;
            if (!cities.isEmpty() && !cities.contains(w.city().toLowerCase(Locale.ROOT))) return false;
            if (w.age() < minAge || w.age() > maxAge) return false;
            if (female != null && !female.equals(w.female())) return false;
            return w.opinion() >= minOpinion && w.opinion() <= maxOpinion;
        }

        static Filter parse(JsonObject o) {
            Set<String> jobs = strings(o, "metiers", true), nations = strings(o, "nations", false), cities = strings(o, "villes", false);
            int minAge = o.has("age_min") ? o.get("age_min").getAsInt() : Integer.MIN_VALUE, maxAge = o.has("age_max") ? o.get("age_max").getAsInt() : Integer.MAX_VALUE;
            Boolean female = o.has("femme") ? o.get("femme").getAsBoolean() : null;
            int minOp = o.has("avis_min") ? o.get("avis_min").getAsInt() : Integer.MIN_VALUE, maxOp = o.has("avis_max") ? o.get("avis_max").getAsInt() : Integer.MAX_VALUE;
            if (jobs.isEmpty() && nations.isEmpty() && cities.isEmpty() && minAge == Integer.MIN_VALUE && maxAge == Integer.MAX_VALUE && female == null
                    && minOp == Integer.MIN_VALUE && maxOp == Integer.MAX_VALUE) return ANY;
            return new Filter(jobs, nations, cities, minAge, maxAge, female, minOp, maxOp);
        }

        private static Set<String> strings(JsonObject o, String key, boolean upper) {
            Set<String> out = new java.util.HashSet<>();
            if (o.has(key)) {
                for (JsonElement e : o.getAsJsonArray(key)) {
                    String s = e.getAsString().strip();
                    out.add(upper ? s.toUpperCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }
    }

    private static volatile List<Entry> entries = List.of();

    public Repliques() {
        super(GSON, "crusadecraft_repliques");
    }

    public static List<Entry> entries() {
        return entries;
    }

    /** Pour les bancs d'essai : remplace les répliques chargées. */
    public static void setForTests(List<Entry> list) {
        entries = List.copyOf(list);
    }

    /** Lit un fichier ; une erreur le rejette en entier. */
    public static Entry parse(JsonObject o) {
        Moment moment = Moment.byName(o.get("moment").getAsString());
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (o.has("roles")) for (JsonElement r : o.getAsJsonArray("roles")) roles.add(Role.valueOf(r.getAsString().trim().toUpperCase(Locale.ROOT)));
        Set<Trait> traits = EnumSet.noneOf(Trait.class);
        if (o.has("traits")) for (JsonElement t : o.getAsJsonArray("traits")) traits.add(Trait.valueOf(t.getAsString().trim().toUpperCase(Locale.ROOT)));
        List<Component> lines = new ArrayList<>();
        for (JsonElement l : o.getAsJsonArray("lines")) {
            if (l.isJsonObject()) lines.add(Component.translatable(l.getAsJsonObject().get("key").getAsString()));
            else if (!l.getAsString().isBlank()) lines.add(Component.literal(l.getAsString().strip()));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("aucune réplique");
        return new Entry(moment, roles, traits, lines, Filter.parse(o));
    }

    /**
     * Une réplique pour quelqu'un de ce rôle, avec ces traits, à ce moment. Les lots réservés (à un rôle ou à des
     * traits) passent avant les lots pour tous, quand il y en a qui conviennent.
     */
    public static Component pick(List<Entry> from, Moment moment, Role role, List<Trait> traits, Random random) {
        return pick(from, moment, new Who(role, traits, "", "", "", -1, null, 0), random);
    }

    /** P42 : la même chose, pour quelqu'un qu'on connaît mieux. */
    public static Component pick(List<Entry> from, Moment moment, Who who, Random random) {
        List<Component> specific = new ArrayList<>(), general = new ArrayList<>();
        for (Entry e : from) {
            if (e.moment() != moment || !e.fits(who)) continue;
            (e.general() ? general : specific).addAll(e.lines());
        }
        List<Component> pool = !specific.isEmpty() && (general.isEmpty() || random.nextBoolean()) ? specific : general;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                loaded.add(parse(e.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : répliques {} illisibles, ignorées : {}", e.getKey(), ex.getMessage());
            }
        }
        entries = List.copyOf(loaded);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} lot(s) de répliques chargé(s)", loaded.size());
    }
}
