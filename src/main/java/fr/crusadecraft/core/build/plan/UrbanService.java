package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * L'atelier des villes (P60), côté serveur : il dresse les plans à la demande de l'écran, garde ceux qu'on prend pour les poser, et range les réglages qu'on enregistre. Rien ne
 * se fie à ce que le client envoie : les réglages sont rebornés à la lecture, et le plan est refait ici.
 */
public final class UrbanService {
    public static final String PREFIX = "@atelier:";
    /** On ne refait pas un plan plus souvent : un curseur qu'on fait glisser enverrait cent demandes. */
    private static final long MIN_MILLIS = 120;
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** Un plan pris pèse lourd : on n'en garde que quelques-uns d'avance. */
    private static final int MAX_DESIGNS = 6;

    private UrbanService() {}

    public static void onServerStopped() {
        LAST.clear();
    }

    /** Les plans du pack de ce style. */
    private record Library(NationStyle style, String pack, List<String> entries, Set<String> used) implements UrbanGenerator.Plans {
        @Override
        public Optional<UrbanGenerator.Pick> pick(TownSize.Role role, int level, int min, int max, Random random) {
            return TownPlanner.pick(pack, entries, role, level, min, max, random, used).map(p -> new UrbanGenerator.Pick(p.ref().encode(), p.sizeX(), p.sizeZ()));
        }

        @Override
        public Optional<WallSets.Ring> wallRing(boolean big, int x0, int z0, int x1, int z1) {
            return WallSets.kit(style, big ? TownSize.Walls.BIG : TownSize.Walls.PALISADE, pack, entries).map(k -> WallSets.assemble(k, x0, z0, x1, z1));
        }
    }

    /** Le plan de ces réglages ; vide si aucun pack du style n'est installé. */
    public static Optional<UrbanPlan> plan(UrbanParams params) {
        Optional<String> pack = PlanLibrary.packFor(params.style());
        if (pack.isEmpty()) return Optional.empty();
        return UrbanGenerator.generate(params, new Library(params.style(), pack.get(), PlanLibrary.packEntries(pack.get()), new HashSet<>()));
    }

