package fr.crusadecraft.core.build;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.BornageStaffItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les constructions des royaumes (P14) : les poser, les bâtir, les relier aux villes.
 *
 * <p>L'éditeur de la carte pose tout, pour n'importe quel royaume, d'un coup ou en chantier. En survie, un joueur
 * bâtit pour son propre royaume, sur ses terres, des constructions militaires payées d'avance par le trésor ; elles
 * s'élèvent peu à peu. Un village fonde une ville et sa communauté (et, avec Comes Alive, ses habitants) ; un donjon
 * loge le souverain d'une ville existante. Jamais une colonie MineColonies.
 */
public final class SiteService {
    /** Blocs posés au plus par tick pour les constructions instantanées : un fort entier en une seconde, sans à-coup. */
    private static final int INSTANT_BUDGET = 1200;
    /** Un donjon se pose à moins de 96 blocs d'une ville de son royaume. */
    private static final int KEEP_RANGE = 96;
    /** Colonne de fondation : on comble le terrain jusqu'à huit blocs sous le sol. */
    private static final int FOUNDATION_DEPTH = 8;

    private static final Map<UUID, List<Blueprints.Placement>> PLANS = new HashMap<>();

    private SiteService() {}

    public static void onServerStopped() {
        PLANS.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Le plan d'une construction, gardé en mémoire le temps du chantier (P34 : celui de sa ville, d'après les packs). */
    static List<Blueprints.Placement> plan(Site site) {
        return PLANS.computeIfAbsent(site.id(), id -> site.town() != null
                ? fr.crusadecraft.core.build.plan.TownPlanner.placements(site.town())
                : Blueprints.plan(site.kind(), Banners.dye(site.color())));
    }

    /** « Fort de Valcourt » : la nature de la construction et le lieu qui lui donne son nom. */
    public static Component displayName(Site site) {
        return Component.translatable(AiNames.elidesPublic(site.name()) ? "crusadecraft.site.named.elided" : "crusadecraft.site.named",
                Component.translatable(site.kind().key()), site.name());
    }

    // ------------------------------------------------------------------ poser

    /** Le bâton en mode « Construire » : la construction choisie, pour le royaume du bâton, au bloc visé. */
    public static Outcome placeFromStaff(ServerPlayer player, ItemStack stack, BlockPos ground, Direction look) {
        Optional<UUID> owner = BornageStaffItem.owner(stack);
        if (owner.isEmpty()) return Outcome.no("crusadecraft.site.no_owner");
        return place(player, BornageStaffItem.build(stack), owner.get(), ground, look.getOpposite(), BornageStaffItem.instant(stack));
    }

