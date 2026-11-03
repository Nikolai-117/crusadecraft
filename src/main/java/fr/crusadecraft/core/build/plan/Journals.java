package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les journaux des constructions posées d'après un plan (P34), et ce qui avance à chaque tick : les plans posés à
 * l'outil d'architecte (quelques milliers de blocs par tick), et les constructions qu'on défait (le terrain revient,
 * du dernier bloc posé au premier). Tout est enregistré avec le monde : une pose ou un retour interrompus reprennent
 * au démarrage suivant.
 */
public final class Journals extends SavedData {
    private static final String NAME = "crusadecraft_journaux";
    /** Blocs changés par tick pour les poses de l'outil, et pour les retours. */
    static final int PASTE_BUDGET = 3000, UNDO_BUDGET = 2500;
    /** Poses de l'outil qu'un joueur peut défaire, les dernières. */
    static final int STACK = 16;

    /** Une pose de l'outil d'architecte en cours : un plan, ou une ville entière. */
    public static final class Job {
        final UUID id, player;
        final ResourceLocation dim;
        final BlockPos anchor;
        final int rotation;
        final String ref;
        final TownPlan town;
        final String name;
        int progress;

        Job(UUID id, UUID player, ResourceLocation dim, BlockPos anchor, int rotation, String ref, TownPlan town, String name) {
            this.id = id;
            this.player = player;
            this.dim = dim;
            this.anchor = anchor.immutable();
            this.rotation = Math.floorMod(rotation, 4);
            this.ref = ref;
            this.town = town;
            this.name = name;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            if (player != null) t.putUUID("player", player);
            t.putString("dim", dim.toString());
            t.put("anchor", NbtUtils.writeBlockPos(anchor));
            t.putInt("rot", rotation);
            if (ref != null) t.putString("ref", ref);
            if (town != null) t.put("town", town.save());
            t.putString("name", name);
            t.putInt("progress", progress);
            return t;
        }