    /** Refaire le plan pour l'écran : vrai si c'est permis maintenant. */
    public static boolean mayPreview(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < MIN_MILLIS) return false;
        LAST.put(player.getUUID(), now);
        return PlanTool.allowed(player);
    }

    // ------------------------------------------------------------------ prendre un plan

    /** On prend ce plan : il va dans le plan d'architecte en main, prêt à être posé. */
    public static Outcome take(ServerPlayer player, UrbanParams params) {
        if (!PlanTool.allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        Optional<ItemStack> held = PlanItem.held(player);
        if (held.isEmpty()) return Outcome.no("crusadecraft.plan.not_held");
        Optional<UrbanPlan> plan = plan(params);
        if (plan.isEmpty()) return Outcome.no("crusadecraft.town.no_pack");
        UUID id = UUID.randomUUID();
        Designs.get(player.server).put(id, plan.get().town(), params);
        String name = params.name().isBlank() ? Component.translatable("crusadecraft.urban.default_name").getString() : params.name();
        int rotation = PlanItem.choice(held.get()).map(PlanItem.Choice::rotation).orElse(0);
        TownPlan town = plan.get().town();
        PlanItem.setChoice(held.get(), new PlanItem.Choice(PREFIX + id, name, town.extent(), 32, town.extent(), 0, rotation, false, 0, 0, 0));
        return Outcome.ok(Component.translatable("crusadecraft.urban.taken", name, plan.get().stats().buildings()));
    }

    public static boolean isDesign(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    /** Le plan dressé qu'un plan d'architecte porte. On rend une copie : la pose relève le terrain sur ce plan-là, et l'on peut en poser plusieurs. */
    public static Optional<TownPlan> design(MinecraftServer server, String ref) {
        if (!isDesign(ref)) return Optional.empty();
        try {
            return Designs.get(server).town(UUID.fromString(ref.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<UrbanParams> designParams(MinecraftServer server, String ref) {
        if (!isDesign(ref)) return Optional.empty();
        try {
            return Designs.get(server).params(UUID.fromString(ref.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Les derniers plans pris, enregistrés avec le monde : on peut quitter le jeu entre le plan et la pose. */
    public static final class Designs extends SavedData {
        private static final String NAME = "crusadecraft_atelier";
        private final LinkedHashMap<UUID, CompoundTag> towns = new LinkedHashMap<>();
        private final Map<UUID, CompoundTag> settings = new HashMap<>();

        static Designs get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Designs::load, Designs::new, NAME);
        }

        void put(UUID id, TownPlan town, UrbanParams params) {
            towns.put(id, town.save());
            settings.put(id, params.save());
            while (towns.size() > MAX_DESIGNS) {
                UUID oldest = towns.keySet().iterator().next();
                towns.remove(oldest);
                settings.remove(oldest);
            }
            setDirty();
        }

        Optional<TownPlan> town(UUID id) {
            return Optional.ofNullable(towns.get(id)).map(TownPlan::load);
        }

        Optional<UrbanParams> params(UUID id) {
            return Optional.ofNullable(settings.get(id)).map(UrbanParams::load);
        }

        static Designs load(CompoundTag t) {
            Designs d = new Designs();
            ListTag l = t.getList("designs", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                UUID id = c.getUUID("id");
                d.towns.put(id, c.getCompound("town"));
                d.settings.put(id, c.getCompound("params"));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            towns.forEach((id, town) -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("id", id);
                c.put("town", town);
                c.put("params", settings.getOrDefault(id, new CompoundTag()));
                l.add(c);
            });
            t.put("designs", l);
            return t;
        }
    }

    // ------------------------------------------------------------------ les réglages qu'on enregistre

    /** Hors du monde : un réglage réussi sert aussi dans le monde d'à côté. */
    private static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("crusadecraft").resolve("reglages_villes");
    }

    /** Un nom de fichier sûr : pas de chemin, pas de caractère interdit. C'est le serveur qui écrit, et le nom vient du client. */
    static String safeName(String name) {
        String s = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} _-]", "").replaceAll("\\s+", "_");
        return s.length() > UrbanParams.NAME_MAX ? s.substring(0, UrbanParams.NAME_MAX) : s;
    }

    public static List<String> savedNames() {
        List<String> out = new ArrayList<>();
        Path dir = folder();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*.nbt")) {
            for (Path p : s) {
                String n = p.getFileName().toString();
                out.add(n.substring(0, n.length() - 4));
            }
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : réglages de villes illisibles dans {}", dir, e);
        }
        out.sort(String::compareTo);
        return out;
    }

    public static Outcome save(ServerPlayer player, String name, UrbanParams params) {
        if (!PlanTool.allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        String safe = safeName(name);
        if (safe.isEmpty()) return Outcome.no("crusadecraft.urban.saved.no_name");
        try {
            Files.createDirectories(folder());
            UrbanParams clean = params.copy();
            clean.clearEdits();
            NbtIo.writeCompressed(clean.save(), folder().resolve(safe + ".nbt").toFile());
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : réglage de ville non enregistré", e);
            return Outcome.no("crusadecraft.urban.saved.failed");
        }
        return Outcome.ok(Component.translatable("crusadecraft.urban.saved", safe));
    }

    public static Optional<UrbanParams> loadSaved(String name) {
        Path file = folder().resolve(safeName(name) + ".nbt");
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.of(UrbanParams.load(NbtIo.readCompressed(file.toFile())));
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : réglage de ville illisible : {}", file, e);
            return Optional.empty();
        }
    }

    public static Outcome delete(ServerPlayer player, String name) {
        if (!PlanTool.allowed(player)) return Outcome.no("crusadecraft.plan.denied");
        try {
            Files.deleteIfExists(folder().resolve(safeName(name) + ".nbt"));
        } catch (IOException e) {
            return Outcome.no("crusadecraft.urban.saved.failed");
        }
        return Outcome.ok(Component.translatable("crusadecraft.urban.deleted", safeName(name)));
    }
}