    /**
     * Pose une construction, son entrée tournée vers {@code front}, son sol au niveau de {@code ground}. Revérifie tout :
     * les droits, le royaume, la place, et en survie le terrain et l'or.
     */
    public static Outcome place(ServerPlayer player, SiteKind kind, UUID owner, BlockPos ground, Direction front, boolean instant) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = data(server);
        ServerLevel level = player.serverLevel();
        boolean editor = WorldEditService.canEdit(player);
        Optional<AiRealm> ai = data.world().realm(owner);
        Optional<Realm> human = data.realm(owner);
        if (ai.isEmpty() && human.isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        long cost = 0;
        Realm payer = null;
        if (!editor) {
            if (!CrusadeConfig.SERVER.survivalBuilding.get()) return Outcome.no("crusadecraft.site.survival_off");
            Optional<Realm> mine = RealmService.realmOf(server, player.getUUID());
            if (mine.isEmpty()) return Outcome.no("crusadecraft.realm.none");
            if (!owner.equals(mine.get().id())) return Outcome.no("crusadecraft.site.only_yours");
            if (!kind.survival()) return Outcome.no("crusadecraft.site.editor_only", Component.translatable(kind.key()));
            boolean onOwnLand = Territory.ownerAt(server, level.dimension().location(), ground.getX() + 0.5, ground.getZ() + 0.5)
                    .map(owner::equals).orElse(false);
            if (!onOwnLand) return Outcome.no("crusadecraft.site.not_your_land");
            instant = false;
            cost = kind.cost();
            payer = mine.get();
            if (payer.gold() < cost) return Outcome.no("crusadecraft.site.no_gold", cost);
        }
        // Une construction ne se pose pas sur une autre.
        for (Site other : data.world().sites()) {
            if (!other.dimension().equals(level.dimension().location())) continue;
            double room = (kind.size() + other.extent()) / 2.0 + 3;
            if (Math.abs(other.anchor().getX() - ground.getX()) < room && Math.abs(other.anchor().getZ() - ground.getZ()) < room) {
                return Outcome.no("crusadecraft.site.too_close", displayName(other));
            }
        }
        int color = ai.map(AiRealm::color).orElseGet(() -> human.get().color());
        long today = RealmService.currentDay(server);
        Site site = new Site(UUID.randomUUID(), kind, nearestPlace(server, level, ground), owner, level.dimension().location(), ground, front,
                color, instant, today);
        if (kind == SiteKind.DONJON) {
            Optional<AiProvince> home = nearestOwnProvince(server, owner, level, ground);
            if (home.isEmpty()) return Outcome.no("crusadecraft.site.keep_far");
            home.get().setSeat(ground);
            site.setProvince(home.get().id());
            site.setName(home.get().name());
        } else if (kind == SiteKind.VILLAGE) {
            String name = new LineageGenerator(ground.asLong() ^ today).placeName();
            Optional<AiProvince> city = WorldEditService.addCity(server, level, owner, name, WorldEditService.CitySize.BOURG, ground.getX(), ground.getZ());
            if (city.isEmpty()) return Outcome.no("crusadecraft.mapedit.city.no_host");
            site.setProvince(city.get().id());
            site.setName(name);
            Community c = new LineageGenerator(ground.asLong() * 31 + today).community(data.lineage(), name, CommunityKind.VILLAGE, owner, 4);
            site.setCommunity(c.id());
        }
        if (payer != null) TreasuryService.spend(server, payer, LedgerCategory.CONSTRUCTIONS, cost);
        data.world().add(site);
        data.setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} posé en {} ({})", kind, ground, instant ? "instantané" : "chantier");
        return Outcome.ok(Component.translatable(instant ? "crusadecraft.site.placed" : cost > 0 ? "crusadecraft.site.started.paid" : "crusadecraft.site.started",
                displayName(site), cost));
    }

    /**
     * Le nom de la ville la plus proche (à moins de 600 blocs), sinon un nom de lieu nouveau. Les colonies des
     * royaumes des joueurs comptent comme les villes IA : un fort posé à côté de Valbrume est celui de Valbrume.
     */
    private static String nearestPlace(MinecraftServer server, ServerLevel level, BlockPos pos) {
        String best = null;
        double bestD = 600.0 * 600.0;
        ResourceLocation dim = level.dimension().location();
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(dim)) continue;
                double d = p.distanceSqr(pos.getX(), pos.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = p.name();
                }
            }
        }
        if (MineColoniesBridge.isReady()) {
            for (Realm realm : data(server).realms()) {
                List<ColonyRef> colonies = new java.util.ArrayList<>(realm.provinces());
                if (!colonies.contains(realm.capital())) colonies.add(realm.capital());
                for (ColonyRef ref : colonies) {
                    if (!ref.dimension().equals(dim)) continue;
                    Optional<BlockPos> center = MineColoniesBridge.center(ref);
                    if (center.isEmpty()) continue;
                    double dx = center.get().getX() - pos.getX(), dz = center.get().getZ() - pos.getZ();
                    if (dx * dx + dz * dz < bestD) {
                        String name = MineColoniesBridge.info(ref).name();
                        if (name == null || name.isBlank()) continue;
                        bestD = dx * dx + dz * dz;
                        best = name;
                    }
                }
            }
        }
        return best != null ? best : new LineageGenerator(pos.asLong()).placeName();
    }

    /** La ville de ce royaume (qu'il tient) la plus proche, à portée d'un donjon. */
    private static Optional<AiProvince> nearestOwnProvince(MinecraftServer server, UUID owner, ServerLevel level, BlockPos pos) {
        AiProvince best = null;
        double bestD = (double) KEEP_RANGE * KEEP_RANGE;
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                UUID holder = p.holder() == null ? r.id() : p.holder();
                if (!holder.equals(owner) || !p.dimension().equals(level.dimension().location())) continue;
                double d = p.distanceSqr(pos.getX(), pos.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // ------------------------------------------------------------------ bâtir

    /** Chaque tick : les chantiers instantanés avancent par lots, les autres d'une étape toutes les quelques secondes. */
    public static void tick(MinecraftServer server, int tick) {
        CrusadeSavedData data = data(server);
        int step = CrusadeConfig.SERVER.buildStepSeconds.get() * 20;
        boolean progressiveTurn = tick % step == 0;
        // P34 : une ville bâtie d'après les plans compte cent mille blocs ; elle avance chaque seconde, à son rythme.
        boolean townTurn = tick % 20 == 0;
        int budget = INSTANT_BUDGET;
        for (Site site : new java.util.ArrayList<>(data.world().sites())) {
            if (site.built()) continue;
            boolean town = site.town() != null;
            if (!site.instant() && !(town ? townTurn : progressiveTurn)) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            if (level == null || !level.hasChunkAt(site.anchor())) continue;
            if (town && fr.crusadecraft.core.build.plan.Journals.undoing(server, site.id())) continue;
            int allowed = site.instant() ? budget : town ? CrusadeConfig.SERVER.townBlocksPerSecond.get() : CrusadeConfig.SERVER.buildBlocksPerStep.get();
            int used = advance(level, site, allowed);
            if (site.instant()) budget -= used;
            data.setDirty();
            if (site.built()) onBuilt(server, level, site);
            if (budget <= 0) break;
        }
    }

    /** Pose jusqu'à {@code allowed} blocs du chantier ; un bloc déjà en place ne compte pas. Renvoie le nombre posé. */
    static int advance(ServerLevel level, Site site, int allowed) {
        if (site.town() != null) return advanceTown(level, site, allowed);
        List<Blueprints.Placement> plan = plan(site);
        Rotation rotation = Blueprints.rotation(site.facing());
        int changed = 0, i = site.progress();
        while (i < plan.size() && changed < allowed) {
            Blueprints.Placement p = plan.get(i);
            BlockPos pos = Blueprints.place(site.anchor(), p, rotation);
            if (p.foundation()) {
                for (int d = 0; d < FOUNDATION_DEPTH; d++) {
                    BlockPos below = pos.below(d);
                    BlockState here = level.getBlockState(below);
                    if (!here.canBeReplaced() && here.getFluidState().isEmpty()) break;
                    journal(level, site, below, here);
                    level.setBlock(below, p.state(), Block.UPDATE_ALL);
                    changed++;
                }
            } else {
                BlockState want = p.state().rotate(rotation);
                BlockState here = level.getBlockState(pos);
                // On ne vide jamais un coffre, un tonneau ou un four : ce qui a un contenu reste.
                boolean keep = want.isAir() && level.getBlockEntity(pos) != null;
                if (!here.equals(want) && !keep && !(want.isAir() && here.isAir())) {
                    journal(level, site, pos, here);
                    level.setBlock(pos, want, Block.UPDATE_ALL);
                    changed++;
                }
            }
            i++;
        }
        site.setProgress(i);
        if (i >= plan.size()) {
            site.setBuilt(true);
            PLANS.remove(site.id());
        }
        return changed;
    }

    /**
     * P34 : le chantier d'une ville d'après les plans. Au premier coup de pioche, le terrain est relevé (et gardé) ;
     * chaque bloc remplacé est noté dans le journal de la ville, qu'elle soit de l'ordinateur ou de l'éditeur.
     */
    private static int advanceTown(ServerLevel level, Site site, int allowed) {
        fr.crusadecraft.core.build.plan.TownPlan town = site.town();
        if (!town.surveyed()) {
            fr.crusadecraft.core.build.plan.TownPlanner.survey(level, site.anchor(), town);
            PLANS.remove(site.id());
        }
        List<Blueprints.Placement> plan = plan(site);
        if (town.total() != plan.size()) town.setTotal(plan.size());
        int[] r = fr.crusadecraft.core.build.plan.PlanPlacer.advance(level, site.anchor(), Rotation.NONE, plan, site.progress(), allowed,
                fr.crusadecraft.core.build.plan.Journals.journal(level.getServer(), site.id(), site.dimension()));
        site.setProgress(r[0]);
        if (r[0] >= plan.size()) {
            site.setBuilt(true);
            PLANS.remove(site.id());
        }
        return r[1];
    }

    /** Un chantier de l'ordinateur note ce qu'il remplace, sous son propre identifiant : de quoi le défaire bloc par bloc. */
    private static void journal(ServerLevel level, Site site, BlockPos pos, BlockState before) {
        if (!site.auto()) return;
        data(level.getServer()).wars().damage().record(site.id(), level.dimension().location(), pos.immutable(), before, null);
    }

    /**
     * Un royaume de l'ordinateur pose une construction de lui-même (P22) : en chantier, jamais trop près d'une autre,
     * avec le journal de ses blocs. Les vérifications du terrain sont faites par celui qui choisit l'endroit.
     */
    public static Optional<Site> placeForAi(MinecraftServer server, ServerLevel level, SiteKind kind, AiRealm owner, BlockPos ground, Direction front) {
        CrusadeSavedData data = data(server);
        for (Site other : data.world().sites()) {
            if (!other.dimension().equals(level.dimension().location())) continue;
            double room = (kind.size() + other.extent()) / 2.0 + 3;
            if (Math.abs(other.anchor().getX() - ground.getX()) < room && Math.abs(other.anchor().getZ() - ground.getZ()) < room) return Optional.empty();
        }
        Site site = new Site(UUID.randomUUID(), kind, nearestPlace(server, level, ground), owner.id(), level.dimension().location(), ground, front,
                owner.color(), false, RealmService.currentDay(server));
        site.setAuto(true);
        data.world().add(site);
        data.setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} pose de lui-même un chantier {} en {}", owner.place(), kind, ground);
        return Optional.of(site);
    }

    /**
     * Défait les constructions que l'ordinateur a posées de lui-même : elles quittent le registre, et chaque bloc
     * qu'elles avaient remplacé revient, quelques dizaines par seconde. Renvoie le nombre de constructions défaites.
     */
    public static int undoAuto(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        List<Site> auto = new java.util.ArrayList<>();
        for (Site s : data.world().sites()) if (s.auto()) auto.add(s);
        for (Site s : auto) {
            data.world().removeSite(s.id());
            PLANS.remove(s.id());
            data.wars().damage().release(s.id());
            // P34 : une ville d'après les plans a son propre journal.
            if (s.town() != null) fr.crusadecraft.core.build.plan.Journals.undo(server, s.id());
            // P28 : un village de l'ordinateur emporte sa ville et ses habitants.
            if (s.kind() == SiteKind.VILLAGE) forgetSettlement(server, s);
        }
        data.setDirty();
        return auto.size();
    }

    /**
     * Ce qu'un village de l'ordinateur avait ajouté au monde, hors des blocs : sa ville sur la carte, sa communauté au
     * registre, et les villageois qui les incarnaient.
     */
    private static void forgetSettlement(MinecraftServer server, Site village) {
        CrusadeSavedData data = data(server);
        if (village.province() >= 0) {
            for (AiRealm r : data.world().realms()) {
                if (r.provinces().removeIf(p -> p.id() == village.province())) {
                    fr.crusadecraft.core.place.PlaceService.forget(server, fr.crusadecraft.core.place.Lieu.Ref.aiProvince(village.province()));
                    for (Realm human : data.realms()) human.claims().remove(village.province());
                }
            }
        }
        if (village.community() != null) {
            var state = data.lineage();
            for (var p : new java.util.ArrayList<>(state.residents(village.community()))) {
                if (p.inWorld()) {
                    ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim()));
                    net.minecraft.world.entity.Entity e = level == null ? null : level.getEntity(p.id());
                    if (e != null) e.discard();
                }
                state.remove(p.id());
            }
            state.removeCommunity(village.community());
        }
    }

    /** Banc d'essai : fait avancer un chantier tout de suite (sans attendre les ticks) ; achevé, il est inauguré. */
    public static int advanceForTests(ServerLevel level, Site site, int allowed) {
        boolean was = site.built();
        int n = advance(level, site, allowed);
        if (!was && site.built()) onBuilt(level.getServer(), level, site);
        return n;
    }

    /**
     * Un royaume de l'ordinateur fonde un village (P28) : un hameau sur la carte, sa communauté au registre (deux
     * familles), le chantier noté bloc par bloc. Le terrain est vérifié par celui qui choisit l'endroit.
     */
    public static Optional<Site> foundVillageForAi(MinecraftServer server, ServerLevel level, AiRealm owner, BlockPos ground, Direction front) {
        return foundVillageForAi(server, level, owner, ground, front, null);
    }

    /** P34 : le même village, bâti d'après les plans du style de son royaume ({@code town}, ou le plan du code sans lui). */
    public static Optional<Site> foundVillageForAi(MinecraftServer server, ServerLevel level, AiRealm owner, BlockPos ground, Direction front,
                                                   fr.crusadecraft.core.build.plan.TownPlan town) {
        CrusadeSavedData data = data(server);
        if (crowded(data, level, town != null ? town.extent() : SiteKind.VILLAGE.size(), ground, null)) return Optional.empty();
        long today = RealmService.currentDay(server);
        String name = new LineageGenerator(ground.asLong() ^ today).placeName();
        Optional<AiProvince> city = WorldEditService.addCity(server, level, owner.id(), name, WorldEditService.CitySize.HAMEAU, ground.getX(), ground.getZ());
        if (city.isEmpty()) return Optional.empty();
        Site site = new Site(UUID.randomUUID(), SiteKind.VILLAGE, name, owner.id(), level.dimension().location(), ground,
                town != null ? Direction.SOUTH : front, owner.color(), false, today);
        site.setAuto(true);
        site.setTown(town);
        site.setProvince(city.get().id());
        Community c = new LineageGenerator(ground.asLong() * 31 + today).community(data.lineage(), name, CommunityKind.VILLAGE, owner.id(),
                fr.crusadecraft.core.ai.VillageService.FOUNDING_FAMILIES);
        site.setCommunity(c.id());
        data.world().add(site);
        data.setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} fonde de lui-même le village {} en {}", owner.place(), name, ground);
        return Optional.of(site);
    }

    /** Un quartier contre un village de l'ordinateur (P28) : même ville, même communauté. */
    public static Optional<Site> quarterForAi(MinecraftServer server, ServerLevel level, AiRealm owner, Site village, BlockPos ground, Direction front) {
        return quarterForAi(server, level, owner, village, ground, front, null);
    }

    /** P34 : le même quartier, de quelques bâtiments d'après les plans ({@code town}). */
    public static Optional<Site> quarterForAi(MinecraftServer server, ServerLevel level, AiRealm owner, Site village, BlockPos ground, Direction front,
                                              fr.crusadecraft.core.build.plan.TownPlan town) {
        CrusadeSavedData data = data(server);
        if (crowded(data, level, town != null ? town.extent() : SiteKind.QUARTIER.size(), ground, null)) return Optional.empty();
        Site site = new Site(UUID.randomUUID(), SiteKind.QUARTIER, village.name(), owner.id(), level.dimension().location(), ground,
                town != null ? Direction.SOUTH : front, owner.color(), false, RealmService.currentDay(server));
        site.setAuto(true);
        site.setTown(town);
        site.setProvince(village.province());
        site.setCommunity(village.community());
        data.world().add(site);
        data.setDirty();
        return Optional.of(site);
    }

    /** Une autre construction à moins de la moitié des deux tailles (et trois blocs) : la place est prise. */
    private static boolean crowded(CrusadeSavedData data, ServerLevel level, SiteKind kind, BlockPos ground) {
        return crowded(data, level, kind.size(), ground, null);
    }

    /** Une autre construction à moins de la moitié des deux emprises (et trois blocs) ; {@code except} ne compte pas. */
    private static boolean crowded(CrusadeSavedData data, ServerLevel level, int size, BlockPos ground, UUID except) {
        for (Site other : data.world().sites()) {
            if (!other.dimension().equals(level.dimension().location()) || other.id().equals(except)) continue;
            double room = (size + other.extent()) / 2.0 + 3;
            if (Math.abs(other.anchor().getX() - ground.getX()) < room && Math.abs(other.anchor().getZ() - ground.getZ()) < room) return true;
        }
        return false;
    }

    /** La construction est achevée : son royaume est prévenu ; un village accueille ses habitants (avec Comes Alive). */
    private static void onBuilt(MinecraftServer server, ServerLevel level, Site site) {
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} achevé en {}", site.kind(), site.anchor());
        data(server).realm(site.owner()).ifPresent(r -> {
            ServerPlayer ruler = server.getPlayerList().getPlayer(r.ruler());
            if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.site.title"),
                    Component.translatable("crusadecraft.toast.site.body", displayName(site))));
        });
        if (site.kind() == SiteKind.QUARTIER) {
            fr.crusadecraft.core.ai.VillageService.onQuarterBuilt(server, level, site);
            return;
        }
        if (site.community() != null) {
            var state = data(server).lineage();
            state.community(site.community()).ifPresent(c -> LineageService.spawnPeople(level, state, state.residents(c.id()), site.anchor().above()));
        }
    }

    /** La part bâtie d'un chantier, en pour cent. */
    public static int percent(Site site) {
        if (site.built()) return 100;
        if (site.town() != null && !PLANS.containsKey(site.id())) {
            int t = site.town().total();
            return t == 0 ? 0 : Math.min(99, site.progress() * 100 / t);
        }
        int total = plan(site).size();
        return total == 0 ? 100 : Math.min(99, site.progress() * 100 / total);
    }

    // ------------------------------------------------------------------ villes d'après les plans (P34)

    /** La ville bâtie (ou en chantier) d'une ville de la carte, s'il y en a une. */
    public static Optional<Site> townOf(MinecraftServer server, int province) {
        for (Site s : data(server).world().sites()) if (s.town() != null && s.province() == province && s.kind() == SiteKind.VILLAGE) return Optional.of(s);
        return Optional.empty();
    }

    /**
     * L'éditeur bâtit une ville de la carte, d'un coup, avec les bâtiments du style de son royaume : sa taille suit
     * sa population. Le chantier commence quand quelqu'un est assez près pour que l'endroit soit chargé.
     */
    public static Outcome buildTown(ServerPlayer player, int provinceId) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        return startTown(player.server, provinceId, true, false).map(s -> Outcome.ok(Component.translatable("crusadecraft.town.started", s.name(),
                Component.translatable(s.town().size().key()),
                Component.translatable(fr.crusadecraft.core.build.plan.NationStyle.byName(s.town().style(), fr.crusadecraft.core.build.plan.NationStyle.EUROPEENNE).key()))))
                .orElseGet(() -> Outcome.no(townRefusal(player.server, provinceId)));
    }

    /** Pourquoi une ville de la carte ne peut pas être bâtie (clé de traduction). */
    private static String townRefusal(MinecraftServer server, int provinceId) {
        if (townOf(server, provinceId).isPresent()) return "crusadecraft.town.exists";
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) if (p.id() == provinceId) return "crusadecraft.town.no_pack";
        }
        return "crusadecraft.mapedit.city.unknown";
    }

    /**
     * Ouvre le chantier d'une ville de la carte (éditeur, ou ville de l'ordinateur qui prend corps). Rien si elle a
     * déjà sa ville, ou si aucun pack de son style n'est installé.
     */
    public static Optional<Site> startTown(MinecraftServer server, int provinceId, boolean instant, boolean auto) {
        CrusadeSavedData data = data(server);
        if (townOf(server, provinceId).isPresent()) return Optional.empty();
        for (AiRealm r : data.world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (p.id() != provinceId) continue;
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.dimension()));
                if (level == null) return Optional.empty();
                fr.crusadecraft.core.build.plan.TownSize size = fr.crusadecraft.core.build.plan.TownSize.forCity(p.population(), p.capital());
                long seed = p.id() * 0x9E3779B97F4A7C15L ^ r.id().getLeastSignificantBits();
                Optional<fr.crusadecraft.core.build.plan.TownPlan> town = fr.crusadecraft.core.build.plan.TownPlanner.plan(r.style(), size, seed);
                if (town.isEmpty()) return Optional.empty();
                UUID owner = p.holder() == null ? r.id() : p.holder();
                long today = RealmService.currentDay(server);
                Site site = new Site(UUID.randomUUID(), SiteKind.VILLAGE, p.name(), owner, p.dimension(), p.seat(), Direction.SOUTH,
                        r.color(), instant, today);
                site.setAuto(auto);
                site.setProvince(p.id());
                site.setTown(town.get());
                Community c = new LineageGenerator(p.seat().asLong() * 31 + today).community(data.lineage(), p.name(), CommunityKind.VILLAGE, owner,
                        size.families());
                site.setCommunity(c.id());
                data.world().add(site);
                data.setDirty();
                CrusadeCraft.LOGGER.info("CrusadeCraft : la ville {} prend corps ({}, {}) en {}", p.name(), size, r.style(), p.seat());
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    /**
     * L'éditeur défait une ville bâtie d'après les plans : le terrain revient bloc par bloc, ses habitants partent,
     * la ville reste sur la carte.
     */
    public static Outcome undoTown(ServerPlayer player, UUID siteId) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        CrusadeSavedData data = data(player.server);
        Optional<Site> site = data.world().site(siteId);
        if (site.isEmpty() || site.get().town() == null) return Outcome.no("crusadecraft.town.not_a_town");
        data.world().removeSite(siteId);
        PLANS.remove(siteId);
        fr.crusadecraft.core.build.plan.Journals.undo(player.server, siteId);
        forgetPeople(player.server, site.get());
        data.setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.town.undone", site.get().name()));
    }

    /** Les habitants d'une construction et leur communauté quittent le registre (et le monde). */
    private static void forgetPeople(MinecraftServer server, Site site) {
        if (site.community() == null) return;
        var state = data(server).lineage();
        for (var p : new java.util.ArrayList<>(state.residents(site.community()))) {
            if (p.inWorld()) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim()));
                net.minecraft.world.entity.Entity e = level == null ? null : level.getEntity(p.id());
                if (e != null) e.discard();
            }
            state.remove(p.id());
        }
        state.removeCommunity(site.community());
    }

    // ------------------------------------------------------------------ éditeur

    public static Outcome give(ServerPlayer player, UUID siteId, UUID owner) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.site.unknown");
        if (data(player.server).world().realm(owner).isEmpty() && data(player.server).realm(owner).isEmpty()) {
            return Outcome.no("crusadecraft.mapedit.realm.unknown");
        }
        site.get().setOwner(owner);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.site.given", displayName(site.get()),
                fr.crusadecraft.core.diplomacy.DiplomacyService.name(player.server, owner)));
    }

    /** Efface une construction du registre (ses blocs restent dans le monde, et la ville d'un village aussi). */
    public static Outcome remove(ServerPlayer player, UUID siteId) {
        if (!WorldEditService.canEdit(player)) return Outcome.no("crusadecraft.mapedit.denied");
        Optional<Site> site = data(player.server).world().removeSite(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.site.unknown");
        PLANS.remove(siteId);
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.site.removed", displayName(site.get())));
    }

    /** Un royaume disparu de l'éditeur : ses constructions disparaissent du registre (leurs pierres restent). */
    public static void onRealmRemoved(MinecraftServer server, UUID realm) {
        data(server).world().sites().removeIf(s -> realm.equals(s.owner()));
    }

    /** Pris en guerre : la place change de mains. */
    public static void capture(MinecraftServer server, UUID siteId, UUID newOwner) {
        data(server).world().site(siteId).ifPresent(s -> {
            s.setOwner(newOwner);
            data(server).setDirty();
        });
    }
}
