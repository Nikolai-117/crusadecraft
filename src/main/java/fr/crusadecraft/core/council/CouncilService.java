package fr.crusadecraft.core.council;

import fr.crusadecraft.compat.minecolonies.DutyAI;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.character.CitizenInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.CitizenSkill;
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
import fr.crusadecraft.network.packet.ToastPacket;
import fr.crusadecraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Le conseil royal au quotidien : tâches confiées et leurs effets chaque jour, réunions à la table,
 * heures de bureau des maires.
 */
public final class CouncilService {
    /** Rayon autour de la table où l'on cherche les sièges du conseil. */
    private static final int SEAT_RADIUS = 8;

    // État de session (jamais enregistré) : présents à la réunion en cours, sièges trouvés.
    private static final Map<UUID, Set<CitizenRef>> ATTENDEES = new HashMap<>();
    private static final Set<UUID> MEETING_ACTIVE = new HashSet<>();
    private static final Map<UUID, List<BlockPos>> SEATS = new HashMap<>();
    private static final Map<UUID, Long> SEATS_SCANNED = new HashMap<>();

    private CouncilService() {}

    public static void init() {
        DutyAI.onArrival((ref, order) -> {
            if (order.kind() == DutyOrders.Kind.MEETING) {
                ATTENDEES.computeIfAbsent(realmIdOf(ref), k -> new HashSet<>()).add(ref);
            } else if (order.kind() == DutyOrders.Kind.AUDIENCE) {
                RequestService.onAudienceArrived(ref, order);
            }
        });
    }

    private static UUID realmIdOf(CitizenRef ref) {
        // L'identifiant du royaume est lu au moment de l'arrivée ; un citoyen sans poste n'a rien à faire là.
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return new UUID(0, 0);
        return CrusadeSavedData.get(server).realmEmploying(ref).map(Realm::id).orElse(new UUID(0, 0));
    }

    public static void onServerStopped() {
        ATTENDEES.clear();
        MEETING_ACTIVE.clear();
        SEATS.clear();
        SEATS_SCANNED.clear();
        DutyOrders.clear();
    }

    // ------------------------------------------------------------------ horloge

    /** Heure du jour (0 à 23) en ticks du cycle Minecraft, où 0 tick = 6 h. */
    static long hourToTicks(int hour) {
        return (long) ((hour - 6 + 24) % 24) * 1000L;
    }

    static long minutesToTicks(int minutes) {
        return minutes * 1000L / 60L;
    }

    /** Ticks écoulés depuis le début d'une fenêtre, ou -1 si l'on est en dehors. */
    static long elapsedInWindow(long dayTime, long start, long duration) {
        long elapsed = Math.floorMod(dayTime - start, 24000L);
        return elapsed < duration ? elapsed : -1;
    }

    // ------------------------------------------------------------------ boucle

    /** Appelé toutes les deux secondes environ. */
    public static void tick(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        long dayTime = overworld.getDayTime() % 24000L;
        long now = overworld.getGameTime();
        boolean moving = CrusadeConfig.SERVER.npcMovement.get();

        long meetingStart = hourToTicks(CrusadeConfig.SERVER.meetingHour.get());
        long meetingLength = minutesToTicks(CrusadeConfig.SERVER.meetingMinutes.get());
        long officeStart = hourToTicks(CrusadeConfig.SERVER.officeHour.get());
        long officeLength = minutesToTicks(CrusadeConfig.SERVER.officeMinutes.get());

        for (Realm realm : data.realms()) {
            if (!realm.state().isActive()) continue;
            CourtState court = realm.court();

            // Passage des jours (rattrapage limité à trois jours après une longue absence).
            if (court.lastProcessedDay() == CourtState.NEVER) {
                court.setLastProcessedDay(today);
                data.setDirty();
            } else {
                int guard = 0;
                while (court.lastProcessedDay() < today && guard++ < 3) {
                    long day = court.lastProcessedDay() + 1;
                    onNewDay(server, realm, day);
                    court.setLastProcessedDay(day);
                    data.setDirty();
                }
                if (court.lastProcessedDay() < today) court.setLastProcessedDay(today);
            }

            // Réunion du conseil
            long elapsed = elapsedInWindow(dayTime, meetingStart, meetingLength);
            boolean convened = now < court.conveneUntilTick();
            if (court.hasTable() && (elapsed >= 0 || convened)) {
                long until = convened ? court.conveneUntilTick() : now + (meetingLength - elapsed);
                if (MEETING_ACTIVE.add(realm.id())) {
                    ATTENDEES.remove(realm.id());
                    SEATS_SCANNED.remove(realm.id());
                }
                if (moving) giveMeetingOrders(server, realm, until);
                else ATTENDEES.computeIfAbsent(realm.id(), k -> new HashSet<>()).addAll(realm.council().values());
            } else if (MEETING_ACTIVE.remove(realm.id())) {
                endMeeting(server, realm, today);
            }

            // Heures de bureau des maires
            long officeElapsed = elapsedInWindow(dayTime, officeStart, officeLength);
            for (Map.Entry<ColonyRef, BlockPos> desk : court.mayorDesks().entrySet()) {
                CitizenRef mayor = realm.mayors().get(desk.getKey());
                if (mayor == null) continue;
                DutyOrders.Order current = DutyOrders.get(mayor);
                if (moving && officeElapsed >= 0 && free(data, mayor)) {
                    DutyOrders.give(mayor, new DutyOrders.Order(DutyOrders.Kind.OFFICE, desk.getKey().dimension(),
                            desk.getValue(), null, desk.getValue(), null, null, now + (officeLength - officeElapsed)));
                } else if (current != null && current.kind() == DutyOrders.Kind.OFFICE) {
                    DutyOrders.cancel(mayor);
                }
            }
        }
    }

