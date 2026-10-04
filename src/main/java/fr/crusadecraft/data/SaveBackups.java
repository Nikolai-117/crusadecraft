package fr.crusadecraft.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Les copies de secours de {@code crusadecraft.dat}, faites <b>avant</b> qu'une autre version du mod ne touche aux
 * données : avant une mise à niveau, ou devant une sauvegarde plus récente que le mod (qu'un vieux jar réécrirait en
 * perdant ce qu'il ne connaît pas).
 *
 * <p>Le {@code .bak} d'{@link AtomicSavedData} ne suffit pas : il est remplacé à chaque sauvegarde du monde, si bien
 * que deux sauvegardes automatiques après une mise à niveau suffisaient à perdre l'original. Ces copies-ci sont
 * datées, portent la version des données, et ne sont jamais réécrites. Les {@value #KEEP} plus récentes sont
 * gardées ; on n'en fait qu'une par changement de version, donc en pratique aucune ne disparaît.
 */
public final class SaveBackups {
    /** Le dossier des copies, à côté du fichier de données (dans {@code <monde>/data/}). */
    public static final String DIR = "crusadecraft_backups";
    public static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Pattern NAME = Pattern.compile("crusadecraft-v\\d+(-plus-recente)?-.+\\.dat");

    private SaveBackups() {}

    /**
     * Copie le fichier de données, s'il existe, sous un nom qui dit sa version et l'heure ({@code
     * crusadecraft-v12-2026-10-04_12-30-00.dat}). {@code newer} marque une sauvegarde plus récente que le mod.
     */
    public static Optional<Path> backup(Path dataFile, int version, boolean newer, LocalDateTime now) throws IOException {
        if (!Files.isRegularFile(dataFile)) return Optional.empty();
        Path dir = dataFile.resolveSibling(DIR);
        Files.createDirectories(dir);
        String base = "crusadecraft-v" + version + (newer ? "-plus-recente" : "") + "-" + STAMP.format(now);
        Path target = dir.resolve(base + ".dat");
        for (int n = 2; Files.exists(target); n++) target = dir.resolve(base + "-" + n + ".dat");
        Files.copy(dataFile, target);
        // La date de la copie, pas celle du fichier copié : c'est elle qui ordonne les copies.
        Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
        prune(dir, KEEP);
        return Optional.of(target);
    }

    /** Garde les {@code keep} copies les plus récentes ; les autres fichiers du dossier ne sont pas touchés. */
    static void prune(Path dir, int keep) throws IOException {
        List<Path> copies;
        try (Stream<Path> s = Files.list(dir)) {
            copies = s.filter(p -> NAME.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(SaveBackups::modified).thenComparing(p -> p.getFileName().toString()))
                    .toList();
        }
        for (int i = 0; i < copies.size() - keep; i++) Files.deleteIfExists(copies.get(i));
    }

    private static FileTime modified(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }
}
