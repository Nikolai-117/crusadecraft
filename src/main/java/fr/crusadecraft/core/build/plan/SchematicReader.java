package fr.crusadecraft.core.build.plan;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lit les fichiers de plans du joueur (P34) : les structures de Minecraft ({@code .nbt}, celles du bloc de structure)
 * et les schématiques de WorldEdit ({@code .schem}, format Sponge 1 à 3). Les anciens {@code .schematic} de MCEdit,
 * à numéros de blocs, ne sont pas lus.
 *
 * <p>Un bloc d'un mod absent est laissé de côté (le terrain reste à sa place) plutôt que de faire échouer le plan.
 */
public final class SchematicReader {
    /** Au-delà, le plan est refusé : quatre millions de blocs, c'est déjà une ville entière. */
    public static final int MAX_VOLUME = 4_000_000;

    private SchematicReader() {}

    public static boolean readable(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".nbt") || n.endsWith(".schem");
    }

    public static RawPlan read(Path file) throws IOException {
        CompoundTag root;
        try (InputStream in = Files.newInputStream(file)) {
            root = NbtIo.readCompressed(in);
        }
        String name = PlanRef.label(file.getFileName().toString());
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem") ? sponge(name, root) : structure(name, root);
    }

    // ------------------------------------------------------------------ .nbt

    static RawPlan structure(String name, CompoundTag root) throws IOException {
        root = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), root, NbtUtils.getDataVersion(root, 500));
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IOException("structure sans taille");
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        checkVolume(sx, sy, sz);
        ListTag palette = root.contains("palettes", Tag.TAG_LIST)
                ? root.getList("palettes", Tag.TAG_LIST).getList(0) : root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int i = 0; i < palette.size(); i++) states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        List<RawPlan.Cell> cells = new ArrayList<>();
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            int idx = b.getInt("state");
            if (pos.size() != 3 || idx < 0 || idx >= states.size()) continue;
            BlockState st = states.get(idx);
            String id = palette.getCompound(idx).getString("Name");
            if (st.isAir() && !id.endsWith("air")) continue;
            cells.add(new RawPlan.Cell(pos.getInt(0), pos.getInt(1), pos.getInt(2), st, id, b.contains("nbt") ? b.getCompound("nbt") : null));
        }
        sort(cells);
        return new RawPlan(name, sx, sy, sz, 0, cells);
    }

    // ------------------------------------------------------------------ .schem

    static RawPlan sponge(String name, CompoundTag root) throws IOException {
        CompoundTag s = root.contains("Schematic", Tag.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        int version = s.getInt("Version");
        int w = s.getShort("Width") & 0xFFFF, h = s.getShort("Height") & 0xFFFF, l = s.getShort("Length") & 0xFFFF;
        checkVolume(w, h, l);
        CompoundTag blocks = version >= 3 ? s.getCompound("Blocks") : s;
        CompoundTag paletteTag = blocks.getCompound("Palette");
        byte[] data = version >= 3 ? blocks.getByteArray("Data") : s.getByteArray("BlockData");
        Map<Integer, BlockState> palette = new HashMap<>();
        Map<Integer, String> ids = new HashMap<>();
        for (String key : paletteTag.getAllKeys()) {
            int idx = paletteTag.getInt(key);
            ids.put(idx, key.contains("[") ? key.substring(0, key.indexOf('[')) : key);
            try {
                palette.put(idx, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, false).blockState());
            } catch (CommandSyntaxException e) {
                palette.put(idx, null);
            }
        }
        int[] indices = Varints.decode(data, w * h * l);
        Map<Integer, CompoundTag> entities = new HashMap<>();
        ListTag be = blocks.contains("BlockEntities", Tag.TAG_LIST) ? blocks.getList("BlockEntities", Tag.TAG_COMPOUND)
                : s.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < be.size(); i++) {
            CompoundTag e = be.getCompound(i);
            int[] pos = e.getIntArray("Pos");
            if (pos.length != 3) continue;
            CompoundTag tag = version >= 3 && e.contains("Data", Tag.TAG_COMPOUND) ? e.getCompound("Data").copy() : e.copy();
            tag.remove("Pos");
            tag.remove("Id");
            tag.putString("id", e.getString("Id"));
            entities.put(pos[0] + pos[2] * w + pos[1] * w * l, tag);
        }
        List<RawPlan.Cell> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int i = x + z * w + y * w * l;
                    BlockState st = palette.get(indices[i]);
                    if (st == null) continue;
                    cells.add(new RawPlan.Cell(x, y, z, st, ids.get(indices[i]), entities.get(i)));
                }
            }
        }
        return new RawPlan(name, w, h, l, 0, cells);
    }

    private static void checkVolume(int x, int y, int z) throws IOException {
        if (x <= 0 || y <= 0 || z <= 0 || (long) x * y * z > MAX_VOLUME) throw new IOException("plan trop grand ou vide : " + x + "×" + y + "×" + z);
    }

    /** Par couches, de bas en haut : l'ordre où l'on bâtit. */
    private static void sort(List<RawPlan.Cell> cells) {
        cells.sort((a, b) -> a.y() != b.y() ? Integer.compare(a.y(), b.y()) : a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.x(), b.x()));
    }

    /** Version de Minecraft des plans écrits par le mod (pour les essais). */
    public static int dataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
}