    private static boolean free(CrusadeSavedData data, CitizenRef ref) {
        return data.character(ref).map(r -> r.status() == CharacterStatus.LIBRE && !r.deceased()).orElse(true);
    }

    private static void giveMeetingOrders(MinecraftServer server, Realm realm, long until) {
        CourtState court = realm.court();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        List<BlockPos> seats = seatsNear(server, realm);
        BlockPos table = court.tablePos();
        int i = 0;
        for (CitizenRef member : realm.council().values()) {
            if (!free(data, member)) continue;
            BlockPos seat = i < seats.size() ? seats.get(i) : null;
            BlockPos target = seat != null ? seat : besideTable(table, i);
            DutyOrders.give(member, new DutyOrders.Order(DutyOrders.Kind.MEETING, court.tableDimension(), target, seat,
                    table, null, null, until));
            i++;
        }
    }

    /** Places autour de la table, quand il n'y a pas assez de sièges. */
    private static BlockPos besideTable(BlockPos table, int index) {
        int[][] around = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
        int[] d = around[index % around.length];
        return table.offset(d[0], 0, d[1]);
    }

    private static List<BlockPos> seatsNear(MinecraftServer server, Realm realm) {
        CourtState court = realm.court();
        Long scanned = SEATS_SCANNED.get(realm.id());
        long now = server.getTickCount();
        if (scanned != null && now - scanned < 1200 && SEATS.containsKey(realm.id())) return SEATS.get(realm.id());
        List<BlockPos> found = new ArrayList<>();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, court.tableDimension()));
        if (level != null && level.isLoaded(court.tablePos())) {
            BlockPos t = court.tablePos();
            for (BlockPos p : BlockPos.betweenClosed(t.offset(-SEAT_RADIUS, -2, -SEAT_RADIUS), t.offset(SEAT_RADIUS, 2, SEAT_RADIUS))) {
                if (level.isLoaded(p) && level.getBlockState(p).is(ModBlocks.COUNCIL_SEAT.get())) found.add(p.immutable());
            }
            found.sort((a, b) -> Double.compare(a.distSqr(t), b.distSqr(t)));
        }
        SEATS.put(realm.id(), found);
        SEATS_SCANNED.put(realm.id(), now);
        return found;
    }

    private static void endMeeting(MinecraftServer server, Realm realm, long today) {
        Set<CitizenRef> present = ATTENDEES.remove(realm.id());
        for (CitizenRef member : realm.council().values()) {
            DutyOrders.Order o = DutyOrders.get(member);
            if (o != null && o.kind() == DutyOrders.Kind.MEETING) DutyOrders.cancel(member);
        }
        if (present == null || present.isEmpty()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        realm.court().setMeetingBonusDay(today);
        for (CitizenRef ref : present) {
            data.character(ref).ifPresent(rec -> rec.addModifier(OpinionModifier.of(OpinionReason.REUNION, today, 1)));
        }
        data.setDirty();
        notifyRuler(server, realm, ToastPacket.Kind.COUNCIL, Component.translatable("crusadecraft.toast.meeting.title"),
                Component.translatable("crusadecraft.toast.meeting.body", present.size(), realm.council().size()));
    }

    // ------------------------------------------------------------------ chaque jour

    static void onNewDay(MinecraftServer server, Realm realm, long day) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        CourtState court = realm.court();
        double bonus = court.meetingBonusDay() != CourtState.NEVER && court.meetingBonusDay() >= day - 1 ? 1.25 : 1.0;
        for (Map.Entry<Post, CitizenRef> seat : new ArrayList<>(realm.council().entrySet())) {
            Post post = seat.getKey();
            CitizenRef ref = seat.getValue();
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
            if (info.isEmpty()) continue;
            CharacterRecord rec = CharacterService.record(server, info.get());
            if (rec.status() != CharacterStatus.LIBRE) continue;
            int skill = CharacterService.stats(info.get().skills(), rec.traits()).total()[post.stat().ordinal()];
            CouncilTask task = court.task(post);
            if (task != null && task.available()) applyTask(server, realm, task, ref, skill, bonus, day);
            grantRoleXp(ref, post.stat());
            int conflict = task == null ? 0 : stressConflict(task, rec.traits());
            rec.addStress(conflict > 0 ? conflict : -5);
        }
        for (CitizenRef mayor : realm.mayors().values()) {
            grantRoleXp(mayor, Post.MAIRE.stat());
            data.character(mayor).ifPresent(rec -> rec.addStress(-5));
        }
        data.setDirty();
    }

    static void applyTask(MinecraftServer server, Realm realm, CouncilTask task, CitizenRef holder, int skill,
                          double bonus, long day) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        switch (task) {
            case GERER_LA_COUR -> {
                double value = (2 + skill / 4.0) * bonus;
                List<CitizenRef> court = new ArrayList<>(realm.council().values());
                court.addAll(realm.mayors().values());
                for (CitizenRef ref : court) {
                    if (ref.equals(holder)) continue;
                    data.character(ref).ifPresent(rec -> rec.addModifier(
                            OpinionModifier.of(OpinionReason.COUR, day, value / OpinionReason.COUR.value())));
                }
            }
            case ENTRAINER_TROUPES -> {
                double xp = (1 + skill / 4.0) * bonus;
                for (ColonyRef colony : realm.allColonies()) {
                    for (CitizenRef guard : MineColoniesBridge.guards(colony)) {
                        MineColoniesBridge.addSkillXp(guard, CitizenSkill.Strength, xp / 2);
                        MineColoniesBridge.addSkillXp(guard, CitizenSkill.Athletics, xp / 2);
                        MineColoniesBridge.addSkillXp(guard, CitizenSkill.Agility, xp / 2);
                        MineColoniesBridge.addSkillXp(guard, CitizenSkill.Dexterity, xp / 2);
                    }
                }
            }
            case GERER_LE_DOMAINE -> {
                int population = 0;
                for (ColonyRef colony : realm.allColonies()) population += MineColoniesBridge.info(colony).citizens();
                long income = Math.round(population * (0.2 + skill / 50.0) * bonus);
                realm.setGold(realm.gold() + income);
            }
            case LEGITIMITE -> realm.setPiety(realm.piety() + (int) Math.round((1 + skill / 5.0) * bonus));
            default -> {
                // SURVEILLER_LES_TERRES et BONHEUR_DU_PEUPLE agissent en continu (requêtes, bonheur).
            }
        }
    }

    /** Un poste fait progresser les compétences de son aptitude, selon les poids de la règle en vigueur. */
    private static void grantRoleXp(CitizenRef ref, Stat stat) {
        if (!CrusadeConfig.SERVER.roleXp.get()) return;
        double perDay = CrusadeConfig.SERVER.roleXpPerDay.get();
        CharacterView.StatSource src = CharacterService.sources().get(stat.ordinal());
        for (int i = 0; i < src.skills().length; i++) {
            MineColoniesBridge.addSkillXp(ref, CitizenSkill.values()[src.skills()[i]], perDay * src.weights()[i]);
        }
    }

    /** Agir contre sa nature coûte : un espion honnête, un maréchal lâche… */
    static int stressConflict(CouncilTask task, List<Trait> traits) {
        int stress = 0;
        if (task.post() == Post.MAITRE_ESPION && traits.contains(Trait.HONNETE)) stress += 10;
        if (task == CouncilTask.ENTRAINER_TROUPES && traits.contains(Trait.LACHE)) stress += 8;
        if (task.post() == Post.CHAPELAIN && traits.contains(Trait.CYNIQUE)) stress += 8;
        if (task.post() == Post.INTENDANT && traits.contains(Trait.PARESSEUX)) stress += 5;
        if (task == CouncilTask.GERER_LA_COUR && traits.contains(Trait.ARROGANT)) stress += 5;
        return stress;
    }

    // ------------------------------------------------------------------ bonheur de la couronne

    /** Facteur de bonheur que le chapelain apporte au peuple (1.0 = aucun effet). */
    public static double crownHappinessFactor(MinecraftServer server, Realm realm) {
        if (!CrusadeConfig.SERVER.crownHappiness.get()) return 1.0;
        CitizenRef chaplain = realm.council().get(Post.CHAPELAIN);
        if (chaplain == null || realm.court().task(Post.CHAPELAIN) != CouncilTask.BONHEUR_DU_PEUPLE) return 1.0;
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(chaplain);
        if (info.isEmpty()) return 1.0;
        CharacterRecord rec = CharacterService.record(server, info.get());
        if (rec.status() != CharacterStatus.LIBRE) return 1.0;
        int learning = CharacterService.stats(info.get().skills(), rec.traits()).total()[Stat.ERUDITION.ordinal()];
        return Math.min(1.5, 1.1 + learning / 100.0);
    }

    /** Réapplique le facteur périodiquement : MineColonies ne le recharge pas au redémarrage. */
    public static void applyHappiness(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            double factor = realm.state().isActive() ? crownHappinessFactor(server, realm) : 1.0;
            for (ColonyRef colony : realm.allColonies()) MineColoniesBridge.applyCrownHappiness(colony, factor);
        }
    }

    // ------------------------------------------------------------------ actions du souverain

    public static CouncilActions.Outcome setTask(ServerPlayer player, Post post, CouncilTask task) {
        Optional<Realm> realm = RealmService.realmOf(player.server, player.getUUID());
        if (realm.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        if (task == null || task.post() != post || !post.isCouncil()) return CouncilActions.Outcome.no("crusadecraft.council.bad_task");
        if (!task.available()) return CouncilActions.Outcome.no("crusadecraft.council.task_unavailable", Component.translatable(task.key()));
        realm.get().court().setTask(post, task);
        CrusadeSavedData.get(player.server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.council.task_set",
                Component.translatable(post.key(false)), Component.translatable(task.key())));
    }

    public static CouncilActions.Outcome convene(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        if (!realm.court().hasTable()) return CouncilActions.Outcome.no("crusadecraft.council.no_table");
        long today = RealmService.currentDay(server);
        if (realm.court().lastConveneDay() == today) return CouncilActions.Outcome.no("crusadecraft.council.already_convened");
        if (realm.council().isEmpty()) return CouncilActions.Outcome.no("crusadecraft.council.nobody");
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        long now = overworld == null ? 0 : overworld.getGameTime();
        realm.court().convene(now + minutesToTicks(CrusadeConfig.SERVER.meetingMinutes.get()), today);
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.council.convened"));
    }

    // ------------------------------------------------------------------ vue

    public static CouncilView view(MinecraftServer server, Realm realm, Post focus) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        CourtState court = realm.court();
        List<CouncilView.Seat> seats = new ArrayList<>();
        for (Post post : Post.values()) {
            if (!post.isCouncil()) continue;
            CitizenRef ref = realm.council().get(post);
            String name = "";
            boolean female = false;
            int stat = 0, opinion = 0;
            CharacterStatus status = CharacterStatus.LIBRE;
            if (ref != null) {
                Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
                if (info.isPresent()) {
                    CharacterRecord rec = CharacterService.record(server, info.get());
                    name = info.get().name();
                    female = info.get().female();
                    stat = CharacterService.stats(info.get().skills(), rec.traits()).total()[post.stat().ordinal()];
                    opinion = Opinion.total(CharacterService.opinionLines(server, realm, rec));
                    status = rec.status();
                } else {
                    name = CharacterService.nameOf(server, ref);
                }
            }
            seats.add(new CouncilView.Seat(post, ref, name, female, stat, opinion, status, court.task(post)));
        }
        String tableColony = "";
        if (court.hasTable()) {
            Optional<ColonyRef> colony = colonyAt(server, court.tableDimension(), court.tablePos());
            tableColony = colony.map(c -> MineColoniesBridge.info(c).name()).orElse("");
        }
        long today = RealmService.currentDay(server);
        return new CouncilView(realm.name(), court.hasTable(), tableColony, CrusadeConfig.SERVER.meetingHour.get(),
                court.lastConveneDay() != today && court.hasTable() && !realm.council().isEmpty(),
                court.meetingBonusDay() != CourtState.NEVER && court.meetingBonusDay() >= today - 1, seats, RequestService.summaries(server, realm), focus,
                realm.gold(), realm.piety(), crownHappinessFactor(server, realm));
    }

    private static Optional<ColonyRef> colonyAt(MinecraftServer server, net.minecraft.resources.ResourceLocation dim, BlockPos pos) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        return level == null ? Optional.empty() : MineColoniesBridge.colonyAt(level, pos);
    }

    private static void notifyRuler(MinecraftServer server, Realm realm, ToastPacket.Kind kind, Component title, Component body) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(kind, title, body));
    }

    /** Colonies du royaume, pour les écrans (nom affiché). */
    static String colonyName(ColonyRef ref) {
        ColonyInfo info = MineColoniesBridge.info(ref);
        return info.isMissing() ? ref.toString() : info.name();
    }
}
