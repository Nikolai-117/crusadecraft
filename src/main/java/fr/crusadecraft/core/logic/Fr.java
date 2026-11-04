package fr.crusadecraft.core.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Un texte en français, côté serveur (P41). Un serveur dédié ne connaît que l'anglais : {@code getString()} y rendrait
 * « Kingdom of Levànzia » dans une lettre française. Le fichier de langue est dans le jar, serveur compris : on le lit
 * nous-mêmes pour ce qui doit être écrit une fois pour toutes (une lettre, une réplique composée).
 */
public final class Fr {
    private static Map<String, String> table;

    private Fr() {}

    private static synchronized Map<String, String> table() {
        if (table != null) return table;
        Map<String, String> loaded = new HashMap<>();
        try (InputStream in = Fr.class.getResourceAsStream("/assets/crusadecraft/lang/fr_fr.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) loaded.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (IOException | RuntimeException e) {
            fr.crusadecraft.CrusadeCraft.LOGGER.warn("CrusadeCraft : langue française illisible côté serveur", e);
        }
        table = loaded;
        return table;
    }

    /** Ce texte, en français quand la clé est à nous, tel que le jeu le rend sinon. */
    public static String of(Component c) {
        StringBuilder out = new StringBuilder();
        ComponentContents k = c.getContents();
        if (k instanceof TranslatableContents t) {
            String pattern = table().get(t.getKey());
            out.append(pattern == null ? Component.translatable(t.getKey(), t.getArgs()).getString() : format(pattern, t.getArgs()));
        } else if (k instanceof LiteralContents l) {
            out.append(l.text());
        }
        for (Component s : c.getSiblings()) out.append(of(s));
        return out.toString();
    }

    /** La valeur d'une clé à nous. */
    public static String key(String key, Object... args) {
        String pattern = table().get(key);
        return pattern == null ? key : format(pattern, args);
    }

    /** Remplit {@code %s}, {@code %d} et {@code %1$s} comme le jeu ; {@code %%} donne un pour-cent. */
    static String format(String pattern, Object[] args) {
        StringBuilder out = new StringBuilder();
        int next = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch != '%' || i == pattern.length() - 1) {
                out.append(ch);
                continue;
            }
            int j = i + 1;
            if (pattern.charAt(j) == '%') {
                out.append('%');
                i = j;
                continue;
            }
            int index = -1;
            int d = j;
            while (d < pattern.length() && Character.isDigit(pattern.charAt(d))) d++;
            if (d > j && d < pattern.length() && pattern.charAt(d) == '$') {
                index = Integer.parseInt(pattern.substring(j, d)) - 1;
                j = d + 1;
            }
            if (j >= pattern.length() || (pattern.charAt(j) != 's' && pattern.charAt(j) != 'd')) {
                out.append(ch);
                continue;
            }
            int at = index >= 0 ? index : next++;
            out.append(at < args.length ? show(args[at]) : "");
            i = j;
        }
        return out.toString();
    }

    private static String show(Object o) {
        return o instanceof Component c ? of(c) : String.valueOf(o);
    }
}
