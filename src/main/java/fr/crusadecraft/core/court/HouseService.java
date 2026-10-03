package fr.crusadecraft.core.court;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Family;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.intrigue.Chronicle;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.NameGenerator;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * La maison du souverain. On y fait entrer des citoyens ; leurs conjoints et leurs enfants, selon la vraie
 * généalogie MineColonies, y entrent d'eux-mêmes. On désigne un héritier, et l'on marie les membres libres aux
 * familles des souverains IA de la même foi : une alliance dynastique.
 */
public final class HouseService {
    /** Prestige qu'il en coûte de faire entrer un citoyen dans la maison. */
    public static final int ADOPT_PRESTIGE = 25;

    private HouseService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ chaque jour

    /** La maison suit la généalogie : conjoints et enfants y entrent, les disparus en sortent. */
    public static void sync(MinecraftServer server, Realm realm, long day) {
        HouseState h = realm.dynasty();
        for (CitizenRef m : new ArrayList<>(h.members())) {
            if (!MineColoniesBridge.citizenExists(m)) {
                h.remove(m);
                continue;
            }
            Family f = MineColoniesBridge.family(m);
            String parent = CharacterService.nameOf(server, m);
            if (f.partner() != null && !h.members().contains(f.partner()) && MineColoniesBridge.citizenExists(f.partner())) {
                welcome(server, realm, f.partner(), day);
                Chronicle.record(server, realm, "crusadecraft.chronicle.house.spouse", Component.literal(CharacterService.nameOf(server, f.partner())),
                        Component.literal(parent));
            }
            for (CitizenRef child : f.children()) {
                if (h.members().contains(child)) continue;
                welcome(server, realm, child, day);
                Chronicle.record(server, realm, "crusadecraft.chronicle.house.birth", Component.literal(CharacterService.nameOf(server, child)),
                        Component.literal(parent));
            }
        }
        if (h.heir() != null && !h.members().contains(h.heir())) h.setHeir(null);
    }

    private static void welcome(MinecraftServer server, Realm realm, CitizenRef ref, long day) {
        realm.dynasty().members().add(ref);
        MineColoniesBridge.citizen(ref).ifPresent(info -> CharacterService.record(server, info).addModifier(OpinionModifier.of(OpinionReason.MAISON, day, 1)));
        data(server).setDirty();
    }

    // ------------------------------------------------------------------ actions

