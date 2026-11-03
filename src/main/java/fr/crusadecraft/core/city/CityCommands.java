package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.BlockEquivalents;
import fr.crusadecraft.core.build.plan.PlanLibrary;
import fr.crusadecraft.core.build.plan.PlanTool;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Les commandes des villes (P39), {@code /crusade ville …} : la même chose qu'au bâton d'intendant, pour la ville et
 * le bâtiment où l'on se tient (utile sur un serveur, ou pour aller vite).
 */
public final class CityCommands {
    private CityCommands() {}

    private static Optional<Site> here(ServerPlayer player) {
        return CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
    }

    /** Ce qu'on sait de la ville où l'on est, et du bâtiment où l'on se tient. */
    public static List<Component> info(ServerPlayer player) {
        List<Component> out = new ArrayList<>();
        Optional<Site> site = here(player);
        if (site.isEmpty()) {
            out.add(Component.translatable("crusadecraft.city.none").withStyle(ChatFormatting.GRAY));
            return out;
        }
        Site s = site.get();
        int decided = 0;
        for (CityBuilding b : s.buildings()) if (b.function() != null) decided++;
        out.add(Component.translatable("crusadecraft.city.cmd.info", s.name(), fr.crusadecraft.core.diplomacy.DiplomacyService.name(player.server, s.owner()))
                .withStyle(ChatFormatting.GOLD));
        out.add(Component.translatable("crusadecraft.city.hud.counts", s.buildings().size(), decided, CityService.people(player.server, s))
                .withStyle(ChatFormatting.GRAY));
        Optional<CityBuilding> b = CityService.buildingAt(s, player.blockPosition());
        if (b.isPresent()) {
            BuildingFunction f = b.get().function();
            Component what = f != null ? Component.translatable(f.key())
                    : b.get().suggestion() != null ? Component.translatable("crusadecraft.city.screen.row.suggested", Component.translatable(b.get().suggestion().key()))
                    : Component.translatable("crusadecraft.city.screen.undecided");
            out.add(Component.translatable("crusadecraft.city.cmd.building", what, b.get().name()).withStyle(ChatFormatting.YELLOW));
            LineageState state = CrusadeSavedData.get(player.server).lineage();
            for (UUID id : b.get().occupants()) {
                Optional<Person> p = state.person(id);
                p.ifPresent(person -> out.add(Component.literal("  · " + state.fullName(person) + (person.alive() ? "" : " †")).withStyle(ChatFormatting.GRAY)));
            }
        }
        return out;
    }

    public static Outcome screen(ServerPlayer player) {
        Optional<Site> site = here(player);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.none");
        CityStaff.sendCity(player, site.get());
        return Outcome.ok(Component.literal(site.get().name()));
    }

    public static Outcome recognize(ServerPlayer player) {
        return here(player).map(s -> CityService.recognize(player, s.id())).orElse(Outcome.no("crusadecraft.city.none"));
    }

    public static Outcome acceptAll(ServerPlayer player) {
        return here(player).map(s -> CityService.acceptAll(player, s.id())).orElse(Outcome.no("crusadecraft.city.none"));
    }

    /** Fait venir tout de suite les gens qui manquent. */
    public static Outcome people(ServerPlayer player) {
        Optional<Site> site = here(player);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.none");
        if (!CityService.allowed(player, site.get())) return Outcome.no("crusadecraft.city.denied");
        int n = CityService.populate(player.serverLevel(), site.get());
        return Outcome.ok(Component.translatable("crusadecraft.city.cmd.people", n, CityService.people(player.server, site.get())));
    }

    public static List<String> functionNames() {
        List<String> out = new ArrayList<>();
        for (BuildingFunction f : BuildingFunction.values()) out.add(f.name().toLowerCase(Locale.ROOT));
        out.add("aucune");
        return out;
    }

    /** Donne une fonction au bâtiment où l'on se tient (reconnu sur place s'il ne l'est pas encore). */
    public static Outcome function(ServerPlayer player, String name, String label) {
        Optional<Site> site = here(player);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.none");
        BuildingFunction f = BuildingFunction.byName(name.toUpperCase(Locale.ROOT));
        if (f == null && !"aucune".equalsIgnoreCase(name)) return Outcome.no("crusadecraft.city.cmd.function.unknown", name);
        BlockPos at = player.blockPosition();
        Optional<CityBuilding> b = CityService.buildingAt(site.get(), at);
        CityBuilding target;
        if (b.isPresent()) {
            target = b.get();
        } else {
            Optional<BuildingScan.Result> r = BuildingScan.fromInside(player.level(), at);
            if (r.isEmpty()) return Outcome.no("crusadecraft.city.cmd.function.outside");
            target = CityService.draft(r.get());
        }
        return CityService.define(player, site.get().id(), target, f, label);
    }

    /** Retire de la ville le bâtiment où l'on se tient. */
    public static Outcome forget(ServerPlayer player) {
        Optional<Site> site = here(player);
        if (site.isEmpty()) return Outcome.no("crusadecraft.city.none");
        Optional<CityBuilding> b = CityService.buildingAt(site.get(), player.blockPosition());
        if (b.isEmpty()) return Outcome.no("crusadecraft.city.cmd.function.outside");
        return CityService.forget(player, site.get().id(), b.get().id());
    }

    /** Relit les fichiers de plans, la table des équivalences, et range les plans livrés qui manqueraient. */
    public static Outcome reload() {
        PlanLibrary.onServerStopped();
        PlanTool.onServerStopped();
        BlockEquivalents.reset();
        int n = PlanLibrary.installBundled();
        return Outcome.ok(Component.translatable("crusadecraft.plan.reloaded", PlanLibrary.fileEntries().size(), n));
    }
}
