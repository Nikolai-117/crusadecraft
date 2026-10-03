package fr.crusadecraft.core.intrigue;

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
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.event.EventService;
import fr.crusadecraft.core.event.PendingEvent;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Ce qui se trame à la cour, une fois par jour du monde : les factions se font et se défont, posent leurs
 * ultimatums ; les complots avancent ou sont éventés ; des secrets naissent et se découvrent ; le peuple trop
 * taxé se soulève. Le Maître-espion voit (« Débusquer les complots » triple ses chances), le Maréchal apaise
 * (« Maintenir l'ordre »). Les décisions passent par des événements à choix.
 */
public final class IntrigueService {

    private IntrigueService() {}

    /** Un courtisan libre et ce qui le pousse. */
    public record Courtier(CitizenRef ref, Post post, String name, int opinion, List<Trait> traits, int intrigue, int martial) {
        boolean council() {
            return post != Post.MAIRE;
        }
    }

    /** Les conseillers et les maires en état de servir, avec leur opinion du souverain. */
    public static List<Courtier> courtiers(MinecraftServer server, Realm realm) {
        List<Courtier> out = new ArrayList<>();
        Map<CitizenRef, Post> all = new LinkedHashMap<>();
        for (Map.Entry<Post, CitizenRef> e : realm.council().entrySet()) all.put(e.getValue(), e.getKey());
        for (CitizenRef m : realm.mayors().values()) all.put(m, Post.MAIRE);
        for (Map.Entry<CitizenRef, Post> e : all.entrySet()) {
            Optional<CitizenInfo> info = MineColoniesBridge.citizen(e.getKey());
            if (info.isEmpty()) continue;
            CharacterRecord rec = CharacterService.record(server, info.get());
            if (rec.status() != CharacterStatus.LIBRE || rec.deceased()) continue;
            int[] stats = CharacterService.stats(info.get().skills(), rec.traits()).total();
            int opinion = Opinion.total(CharacterService.opinionLines(server, realm, rec));
            out.add(new Courtier(e.getKey(), e.getValue(), info.get().name(), opinion, rec.traits(), stats[Stat.INTRIGUE.ordinal()],
                    stats[Stat.MARTIAL.ordinal()]));
        }
        return out;
    }

    private static Courtier holder(List<Courtier> court, Post post) {
        for (Courtier c : court) if (c.post() == post) return c;
        return null;
    }

    /** L'Intrigue du Maître-espion (0 sans lui) et s'il débusque les complots. */
    static int spy(List<Courtier> court) {
        Courtier spy = holder(court, Post.MAITRE_ESPION);
        return spy == null ? 0 : Math.max(1, spy.intrigue());
    }

    static boolean hunting(Realm realm) {
        return realm.court().task(Post.MAITRE_ESPION) == CouncilTask.DEBUSQUER_COMPLOTS;
    }

    /** L'ordre que maintient le Maréchal (son Martial, s'il s'y emploie), et la stabilité d'un héritier désigné (10). */
    static int order(Realm realm, List<Courtier> court) {
        Courtier marshal = holder(court, Post.MARECHAL);
        int order = marshal != null && realm.court().task(Post.MARECHAL) == CouncilTask.MAINTENIR_ORDRE ? marshal.martial() : 0;
        return order + (realm.dynasty().heir() != null ? 10 : 0);
    }

    // ------------------------------------------------------------------ chaque jour

    public static void onNewDay(MinecraftServer server, Realm realm, long day) {
        Random random = new Random(day * 7349L ^ realm.id().getMostSignificantBits());
        List<Courtier> court = courtiers(server, realm);
        int spy = spy(court);
        boolean hunting = hunting(realm);
        int order = order(realm, court);
        if (CrusadeConfig.SERVER.factions.get()) factions(server, realm, court, spy, hunting, order, day, random);
        if (CrusadeConfig.SERVER.plots.get()) {
            plots(server, realm, court, spy, hunting, day, random);
            secrets(server, realm, court, spy, hunting, random);
        }
        if (CrusadeConfig.SERVER.revolts.get()) revolts(server, realm, order, day, random);
        CrusadeSavedData.get(server).setDirty();
    }

    // ------------------------------------------------------------------ factions

    static void factions(MinecraftServer server, Realm realm, List<Courtier> court, int spy, boolean hunting, int order, long day, Random random) {
        IntrigueState st = realm.intrigue();
        int courtWeight = 0;
        for (Courtier c : court) courtWeight += IntrigueRules.weight(c.council());
        for (Faction.Kind kind : Faction.Kind.values()) {
            Set<Courtier> members = new LinkedHashSet<>();
            ColonyRef colony = null;
            CitizenRef target = null;
            switch (kind) {
                case BAISSE_IMPOTS -> {
                    if (realm.laws().level(Law.FISCALITE) >= 1) {
                        for (Courtier c : court) if (willing(st, c, day)) members.add(c);
                    }
                }
                case INDEPENDANCE -> {
                    for (Courtier c : court) {
                        if (c.post() != Post.MAIRE || c.ref().colony().equals(realm.capital()) || !willing(st, c, day)) continue;
                        if (MineColoniesBridge.info(c.ref().colony()).happiness() >= 6) continue;
                        if (colony == null) colony = c.ref().colony();
                        if (c.ref().colony().equals(colony)) members.add(c);
                    }
                }
                case CHANGEMENT -> {
                    int worst = 0;
                    for (Courtier c : court) {
                        int score = IntrigueRules.hatedScore(c.traits());
                        if (c.council() && score > worst) {
                            worst = score;
                            target = c.ref();
                        }
                    }
                    if (target != null) for (Courtier c : court) if (!c.ref().equals(target) && willing(st, c, day)) members.add(c);
                }
            }
            Faction f = st.factions().get(kind);
            if (members.isEmpty()) {
                st.factions().remove(kind);
                continue;
            }
            if (f == null) {
                f = new Faction(kind, day);
                st.factions().put(kind, f);
            }
            f.members().clear();
            int weight = 0, intrigue = 0;
            for (Courtier c : members) {
                f.members().add(c.ref());
                weight += IntrigueRules.weight(c.council());
                intrigue += c.intrigue();
            }
            f.setColony(colony);
            f.setTarget(target);
            if (!f.discovered() && random.nextDouble() < IntrigueRules.discovery(spy, intrigue / members.size(), hunting)) {
                f.setDiscovered(true);
                notify(server, realm, Component.translatable("crusadecraft.toast.faction.title"),
                        Component.translatable("crusadecraft.toast.faction.body", Component.translatable(kind.key())));
            }
            int power = IntrigueRules.power(weight, courtWeight, realm.dread(), order);
            long since = f.lastUltimatumDay() == Long.MIN_VALUE ? Long.MAX_VALUE : day - f.lastUltimatumDay();
            if (IntrigueRules.ultimatum(power, members.size(), since)) {
                f.setLastUltimatumDay(day);
                f.setDiscovered(true);
                Courtier lead = members.iterator().next();
                EventService.trigger(server, realm, "faction_" + kind.name().toLowerCase(Locale.ROOT), lead.ref(), lead.name(),
                        place(server, f), kind.name());
            }
        }
    }

    /** Mécontent, et libre de se liguer (pas tenu par un chantage). */
    private static boolean willing(IntrigueState st, Courtier c, long day) {
        return IntrigueRules.discontent(c.opinion(), c.traits()) && !st.hooked(c.ref(), day);
    }

    /** Le lieu ou la personne qu'une faction vise : la province qui veut partir, le conseiller qu'on veut chasser. */
    static String place(MinecraftServer server, Faction f) {
        if (f.colony() != null) return MineColoniesBridge.info(f.colony()).name();
        if (f.target() != null) return CharacterService.nameOf(server, f.target());
        return "";
    }

    /** Puissance d'une faction, telle que l'affiche l'écran des intrigues. */
    public static int power(MinecraftServer server, Realm realm, Faction f) {
        List<Courtier> court = courtiers(server, realm);
        int total = 0, weight = 0;
        for (Courtier c : court) {
            total += IntrigueRules.weight(c.council());
            if (f.members().contains(c.ref())) weight += IntrigueRules.weight(c.council());
        }
        return IntrigueRules.power(weight, total, realm.dread(), order(realm, court));
    }

    /**
     * L'ultimatum tranché : céder ({@code concede}, la faction obtient ce qu'elle veut), apaiser ({@code appease},
     * on achète la paix sans rien céder, la faction se disperse) ou refuser ({@code refuse}, elle se soulève).
     */
    public static void resolveFaction(MinecraftServer server, Realm realm, Faction.Kind kind, String mode, long day) {
        IntrigueState st = realm.intrigue();
        Faction f = st.factions().remove(kind);
        if (f == null) return;
        boolean concede = "concede".equals(mode);
        boolean appease = "appease".equals(mode);
        CrusadeSavedData data = CrusadeSavedData.get(server);
        for (CitizenRef m : f.members()) {
            data.character(m).ifPresent(r -> r.addModifier(OpinionModifier.of(concede || appease ? OpinionReason.FACTION_CONCEDEE
                    : OpinionReason.FACTION_REFUSEE, day, appease ? 0.5 : 1)));
        }
        Component what = Component.translatable(kind.key());
        String placeName = place(server, f);
        Component where = Component.literal(placeName.isEmpty() ? "" : " (" + placeName + ")");
        if (appease) {
            Chronicle.record(server, realm, "crusadecraft.chronicle.faction.appeased", what, where);
            data.setDirty();
            return;
        }
        if (concede) {
            switch (kind) {
                case BAISSE_IMPOTS -> {
                    int level = realm.laws().level(Law.FISCALITE);
                    if (level > 0) realm.laws().set(Law.FISCALITE, level - 1, day);
                }
                case INDEPENDANCE -> {
                    if (f.colony() != null && !f.colony().equals(realm.capital())) {
                        CitizenRef mayor = realm.mayors().get(f.colony());
                        if (mayor != null) realm.vacate(mayor);
                        realm.removeProvince(f.colony());
                    }
                }
                case CHANGEMENT -> {
                    if (f.target() != null && realm.postOf(f.target()) != null) {
                        realm.vacate(f.target());
                        data.character(f.target()).ifPresent(r -> {
                            r.addModifier(OpinionModifier.of(OpinionReason.DESTITUE, day, 1));
                            r.remember(day, "crusadecraft.history.dismissed");
                        });
                    }
                }
            }
            Chronicle.record(server, realm, "crusadecraft.chronicle.faction.conceded", what, where);
        } else {
            switch (kind) {
                case BAISSE_IMPOTS -> {
                    for (ColonyRef c : realm.allColonies()) st.setRevolt(c, day + 3);
                }
                case INDEPENDANCE -> {
                    if (f.colony() != null) st.setRevolt(f.colony(), day + 10);
                }
                case CHANGEMENT -> {
                    // Le refus coûte en prestige et en opinion (effets de l'événement) ; rien d'autre ne bouge.
                }
            }
            Chronicle.record(server, realm, "crusadecraft.chronicle.faction.refused", what, where);
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ complots

    static void plots(MinecraftServer server, Realm realm, List<Courtier> court, int spy, boolean hunting, long day, Random random) {
        IntrigueState st = realm.intrigue();
        Map<CitizenRef, Courtier> byRef = new LinkedHashMap<>();
        for (Courtier c : court) byRef.put(c.ref(), c);
        st.plots().removeIf(p -> !byRef.containsKey(p.plotter()) && !pendingFor(st, p.id().toString()));
        for (Plot p : new ArrayList<>(st.plots())) {
            Courtier c = byRef.get(p.plotter());
            if (p.discovered() || c == null) continue;
            p.setProgress(p.progress() + IntrigueRules.plotProgress(c.intrigue()));
            if (random.nextDouble() < IntrigueRules.discovery(spy, c.intrigue(), hunting)) {
                p.setDiscovered(true);
                EventService.trigger(server, realm, "plot_discovered", c.ref(), c.name(), p.kind().key(), p.id().toString());
                continue;
            }
            if (p.progress() >= 100) succeed(server, realm, p, c);
        }
        for (Courtier c : court) {
            if (st.plots().stream().anyMatch(p -> p.plotter().equals(c.ref()))) continue;
            if (IntrigueRules.startsPlot(c.opinion(), c.traits(), random.nextDouble())) {
                st.plots().add(new Plot(UUID.randomUUID(), c.ref(), IntrigueRules.plotKind(c.traits(), random.nextDouble()), day));
            }
        }
    }

    private static boolean pendingFor(IntrigueState st, String ref) {
        for (PendingEvent e : st.pending()) if (ref.equals(e.ref())) return true;
        return false;
    }

    /** Le complot a abouti sans être éventé : le coup porte, et l'on apprend trop tard qui l'a porté. */
    static void succeed(MinecraftServer server, Realm realm, Plot p, Courtier c) {
        realm.intrigue().plots().remove(p);
        switch (p.kind()) {
            case DETOURNEMENT -> TreasuryService.charge(server, realm, LedgerCategory.PILLAGE, Math.round(Math.max(0, realm.gold()) * 0.15));
            case SABOTAGE -> realm.setArmyMen(Math.max(0, WarService.men(realm) * 7 / 10));
            case DIFFAMATION -> realm.setPrestige(Math.max(0, realm.prestige() - 40));
        }
        EventService.trigger(server, realm, "plot_succeeded", c.ref(), c.name(), p.kind().key(), p.id().toString());
        Chronicle.record(server, realm, "crusadecraft.chronicle.plot.succeeded", Component.literal(c.name()), Component.translatable(p.kind().key()));
    }

    /** Le complot éventé est réglé (emprisonnement, exil, pardon) : il disparaît. */
    public static void endPlot(MinecraftServer server, Realm realm, UUID id, String how) {
        long today = RealmService.currentDay(server);
        realm.intrigue().plot(id).ifPresent(p -> {
            realm.intrigue().plots().remove(p);
            if ("forgive".equals(how)) {
                CrusadeSavedData.get(server).character(p.plotter()).ifPresent(r -> r.addModifier(OpinionModifier.of(OpinionReason.COMPLOT_PARDONNE, today, 1)));
            }
        });
    }

    // ------------------------------------------------------------------ secrets

    static void secrets(MinecraftServer server, Realm realm, List<Courtier> court, int spy, boolean hunting, Random random) {
        IntrigueState st = realm.intrigue();
        Map<CitizenRef, Courtier> byRef = new LinkedHashMap<>();
        for (Courtier c : court) byRef.put(c.ref(), c);
        st.secrets().removeIf(s -> !byRef.containsKey(s.holder()));
        for (Courtier c : court) {
            if (st.secrets().stream().anyMatch(s -> s.holder().equals(c.ref()))) continue;
            Secret.Kind kind = IntrigueRules.secretFor(c.traits(), random.nextDouble());
            if (kind != null) st.secrets().add(new Secret(UUID.randomUUID(), c.ref(), kind));
        }
        for (Secret s : st.secrets()) {
            if (s.known()) continue;
            Courtier c = byRef.get(s.holder());
            if (random.nextDouble() < IntrigueRules.discovery(spy, c.intrigue(), hunting) * 1.5) {
                s.setKnown(true);
                notify(server, realm, Component.translatable("crusadecraft.toast.secret.title"),
                        Component.translatable("crusadecraft.toast.secret.body", c.name(), Component.translatable(s.kind().key())));
            }
        }
    }

    /** Le chantage : le courtisan se tient tranquille deux mois (ni faction ni complot), mais il ne l'oublie pas. */
    public static CouncilActions.Outcome blackmail(ServerPlayer player, UUID secretId) {
        return useSecret(player, secretId, true);
    }

    /** La révélation : justice est faite aux yeux de tous, et le courtisan en garde une rancune tenace. */
    public static CouncilActions.Outcome expose(ServerPlayer player, UUID secretId) {
        return useSecret(player, secretId, false);
    }

    private static CouncilActions.Outcome useSecret(ServerPlayer player, UUID secretId, boolean blackmail) {
        MinecraftServer server = player.server;
        Optional<Realm> found = RealmService.realmOf(server, player.getUUID());
        if (found.isEmpty()) return CouncilActions.Outcome.no("crusadecraft.realm.none");
        Realm realm = found.get();
        Optional<Secret> s = realm.intrigue().secret(secretId);
        if (s.isEmpty() || !s.get().known()) return CouncilActions.Outcome.no("crusadecraft.intrigue.secret_gone");
        long today = RealmService.currentDay(server);
        Secret secret = s.get();
        String name = CharacterService.nameOf(server, secret.holder());
        Optional<CharacterRecord> rec = CrusadeSavedData.get(server).character(secret.holder());
        realm.intrigue().secrets().remove(secret);
        if (blackmail) {
            realm.intrigue().hook(secret.holder(), today + 60);
            for (Faction f : realm.intrigue().factions().values()) f.members().remove(secret.holder());
            realm.intrigue().plots().removeIf(p -> p.plotter().equals(secret.holder()));
            rec.ifPresent(r -> r.addModifier(OpinionModifier.of(OpinionReason.CHANTAGE, today, 1)));
            realm.setDread(realm.dread() + 3);
            Chronicle.record(server, realm, "crusadecraft.chronicle.secret.blackmail", Component.literal(name), Component.translatable(secret.kind().key()));
        } else {
            rec.ifPresent(r -> {
                r.addModifier(OpinionModifier.of(OpinionReason.SECRET_REVELE, today, 1));
                if (secret.kind() == Secret.Kind.CORRUPTION) r.addTrait(Trait.CORROMPU);
            });
            realm.setPrestige(realm.prestige() + 5);
            if (secret.kind() == Secret.Kind.HERESIE) realm.setPiety(realm.piety() + 10);
            Chronicle.record(server, realm, "crusadecraft.chronicle.secret.exposed", Component.literal(name), Component.translatable(secret.kind().key()));
        }
        CrusadeSavedData.get(server).setDirty();
        return CouncilActions.Outcome.ok(Component.translatable(blackmail ? "crusadecraft.intrigue.blackmailed" : "crusadecraft.intrigue.exposed", name));
    }

    // ------------------------------------------------------------------ révoltes

    static void revolts(MinecraftServer server, Realm realm, int order, long day, Random random) {
        IntrigueState st = realm.intrigue();
        int fiscal = realm.laws().level(Law.FISCALITE);
        for (ColonyRef colony : realm.allColonies()) {
            if (st.inRevolt(colony, day) || pendingFor(st, colony.toString()) || !MineColoniesBridge.exists(colony)) continue;
            ColonyReport r = MineColoniesBridge.report(colony);
            if (random.nextDouble() < IntrigueRules.revoltChance(r.happiness(), fiscal, order)) {
                EventService.trigger(server, realm, "revolt", null, "", r.name(), colony.toString());
            }
        }
    }

    private static void notify(MinecraftServer server, Realm realm, Component title, Component body) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.COUNCIL, title, body));
    }

    // ------------------------------------------------------------------ vue

    public static IntrigueView view(MinecraftServer server, Realm realm) {
        long today = RealmService.currentDay(server);
        List<Courtier> court = courtiers(server, realm);
        Courtier spy = holder(court, Post.MAITRE_ESPION);
        Courtier marshal = holder(court, Post.MARECHAL);
        IntrigueState st = realm.intrigue();
        List<IntrigueView.FactionLine> factions = new ArrayList<>();
        int hidden = 0;
        for (Faction f : st.factions().values()) {
            if (!f.discovered()) {
                hidden++;
                continue;
            }
            List<String> names = new ArrayList<>();
            for (CitizenRef m : f.members()) names.add(CharacterService.nameOf(server, m));
            factions.add(new IntrigueView.FactionLine(f.kind(), names, power(server, realm, f), place(server, f)));
        }
        List<IntrigueView.PlotLine> plots = new ArrayList<>();
        for (Plot p : st.plots()) if (p.discovered()) plots.add(new IntrigueView.PlotLine(CharacterService.nameOf(server, p.plotter()), p.kind(), p.progress()));
        List<IntrigueView.SecretLine> secrets = new ArrayList<>();
        for (Secret s : st.secrets()) if (s.known()) secrets.add(new IntrigueView.SecretLine(s.id(), CharacterService.nameOf(server, s.holder()), s.kind()));
        List<String> revolts = new ArrayList<>();
        for (ColonyRef c : realm.allColonies()) if (st.inRevolt(c, today)) revolts.add(MineColoniesBridge.info(c).name());
        return new IntrigueView(spy == null ? "" : spy.name(), hunting(realm), marshal != null && realm.court().task(Post.MARECHAL) == CouncilTask.MAINTENIR_ORDRE, factions,
                spy == null ? 0 : hidden, plots,
                secrets, revolts, st.pending().size(), realm.dread());
    }
}
