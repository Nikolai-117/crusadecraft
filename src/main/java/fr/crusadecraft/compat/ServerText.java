package fr.crusadecraft.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nos textes, rendus côté serveur (P17). Un serveur ne charge pas les traductions : un {@code Component.translatable}
 * y reste une clé. Pour ce qui part ailleurs qu'en jeu — la carte web —, on lit les fichiers de langue du mod, rangés
 * dans le jar, et on compose les textes nous-mêmes. Une clé inconnue reste telle quelle.
 */
public final class ServerText {
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");
    private static final Map<String, Map<String, String>> LANGS = new HashMap<>();

    private ServerText() {}

    private static Map<String, String> lang(String code) {
        return LANGS.computeIfAbsent(code, c -> {
            Map<String, String> out = new HashMap<>();
            try (InputStream in = ServerText.class.getResourceAsStream("/assets/" + CrusadeCraft.MOD_ID + "/lang/" + c + ".json")) {
                if (in == null) return out;
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : json.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
            } catch (Exception e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : langue {} illisible pour la carte web", c, e);
            }
            return out;
        });
    }

    /** Le texte d'un composant, dans cette langue ({@code fr_fr}, {@code en_us}…), arguments compris. */
    public static String render(Component c, String language) {
        StringBuilder out = new StringBuilder();
        append(out, c, lang(language), lang("en_us"));
        return out.toString();
    }

    private static void append(StringBuilder out, Component c, Map<String, String> lang, Map<String, String> fallback) {
        if (c.getContents() instanceof TranslatableContents t) {
            String pattern = lang.getOrDefault(t.getKey(), fallback.getOrDefault(t.getKey(), t.getFallback() != null ? t.getFallback() : t.getKey()));
            Object[] args = t.getArgs();
            Matcher m = ARG.matcher(pattern);
            int next = 0, last = 0;
            while (m.find()) {
                out.append(pattern, last, m.start());
                if (m.group().equals("%%")) {
                    out.append('%');
                } else {
                    int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                    if (index >= 0 && index < args.length) {
                        Object a = args[index];
                        if (a instanceof Component ac) append(out, ac, lang, fallback);
                        else out.append(a);
                    }
                }
                last = m.end();
            }
            out.append(pattern.substring(last));
        } else if (c.getContents() instanceof LiteralContents l) {
            out.append(l.text());
        } else {
            out.append(c.getContents() == null ? "" : Component.empty().append(c.plainCopy()).getString());
        }
        for (Component sibling : c.getSiblings()) append(out, sibling, lang, fallback);
    }
}
