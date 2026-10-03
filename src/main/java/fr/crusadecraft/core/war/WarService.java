package fr.crusadecraft.core.war;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.diplomacy.Diplomacy;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldState;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.SealItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * La guerre côté serveur : déclarations et alliés, armées, campagne de chaque jour, occupation, paix scellée et
 * ses conséquences. Les combats en jeu (sièges, raids) vivent dans {@link WarFieldService}.
 */
public final class WarService {

    private WarService() {}

    // ------------------------------------------------------------------ royaumes

    static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    static Optional<AiRealm> ai(MinecraftServer server, UUID id) {
        return data(server).world().realm(id);
    }

    static Optional<Realm> player(MinecraftServer server, UUID id) {
        return data(server).realm(id);
    }

    public static WarView.Party party(MinecraftServer server, UUID id) {
        Optional<AiRealm> a = ai(server, id);
        if (a.isPresent()) return new WarView.Party(id, true, a.get().place(), a.get().rank(), a.get().color());
        Realm r = player(server, id).orElse(null);
        return r == null ? new WarView.Party(id, false, "?", fr.crusadecraft.core.realm.TitleRank.BARONNIE, 0x808080)
                : new WarView.Party(id, false, r.name(), r.rank(), r.color());
    }

    static Component name(MinecraftServer server, UUID id) {
        return DiplomacyService.name(server, id);
    }

    /** Les provinces IA d'un royaume : les siennes (IA) ou celles qu'il a conquises (joueur). */
    public static List<AiProvince> provincesOf(MinecraftServer server, UUID id) {
        List<AiProvince> out = new ArrayList<>();
        for (AiRealm r : data(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                UUID holder = p.holder() == null ? r.id() : p.holder();
                if (holder.equals(id)) out.add(p);
            }
        }
        return out;
    }

    static Optional<AiProvince> province(MinecraftServer server, int id) {
        for (AiRealm r : data(server).world().realms()) for (AiProvince p : r.provinces()) if (p.id() == id) return Optional.of(p);
        return Optional.empty();
    }

    /** Qui tient cette province aujourd'hui. */
    static UUID holderOf(MinecraftServer server, AiProvince p) {
        if (p.holder() != null) return p.holder();
        for (AiRealm r : data(server).world().realms()) if (r.provinces().contains(p)) return r.id();
        return null;
    }

    /** Le revenu quotidien des provinces conquises par un royaume de joueur (hors celles que l'ennemi occupe). */
    public static long conquestIncome(MinecraftServer server, Realm r) {
        long sum = 0;
        for (AiProvince p : provincesOf(server, r.id())) {
            boolean occupied = data(server).wars().all().stream().anyMatch(w -> w.occupied().contains(p.id()));
            if (!occupied) sum += p.income();
        }
        return sum;
    }

    // ------------------------------------------------------------------ armées

    /** Hommes qu'un royaume de joueur peut lever : six par garde, un pour trois citoyens. */
    public static int maxMen(Realm r) {
        int men = 0;
        for (ColonyRef colony : r.allColonies()) {
            ColonyReport report = MineColoniesBridge.report(colony);
            men += report.guards() * 6 + report.citizens() / 3;
        }
        return Math.max(5, men);
    }

    public static int men(Realm r) {
        if (r.armyMen() < 0) r.setArmyMen(maxMen(r));
        return r.armyMen();
    }

    /** Solde de l'armée levée : une pièce par dizaine d'hommes et par jour. */
    public static int upkeep(Realm r) {
        return r.armyRaised() ? (int) Math.ceil(men(r) / 10.0) : 0;
    }

    /** Les levées des vassaux : la part du contrat, réduite si le vassal est mécontent. */
    static int vassalLevies(MinecraftServer server, Realm r) {
        Diplomacy d = data(server).diplomacy();
        int levies = 0;
        for (UUID v : d.vassalsOf(r.id())) {
            Relation rel = d.between(v, r.id());
            Optional<AiRealm> a = ai(server, v);
            if (a.isPresent()) {
                int opinion = DiplomacyService.opinion(server, a.get(), r);
                double factor = opinion < -50 ? 0 : opinion < 0 ? 0.5 : 1.0;
                levies += (int) Math.round(strength(a.get(), 0) * rel.vassalLevy() / 100.0 * factor);
            } else {
                Optional<Realm> o = player(server, v);
                if (o.isPresent()) levies += (int) Math.round(maxMen(o.get()) * rel.vassalLevy() / 100.0);
            }
        }
        return levies;
    }

