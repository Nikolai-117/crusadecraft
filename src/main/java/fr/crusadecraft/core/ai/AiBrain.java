package fr.crusadecraft.core.ai;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.core.world.WorldState;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * La politique des royaumes IA, une fois par jour du monde : la menace des joueurs retombe, les vassaux IA paient
 * leur suzerain, chaque royaume réfléchit (guerre s'il en a l'envie et les moyens, sinon traité avec un voisin),
 * les coalitions se forment, attaquent et se défont. Les règles elles-mêmes sont dans {@link AiRules}.
 */
public final class AiBrain {

    private AiBrain() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    public static void onNewDay(MinecraftServer server, long day) {
        CrusadeSavedData data = data(server);
        for (Realm r : data.realms()) r.setThreat(AiRules.decayThreat(r.threat(), data.wars().atWar(r.id())));
        vassalTaxes(server);
        if (CrusadeConfig.SERVER.aiDecisions.get()) {
            Random random = new Random(day * 104729L + server.getWorldData().worldGenOptions().seed());
            List<AiRealm> realms = new ArrayList<>(data.world().realms());
            Collections.shuffle(realms, random);
            int chance = CrusadeConfig.SERVER.aiDecisionChance.get();
            for (AiRealm ai : realms) {
                if (!free(server, ai) || random.nextInt(100) >= chance) continue;
                think(server, ai, random);
            }
            if (CrusadeConfig.SERVER.coalitions.get()) coalitions(server, day);
        }
        data.setDirty();
    }

    /** Un royaume qui décide pour lui-même : il tient encore une terre et ne prête hommage à personne. */
    private static boolean free(MinecraftServer server, AiRealm ai) {
        // P41 : une nation qui a un souverain ne décide plus pour elle-même : c'est lui qui répond.
        return !ai.landless() && ai.sovereign() == null && data(server).diplomacy().suzerainOf(ai.id()).isEmpty();
    }

    /** Un royaume IA réfléchit : la guerre s'il en a l'envie et les moyens, sinon un traité avec un voisin. */
    public static void think(MinecraftServer server, AiRealm ai, Random random) {
        if (CrusadeConfig.SERVER.aiWars.get() && fr.crusadecraft.core.war.CrusadeService.aiCall(server, ai, random)) return;
        if (CrusadeConfig.SERVER.aiWars.get() && !data(server).wars().atWar(ai.id()) && considerWar(server, ai)) return;
        considerTreaty(server, ai, random);
    }

    // ------------------------------------------------------------------ opinion et forces

