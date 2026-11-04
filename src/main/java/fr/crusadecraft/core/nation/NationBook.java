package fr.crusadecraft.core.nation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.CrusadeCraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Les nations écrites d'avance (P41), lues dans {@code crusadecraft/nations/*.json}. Le mod peut en livrer (dans le jar,
 * sous {@code /crusadecraft_nations/}) : elles y sont rangées si le fichier n'existe pas, et ne sont jamais écrasées,
 * pour qu'une correction du joueur survive à une nouvelle version. {@link #restore} remet la version du jar, sur demande.
 */
public final class NationBook {
    static final String BUNDLED = "/crusadecraft_nations/";
    private static volatile List<NationDef> nations = List.of();
    private static volatile boolean forTests;

    private NationBook() {}

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("crusadecraft").resolve("nations");
    }

    public static List<NationDef> all() {
        return nations;
    }

    public static Optional<NationDef> byKey(String key) {
        if (key == null || key.isEmpty()) return Optional.empty();
        for (NationDef n : nations) if (n.key().equalsIgnoreCase(key)) return Optional.of(n);
        return Optional.empty();
    }

    /** La nation dont les villes sont rangées dans ce dossier de plans (« Levenzia »). */
    public static Optional<NationDef> byFolder(String folder) {
        if (folder == null || folder.isEmpty()) return Optional.empty();
        String head = folder.contains("/") ? folder.substring(0, folder.indexOf('/')) : folder;
        for (NationDef n : nations) if (!n.folder().isEmpty() && n.folder().equalsIgnoreCase(head)) return Optional.of(n);
        return Optional.empty();
    }

    /** Pour les bancs d'essai : ces nations-là, et plus aucune lecture de fichier jusqu'à {@code setForTests(null)}. */
    public static void setForTests(List<NationDef> list) {
        forTests = list != null;
        nations = list == null ? List.of() : List.copyOf(list);
    }

    /** Range ce que le jar livre, puis relit le dossier. Renvoie les erreurs de lecture, pour les dire au joueur. */
    public static List<String> reload() {
        if (forTests) return List.of();
        List<String> errors = new ArrayList<>();
        Path dir = folder();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            errors.add(dir + " : " + e.getMessage());
            return errors;
        }
        installBundled();
        List<NationDef> loaded = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                    NationDef def = NationDef.parse(o);
                    if (loaded.stream().anyMatch(n -> n.key().equals(def.key()))) throw new IllegalArgumentException("clé déjà prise : " + def.key());
                    loaded.add(def);
                } catch (IOException | RuntimeException e) {
                    errors.add(p.getFileName() + " : " + e.getMessage());
                    CrusadeCraft.LOGGER.warn("CrusadeCraft : nation {} illisible, ignorée : {}", p.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            errors.add(dir + " : " + e.getMessage());
        }
        nations = List.copyOf(loaded);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} nation(s) lue(s) dans {}", loaded.size(), dir);
        return errors;
    }

    /** Ce que le jar livre, rangé là où il manque. */
    static int installBundled() {
        int n = 0;
        for (String name : bundled()) {
            Path target = folder().resolve(name).normalize();
            if (!target.startsWith(folder()) || Files.exists(target)) continue;
            try (InputStream in = NationBook.class.getResourceAsStream(BUNDLED + name)) {
                if (in == null) continue;
                Files.copy(in, target);
                n++;
            } catch (IOException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : nation {} non rangée", name, e);
            }
        }
        return n;
    }

    /** Les fichiers que le jar annonce. */
    static List<String> bundled() {
        List<String> out = new ArrayList<>();
        try (InputStream index = NationBook.class.getResourceAsStream(BUNDLED + "index.txt")) {
            if (index == null) return out;
            for (String line : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String s = line.strip();
                if (!s.isEmpty() && !s.startsWith("#")) out.add(s);
            }
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : liste des nations du jar illisible", e);
        }
        return out;
    }

    /**
     * Remet les fichiers du jar par-dessus ceux du dossier, en gardant chaque ancien fichier à côté en {@code .bak}.
     * Sur demande seulement : c'est ce qui permet de reprendre une version plus récente du mod. Renvoie leur nombre.
     */
    public static int restore() {
        int n = 0;
        for (String name : bundled()) {
            Path target = folder().resolve(name).normalize();
            if (!target.startsWith(folder())) continue;
            try (InputStream in = NationBook.class.getResourceAsStream(BUNDLED + name)) {
                if (in == null) continue;
                Files.createDirectories(folder());
                if (Files.exists(target)) Files.copy(target, target.resolveSibling(name + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                n++;
            } catch (IOException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : nation {} non restaurée", name, e);
            }
        }
        return n;
    }
}
