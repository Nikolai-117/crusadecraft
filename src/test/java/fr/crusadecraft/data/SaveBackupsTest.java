package fr.crusadecraft.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les copies de secours faites avant une mise à niveau des données (P15). */
class SaveBackupsTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 4, 12, 30, 0);

    private static List<Path> copies(Path data) throws IOException {
        try (Stream<Path> s = Files.list(data.resolve(SaveBackups.DIR))) {
            return s.sorted().toList();
        }
    }

    @Test
    void laCopieGardeLeFichierTelQuel(@TempDir Path data) throws IOException {
        Path file = data.resolve("crusadecraft.dat");
        byte[] bytes = {1, 2, 3, 42};
        Files.write(file, bytes);
        Path copy = SaveBackups.backup(file, 12, false, NOW).orElseThrow();
        assertEquals("crusadecraft-v12-2026-10-04_12-30-00.dat", copy.getFileName().toString());
        assertArrayEquals(bytes, Files.readAllBytes(copy));
        assertArrayEquals(bytes, Files.readAllBytes(file), "l'original n'est pas touché");
    }

    @Test
    void jamaisDEcrasement(@TempDir Path data) throws IOException {
        Path file = data.resolve("crusadecraft.dat");
        Files.write(file, new byte[]{1});
        Path a = SaveBackups.backup(file, 12, false, NOW).orElseThrow();
        Files.write(file, new byte[]{2});
        Path b = SaveBackups.backup(file, 12, false, NOW).orElseThrow();
        assertNotEquals(a, b, "deux copies à la même seconde ne s'écrasent pas");
        assertEquals(1, Files.readAllBytes(a)[0]);
        assertEquals(2, Files.readAllBytes(b)[0]);
    }

    @Test
    void uneSauvegardePlusRecenteEstSignalee(@TempDir Path data) throws IOException {
        Path file = data.resolve("crusadecraft.dat");
        Files.write(file, new byte[]{1});
        Path copy = SaveBackups.backup(file, 14, true, NOW).orElseThrow();
        assertTrue(copy.getFileName().toString().startsWith("crusadecraft-v14-plus-recente-"));
    }

    @Test
    void sansFichierRienACopier(@TempDir Path data) throws IOException {
        assertTrue(SaveBackups.backup(data.resolve("crusadecraft.dat"), 12, false, NOW).isEmpty());
    }

    @Test
    void lesDixPlusRecentesSontGardees(@TempDir Path data) throws IOException {
        Path dir = Files.createDirectories(data.resolve(SaveBackups.DIR));
        // Douze vieilles copies datées d'heures différentes, et un fichier étranger qui ne doit pas bouger.
        for (int i = 0; i < 12; i++) {
            Path p = dir.resolve("crusadecraft-v" + (5 + i) + "-2026-01-01_00-00-0" + (i % 10) + ".dat");
            Files.write(p, new byte[]{(byte) i});
            Files.setLastModifiedTime(p, FileTime.fromMillis(1_000_000L + i * 1000L));
        }
        Path foreign = Files.writeString(dir.resolve("notes.txt"), "à moi");
        SaveBackups.prune(dir, SaveBackups.KEEP);
        List<Path> left = copies(data);
        assertEquals(SaveBackups.KEEP + 1, left.size(), "dix copies plus le fichier étranger");
        assertTrue(Files.exists(foreign));
        assertTrue(Files.notExists(dir.resolve("crusadecraft-v5-2026-01-01_00-00-00.dat")), "la plus ancienne part");
        assertTrue(Files.exists(dir.resolve("crusadecraft-v16-2026-01-01_00-00-01.dat")), "la plus récente reste");
    }
}