    /** L'opinion d'un souverain IA envers un autre royaume IA : les mêmes règles qu'envers un joueur. */
    public static int opinion(MinecraftServer server, AiRealm of, AiRealm about) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(of.id(), about.id());
        boolean dejure = of.capital() != null && about.capital() != null && of.capital().dejureKingdom() == about.capital().dejureKingdom();
        DiplomacyRules.Context c = new DiplomacyRules.Context(of.personality(), of.ruler().traits(), of.rank(), about.rank(), about.prestige(),
                0, dejure, 0, WarService.strength(server, about.id(), 0), WarService.strength(server, of.id(), 0), 0);
        return Opinion.total(DiplomacyRules.opinion(rel, of.id(), about.id(), c, today));
    }

    /** La force d'un royaume et de ses alliés de plein droit (alliance totale), qui le suivraient à l'attaque. */
    static int attackSide(MinecraftServer server, UUID id) {
        long today = RealmService.currentDay(server);
        int s = WarService.strength(server, id, 0);
        for (Relation rel : data(server).diplomacy().all()) {
            if (rel.involves(id) && rel.has(Treaty.ALLIANCE, today)) s += WarService.strength(server, rel.other(id), 0) / 2;
        }
        for (UUID v : data(server).diplomacy().vassalsOf(id)) s += WarService.strength(server, v, 0) / 3;
        return s;
    }

    /** La force qui défendrait ce royaume contre {@code attacker} : lui, ses alliances (défensives comprises), ses vassaux. */
    static int defenseSide(MinecraftServer server, UUID id, UUID attacker) {
        long today = RealmService.currentDay(server);
        int s = WarService.strength(server, id, 0);
        for (Relation rel : data(server).diplomacy().all()) {
            if (!rel.involves(id) || rel.involves(attacker)) continue;
            if (rel.has(Treaty.ALLIANCE, today) || rel.has(Treaty.ALLIANCE_DEFENSIVE, today)) s += WarService.strength(server, rel.other(id), 0) / 2;
        }
        for (UUID v : data(server).diplomacy().vassalsOf(id)) if (!v.equals(attacker)) s += WarService.strength(server, v, 0) / 3;
        return s;
    }

    /** Le siège d'où un royaume IA mène sa politique : sa capitale s'il la tient, sinon sa première province. */
    static AiProvince seat(MinecraftServer server, AiRealm ai) {
        List<AiProvince> held = WarService.provincesOf(server, ai.id());
        AiProvince capital = ai.capital();
        if (capital != null && held.contains(capital)) return capital;
        return held.isEmpty() ? capital : held.get(0);
    }

    private static Optional<BlockPos> seatOf(MinecraftServer server, UUID id) {
        Optional<AiRealm> ai = data(server).world().realm(id);
        if (ai.isPresent()) {
            AiProvince p = seat(server, ai.get());
            return p == null ? Optional.empty() : Optional.of(p.seat());
        }
        return data(server).realm(id).flatMap(r -> MineColoniesBridge.center(r.capital()));
    }

    // ------------------------------------------------------------------ guerre

    /** Cherche une proie à sa mesure ; déclare la guerre si elle en trouve une. */
    public static boolean considerWar(MinecraftServer server, AiRealm ai) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        Optional<BlockPos> home = seatOf(server, ai.id());
        if (home.isEmpty()) return false;
        int mine = attackSide(server, ai.id());
        List<AiRules.Target> targets = new ArrayList<>();
        // On ne s'acharne pas sur un royaume qui se bat déjà : chacun sa guerre.
        for (AiRealm other : data.world().realms()) {
            if (other == ai || other.landless() || data.wars().atWar(other.id())) continue;
            if (WarService.declarationProblem(server, ai.id(), other.id()) != null) continue;
            Optional<BlockPos> there = seatOf(server, other.id());
            if (there.isEmpty()) continue;
            Relation rel = data.diplomacy().between(ai.id(), other.id());
            targets.add(new AiRules.Target(other.id(), false, opinion(server, ai, other), rel.hostile(), mine,
                    defenseSide(server, other.id(), ai.id()), Math.sqrt(home.get().distSqr(there.get())),
                    WarService.provincesOf(server, other.id()).size(), other.rank()));
        }
        if (CrusadeConfig.SERVER.aiWarsOnPlayers.get()) {
            for (Realm p : data.realms()) {
                if (!p.state().isActive() || !ai.discoveredBy(p.ruler())) continue;
                if (today - p.foundedDay() < CrusadeConfig.SERVER.aiGraceDays.get()) continue;
                if (data.wars().atWar(p.id()) || WarService.declarationProblem(server, ai.id(), p.id()) != null) continue;
                Optional<BlockPos> there = seatOf(server, p.id());
                if (there.isEmpty()) continue;
                Relation rel = data.diplomacy().between(ai.id(), p.id());
                targets.add(new AiRules.Target(p.id(), true, DiplomacyService.opinion(server, ai, p), rel.hostile(), mine,
                        defenseSide(server, p.id(), ai.id()), Math.sqrt(home.get().distSqr(there.get())),
                        WarService.provincesOf(server, p.id()).size(), p.rank()));
            }
        }
        AiRules.Target t = AiRules.pickTarget(ai.personality(), targets, CrusadeConfig.SERVER.aiWarRange.get());
        if (t == null) return false;
        declare(server, ai, t);
        return true;
    }

    /** La déclaration elle-même : le motif, la province la plus proche si l'on en veut une, la lettre au joueur visé. */
    public static War declare(MinecraftServer server, AiRealm ai, AiRules.Target t) {
        CasusBelli cb = AiRules.casusBelli(ai.rank(), t);
        int province = -1;
        if (cb == CasusBelli.CONQUEST) {
            BlockPos home = seatOf(server, ai.id()).orElse(BlockPos.ZERO);
            AiProvince best = null;
            for (AiProvince p : WarService.provincesOf(server, t.id())) {
                if (best == null || p.distanceSqr(home.getX(), home.getZ()) < best.distanceSqr(home.getX(), home.getZ())) best = p;
            }
            if (best != null) province = best.id();
        }
        War war = WarService.declareBetween(server, ai.id(), t.id(), cb, province);
        fr.crusadecraft.core.nation.NationService.onWarDeclared(server, ai, t.id());
        if (t.player()) {
            data(server).realm(t.id()).ifPresent(p -> {
                long today = RealmService.currentDay(server);
                DiplomacyService.post(server, p, new Letter(UUID.randomUUID(), ai.id(), true, p.id(), sender(ai),
                        Component.translatable("crusadecraft.letter.ai_war.subject"),
                        Component.translatable("crusadecraft.letter.ai_war.body", AiNames.realmThe(ai.rank(), ai.place()),
                                Component.translatable(cb.key())), List.of(), today, today + 7, 0));
            });
        }
        return war;
    }

    private static Component sender(AiRealm ai) {
        return Component.translatable("crusadecraft.letter.sender", ai.ruler().name(),
                Component.translatable(ai.rank().holderKey(ai.ruler().female())), AiNames.realm(ai.rank(), ai.place()));
    }

    // ------------------------------------------------------------------ traités

    /** Un voisin au hasard ; si chacun estime l'autre, ils signent (commerce, non-agression, alliance). */
    public static Treaty considerTreaty(MinecraftServer server, AiRealm ai, Random random) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        List<AiRealm> others = new ArrayList<>();
        for (AiRealm o : data.world().realms()) {
            if (o != ai && free(server, o) && data.wars().between(ai.id(), o.id()).isEmpty()) others.add(o);
        }
        if (others.isEmpty()) return null;
        AiRealm other = others.get(random.nextInt(others.size()));
        Relation rel = data.diplomacy().between(ai.id(), other.id());
        if (rel.war() || rel.hostile()) return null;
        Set<Treaty> current = EnumSet.noneOf(Treaty.class);
        for (Treaty t : Treaty.values()) if (rel.has(t, today)) current.add(t);
        Treaty t = AiRules.treaty(ai.personality(), other.personality(), opinion(server, ai, other), opinion(server, other, ai), current);
        if (t == null) return null;
        rel.sign(t, today);
        rel.addOpinion(ai.id(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
        rel.addOpinion(other.id(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
        if (t == Treaty.ALLIANCE || t == Treaty.ALLIANCE_DEFENSIVE) {
            publish(server, WorldNews.of(today, "crusadecraft.news.alliance", List.of(ai.id(), other.id()),
                    AiNames.realm(ai.rank(), ai.place()), AiNames.realm(other.rank(), other.place()), Component.translatable(t.key())), false);
        }
        return t;
    }

    /** Les vassaux IA d'un suzerain IA lui versent chaque jour l'impôt du contrat (ceux des joueurs passent par la diplomatie). */
    static void vassalTaxes(MinecraftServer server) {
        WorldState world = data(server).world();
        for (Relation rel : data(server).diplomacy().all()) {
            if (rel.suzerain() == null) continue;
            Optional<AiRealm> lord = world.realm(rel.suzerain()), vassal = world.realm(rel.other(rel.suzerain()));
            if (lord.isEmpty() || vassal.isEmpty()) continue;
            long share = Math.min(Math.max(0, vassal.get().gold()), Math.round(vassal.get().lastIncome() * rel.vassalTax() / 100.0));
            if (share <= 0) continue;
            vassal.get().setGold(vassal.get().gold() - share);
            lord.get().setGold(lord.get().gold() + share);
        }
    }

    // ------------------------------------------------------------------ coalitions

    /**
     * Contre chaque joueur trop menaçant, les royaumes qui le craignent se liguent. Annoncée dès deux membres, la
     * coalition défend chacun des siens ; si elle pèse assez, elle attaque. Elle se défait quand la menace retombe.
     */
    public static void coalitions(MinecraftServer server, long day) {
        CrusadeSavedData data = data(server);
        WorldState world = data.world();
        int threshold = CrusadeConfig.SERVER.coalitionThreshold.get();
        world.coalitions().removeIf(c -> data.realm(c.target()).isEmpty());
        for (Realm p : data.realms()) {
            Optional<Coalition> existing = world.coalitionAgainst(p.id());
            if (existing.isPresent() && AiRules.coalitionDissolves(p.threat(), threshold) && !atWarWithMembers(server, existing.get())) {
                world.coalitions().remove(existing.get());
                if (existing.get().announced()) {
                    notify(server, p, ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.coalition_end.title"),
                            Component.translatable("crusadecraft.toast.coalition_end.body"));
                    publish(server, WorldNews.of(day, "crusadecraft.news.coalition_end", List.of(p.id()), Component.literal(p.name())), false);
                }
                continue;
            }
            if (p.threat() < threshold && existing.isEmpty()) continue;
            Coalition c = existing.orElseGet(() -> {
                Coalition fresh = new Coalition(p.id(), day);
                world.coalitions().add(fresh);
                return fresh;
            });
            if (p.threat() >= threshold) recruit(server, c, p, threshold);
            if (c.active() && !c.announced()) {
                c.setAnnounced(true);
                notify(server, p, ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.coalition.title"),
                        Component.translatable("crusadecraft.toast.coalition.body", c.members().size()));
                fr.crusadecraft.core.intrigue.Chronicle.record(server, p, "crusadecraft.chronicle.coalition", Component.literal(String.valueOf(c.members().size())));
                publish(server, WorldNews.of(day, "crusadecraft.news.coalition", new ArrayList<>(c.members()),
                        Component.literal(p.name()), Component.literal(String.valueOf(c.members().size()))), false);
            }
            if (c.active()) coalitionWar(server, c, p, day);
        }
    }

    /** Les royaumes qui craignent ce joueur, et que rien ne lie à lui, rejoignent la coalition. */
    static void recruit(MinecraftServer server, Coalition c, Realm p, int threshold) {
        long today = RealmService.currentDay(server);
        for (AiRealm ai : data(server).world().realms()) {
            if (!free(server, ai) || c.members().contains(ai.id()) || !ai.discoveredBy(p.ruler())) continue;
            Relation rel = data(server).diplomacy().between(ai.id(), p.id());
            boolean bound = rel.has(Treaty.ALLIANCE, today) || rel.has(Treaty.ALLIANCE_DEFENSIVE, today)
                    || rel.has(Treaty.NON_AGRESSION, today) || rel.suzerain() != null;
            if (AiRules.joinsCoalition(ai.personality(), DiplomacyService.opinion(server, ai, p), p.threat(), threshold, bound)) {
                c.members().add(ai.id());
            }
        }
    }

    private static boolean atWarWithMembers(MinecraftServer server, Coalition c) {
        for (UUID m : c.members()) if (data(server).wars().between(m, c.target()).isPresent()) return true;
        return false;
    }

    /** Si elle pèse nettement plus que le joueur et ses alliés, la coalition attaque, menée par son plus fort membre. */
    static void coalitionWar(MinecraftServer server, Coalition c, Realm p, long day) {
        if (!CrusadeConfig.SERVER.aiWarsOnPlayers.get() || atWarWithMembers(server, c)) return;
        if (day - p.foundedDay() < CrusadeConfig.SERVER.aiGraceDays.get()) return;
        int strength = 0;
        AiRealm leader = null;
        for (UUID m : c.members()) {
            Optional<AiRealm> ai = data(server).world().realm(m);
            if (ai.isEmpty()) continue;
            int s = WarService.strength(server, m, 0);
            strength += s;
            if (WarService.declarationProblem(server, m, p.id()) == null
                    && (leader == null || s > WarService.strength(server, leader.id(), 0))) leader = ai.get();
        }
        if (leader == null || !AiRules.coalitionAttacks(strength, defenseSide(server, p.id(), leader.id()), c.members().size())) return;
        int provinces = WarService.provincesOf(server, p.id()).size();
        War war = declare(server, leader, new AiRules.Target(p.id(), true, -100, true, strength, 0, 0, provinces, p.rank()));
        for (UUID m : c.members()) if (war.side(m) == 0) war.attackerAllies().add(m);
        publish(server, WorldNews.of(day, "crusadecraft.news.coalition_war", new ArrayList<>(c.members()),
                AiNames.realm(leader.rank(), leader.place()), Component.literal(p.name())), true);
    }

    // ------------------------------------------------------------------ nouvelles

    /**
     * Une nouvelle du monde : gardée pour l'onglet des chroniques, et annoncée (si {@code toast}) aux souverains en
     * ligne qui connaissent l'un des royaumes en cause. Ceux qui y sont mêlés ont déjà leurs propres messages.
     */
    public static void publish(MinecraftServer server, WorldNews n, boolean toast) {
        CrusadeSavedData data = data(server);
        data.world().addNews(n);
        data.setDirty();
        if (!toast || !CrusadeConfig.SERVER.worldNewsToasts.get()) return;
        for (Realm r : data.realms()) {
            if (n.realms().contains(r.id()) || !knows(server, r, n)) continue;
            ServerPlayer ruler = server.getPlayerList().getPlayer(r.ruler());
            if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.EVENT,
                    Component.translatable("crusadecraft.toast.news.title"), n.text()));
        }
    }

    /** Ce souverain connaît-il l'un des royaumes dont parle la nouvelle ? Les royaumes des joueurs sont connus de tous. */
    public static boolean knows(MinecraftServer server, Realm r, WorldNews n) {
        for (UUID id : n.realms()) {
            if (id.equals(r.id()) || data(server).realm(id).isPresent()) return true;
            Optional<AiRealm> ai = data(server).world().realm(id);
            if (ai.isPresent() && ai.get().discoveredBy(r.ruler())) return true;
        }
        return false;
    }

    /** Les nouvelles qu'un souverain peut lire, les plus récentes en tête. */
    public static List<WorldNews> newsFor(MinecraftServer server, Realm r) {
        List<WorldNews> out = new ArrayList<>();
        for (WorldNews n : data(server).world().news()) if (knows(server, r, n)) out.add(n);
        return out;
    }

    private static void notify(MinecraftServer server, Realm p, ToastPacket.Kind kind, Component title, Component body) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(p.ruler());
        if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(kind, title, body));
    }
}
