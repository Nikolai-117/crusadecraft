package fr.crusadecraft.compat.minecolonies;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintTagUtils;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.plan.RawPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Les packs de style de Structurize (P34) : ceux de MineColonies, ceux des mods de style (Byzantine…), et ceux que le
 * joueur a scannés dans son dossier {@code blueprints}. On les lit au moment du jeu, par l'API de Structurize ;
 * aucun plan n'est copié dans le mod (leurs auteurs ne l'ont pas permis).
 *
 * <p>Si l'API a changé, le pont se coupe une fois pour toutes et les villes retombent sur les plans dessinés par le
 * code : rien ne plante.
 */
public final class StructurizePlans {

    /** Un pack de style : son nom (celui de Structurize, qui sert de clé) et son dossier. */
    public record Pack(String name, Path root, List<String> authors) {}

    private static volatile boolean broken;

    private StructurizePlans() {}

    private static boolean available() {
        return !broken && ModList.get() != null && ModList.get().isLoaded("structurize");
    }

    private static void breakBridge(Throwable t) {
        if (!broken) {
            broken = true;
            CrusadeCraft.LOGGER.warn("CrusadeCraft : l'API de Structurize ne répond pas comme prévu, les plans des packs sont coupés", t);
        }
    }

    /** Les packs chargés, dans l'ordre de Structurize. */
    public static List<Pack> packs() {
        if (!available()) return List.of();
        try {
            List<Pack> out = new ArrayList<>();
            for (StructurePackMeta m : StructurePacks.getPackMetas()) {
                if (m.isDisabled()) continue;
                out.add(new Pack(m.getName(), m.getPath(), List.copyOf(m.getAuthors())));
            }
            return out;
        } catch (LinkageError | RuntimeException e) {
            breakBridge(e);
            return List.of();
        }
    }

    /** Les plans d'un pack : leurs chemins dans le pack, avec « / » et l'extension, triés. */
    public static List<String> list(String pack) {
        if (!available()) return List.of();
        try {
            StructurePackMeta meta = StructurePacks.getStructurePack(pack);
            if (meta == null) return List.of();
            Path root = meta.getPath();
            List<String> out = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.getFileName() != null && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".blueprint"))
                        .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
            }
            out.sort(String::compareTo);
            return out;
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : le pack {} ne se lit pas", pack, e);
            return List.of();
        } catch (LinkageError | RuntimeException e) {
            breakBridge(e);
            return List.of();
        }
    }

    /**
     * Lit un plan. La couche du sol vient de la balise « groundlevel » du plan quand il en a une ; sinon, celle sous
     * le bloc d'ancrage (une cabane se pose sur le sol).
     */
    public static Optional<RawPlan> read(String pack, String path) {
        if (!available()) return Optional.empty();
        try {
            StructurePackMeta meta = StructurePacks.getStructurePack(pack);
            if (meta == null) return Optional.empty();
            Blueprint bp = StructurePacks.getBlueprint(pack, meta.getPath().resolve(path));
            if (bp == null) return Optional.empty();
            int sx = bp.getSizeX(), sy = bp.getSizeY(), sz = bp.getSizeZ();
            BlockPos anchor = bp.getPrimaryBlockOffset();
            BlockPos groundTag = BlueprintTagUtils.getFirstPosForTag(bp, "groundlevel");
            int groundY = groundTag != null ? anchor.getY() + groundTag.getY() : anchor.getY() - 1;
            groundY = Math.max(0, Math.min(sy - 1, groundY));
            short[][][] structure = bp.getStructure();
            BlockState[] palette = bp.getPalette();
            CompoundTag[][][] entities = bp.getTileEntities();
            String[] ids = new String[palette.length];
            for (int i = 0; i < palette.length; i++) ids[i] = BuiltInRegistries.BLOCK.getKey(palette[i].getBlock()).toString();
            List<RawPlan.Cell> cells = new ArrayList<>();
            for (int y = 0; y < sy; y++) {
                boolean[][] inside = y < groundY ? inside(structure[y], palette, sx, sz) : null;
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        int idx = structure[y][z][x] & 0xFFFF;
                        if (idx >= palette.length) continue;
                        BlockState st = palette[idx];
                        // Sous le sol, le vide d'un plan n'est creusé que dans les murs (une cave) : ailleurs, le terrain reste.
                        if (st.isAir() && inside != null && !inside[z][x]) continue;
                        CompoundTag tag = entities == null ? null : entities[y][z][x];
                        cells.add(new RawPlan.Cell(x, y, z, st, ids[idx], tag));
                    }
                }
            }
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".blueprint", "");
            return Optional.of(new RawPlan(name, sx, sy, sz, groundY, cells));
        } catch (LinkageError | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : le plan {} / {} ne se lit pas", pack, path, e);
            if (e instanceof LinkageError) breakBridge(e);
            return Optional.empty();
        }
    }

    /** Les cases vides d'une couche enfermées entre des blocs, en long comme en large. */
    private static boolean[][] inside(short[][] layer, BlockState[] palette, int sx, int sz) {
        int[] minX = new int[sz], maxX = new int[sz], minZ = new int[sx], maxZ = new int[sx];
        java.util.Arrays.fill(minX, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxX, Integer.MIN_VALUE);
        java.util.Arrays.fill(minZ, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxZ, Integer.MIN_VALUE);
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int idx = layer[z][x] & 0xFFFF;
                if (idx >= palette.length || palette[idx].isAir()) continue;
                minX[z] = Math.min(minX[z], x);
                maxX[z] = Math.max(maxX[z], x);
                minZ[x] = Math.min(minZ[x], z);
                maxZ[x] = Math.max(maxZ[x], z);
            }
        }
        boolean[][] out = new boolean[sz][sx];
        for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) out[z][x] = minX[z] < x && x < maxX[z] && minZ[x] < z && z < maxZ[x];
        return out;
    }
}
