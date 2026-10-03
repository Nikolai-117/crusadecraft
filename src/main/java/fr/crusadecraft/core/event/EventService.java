package fr.crusadecraft.core.event;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.ColonyReport;
import fr.crusadecraft.core.ai.AiRules;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.intrigue.Chronicle;
import fr.crusadecraft.core.intrigue.Faction;
import fr.crusadecraft.core.intrigue.IntrigueService;
import fr.crusadecraft.core.intrigue.IntrigueState;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Les événements à choix : ceux qui surgissent au hasard, ceux qu'appellent une faction, un complot, une révolte.
 * Un événement attend la décision du souverain quelques jours ; passé ce délai, l'option par défaut s'applique.
 */
public final class EventService {
    private static final Map<ResourceLocation, EventDef> EVENTS = new LinkedHashMap<>();

    private EventService() {}

    /** Remplacés à chaque chargement des datapacks. */
    public static void setEvents(Map<ResourceLocation, EventDef> events) {
        EVENTS.clear();
        EVENTS.putAll(events);
    }

    public static Map<ResourceLocation, EventDef> events() {
        return EVENTS;
    }

    public static Optional<EventDef> def(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Optional.empty() : Optional.ofNullable(EVENTS.get(rl));
    }

    /**
     * Un texte d'événement : une clé de traduction, ou du texte brut (un datapack peut écrire directement ses
     * phrases). Faute de traduction, le texte lui-même sert de modèle : les {@code %1$s} y sont remplis.
     */
    public static Component text(String s, Object... args) {
        Object[] parts = new Object[args.length];
        for (int i = 0; i < args.length; i++) parts[i] = args[i] instanceof String str ? arg(str) : args[i];
        return Component.translatable(s, parts);
    }

    /** Un argument d'événement : une clé de traduction (un genre de complot…) se traduit, un nom reste tel quel. */
    static Component arg(String s) {
        return s != null && s.startsWith("crusadecraft.") ? Component.translatable(s) : Component.literal(s == null ? "" : s);
    }

    // ------------------------------------------------------------------ contexte

    public static EventDef.EventContext context(MinecraftServer server, Realm realm) {
        int citizens = 0, n = 0;
        double happiness = 0;
        for (ColonyRef c : realm.allColonies()) {
            if (!MineColoniesBridge.exists(c)) continue;
            ColonyReport r = MineColoniesBridge.report(c);
            citizens += r.citizens();
            happiness += r.happiness();
            n++;
        }
        Set<Post> posts = EnumSet.noneOf(Post.class);
        CrusadeSavedData data = CrusadeSavedData.get(server);
        for (Map.Entry<Post, CitizenRef> e : realm.council().entrySet()) {
            if (data.character(e.getValue()).map(r -> r.status() == CharacterStatus.LIBRE && !r.deceased()).orElse(true)) posts.add(e.getKey());
        }
        if (!realm.mayors().isEmpty()) posts.add(Post.MAIRE);
        Map<Law, Integer> laws = new EnumMap<>(Law.class);
        for (Law law : Law.values()) laws.put(law, realm.laws().level(law));
        long today = RealmService.currentDay(server);
        return new EventDef.EventContext(citizens, realm.gold(), realm.prestige(), realm.dread(), realm.piety(), data.wars().atWar(realm.id()),
                posts, realm.rank(), today - realm.foundedDay(), n == 0 ? 5.0 : happiness / n, laws);
    }

    // ------------------------------------------------------------------ chaque jour

    /** Les événements échus prennent leur option par défaut ; puis, parfois, un nouvel événement surgit. */
    public static void onNewDay(MinecraftServer server, Realm realm, long day) {
        IntrigueState st = realm.intrigue();
        for (PendingEvent e : new ArrayList<>(st.pending())) {
            if (day >= e.expires()) resolve(server, realm, e, def(e.event()).map(EventDef::defaultOption).orElse(0), true);
        }
        if (!CrusadeConfig.SERVER.events.get() || !st.pending().isEmpty()) return;
        Random random = new Random(day * 92821L ^ realm.id().getLeastSignificantBits());
        if (random.nextInt(100) >= CrusadeConfig.SERVER.eventChance.get()) return;
        randomEvent(server, realm, random);
    }

    /** Tire un événement au hasard parmi ceux que permettent la situation et le temps écoulé depuis le dernier. */
    public static Optional<PendingEvent> randomEvent(MinecraftServer server, Realm realm, Random random) {
        return pick(server, realm, EventDef.RANDOM, random).map(d -> queue(server, realm, d, null, "", "", ""));
    }

    /** Un événement appelé par une situation (ultimatum, complot, révolte), avec ses personnages et ses lieux. */
    public static Optional<PendingEvent> trigger(MinecraftServer server, Realm realm, String trigger, CitizenRef subject, String subjectName,
                                                 String place, String ref) {
        Random random = new Random(RealmService.currentDay(server) * 31L + trigger.hashCode() + (ref == null ? 0 : ref.hashCode()));
        return pick(server, realm, trigger, random).map(d -> queue(server, realm, d, subject, subjectName, place, ref == null ? "" : ref));
    }

