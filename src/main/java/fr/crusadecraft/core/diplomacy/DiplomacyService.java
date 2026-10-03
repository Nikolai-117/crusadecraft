package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.council.CouncilActions;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * La diplomatie au quotidien : propositions et réponses par pigeon, cadeaux et insultes, mission du chancelier,
 * courrier, effets de chaque jour (tributs, impôts des vassaux, commerce), lettres spontanées des royaumes IA.
 */
public final class DiplomacyService {
    /** Lettres déjà annoncées par une notification (état de session). */
    private static final Set<UUID> ANNOUNCED = new HashSet<>();
    public static final int[] GIFTS = {25, 50, 100};

    private DiplomacyService() {}

    public static void onServerStopped() {
        ANNOUNCED.clear();
    }

    // ------------------------------------------------------------------ qui est qui

    private static Diplomacy diplomacy(MinecraftServer server) {
        return CrusadeSavedData.get(server).diplomacy();
    }

    private static Optional<AiRealm> ai(MinecraftServer server, UUID id) {
        return CrusadeSavedData.get(server).world().realm(id);
    }

    /** Le nom d'un royaume, joueur ou IA. */
    public static Component name(MinecraftServer server, UUID id) {
        Optional<AiRealm> a = ai(server, id);
        if (a.isPresent()) return AiNames.realmThe(a.get().rank(), a.get().place());
        return CrusadeSavedData.get(server).realm(id).map(r -> (Component) Component.literal(r.name())).orElse(Component.literal("?"));
    }

    /** « Godefroy, Roi d'Ambrevent » : la signature d'une lettre. */
    private static Component sender(AiRealm r) {
        return Component.translatable("crusadecraft.letter.sender", r.ruler().name(),
                Component.translatable(r.rank().holderKey(r.ruler().female())), AiNames.realm(r.rank(), r.place()));
    }

    private static Component sender(Realm r) {
        return Component.translatable("crusadecraft.letter.sender", r.rulerName(),
                Component.translatable(r.rank().holderKey(r.feminineTitle())), r.name());
    }

    // ------------------------------------------------------------------ forces et opinion

    /** Force d'un royaume de joueur : sa population, et ses gardes qui comptent triple. */
    public static int strength(Realm r) {
        int s = 0;
        for (ColonyRef colony : r.allColonies()) {
            ColonyReport report = MineColoniesBridge.report(colony);
            s += report.citizens() + report.guards() * 3;
        }
        return Math.max(1, s);
    }

    public static int strength(AiRealm r) {
        return Math.max(1, (int) Math.round(r.population() * r.personality().levyFactor()));
    }

