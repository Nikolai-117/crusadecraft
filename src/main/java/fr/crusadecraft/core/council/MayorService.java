package fr.crusadecraft.core.council;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Le Bureau du Maire : le rapport d'une colonie et le choix de son maire. */
public final class MayorService {
    private static final int CANDIDATES = 6;

    private MayorService() {}

    public static MayorView view(MinecraftServer server, ServerPlayer viewer, ColonyRef colony) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Realm> holder = data.realmHolding(colony);
        boolean canAct = holder.isPresent() && holder.get().ruler().equals(viewer.getUUID()) && holder.get().state().isActive();
        MayorView.Person mayor = null;
        List<MayorView.Person> candidates = new ArrayList<>();
        List<CouncilView.RequestSummary> requests = new ArrayList<>();
        boolean desk = false;
        if (holder.isPresent()) {
            Realm realm = holder.get();
            desk = realm.court().mayorDesks().containsKey(colony);
            CitizenRef current = realm.mayors().get(colony);
            if (current != null) mayor = person(server, realm, current);
            if (canAct) {
                for (CitizenRef ref : MineColoniesBridge.citizensOf(colony)) {
                    if (ref.equals(current)) continue;
                    MayorView.Person p = candidate(server, realm, ref);
                    if (p != null) candidates.add(p);
                }
                candidates.sort(Comparator.comparingInt(MayorView.Person::stewardship).reversed());
                if (candidates.size() > CANDIDATES) candidates = new ArrayList<>(candidates.subList(0, CANDIDATES));
            }
            for (Request r : realm.court().requests()) {
                if (r.from().equals(current)) {
                    requests.add(new CouncilView.RequestSummary(r.id(), r.type(), CharacterService.nameOf(server, r.from()),
                            r.place(), r.count(), r.createdDay()));
                }
            }
        }
        return new MayorView(colony, MineColoniesBridge.info(colony).name(), canAct, mayor, MineColoniesBridge.report(colony),
                candidates, CrusadeConfig.SERVER.officeHour.get(), desk, requests);
    }

    private static MayorView.Person person(MinecraftServer server, Realm realm, CitizenRef ref) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty()) return new MayorView.Person(ref, CharacterService.nameOf(server, ref), false, 0, 0);
        CharacterRecord rec = CharacterService.record(server, info.get());
        int stew = CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.INTENDANCE.ordinal()];
        int opinion = Opinion.total(CharacterService.opinionLines(server, realm, rec));
        return new MayorView.Person(ref, info.get().name(), info.get().female(), stew, opinion);
    }

    /** Un candidat possible : adulte, libre, sans autre poste. */
    private static MayorView.Person candidate(MinecraftServer server, Realm realm, CitizenRef ref) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty() || info.get().child()) return null;
        if (realm.postOf(ref) != null) return null;
        Optional<CharacterRecord> existing = CrusadeSavedData.get(server).character(ref);
        if (existing.isPresent() && existing.get().status() != CharacterStatus.LIBRE) return null;
        return person(server, realm, ref);
    }
}
