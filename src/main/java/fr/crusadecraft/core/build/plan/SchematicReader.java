package fr.crusadecraft.core.build.plan;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lit les fichiers de plans du joueur (P34) : les structures de Minecraft ({@code .nbt}, celles du bloc de structure)
 * et les schématiques de WorldEdit ({@code .schem}, format Sponge 1 à 3). Les anciens {@code .schematic} de MCEdit,
 * à numéros de blocs, ne sont pas lus.
 *
 * <p>P38 : un bloc qu'on n'a pas (d'un autre mod, d'une version plus récente) prend son équivalent installé, de même
 * forme ({@link BlockEquivalents}) ; sans équivalent, il est laissé de côté plutôt que de faire échouer le plan. Une
 * ville entière se lit sans tenir ses millions de cases une à une ({@link GridCells}).
 */
public final class SchematicReader {
    /** Au-delà, le plan est refusé : douze millions de cases, c'est déjà une grande ville avec sa colline. */
    public static final int MAX_VOLUME = 12_000_000;
    /** Au-delà, les cases sont lues à la demande ({@link GridCells}) ; abaissé par les essais. */
    static int lazyVolume = 262_144;
    /** La part des bords du plan où l'on doit trouver du sol pour croire qu'il a apporté son terrain. */
    static final double TERRAIN_SHARE = 0.6;
    /** Ce qui fait un sol : on cherche la plus haute de ces couches au bord du plan. */
    static final Set<String> SOILS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
            "minecraft:rooted_dirt", "minecraft:mycelium", "minecraft:dirt_path", "minecraft:farmland", "minecraft:mud", "minecraft:gravel",
            "minecraft:sand", "minecraft:red_sand", "minecraft:moss_block", "minecraft:snow_block", "minecraft:clay");

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

    // ------------------------------------------------------------------ la palette

    /** Ce que devient chaque sorte de bloc d'un fichier, et ce qu'on a dû remplacer ou laisser. */
    static final class Palette {
        final List<BlockState> states = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        final List<CompoundTag> extra = new ArrayList<>();
        final List<Boolean> orient = new ArrayList<>();
        final Set<String> replaced = new TreeSet<>(), unknown = new TreeSet<>();
        private final Map<Long, Integer> variants = new HashMap<>();

        void set(int idx, String paletteKey) {
            while (states.size() <= idx) {
                states.add(null);
                ids.add(null);
                extra.add(null);
                orient.add(false);
            }
            String id = (paletteKey.contains("[") ? paletteKey.substring(0, paletteKey.indexOf('[')) : paletteKey).toLowerCase(Locale.ROOT);
            Map<String, String> props = BlockEquivalents.props(paletteKey);
            Optional<Block> block = BlockEquivalents.block(id);
            if (block.isPresent()) {
                BlockState st;
                try {
                    st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), paletteKey, false).blockState();
                } catch (CommandSyntaxException e) {
                    // Une propriété d'une autre version : on garde celles que le bloc connaît.
                    st = BlockEquivalents.withProps(block.get().defaultBlockState(), props, false);
                }
                states.set(idx, st);
                ids.set(idx, id);
                return;
            }
            Optional<BlockEquivalents.Result> r = BlockEquivalents.resolve(id, props);
            if (r.isEmpty()) {
                unknown.add(id);
                return;
            }
            replaced.add(id);
            states.set(idx, r.get().state());
            ids.set(idx, r.get().id());
            extra.set(idx, r.get().tag());
            orient.set(idx, r.get().orient());
        }

        boolean solid(int idx) {
            return idx >= 0 && idx < states.size() && states.get(idx) != null && !states.get(idx).isAir();
        }

        boolean orients(int idx) {
            return idx >= 0 && idx < orient.size() && orient.get(idx);
        }

        /** La même sorte, tournée vers {@code facing} (une nouvelle entrée de palette au besoin). */
        int variant(int idx, Direction facing) {
            BlockState st = states.get(idx);
            if (!st.hasProperty(BlockStateProperties.FACING) && !st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return idx;
            DirectionProperty p = st.hasProperty(BlockStateProperties.FACING) ? BlockStateProperties.FACING : BlockStateProperties.HORIZONTAL_FACING;
            if (!p.getPossibleValues().contains(facing) || st.getValue(p) == facing) return idx;
            return variants.computeIfAbsent(((long) idx << 3) | facing.ordinal(), k -> {
                states.add(st.setValue(p, facing));
                ids.add(ids.get(idx));
                extra.add(extra.get(idx));
                orient.add(false);
                return states.size() - 1;
            });
        }
    }

    // ------------------------------------------------------------------ .nbt

    static RawPlan structure(String name, CompoundTag root) throws IOException {
        root = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), root, NbtUtils.getDataVersion(root, 500));
        ListTag size = root.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IOException("structure sans taille");
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        checkVolume(sx, sy, sz);
        ListTag paletteTag = root.contains("palettes", Tag.TAG_LIST)
                ? root.getList("palettes", Tag.TAG_LIST).getList(0) : root.getList("palette", Tag.TAG_COMPOUND);
        Palette palette = new Palette();
        for (int i = 0; i < paletteTag.size(); i++) palette.set(i, key(paletteTag.getCompound(i)));
        List<RawPlan.Cell> cells = new ArrayList<>();
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            int idx = b.getInt("state");
            if (pos.size() != 3 || idx < 0 || idx >= palette.states.size() || palette.states.get(idx) == null) continue;
            CompoundTag tag = b.contains("nbt") ? b.getCompound("nbt") : palette.extra.get(idx);
            cells.add(new RawPlan.Cell(pos.getInt(0), pos.getInt(1), pos.getInt(2), palette.states.get(idx), palette.ids.get(idx), tag));
        }
        sort(cells);
        return new RawPlan(name, sx, sy, sz, 0, cells, palette.replaced.size(), List.copyOf(palette.unknown));
    }

    /** Une entrée de palette d'une structure, écrite comme une clé de schématique : {@code nom[clé=valeur,…]}. */
    static String key(CompoundTag entry) {
        StringBuilder b = new StringBuilder(entry.getString("Name"));
        CompoundTag props = entry.getCompound("Properties");
        if (!props.isEmpty()) {
            b.append('[');
            boolean first = true;
            for (String k : props.getAllKeys()) {
                if (!first) b.append(',');
                b.append(k).append('=').append(props.getString(k));
                first = false;
            }
            b.append(']');
        }
        return b.toString();
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
        Palette palette = new Palette();
        for (String key : paletteTag.getAllKeys()) palette.set(paletteTag.getInt(key), key);
        int[] indices = Varints.decode(data, w * h * l);
        Map<Integer, CompoundTag> entities = new HashMap<>();
        ListTag be = blocks.contains("BlockEntities", Tag.TAG_LIST) ? blocks.getList("BlockEntities", Tag.TAG_COMPOUND)
                : s.getList("TileEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < be.size(); i++) {
            CompoundTag e = be.getCompound(i);
            int[] pos = e.getIntArray("Pos");
            if (pos.length != 3) continue;
            int cell = pos[0] + pos[2] * w + pos[1] * w * l;
            if (cell < 0 || cell >= indices.length) continue;
            int idx = indices[cell];
            // L'entité d'un bloc qu'on a remplacé n'a plus de sens : le bloc d'arrivée garde les siennes.
            if (idx < 0 || idx >= palette.states.size() || palette.states.get(idx) == null || palette.extra.get(idx) != null) continue;
            String beId = e.getString("Id");
            String ns = BuiltInRegistries.BLOCK.getKey(palette.states.get(idx).getBlock()).getNamespace();
            if (beId.contains(":") && !beId.substring(0, beId.indexOf(':')).equals(ns)) continue;
            CompoundTag tag = version >= 3 && e.contains("Data", Tag.TAG_COMPOUND) ? e.getCompound("Data").copy() : e.copy();
            tag.remove("Pos");
            tag.remove("Id");
            tag.putString("id", e.getString("Id"));
            entities.put(cell, tag);
        }
        orient(indices, palette, w, h, l);
        int groundY = ground(indices, palette, w, h, l);
        BlockState[] states = palette.states.toArray(new BlockState[0]);
        String[] ids = palette.ids.toArray(new String[0]);
        CompoundTag[] extra = palette.extra.toArray(new CompoundTag[0]);
        if ((long) w * h * l > lazyVolume) {
            return new RawPlan(name, w, h, l, groundY, new GridCells(w, h, l, indices, states, ids, extra, entities),
                    palette.replaced.size(), List.copyOf(palette.unknown));
        }
        List<RawPlan.Cell> cells = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int i = x + z * w + y * w * l;
                    int idx = indices[i];
                    if (idx < 0 || idx >= states.length || states[idx] == null) continue;
                    CompoundTag tag = entities.containsKey(i) ? entities.get(i) : extra[idx];
                    cells.add(new RawPlan.Cell(x, y, z, states[idx], ids[idx], tag));
                }
            }
        }
        return new RawPlan(name, w, h, l, groundY, cells, palette.replaced.size(), List.copyOf(palette.unknown));
    }

    /**
     * Les colombages remplacés se tournent selon leur mur (P38) : pris entre deux voisins est–ouest, le mur court
     * d'est en ouest et le motif regarde le nord ; entre deux voisins nord–sud, il regarde l'est.
     */
    static void orient(int[] indices, Palette palette, int w, int h, int l) {
        for (int i = 0; i < indices.length; i++) {
            if (!palette.orients(indices[i])) continue;
            int x = i % w, z = (i / w) % l;
            boolean ew = x > 0 && x < w - 1 && palette.solid(indices[i - 1]) && palette.solid(indices[i + 1]);
            boolean ns = z > 0 && z < l - 1 && palette.solid(indices[i - w]) && palette.solid(indices[i + w]);
            if (ew == ns) continue;
            indices[i] = palette.variant(indices[i], ew ? Direction.NORTH : Direction.EAST);
        }
    }

    /**
     * La couche du sol (P38) : un plan qui a apporté son terrain (une ville sur sa colline) a de la terre sur ses
     * bords. On prend, sur chaque colonne du pourtour, la plus haute couche de sol, et la médiane de ces hauteurs ;
     * sans sol sur la plupart des bords (un bâtiment seul), la couche du bas.
     */
    static int ground(int[] indices, Palette palette, int w, int h, int l) {
        List<Integer> tops = new ArrayList<>();
        int columns = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < l; z++) {
                if (x != 0 && x != w - 1 && z != 0 && z != l - 1) continue;
                columns++;
                for (int y = h - 1; y >= 0; y--) {
                    int idx = indices[x + z * w + y * w * l];
                    if (idx >= 0 && idx < palette.ids.size() && palette.ids.get(idx) != null && SOILS.contains(palette.ids.get(idx))) {
                        tops.add(y);
                        break;
                    }
                }
            }
        }
        return groundOf(tops, columns);
    }

    /** La médiane des hauteurs de sol, si assez de colonnes du bord en ont ; sinon 0. */
    static int groundOf(List<Integer> tops, int columns) {
        if (columns == 0 || tops.size() < columns * TERRAIN_SHARE) return 0;
        List<Integer> sorted = new ArrayList<>(tops);
        sorted.sort(Integer::compare);
        return sorted.get(sorted.size() / 2);
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

    /** Pour les essais : abaisse le seuil des cases lues à la demande, et le rend. */
    public static int setLazyVolume(int volume) {
        int before = lazyVolume;
        lazyVolume = volume;
        return before;
    }

    /** Les identifiants laissés de côté, joints pour un message. */
    public static String join(List<String> ids, int max) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (String id : ids) seen.put(id, true);
        List<String> list = new ArrayList<>(seen.keySet());
        String out = String.join(", ", list.subList(0, Math.min(max, list.size())));
        return list.size() > max ? out + "…" : out;
    }
}
