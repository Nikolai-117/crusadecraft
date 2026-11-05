package fr.crusadecraft.core.nation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Écrire le fichier d'une nation depuis le jeu (P46), pour qu'un royaume fondé en jouant vaille un royaume écrit à la main. */
public final class NationFile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private NationFile() {}

    /** La clé d'une nation d'après son nom : « Levànzia » donne « levanzia ». */
    public static String keyOf(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "nation" : s;
    }

    /** Crée le fichier et le relit. Renvoie la clé, libre (« nouvelle_2 » si « nouvelle » est prise), ou {@code null} si l'écriture échoue. */
    public static String create(String name, TitleRank rank, int color, AiPersonality personality, boolean player) {
        String base = keyOf(name), key = base;
        int n = 2;
        while (NationBook.byKey(key).isPresent() || Files.exists(NationBook.folder().resolve(key + ".json"))) key = base + "_" + n++;
        JsonObject o = new JsonObject();
        o.addProperty("cle", key);
        o.addProperty("nom", name);
        o.addProperty("dossier", name);
        o.addProperty("rang", rank.name());
        o.addProperty("couleur", String.format("%06X", color & 0xFFFFFF));
        o.addProperty("temperament", personality.name());
        o.addProperty("foi", "FIDELE");
        o.addProperty("joueur", player);
        o.addProperty("maison", name);
        o.add("histoire", new JsonArray());
        try {
            Files.createDirectories(NationBook.folder());
            Files.writeString(NationBook.folder().resolve(key + ".json"), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : nation {} non écrite", name, e);
            return null;
        }
        NationBook.reload();
        return NationBook.byKey(key).isPresent() ? key : null;
    }

    /** L'histoire d'une nation, réécrite depuis le jeu : les autres réglages du fichier ne bougent pas. */
    public static boolean setHistory(String key, List<String> paragraphs) {
        Path file = NationBook.folder().resolve(key + ".json");
        if (!Files.exists(file)) return false;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray a = new JsonArray();
            for (String p : paragraphs) if (!p.isBlank()) a.add(p.strip());
            o.add("histoire", a);
            Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
            NationBook.reload();
            return true;
        } catch (IOException | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : histoire de {} non écrite", key, e);
            return false;
        }
    }
}
