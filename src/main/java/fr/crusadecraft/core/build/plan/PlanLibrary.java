package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.StructurizePlans;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * La bibliothèque des plans (P34), côté serveur : les packs de style de Structurize et les fichiers de plans du
 * joueur. Les dossiers lus pour les fichiers :
 * <ul>
 *   <li>{@code crusadecraft/plans/} dans le dossier du jeu (créé au démarrage, c'est le nôtre) ;</li>
 *   <li>{@code schematics/} et {@code config/worldedit/schematics/}, s'ils existent (lus, jamais modifiés).</li>
 * </ul>
 * Les plans lus sont gardés en mémoire, les vingt-quatre derniers.
 */
public final class PlanLibrary {
    private static final int CACHE = 24;

    /** Une étagère du catalogue : un pack, ou les fichiers du joueur. */
    public record Source(String id, String label, int count) {}

    /** Un plan du catalogue : sa référence encodée, son nom, son dossier, sa sorte ({@link PlanKinds}). */
    public record Entry(String ref, String label, String category, int kind) {}

    /** Le nom de l'étagère des fichiers. */
    public static final String FILES = "@fichiers";

    private static final Map<String, List<String>> PACK_ENTRIES = new HashMap<>();
    private static final Map<String, Plan> PLANS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Plan> e) {
            return size() > CACHE;
        }
    };

    private PlanLibrary() {}

    public static synchronized void onServerStopped() {
        PACK_ENTRIES.clear();
        PLANS.clear();
    }

    /** Les dossiers de fichiers de plans, par nom court. */
    public static Map<String, Path> roots() {
        Path game = FMLPaths.GAMEDIR.get();
        Map<String, Path> out = new LinkedHashMap<>();
        out.put("plans", game.resolve("crusadecraft").resolve("plans"));
        out.put("schematics", game.resolve("schematics"));
        out.put("worldedit", game.resolve("config").resolve("worldedit").resolve("schematics"));
        return out;
    }

    /** Crée notre dossier de plans s'il manque. */
    public static void ensureFolder() {
        try {
            Files.createDirectories(roots().get("plans"));
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : impossible de créer le dossier des plans", e);
        }
    }

    // ------------------------------------------------------------------ catalogue

    public static List<StructurizePlans.Pack> packs() {
        return StructurizePlans.packs();
    }

    public static synchronized List<String> packEntries(String pack) {
        return PACK_ENTRIES.computeIfAbsent(pack, StructurizePlans::list);
    }

    /** Les fichiers .nbt et .schem des dossiers de plans. */
    public static List<Entry> fileEntries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Path> root : roots().entrySet()) {
            if (!Files.isDirectory(root.getValue())) continue;
            try (Stream<Path> walk = Files.walk(root.getValue(), 6)) {
                walk.filter(p -> Files.isRegularFile(p) && SchematicReader.readable(p.getFileName().toString())).sorted().forEach(p -> {
                    String rel = root.getValue().relativize(p).toString().replace('\\', '/');
                    PlanRef ref = new PlanRef(PlanRef.Source.FILE, root.getKey(), rel);
                    String cat = ref.category();
                    // P38 : le sous-dossier est l'étagère (« Levenzia », « Varenhold ») ; à la racine, le nom du dossier.
                    out.add(new Entry(ref.encode(), ref.label(), cat.isEmpty() ? root.getKey() : cat, PlanKinds.AUTRE));
                });
            } catch (IOException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : le dossier de plans {} ne se lit pas", root.getValue(), e);
            }
        }
        return out;
    }

    /** Les étagères : chaque pack, puis les fichiers du joueur. */
    public static List<Source> sources() {
        List<Source> out = new ArrayList<>();
        for (StructurizePlans.Pack p : packs()) out.add(new Source(p.name(), p.name(), packEntries(p.name()).size()));
        out.add(new Source(FILES, "", fileEntries().size()));
        return out;
    }

    /** Les plans d'une étagère. */
    public static List<Entry> entries(String source) {
        if (FILES.equals(source)) return fileEntries();
        List<Entry> out = new ArrayList<>();
        for (String path : packEntries(source)) {
            PlanRef ref = new PlanRef(PlanRef.Source.PACK, source, path);
            out.add(new Entry(ref.encode(), ref.label(), ref.category(), PlanKinds.kind(path)));
        }
        return out;
    }

    // ------------------------------------------------------------------ lecture

    /** Le premier pack installé d'un style, par ordre de préférence. */
    public static Optional<String> packFor(NationStyle style) {
        List<StructurizePlans.Pack> installed = packs();
        for (String want : style.packs()) {
            for (StructurizePlans.Pack p : installed) {
                String name = PlanRef.normalize(p.name()), folder = p.root().getFileName() == null ? "" : PlanRef.normalize(p.root().getFileName().toString());
                if (name.equals(want) || folder.equals(want)) return Optional.of(p.name());
            }
        }
        return Optional.empty();
    }

    /**
     * Lit un plan (ou le reprend en mémoire). Un fichier doit rester dans son dossier : un chemin qui en sort est
     * refusé, d'où qu'il vienne.
     */
    public static Optional<Plan> load(PlanRef ref, boolean keepHuts) {
        String key = ref.encode() + (keepHuts ? "#h" : "");
        synchronized (PlanLibrary.class) {
            Plan cached = PLANS.get(key);
            if (cached != null) return Optional.of(cached);
        }
        Optional<RawPlan> raw = switch (ref.source()) {
            case PACK -> packEntries(ref.pack()).contains(ref.path()) ? StructurizePlans.read(ref.pack(), ref.path()) : Optional.empty();
            case FILE -> readFile(ref);
        };
        if (raw.isEmpty()) return Optional.empty();
        Plan plan = PlanBuilder.build(ref, raw.get(), keepHuts);
        synchronized (PlanLibrary.class) {
            PLANS.put(key, plan);
        }
        return Optional.of(plan);
    }

    static Optional<RawPlan> readFile(PlanRef ref) {
        Path root = roots().get(ref.pack());
        if (root == null) return Optional.empty();
        Path file = root.resolve(ref.path()).normalize();
        if (!file.startsWith(root.normalize()) || !Files.isRegularFile(file) || !SchematicReader.readable(file.getFileName().toString())) {
            return Optional.empty();
        }
        try {
            return Optional.of(SchematicReader.read(file));
        } catch (IOException | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : le plan {} ne se lit pas : {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    /** Le dossier de nos plans, pour le dire au joueur. */
    public static String folderForDisplay() {
        return roots().get("plans").toAbsolutePath().toString();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