        static Job load(CompoundTag t) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            Job j = new Job(t.getUUID("id"), t.hasUUID("player") ? t.getUUID("player") : null,
                    dim == null ? ResourceLocation.tryParse("minecraft:overworld") : dim, NbtUtils.readBlockPos(t.getCompound("anchor")),
                    t.getInt("rot"), t.contains("ref") ? t.getString("ref") : null, t.contains("town") ? TownPlan.load(t.getCompound("town")) : null,
                    t.getString("name"));
            j.progress = t.getInt("progress");
            return j;
        }
    }

    private final List<Job> jobs = new ArrayList<>();
    private final List<UUID> undoing = new ArrayList<>();
    private final Map<UUID, ResourceLocation> dims = new HashMap<>();
    private final Map<UUID, List<UUID>> stacks = new LinkedHashMap<>();
    private static final Map<UUID, List<Blueprints.Placement>> PLACEMENTS = new HashMap<>();
    /** P39.2 : jusqu'où les fondations d'une pose descendent (une ville entière : jusqu'au sol). */
    private static final Map<UUID, Integer> DEPTHS = new HashMap<>();

    public Journals() {}

    public static Journals get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Journals::load, Journals::new, NAME);
    }

    private static String fileName(UUID id) {
        return "crusadecraft_journal_" + id;
    }

    /** Le journal d'une construction, dans ce monde (créé s'il manque). */
    public static BuildJournal journal(MinecraftServer server, UUID id, ResourceLocation dim) {
        Journals index = get(server);
        if (!dim.equals(index.dims.put(id, dim))) index.setDirty();
        return server.overworld().getDataStorage().computeIfAbsent(BuildJournal::load, BuildJournal::new, fileName(id));
    }

    public static void onServerStopped() {
        PLACEMENTS.clear();
        DEPTHS.clear();
    }

    // ------------------------------------------------------------------ défaire

    /** Défait une construction : ses blocs reviennent peu à peu. Renvoie faux si elle n'a pas de journal. */
    public static boolean undo(MinecraftServer server, UUID id) {
        Journals index = get(server);
        if (!index.dims.containsKey(id)) return false;
        index.jobs.removeIf(j -> j.id.equals(id));
        PLACEMENTS.remove(id);
        if (!index.undoing.contains(id)) index.undoing.add(id);
        index.setDirty();
        return true;
    }

    /** Une pose de l'outil est-elle encore en cours ? */
    public static boolean pasting(MinecraftServer server, UUID id) {
        for (Job j : get(server).jobs) if (j.id.equals(id)) return true;
        return false;
    }

    /** P35 : on garde ce qui est bâti pour de bon — le journal s'efface, on ne pourra plus le défaire. */
    public static void discard(MinecraftServer server, UUID id) {
        Journals index = get(server);
        index.jobs.removeIf(j -> j.id.equals(id));
        index.undoing.remove(id);
        ResourceLocation dim = index.dims.remove(id);
        for (List<UUID> stack : index.stacks.values()) stack.remove(id);
        PLACEMENTS.remove(id);
        index.setDirty();
        if (dim != null) forget(server, id, journal(server, id, dim));
        index.dims.remove(id);
    }

    public static boolean undoing(MinecraftServer server, UUID id) {
        return get(server).undoing.contains(id);
    }

    /** Blocs encore à remettre, toutes constructions confondues. */
    public static int pendingUndo(MinecraftServer server) {
        int n = 0;
        for (UUID id : get(server).undoing) n += journal(server, id, get(server).dims.get(id)).size();
        return n;
    }

    // ------------------------------------------------------------------ l'outil d'architecte

    /** Lance la pose d'un plan (ou d'une ville) à l'outil ; elle avance de quelques milliers de blocs par tick. */
    public static UUID paste(ServerPlayer player, BlockPos anchor, int rotation, String ref, TownPlan town, String name) {
        Journals index = get(player.server);
        UUID id = UUID.randomUUID();
        ResourceLocation dim = player.serverLevel().dimension().location();
        index.jobs.add(new Job(id, player.getUUID(), dim, anchor, rotation, ref, town, name));
        index.dims.put(id, dim);
        List<UUID> stack = index.stacks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        stack.add(id);
        while (stack.size() > STACK) stack.remove(0);
        index.setDirty();
        return id;
    }

    /** Défait la dernière pose de ce joueur. */
    public static Optional<UUID> undoLast(ServerPlayer player) {
        Journals index = get(player.server);
        List<UUID> stack = index.stacks.get(player.getUUID());
        if (stack == null || stack.isEmpty()) return Optional.empty();
        UUID id = stack.remove(stack.size() - 1);
        index.setDirty();
        undo(player.server, id);
        return Optional.of(id);
    }

    // ------------------------------------------------------------------ chaque tick

    public static void tick(MinecraftServer server) {
        Journals index = get(server);
        if (index.jobs.isEmpty() && index.undoing.isEmpty()) return;
        int budget = PASTE_BUDGET;
        for (Job job : new ArrayList<>(index.jobs)) {
            if (budget <= 0) break;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, job.dim));
            if (level == null || !level.hasChunkAt(job.anchor)) continue;
            List<Blueprints.Placement> plan = PLACEMENTS.computeIfAbsent(job.id, id -> compile(level, job));
            int[] r = PlanPlacer.advance(level, job.anchor, Rotation.values()[job.rotation], plan, job.progress, budget,
                    journal(server, job.id, job.dim), DEPTHS.getOrDefault(job.id, PlanPlacer.FOUNDATION_DEPTH));
            job.progress = r[0];
            budget -= Math.max(1, r[1]);
            index.setDirty();
            ServerPlayer owner = job.player == null ? null : server.getPlayerList().getPlayer(job.player);
            if (owner != null && (server.getTickCount() % 5 == 0 || job.progress >= plan.size())) {
                fr.crusadecraft.network.CrusadeNetwork.toPlayer(owner, new fr.crusadecraft.network.packet.PlanPackets.Progress(job.name,
                        plan.isEmpty() ? 100 : Math.min(100, job.progress * 100 / plan.size())));
            }
            if (job.progress >= plan.size()) {
                index.jobs.remove(job);
                PLACEMENTS.remove(job.id);
                DEPTHS.remove(job.id);
                ServerPlayer p = job.player == null ? null : server.getPlayerList().getPlayer(job.player);
                if (p != null) p.sendSystemMessage(Component.translatable("crusadecraft.plan.done", job.name));
                // Une ville du monde (posée au plan, ou importée d'un fichier) : ses habitants, ses bâtiments.
                fr.crusadecraft.core.build.SiteService.onPasted(server, job.id, p);
                CrusadeCraft.LOGGER.info("CrusadeCraft : plan {} posé en {} ({} étapes)", job.name, job.anchor, plan.size());
            }
        }
        int undo = UNDO_BUDGET;
        for (UUID id : new ArrayList<>(index.undoing)) {
            if (undo <= 0) break;
            ResourceLocation dim = index.dims.get(id);
            ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level == null) {
                index.undoing.remove(id);
                continue;
            }
            BuildJournal j = journal(server, id, dim);
            undo -= Math.max(1, j.undo(level, undo));
            if (j.isEmpty()) {
                index.undoing.remove(id);
                index.dims.remove(id);
                forget(server, id, j);
                CrusadeCraft.LOGGER.info("CrusadeCraft : construction {} défaite, le terrain est revenu", id);
            }
            index.setDirty();
        }
    }

    private static List<Blueprints.Placement> compile(ServerLevel level, Job job) {
        if (job.town != null) {
            if (job.progress == 0) moveAside(level, job.anchor, job.town.extent() / 2 + 2, job.anchor.getY() - 8, job.anchor.getY() + 40);
            TownPlanner.survey(level, job.anchor, job.town);
            return TownPlanner.placements(job.town);
        }
        Optional<Plan> plan = PlanRef.decode(job.ref).flatMap(r -> PlanLibrary.load(r, true));
        if (plan.isPresent() && plan.get().city()) DEPTHS.put(job.id, PlanPlacer.CITY_FOUNDATION_DEPTH);
        if (plan.isPresent() && job.progress == 0) {
            int bottom = job.anchor.getY() - plan.get().groundY();
            moveAside(level, job.anchor, plan.get().footprint() / 2 + 2, bottom - 1, bottom + plan.get().sizeY() + PlanBuilder.CLEARING);
        }
        return plan.map(Plan::placements).orElse(List.of());
    }

    /**
     * P38 : les bêtes et les gens qui se trouvent sur l'emprise d'une pose sont écartés au-delà du bord le plus proche,
     * posés sur le sol : sans quoi la ville (et sa colline) les ensevelit et ils meurent étouffés.
     */
    static int moveAside(ServerLevel level, BlockPos anchor, int half, int y0, int y1) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(anchor.getX() - half, y0, anchor.getZ() - half,
                anchor.getX() + half + 1, y1, anchor.getZ() + half + 1);
        int n = 0;
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            double dx = e.getX() - anchor.getX(), dz = e.getZ() - anchor.getZ();
            double x = e.getX(), z = e.getZ();
            if (Math.abs(dx) >= Math.abs(dz)) x = anchor.getX() + (dx < 0 ? -1 : 1) * (half + 4.5);
            else z = anchor.getZ() + (dz < 0 ? -1 : 1) * (half + 4.5);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            e.teleportTo(x, y, z);
            n++;
        }
        if (n > 0) CrusadeCraft.LOGGER.info("CrusadeCraft : {} bêtes et gens écartés de l'emprise d'une pose en {}", n, anchor);
        return n;
    }

    /** Un journal vidé : son fichier s'efface. */
    private static void forget(MinecraftServer server, UUID id, BuildJournal j) {
        j.clear();
        j.setDirty(false);
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(fileName(id) + ".dat");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : le journal {} ne s'efface pas", file, e);
        }
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        ListTag j = new ListTag();
        for (Job job : jobs) j.add(job.save());
        t.put("jobs", j);
        ListTag u = new ListTag();
        for (UUID id : undoing) u.add(NbtUtils.createUUID(id));
        t.put("undoing", u);
        CompoundTag d = new CompoundTag();
        for (Map.Entry<UUID, ResourceLocation> e : dims.entrySet()) d.putString(e.getKey().toString(), e.getValue().toString());
        t.put("dims", d);
        CompoundTag s = new CompoundTag();
        for (Map.Entry<UUID, List<UUID>> e : stacks.entrySet()) {
            ListTag l = new ListTag();
            for (UUID id : e.getValue()) l.add(NbtUtils.createUUID(id));
            s.put(e.getKey().toString(), l);
        }
        t.put("stacks", s);
        return t;
    }

    public static Journals load(CompoundTag t) {
        Journals j = new Journals();
        ListTag jobs = t.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) j.jobs.add(Job.load(jobs.getCompound(i)));
        ListTag u = t.getList("undoing", Tag.TAG_INT_ARRAY);
        for (Tag x : u) j.undoing.add(NbtUtils.loadUUID(x));
        CompoundTag d = t.getCompound("dims");
        for (String k : d.getAllKeys()) {
            ResourceLocation rl = ResourceLocation.tryParse(d.getString(k));
            try {
                if (rl != null) j.dims.put(UUID.fromString(k), rl);
            } catch (IllegalArgumentException ignored) {
                // clé illisible
            }
        }
        CompoundTag s = t.getCompound("stacks");
        for (String k : s.getAllKeys()) {
            try {
                List<UUID> l = new ArrayList<>();
                for (Tag x : s.getList(k, Tag.TAG_INT_ARRAY)) l.add(NbtUtils.loadUUID(x));
                j.stacks.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) {
                // clé illisible
            }
        }
        return j;
    }
}
