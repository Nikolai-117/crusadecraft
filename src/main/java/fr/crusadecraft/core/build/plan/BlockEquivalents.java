package fr.crusadecraft.core.build.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Les équivalences de blocs des plans venus d'ailleurs (P38) : une ville bâtie avec un mod qu'on n'a pas (Middle-earth),
 * ou dans une version plus récente de Minecraft, se pose quand même. Chaque bloc inconnu prend le premier bloc installé
 * de sa liste, <b>de même forme</b> : un escalier reste un escalier avec son orientation, une demi-dalle verticale
 * devient une plaque de Dawn of Time, un torchis à colombages devient un colombage de Domum Ornamentum avec le même
 * motif. Seule la matière change.
 *
 * <p>La table livrée ({@code data/crusadecraft/equivalences.json}) peut être complétée ou corrigée par
 * {@code crusadecraft/equivalences.json} dans le dossier du jeu, au même format. Une clé est un identifiant, un
 * identifiant avec des propriétés ({@code me:x[double=true]}, plus précis, l'emporte), ou un motif à une étoile
 * ({@code minecraft:pale_oak_*}, {@code *_stairs}) dont la part étoilée se reporte dans la cible.
 */
public final class BlockEquivalents {
    static final String BUILT_IN = "/data/crusadecraft/equivalences.json";
    /** Les clés de texture des colombages de Domum Ornamentum : le bois du cadre, puis le remplissage. */
    static final String FRAME_KEY = "minecraft:block/oak_planks", CENTER_KEY = "minecraft:block/dark_oak_planks";

    /**
     * Une règle.
     *
     * @param to     les blocs candidats, dans l'ordre : le premier installé l'emporte
     * @param flip   l'orientation d'origine est retournée (les demi-dalles verticales de Middle-earth regardent vers
     *               l'extérieur, les plaques de Dawn of Time vers le côté qu'elles occupent)
     * @param set    des propriétés imposées (des feuilles qui ne se fanent pas)
     * @param frame  le bois du cadre, pour un colombage de Domum Ornamentum
     * @param center le remplissage du colombage
     * @param orient le colombage se tourne selon le mur où il est pris
     */
    public record Rule(List<String> to, boolean flip, Map<String, String> set, String frame, String center, boolean orient) {}

    /** Une clé de la table : un identifiant (ou un motif), et les propriétés qu'il doit avoir. */
    public record Key(String id, Map<String, String> when) {
        boolean pattern() {
            return id.indexOf('*') >= 0;
        }
    }

    /** Ce que devient un bloc : son état, son identifiant, les données de son entité (un colombage), à orienter. */
    public record Result(BlockState state, String id, CompoundTag tag, boolean orient) {}

    private static volatile Map<Key, Rule> table;
    private static volatile long userStamp = Long.MIN_VALUE;

    private BlockEquivalents() {}

    // ------------------------------------------------------------------ lecture de la table

    /** La table livrée, plus celle du joueur par-dessus (relue si son fichier change). */
    static Map<Key, Rule> table() {
        Path user = userFile();
        long stamp = stamp(user);
        Map<Key, Rule> t = table;
        if (t != null && stamp == userStamp) return t;
        synchronized (BlockEquivalents.class) {
            if (table != null && stamp == userStamp) return table;
            Map<Key, Rule> merged = new LinkedHashMap<>();
            try (InputStream in = BlockEquivalents.class.getResourceAsStream(BUILT_IN)) {
                if (in != null) merged.putAll(parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (IOException | RuntimeException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : la table d'équivalences livrée ne se lit pas : {}", e.getMessage());
            }
            if (user != null && Files.isRegularFile(user)) {
                try (Reader r = Files.newBufferedReader(user, StandardCharsets.UTF_8)) {
                    merged.putAll(parse(r));
                } catch (IOException | RuntimeException e) {
                    CrusadeCraft.LOGGER.warn("CrusadeCraft : {} ne se lit pas : {}", user, e.getMessage());
                }
            }
            table = merged;
            userStamp = stamp;
            return merged;
        }
    }

    private static Path userFile() {
        try {
            return FMLPaths.GAMEDIR.get().resolve("crusadecraft").resolve("equivalences.json");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long stamp(Path p) {
        try {
            return p != null && Files.isRegularFile(p) ? Files.getLastModifiedTime(p).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /** Lit une table : chaque clé vers une cible, une liste de cibles, ou une règle détaillée. */
    static Map<Key, Rule> parse(Reader reader) {
        Map<Key, Rule> out = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            Rule rule = rule(e.getValue());
            if (rule != null && !rule.to().isEmpty()) out.put(key(e.getKey()), rule);
        }
        return out;
    }

    static Rule rule(JsonElement v) {
        if (v.isJsonPrimitive()) return new Rule(List.of(v.getAsString()), false, Map.of(), null, null, false);
        if (v.isJsonArray()) {
            List<String> to = new ArrayList<>();
            v.getAsJsonArray().forEach(x -> to.add(x.getAsString()));
            return new Rule(List.copyOf(to), false, Map.of(), null, null, false);
        }
        if (!v.isJsonObject()) return null;
        JsonObject o = v.getAsJsonObject();
        List<String> to = new ArrayList<>();
        JsonElement t = o.get("to");
        if (t != null && t.isJsonArray()) t.getAsJsonArray().forEach(x -> to.add(x.getAsString()));
        else if (t != null) to.add(t.getAsString());
        Map<String, String> set = new LinkedHashMap<>();
        if (o.has("set") && o.get("set").isJsonObject()) {
            for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("set").entrySet()) set.put(s.getKey(), s.getValue().getAsString());
        }
        return new Rule(List.copyOf(to), o.has("flip") && o.get("flip").getAsBoolean(), Map.copyOf(set),
                o.has("frame") ? o.get("frame").getAsString() : null, o.has("center") ? o.get("center").getAsString() : null,
                o.has("orient") && o.get("orient").getAsBoolean());
    }

    /** {@code me:x[a=b,c=d]} → l'identifiant et ses propriétés. */
    public static Key key(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        int open = s.indexOf('[');
        if (open < 0 || !s.endsWith("]")) return new Key(s, Map.of());
        Map<String, String> props = new LinkedHashMap<>();
        for (String kv : s.substring(open + 1, s.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        return new Key(s.substring(0, open), Map.copyOf(props));
    }

    // ------------------------------------------------------------------ recherche

    /**
     * La règle d'un bloc : d'abord une clé à propriétés qui lui correspond (la plus précise), puis son identifiant, puis
     * le motif le plus long. Renvoie aussi la part étoilée du motif, à reporter dans la cible.
     */
    static Optional<Map.Entry<Rule, String>> find(Map<Key, Rule> table, String id, Map<String, String> props) {
        Key best = null;
        for (Key k : table.keySet()) {
            if (k.when().isEmpty() || k.pattern() || !k.id().equals(id)) continue;
            if (!props.entrySet().containsAll(k.when().entrySet())) continue;
            if (best == null || k.when().size() > best.when().size()) best = k;
        }
        if (best != null) return Optional.of(Map.entry(table.get(best), ""));
        Rule exact = table.get(new Key(id, Map.of()));
        if (exact != null) return Optional.of(Map.entry(exact, ""));
        List<Key> patterns = new ArrayList<>();
        for (Key k : table.keySet()) if (k.pattern() && k.when().isEmpty()) patterns.add(k);
        patterns.sort(Comparator.comparingInt((Key k) -> -(k.id().length())));
        for (Key k : patterns) {
            String star = match(k.id(), id);
            if (star != null) return Optional.of(Map.entry(table.get(k), star));
        }
        return Optional.empty();
    }

    /** La part étoilée si {@code id} suit le motif (une seule étoile), sinon {@code null}. */
    static String match(String pattern, String id) {
        int star = pattern.indexOf('*');
        String before = pattern.substring(0, star), after = pattern.substring(star + 1);
        if (id.length() < before.length() + after.length() || !id.startsWith(before) || !id.endsWith(after)) return null;
        return id.substring(before.length(), id.length() - after.length());
    }

    /** Ce que devient un bloc qu'on n'a pas, ou rien s'il n'a pas d'équivalent installé. */
    public static Optional<Result> resolve(String id, Map<String, String> props) {
        Optional<Map.Entry<Rule, String>> found = find(table(), id.toLowerCase(Locale.ROOT), props);
        if (found.isEmpty()) return Optional.empty();
        Rule rule = found.get().getKey();
        for (String target : rule.to()) {
            String name = target.replace("*", found.get().getValue());
            Optional<Block> block = block(name);
            if (block.isEmpty()) continue;
            BlockState state = withProps(block.get().defaultBlockState(), props, rule.flip());
            state = withProps(state, rule.set(), false);
            CompoundTag tag = null;
            if (name.startsWith("domum_ornamentum:") && rule.frame() != null && rule.center() != null) tag = frameTag(rule.frame(), rule.center());
            return Optional.of(new Result(state, name, tag, rule.orient()));
        }
        return Optional.empty();
    }

    static Optional<Block> block(String name) {
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return Optional.empty();
        return Optional.of(BuiltInRegistries.BLOCK.get(rl));
    }

    /** Les données d'un colombage de Domum Ornamentum : le bois du cadre et le remplissage. */
    static CompoundTag frameTag(String frame, String center) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "domum_ornamentum:materially_retexturable");
        CompoundTag textures = new CompoundTag();
        textures.putString(FRAME_KEY, frame);
        textures.putString(CENTER_KEY, center);
        tag.put("textureData", textures);
        return tag;
    }

    /** Recopie sur {@code base} les propriétés d'origine qu'il connaît, l'orientation retournée si demandé. */
    public static BlockState withProps(BlockState base, Map<String, String> props, boolean flip) {
        BlockState out = base;
        for (Map.Entry<String, String> e : props.entrySet()) {
            Property<?> p = base.getBlock().getStateDefinition().getProperty(e.getKey());
            if (p == null) continue;
            String value = flip && "facing".equals(e.getKey()) ? opposite(e.getValue()) : e.getValue();
            out = with(out, p, value);
        }
        return out;
    }

    /** Le côté opposé, pour une orientation (les autres valeurs passent telles quelles). */
    public static String opposite(String facing) {
        Direction d = Direction.byName(facing);
        return d == null ? facing : d.getOpposite().getName();
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> p, String value) {
        Optional<T> v = p.getValue(value);
        return v.isPresent() ? state.setValue(p, v.get()) : state;
    }

    /** Lit les propriétés d'une clé de palette Sponge ({@code minecraft:oak_stairs[facing=east,half=top]}). */
    public static Map<String, String> props(String paletteKey) {
        return key(paletteKey).when();
    }
}
