package fr.crusadecraft.core.war;

import fr.crusadecraft.core.ai.AiBrain;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.intrigue.Chronicle;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les croisades : une guerre sainte contre un royaume païen ou hérétique, à laquelle les royaumes fidèles se
 * rallient (les fanatiques toujours, les autres s'ils estiment celui qui l'appelle), et où les autres souverains
 * sont appelés par lettre. La victoire est bénie : piété et prestige pour tous les croisés.
 */
public final class CrusadeService {
    /** Piété et prestige de chaque croisé victorieux. */
    public static final int BLESSING_PIETY = 50;
    public static final int BLESSING_PRESTIGE = 30;

    private CrusadeService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Appelle une croisade : guerre sainte déclarée, fidèles ralliés, autres souverains appelés. */
    public static War call(MinecraftServer server, UUID leader, UUID target, int province) {
        War w = WarService.declareBetween(server, leader, target, CasusBelli.GUERRE_SAINTE, province);
        w.setCrusade(true);
        rally(server, w);
        invite(server, w);
        long today = RealmService.currentDay(server);
        AiBrain.publish(server, WorldNews.of(today, "crusadecraft.news.crusade", List.of(leader, target), WarService.name(server, leader),
                WarService.name(server, target)), true);
        data(server).setDirty();
        return w;
    }

