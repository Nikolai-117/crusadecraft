package fr.crusadecraft.core.city;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les villes vivantes (P38). Une ville (une construction du registre : celle posée au plan d'architecte, une ville
 * importée d'une schématique, un village) reçoit au bâton d'intendant des bâtiments et leur fonction. Chaque fonction
 * amène ses gens, inscrits au registre des lignées dans la communauté de la ville : des familles dans les maisons, un
 * boulanger à la boulangerie, des gardes à la caserne. Ils apparaissent en villageois de Comes Alive quand un joueur
 * approche, passent la journée à leur ouvrage (les familles sur la place), rentrent le soir ; un marchand mort est
 * remplacé le lendemain, et les étals se regarnissent chaque jour.
 */
public final class CityService {
    /** Au plus, les gens qu'une ville fait vivre (familles et métiers) : les villageois de Comes Alive pèsent lourd. */
    public static final int PEOPLE_MAX = 80;
    /** Au plus, les gens qui apparaissent d'un coup (toutes les cinq secondes), par ville. */
    static final int SPAWN_PER_PASS = 4;
    /** Un joueur à moins de cette distance du bord de la ville la fait vivre. */
    static final int NEAR = 48;
    /** Les bâtiments qu'une reconnaissance ajoute au plus. */
    static final int RECOGNIZE_MAX = 200;
    /** La marque des gens d'une ville, pour retirer un villageois resté d'un bâtiment qui n'a plus de fonction. */
    public static final String TAG = "crusadecraft_ville";

    /** Le jour (du monde) où l'on a vu mort un marchand : il est remplacé le lendemain. */
    private static final Map<UUID, Long> DEAD_SINCE = new HashMap<>();
    /** Le dernier jour où les étals d'une ville ont été regarnis. */
    private static final Map<UUID, Long> RESTOCKED = new HashMap<>();

    private CityService() {}

    /**
     * P39 : les villageois que la ville a fait apparaître. Posé dans un tronçon à peine chargé, un villageois n'est
     * trouvable par le monde que quelques ticks plus tard : on le garde sous la main pour pouvoir le renvoyer.
     */
    private static final Map<UUID, java.lang.ref.WeakReference<Entity>> SPAWNED = new HashMap<>();

    public static void onServerStopped() {
        DEAD_SINCE.clear();
        RESTOCKED.clear();
        SPAWNED.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ les villes

    /** Une construction où l'on vit : un village (et toute ville posée au plan), un quartier, un donjon. */
    public static boolean city(Site site) {
        return site.kind() == SiteKind.VILLAGE || site.kind() == SiteKind.QUARTIER || site.kind() == SiteKind.DONJON;
    }

    /** La boîte d'une ville : {x0, y0, z0, x1, y1, z1}. */
    public static int[] box(Site site) {
        int half = site.extent() / 2 + 4;
        BlockPos a = site.anchor();
        int y0, y1;
        if (site.imported()) {
            y0 = a.getY() - site.importedGround() - 2;
            y1 = a.getY() - site.importedGround() + site.importedHeight() + 2;
        } else {
            y0 = a.getY() - 16;
            y1 = a.getY() + 48;
        }
        return new int[]{a.getX() - half, y0, a.getZ() - half, a.getX() + half, y1, a.getZ() + half};
    }

    /** Dans la ville : sur son emprise, de sa cave jusqu'à bien au-dessus de ses toits (on la survole aussi). */
    public static boolean inside(Site site, BlockPos p) {
        int[] b = box(site);
        return p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] - 8 && p.getY() <= b[4] + ABOVE && p.getZ() >= b[2] && p.getZ() <= b[5];
    }

    /** Ce qu'on compte encore dans la ville au-dessus de ses toits. */
    static final int ABOVE = 96;

    /** La ville où est ce point (la plus petite, si elles se chevauchent). */
    public static Optional<Site> cityAt(MinecraftServer server, ResourceLocation dim, BlockPos pos) {
        Site best = null;
        for (Site s : data(server).world().sites()) {
            if (!city(s) || !dim.equals(s.dimension()) || !inside(s, pos)) continue;
            if (best == null || s.extent() < best.extent()) best = s;
        }
        return Optional.ofNullable(best);
    }

    public static Optional<CityBuilding> buildingAt(Site site, BlockPos pos) {
        CityBuilding best = null;
        for (CityBuilding b : site.buildings()) {
            if (!b.contains(pos)) continue;
            long vol = volume(b);
            if (best == null || vol < volume(best)) best = b;
        }
        return Optional.ofNullable(best);
    }

    private static long volume(CityBuilding b) {
        return (long) (b.max().getX() - b.min().getX() + 1) * (b.max().getY() - b.min().getY() + 1) * (b.max().getZ() - b.min().getZ() + 1);
    }

    /** La place de la ville, où les familles passent la journée : le marché, la mairie, sinon le centre. */
    static BlockPos plaza(Site site) {
        for (BuildingFunction f : new BuildingFunction[]{BuildingFunction.MARCHE, BuildingFunction.MAIRIE}) {
            for (CityBuilding b : site.buildings()) if (b.function() == f) return b.spot();
        }
        return site.anchor().above();
    }

    /** Au plus, les places dehors qu'on cherche dans la ville. */
    static final int SPOTS = 160;

    /**
     * Des places dehors, dans les rues et sur les places autour de la place de la ville (P38) : chaque membre d'une famille
     * en reçoit une pour la journée, sans quoi tous convergeaient vers le même bloc et s'y entassaient. Ni sur un toit
     * (à moins de cinq blocs de la hauteur de la place), ni dans un bâtiment, et à ciel ouvert.
     */
    static List<BlockPos> outdoorSpots(ServerLevel level, Site site) {
        BlockPos c = plaza(site);
        List<BlockPos> out = new ArrayList<>();
        // En grille sur toute la ville : les familles se répartissent dans ses rues, pas autour d'un seul bloc.
        int step = Math.max(4, site.extent() / 14), radius = Math.max(16, site.extent() / 2);
        for (int r = 0; r <= radius && out.size() < SPOTS; r += step) {
            for (int dx = -r; dx <= r && out.size() < SPOTS; dx += step) {
                for (int dz = -r; dz <= r && out.size() < SPOTS; dz += step) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = c.getX() + dx, z = c.getZ() + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    BlockPos p = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (Math.abs(p.getY() - c.getY()) > SPOT_DY || !inside(site, p) || buildingAt(site, p).isPresent()) continue;
                    BlockState below = level.getBlockState(p.below());
                    if (!below.getFluidState().isEmpty() || !street(below) || !BuildingScan.standable(level, p)) continue;
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Une ville sur sa colline : ses rues montent et descendent autour de la place. */
    static final int SPOT_DY = 24;

    /** Un sol de rue ou de place, pas un toit ni un arbre : ni escalier, ni dalle, ni clôture, ni vitre, ni feuillage, ni tronc. */
    static boolean street(BlockState s) {
        net.minecraft.world.level.block.Block b = s.getBlock();
        return !(b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.SlabBlock
                || b instanceof net.minecraft.world.level.block.FenceBlock || b instanceof net.minecraft.world.level.block.WallBlock
                || b instanceof net.minecraft.world.level.block.IronBarsBlock || b instanceof net.minecraft.world.level.block.AbstractGlassBlock
                || b instanceof net.minecraft.world.level.block.LeavesBlock) && !s.is(net.minecraft.tags.BlockTags.LOGS);
    }

    // ------------------------------------------------------------------ reconnaître

    /**
     * Cherche les bâtiments d'une ville d'après ses portes : chaque porte qui mène à un intérieur couvert donne un
     * bâtiment, avec la fonction que ses meubles suggèrent (à valider). Renvoie le nombre de bâtiments ajoutés.
     */
    public static int recognize(ServerLevel level, Site site) {
        int[] box = box(site);
        int added = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = box[1]; y <= box[4] && added < RECOGNIZE_MAX; y++) {
            for (int z = box[2]; z <= box[5] && added < RECOGNIZE_MAX; z++) {
                for (int x = box[0]; x <= box[3] && added < RECOGNIZE_MAX; x++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
                    if (buildingAt(site, m).isPresent()) continue;
                    Optional<BuildingScan.Result> r = BuildingScan.fromClick(level, m.immutable(), Direction.UP);
                    if (r.isEmpty() || overlaps(site, r.get())) continue;
                    CityBuilding b = draft(r.get());
                    site.buildings().add(b);
                    added++;
                }
            }
        }
        if (added > 0) data(level.getServer()).setDirty();
        return added;
    }

    private static boolean overlaps(Site site, BuildingScan.Result r) {
        for (CityBuilding b : site.buildings()) if (b.overlap(r.min(), r.max()) > 0.5) return true;
        return false;
    }

    /** Un bâtiment reconnu, pas encore décidé : sa fonction est seulement proposée. */
    public static CityBuilding draft(BuildingScan.Result r) {
        CityBuilding b = new CityBuilding(UUID.randomUUID(), r.min(), r.max(), r.spot());
        b.setStats(r.floor(), r.levels(), r.furniture());
        b.setSuggestion(BuildingScan.suggest(r.furniture(), r.floor(), r.levels()));
        return b;
    }

    // ------------------------------------------------------------------ décider

    /** Qui peut tenir le bâton : le créatif, l'éditeur de la carte, ou le maître des lieux. */
    public static boolean allowed(ServerPlayer player, Site site) {
        if (PlanTool.allowed(player)) return true;
        return data(player.server).realm(site.owner()).map(r -> player.getUUID().equals(r.ruler())).orElse(false);
    }

    /**
     * Donne (ou change) la fonction d'un bâtiment, qui reçoit ses gens. Un bâtiment nouveau (pas encore dans la ville)
     * y entre avec la boîte donnée.
     */
    public static Outcome define(ServerPlayer player, UUID siteId, CityBuilding building, BuildingFunction function, String name) {
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (!allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        Site s = site.get();
        CityBuilding b = s.building(building.id()).orElse(null);
        if (b == null) {
            if (!inside(s, building.spot())) return Outcome.no("crusadecraft.city.outside");
            b = building;
            s.buildings().add(b);
        }
        b.setName(name == null ? "" : name.strip());
        if (b.function() != function) {
            dismiss(player.server, b);
            b.setFunction(function);
            if (function != null) settle(player.server, s, b, player.getRandom().nextLong());
            // Un marché, une mairie : la place de la ville est là désormais.
            if (function == BuildingFunction.MARCHE || function == BuildingFunction.MAIRIE) respread(player.server, s);
        }
        data(player.server).setDirty();
        if (function == null) return Outcome.ok(Component.translatable("crusadecraft.city.cleared"));
        int people = b.occupants().size();
        return Outcome.ok(people == 0 && full(player.server, s)
                ? Component.translatable("crusadecraft.city.defined.full", Component.translatable(function.key()), PEOPLE_MAX)
                : Component.translatable("crusadecraft.city.defined", Component.translatable(function.key()), people));
    }

    /** Retire un bâtiment de la ville : ses gens s'en vont. */
    public static Outcome forget(ServerPlayer player, UUID siteId, UUID buildingId) {
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (!allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        Optional<CityBuilding> b = site.get().building(buildingId);
        if (b.isEmpty()) return Outcome.no("crusadecraft.city.building.unknown");
        dismiss(player.server, b.get());
        site.get().buildings().remove(b.get());
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.forgotten"));
    }

    /** Toutes les fonctions proposées sont acceptées d'un coup. */
    public static Outcome acceptAll(ServerPlayer player, UUID siteId) {
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (!allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        int n = 0;
        for (CityBuilding b : site.get().buildings()) {
            if (b.function() != null || b.suggestion() == null) continue;
            b.setFunction(b.suggestion());
            settle(player.server, site.get(), b, player.getRandom().nextLong());
            n++;
        }
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.accepted", n, people(player.server, site.get())));
    }

    /** Reconnaît les bâtiments de la ville où est le joueur. */
    public static Outcome recognize(ServerPlayer player, UUID siteId) {
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        if (!allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, site.get().dimension()));
        if (level == null) return Outcome.no("crusadecraft.city.unknown");
        int n = recognize(level, site.get());
        return Outcome.ok(Component.translatable("crusadecraft.city.recognized", n, site.get().buildings().size()));
    }

    // ------------------------------------------------------------------ les gens

    /** Les vivants de la ville (ceux de ses bâtiments). */
    public static int people(MinecraftServer server, Site site) {
        LineageState state = data(server).lineage();
        int n = 0;
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) if (state.person(id).map(Person::alive).orElse(false)) n++;
        }
        return n;
    }

    static boolean full(MinecraftServer server, Site site) {
        return people(server, site) >= PEOPLE_MAX;
    }

    /** La communauté de la ville au registre (créée au besoin, vide). */
    static Community community(MinecraftServer server, Site site) {
        LineageState state = data(server).lineage();
        Optional<Community> c = state.community(site.community());
        if (c.isPresent()) return c.get();
        Community made = new LineageGenerator(site.id().getLeastSignificantBits()).community(state, site.name(), CommunityKind.VILLAGE, site.owner(), 0);
        site.setCommunity(made.id());
        return made;
    }

    /** Les gens qu'une fonction amène : des familles dans une maison, ceux qui y travaillent ailleurs. */
    static void settle(MinecraftServer server, Site site, CityBuilding b, long seed) {
        BuildingFunction f = b.function();
        if (f == null || full(server, site)) return;
        LineageState state = data(server).lineage();
        Community c = community(server, site);
        LineageGenerator gen = new LineageGenerator(seed);
        if (f.dwelling()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            List<BlockPos> spots = level == null ? List.of() : outdoorSpots(level, site);
            java.util.Random random = new java.util.Random(seed);
            for (Person p : gen.populate(state, c, BuildingFunction.families(b.floor()))) {
                // Sans rue trouvée autour de la place, la famille passe la journée devant chez elle : jamais tous au même bloc.
                p.setWork(spots.isEmpty() ? b.spot() : spots.get(random.nextInt(spots.size())));
                b.occupants().add(p.id());
            }
        } else {
            boolean men = f == BuildingFunction.CASERNE || f == BuildingFunction.TOUR || f == BuildingFunction.CHATEAU;
            for (int i = 0; i < f.postsFor(b.floor()).size(); i++) {
                Person p = gen.worker(state, c, f.role(), men);
                p.setWork(b.spot());
                b.occupants().add(p.id());
            }
        }
        data(server).setDirty();
    }

    /** Les gens d'un bâtiment s'en vont : du monde et du registre. */
    static void dismiss(MinecraftServer server, CityBuilding b) {
        LineageState state = data(server).lineage();
        for (UUID id : b.occupants()) {
            Optional<Person> p = state.person(id);
            if (p.isPresent() && p.get().inWorld()) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.get().worldDim()));
                Entity e = level == null ? null : level.getEntity(id);
                if (e == null && SPAWNED.containsKey(id)) e = SPAWNED.get(id).get();
                if (e != null && !e.isRemoved()) e.discard();
            }
            state.remove(id);
            SPAWNED.remove(id);
            DEAD_SINCE.remove(id);
        }
        b.occupants().clear();
        data(server).setDirty();
    }

    /** Le métier du {@code index}-ième de ceux qui travaillent dans ce bâtiment. */
    static String post(CityBuilding b, int index) {
        List<String> posts = b.function().postsFor(b.floor());
        return posts.isEmpty() ? "minecraft:none" : posts.get(Math.floorMod(index, posts.size()));
    }

    /**
     * Toutes les cinq secondes : dans chaque ville où un joueur passe, les gens qui manquent arrivent, un marchand mort
     * la veille est remplacé, et les étals se regarnissent une fois par jour.
     */
    public static void tick(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        LineageState state = data.lineage();
        for (Site site : new ArrayList<>(data.world().sites())) {
            if (site.buildings().isEmpty()) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            if (level == null || !watched(level, site)) continue;
            long day = level.getDayTime() / 24000L;
            int spawned = 0;
            boolean restock = !Long.valueOf(day).equals(RESTOCKED.get(site.id()));
            for (CityBuilding b : site.buildings()) {
                if (b.function() == null) continue;
                // P39 : une maison vide (toute sa famille morte) accueille une famille nouvelle le lendemain.
                if (b.function().dwelling() && !b.occupants().isEmpty() && b.occupants().stream().noneMatch(id -> state.person(id).map(Person::alive).orElse(false))) {
                    long seen = DEAD_SINCE.computeIfAbsent(b.id(), k -> day);
                    if (day > seen) {
                        DEAD_SINCE.remove(b.id());
                        b.occupants().clear();
                        settle(server, site, b, level.random.nextLong());
                    }
                    continue;
                }
                for (int i = 0; i < b.occupants().size(); i++) {
                    UUID id = b.occupants().get(i);
                    Optional<Person> p = state.person(id);
                    if (p.isEmpty()) continue;
                    if (!p.get().alive()) {
                        if (b.function().dwelling()) continue;
                        long seen = DEAD_SINCE.computeIfAbsent(id, k -> day);
                        if (day > seen) {
                            Person next = new LineageGenerator(level.random.nextLong()).worker(state, community(server, site), b.function().role(),
                                    b.function() == BuildingFunction.CASERNE || b.function() == BuildingFunction.TOUR);
                            next.setWork(b.spot());
                            b.occupants().set(i, next.id());
                            DEAD_SINCE.remove(id);
                            data.setDirty();
                        }
                        continue;
                    }
                    if (!p.get().inWorld()) {
                        if (spawned >= SPAWN_PER_PASS || !level.isLoaded(b.spot())) continue;
                        if (spawn(level, state, p.get(), b, i).isPresent()) {
                            spawned++;
                            data.setDirty();
                        }
                    } else if (restock && !b.function().dwelling()) {
                        Entity e = level.getEntity(id);
                        if (e instanceof Villager v) v.restock();
                    }
                }
            }
            if (restock) RESTOCKED.put(site.id(), day);
            sweep(level, site);
        }
    }

    /** Fait apparaître d'un coup tous les gens qui manquent (essais, et villes chargées sans joueur proche). */
    public static int populate(ServerLevel level, Site site) {
        return populateEntities(level, site).size();
    }

    /** Les mêmes, en rendant les villageois apparus. */
    public static List<Entity> populateEntities(ServerLevel level, Site site) {
        LineageState state = data(level.getServer()).lineage();
        List<Entity> out = new ArrayList<>();
        for (CityBuilding b : site.buildings()) {
            if (b.function() == null || !level.isLoaded(b.spot())) continue;
            for (int i = 0; i < b.occupants().size(); i++) {
                Optional<Person> p = state.person(b.occupants().get(i));
                if (p.isEmpty() || !p.get().alive() || p.get().inWorld()) continue;
                spawn(level, state, p.get(), b, i).ifPresent(out::add);
            }
        }
        if (!out.isEmpty()) data(level.getServer()).setDirty();
        return out;
    }

    /** P39 : les villageois d'une ville dont la personne n'est plus au registre s'en vont (un bâtiment qui a changé de fonction). */
    static int sweep(ServerLevel level, Site site) {
        int[] b = box(site);
        int n = 0;
        for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(b[0], b[1] - 8, b[2], b[3] + 1, b[4] + ABOVE, b[5] + 1),
                v -> v.getTags().contains(TAG))) {
            if (!isStray(v)) continue;
            v.discard();
            n++;
        }
        return n;
    }

    /** Un joueur est-il assez près de la ville pour qu'elle vive ? */
    static boolean watched(ServerLevel level, Site site) {
        int[] b = box(site);
        for (ServerPlayer p : level.players()) {
            if (p.getX() >= b[0] - NEAR && p.getX() <= b[3] + NEAR && p.getZ() >= b[2] - NEAR && p.getZ() <= b[5] + NEAR) return true;
        }
        return false;
    }

    /** Fait apparaître cette personne dans son bâtiment, en villageois de Comes Alive (sinon de Minecraft). */
    static Optional<Entity> spawn(ServerLevel level, LineageState state, Person p, CityBuilding b, int index) {
        BlockPos at = b.spot();
        Optional<Entity> e;
        if (McaBridge.isReady()) {
            McaBridge.syncTree(level, state, List.of(p));
            e = McaBridge.spawn(level, p, state.fullName(p), at);
        } else {
            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return Optional.empty();
            v.setUUID(p.id());
            v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null, null);
            v.setCustomName(Component.literal(state.fullName(p)));
            if (p.age() < 16) v.setAge(-24000);
            v.addTag(McaBridge.TAG);
            e = level.addFreshEntity(v) ? Optional.of(v) : Optional.empty();
        }
        if (e.isEmpty()) return e;
        e.get().addTag(TAG);
        SPAWNED.put(p.id(), new java.lang.ref.WeakReference<>(e.get()));
        if (!b.function().dwelling() && p.age() >= 16) CityTrades.apply(e.get(), b.function(), post(b, index), level.random);
        p.setInWorld(level.dimension().location(), at);
        return e;
    }

    /** Un villageois d'une ville dont la personne n'est plus au registre (son bâtiment a changé de fonction). */
    public static boolean isStray(Entity entity) {
        if (!entity.getTags().contains(TAG) || entity.getServer() == null) return false;
        Optional<Person> p = data(entity.getServer()).lineage().person(entity.getUUID());
        return p.isEmpty() || !p.get().alive();
    }

    // ------------------------------------------------------------------ une ville importée

    /**
     * P39 : fonder une ville là où l'on est, sur ce qui est déjà bâti : elle devient une ville du monde (du royaume
     * dont ce sont les terres, sinon une nation nouvelle), et ses bâtiments sont reconnus d'après leurs portes.
     */
    public static Outcome found(ServerPlayer player, String name, int radius) {
        if (!PlanTool.allowed(player)) return Outcome.no("crusadecraft.city.found.denied");
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || clean.length() > 48) return Outcome.no("crusadecraft.city.found.name");
        BlockPos at = player.blockPosition();
        if (cityAt(player.server, player.level().dimension().location(), at).isPresent()) return Outcome.no("crusadecraft.city.found.taken");
        int r = Math.max(FOUND_MIN, Math.min(FOUND_MAX, radius));
        BlockPos ground = at.below();
        Component joined = fr.crusadecraft.core.build.SiteService.registerFoundedTown(player, ground, clean, r);
        Optional<Site> site = cityAt(player.server, player.level().dimension().location(), at);
        if (site.isEmpty()) return new Outcome(false, joined);
        int n = recognize(player.serverLevel(), site.get());
        return Outcome.ok(Component.translatable("crusadecraft.city.found.done", joined, n));
    }

    /** L'emprise d'une ville fondée sur place, de son centre à son bord. */
    public static final int FOUND_MIN = 16, FOUND_MAX = 256, FOUND_DEFAULT = 64;

    /** La place a changé (un marché, une mairie) : les familles y vont désormais passer la journée. */
    static void respread(MinecraftServer server, Site site) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
        if (level == null) return;
        List<BlockPos> spots = outdoorSpots(level, site);
        if (spots.isEmpty()) return;
        LineageState state = data(server).lineage();
        java.util.Random random = new java.util.Random(site.id().getMostSignificantBits());
        for (CityBuilding b : site.buildings()) {
            if (b.function() == null || !b.function().dwelling()) continue;
            for (UUID id : b.occupants()) state.person(id).ifPresent(p -> p.setWork(spots.get(random.nextInt(spots.size()))));
        }
        data(server).setDirty();
    }

    /** La pose d'une ville importée est finie : on reconnaît ses bâtiments, et l'on dit au joueur quoi faire. */
    public static void onPasted(MinecraftServer server, Site site, ServerPlayer placer) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
        if (level == null) return;
        int n = recognize(level, site);
        CrusadeCraft.LOGGER.info("CrusadeCraft : ville {} posée, {} bâtiments reconnus", site.name(), n);
        if (placer != null) placer.sendSystemMessage(Component.translatable("crusadecraft.city.pasted", site.name(), n));
    }
}