    public static CouncilActions.Outcome adopt(ServerPlayer player, CitizenRef ref) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        HouseState h = realm.dynasty();
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty() || !realm.holds(ref.colony())) return CouncilActions.Outcome.no("crusadecraft.house.not_subject");
        if (h.members().contains(ref)) return CouncilActions.Outcome.no("crusadecraft.house.already_member", info.get().name());
        if (h.members().size() >= HouseState.MAX_ADOPTED) return CouncilActions.Outcome.no("crusadecraft.house.full", HouseState.MAX_ADOPTED);
        if (info.get().child()) return CouncilActions.Outcome.no("crusadecraft.character.too_young", info.get().name());
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return CouncilActions.Outcome.no("crusadecraft.character.unavailable", info.get().name(),
                Component.translatable(rec.status().key()));
        if (realm.prestige() < ADOPT_PRESTIGE) return CouncilActions.Outcome.no("crusadecraft.request.no_prestige", ADOPT_PRESTIGE);
        long today = RealmService.currentDay(server);
        realm.setPrestige(realm.prestige() - ADOPT_PRESTIGE);
        welcome(server, realm, ref, today);
        rec.remember(today, "crusadecraft.history.house", realm.name());
        Chronicle.record(server, realm, "crusadecraft.chronicle.house.adopted", Component.literal(info.get().name()));
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.house.adopted", info.get().name()));
    }

    public static CouncilActions.Outcome designateHeir(ServerPlayer player, CitizenRef ref) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        HouseState h = realm.dynasty();
        if (!h.members().contains(ref)) return CouncilActions.Outcome.no("crusadecraft.house.not_member");
        if (ref.equals(h.heir())) return CouncilActions.Outcome.no("crusadecraft.house.already_heir");
        long today = RealmService.currentDay(server);
        boolean first = h.heir() == null;
        h.setHeir(ref);
        if (first) realm.setPrestige(realm.prestige() + 10);
        String name = CharacterService.nameOf(server, ref);
        data(server).character(ref).ifPresent(r -> {
            r.addModifier(OpinionModifier.of(OpinionReason.HERITIER, today, 1));
            r.remember(today, "crusadecraft.history.heir");
        });
        Chronicle.record(server, realm, "crusadecraft.chronicle.house.heir", Component.literal(name));
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.house.heir_named", name));
    }

    // ------------------------------------------------------------------ mariages

    /** Libre d'épouser : adulte, sans conjoint dans sa colonie, jamais marié par la maison. */
    public static boolean eligible(MinecraftServer server, Realm realm, CitizenRef ref) {
        if (!realm.dynasty().members().contains(ref) || realm.dynasty().marriageOf(ref).isPresent()) return false;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty() || info.get().child()) return false;
        return MineColoniesBridge.family(ref).partner() == null;
    }

    /** La réponse qu'un souverain IA fera à une demande en mariage. */
    public static DiplomacyRules.Verdict verdict(MinecraftServer server, Realm realm, AiRealm ai) {
        Relation rel = data(server).diplomacy().between(ai.id(), realm.id());
        return DiplomacyRules.marriage(DiplomacyService.opinion(server, ai, realm), DiplomacyService.context(server, ai, realm), rel,
                !ai.faith().infidel(), realm.dynasty().marriedInto(ai.id()));
    }

    /** Demander la main d'un membre de la famille d'un souverain IA pour un membre de la maison. */
    public static CouncilActions.Outcome propose(ServerPlayer player, CitizenRef member, UUID aiId) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<AiRealm> ai = data(server).world().realm(aiId);
        if (ai.isEmpty() || !ai.get().discoveredBy(player.getUUID())) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        if (!eligible(server, realm, member)) return CouncilActions.Outcome.no("crusadecraft.house.not_eligible");
        DiplomacyRules.Verdict v = verdict(server, realm, ai.get());
        if (v.blockedKey() != null) return CouncilActions.Outcome.no(v.blockedKey());
        long today = RealmService.currentDay(server);
        Component realmName = AiNames.realmThe(ai.get().rank(), ai.get().place());
        String name = CharacterService.nameOf(server, member);
        if (v.accepted()) {
            realm.dynasty().promised().put(aiId, member);
            DiplomacyService.post(server, realm, new Letter(UUID.randomUUID(), aiId, true, realm.id(), sender(ai.get()),
                    Component.translatable("crusadecraft.letter.marriage_accept.subject"),
                    Component.translatable("crusadecraft.letter.marriage_accept.body", realmName, name),
                    List.of(new Letter.Choice(Letter.Action.SEAL_MARRIAGE, true), new Letter.Choice(Letter.Action.REFUSE, false)), today, today + 5, 0));
        } else {
            data(server).diplomacy().between(aiId, realm.id()).addOpinion(aiId, OpinionModifier.of(OpinionReason.REFUS_DIPLOMATIQUE, today, 1));
            DiplomacyService.post(server, realm, new Letter(UUID.randomUUID(), aiId, true, realm.id(), sender(ai.get()),
                    Component.translatable("crusadecraft.letter.marriage_refuse.subject"),
                    Component.translatable("crusadecraft.letter.marriage_refuse.body", realmName, name), List.of(), today, today + 7, 0));
        }
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.diplo.sent", realmName));
    }

    /** Un souverain IA bien disposé propose lui-même un mariage (par le courrier du Colombier). */
    public static Optional<Letter> aiOffer(MinecraftServer server, Realm realm, AiRealm ai, long today) {
        if (ai.faith().infidel() || realm.dynasty().marriedInto(ai.id())) return Optional.empty();
        Relation rel = data(server).diplomacy().between(ai.id(), realm.id());
        if (rel.marriage() || rel.war()) return Optional.empty();
        CitizenRef member = null;
        for (CitizenRef m : realm.dynasty().members()) {
            if (eligible(server, realm, m)) {
                member = m;
                break;
            }
        }
        if (member == null) return Optional.empty();
        realm.dynasty().promised().put(ai.id(), member);
        Component realmName = AiNames.realmThe(ai.rank(), ai.place());
        return Optional.of(new Letter(UUID.randomUUID(), ai.id(), true, realm.id(), sender(ai),
                Component.translatable("crusadecraft.letter.marriage_offer.subject"),
                Component.translatable("crusadecraft.letter.marriage_offer.body", realmName, CharacterService.nameOf(server, member)),
                List.of(new Letter.Choice(Letter.Action.SEAL_MARRIAGE, true), new Letter.Choice(Letter.Action.REFUSE, false)), today, today + 5, 0));
    }

    /** Le mariage scellé : les deux maisons s'unissent, une alliance défensive suit. */
    public static CouncilActions.Outcome marry(MinecraftServer server, Realm realm, UUID aiId) {
        Optional<AiRealm> ai = data(server).world().realm(aiId);
        if (ai.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.letter.gone");
        HouseState h = realm.dynasty();
        CitizenRef member = h.promised().remove(aiId);
        if (member == null || !eligible(server, realm, member)) {
            member = null;
            for (CitizenRef m : h.members()) {
                if (eligible(server, realm, m)) {
                    member = m;
                    break;
                }
            }
        }
        if (member == null) return CouncilActions.Outcome.no("crusadecraft.house.nobody_to_marry");
        if (h.marriedInto(aiId)) return CouncilActions.Outcome.no("crusadecraft.house.block.already");
        long today = RealmService.currentDay(server);
        boolean female = MineColoniesBridge.citizen(member).map(CitizenInfo::female).orElse(false);
        String spouse = new NameGenerator(aiId.getLeastSignificantBits() ^ member.citizenId()).personName(!female);
        h.marriages().add(new HouseState.Marriage(member, aiId, spouse, today));
        Relation rel = data(server).diplomacy().between(aiId, realm.id());
        rel.setMarriage(true);
        if (!rel.has(Treaty.ALLIANCE, today)) rel.sign(Treaty.ALLIANCE_DEFENSIVE, today);
        rel.addOpinion(aiId, OpinionModifier.of(OpinionReason.MARIAGE, today, 1));
        realm.setPrestige(realm.prestige() + 15);
        String name = CharacterService.nameOf(server, member);
        Component house = AiNames.house(ai.get().house());
        data(server).character(member).ifPresent(r -> {
            r.addModifier(OpinionModifier.of(OpinionReason.MARIAGE, today, 1));
            r.remember(today, "crusadecraft.history.married", spouse, ai.get().house());
        });
        Chronicle.record(server, realm, "crusadecraft.chronicle.house.married", Component.literal(name), Component.literal(spouse), house);
        data(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.house.married", name, spouse, house));
    }

    private static Component sender(AiRealm ai) {
        return Component.translatable("crusadecraft.letter.sender", ai.ruler().name(),
                Component.translatable(ai.rank().holderKey(ai.ruler().female())), AiNames.realm(ai.rank(), ai.place()));
    }

    // ------------------------------------------------------------------ vue

    public static HouseView view(MinecraftServer server, Realm realm, ServerPlayer viewer) {
        HouseState h = realm.dynasty();
        List<HouseView.Member> members = new ArrayList<>();
        for (CitizenRef m : h.members()) {
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(m);
            if (info.isEmpty()) continue;
            Family f = MineColoniesBridge.family(m);
            String spouse = "";
            Optional<HouseState.Marriage> marriage = h.marriageOf(m);
            if (marriage.isPresent()) {
                String house = data(server).world().realm(marriage.get().realm()).map(AiRealm::house).orElse("?");
                spouse = Component.translatable("crusadecraft.house.spouse.dynastic", marriage.get().spouse(), AiNames.house(house)).getString();
            } else if (f.partner() != null) {
                spouse = CharacterService.nameOf(server, f.partner());
            }
            members.add(new HouseView.Member(m, info.get().name(), info.get().female(), info.get().child(), m.equals(h.heir()), spouse,
                    f.children().size(), eligible(server, realm, m)));
        }
        List<HouseView.Candidate> candidates = new ArrayList<>();
        List<CitizenRef> court = new ArrayList<>();
        for (var e : realm.council().entrySet()) court.add(e.getValue());
        court.addAll(realm.mayors().values());
        for (CitizenRef c : court) {
            if (h.members().contains(c)) continue;
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(c);
            if (info.isEmpty() || info.get().child()) continue;
            Post post = realm.postOf(c);
            candidates.add(new HouseView.Candidate(c, info.get().name(), post == null ? "" : post.key(info.get().female())));
        }
        List<HouseView.Match> matches = new ArrayList<>();
        for (AiRealm ai : data(server).world().realms()) {
            if (!ai.discoveredBy(viewer.getUUID()) || ai.landless()) continue;
            DiplomacyRules.Verdict v = verdict(server, realm, ai);
            matches.add(new HouseView.Match(ai.id(), ai.place(), ai.rank(), ai.color(), ai.house(), ai.faith(), v.accepted(),
                    v.blockedKey() == null ? "" : v.blockedKey(), v.total()));
        }
        return new HouseView(realm.dynasty().members().size(), HouseState.MAX_ADOPTED, realm.prestige(), ADOPT_PRESTIGE, members, candidates, matches);
    }
}
