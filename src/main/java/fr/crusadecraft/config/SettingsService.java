package fr.crusadecraft.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * L'écran de réglages en jeu (P30) : tous les réglages du fichier du monde, rangés par section, lus tels que Forge les
 * décrit (commentaire, bornes). Oui/non, nombres et choix se modifient depuis l'écran ; les listes restent dans le
 * fichier. Réservé aux opérateurs et au propriétaire d'un monde solo. Chaque changement est revérifié par les bornes
 * du réglage, enregistré dans le fichier, et pris en compte aussitôt.
 */
public final class SettingsService {
    private SettingsService() {}

    /** Le genre d'un réglage, pour l'écran. */
    public enum Kind { BOOL, INT, DOUBLE, ENUM, TEXT, LIST }

    /**
     * Un réglage : son chemin (« guerre.ostesIA »), son genre, sa valeur, ses bornes (nombres) ou ses choix
     * (énumérations), et son commentaire.
     */
    public record Entry(String path, Kind kind, String value, String min, String max, List<String> choices, String comment) {}

    public record View(boolean allowed, List<Entry> entries) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(allowed);
            buf.writeVarInt(entries.size());
            for (Entry e : entries) {
                buf.writeUtf(e.path());
                buf.writeEnum(e.kind());
                buf.writeUtf(e.value(), 4096);
                buf.writeUtf(e.min());
                buf.writeUtf(e.max());
                buf.writeVarInt(e.choices().size());
                for (String c : e.choices()) buf.writeUtf(c);
                buf.writeUtf(e.comment(), 4096);
            }
        }

        public static View read(FriendlyByteBuf buf) {
            boolean allowed = buf.readBoolean();
            int n = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String path = buf.readUtf();
                Kind kind = buf.readEnum(Kind.class);
                String value = buf.readUtf(4096), min = buf.readUtf(), max = buf.readUtf();
                int m = buf.readVarInt();
                List<String> choices = new ArrayList<>(m);
                for (int j = 0; j < m; j++) choices.add(buf.readUtf());
                entries.add(new Entry(path, kind, value, min, max, choices, buf.readUtf(4096)));
            }
            return new View(allowed, entries);
        }
    }

    private static final java.util.Set<java.util.UUID> TEST_GRANTS = new java.util.HashSet<>();

    /** Banc d'essai : le serveur des tests ne donne pas les droits d'opérateur. */
    public static void grantForTests(java.util.UUID player, boolean granted) {
        if (granted) TEST_GRANTS.add(player);
        else TEST_GRANTS.remove(player);
    }

    /** Qui peut régler : un opérateur, ou le propriétaire d'un monde solo (même sans les commandes). */
    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile()) || TEST_GRANTS.contains(player.getUUID());
    }

    /** Tous les réglages, dans l'ordre du fichier. */
    public static View view(ServerPlayer player) {
        List<Entry> out = new ArrayList<>();
        if (allowed(player)) collect(CrusadeConfig.SERVER_SPEC.getValues(), CrusadeConfig.SERVER_SPEC.getSpec(), "", out);
        return new View(allowed(player), out);
    }

    private static void collect(UnmodifiableConfig values, UnmodifiableConfig specs, String prefix, List<Entry> out) {
        for (Map.Entry<String, Object> e : values.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object spec = specs.valueMap().get(e.getKey());
            if (e.getValue() instanceof UnmodifiableConfig sub && spec instanceof UnmodifiableConfig subSpec) {
                collect(sub, subSpec, path, out);
            } else if (e.getValue() instanceof ForgeConfigSpec.ConfigValue<?> cv && spec instanceof ForgeConfigSpec.ValueSpec vs) {
                out.add(entry(path, cv, vs));
            }
        }
    }

    static Entry entry(String path, ForgeConfigSpec.ConfigValue<?> cv, ForgeConfigSpec.ValueSpec vs) {
        Object v = cv.get();
        String comment = vs.getComment() == null ? "" : vs.getComment().strip();
        ForgeConfigSpec.Range<?> range = vs.getRange();
        String min = range == null ? "" : String.valueOf(range.getMin()), max = range == null ? "" : String.valueOf(range.getMax());
        if (cv instanceof ForgeConfigSpec.BooleanValue) return new Entry(path, Kind.BOOL, String.valueOf(v), "", "", List.of(), comment);
        if (cv instanceof ForgeConfigSpec.IntValue || cv instanceof ForgeConfigSpec.LongValue) {
            return new Entry(path, Kind.INT, String.valueOf(v), min, max, List.of(), comment);
        }
        if (cv instanceof ForgeConfigSpec.DoubleValue) return new Entry(path, Kind.DOUBLE, String.valueOf(v), min, max, List.of(), comment);
        if (cv instanceof ForgeConfigSpec.EnumValue<?> && v instanceof Enum<?> en) {
            List<String> choices = new ArrayList<>();
            for (Object c : en.getDeclaringClass().getEnumConstants()) choices.add(((Enum<?>) c).name());
            return new Entry(path, Kind.ENUM, en.name(), "", "", choices, comment);
        }
        if (v instanceof List<?> list) return new Entry(path, Kind.LIST, String.join(", ", list.stream().map(String::valueOf).toList()), "", "", List.of(), comment);
        return new Entry(path, Kind.TEXT, String.valueOf(v), "", "", List.of(), comment);
    }

    /** Trouve un réglage par son chemin. */
    static ForgeConfigSpec.ConfigValue<?> value(String path) {
        Object o = CrusadeConfig.SERVER_SPEC.getValues().get(path);
        return o instanceof ForgeConfigSpec.ConfigValue<?> cv ? cv : null;
    }

    static ForgeConfigSpec.ValueSpec spec(String path) {
        Object o = CrusadeConfig.SERVER_SPEC.getSpec().get(path);
        return o instanceof ForgeConfigSpec.ValueSpec vs ? vs : null;
    }

    /** « hommesParSoldat » devient « Hommes par soldat », « ostesIA » devient « Ostes IA ». */
    public static String humanize(String key) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean cut = Character.isUpperCase(c) && i > 0
                    && (Character.isLowerCase(key.charAt(i - 1)) || (i + 1 < key.length() && Character.isLowerCase(key.charAt(i + 1))));
            if (cut && word.length() > 0) {
                words.add(word.toString());
                word.setLength(0);
            }
            word.append(c);
        }
        if (word.length() > 0) words.add(word.toString());
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            boolean acronym = w.length() > 1 && w.chars().allMatch(Character::isUpperCase);
            if (out.length() > 0) out.append(' ');
            String lower = w.toLowerCase(java.util.Locale.ROOT);
            out.append(acronym ? w : ACCENTS.getOrDefault(lower, lower));
        }
        return out.isEmpty() ? key : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    /** Les mots des clés du fichier, écrits sans accents, tels qu'on les lit. */
    private static final Map<String, String> ACCENTS = Map.ofEntries(
            Map.entry("hotel", "hôtel"), Map.entry("trone", "trône"), Map.entry("depart", "départ"), Map.entry("regles", "règles"),
            Map.entry("quantite", "quantité"), Map.entry("recompense", "récompense"), Map.entry("duree", "durée"), Map.entry("reunion", "réunion"),
            Map.entry("deplacements", "déplacements"), Map.entry("chomeurs", "chômeurs"), Map.entry("affames", "affamés"),
            Map.entry("requetes", "requêtes"), Map.entry("bareme", "barème"), Map.entry("impot", "impôt"), Map.entry("impots", "impôts"),
            Map.entry("etat", "état"), Map.entry("autorises", "autorisés"), Map.entry("autorise", "autorisé"), Map.entry("delai", "délai"),
            Map.entry("decouverte", "découverte"), Map.entry("heraut", "héraut"), Map.entry("herauts", "hérauts"),
            Map.entry("independantes", "indépendantes"), Map.entry("editeur", "éditeur"), Map.entry("cout", "coût"), Map.entry("entree", "entrée"),
            Map.entry("allies", "alliés"), Map.entry("operateurs", "opérateurs"), Map.entry("tresor", "trésor"), Map.entry("armees", "armées"),
            Map.entry("opacite", "opacité"), Map.entry("penalite", "pénalité"), Map.entry("levees", "levées"), Map.entry("repliques", "répliques"),
            Map.entry("treve", "trêve"), Map.entry("siege", "siège"), Map.entry("sieges", "sièges"), Map.entry("decisions", "décisions"),
            Map.entry("repit", "répit"), Map.entry("portee", "portée"), Map.entry("evenement", "événement"), Map.entry("evenements", "événements"),
            Map.entry("revoltes", "révoltes"), Map.entry("etape", "étape"), Map.entry("pnj", "PNJ"), Map.entry("xp", "XP"),
            Map.entry("hundred", "Hundred"), Map.entry("years", "Years"), Map.entry("war", "War"), Map.entry("n", "'N'"),
            Map.entry("taxes", "Taxes"), Map.entry("nobility", "Nobility"), Map.entry("dynmap", "Dynmap"), Map.entry("xaero", "Xaero"));

    /** Lit le texte saisi selon le genre du réglage ; {@code null} s'il est illisible. Logique pure. */
    public static Object parse(Kind kind, String text, List<String> choices) {
        String t = text == null ? "" : text.trim();
        try {
            return switch (kind) {
                case BOOL -> t.equalsIgnoreCase("true") || t.equalsIgnoreCase("oui") ? Boolean.TRUE
                        : t.equalsIgnoreCase("false") || t.equalsIgnoreCase("non") ? Boolean.FALSE : null;
                case INT -> Integer.parseInt(t);
                case DOUBLE -> Double.parseDouble(t.replace(',', '.'));
                case ENUM -> choices.contains(t) ? t : null;
                case TEXT -> t;
                case LIST -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Change un réglage, s'il respecte ses bornes ; l'enregistre dans le fichier du monde. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Outcome set(ServerPlayer player, String path, String text) {
        if (!allowed(player)) return Outcome.no("crusadecraft.settings.denied");
        ForgeConfigSpec.ConfigValue cv = value(path);
        ForgeConfigSpec.ValueSpec vs = spec(path);
        if (cv == null || vs == null) return Outcome.no("crusadecraft.settings.unknown", path);
        Entry e = entry(path, cv, vs);
        if (e.kind() == Kind.LIST) return Outcome.no("crusadecraft.settings.list");
        Object parsed = parse(e.kind(), text, e.choices());
        if (parsed instanceof String s && e.kind() == Kind.ENUM) {
            parsed = Enum.valueOf((Class) ((Enum<?>) cv.get()).getDeclaringClass(), s);
        }
        if (parsed instanceof Integer i && cv instanceof ForgeConfigSpec.LongValue) parsed = (long) i;
        if (parsed == null || !vs.test(parsed)) return Outcome.no("crusadecraft.settings.invalid", path, e.min(), e.max());
        cv.set(parsed);
        CrusadeConfig.SERVER_SPEC.save();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} règle {} = {}", player.getGameProfile().getName(), path, parsed);
        return Outcome.ok(Component.translatable("crusadecraft.settings.saved", path, String.valueOf(parsed)));
    }
}
