package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.logic.TextRules;
import fr.crusadecraft.core.nation.NationFile;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * À qui est une ville (P46) : la renommer, la faire passer d'un royaume à l'autre, la rendre indépendante, ou fonder un
 * royaume autour d'elle. Tout ce qui vit dans la ville suit : sa province, ses habitants, son emprise sur la carte.
 */
public final class CityAdmin {
    /** Quelques teintes pour les villes qui deviennent indépendantes. */
    private static final int[] COLORS = {0x8C3B3B, 0x3B6C8C, 0x4F7F3F, 0x8C6F2F, 0x6B4F8C, 0x2F7F7A, 0xA0522D, 0x556B2F};

    private CityAdmin() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** La ville change de nom : sa province et sa communauté aussi. */
    public static Outcome rename(MinecraftServer server, Site site, String raw) {
        String name = TextRules.clean(raw, TextRules.NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        site.setName(name);
        province(server, site).ifPresent(p -> p.setName(name));
        data(server).lineage().community(site.community()).ifPresent(c -> c.setName(name));
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.name.city.done", name));
    }

    private static Optional<AiProvince> province(MinecraftServer server, Site site) {
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) if (p.id() == site.province()) return Optional.of(p);
        }
        return Optional.empty();
    }

    private static Optional<AiRealm> host(MinecraftServer server, Site site) {
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) if (p.id() == site.province()) return Optional.of(r);
        }
        return Optional.empty();
    }

    /** La ville rejoint ce royaume de l'ordinateur (ou une nation). */
    public static Outcome join(MinecraftServer server, Site site, UUID realmId) {
        CrusadeSavedData data = data(server);
        Optional<AiRealm> target = data.world().realm(realmId);
        if (target.isEmpty()) return Outcome.no("crusadecraft.mapedit.realm.unknown");
        if (realmId.equals(site.owner())) return Outcome.no("crusadecraft.city.panel.same_realm");
        move(server, site, target.get());
        return Outcome.ok(Component.translatable("crusadecraft.city.panel.joined", site.name(), AiNames.realm(target.get().rank(), target.get().place())));
    }

    /** La ville ne dépend plus de personne : elle devient un petit État à elle seule, qui porte son nom. */
    public static Outcome independent(MinecraftServer server, ServerLevel level, Site site) {
        Optional<AiRealm> current = data(server).world().realm(site.owner());
        if (current.isPresent() && current.get().nation().isEmpty() && current.get().ownProvinces().size() == 1 && current.get().place().equals(site.name())) {
            return Outcome.no("crusadecraft.city.panel.already_independent");
        }
        int color = COLORS[Math.floorMod(site.name().hashCode(), COLORS.length)];
        AiRealm realm = WorldEditService.addLandlessRealm(server, level, site.name(), "", TitleRank.COMTE, color, AiFaith.FIDELE, AiPersonality.PACIFIQUE);
        long today = RealmService.currentDay(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) realm.discover(p.getUUID(), today);
        move(server, site, realm);
        return Outcome.ok(Component.translatable("crusadecraft.city.panel.independent", site.name()));
    }

    /**
     * Un royaume fondé autour de cette ville : une vraie nation, avec son fichier que l'on pourra compléter. Celui qui le
     * fonde pour lui-même en prend la couronne.
     */
    public static Outcome newRealm(ServerPlayer player, Site site, String rawName, TitleRank rank, int color, AiPersonality personality, boolean mine) {
        MinecraftServer server = player.server;
        String name = TextRules.clean(rawName, TextRules.NAME_MAX);
        if (!TextRules.isValidName(name)) return Outcome.no("crusadecraft.name.invalid", TextRules.NAME_MAX);
        String key = NationFile.create(name, rank, color, personality, mine);
        if (key == null) return Outcome.no("crusadecraft.nation.create.failed");
        NationService.ensure(server);
        Optional<AiRealm> realm = NationService.realm(server, key);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.nation.create.failed");
        move(server, site, realm.get());
        if (mine) {
            Optional<AiRealm> worn = NationService.ruledBy(server, player.getUUID());
            if (worn.isPresent()) {
                // Une couronne à la fois : le nouveau royaume reste à l'ordinateur, et on le dit.
                return Outcome.ok(Component.translatable("crusadecraft.city.panel.realm_founded.crowned", AiNames.realm(rank, name), site.name(), worn.get().place()));
            }
            NationService.crown(player, realm.get());
        }
        return Outcome.ok(Component.translatable("crusadecraft.city.panel.realm_founded", AiNames.realm(rank, name), site.name()));
    }

    /** La ville et tout ce qui vit dedans passent à ce royaume. L'ancien, s'il n'existait que par elle, disparaît. */
    static void move(MinecraftServer server, Site site, AiRealm target) {
        CrusadeSavedData data = data(server);
        Optional<AiRealm> old = host(server, site);
        UUID was = site.owner();
        Optional<AiProvince> found = province(server, site);
        if (found.isPresent() && old.isPresent() && old.get() != target) {
            AiProvince p = found.get();
            old.get().provinces().remove(p);
            target.provinces().add(new AiProvince(p.id(), p.name(), p.dimension(), p.seat(), p.anchor(), false, p.dejureKingdom(), p.population(), p.development()));
        }
        site.setOwner(target.id());
        LineageState state = data.lineage();
        state.community(site.community()).ifPresent((Community c) -> {
            c.setRealm(target.id());
            for (Person x : state.residents(c.id())) x.setRealm(target.id());
        });
        NationService.claim(server, site, target.id());
        data.setDirty();
        // Une nation fondée par la seule grâce de cette ville n'a plus de raison d'être ; une nation écrite reste, elle.
        data.world().realm(was).ifPresent(r -> {
            if (r != target && r.landless() && r.handMade() && r.nation().isEmpty() && data.world().sites().stream().noneMatch(s -> was.equals(s.owner()))) {
                WorldEditService.removeRealm(server, was);
            }
        });
    }
}