    /** Force d'un royaume IA : sa population selon son tempérament, entamée par la fatigue. */
    static int strength(AiRealm a, int exhaustion) {
        return (int) Math.round(a.population() * a.personality().levyFactor() * 0.8 * (1 - exhaustion / 200.0));
    }

    /** Force sur le terrain d'un royaume engagé : une armée non levée ne compte que pour un tiers. */
    public static int strength(MinecraftServer server, UUID id, int exhaustion) {
        Optional<AiRealm> a = ai(server, id);
        if (a.isPresent()) return strength(a.get(), exhaustion);
        Optional<Realm> r = player(server, id);
        if (r.isEmpty()) return 0;
        int men = men(r.get());
        return (r.get().armyRaised() ? men : men / 3) + vassalLevies(server, r.get());
    }

    static int sideStrength(MinecraftServer server, War w, int side) {
        int exhaustion = side > 0 ? w.attackerExhaustion() : w.defenderExhaustion();
        int s = strength(server, side > 0 ? w.attacker() : w.defender(), exhaustion);
        for (UUID ally : side > 0 ? w.attackerAllies() : w.defenderAllies()) s += strength(server, ally, exhaustion) / 2;
        return s;
    }

    /** Le Martial du général d'un camp : le maréchal du royaume qui mène ce camp. */
    static int general(MinecraftServer server, UUID id) {
        Optional<AiRealm> a = ai(server, id);
        if (a.isPresent()) {
            var marshal = a.get().council().get(Post.MARECHAL);
            return marshal == null ? 0 : marshal.stat(Stat.MARTIAL);
        }
        Optional<Realm> r = player(server, id);
        if (r.isEmpty()) return 0;
        CitizenRef marshal = r.get().council().get(Post.MARECHAL);
        if (marshal == null) return 0;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(marshal);
        if (info.isEmpty()) return 0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return 0;
        return CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.MARTIAL.ordinal()];
    }

    public static CouncilActions.Outcome raise(ServerPlayer player, boolean raise) {
        Optional<Realm> found = RealmService.realmOf(player.server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm r = found.get();
        if (r.armyRaised() == raise) return CouncilActions.Outcome.no(raise ? "crusadecraft.war.army.already" : "crusadecraft.war.army.not_raised");
        r.setArmyRaised(raise);
        data(player.server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable(raise ? "crusadecraft.war.army.raised" : "crusadecraft.war.army.disbanded", men(r)));
    }

    // ------------------------------------------------------------------ motifs et déclaration

    private static boolean sameDejure(MinecraftServer server, Realm attacker, AiProvince p) {
        WorldState world = data(server).world();
        Optional<BlockPos> center = MineColoniesBridge.center(attacker.capital());
        if (center.isEmpty() || world.dejure().isEmpty()) return false;
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < world.dejure().size(); i++) {
            double d = Math.hypot(world.dejure().get(i).centerX() - center.get().getX(), world.dejure().get(i).centerZ() - center.get().getZ());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return p.dejureKingdom() == best;
    }

    private static WarRules.Bonds bonds(MinecraftServer server, UUID a, UUID b, long today) {
        Diplomacy d = data(server).diplomacy();
        Relation rel = d.between(a, b);
        boolean vassalOfOther = d.suzerainOf(b).map(s -> !s.equals(a)).orElse(false);
        return new WarRules.Bonds(data(server).wars().between(a, b).isPresent(), rel.truce(today), rel.has(Treaty.NON_AGRESSION, today),
                rel.has(Treaty.ALLIANCE, today) || rel.has(Treaty.ALLIANCE_DEFENSIVE, today), rel.suzerain() != null, vassalOfOther);
    }

    /** Ce qu'on peut tenter contre un royaume : interdiction générale, motifs ouverts et provinces visables. */
    public static WarView.Target target(MinecraftServer server, Realm me, UUID other) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(me.id(), other);
        String problem = WarRules.declarationProblem(bonds(server, me.id(), other, today));
        Optional<AiRealm> a = ai(server, other);
        int opinion = a.map(x -> DiplomacyService.opinion(server, x, me)).orElse(0);
        boolean isVassal = data(server).diplomacy().suzerainOf(other).isPresent();
        List<AiProvince> theirs = provincesOf(server, other);
        List<WarView.CbOption> cbs = new ArrayList<>();
        for (CasusBelli cb : CasusBelli.values()) {
            List<WarView.ProvinceOption> provinces = new ArrayList<>();
            boolean anyClaim = false, anyDejure = false;
            if (cb.targetsProvince()) {
                for (AiProvince p : theirs) {
                    boolean ok = switch (cb) {
                        case CLAIM -> me.claims().contains(p.id());
                        case DEJURE -> sameDejure(server, me, p);
                        default -> true;
                    };
                    if (cb == CasusBelli.CLAIM && ok) anyClaim = true;
                    if (cb == CasusBelli.DEJURE && ok) anyDejure = true;
                    if (ok) provinces.add(new WarView.ProvinceOption(p.id(), p.name()));
                }
            }
            WarRules.CbContext ctx = new WarRules.CbContext(me.rank(), party(server, other).rank(), anyClaim, anyDejure,
                    rel.hostile(), opinion, me.prestige(), isVassal);
            String cbProblem = WarRules.cbProblem(cb, ctx);
            if (cbProblem == null && cb.targetsProvince() && provinces.isEmpty()) cbProblem = "crusadecraft.war.cb.no_province";
            cbs.add(new WarView.CbOption(cb, cbProblem, provinces));
        }
        DiploStatus status = rel.status(me.id(), today);
        if (data(server).wars().between(me.id(), other).isPresent()) status = DiploStatus.GUERRE;
        return new WarView.Target(party(server, other), status, strength(server, other, 0), problem, cbs);
    }

    public static CouncilActions.Outcome declare(ServerPlayer player, UUID target, CasusBelli cb, int provinceId) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty() || !found.get().state().isActive()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm me = found.get();
        boolean known = ai(server, target).map(a -> a.discoveredBy(player.getUUID())).orElse(player(server, target).isPresent());
        if (!known || target.equals(me.id())) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        WarView.Target t = target(server, me, target);
        if (t.problem() != null) return CouncilActions.Outcome.no(t.problem());
        WarView.CbOption option = t.cbs().stream().filter(c -> c.cb() == cb).findFirst().orElse(null);
        if (option == null || option.problem() != null) return CouncilActions.Outcome.no(option == null ? "crusadecraft.war.cb.no_province" : option.problem());
        int province = -1;
        if (cb.targetsProvince()) {
            province = option.provinces().stream().anyMatch(p -> p.id() == provinceId) ? provinceId : option.provinces().get(0).id();
        }
        long today = RealmService.currentDay(server);
        me.setPrestige(me.prestige() - cb.prestigeCost());
        War war = new War(UUID.randomUUID(), me.id(), target, cb, province, today);
        Diplomacy d = data(server).diplomacy();
        // Les alliés : les vôtres (alliance totale) et ceux de l'adversaire (alliances, y compris défensives).
        for (Relation rel : d.all()) {
            if (!rel.involves(me.id()) && !rel.involves(target)) continue;
            if (rel.involves(me.id()) && !rel.involves(target) && rel.has(Treaty.ALLIANCE, today)) war.attackerAllies().add(rel.other(me.id()));
            if (rel.involves(target) && !rel.involves(me.id()) && (rel.has(Treaty.ALLIANCE, today) || rel.has(Treaty.ALLIANCE_DEFENSIVE, today))) {
                UUID ally = rel.other(target);
                if (!war.attackerAllies().contains(ally)) war.defenderAllies().add(ally);
            }
        }
        // Les vassaux suivent leur suzerain.
        for (UUID v : d.vassalsOf(me.id())) if (!v.equals(target)) war.attackerAllies().add(v);
        for (UUID v : d.vassalsOf(target)) if (!v.equals(me.id())) war.defenderAllies().add(v);
        Relation rel = d.between(me.id(), target);
        rel.setWar(true);
        rel.setHostile(true);
        if (ai(server, target).isPresent()) rel.addOpinion(target, OpinionModifier.of(OpinionReason.INSULTE, today, 1));
        data(server).wars().add(war);
        String goal = province >= 0 ? province(server, province).map(AiProvince::name).orElse("?") : "";
        war.log(today, "crusadecraft.war.log.declared", 0, cb.name(), goal);
        hostileRanks(server, war, true);
        data(server).setDirty();
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} déclare la guerre ({}) à {}", me.name(), cb, name(server, target).getString());
        Component enemy = name(server, target);
        notify(server, me.id(), ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.declared.title"),
                Component.translatable("crusadecraft.toast.war.declared.body", enemy, Component.translatable(cb.key())));
        notify(server, target, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.attacked.title"),
                Component.translatable("crusadecraft.toast.war.attacked.body", Component.literal(me.name())));
        for (UUID ally : war.attackerAllies()) notify(server, ally, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.called.title"),
                Component.translatable("crusadecraft.toast.war.called.body", Component.literal(me.name()), enemy));
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.war.declared", enemy));
    }

    /** En guerre entre joueurs, chaque souverain devient « Hostile » dans les colonies de l'autre ; rendu à la paix. */
    private static void hostileRanks(MinecraftServer server, War w, boolean start) {
        if (!CrusadeConfig.SERVER.hostileRanks.get()) return;
        Optional<Realm> a = player(server, w.attacker()), b = player(server, w.defender());
        if (a.isEmpty() || b.isEmpty()) return;
        for (Realm[] pair : new Realm[][]{{a.get(), b.get()}, {b.get(), a.get()}}) {
            for (ColonyRef colony : pair[0].allColonies()) {
                String key = colony + "|" + pair[1].ruler();
                if (start) {
                    String previous = MineColoniesBridge.setHostile(colony, pair[1].ruler());
                    if (previous != null) w.savedRanks().put(key, previous);
                } else if (w.savedRanks().containsKey(key)) {
                    MineColoniesBridge.setRankByName(colony, pair[1].ruler(), w.savedRanks().get(key));
                }
            }
        }
    }

    // ------------------------------------------------------------------ score

    /** Les terres d'un camp, et combien l'autre en tient. */
    private static int occupationFor(MinecraftServer server, War w, int side) {
        UUID main = side > 0 ? w.attacker() : w.defender();
        List<AiProvince> provinces = provincesOf(server, main);
        int total = provinces.size(), held = 0;
        boolean capital = false;
        for (AiProvince p : provinces) {
            if (w.occupied().contains(p.id())) {
                held++;
                capital |= p.capital();
            }
        }
        Optional<Realm> r = player(server, main);
        if (r.isPresent()) {
            for (ColonyRef c : r.get().allColonies()) {
                total++;
                if (w.occupiedColonies().contains(c.toString())) {
                    held++;
                    capital |= c.equals(r.get().capital());
                }
            }
        }
        return WarRules.occupationScore(held, total, capital);
    }

    /** Le score de la guerre, vu de l'attaquant. */
    public static int score(MinecraftServer server, War w) {
        int attackerGains = occupationFor(server, w, -1);
        int defenderGains = occupationFor(server, w, 1);
        return WarRules.score(w.battleScore(), attackerGains - defenderGains, w.goalTicks());
    }

    // ------------------------------------------------------------------ chaque jour

    /** Le passage d'un jour : campagne, occupations, fatigue, solde des armées, offres de paix des IA. */
    public static void onNewDay(MinecraftServer server, long day) {
        Random random = new Random(day * 7919L + server.getWorldData().worldGenOptions().seed());
        for (War w : new ArrayList<>(data(server).wars().all())) {
            int a = sideStrength(server, w, 1), d = sideStrength(server, w, -1);
            WarRules.Skirmish s = WarRules.skirmish(a, d, general(server, w.attacker()), general(server, w.defender()), random.nextDouble());
            w.addBattle(s.delta());
            losses(server, w.attacker(), s.lossA());
            losses(server, w.defender(), s.lossB());
            w.tire(1 + s.lossA() / 3, 1 + s.lossB() / 3);
            if (s.delta() != 0) {
                UUID winner = s.delta() > 0 ? w.attacker() : w.defender();
                w.log(day, "crusadecraft.war.log.skirmish", s.delta(), name(server, winner).getString());
                Component body = Component.translatable("crusadecraft.toast.war.skirmish.body", name(server, winner), Math.abs(s.delta()));
                notifyBoth(server, w, ToastPacket.Kind.WAR, Component.translatable("crusadecraft.toast.war.skirmish.title"), body);
            }
            if (w.targetProvince() >= 0 && w.occupied().contains(w.targetProvince())) w.tickGoal();
            occupationIncome(server, w);
            aiOffers(server, w, day);
        }
        // Solde des armées levées et reconstitution en temps de paix.
        for (Realm r : data(server).realms()) {
            int max = maxMen(r);
            if (r.armyRaised()) TreasuryService.charge(server, r, LedgerCategory.ARMEE, upkeep(r));
            if (!data(server).wars().atWar(r.id())) r.setArmyMen(Math.min(max, men(r) + Math.max(1, max / 10)));
            else r.setArmyMen(Math.min(max, men(r)));
        }
        data(server).setDirty();
    }

    private static void losses(MinecraftServer server, UUID id, int percent) {
        player(server, id).ifPresent(r -> {
            int men = men(r);
            int lost = (int) Math.round(men * percent / 100.0 * (r.armyRaised() ? 1.0 : 0.4));
            r.setArmyMen(Math.max(0, men - lost));
        });
    }

    /** Occupation fiscale : la province occupée paie son revenu à l'occupant. */
    private static void occupationIncome(MinecraftServer server, War w) {
        for (int id : w.occupied()) {
            Optional<AiProvince> p = province(server, id);
            if (p.isEmpty()) continue;
            UUID holder = holderOf(server, p.get());
            if (holder == null) continue;
            int holderSide = w.side(holder);
            UUID occupier = holderSide > 0 ? w.defender() : w.attacker();
            player(server, occupier).ifPresent(r -> TreasuryService.credit(server, r, LedgerCategory.BUTIN, p.get().income()));
        }
    }

    /** Une IA qui perd gros propose sa reddition ; une IA qui gagne gros exige la vôtre. */
    private static void aiOffers(MinecraftServer server, War w, long day) {
        if (w.lastAiOfferDay() != Long.MIN_VALUE && day - w.lastAiOfferDay() < 3) return;
        for (int side : new int[]{1, -1}) {
            UUID main = side > 0 ? w.attacker() : w.defender();
            UUID enemy = side > 0 ? w.defender() : w.attacker();
            Optional<AiRealm> ai = ai(server, main);
            Optional<Realm> player = player(server, enemy);
            if (ai.isEmpty() || player.isEmpty()) continue;
            int aiScore = side * score(server, w);
            int exhaustion = side > 0 ? w.attackerExhaustion() : w.defenderExhaustion();
            WarRules.Peace offer = null;
            if (aiScore <= -50 || (aiScore <= -25 && exhaustion >= 60)) offer = WarRules.Peace.SURRENDER;
            else if (aiScore >= 60) offer = WarRules.Peace.VICTORY;
            if (offer == null) continue;
            w.setLastAiOfferDay(day);
            Component realm = AiNames.realmThe(ai.get().rank(), ai.get().place());
            String key = offer == WarRules.Peace.SURRENDER ? "surrender" : "demand";
            DiplomacyService.post(server, player.get(), new Letter(UUID.randomUUID(), main, true, player.get().id(),
                    Component.translatable("crusadecraft.letter.sender", ai.get().ruler().name(),
                            Component.translatable(ai.get().rank().holderKey(ai.get().ruler().female())), AiNames.realm(ai.get().rank(), ai.get().place())),
                    Component.translatable("crusadecraft.letter.peace_" + key + ".subject"),
                    Component.translatable("crusadecraft.letter.peace_" + key + ".body", realm),
                    List.of(new Letter.Choice(Letter.Action.SEAL_PEACE, true), new Letter.Choice(Letter.Action.REFUSE, false)),
                    day, day + 3, 0).withPeace(w.id(), offer.name()));
        }
    }

    // ------------------------------------------------------------------ paix

    /** La réponse prévue de l'IA adverse à chaque paix possible. */
    static Map<WarRules.Peace, WarRules.Verdict> peaceVerdicts(MinecraftServer server, War w, int mySide, long day) {
        Map<WarRules.Peace, WarRules.Verdict> out = new EnumMap<>(WarRules.Peace.class);
        UUID enemy = mySide > 0 ? w.defender() : w.attacker();
        if (ai(server, enemy).isEmpty()) return out;
        int aiScore = -mySide * score(server, w);
        int exhaustion = mySide > 0 ? w.defenderExhaustion() : w.attackerExhaustion();
        for (WarRules.Peace p : WarRules.Peace.values()) out.put(p, WarRules.aiAnswer(p, aiScore, exhaustion, day - w.startDay()));
        return out;
    }

    public static CouncilActions.Outcome proposePeace(ServerPlayer player, UUID warId, WarRules.Peace term) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        Optional<War> war = data(server).wars().get(warId);
        if (found.isEmpty() || war.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.war.gone");
        Realm me = found.get();
        War w = war.get();
        int mySide = w.side(me.id());
        boolean leader = me.id().equals(w.attacker()) || me.id().equals(w.defender());
        if (mySide == 0 || !leader) return CouncilActions.Outcome.no("crusadecraft.war.not_leader");
        long today = RealmService.currentDay(server);
        UUID enemy = mySide > 0 ? w.defender() : w.attacker();
        Optional<AiRealm> ai = ai(server, enemy);
        if (term == WarRules.Peace.SURRENDER) {
            Optional<Component> seal = SealItem.problem(player, me);
            if (seal.isPresent()) return new CouncilActions.Outcome(false, seal.get());
            endWar(server, w, -mySide);
            return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.war.surrendered", name(server, enemy)));
        }
        if (ai.isPresent()) {
            WarRules.Verdict v = peaceVerdicts(server, w, mySide, today).get(term);
            Component realm = AiNames.realmThe(ai.get().rank(), ai.get().place());
            Component sender = Component.translatable("crusadecraft.letter.sender", ai.get().ruler().name(),
                    Component.translatable(ai.get().rank().holderKey(ai.get().ruler().female())), AiNames.realm(ai.get().rank(), ai.get().place()));
            if (v != null && v.accepted()) {
                // Il accepte : la paix proposée est scellée de votre sceau.
                WarRules.Peace theirs = term == WarRules.Peace.VICTORY ? WarRules.Peace.SURRENDER : term;
                DiplomacyService.post(server, me, new Letter(UUID.randomUUID(), enemy, true, me.id(), sender,
                        Component.translatable("crusadecraft.letter.peace_accept.subject"),
                        Component.translatable("crusadecraft.letter.peace_accept.body", realm, Component.translatable(term.key())),
                        List.of(new Letter.Choice(Letter.Action.SEAL_PEACE, true), new Letter.Choice(Letter.Action.REFUSE, false)),
                        today, today + 3, 0).withPeace(w.id(), theirs.name()));
            } else {
                DiplomacyService.post(server, me, new Letter(UUID.randomUUID(), enemy, true, me.id(), sender,
                        Component.translatable("crusadecraft.letter.peace_refuse.subject"),
                        Component.translatable("crusadecraft.letter.peace_refuse.body", realm), List.of(), today, today + 7, 0));
            }
            return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.sent", name(server, enemy)));
        }
        Optional<Realm> other = player(server, enemy);
        if (other.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.war.gone");
        DiplomacyService.post(server, other.get(), new Letter(UUID.randomUUID(), me.id(), false, other.get().id(),
                Component.translatable("crusadecraft.letter.sender", me.rulerName(), Component.translatable(me.rank().holderKey(me.feminineTitle())), me.name()),
                Component.translatable("crusadecraft.letter.peace_player.subject"),
                Component.translatable("crusadecraft.letter.peace_player.body", me.name(), Component.translatable(term.key())),
                List.of(new Letter.Choice(Letter.Action.SEAL_PEACE, true), new Letter.Choice(Letter.Action.REFUSE, false)),
                today, today + 5, 0).withPeace(w.id(), term.name()));
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.sent", other.get().name()));
    }

    /** Une lettre de paix scellée : ses termes sont ceux de son auteur. */
    public static CouncilActions.Outcome sealPeace(MinecraftServer server, Realm realm, Letter l) {
        Optional<War> war = l.war() == null ? Optional.empty() : data(server).wars().get(l.war());
        if (war.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.war.gone");
        WarRules.Peace term;
        try {
            term = WarRules.Peace.valueOf(l.peace());
        } catch (IllegalArgumentException e) {
            return CouncilActions.Outcome.no("crusadecraft.letter.gone");
        }
        War w = war.get();
        int authorSide = w.side(l.from());
        if (authorSide == 0) return CouncilActions.Outcome.no("crusadecraft.war.gone");
        int winner = term == WarRules.Peace.WHITE ? 0 : term == WarRules.Peace.VICTORY ? authorSide : -authorSide;
        endWar(server, w, winner);
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.war.peace_sealed", Component.translatable(term.key())));
    }

    /**
     * La fin d'une guerre. {@code winner} : +1 l'attaquant l'emporte, −1 le défenseur, 0 paix blanche.
     * Trêve, prestige, but de guerre, réparations ; les combats en jeu cessent et les dégâts sont réparés.
     */
    public static void endWar(MinecraftServer server, War w, int winner) {
        long today = RealmService.currentDay(server);
        CrusadeSavedData data = data(server);
        Relation rel = data.diplomacy().between(w.attacker(), w.defender());
        rel.setWar(false);
        rel.setHostile(false);
        rel.setTruceUntil(today + CrusadeConfig.SERVER.truceDays.get());
        UUID win = winner > 0 ? w.attacker() : w.defender();
        UUID lose = winner > 0 ? w.defender() : w.attacker();
        Component outcome;
        if (winner == 0) {
            outcome = Component.translatable("crusadecraft.war.result.white");
        } else {
            int prestige = WarRules.victoryPrestige(w.cb());
            addPrestige(server, win, prestige);
            addPrestige(server, lose, -prestige);
            if (winner > 0) {
                switch (w.cb()) {
                    case CLAIM, DEJURE, CONQUEST -> province(server, w.targetProvince()).ifPresent(p -> {
                        p.setHolder(w.attacker());
                        player(server, w.attacker()).ifPresent(r -> r.claims().remove(p.id()));
                    });
                    case VASSALIZE -> data.diplomacy().between(w.defender(), w.attacker()).makeVassal(w.attacker(), 25, 25);
                    case HUMILIATE -> transferGold(server, w.defender(), w.attacker(), 0.25);
                }
            } else {
                // L'attaquant vaincu paie réparation.
                transferGold(server, w.attacker(), w.defender(), 0.2);
            }
            outcome = Component.translatable("crusadecraft.war.result.victory", name(server, win));
        }
        w.log(today, "crusadecraft.war.log.peace", 0, winner == 0 ? "WHITE" : name(server, win).getString());
        hostileRanks(server, w, false);
        data.wars().damage().release(w.id());
        data.wars().remove(w.id());
        WarFieldService.onWarEnded(server, w.id());
        data.setDirty();
        notifyBoth(server, w, ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.war.peace.title"), outcome);
        CrusadeCraft.LOGGER.info("CrusadeCraft : fin de la guerre {} ({})", w.id(), winner);
    }

    private static void addPrestige(MinecraftServer server, UUID id, int amount) {
        ai(server, id).ifPresent(a -> a.setPrestige(Math.max(0, a.prestige() + amount)));
        player(server, id).ifPresent(r -> r.setPrestige(Math.max(0, r.prestige() + amount)));
    }

    private static void transferGold(MinecraftServer server, UUID from, UUID to, double share) {
        long amount = 0;
        Optional<AiRealm> a = ai(server, from);
        Optional<Realm> p = player(server, from);
        if (a.isPresent()) {
            amount = Math.round(Math.max(0, a.get().gold()) * share);
            a.get().setGold(a.get().gold() - amount);
        } else if (p.isPresent()) {
            amount = TreasuryService.charge(server, p.get(), LedgerCategory.PILLAGE, Math.round(Math.max(0, p.get().gold()) * share));
        }
        long paid = amount;
        ai(server, to).ifPresent(x -> x.setGold(x.gold() + paid));
        player(server, to).ifPresent(r -> TreasuryService.credit(server, r, LedgerCategory.BUTIN, paid));
    }

    // ------------------------------------------------------------------ zones de guerre

    /** La guerre dont ce lieu est un théâtre : le territoire d'un royaume en guerre. */
    public static Optional<War> warZone(MinecraftServer server, ResourceLocation dim, BlockPos pos) {
        for (War w : data(server).wars().all()) {
            for (UUID id : participants(w)) if (inTerritory(server, id, dim, pos)) return Optional.of(w);
        }
        return Optional.empty();
    }

    /** Ce lieu est-il en territoire ennemi pour ce royaume ? */
    public static boolean enemyTerritory(MinecraftServer server, UUID realm, ResourceLocation dim, BlockPos pos) {
        for (War w : data(server).wars().all()) {
            if (!w.involves(realm)) continue;
            for (UUID id : participants(w)) if (w.opposes(realm, id) && inTerritory(server, id, dim, pos)) return true;
        }
        return false;
    }

    static List<UUID> participants(War w) {
        List<UUID> out = new ArrayList<>();
        out.add(w.attacker());
        out.add(w.defender());
        out.addAll(w.attackerAllies());
        out.addAll(w.defenderAllies());
        return out;
    }

    static boolean inTerritory(MinecraftServer server, UUID id, ResourceLocation dim, BlockPos pos) {
        int radius = CrusadeConfig.SERVER.territoryRadius.get();
        for (AiProvince p : provincesOf(server, id)) {
            if (p.dimension().equals(dim) && p.distanceSqr(pos.getX() + 0.5, pos.getZ() + 0.5) <= (double) radius * radius) return true;
        }
        Optional<Realm> r = player(server, id);
        if (r.isPresent()) {
            int colonyRadius = CrusadeConfig.SERVER.colonyRadius.get();
            for (ColonyRef c : r.get().allColonies()) {
                if (!c.dimension().equals(dim)) continue;
                Optional<BlockPos> center = MineColoniesBridge.center(c);
                if (center.isPresent() && center.get().distSqr(pos) <= (double) colonyRadius * colonyRadius) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ messages

    static void notify(MinecraftServer server, UUID realmId, ToastPacket.Kind kind, Component title, Component body) {
        player(server, realmId).ifPresent(r -> {
            ServerPlayer ruler = server.getPlayerList().getPlayer(r.ruler());
            if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(kind, title, body));
        });
    }

    static void notifyBoth(MinecraftServer server, War w, ToastPacket.Kind kind, Component title, Component body) {
        for (UUID id : participants(w)) notify(server, id, kind, title, body);
    }

    // ------------------------------------------------------------------ vue

    public static WarView view(ServerPlayer player) {
        MinecraftServer server = player.server;
        Realm me = RealmService.realmOf(server, player.getUUID()).orElseThrow();
        long today = RealmService.currentDay(server);
        List<WarView.WarLine> lines = new ArrayList<>();
        for (War w : data(server).wars().involving(me.id())) {
            int side = w.side(me.id());
            List<String> aa = new ArrayList<>(), da = new ArrayList<>();
            for (UUID x : w.attackerAllies()) aa.add(name(server, x).getString());
            for (UUID x : w.defenderAllies()) da.add(name(server, x).getString());
            List<String> occupied = new ArrayList<>();
            for (int id : w.occupied()) province(server, id).ifPresent(p -> occupied.add(p.name()));
            for (String c : w.occupiedColonies()) occupied.add(c);
            String target = w.targetProvince() >= 0 ? province(server, w.targetProvince()).map(AiProvince::name).orElse("?") : "";
            boolean leader = me.id().equals(w.attacker()) || me.id().equals(w.defender());
            lines.add(new WarView.WarLine(w.id(), party(server, w.attacker()), party(server, w.defender()), aa, da, w.cb(), target, side,
                    leader, score(server, w), sideStrength(server, w, side), sideStrength(server, w, -side),
                    side > 0 ? w.attackerExhaustion() : w.defenderExhaustion(), side > 0 ? w.defenderExhaustion() : w.attackerExhaustion(),
                    occupied, today - w.startDay(), new ArrayList<>(w.events()), peaceVerdicts(server, w, side, today)));
        }
        List<WarView.Target> targets = new ArrayList<>();
        for (AiRealm a : data(server).world().realms()) {
            if (a.discoveredBy(player.getUUID()) && !a.landless()) targets.add(target(server, me, a.id()));
        }
        for (Realm r : data(server).realms()) if (!r.id().equals(me.id())) targets.add(target(server, me, r.id()));
        Optional<Component> seal = SealItem.problem(player, me);
        return new WarView(men(me), maxMen(me), me.armyRaised(), (int) Math.ceil(men(me) / 10.0), me.gold(), me.prestige(),
                seal.isEmpty(), seal.map(Component::getString).orElse(""), lines, targets);
    }
}