    private static int chancellorDiplomacy(MinecraftServer server, Realm realm) {
        CitizenRef chancellor = realm.council().get(Post.CHANCELIER);
        if (chancellor == null) return 0;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(chancellor);
        if (info.isEmpty()) return 0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return 0;
        return CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.DIPLOMATIE.ordinal()];
    }

    private static boolean sameDejure(MinecraftServer server, AiRealm ai, Realm player) {
        WorldState world = CrusadeSavedData.get(server).world();
        AiProvince capital = ai.capital();
        Optional<net.minecraft.core.BlockPos> center = MineColoniesBridge.center(player.capital());
        if (capital == null || center.isEmpty() || world.dejure().isEmpty()) return false;
        return capital.dejureKingdom() == nearestDejure(world, center.get().getX(), center.get().getZ());
    }

    private static int nearestDejure(WorldState world, int x, int z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < world.dejure().size(); i++) {
            double d = Math.hypot(world.dejure().get(i).centerX() - x, world.dejure().get(i).centerZ() - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    public static DiplomacyRules.Context context(MinecraftServer server, AiRealm ai, Realm player) {
        return new DiplomacyRules.Context(ai.personality(), ai.ruler().traits(), ai.rank(), player.rank(), player.prestige(),
                player.dread(), sameDejure(server, ai, player), chancellorDiplomacy(server, player), strength(player), strength(ai),
                player.threat());
    }

    public static List<Opinion.Line> opinionLines(MinecraftServer server, AiRealm ai, Realm player) {
        Relation rel = diplomacy(server).between(ai.id(), player.id());
        return DiplomacyRules.opinion(rel, ai.id(), player.id(), context(server, ai, player), RealmService.currentDay(server));
    }

    public static int opinion(MinecraftServer server, AiRealm ai, Realm player) {
        return Opinion.total(opinionLines(server, ai, player));
    }

    // ------------------------------------------------------------------ courrier

    private static long arrival(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        return now + CrusadeConfig.SERVER.letterDelaySeconds.get() * 20L;
    }

    /** Envoie une lettre ; elle arrive au Colombier après le vol du pigeon. */
    public static void post(MinecraftServer server, Realm to, Letter letter) {
        to.mail().add(letter);
        CrusadeSavedData.get(server).setDirty();
    }

    private static Letter letter(MinecraftServer server, UUID from, boolean fromAi, Realm to, Component sender, Component subject,
                                 Component body, List<Letter.Choice> choices, int validDays) {
        long today = RealmService.currentDay(server);
        return new Letter(UUID.randomUUID(), from, fromAi, to.id(), sender, subject, body, choices, today,
                today + Math.max(1, validDays), arrival(server));
    }

    private static List<Letter.Choice> sealOrRefuse(Letter.Action seal) {
        return List.of(new Letter.Choice(seal, true), new Letter.Choice(Letter.Action.REFUSE, false));
    }

    /** Annonce l'arrivée des pigeons et retire les lettres échues. Appelé toutes les deux secondes. */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        long now = overworld.getGameTime();
        long today = RealmService.currentDay(server);
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            int fresh = 0;
            for (Iterator<Letter> it = realm.mail().iterator(); it.hasNext(); ) {
                Letter l = it.next();
                if (l.needsAnswer() && l.expired(today)) {
                    it.remove();
                    ANNOUNCED.remove(l.id());
                    CrusadeSavedData.get(server).setDirty();
                    continue;
                }
                if (l.arrived(now) && !l.delivered() && ANNOUNCED.add(l.id())) fresh++;
            }
            if (fresh > 0) {
                ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
                if (ruler != null) {
                    CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.LETTER,
                            Component.translatable("crusadecraft.toast.mail.title"),
                            Component.translatable(fresh == 1 ? "crusadecraft.toast.mail.one" : "crusadecraft.toast.mail.many", fresh)));
                }
            }
        }
    }

    /** Les lettres arrivées et pas encore prises. */
    public static int arrived(MinecraftServer server, Realm realm) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        int n = 0;
        for (Letter l : realm.mail()) if (l.arrived(now) && !l.delivered()) n++;
        return n;
    }

    public static int awaiting(MinecraftServer server, Realm realm) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        int n = 0;
        for (Letter l : realm.mail()) if (!l.arrived(now)) n++;
        return n;
    }

    /** Remet au souverain les lettres arrivées, en objets à lire. */
    public static CouncilActions.Outcome takeMail(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        int given = 0;
        for (Iterator<Letter> it = realm.mail().iterator(); it.hasNext(); ) {
            Letter l = it.next();
            if (!l.arrived(now) || l.delivered()) continue;
            l.markDelivered();
            give(player, fr.crusadecraft.item.LetterItem.create(l));
            if (l.claimProvince() >= 0) give(player, fr.crusadecraft.item.ClaimScrollItem.create(server, realm, l.claimProvince()));
            if (l.amount() > 0 && !l.needsAnswer() && l.treaty() == null) {
                // Un présent joint à la lettre.
                TreasuryService.credit(server, realm, LedgerCategory.TRIBUTS, l.amount());
            }
            if (!l.needsAnswer()) it.remove();
            given++;
        }
        CrusadeSavedData.get(server).setDirty();
        if (given == 0) return CouncilActions.Outcome.no("crusadecraft.mail.empty");
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.mail.taken", given));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ------------------------------------------------------------------ propositions

    private static Optional<Realm> ruler(ServerPlayer player) {
        return RealmService.realmOf(player.server, player.getUUID()).filter(r -> r.state().isActive());
    }

    /** Propose un traité, un hommage ou exige un tribut. La réponse arrive par pigeon. */
    public static CouncilActions.Outcome propose(ServerPlayer player, UUID target, DiplomacyRules.Proposal proposal, int tax, int levy) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        long today = RealmService.currentDay(server);
        Optional<AiRealm> aiTarget = ai(server, target);
        if (aiTarget.isPresent()) {
            AiRealm ai = aiTarget.get();
            if (!ai.discoveredBy(player.getUUID())) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
            Relation rel = diplomacy(server).between(ai.id(), realm.id());
            DiplomacyRules.Verdict v = DiplomacyRules.evaluate(proposal, opinion(server, ai, realm), context(server, ai, realm),
                    rel, ai.id(), realm.id(), tax, levy, today);
            if (v.blockedKey() != null) return CouncilActions.Outcome.no(v.blockedKey());
            answerFromAi(server, realm, ai, rel, proposal, v.accepted(), tax, levy);
            CrusadeSavedData.get(server).setDirty();
            return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.sent", AiNames.realmThe(ai.rank(), ai.place())));
        }
        Optional<Realm> playerTarget = CrusadeSavedData.get(server).realm(target);
        if (playerTarget.isEmpty() || playerTarget.get().id().equals(realm.id())) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        return proposeToPlayer(server, realm, playerTarget.get(), proposal, tax, levy);
    }

    private static void answerFromAi(MinecraftServer server, Realm realm, AiRealm ai, Relation rel, DiplomacyRules.Proposal p,
                                     boolean accepted, int tax, int levy) {
        long today = RealmService.currentDay(server);
        Component who = sender(ai);
        Component realmName = AiNames.realmThe(ai.rank(), ai.place());
        if (!accepted) {
            rel.addOpinion(ai.id(), OpinionModifier.of(OpinionReason.REFUS_DIPLOMATIQUE, today, p == DiplomacyRules.Proposal.TRIBUT ? 2 : 1));
            if (p == DiplomacyRules.Proposal.TRIBUT) rel.setHostile(true);
            post(server, realm, letter(server, ai.id(), true, realm, who,
                    Component.translatable("crusadecraft.letter.refuse.subject", Component.translatable(p.key())),
                    Component.translatable("crusadecraft.letter.refuse.body." + p.name().toLowerCase(java.util.Locale.ROOT), realmName),
                    List.of(), 7));
            return;
        }
        switch (p) {
            case TRIBUT -> {
                int perDay = DiplomacyRules.tributeFor(ai.lastIncome());
                rel.setTribute(ai.id(), perDay, today + 20);
                rel.addOpinion(ai.id(), OpinionModifier.of(OpinionReason.EXIGENCE, today, 1));
                post(server, realm, letter(server, ai.id(), true, realm, who, Component.translatable("crusadecraft.letter.tribute.subject"),
                        Component.translatable("crusadecraft.letter.tribute.body", realmName, perDay, 20), List.of(), 7));
            }
            case VASSALISATION -> post(server, realm, letter(server, ai.id(), true, realm, who,
                    Component.translatable("crusadecraft.letter.homage.subject"),
                    Component.translatable("crusadecraft.letter.homage.body", realmName, Relation.clampRate(tax), Relation.clampRate(levy)),
                    sealOrRefuse(Letter.Action.SEAL_VASSALAGE), 3).withContract(tax, levy));
            default -> post(server, realm, letter(server, ai.id(), true, realm, who,
                    Component.translatable("crusadecraft.letter.accept.subject", Component.translatable(p.treaty().key())),
                    Component.translatable("crusadecraft.letter.accept.body", realmName, Component.translatable(p.treaty().key())),
                    sealOrRefuse(Letter.Action.SEAL_TREATY), 3).withTreaty(p.treaty()));
        }
    }

    private static CouncilActions.Outcome proposeToPlayer(MinecraftServer server, Realm from, Realm to, DiplomacyRules.Proposal p,
                                                          int tax, int levy) {
        if (p == DiplomacyRules.Proposal.TRIBUT) return CouncilActions.Outcome.no("crusadecraft.diplo.block.players_tribute");
        Relation rel = diplomacy(server).between(from.id(), to.id());
        long today = RealmService.currentDay(server);
        if (p.treaty() != null && rel.has(p.treaty(), today)) return CouncilActions.Outcome.no("crusadecraft.diplo.block.already");
        Letter l;
        if (p == DiplomacyRules.Proposal.VASSALISATION) {
            if (rel.suzerain() != null) return CouncilActions.Outcome.no("crusadecraft.diplo.block.already");
            l = letter(server, from.id(), false, to, sender(from), Component.translatable("crusadecraft.letter.player_homage.subject"),
                    Component.translatable("crusadecraft.letter.player_homage.body", from.name(), Relation.clampRate(tax), Relation.clampRate(levy)),
                    sealOrRefuse(Letter.Action.SEAL_HOMAGE), 5).withContract(tax, levy);
        } else {
            l = letter(server, from.id(), false, to, sender(from),
                    Component.translatable("crusadecraft.letter.player_treaty.subject", Component.translatable(p.treaty().key())),
                    Component.translatable("crusadecraft.letter.player_treaty.body", from.name(), Component.translatable(p.treaty().key())),
                    sealOrRefuse(Letter.Action.SEAL_TREATY), 5).withTreaty(p.treaty());
        }
        post(server, to, l);
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.sent", to.name()));
    }

    // ------------------------------------------------------------------ réponses aux lettres

    public static CouncilActions.Outcome answer(ServerPlayer player, UUID letterId, int choiceIndex) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        long today = RealmService.currentDay(server);
        Letter letter = null;
        for (Letter l : realm.mail()) if (l.id().equals(letterId)) letter = l;
        if (letter == null || !letter.needsAnswer()) return CouncilActions.Outcome.no("crusadecraft.letter.gone");
        if (letter.expired(today)) {
            realm.mail().remove(letter);
            return CouncilActions.Outcome.no("crusadecraft.letter.expired");
        }
        if (choiceIndex < 0 || choiceIndex >= letter.choices().size()) return CouncilActions.Outcome.no("crusadecraft.letter.gone");
        Letter.Choice choice = letter.choices().get(choiceIndex);
        if (choice.needsSeal()) {
            Optional<Component> problem = SealItem.problem(player, realm);
            if (problem.isPresent()) return new CouncilActions.Outcome(false, problem.get());
        }
        CouncilActions.Outcome out = apply(server, realm, letter, choice.action(), today);
        if (out.success()) {
            realm.mail().remove(letter);
            fr.crusadecraft.item.LetterItem.markAnswered(player, letterId, choiceIndex);
            CrusadeSavedData.get(server).setDirty();
        }
        return out;
    }

    private static CouncilActions.Outcome apply(MinecraftServer server, Realm realm, Letter l, Letter.Action action, long today) {
        Diplomacy d = diplomacy(server);
        Relation rel = d.between(realm.id(), l.from());
        Component other = name(server, l.from());
        switch (action) {
            case SEAL_TREATY -> {
                if (l.treaty() == null) return CouncilActions.Outcome.no("crusadecraft.letter.gone");
                rel.sign(l.treaty(), today);
                if (l.fromAi()) rel.addOpinion(l.from(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
                else notifyPlayer(server, l.from(), realm, "crusadecraft.letter.sealed.subject", "crusadecraft.letter.sealed.body",
                        Component.translatable(l.treaty().key()));
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.treaty_sealed", Component.translatable(l.treaty().key()), other));
            }
            case SEAL_VASSALAGE -> {
                // Un royaume IA vous prête hommage.
                if (d.suzerainOf(l.from()).isPresent()) return CouncilActions.Outcome.no("crusadecraft.diplo.block.already");
                rel.makeVassal(realm.id(), l.tax(), l.levy());
                rel.addOpinion(l.from(), OpinionModifier.of(OpinionReason.HOMMAGE, today, 1));
                realm.setPrestige(realm.prestige() + 10);
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.vassal_sealed", other));
            }
            case SEAL_HOMAGE -> {
                // Un autre joueur vous propose d'être son vassal : vous acceptez.
                if (d.suzerainOf(realm.id()).isPresent()) return CouncilActions.Outcome.no("crusadecraft.diplo.block.already");
                if (d.suzerainOf(l.from()).map(realm.id()::equals).orElse(false)) return CouncilActions.Outcome.no("crusadecraft.diplo.block.vassal");
                rel.makeVassal(l.from(), l.tax(), l.levy());
                notifyPlayer(server, l.from(), realm, "crusadecraft.letter.homage_sealed.subject", "crusadecraft.letter.homage_sealed.body",
                        Component.literal(realm.name()));
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.homage_sealed", other));
            }
            case SEAL_PEACE -> {
                return fr.crusadecraft.core.war.WarService.sealPeace(server, realm, l);
            }
            case PAY_TRIBUTE -> {
                rel.setTribute(realm.id(), l.amount(), today + 20);
                if (l.fromAi()) ai(server, l.from()).ifPresent(a -> rel.setHostile(false));
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.tribute_paid", other, l.amount()));
            }
            case REFUSE -> {
                if (l.fromAi()) {
                    boolean demand = l.amount() > 0;
                    rel.addOpinion(l.from(), OpinionModifier.of(OpinionReason.REFUS_DIPLOMATIQUE, today, demand ? 2 : 1));
                    if (demand) rel.setHostile(true);
                } else {
                    notifyPlayer(server, l.from(), realm, "crusadecraft.letter.declined.subject", "crusadecraft.letter.declined.body",
                            Component.literal(realm.name()));
                }
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.refused", other));
            }
            default -> {
                return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.letter.read"));
            }
        }
    }

    private static void notifyPlayer(MinecraftServer server, UUID to, Realm from, String subjectKey, String bodyKey, Component arg) {
        CrusadeSavedData.get(server).realm(to).ifPresent(target -> post(server, target, letter(server, from.id(), false, target,
                sender(from), Component.translatable(subjectKey), Component.translatable(bodyKey, from.name(), arg), List.of(), 7)));
    }

    // ------------------------------------------------------------------ cadeaux, insultes, chancelier, ruptures

    public static CouncilActions.Outcome gift(ServerPlayer player, UUID target, int gold) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (gold <= 0) return CouncilActions.Outcome.no("crusadecraft.diplo.block.already");
        Optional<AiRealm> ai = ai(server, target);
        Optional<Realm> other = CrusadeSavedData.get(server).realm(target);
        if (ai.isEmpty() && (other.isEmpty() || other.get().id().equals(realm.id()))) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        if (!TreasuryService.spend(server, realm, LedgerCategory.DIPLOMATIE, gold)) {
            return CouncilActions.Outcome.no("crusadecraft.request.no_gold", gold);
        }
        long today = RealmService.currentDay(server);
        Relation rel = diplomacy(server).between(realm.id(), target);
        if (ai.isPresent()) {
            AiRealm a = ai.get();
            int value = DiplomacyRules.giftOpinion(gold, a.ruler().traits());
            rel.addOpinion(a.id(), OpinionModifier.of(OpinionReason.CADEAU, today, value / 10.0));
            a.setGold(a.gold() + gold);
            post(server, realm, letter(server, a.id(), true, realm, sender(a), Component.translatable("crusadecraft.letter.thanks.subject"),
                    Component.translatable("crusadecraft.letter.thanks.body", AiNames.realmThe(a.rank(), a.place()), gold), List.of(), 7));
        } else {
            Realm o = other.get();
            TreasuryService.credit(server, o, LedgerCategory.TRIBUTS, gold);
            post(server, o, letter(server, realm.id(), false, o, sender(realm), Component.translatable("crusadecraft.letter.gift.subject"),
                    Component.translatable("crusadecraft.letter.gift.body", realm.name(), gold), List.of(), 7));
        }
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.gift_sent", gold, name(server, target)));
    }

    public static CouncilActions.Outcome insult(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<AiRealm> ai = ai(server, target);
        if (ai.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.diplo.block.players_insult");
        long today = RealmService.currentDay(server);
        Relation rel = diplomacy(server).between(realm.id(), target);
        rel.addOpinion(target, OpinionModifier.of(OpinionReason.INSULTE, today, 1));
        realm.setDread(realm.dread() + 2);
        realm.setPrestige(realm.prestige() + 2);
        if (opinion(server, ai.get(), realm) < -40) rel.setHostile(true);
        AiRealm a = ai.get();
        post(server, realm, letter(server, a.id(), true, realm, sender(a), Component.translatable("crusadecraft.letter.outrage.subject"),
                Component.translatable("crusadecraft.letter.outrage.body", AiNames.realmThe(a.rank(), a.place())), List.of(), 7));
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.insult_sent", name(server, target)));
    }

    /** Charge le chancelier d'améliorer les relations avec ce royaume (tâche « Améliorer les relations »). */
    public static CouncilActions.Outcome sendChancellor(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (realm.council().get(Post.CHANCELIER) == null) return CouncilActions.Outcome.no("crusadecraft.diplo.no_chancellor");
        if (ai(server, target).isEmpty()) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        realm.court().setTask(Post.CHANCELIER, fr.crusadecraft.core.council.CouncilTask.AMELIORER_RELATIONS);
        realm.court().setTaskTarget(Post.CHANCELIER, target.toString());
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.chancellor_sent", name(server, target)));
    }

    /** Rompt un traité : l'autre s'en souviendra, et le prestige en pâtit. */
    public static CouncilActions.Outcome breakTreaty(ServerPlayer player, UUID target, Treaty treaty) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        long today = RealmService.currentDay(server);
        Optional<Relation> rel = diplomacy(server).find(realm.id(), target);
        if (rel.isEmpty() || !rel.get().has(treaty, today)) return CouncilActions.Outcome.no("crusadecraft.diplo.no_treaty");
        rel.get().breakTreaty(treaty);
        rel.get().addOpinion(target, OpinionModifier.of(OpinionReason.INSULTE, today, 0.7));
        realm.setPrestige(Math.max(0, realm.prestige() - 15));
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.treaty_broken", Component.translatable(treaty.key()), name(server, target)));
    }

    // ------------------------------------------------------------------ chaque jour

    /** Le passage d'un jour pour un royaume de joueur : traités échus, tributs, impôts des vassaux, commerce. */
    public static void onNewDay(MinecraftServer server, Realm realm, long day) {
        Diplomacy d = diplomacy(server);
        for (Relation rel : new ArrayList<>(d.all())) {
            if (!rel.involves(realm.id())) continue;
            UUID otherId = rel.other(realm.id());
            Optional<AiRealm> ai = ai(server, otherId);
            Optional<Realm> otherPlayer = CrusadeSavedData.get(server).realm(otherId);
            if (ai.isEmpty() && otherPlayer.isEmpty()) continue;

            for (Treaty t : rel.expire(day)) {
                Component who = name(server, otherId);
                post(server, realm, letter(server, otherId, ai.isPresent(), realm, who, Component.translatable("crusadecraft.letter.expired.subject"),
                        Component.translatable("crusadecraft.letter.expired.body", Component.translatable(t.key()), who), List.of(), 7));
            }
            // Tributs
            if (rel.paysTribute(otherId, day) && ai.isPresent()) {
                AiRealm a = ai.get();
                int paid = (int) Math.min(rel.tributePerDay(), Math.max(0, a.gold()));
                a.setGold(a.gold() - paid);
                TreasuryService.credit(server, realm, LedgerCategory.TRIBUTS, paid);
            } else if (rel.paysTribute(realm.id(), day)) {
                long paid = TreasuryService.charge(server, realm, LedgerCategory.DIPLOMATIE, rel.tributePerDay());
                ai.ifPresent(a -> a.setGold(a.gold() + paid));
                otherPlayer.ifPresent(o -> TreasuryService.credit(server, o, LedgerCategory.TRIBUTS, paid));
            }
            // Impôts des vassaux IA
            if (ai.isPresent() && rel.isVassalOf(otherId, realm.id())) {
                AiRealm a = ai.get();
                long share = Math.min(Math.max(0, a.gold()), Math.round(a.lastIncome() * rel.vassalTax() / 100.0));
                a.setGold(a.gold() - share);
                TreasuryService.credit(server, realm, LedgerCategory.VASSAUX, share);
            }
            // Un joueur vassal verse sa part des impôts du jour à son suzerain.
            if (otherPlayer.isPresent() && rel.isVassalOf(realm.id(), otherId)) {
                long taxes = realm.ledger().peek(day).amount(LedgerCategory.IMPOTS);
                long share = TreasuryService.charge(server, realm, LedgerCategory.VASSAUX, Math.round(taxes * rel.vassalTax() / 100.0));
                TreasuryService.credit(server, otherPlayer.get(), LedgerCategory.VASSAUX, share);
            }
            // Commerce
            if (rel.has(Treaty.COMMERCE, day)) {
                long trade = 2 + ai.map(a -> a.capital() == null ? 0L : (long) a.capital().development() / 20).orElse(0L);
                TreasuryService.credit(server, realm, LedgerCategory.COMMERCE, trade);
            }
            rel.prune(day);
        }
        CrusadeSavedData.get(server).setDirty();
    }

    /** De temps en temps, les souverains IA écrivent : propositions, exigences, présents. */
    public static void aiLetters(MinecraftServer server) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        Random random = new Random(server.getTickCount() * 31L + today);
        for (Realm realm : data.realms()) {
            if (!realm.state().isActive() || realm.mail().size() >= CrusadeConfig.SERVER.mailboxMax.get()) continue;
            for (AiRealm ai : data.world().realms()) {
                if (!ai.discoveredBy(realm.ruler()) || realm.mail().size() >= CrusadeConfig.SERVER.mailboxMax.get()) continue;
                Relation rel = data.diplomacy().between(ai.id(), realm.id());
                if (rel.war() || rel.suzerain() != null) continue;
                if (rel.lastAiLetterDay() != Long.MIN_VALUE && today - rel.lastAiLetterDay() < 3) continue;
                if (random.nextInt(4) != 0) continue;
                Optional<Letter> l = aiInitiative(server, realm, ai, rel, today, random);
                if (l.isEmpty()) continue;
                rel.setLastAiLetterDay(today);
                post(server, realm, l.get());
            }
        }
    }

    private static Optional<Letter> aiInitiative(MinecraftServer server, Realm realm, AiRealm ai, Relation rel, long today, Random random) {
        int opinion = opinion(server, ai, realm);
        DiplomacyRules.Context c = context(server, ai, realm);
        Component who = sender(ai);
        Component realmName = AiNames.realmThe(ai.rank(), ai.place());
        if (opinion >= 40 && ai.gold() > 60 && random.nextBoolean()) {
            int gift = 10 + random.nextInt(21);
            ai.setGold(ai.gold() - gift);
            return Optional.of(letter(server, ai.id(), true, realm, who, Component.translatable("crusadecraft.letter.ai_gift.subject"),
                    Component.translatable("crusadecraft.letter.ai_gift.body", realmName, gift), List.of(), 7).withAmount(gift));
        }
        Treaty offer = null;
        if (opinion >= 45 && !rel.has(Treaty.ALLIANCE_DEFENSIVE, today) && !rel.has(Treaty.ALLIANCE, today)) offer = Treaty.ALLIANCE_DEFENSIVE;
        else if (ai.personality() == fr.crusadecraft.core.world.AiPersonality.MARCHAND && opinion >= 5 && !rel.has(Treaty.COMMERCE, today)) offer = Treaty.COMMERCE;
        else if (ai.personality() == fr.crusadecraft.core.world.AiPersonality.PACIFIQUE && opinion >= 0 && !rel.has(Treaty.NON_AGRESSION, today)) offer = Treaty.NON_AGRESSION;
        if (offer != null) {
            return Optional.of(letter(server, ai.id(), true, realm, who,
                    Component.translatable("crusadecraft.letter.ai_offer.subject", Component.translatable(offer.key())),
                    Component.translatable("crusadecraft.letter.ai_offer.body", realmName, Component.translatable(offer.key())),
                    sealOrRefuse(Letter.Action.SEAL_TREATY), 3).withTreaty(offer));
        }
        boolean aggressive = ai.personality() == fr.crusadecraft.core.world.AiPersonality.EXPANSIONNISTE
                || ai.personality() == fr.crusadecraft.core.world.AiPersonality.OPPORTUNISTE;
        if (aggressive && c.ratio() <= 0.6 && opinion < 10 && !rel.paysTribute(realm.id(), today)) {
            int perDay = Math.max(2, (int) Math.round(TreasuryService.view(server, realm).forecastIncome() * 0.1));
            return Optional.of(letter(server, ai.id(), true, realm, who, Component.translatable("crusadecraft.letter.ai_demand.subject"),
                    Component.translatable("crusadecraft.letter.ai_demand.body", realmName, perDay, 20),
                    List.of(new Letter.Choice(Letter.Action.PAY_TRIBUTE, false), new Letter.Choice(Letter.Action.REFUSE, false)), 3).withAmount(perDay));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ vue

    public static DiplomacyView view(ServerPlayer player) {
        MinecraftServer server = player.server;
        Realm realm = RealmService.realmOf(server, player.getUUID()).orElseThrow();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        List<DiplomacyView.Entry> entries = new ArrayList<>();
        int myStrength = strength(realm);
        for (AiRealm ai : data.world().realms()) {
            if (!ai.discoveredBy(player.getUUID())) continue;
            Relation rel = data.diplomacy().between(ai.id(), realm.id());
            DiplomacyRules.Context c = context(server, ai, realm);
            List<Opinion.Line> lines = DiplomacyRules.opinion(rel, ai.id(), realm.id(), c, today);
            int opinion = Opinion.total(lines);
            Map<DiplomacyRules.Proposal, DiplomacyRules.Verdict> verdicts = new EnumMap<>(DiplomacyRules.Proposal.class);
            for (DiplomacyRules.Proposal p : DiplomacyRules.Proposal.values()) {
                verdicts.put(p, DiplomacyRules.evaluate(p, opinion, c, rel, ai.id(), realm.id(), 20, 20, today));
            }
            entries.add(new DiplomacyView.Entry(ai.id(), true, ai.place(), ai.rank(), ai.color(), ai.ruler().name(), ai.ruler().female(),
                    ai.personality(), rel.status(realm.id(), today), opinion, lines, treatyDays(rel, today), rel.vassalTax(),
                    rel.vassalLevy(), rel.paysTribute(ai.id(), today), rel.paysTribute(realm.id(), today), rel.tributePerDay(),
                    myStrength / (double) strength(ai), "", verdicts));
        }
        for (Realm other : data.realms()) {
            if (other.id().equals(realm.id())) continue;
            Relation rel = data.diplomacy().between(other.id(), realm.id());
            String mcRank = MineColoniesBridge.rankName(realm.capital(), other.ruler());
            entries.add(new DiplomacyView.Entry(other.id(), false, other.name(), other.rank(), other.color(), other.rulerName(),
                    other.feminineTitle(), null, rel.status(realm.id(), today), 0, List.of(), treatyDays(rel, today), rel.vassalTax(),
                    rel.vassalLevy(), rel.paysTribute(other.id(), today), rel.paysTribute(realm.id(), today), rel.tributePerDay(),
                    myStrength / (double) strength(other), mcRank, Map.of()));
        }
        String target = realm.court().taskTarget(Post.CHANCELIER);
        UUID chancellorTarget = null;
        if (target != null && realm.court().task(Post.CHANCELIER) == fr.crusadecraft.core.council.CouncilTask.AMELIORER_RELATIONS) {
            try {
                chancellorTarget = UUID.fromString(target);
            } catch (IllegalArgumentException ignored) {
                // cible illisible
            }
        }
        Optional<Component> seal = SealItem.problem(player, realm);
        return new DiplomacyView(realm.gold(), realm.prestige(), arrived(server, realm), awaiting(server, realm), seal.isEmpty(),
                seal.map(Component::getString).orElse(""), chancellorTarget, realm.council().get(Post.CHANCELIER) != null, entries);
    }

    private static Map<Treaty, Long> treatyDays(Relation rel, long today) {
        Map<Treaty, Long> out = new EnumMap<>(Treaty.class);
        for (Map.Entry<Treaty, Long> e : rel.treaties().entrySet()) {
            if (e.getValue() <= today) continue;
            out.put(e.getKey(), e.getValue() == Long.MAX_VALUE ? -1L : e.getValue() - today);
        }
        return out;
    }
}
