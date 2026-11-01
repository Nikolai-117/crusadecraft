package fr.crusadecraft.core.build.plan;

import fr.crusadecraft.compat.minecolonies.ColonyFounder;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.build.Blueprints;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.OpenFoundingScreenPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Une ville posée d'après les plans devient à soi (P35) : une vraie colonie MineColonies, dont les colons travaillent
 * dans ses bâtiments, rattachée à son royaume — ou la capitale d'un royaume qu'on fonde là. La ville quitte alors le
 * registre des constructions : c'est désormais une colonie, qui vit comme toutes les autres (on ne la « défait » plus).
 */
public final class TownColony {

    /** Un bloc de cabane du plan, là où il est dans le monde, avec son pack, son chemin et son niveau. */
    public record HutSpot(BlockPos pos, BlockState state, String pack, String path, int level, boolean townHall) {}

    private TownColony() {}

    /** Les cabanes de la ville, dans le monde : relues dans les plans (cabanes gardées), tournées comme la ville. */
    public static List<HutSpot> huts(Site site) {
        TownPlan town = site.town();
        List<HutSpot> out = new ArrayList<>();
        if (town == null) return out;
        Rotation global = Rotation.values()[Math.floorMod(town.rotation(), 4)];
        for (int i = 0; i < town.pieces().size(); i++) {
            TownPlan.Piece piece = town.pieces().get(i);
            Optional<PlanRef> ref = PlanRef.decode(piece.ref());
            Optional<Plan> plan = ref.flatMap(r -> PlanLibrary.load(r, true));
            if (plan.isEmpty()) continue;
            Rotation rot = Rotation.values()[piece.rotation()];
            for (Blueprints.Placement p : plan.get().placements()) {
                String id = BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).toString();
                if (!id.startsWith("minecolonies:blockhut")) continue;
                int[] xz = PlanBuilder.turn(p.x(), p.z(), rot);
                Blueprints.Placement local = new Blueprints.Placement(piece.dx() + xz[0], p.y() + piece.dy(), piece.dz() + xz[1], p.state(), false);
                BlockPos pos = Blueprints.place(site.anchor(), local, global);
                BlockState state = p.state().rotate(rot).rotate(global);
                out.add(new HutSpot(pos, state, ref.get().pack(), ref.get().path(), PlanRules.level(ref.get().label()),
                        "townhall".equals(PlanRules.hutType(id))));
            }
        }
        return out;
    }

    /** La ville bâtie dans laquelle se tient ce joueur, s'il y en a une. */
    public static Optional<Site> townAt(ServerPlayer player) {
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        Site best = null;
        double bestD = Double.MAX_VALUE;
        for (Site s : data.world().sites()) {
            if (s.town() == null || !s.dimension().equals(player.serverLevel().dimension().location())) continue;
            double half = s.extent() / 2.0 + 4;
            double dx = player.getX() - s.anchor().getX(), dz = player.getZ() - s.anchor().getZ();
            if (Math.abs(dx) > half || Math.abs(dz) > half) continue;
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Le joueur peut-il faire sienne cette ville ? En créatif, avec l'éditeur, ou si elle est déjà à son royaume. */
    static boolean allowed(ServerPlayer player, Site site) {
        if (player.isCreative() || WorldEditService.canEdit(player)) return true;
        return RealmService.realmOf(player.server, player.getUUID()).map(r -> r.id().equals(site.owner())).orElse(false);
    }

    /**
     * Fait de la ville une colonie de ce joueur. {@code founding} : il veut y fonder son royaume (l'écran de fondation
     * s'ouvre aussitôt, la ville en capitale) ; sinon la colonie rejoint son royaume s'il en a un.
     */
    public static Outcome makeColony(ServerPlayer player, UUID siteId, boolean founding) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Site> found = siteId == null ? townAt(player) : data.world().site(siteId);
        if (found.isEmpty() || found.get().town() == null) return Outcome.no("crusadecraft.colony.no_town");
        Site site = found.get();
        if (!allowed(player, site)) return Outcome.no("crusadecraft.colony.denied");
        if (!MineColoniesBridge.isReady()) return Outcome.no("crusadecraft.colony.no_minecolonies");
        if (Journals.pasting(server, site.id()) || !site.built()) return Outcome.no("crusadecraft.colony.not_finished");
        Optional<Realm> realm = RealmService.realmOf(server, player.getUUID());
        if (founding && realm.isPresent()) return Outcome.no("crusadecraft.colony.already_ruler", realm.get().name());
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, site.dimension()));
        if (level == null) return Outcome.no("crusadecraft.colony.no_town");
        List<HutSpot> huts = huts(site);
        HutSpot hall = huts.stream().filter(HutSpot::townHall).findFirst().orElse(null);
        if (hall == null) return Outcome.no("crusadecraft.colony.no_town_hall");
        Optional<ColonyFounder.Founded> colony = ColonyFounder.found(level, player, site.name(), hall.pack(), huts);
        if (colony.isEmpty()) return Outcome.no("crusadecraft.colony.refused");
        ColonyRef ref = colony.get().colony();
        // La ville quitte le registre des constructions et la carte de l'ordinateur : c'est une colonie, désormais.
        release(server, site);
        if (realm.isPresent()) {
            realm.get().addProvince(ref);
            fr.crusadecraft.core.intrigue.Chronicle.record(server, realm.get(), "crusadecraft.chronicle.annexed", Component.literal(site.name()));
            data.setDirty();
            return Outcome.ok(Component.translatable("crusadecraft.colony.joined", site.name(), colony.get().buildings(), realm.get().name()));
        }
        data.setDirty();
        if (founding) {
            RealmService.grantFounding(player, hall.pos());
            CrusadeNetwork.toPlayer(player, new OpenFoundingScreenPacket(hall.pos(), MineColoniesBridge.info(ref), player.getGameProfile().getName()));
            return Outcome.ok(Component.translatable("crusadecraft.colony.founding", site.name(), colony.get().buildings()));
        }
        return Outcome.ok(Component.translatable("crusadecraft.colony.made", site.name(), colony.get().buildings()));
    }

    /**
     * La ville n'est plus une construction du mod : elle quitte le registre (son journal aussi), sa ville de
     * l'ordinateur quitte la carte, et une nation fondée par elle seule disparaît avec elle.
     */
    static void release(MinecraftServer server, Site site) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        data.world().removeSite(site.id());
        SiteService.forgetPlan(site.id());
        Journals.discard(server, site.id());
        if (site.province() < 0) return;
        for (AiRealm r : new ArrayList<>(data.world().realms())) {
            if (!r.provinces().removeIf(p -> p.id() == site.province())) continue;
            fr.crusadecraft.core.place.PlaceService.forget(server, fr.crusadecraft.core.place.Lieu.Ref.aiProvince(site.province()));
            for (Realm human : data.realms()) human.claims().remove(site.province());
            if (r.provinces().isEmpty() && r.handMade()) WorldEditService.removeRealm(server, r.id());
        }
    }

    /** Les deux boutons cliquables proposés après la pose d'une ville entière. */
    public static Component offer(UUID siteId) {
        MutableComponent colony = Component.translatable("crusadecraft.colony.offer.colony").withStyle(Style.EMPTY.withColor(0xE7BD55).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crusade ville colonie " + siteId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("crusadecraft.colony.offer.colony.tip"))));
        MutableComponent realm = Component.translatable("crusadecraft.colony.offer.realm").withStyle(Style.EMPTY.withColor(0xE7BD55).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crusade ville royaume " + siteId))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("crusadecraft.colony.offer.realm.tip"))));
        return Component.translatable("crusadecraft.colony.offer", colony, realm);
    }
}
