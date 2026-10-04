package fr.crusadecraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zéro perte : les sauvegardes faites par les anciennes versions du mod (1.0.0, 1.1.0, 1.2.0, 1.3.0), gardées dans
 * {@code src/test/resources/fixtures}, se chargent avec celle-ci, se mettent à niveau et se réécrivent sans rien
 * perdre de ce qu'elles contenaient.
 */
class SaveFixturesTest {

    private static List<Path> fixtures() throws IOException, URISyntaxException {
        URL dir = SaveFixturesTest.class.getResource("/fixtures");
        if (dir == null) return List.of();
        try (Stream<Path> s = Files.list(Path.of(dir.toURI()))) {
            return s.filter(p -> p.getFileName().toString().endsWith(".dat")).sorted().toList();
        }
    }

    private static CompoundTag read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in).getCompound("data");
        }
    }

    private static int size(CompoundTag t, String path) {
        CompoundTag at = t;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) at = at.getCompound(parts[i]);
        return at.getList(parts[parts.length - 1], Tag.TAG_COMPOUND).size();
    }

    @Test
    void lesAnciennesSauvegardesSeChargentSansRienPerdre() throws IOException, URISyntaxException {
        List<Path> files = fixtures();
        assertFalse(files.isEmpty(), "aucune sauvegarde de référence");
        java.util.Set<Integer> versions = new java.util.TreeSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            CompoundTag raw = read(file);
            int version = raw.getInt("version");
            versions.add(version);
            assertTrue(version >= 1 && version <= CrusadeSavedData.DATA_VERSION, name + " : version inattendue " + version);

            CrusadeSavedData data = CrusadeSavedData.load(raw.copy());
            assertEquals(size(raw, "realms"), data.realms().size(), name + " : royaumes des joueurs perdus");
            assertEquals(size(raw, "world.realms"), data.world().realms().size(), name + " : royaumes IA perdus");
            assertEquals(size(raw, "world.sites"), data.world().sites().size(), name + " : constructions perdues");

            // Réécrite par cette version, relue : rien n'a disparu, et elle est à jour.
            CompoundTag saved = data.save(new CompoundTag());
            assertEquals(CrusadeSavedData.DATA_VERSION, saved.getInt("version"), name + " : pas mise à jour");
            for (String key : raw.getAllKeys()) assertTrue(saved.contains(key), name + " : la section « " + key + " » a disparu");
            for (String key : raw.getCompound("world").getAllKeys()) {
                assertTrue(saved.getCompound("world").contains(key), name + " : la section « world." + key + " » a disparu");
            }
            assertEquals(size(raw, "realms"), size(saved, "realms"), name + " : royaumes perdus à l'écriture");
            assertEquals(size(raw, "world.realms"), size(saved, "world.realms"), name + " : royaumes IA perdus à l'écriture");
            assertEquals(size(raw, "characters"), size(saved, "characters"), name + " : personnages perdus");
            assertEquals(size(raw, "lineage.persons"), size(saved, "lineage.persons"), name + " : registre des lignées abîmé");
            CrusadeSavedData again = CrusadeSavedData.load(saved.copy());
            assertEquals(data.realms().size(), again.realms().size(), name + " : la relecture perd des royaumes");
            assertEquals(data.world().realms().size(), again.world().realms().size(), name + " : la relecture perd des royaumes IA");
            String diff = firstDifference(saved, again.save(new CompoundTag()), "");
            assertEquals("", diff, name + " : écrire puis relire change les données en " + diff);
        }
        // Les formats des versions 1.0 (10), 1.1 (11) et 1.2/1.3 (12) sont tous couverts.
        assertTrue(versions.containsAll(java.util.List.of(10, 11, 12)), "formats couverts : " + versions);
    }

    /** Le premier endroit où deux données diffèrent ({@code ""} si elles sont égales), pour un message lisible. */
    private static String firstDifference(Tag a, Tag b, String path) {
        if (a.equals(b)) return "";
        if (a instanceof CompoundTag ca && b instanceof CompoundTag cb) {
            for (String k : ca.getAllKeys()) {
                if (!cb.contains(k)) return path + "." + k + " (disparu)";
                String d = firstDifference(ca.get(k), cb.get(k), path + "." + k);
                if (!d.isEmpty()) return d;
            }
            for (String k : cb.getAllKeys()) if (!ca.contains(k)) return path + "." + k + " (apparu)";
        }
        if (a instanceof net.minecraft.nbt.ListTag la && b instanceof net.minecraft.nbt.ListTag lb) {
            if (la.size() != lb.size()) return path + " (taille " + la.size() + " → " + lb.size() + ")";
            for (int i = 0; i < la.size(); i++) {
                String d = firstDifference(la.get(i), lb.get(i), path + "[" + i + "]");
                if (!d.isEmpty()) return d;
            }
        }
        String sa = a.toString(), sb = b.toString();
        return path + " : " + (sa.length() > 120 ? sa.substring(0, 120) + "…" : sa) + " → " + (sb.length() > 120 ? sb.substring(0, 120) + "…" : sb);
    }
}
