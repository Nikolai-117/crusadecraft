package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâton d'intendant (P38), côté serveur. Clic droit sur une porte (ou un mur, de l'intérieur) : le bâtiment est
 * reconnu et l'écran de sa fonction s'ouvre ; sur un bâtiment déjà connu, on le retrouve. Accroupi, deux clics posent
 * les coins d'une boîte tracée à la main. Vers le ciel, l'écran de la ville. Ce qu'on vient de reconnaître reste en
 * brouillon tant qu'on n'a pas choisi : rien n'entre dans la ville sans décision du joueur.
 */
public final class CityStaff {
    private static final String TAG = "crusadecraft_intendant";
    /** Le brouillon de chaque joueur : le bâtiment reconnu, pas encore dans la ville. */
    private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

    record Draft(UUID site, CityBuilding building) {}

    private CityStaff() {}

    public static void onServerStopped() {
        DRAFTS.clear();
    }

    private static CrusadeSavedData data(ServerPlayer player) {
        return CrusadeSavedData.get(player.server);
    }

    // ------------------------------------------------------------------ gestes

    public static void useOn(ServerPlayer player, ItemStack stack, BlockPos pos, Direction face, boolean sneaking) {
        if (sneaking) {
            corner(player, stack, pos);
            return;
        }
        Optional<Site> city = CityService.cityAt(player.server, player.level().dimension().location(), pos);
        if (city.isEmpty()) {
            say(player, Component.translatable("crusadecraft.city.none"));
            return;
        }
        if (!CityService.allowed(player, city.get())) {
            say(player, Component.translatable("crusadecraft.city.denied"));
            return;
        }
        Optional<CityBuilding> known = CityService.buildingAt(city.get(), pos);
        if (known.isPresent()) {
            open(player, city.get(), known.get(), false);
            return;
        }
        Optional<BuildingScan.Result> r = BuildingScan.fromClick(player.level(), pos, face);
        if (r.isEmpty()) {
            say(player, Component.translatable("crusadecraft.city.scan.failed"));
            return;
        }
        for (CityBuilding b : city.get().buildings()) {
            if (b.overlap(r.get().min(), r.get().max()) > 0.5) {
                open(player, city.get(), b, false);
                return;
            }
        }
        CityBuilding draft = CityService.draft(r.get());
        DRAFTS.put(player.getUUID(), new Draft(city.get().id(), draft));
        open(player, city.get(), draft, true);
    }

    /** Vers le ciel : l'écran de la ville ; accroupi, les coins tracés s'effacent. */
    public static void useInAir(ServerPlayer player, ItemStack stack, boolean sneaking) {
        if (sneaking) {
            stack.removeTagKey(TAG);
            say(player, Component.translatable("crusadecraft.city.corners.cleared"));
            return;
        }
        Optional<Site> city = CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
        if (city.isEmpty()) {
            say(player, Component.translatable("crusadecraft.city.none"));
            return;
        }
        sendCity(player, city.get());
    }

    /** Un coin de la boîte tracée à la main ; au second, le bâtiment entre en brouillon. */
    static void corner(ServerPlayer player, ItemStack stack, BlockPos pos) {
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        if (!t.contains("a") || t.contains("b")) {
            t.putLong("a", pos.asLong());
            t.remove("b");
            say(player, Component.translatable("crusadecraft.city.corner.first", pos.getX(), pos.getY(), pos.getZ()));
            return;
        }
        BlockPos a = BlockPos.of(t.getLong("a"));
        t.putLong("b", pos.asLong());
        Optional<BuildingScan.Result> r = BuildingScan.fromBox(player.level(), a, pos);
        if (r.isEmpty()) {
            say(player, Component.translatable("crusadecraft.city.corner.too_big", BuildingScan.MAX_SIDE, BuildingScan.MAX_HEIGHT));
            return;
        }
        BlockPos center = new BlockPos((a.getX() + pos.getX()) / 2, (a.getY() + pos.getY()) / 2, (a.getZ() + pos.getZ()) / 2);
        Optional<Site> city = CityService.cityAt(player.server, player.level().dimension().location(), center);
        if (city.isEmpty()) {
            say(player, Component.translatable("crusadecraft.city.none"));
            return;
        }
        if (!CityService.allowed(player, city.get())) {
            say(player, Component.translatable("crusadecraft.city.denied"));
            return;
        }
        stack.removeTagKey(TAG);
        CityBuilding draft = CityService.draft(r.get());
        DRAFTS.put(player.getUUID(), new Draft(city.get().id(), draft));
        open(player, city.get(), draft, true);
    }

    /** Les coins posés sur le bâton (pour les dessiner), ou rien. */
    public static Optional<BlockPos> cornerA(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        return t != null && t.contains("a") && !t.contains("b") ? Optional.of(BlockPos.of(t.getLong("a"))) : Optional.empty();
    }

