package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.SealItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Titres, contrats de vassalité et revendications : ce qui se règle à la Chancery Table, sous le Sceau royal. */
public final class TitleService {

    private TitleService() {}

    /** Les provinces d'un royaume : ses colonies, ses conquêtes, et les terres de ses vassaux. */
    public static int provinces(MinecraftServer server, Realm realm) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int n = realm.allColonies().size() + fr.crusadecraft.core.war.WarService.provincesOf(server, realm.id()).size();
        for (UUID vassal : data.diplomacy().vassalsOf(realm.id())) {
            n += data.world().realm(vassal).map(a -> a.ownProvinces().size())
                    .orElseGet(() -> data.realm(vassal).map(r -> r.allColonies().size()).orElse(0));
        }
        return n;
    }

    public record Requirement(TitleRank rank, int provinces, int gold, int prestige) {}

    public static Optional<Requirement> next(Realm realm) {
        TitleRank next = realm.rank().next();
        if (next == realm.rank()) return Optional.empty();
        int i = next.ordinal() - 1;
        return Optional.of(new Requirement(next, pick(CrusadeConfig.SERVER.titleProvinces.get(), i),
                pick(CrusadeConfig.SERVER.titleGold.get(), i), pick(CrusadeConfig.SERVER.titlePrestige.get(), i)));
    }

    private static int pick(List<? extends Integer> list, int index) {
        if (list.isEmpty()) return 0;
        return list.get(Math.max(0, Math.min(list.size() - 1, index)));
    }

    private static Optional<Realm> ruler(ServerPlayer player) {
        return RealmService.realmOf(player.server, player.getUUID()).filter(r -> r.state().isActive());
    }

    public static CouncilActions.Outcome createTitle(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<Requirement> req = next(realm);
        if (req.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.title.highest");
        Optional<Component> seal = SealItem.problem(player, realm);
        if (seal.isPresent()) return new CouncilActions.Outcome(false, seal.get());
        Requirement r = req.get();
        if (provinces(server, realm) < r.provinces()) return CouncilActions.Outcome.no("crusadecraft.title.need_provinces", r.provinces());
        if (realm.prestige() < r.prestige()) return CouncilActions.Outcome.no("crusadecraft.request.no_prestige", r.prestige());
        if (!TreasuryService.spend(server, realm, LedgerCategory.TITRES, r.gold())) return CouncilActions.Outcome.no("crusadecraft.request.no_gold", r.gold());
        realm.setPrestige(realm.prestige() - r.prestige());
        realm.setRank(r.rank());
        CrusadeSavedData.get(server).setDirty();
        Component title = Component.translatable(r.rank().holderKey(realm.feminineTitle()));
        CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.toast.title.title"),
                Component.translatable("crusadecraft.toast.title.body", title, Component.translatable(r.rank().titleKey()))));
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.title.created", Component.translatable(r.rank().titleKey())));
    }

    /** Modifie le contrat d'un vassal : plus d'impôts ou de levées, et il vous aimera moins. */
    public static CouncilActions.Outcome setContract(ServerPlayer player, UUID vassal, int tax, int levy) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<Relation> rel = CrusadeSavedData.get(server).diplomacy().find(realm.id(), vassal);
        if (rel.isEmpty() || !rel.get().isVassalOf(vassal, realm.id())) return CouncilActions.Outcome.no("crusadecraft.title.not_vassal");
        Optional<Component> seal = SealItem.problem(player, realm);
        if (seal.isPresent()) return new CouncilActions.Outcome(false, seal.get());
        rel.get().setContract(tax, levy);
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.title.contract_set", Relation.clampRate(tax), Relation.clampRate(levy)));
    }

    /** Libère un vassal : il en sera reconnaissant, mais le royaume rétrécit. */
    public static CouncilActions.Outcome release(ServerPlayer player, UUID vassal) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<Relation> rel = CrusadeSavedData.get(server).diplomacy().find(realm.id(), vassal);
        if (rel.isEmpty() || !rel.get().isVassalOf(vassal, realm.id())) return CouncilActions.Outcome.no("crusadecraft.title.not_vassal");
        rel.get().freeVassal();
        rel.get().addOpinion(vassal, OpinionModifier.of(OpinionReason.CADEAU, RealmService.currentDay(server), 2));
        realm.setPrestige(Math.max(0, realm.prestige() - 20));
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.title.released", DiplomacyService.name(server, vassal)));
    }

    /** Confie au chancelier la fabrication d'une revendication sur une province connue. */
    public static CouncilActions.Outcome fabricateClaim(ServerPlayer player, int province) {
        MinecraftServer server = player.server;
        Optional<Realm> found = ruler(player);
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (realm.council().get(Post.CHANCELIER) == null) return CouncilActions.Outcome.no("crusadecraft.diplo.no_chancellor");
        Optional<AiProvince> target = province(server, province, player.getUUID());
        if (target.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.world.unknown_realm");
        if (realm.claims().contains(province)) return CouncilActions.Outcome.no("crusadecraft.claim.already");
        realm.court().setTask(Post.CHANCELIER, CouncilTask.FABRIQUER_REVENDICATION);
        realm.court().setTaskTarget(Post.CHANCELIER, "p:" + province);
        realm.court().setClaimProgress(0);
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.claim.started", target.get().name()));
    }

    /** Une province IA d'un royaume découvert par ce joueur. */
    static Optional<AiProvince> province(MinecraftServer server, int id, UUID player) {
        for (AiRealm r : CrusadeSavedData.get(server).world().realms()) {
            if (!r.discoveredBy(player)) continue;
            for (AiProvince p : r.provinces()) if (p.id() == id) return Optional.of(p);
        }
        return Optional.empty();
    }

    /** Le progrès quotidien de la revendication en cours (tâche du chancelier). Renvoie vrai si elle aboutit. */
    public static boolean advanceClaim(MinecraftServer server, Realm realm, int diplomacy, double bonus) {
        String target = realm.court().taskTarget(Post.CHANCELIER);
        if (target == null || !target.startsWith("p:")) return false;
        int province;
        try {
            province = Integer.parseInt(target.substring(2));
        } catch (NumberFormatException e) {
            return false;
        }
        int progress = realm.court().claimProgress() + (int) Math.round((3 + diplomacy / 3.0) * bonus);
        if (progress < 100) {
            realm.court().setClaimProgress(progress);
            return false;
        }
        realm.court().setClaimProgress(0);
        realm.court().setTaskTarget(Post.CHANCELIER, null);
        realm.court().setTask(Post.CHANCELIER, CouncilTask.GERER_LA_COUR);
        realm.claims().add(province);
        String name = province(server, province, realm.ruler()).map(AiProvince::name).orElse("?");
        long today = RealmService.currentDay(server);
        DiplomacyService.post(server, realm, new Letter(UUID.randomUUID(), realm.id(), false, realm.id(),
                Component.translatable("crusadecraft.letter.chancellor"), Component.translatable("crusadecraft.letter.claim.subject", name),
                Component.translatable("crusadecraft.letter.claim.body", name), List.of(), today, today + 30, 0).withClaim(province));
        return true;
    }

    public static ChanceryView view(ServerPlayer player) {
        MinecraftServer server = player.server;
        Realm realm = RealmService.realmOf(server, player.getUUID()).orElseThrow();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Requirement> req = next(realm);
        List<ChanceryView.Vassal> vassals = new ArrayList<>();
        for (UUID v : data.diplomacy().vassalsOf(realm.id())) {
            Relation rel = data.diplomacy().between(v, realm.id());
            Optional<AiRealm> ai = data.world().realm(v);
            if (ai.isPresent()) {
                AiRealm a = ai.get();
                vassals.add(new ChanceryView.Vassal(a.id(), true, a.place(), a.rank(), a.color(), a.ownProvinces().size(), rel.vassalTax(),
                        rel.vassalLevy(), DiplomacyService.opinion(server, a, realm), Math.round(a.lastIncome() * rel.vassalTax() / 100.0)));
            } else {
                data.realm(v).ifPresent(o -> vassals.add(new ChanceryView.Vassal(o.id(), false, o.name(), o.rank(), o.color(),
                        o.allColonies().size(), rel.vassalTax(), rel.vassalLevy(), 0, 0)));
            }
        }
        List<ChanceryView.Claim> claims = new ArrayList<>();
        List<ChanceryView.Claim> candidates = new ArrayList<>();
        for (AiRealm r : data.world().realms()) {
            if (!r.discoveredBy(player.getUUID()) || data.diplomacy().find(r.id(), realm.id()).map(x -> x.isVassalOf(r.id(), realm.id())).orElse(false)) continue;
            for (AiProvince p : r.provinces()) {
                if (realm.id().equals(p.holder())) continue;
                int distance = (int) Math.sqrt(p.distanceSqr(player.getX(), player.getZ()));
                ChanceryView.Claim c = new ChanceryView.Claim(p.id(), p.name(), r.place(), r.rank(), distance);
                if (realm.claims().contains(p.id())) claims.add(c);
                else candidates.add(c);
            }
        }
        candidates.sort(Comparator.comparingInt(ChanceryView.Claim::distance));
        if (candidates.size() > 12) candidates = new ArrayList<>(candidates.subList(0, 12));
        int claimTarget = -1;
        String target = realm.court().taskTarget(Post.CHANCELIER);
        if (target != null && target.startsWith("p:") && realm.court().task(Post.CHANCELIER) == CouncilTask.FABRIQUER_REVENDICATION) {
            try {
                claimTarget = Integer.parseInt(target.substring(2));
            } catch (NumberFormatException ignored) {
                // cible illisible
            }
        }
        Optional<Component> seal = SealItem.problem(player, realm);
        return new ChanceryView(realm.name(), realm.rank(), provinces(server, realm), req.map(Requirement::rank).orElse(null),
                req.map(Requirement::provinces).orElse(0), req.map(Requirement::gold).orElse(0), req.map(Requirement::prestige).orElse(0),
                realm.gold(), realm.prestige(), seal.isEmpty(), seal.map(Component::getString).orElse(""),
                realm.council().get(Post.CHANCELIER) != null, vassals, claims, candidates, claimTarget, realm.court().claimProgress());
    }

    /** Pour les messages : le nom d'un royaume IA. */
    static Component aiName(AiRealm r) {
        return AiNames.realm(r.rank(), r.place());
    }
}
