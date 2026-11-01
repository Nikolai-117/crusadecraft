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
        FETE, DEUIL, GUERRE, COUR;

        static Moment byName(String name) {
            return Moment.valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
    }

    /** Un lot de répliques et à qui il convient. Des ensembles vides : tout le monde. */
    public record Entry(Moment moment, Set<Role> roles, Set<Trait> traits, List<Component> lines) {
        boolean fits(Role role, List<Trait> held) {
            if (!roles.isEmpty() && !roles.contains(role)) return false;
            if (traits.isEmpty()) return true;
            for (Trait t : held) if (traits.contains(t)) return true;
            return false;
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
        return new Entry(moment, roles, traits, lines);
    }

    /**
     * Une réplique pour quelqu'un de ce rôle, avec ces traits, à ce moment. Les lots réservés (à un rôle ou à des
     * traits) passent avant les lots pour tous, quand il y en a qui conviennent.
     */
    public static Component pick(List<Entry> from, Moment moment, Role role, List<Trait> traits, Random random) {
        List<Component> specific = new ArrayList<>(), general = new ArrayList<>();
        for (Entry e : from) {
            if (e.moment() != moment || !e.fits(role, traits)) continue;
            (e.roles().isEmpty() && e.traits().isEmpty() ? general : specific).addAll(e.lines());
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