    private static void say(ServerPlayer player, Component c) {
        player.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------------ décisions (paquets)

    /** Le joueur a choisi : une fonction (ou aucune), un nom ; ou il retire le bâtiment de la ville. */
    public static void decide(ServerPlayer player, UUID siteId, UUID buildingId, int function, String name, boolean forget) {
        Outcome out;
        if (forget) {
            Draft d = DRAFTS.get(player.getUUID());
            if (d != null && d.building().id().equals(buildingId)) {
                DRAFTS.remove(player.getUUID());
                out = Outcome.ok(Component.translatable("crusadecraft.city.draft.dropped"));
            } else {
                out = CityService.forget(player, siteId, buildingId);
            }
        } else {
            Optional<Site> site = data(player).world().site(siteId);
            CityBuilding b = site.flatMap(s -> s.building(buildingId)).orElse(null);
            Draft d = DRAFTS.get(player.getUUID());
            if (b == null && d != null && d.site().equals(siteId) && d.building().id().equals(buildingId)) b = d.building();
            if (b == null) {
                out = Outcome.no("crusadecraft.city.building.unknown");
            } else {
                out = CityService.define(player, siteId, b, BuildingFunction.byOrdinal(function), name);
                if (out.success()) DRAFTS.remove(player.getUUID());
            }
        }
        player.displayClientMessage(out.message().copy().withStyle(out.success() ? ChatFormatting.GOLD : ChatFormatting.RED), false);
    }

    public static final int OPEN = 0, RECOGNIZE = 1, ACCEPT_ALL = 2;

    public static void cityAction(ServerPlayer player, UUID siteId, int op) {
        Optional<Site> site = data(player).world().site(siteId);
        if (site.isEmpty()) return;
        if (op != OPEN) {
            Outcome out = op == RECOGNIZE ? CityService.recognize(player, siteId) : CityService.acceptAll(player, siteId);
            player.displayClientMessage(out.message().copy().withStyle(out.success() ? ChatFormatting.GOLD : ChatFormatting.RED), false);
        }
        sendCity(player, site.get());
    }

    // ------------------------------------------------------------------ écrans et cadres

    static void open(ServerPlayer player, Site site, CityBuilding b, boolean draft) {
        CrusadeNetwork.toPlayer(player, new BuildingPackets.OpenBuilding(site.id(), site.name(), view(player, b), draft));
    }

    static BuildingPackets.View view(ServerPlayer player, CityBuilding b) {
        LineageState state = data(player).lineage();
        List<String> people = new ArrayList<>();
        for (UUID id : b.occupants()) {
            Optional<Person> p = state.person(id);
            if (p.isEmpty()) continue;
            people.add(state.fullName(p.get()) + (p.get().alive() ? "" : " †"));
        }
        return new BuildingPackets.View(b.id(), b.min(), b.max(), b.spot(), b.function() == null ? -1 : b.function().ordinal(),
                b.suggestion() == null ? -1 : b.suggestion().ordinal(), b.name(), b.floor(), b.levels(), new HashMap<>(b.furniture()), people);
    }

    static void sendCity(ServerPlayer player, Site site) {
        List<BuildingPackets.Row> rows = new ArrayList<>();
        for (CityBuilding b : site.buildings()) {
            rows.add(new BuildingPackets.Row(b.id(), b.function() == null ? -1 : b.function().ordinal(), b.suggestion() == null ? -1 : b.suggestion().ordinal(),
                    b.name(), b.occupants().size(), b.spot()));
        }
        CrusadeNetwork.toPlayer(player, new BuildingPackets.OpenCity(site.id(), site.name(), CityService.people(player.server, site), CityService.PEOPLE_MAX,
                CityService.allowed(player, site), rows));
    }

    /** Les cadres des bâtiments de la ville où est le joueur (rien hors d'une ville). */
    public static void sendOutlines(ServerPlayer player) {
        Optional<Site> city = CityService.cityAt(player.server, player.level().dimension().location(), player.blockPosition());
        List<BuildingPackets.Outline> out = new ArrayList<>();
        city.ifPresent(s -> {
            for (CityBuilding b : s.buildings()) {
                out.add(new BuildingPackets.Outline(b.min(), b.max(), b.function() == null ? -1 : b.function().ordinal(),
                        b.suggestion() == null ? -1 : b.suggestion().ordinal(), b.name(), b.occupants().size()));
            }
        });
        Draft d = DRAFTS.get(player.getUUID());
        CityBuilding draft = d == null ? null : d.building();
        CrusadeNetwork.toPlayer(player, new BuildingPackets.Outlines(city.map(Site::name).orElse(""), city.map(s -> CityService.people(player.server, s)).orElse(0),
                out, draft == null ? null : draft.min(), draft == null ? null : draft.max()));
    }
}
