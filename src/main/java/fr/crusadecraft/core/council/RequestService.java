package fr.crusadecraft.core.council;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtPackets;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Les requêtes des dignitaires : quand elles naissent (rapports des maires, alertes du maître-espion,
 * griefs, ambitions), comment elles parviennent au souverain (le dignitaire vient le trouver, ou une
 * notification s'il est loin), et l'effet de chaque réponse.
 */
public final class RequestService {
    /** Distance maximale à laquelle un dignitaire vient trouver le souverain à pied. */
    private static final double AUDIENCE_RANGE = 48;
    private static final long AUDIENCE_TIMEOUT_TICKS = 20 * 60;

    // Session : requêtes déjà annoncées, audiences en cours.
    private static final Set<UUID> ANNOUNCED = new HashSet<>();
    private static final Set<UUID> IN_AUDIENCE = new HashSet<>();
    private static final Random RANDOM = new Random();

    private RequestService() {}

    public static void onServerStopped() {
        ANNOUNCED.clear();
        IN_AUDIENCE.clear();
    }

    // ------------------------------------------------------------------ naissance des requêtes

    /** Passage périodique : nouvelles requêtes et requêtes expirées. */
    public static void periodic(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        for (Realm realm : data.realms()) {
            if (!realm.state().isActive()) continue;
            expire(server, realm, today);
            generate(server, realm, today);
        }
        data.setDirty();
    }

    private static void generate(MinecraftServer server, Realm realm, long today) {
        CourtState court = realm.court();
        int max = CrusadeConfig.SERVER.maxPendingRequests.get();

        // Les maires rapportent les difficultés de leur colonie.
        for (Map.Entry<ColonyRef, CitizenRef> e : realm.mayors().entrySet()) {
            ColonyReport report = MineColoniesBridge.report(e.getKey());
            if (report.name().isEmpty()) continue;
            CitizenRef mayor = e.getValue();
            raise(server, realm, RequestType.MAYOR_HOMELESS, mayor, report.name(), report.homeless(),
                    CrusadeConfig.SERVER.homelessThreshold.get(), today, max);
            raise(server, realm, RequestType.MAYOR_JOBLESS, mayor, report.name(), report.jobless(),
                    CrusadeConfig.SERVER.joblessThreshold.get(), today, max);
            raise(server, realm, RequestType.MAYOR_SICK, mayor, report.name(), report.sick(),
                    CrusadeConfig.SERVER.sickThreshold.get(), today, max);
            raise(server, realm, RequestType.MAYOR_HUNGER, mayor, report.name(), report.hungry(),
                    CrusadeConfig.SERVER.hungryThreshold.get(), today, max);
        }

        // Le maître-espion qui surveille les terres annonce les attaques de pillards.
        CitizenRef spy = realm.council().get(Post.MAITRE_ESPION);
        if (spy != null && court.task(Post.MAITRE_ESPION) == CouncilTask.SURVEILLER_LES_TERRES) {
            int intrigue = statOf(server, spy, Stat.INTRIGUE);
            for (ColonyRef colony : realm.allColonies()) {
                ColonyReport report = MineColoniesBridge.report(colony);
                if (!report.raidTonight()) continue;
                if (RANDOM.nextDouble() < Math.min(1.0, 0.4 + intrigue / 25.0)) {
                    raise(server, realm, RequestType.SPY_RAID_WARNING, spy, report.name(), 1, 1, today, max + 2);
                }
            }
        }

        // Griefs et ambitions des dignitaires.
        List<CitizenRef> dignitaries = new ArrayList<>(realm.council().values());
        dignitaries.addAll(realm.mayors().values());
        for (CitizenRef ref : dignitaries) {
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
            if (info.isEmpty()) continue;
            CharacterRecord rec = CharacterService.record(server, info.get());
            int opinion = Opinion.total(CharacterService.opinionLines(server, realm, rec));
            if (opinion <= -30) raise(server, realm, RequestType.GRIEVANCE, ref, "", 1, 1, today, max);
            if (rec.traits().contains(Trait.AMBITIEUX) && RANDOM.nextDouble() < 0.25) {
                int[] stats = CharacterService.stats(info.get().skills(), rec.traits()).total();
                int best = 0;
                for (int s : stats) best = Math.max(best, s);
                if (best >= 12) raise(server, realm, RequestType.AMBITION, ref, "", 1, 1, today, max);
            }
        }
    }

    private static void raise(MinecraftServer server, Realm realm, RequestType type, CitizenRef from, String place,
                              int count, int threshold, long today, int max) {
        if (count < threshold) return;
        CourtState court = realm.court();
        if (court.requests().size() >= max) return;
        if (court.onCooldown(type, from, today)) return;
        for (Request r : court.requests()) {
            if (r.type() == type && r.from().equals(from)) return;
        }
        Request request = new Request(UUID.randomUUID(), type, from, place, count, today);
        court.requests().add(request);
        court.markRequested(type, from, today);
        if (type == RequestType.SPY_RAID_WARNING) {
            // Une alerte n'attend pas : elle arrive aussitôt, en plus de l'audience.
            notifyRuler(server, realm, ToastPacket.Kind.WAR, Component.translatable(type.titleKey()),
                    Component.translatable("crusadecraft.toast.raid_warning", place));
        }
    }

    private static void expire(MinecraftServer server, Realm realm, long today) {
        int days = CrusadeConfig.SERVER.requestExpiryDays.get();
        Iterator<Request> it = realm.court().requests().iterator();
        while (it.hasNext()) {
            Request r = it.next();
            if (today - r.createdDay() < days) continue;
            it.remove();
            ANNOUNCED.remove(r.id());
            if (r.type() != RequestType.SPY_RAID_WARNING) {
                CrusadeSavedData.get(server).character(r.from()).ifPresent(rec ->
                        rec.addModifier(OpinionModifier.of(OpinionReason.REQUETE_IGNOREE, today, 0.5)));
            }
        }
    }

    private static int statOf(MinecraftServer server, CitizenRef ref, Stat stat) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty()) return 0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        return CharacterService.stats(info.get().skills(), rec.traits()).total()[stat.ordinal()];
    }

    // ------------------------------------------------------------------ livraison

    /**
     * Les requêtes en attente cherchent leur souverain : s'il est à portée, le dignitaire vient le trouver
     * à pied ; sinon une notification annonce qu'il demande audience (on répond depuis la Table du Conseil).
     */
    public static void deliver(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        boolean moving = CrusadeConfig.SERVER.npcMovement.get();
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
            if (ruler == null || !realm.state().isActive()) continue;
            for (Request r : realm.court().requests()) {
                if (IN_AUDIENCE.contains(r.id())) continue;
                Optional<Entity> entity = MineColoniesBridge.citizenEntity(r.from());
                boolean near = entity.isPresent() && entity.get().level() == ruler.level()
                        && entity.get().distanceTo(ruler) <= AUDIENCE_RANGE;
                if (moving && near) {
                    IN_AUDIENCE.add(r.id());
                    DutyOrders.give(r.from(), new DutyOrders.Order(DutyOrders.Kind.AUDIENCE, ruler.level().dimension().location(),
                            ruler.blockPosition(), null, null, ruler.getUUID(), r.id(), now + AUDIENCE_TIMEOUT_TICKS));
                } else if (ANNOUNCED.add(r.id())) {
                    String name = CharacterService.nameOf(server, r.from());
                    CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.COUNCIL,
                            Component.translatable("crusadecraft.toast.audience.title"),
                            Component.translatable("crusadecraft.toast.audience.body", name, Component.translatable(r.type().titleKey()))));
                }
            }
            // Audiences terminées sans arrivée (souverain parti, chemin bloqué) : on retente plus tard.
            IN_AUDIENCE.removeIf(id -> {
                for (Request r : realm.court().requests()) {
                    if (r.id().equals(id)) {
                        DutyOrders.Order o = DutyOrders.get(r.from());
                        return o == null || o.kind() != DutyOrders.Kind.AUDIENCE;
                    }
                }
                return false;
            });
        }
    }

    /** Le dignitaire est arrivé devant le souverain : la requête s'ouvre. */
    static void onAudienceArrived(CitizenRef ref, DutyOrders.Order order) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        DutyOrders.cancel(ref);
        IN_AUDIENCE.remove(order.requestId());
        ServerPlayer ruler = server.getPlayerList().getPlayer(order.player());
        if (ruler == null) return;
        open(ruler, order.requestId());
    }

    /** Envoie une requête au souverain pour qu'il y réponde. */
    public static void open(ServerPlayer player, UUID requestId) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return;
        for (Request r : realm.get().court().requests()) {
            if (r.id().equals(requestId)) {
                CrusadeNetwork.toPlayer(player, new CourtPackets.OpenRequest(view(player.server, realm.get(), r)));
                return;
            }
        }
        player.displayClientMessage(Component.translatable("crusadecraft.request.gone"), true);
    }

    static RequestView view(MinecraftServer server, Realm realm, Request r) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(r.from());
        List<RequestView.ChoiceView> choices = new ArrayList<>();
        for (RequestType.Choice c : r.type().choices()) {
            int gold = goldCost(c), prestige = prestigeCost(c);
            boolean enabled = realm.gold() >= gold && realm.prestige() >= prestige;
            choices.add(new RequestView.ChoiceView(c, enabled, gold, prestige));
        }
        return new RequestView(r.id(), r.type(), info.map(CitizenInfo::name).orElse(CharacterService.nameOf(server, r.from())),
                info.map(CitizenInfo::female).orElse(false), realm.postOf(r.from()), r.place(), r.count(),
                info.map(CitizenInfo::entityId).orElse(-1), realm.feminineTitle(), choices);
    }

    public static List<CouncilView.RequestSummary> summaries(MinecraftServer server, Realm realm) {
        List<CouncilView.RequestSummary> out = new ArrayList<>();
        for (Request r : realm.court().requests()) {
            out.add(new CouncilView.RequestSummary(r.id(), r.type(), CharacterService.nameOf(server, r.from()), r.place(),
                    r.count(), r.createdDay()));
        }
        return out;
    }

    // ------------------------------------------------------------------ réponses

    public static int goldCost(RequestType.Choice c) {
        return switch (c) {
            case PAY_HEALER -> 5;
            case OPEN_GRANARIES, GRANT_FAVOUR -> 10;
            default -> 0;
        };
    }

    public static int prestigeCost(RequestType.Choice c) {
        return c == RequestType.Choice.PROMISE_HONOURS ? 10 : 0;
    }

    public static CouncilActions.Outcome answer(ServerPlayer player, UUID requestId, RequestType.Choice choice) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Request request = null;
        for (Request r : realm.court().requests()) if (r.id().equals(requestId)) request = r;
        if (request == null) return CouncilActions.Outcome.no("crusadecraft.request.gone");
        if (!request.type().choices().contains(choice)) return CouncilActions.Outcome.no("crusadecraft.request.bad_choice");
        int gold = goldCost(choice), prestige = prestigeCost(choice);
        if (realm.gold() < gold) return CouncilActions.Outcome.no("crusadecraft.request.no_gold", gold);
        if (realm.prestige() < prestige) return CouncilActions.Outcome.no("crusadecraft.request.no_prestige", prestige);

        long today = RealmService.currentDay(server);
        realm.setGold(realm.gold() - gold);
        realm.setPrestige(realm.prestige() - prestige);
        Optional<CharacterRecord> rec = data.character(request.from());
        OpinionReason reason = switch (choice) {
            case PROMISE, TO_ARMS -> OpinionReason.PROMESSE;
            case PAY_HEALER, OPEN_GRANARIES -> OpinionReason.REQUETE_SATISFAITE;
            case GRANT_FAVOUR, PROMISE_HONOURS -> OpinionReason.FAVEUR;
            case THREATEN -> OpinionReason.MENACE;
            case REFUSE -> OpinionReason.REFUS;
            case IGNORE, TIGHTEN_BELTS, PRAY -> OpinionReason.REQUETE_IGNOREE;
            case ACKNOWLEDGE -> null;
        };
        double mult = choice == RequestType.Choice.PRAY ? 0.5 : 1.0;
        if (reason != null) rec.ifPresent(r -> r.addModifier(OpinionModifier.of(reason, today, mult)));
        if (choice == RequestType.Choice.PRAY) realm.setPiety(realm.piety() + 1);
        if (choice == RequestType.Choice.THREATEN) realm.setDread(realm.dread() + 3);
        if (choice == RequestType.Choice.IGNORE && request.type() == RequestType.MAYOR_HOMELESS) realm.setDread(realm.dread() + 1);
        Request answered = request;
        rec.ifPresent(r -> r.remember(today, "crusadecraft.history.request", answered.type().titleKey(), choice.key()));
        realm.court().requests().remove(request);
        ANNOUNCED.remove(request.id());
        IN_AUDIENCE.remove(request.id());
        data.setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.request.answered",
                CharacterService.nameOf(server, request.from())));
    }

    private static void notifyRuler(MinecraftServer server, Realm realm, ToastPacket.Kind kind, Component title, Component body) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(kind, title, body));
    }
}
