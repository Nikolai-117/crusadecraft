package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.InhabitantEggItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.InhabitantPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * L'œuf d'habitant (P42) : on choisit une ville et un métier, et quelqu'un naît là où l'on vise : un villageois de Comes
 * Alive, avec sa fiche, sa place au registre et, s'il y a un bâtiment de son métier dans la ville, son poste.
 */
public final class CityEggs {
    private CityEggs() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Clic droit sur un bloc. Un œuf déjà réglé fait naître ; accroupi ou vierge, il ouvre l'écran du choix. */
    public static void use(ServerPlayer player, ItemStack stack, BlockPos at) {
        Optional<InhabitantEggItem.Choice> choice = InhabitantEggItem.choice(stack);
        if (choice.isEmpty() || player.isShiftKeyDown()) {
            openScreen(player, at);
            return;
        }
        Outcome out = hatch(player, choice.get().site(), choice.get().job(), at);
        player.displayClientMessage(out.message(), true);
        if (out.success() && !player.getAbilities().instabuild) stack.shrink(1);
    }

    private static void openScreen(ServerPlayer player, BlockPos at) {
        List<InhabitantPackets.CityRow> rows = new ArrayList<>();
        List<Site> cities = new ArrayList<>();
        for (Site s : data(player.server).world().sites()) if (CityService.city(s)) cities.add(s);
        cities.sort(Comparator.comparingDouble(s -> s.dimension().equals(player.level().dimension().location()) ? s.anchor().distSqr(at) : Double.MAX_VALUE));
        for (Site s : cities) {
            String realm = data(player.server).world().realm(s.owner()).map(r -> Fr.of(AiNames.realm(r.rank(), r.place())))
                    .orElseGet(() -> data(player.server).realm(s.owner()).map(fr.crusadecraft.core.realm.Realm::name).orElse(""));
            rows.add(new InhabitantPackets.CityRow(s.id(), s.name(), realm, s.color(), CityService.people(player.server, s), CityService.PEOPLE_MAX));
        }
        CrusadeNetwork.toPlayer(player, new InhabitantPackets.OpenEgg(at, rows));
    }

    /** Le choix de l'écran : l'œuf le retient, et le premier habitant naît aussitôt à l'endroit visé. */
    public static Outcome choose(ServerPlayer player, UUID siteId, BuildingFunction job, BlockPos at) {
        Optional<ItemStack> held = InhabitantEggItem.held(player);
        if (held.isEmpty()) return Outcome.no("crusadecraft.city.egg.none");
        Optional<Site> site = data(player.server).world().site(siteId);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        InhabitantEggItem.setChoice(held.get(), new InhabitantEggItem.Choice(siteId, job, site.get().name(), site.get().color()));
        Outcome out = hatch(player, siteId, job, at);
        if (out.success() && !player.getAbilities().instabuild) held.get().shrink(1);
        return out;
    }

    /** Quelqu'un naît : inscrit au registre, dans sa ville, à son poste s'il y en a un de libre, et aussitôt dans le monde. */
    public static Outcome hatch(ServerPlayer player, UUID siteId, BuildingFunction job, BlockPos at) {
        MinecraftServer server = player.server;
        Optional<Site> found = data(server).world().site(siteId);
        if (found.isEmpty()) return Outcome.no("crusadecraft.city.unknown");
        Site site = found.get();
        ServerLevel level = player.serverLevel();
        if (!site.dimension().equals(level.dimension().location())) return Outcome.no("crusadecraft.city.egg.elsewhere", site.name());
        if (CityService.people(server, site) >= CityService.PEOPLE_MAX) return Outcome.no("crusadecraft.city.egg.full", site.name(), CityService.PEOPLE_MAX);
        LineageState state = data(server).lineage();
        Community c = CityService.community(server, site);
        boolean men = job == BuildingFunction.CASERNE || job == BuildingFunction.TOUR || job == BuildingFunction.CHATEAU;
        Random random = new Random(level.random.nextLong());
        Person p = CityPeople.generator(server, site, random.nextLong()).worker(state, c, job.dwelling() ? Role.AUCUN : job.role(), men);
        CityBuilding b = post(site, job, at);
        p.setWork(b == null ? at.above() : b.spot());
        if (b != null) b.occupants().add(p.id());
        CityPeople.dress(server, site, p, job, random);
        String post = b == null ? (job.posts().isEmpty() ? "minecraft:none" : job.posts().get(0)) : CityService.post(b, b.occupants().size() - 1);
        Optional<Entity> e = CityService.spawnAt(level, state, p, at.above(), job, post);
        if (e.isEmpty()) {
            if (b != null) b.occupants().remove(p.id());
            state.remove(p.id());
            return Outcome.no("crusadecraft.city.egg.failed");
        }
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.egg.born", state.fullName(p), CityTalk.jobTitle(p), site.name()));
    }

    /** Le bâtiment de ce métier sous la main, sinon le plus proche dans la ville, sinon aucun. */
    private static CityBuilding post(Site site, BuildingFunction job, BlockPos at) {
        Optional<CityBuilding> here = CityService.buildingAt(site, at).filter(b -> b.function() == job);
        if (here.isPresent()) return here.get();
        CityBuilding best = null;
        double bestD = Double.MAX_VALUE;
        for (CityBuilding b : site.buildings()) {
            if (b.function() != job) continue;
            double d = b.spot().distSqr(at);
            if (d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }
}
