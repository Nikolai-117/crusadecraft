package fr.crusadecraft.core.build;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
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

    /** Le plan d'une construction, gardé en mémoire le temps du chantier. */
    static List<Blueprints.Placement> plan(Site site) {
        return PLANS.computeIfAbsent(site.id(), id -> Blueprints.plan(site.kind(), Banners.dye(site.color())));
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
            double room = (kind.size() + other.kind().size()) / 2.0 + 3;
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

    /** Le nom de la ville la plus proche (à moins de 600 blocs), sinon un nom de lieu nouveau. */
    private static String nearestPlace(MinecraftServer server, ServerLevel level, BlockPos pos) {
        String best = null;
        double bestD = 600.0 * 600.0;
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (!p.dimension().equals(level.dimension().location())) continue;
                double d = p.distanceSqr(pos.getX(), pos.getZ());
                if (d < bestD) {
                    bestD = d;
                    best = p.name();
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
        int budget = INSTANT_BUDGET;
        for (Site site : data.world().sites()) {
            if (site.built()) continue;
            if (!site.instant() && !progressiveTurn) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
            if (level == null || !level.hasChunkAt(site.anchor())) continue;
            int allowed = site.instant() ? budget : CrusadeConfig.SERVER.buildBlocksPerStep.get();
            int used = advance(level, site, allowed);
            if (site.instant()) budget -= used;
            data.setDirty();
            if (site.built()) onBuilt(server, level, site);
            if (budget <= 0) break;
        }
    }

    /** Pose jusqu'à {@code allowed} blocs du chantier ; un bloc déjà en place ne compte pas. Renvoie le nombre posé. */
    static int advance(ServerLevel level, Site site, int allowed) {
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
                    level.setBlock(below, p.state(), Block.UPDATE_ALL);
                    changed++;
                }
            } else {
                BlockState want = p.state().rotate(rotation);
                BlockState here = level.getBlockState(pos);
                // On ne vide jamais un coffre, un tonneau ou un four : ce qui a un contenu reste.
                boolean keep = want.isAir() && level.getBlockEntity(pos) != null;
                if (!here.equals(want) && !keep && !(want.isAir() && here.isAir())) {
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

    /** Banc d'essai : fait avancer un chantier tout de suite (sans attendre les ticks). */
    public static int advanceForTests(ServerLevel level, Site site, int allowed) {
        return advance(level, site, allowed);
    }

    /** La construction est achevée : son royaume est prévenu ; un village accueille ses habitants (avec Comes Alive). */
    private static void onBuilt(MinecraftServer server, ServerLevel level, Site site) {
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} achevé en {}", site.kind(), site.anchor());
        data(server).realm(site.owner()).ifPresent(r -> {
            ServerPlayer ruler = server.getPlayerList().getPlayer(r.ruler());
            if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.site.title"),
                    Component.translatable("crusadecraft.toast.site.body", displayName(site))));
        });
        if (site.community() != null) {
            var state = data(server).lineage();
            state.community(site.community()).ifPresent(c -> LineageService.spawnPeople(level, state, state.residents(c.id()), site.anchor().above()));
        }
    }

    /** La part bâtie d'un chantier, en pour cent. */
    public static int percent(Site site) {
        if (site.built()) return 100;
        int total = plan(site).size();
        return total == 0 ? 100 : Math.min(99, site.progress() * 100 / total);
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
