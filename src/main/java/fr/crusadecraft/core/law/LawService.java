package fr.crusadecraft.core.law;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Changer une loi : le conseil vote, et l'on passe outre en dépensant du prestige. Sans conseil, le
 * souverain décide seul.
 */
public final class LawService {

    private LawService() {}

    /** Un conseiller en état de voter, et son citoyen. */
    private record Councillor(CitizenRef ref, LawEffects.Voter voter) {}

    private static List<Councillor> councillors(MinecraftServer server, Realm realm) {
        List<Councillor> out = new ArrayList<>();
        for (CitizenRef ref : realm.council().values()) {
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
            if (info.isEmpty()) continue;
            CharacterRecord rec = CharacterService.record(server, info.get());
            if (rec.status() != CharacterStatus.LIBRE || rec.deceased()) continue;
            int opinion = Opinion.total(CharacterService.opinionLines(server, realm, rec));
            out.add(new Councillor(ref, new LawEffects.Voter(info.get().name(), opinion, rec.traits())));
        }
        return out;
    }

    private static LawEffects.Vote vote(List<Councillor> council, Realm realm, Law law, int to) {
        List<LawEffects.Voter> voters = new ArrayList<>();
        for (Councillor c : council) voters.add(c.voter());
        return LawEffects.vote(voters, law, realm.laws().level(law), to, realm.dread());
    }

    private static int forceCost(LawEffects.Vote vote) {
        return LawEffects.forceCost(vote, CrusadeConfig.SERVER.forceCostPerVote.get());
    }

    /** Le niveau est-il ouvert par les recherches de la capitale ? */
    public static boolean unlocked(Realm realm, Law law, int level) {
        ResourceLocation research = law.levels().get(law.clamp(level)).research();
        return research == null || MineColoniesBridge.researchEffect(realm.capital(), research) > 0;
    }

    public static LawsView view(MinecraftServer server, Realm realm) {
        long today = RealmService.currentDay(server);
        List<Councillor> council = councillors(server, realm);
        List<LawsView.LawLine> lines = new ArrayList<>();
        for (Law law : Law.values()) {
            int current = realm.laws().level(law);
            List<LawsView.LevelOption> options = new ArrayList<>();
            for (int level = 0; level < law.levels().size(); level++) {
                List<String> approve = new ArrayList<>(), reject = new ArrayList<>();
                int cost = 0;
                if (level != current) {
                    LawEffects.Vote vote = vote(council, realm, law, level);
                    for (LawEffects.Ballot b : vote.ballots()) (b.approves() ? approve : reject).add(b.name());
                    cost = forceCost(vote);
                }
                options.add(new LawsView.LevelOption(level, unlocked(realm, law, level), approve, reject, cost));
            }
            lines.add(new LawsView.LawLine(law, current,
                    realm.laws().cooldownLeft(law, today, CrusadeConfig.SERVER.lawCooldownDays.get()), options));
        }
        return new LawsView(realm.prestige(), council.size(), lines);
    }

    /**
     * Propose un changement de loi. S'il est rejeté et que {@code force} est vrai, la loi passe quand même,
     * contre du prestige, et ceux qui ont voté contre s'en souviennent.
     */
    public static CouncilActions.Outcome propose(ServerPlayer player, Law law, int level, boolean force) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (!realm.state().isActive()) return CouncilActions.Outcome.no("crusadecraft.law.realm_dormant");
        if (law == null || level < 0 || level >= law.levels().size()) return CouncilActions.Outcome.no("crusadecraft.law.bad_level");
        int current = realm.laws().level(law);
        if (level == current) return CouncilActions.Outcome.no("crusadecraft.law.already");
        if (!unlocked(realm, law, level)) return CouncilActions.Outcome.no("crusadecraft.law.locked");
        long today = RealmService.currentDay(server);
        long wait = realm.laws().cooldownLeft(law, today, CrusadeConfig.SERVER.lawCooldownDays.get());
        if (wait > 0) return CouncilActions.Outcome.no("crusadecraft.law.cooldown", wait);

        List<Councillor> council = councillors(server, realm);
        LawEffects.Vote vote = vote(council, realm, law, level);
        Component levelName = Component.translatable(law.levelKey(level));
        CrusadeSavedData data = CrusadeSavedData.get(server);
        if (!vote.passes()) {
            int cost = forceCost(vote);
            if (!force) {
                return CouncilActions.Outcome.no("crusadecraft.law.rejected", vote.approvals(), vote.rejections(), cost);
            }
            if (realm.prestige() < cost) return CouncilActions.Outcome.no("crusadecraft.law.no_prestige", cost);
            realm.setPrestige(realm.prestige() - cost);
            for (int i = 0; i < council.size(); i++) {
                if (vote.ballots().get(i).approves()) continue;
                data.character(council.get(i).ref()).ifPresent(r -> {
                    r.addModifier(OpinionModifier.of(OpinionReason.LOI_IMPOSEE, today, 1));
                    r.remember(today, "crusadecraft.history.law_forced", law.levelKey(level));
                });
            }
        }
        realm.laws().set(law, level, today);
        fr.crusadecraft.core.intrigue.Chronicle.record(server, realm, "crusadecraft.chronicle.law", Component.translatable(law.key()), levelName);
        data.setDirty();
        if (law == Law.FISCALITE) TreasuryService.applyHappiness(server);
        String key = vote.ballots().isEmpty() ? "crusadecraft.law.decreed" : vote.passes() ? "crusadecraft.law.adopted" : "crusadecraft.law.forced";
        return CouncilActions.Outcome.ok(Component.translatable(key, levelName, vote.approvals(), vote.rejections()));
    }
}