    private static Optional<EventDef> pick(MinecraftServer server, Realm realm, String trigger, Random random) {
        long today = RealmService.currentDay(server);
        EventDef.EventContext ctx = context(server, realm);
        List<EventDef> candidates = new ArrayList<>();
        int total = 0;
        for (EventDef d : EVENTS.values()) {
            if (!d.trigger().equals(trigger) || d.weight() <= 0 || !d.conditions().test(ctx)) continue;
            Long last = realm.intrigue().eventCooldowns().get(d.id().toString());
            if (EventDef.RANDOM.equals(trigger) && last != null && today - last < d.cooldown()) continue;
            candidates.add(d);
            total += d.weight();
        }
        if (candidates.isEmpty()) return Optional.empty();
        int roll = random.nextInt(total);
        for (EventDef d : candidates) {
            roll -= d.weight();
            if (roll < 0) return Optional.of(d);
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    static PendingEvent queue(MinecraftServer server, Realm realm, EventDef d, CitizenRef subject, String subjectName, String place, String ref) {
        IntrigueState st = realm.intrigue();
        long today = RealmService.currentDay(server);
        // Trop d'affaires en suspens : la plus ancienne se règle d'elle-même.
        while (st.pending().size() >= IntrigueState.PENDING_MAX) {
            PendingEvent old = st.pending().get(0);
            resolve(server, realm, old, def(old.event()).map(EventDef::defaultOption).orElse(0), true);
        }
        PendingEvent e = new PendingEvent(UUID.randomUUID(), d.id().toString(), today, today + d.expires(), subject,
                subjectName == null ? "" : subjectName, place == null ? "" : place, ref);
        st.pending().add(e);
        st.eventCooldowns().put(d.id().toString(), today);
        CrusadeSavedData.get(server).setDirty();
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) {
            CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.EVENT, title(d, e, realm),
                    Component.translatable("crusadecraft.toast.event.body", d.expires())));
        }
        return e;
    }

    static Component title(EventDef d, PendingEvent e, Realm realm) {
        return text(d.title(), e.subjectName(), e.place(), realm.name());
    }

    // ------------------------------------------------------------------ décision

    /** Le souverain choisit une option. */
    public static CouncilActions.Outcome answer(ServerPlayer player, UUID id, int option) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<PendingEvent> e = realm.intrigue().pendingEvent(id);
        if (e.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.event.gone");
        Optional<EventDef> d = def(e.get().event());
        if (d.isEmpty() || option < 0 || option >= d.get().options().size()) {
            realm.intrigue().pending().remove(e.get());
            return CouncilActions.Outcome.no("crusadecraft.event.gone");
        }
        if (!d.get().options().get(option).requires().test(context(server, realm))) return CouncilActions.Outcome.no("crusadecraft.event.requires");
        resolve(server, realm, e.get(), option, false);
        return CouncilActions.Outcome.ok(Component.translatable("crusadecraft.event.decided"));
    }

    /** Applique une option, retire l'événement et l'inscrit dans la chronique. */
    static void resolve(MinecraftServer server, Realm realm, PendingEvent e, int option, boolean automatic) {
        realm.intrigue().pending().remove(e);
        Optional<EventDef> found = def(e.event());
        if (found.isEmpty()) return;
        EventDef d = found.get();
        EventDef.Option opt = d.options().get(Math.max(0, Math.min(d.options().size() - 1, option)));
        long today = RealmService.currentDay(server);
        apply(server, realm, e, opt.effects(), today);
        Component title = title(d, e, realm);
        Component choice = text(opt.text(), e.subjectName(), e.place(), realm.name());
        if (opt.effects().chronicle() != null) {
            Chronicle.record(server, realm, opt.effects().chronicle(), arg(e.subjectName()), arg(e.place()), title, choice);
        } else {
            Chronicle.record(server, realm, automatic ? "crusadecraft.chronicle.event.auto" : "crusadecraft.chronicle.event", title, choice);
        }
        CrusadeSavedData.get(server).setDirty();
        CrusadeCraft.LOGGER.debug("CrusadeCraft : événement {} réglé (option {}{})", d.id(), option, automatic ? ", d'office" : "");
    }

    /** Les effets d'une option sur le royaume, la cour, le personnage en cause et la situation qui l'a appelée. */
    static void apply(MinecraftServer server, Realm realm, PendingEvent e, EventDef.Effects fx, long day) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        if (fx.gold() > 0) TreasuryService.credit(server, realm, LedgerCategory.EVENEMENTS, fx.gold());
        if (fx.gold() < 0) TreasuryService.charge(server, realm, LedgerCategory.EVENEMENTS, -fx.gold());
        realm.setPrestige(Math.max(0, realm.prestige() + fx.prestige()));
        realm.setPiety(Math.max(0, realm.piety() + fx.piety()));
        realm.setDread(Math.max(0, realm.dread() + fx.dread()));
        if (fx.threat() != 0) realm.setThreat(AiRules.addThreat(realm.threat(), fx.threat()));
        if (fx.army() != 0) {
            int men = WarService.men(realm);
            realm.setArmyMen(Math.max(0, men + men * fx.army() / 100));
        }
        if (fx.councilOpinion() != 0) {
            List<CitizenRef> court = new ArrayList<>(realm.council().values());
            court.addAll(realm.mayors().values());
            for (CitizenRef ref : court) opinion(server, ref, fx.councilOpinion(), day);
        }
        if (fx.post() != null && fx.postOpinion() != 0) {
            if (fx.post() == Post.MAIRE) for (CitizenRef m : realm.mayors().values()) opinion(server, m, fx.postOpinion(), day);
            else if (realm.council().get(fx.post()) != null) opinion(server, realm.council().get(fx.post()), fx.postOpinion(), day);
        }
        if (e.subject() != null) {
            if (fx.subjectOpinion() != 0) opinion(server, e.subject(), fx.subjectOpinion(), day);
            Optional<CharacterRecord> rec = data.character(e.subject());
            if (rec.isPresent() && fx.subjectStatus() != null && fx.subjectStatus() != CharacterStatus.LIBRE) {
                realm.vacate(e.subject());
                rec.get().setStatus(fx.subjectStatus(), fx.statusDays() > 0 ? day + fx.statusDays() : -1);
                rec.get().addModifier(OpinionModifier.of(fx.subjectStatus() == CharacterStatus.EXILE ? OpinionReason.EXILE : OpinionReason.EMPRISONNE, day, 1));
                rec.get().remember(day, fx.subjectStatus() == CharacterStatus.EXILE ? "crusadecraft.history.exiled" : "crusadecraft.history.imprisoned",
                        String.valueOf(fx.statusDays()));
                realm.intrigue().forget(e.subject());
            }
            if (rec.isPresent() && fx.subjectTrait() != null) rec.get().addTrait(fx.subjectTrait());
        }
        if (fx.lawDown() != null) {
            int level = realm.laws().level(fx.lawDown());
            if (level > 0) realm.laws().set(fx.lawDown(), level - 1, day);
        }
        if (fx.faction() != null) {
            Faction.Kind kind = Faction.Kind.byName(e.ref());
            if (kind != null) IntrigueService.resolveFaction(server, realm, kind, fx.faction(), day);
        }
        if (fx.plot() != null) {
            try {
                IntrigueService.endPlot(server, realm, UUID.fromString(e.ref()), fx.plot());
            } catch (IllegalArgumentException ignored) {
                // pas de complot derrière cet événement
            }
        }
        if (fx.revoltDays() > 0) {
            ColonyRef colony = parseColony(e.ref());
            if (colony != null && realm.holds(colony)) realm.intrigue().setRevolt(colony, day + fx.revoltDays());
        }
        data.setDirty();
    }

    private static void opinion(MinecraftServer server, CitizenRef ref, int value, long day) {
        CrusadeSavedData.get(server).character(ref).ifPresent(r -> r.addModifier(OpinionModifier.of(OpinionReason.EVENEMENT, day, value / 10.0)));
    }

    /** Une colonie écrite « dimension#numéro ». */
    public static ColonyRef parseColony(String s) {
        if (s == null) return null;
        int hash = s.lastIndexOf('#');
        if (hash <= 0) return null;
        ResourceLocation dim = ResourceLocation.tryParse(s.substring(0, hash));
        try {
            return dim == null ? null : new ColonyRef(dim, Integer.parseInt(s.substring(hash + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ vue

    /** L'événement tel que l'écran le montre : textes, icône, options avec leurs effets et ce qui manque pour les choisir. */
    public static Optional<EventView> view(MinecraftServer server, Realm realm, UUID id) {
        Optional<PendingEvent> e = id == null ? realm.intrigue().pending().stream().findFirst() : realm.intrigue().pendingEvent(id);
        if (e.isEmpty()) return Optional.empty();
        Optional<EventDef> d = def(e.get().event());
        if (d.isEmpty()) return Optional.empty();
        EventDef.EventContext ctx = context(server, realm);
        long today = RealmService.currentDay(server);
        List<EventView.OptionView> options = new ArrayList<>();
        for (EventDef.Option o : d.get().options()) {
            List<Component> lines = new ArrayList<>();
            for (EventDef.Line l : o.effects().lines()) {
                boolean plain = l.key().endsWith(".status") || l.key().endsWith(".revolt");
                String value = plain ? String.valueOf(l.value()) : (l.value() > 0 ? "+" : "") + l.value();
                lines.add(l.arg() == null ? Component.translatable(l.key(), value) : Component.translatable(l.key(), Component.translatable(l.arg()), value));
            }
            options.add(new EventView.OptionView(text(o.text(), e.get().subjectName(), e.get().place(), realm.name()), o.requires().test(ctx), lines));
        }
        return Optional.of(new EventView(e.get().id(), title(d.get(), e.get(), realm),
                text(d.get().text(), e.get().subjectName(), e.get().place(), realm.name()), d.get().icon(), options,
                Math.max(0, e.get().expires() - today), d.get().defaultOption(), realm.intrigue().pending().size()));
    }
}
