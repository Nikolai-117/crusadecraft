package fr.crusadecraft.core.realm;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Les villes du royaume (P19) : rattacher une autre colonie qu'on possède, la confier à un gouverneur pris dans le
 * registre des lignées, régler ce qu'elle doit à la couronne, ou la reprendre en direct. Rien ne change côté
 * MineColonies : le souverain reste le propriétaire de la colonie, le gouverneur la tient pour lui.
 */
public final class CityService {
    private CityService() {}

    private static Optional<Realm> ruled(ServerPlayer player) {
        return RealmService.realmOf(player.server, player.getUUID()).filter(r -> r.state().isActive());
    }

    /** Ceux qui peuvent gouverner : les vivants du registre rattachés à ce royaume, ou de la maison du souverain. */
    public static List<Person> candidates(MinecraftServer server, Realm realm) {
        LineageState lineage = CrusadeSavedData.get(server).lineage();
        UUID house = lineage.houseOfRealm(realm.id()).map(h -> h.id()).orElse(null);
        List<Person> out = new ArrayList<>();
        for (Person p : lineage.persons()) {
            if (!p.alive()) continue;
            if (realm.id().equals(p.realm()) || (house != null && house.equals(p.house()))) out.add(p);
        }
        out.sort(Comparator.comparing(lineage::fullName));
        return out;
    }

    /** L'héritier d'un gouverneur mort, s'il peut gouverner pour ce royaume. */
    static Optional<Person> inheritor(MinecraftServer server, Realm realm, Person dead) {
        return fr.crusadecraft.core.lineage.LifeService.heirOf(server, dead).filter(h -> isCandidate(server, realm, h.id()));
    }

    private static boolean isCandidate(MinecraftServer server, Realm realm, UUID person) {
        for (Person p : candidates(server, realm)) if (p.id().equals(person)) return true;
        return false;
    }

    /**
     * Les termes devenus caducs : un gouverneur mort ou effacé du registre, une ville perdue. La ville revient en direct.
     * Renvoie vrai si quelque chose a changé.
     */
    public static boolean sweep(MinecraftServer server, Realm realm) {
        LineageState lineage = CrusadeSavedData.get(server).lineage();
        boolean changed = false;
        for (Map.Entry<ColonyRef, CityTerms> e : new ArrayList<>(realm.cities().entrySet())) {
            Optional<Person> governor = lineage.person(e.getValue().governor());
            if (!realm.holds(e.getKey()) || governor.isEmpty()) {
                realm.setCityTerms(e.getKey(), null);
                changed = true;
            } else if (!governor.get().alive()) {
                // P26 : la ville passe à l'héritier du gouverneur s'il peut la tenir, aux mêmes conditions ; sinon elle revient.
                Optional<Person> heir = inheritor(server, realm, governor.get());
                realm.setCityTerms(e.getKey(), heir.map(h -> e.getValue().withGovernor(h.id())).orElse(null));
                changed = true;
            }
        }
        if (changed) CrusadeSavedData.get(server).setDirty();
        return changed;
    }

    /** Rattache au royaume la colonie du souverain où il se tient. */
    public static Outcome attachHere(ServerPlayer player) {
        Optional<Realm> realm = ruled(player);
        if (realm.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(player.level(), player.blockPosition());
        if (colony.isEmpty()) return Outcome.no("crusadecraft.city.attach.none");
        String name = MineColoniesBridge.info(colony.get()).name();
        if (realm.get().holds(colony.get())) return Outcome.no("crusadecraft.city.attach.already", name);
        Optional<Component> said = RealmService.annexOwnColony(player, colony.get());
        if (realm.get().holds(colony.get())) return Outcome.ok(said.orElse(Component.translatable("crusadecraft.annex.done", name)));
        return said.map(m -> new Outcome(false, m)).orElse(Outcome.no("crusadecraft.city.attach.none"));
    }

    /** Confie une ville à un gouverneur, ou change ce qu'elle doit à la couronne. */
    public static Outcome entrust(ServerPlayer player, ColonyRef colony, UUID governor, int taxShare, int levyShare) {
        Optional<Realm> found = ruled(player);
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (colony == null || !realm.holds(colony)) return Outcome.no("crusadecraft.city.not_yours");
        if (colony.equals(realm.capital())) return Outcome.no("crusadecraft.city.capital");
        if (governor == null) return recall(player, colony);
        if (!isCandidate(player.server, realm, governor)) return Outcome.no("crusadecraft.city.governor.invalid");
        CityTerms terms = new CityTerms(governor, taxShare, levyShare);
        realm.setCityTerms(colony, terms);
        CrusadeSavedData data = CrusadeSavedData.get(player.server);
        data.setDirty();
        String who = data.lineage().person(governor).map(data.lineage()::fullName).orElse("?");
        return Outcome.ok(Component.translatable("crusadecraft.city.entrusted", MineColoniesBridge.info(colony).name(), who,
                terms.taxShare(), terms.levyShare()));
    }

    /** Reprend une ville en direct. */
    public static Outcome recall(ServerPlayer player, ColonyRef colony) {
        Optional<Realm> found = ruled(player);
        if (found.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (colony == null || !realm.holds(colony)) return Outcome.no("crusadecraft.city.not_yours");
        if (realm.cityTerms(colony).isEmpty()) return Outcome.no("crusadecraft.city.direct.already");
        realm.setCityTerms(colony, null);
        CrusadeSavedData.get(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.city.recalled", MineColoniesBridge.info(colony).name()));
    }

    /** L'écran « Villes » de ce souverain ; vide s'il n'a pas de royaume. */
    public static Optional<CityView> view(ServerPlayer player) {
        Optional<Realm> found = ruled(player);
        if (found.isEmpty()) return Optional.empty();
        Realm realm = found.get();
        MinecraftServer server = player.server;
        sweep(server, realm);
        LineageState lineage = CrusadeSavedData.get(server).lineage();
        int limit = CityRules.domainLimit(realm.rank(), CrusadeConfig.SERVER.domainLimits.get());
        Set<ColonyRef> over = CityRules.overLimit(realm.allColonies(), realm.cities(), limit);
        List<CityView.Row> rows = new ArrayList<>();
        for (ColonyRef c : realm.allColonies()) {
            ColonyInfo info = MineColoniesBridge.info(c);
            CityTerms terms = realm.cityTerms(c).orElse(null);
            String governor = terms == null ? "" : lineage.person(terms.governor()).map(lineage::fullName).orElse("?");
            rows.add(new CityView.Row(c, info.isMissing() ? "?" : info.name(), c.equals(realm.capital()), info.citizens(),
                    terms == null ? null : terms.governor(), governor, terms == null ? 100 : terms.taxShare(),
                    terms == null ? 100 : terms.levyShare(), over.contains(c)));
        }
        List<CityView.Candidate> candidates = new ArrayList<>();
        for (Person p : candidates(server, realm)) candidates.add(new CityView.Candidate(p.id(), lineage.fullName(p)));
        String here = MineColoniesBridge.colonyAt(player.level(), player.blockPosition())
                .filter(c -> !realm.holds(c) && MineColoniesBridge.isOwner(c, player.getUUID())
                        && CrusadeSavedData.get(server).realmHolding(c).isEmpty())
                .map(c -> MineColoniesBridge.info(c).name()).orElse("");
        return Optional.of(new CityView(realm.name(), limit, CityRules.directCount(realm.allColonies(), realm.cities()),
                CrusadeConfig.SERVER.domainPenalty.get(), rows, candidates, here, CrusadeConfig.SERVER.governorTaxShare.get(),
                CrusadeConfig.SERVER.governorLevyShare.get()));
    }
}