    /** Lié au royaume visé (allié, pacte, vassal, suzerain), on ne prend pas la croix contre lui. */
    private static boolean bound(MinecraftServer server, UUID realm, UUID target) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(realm, target);
        return rel.has(Treaty.ALLIANCE, today) || rel.has(Treaty.ALLIANCE_DEFENSIVE, today) || rel.has(Treaty.NON_AGRESSION, today)
                || rel.suzerain() != null;
    }

    /** Les royaumes IA fidèles se rallient : les fanatiques toujours, les autres s'ils estiment le meneur. */
    static void rally(MinecraftServer server, War w) {
        for (AiRealm ai : data(server).world().realms()) {
            if (ai.landless() || ai.faith().infidel() || w.side(ai.id()) != 0) continue;
            if (data(server).diplomacy().suzerainOf(ai.id()).isPresent() || bound(server, ai.id(), w.defender())) continue;
            boolean joins = ai.personality() == AiPersonality.FANATIQUE || opinionOfLeader(server, ai, w.attacker()) >= 20;
            if (joins) w.attackerAllies().add(ai.id());
        }
    }

    private static int opinionOfLeader(MinecraftServer server, AiRealm ai, UUID leader) {
        Optional<AiRealm> other = data(server).world().realm(leader);
        if (other.isPresent()) return AiBrain.opinion(server, ai, other.get());
        return data(server).realm(leader).map(r -> DiplomacyService.opinion(server, ai, r)).orElse(0);
    }

    /** Les souverains qui connaissent le meneur sont appelés à prendre la croix, par lettre. */
    static void invite(MinecraftServer server, War w) {
        long today = RealmService.currentDay(server);
        Optional<AiRealm> aiLeader = data(server).world().realm(w.attacker());
        Optional<Realm> playerLeader = data(server).realm(w.attacker());
        Component sender = aiLeader.map(a -> (Component) Component.translatable("crusadecraft.letter.sender", a.ruler().name(),
                        Component.translatable(a.rank().holderKey(a.ruler().female())), AiNames.realm(a.rank(), a.place())))
                .orElseGet(() -> playerLeader.map(r -> (Component) Component.translatable("crusadecraft.letter.sender", r.rulerName(),
                        Component.translatable(r.rank().holderKey(r.feminineTitle())), r.name())).orElse(Component.literal("?")));
        for (Realm r : data(server).realms()) {
            if (!r.state().isActive() || w.side(r.id()) != 0 || r.id().equals(w.defender())) continue;
            if (aiLeader.isPresent() && !aiLeader.get().discoveredBy(r.ruler())) continue;
            DiplomacyService.post(server, r, new Letter(UUID.randomUUID(), w.attacker(), aiLeader.isPresent(), r.id(), sender,
                    Component.translatable("crusadecraft.letter.crusade.subject"),
                    Component.translatable("crusadecraft.letter.crusade.body", WarService.name(server, w.attacker()), WarService.name(server, w.defender())),
                    List.of(new Letter.Choice(Letter.Action.JOIN_CRUSADE, false), new Letter.Choice(Letter.Action.REFUSE, false)),
                    today, today + 5, 0).withPeace(w.id(), "CRUSADE"));
        }
    }

    /** Un souverain prend la croix : il rejoint le camp des croisés. */
    public static CouncilActions.Outcome join(MinecraftServer server, Realm realm, UUID warId) {
        Optional<War> war = warId == null ? Optional.empty() : data(server).wars().get(warId);
        if (war.isEmpty() || !war.get().crusade()) return CouncilActions.Outcome.no("crusadecraft.crusade.over");
        War w = war.get();
        if (w.side(realm.id()) != 0) return CouncilActions.Outcome.no("crusadecraft.crusade.already");
        if (bound(server, realm.id(), w.defender())) return CouncilActions.Outcome.no("crusadecraft.crusade.bound");
        w.attackerAllies().add(realm.id());
        Chronicle.record(server, realm, "crusadecraft.chronicle.crusade.joined", WarService.name(server, w.attacker()), WarService.name(server, w.defender()));
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.crusade.joined", WarService.name(server, w.defender())));
    }

    /** La croisade victorieuse : chaque croisé reçoit piété et prestige. */
    static void bless(MinecraftServer server, War w) {
        for (UUID id : WarService.participants(w)) {
            if (w.side(id) != 1) continue;
            data(server).world().realm(id).ifPresent(a -> {
                a.setPiety(a.piety() + BLESSING_PIETY);
                a.setPrestige(a.prestige() + BLESSING_PRESTIGE);
            });
            data(server).realm(id).ifPresent(r -> {
                r.setPiety(r.piety() + BLESSING_PIETY);
                r.setPrestige(r.prestige() + BLESSING_PRESTIGE);
                Chronicle.record(server, r, "crusadecraft.chronicle.crusade.won", WarService.name(server, w.defender()));
            });
        }
    }

    /**
     * Un royaume IA fanatique, pieux et en paix appelle parfois à la croisade contre le plus proche royaume païen
     * ou hérétique. Renvoie vrai s'il l'a fait.
     */
    public static boolean aiCall(MinecraftServer server, AiRealm ai, Random random) {
        if (ai.personality() != AiPersonality.FANATIQUE || ai.faith().infidel() || ai.piety() < 150) return false;
        if (data(server).wars().atWar(ai.id()) || random.nextDouble() >= 0.15) return false;
        AiProvince home = ai.capital();
        if (home == null) return false;
        AiRealm target = null;
        double best = Double.MAX_VALUE;
        for (AiRealm other : data(server).world().realms()) {
            if (other == ai || !other.faith().infidel() || other.landless() || data(server).wars().atWar(other.id())) continue;
            if (WarService.declarationProblem(server, ai.id(), other.id()) != null) continue;
            AiProvince seat = other.capital();
            if (seat == null) continue;
            double d = seat.distanceSqr(home.seat().getX(), home.seat().getZ());
            if (d < best) {
                best = d;
                target = other;
            }
        }
        if (target == null) return false;
        int province = -1;
        double nearest = Double.MAX_VALUE;
        BlockPos h = home.seat();
        for (AiProvince p : WarService.provincesOf(server, target.id())) {
            double d = p.distanceSqr(h.getX(), h.getZ());
            if (d < nearest) {
                nearest = d;
                province = p.id();
            }
        }
        ai.setPiety(ai.piety() - CasusBelli.CRUSADE_PIETY);
        call(server, ai.id(), target.id(), province);
        return true;
    }
}
