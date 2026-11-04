package fr.crusadecraft.core.nation;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ai.AiBrain;
import fr.crusadecraft.core.ai.AiRules;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.lineage.Community;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldState;
import fr.crusadecraft.core.world.ZoneService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Les nations (P41) : des royaumes qui existent dès le départ, écrits dans {@link NationBook}, sans terre tant qu'aucune
 * de leurs villes n'est posée. Une ville rangée dans le dossier d'une nation lui revient, où qu'on la pose.
 *
 * <p>Une nation de joueur reçoit un souverain, le premier qui y pose une ville : l'ordinateur ne décide plus pour elle.
 * Les autres royaumes lui écrivent (offres, présents, déclarations) et il répond depuis l'écran de la nation. Les
 * nations de l'ordinateur vivent comme tout royaume IA, et renouvellent leurs traités d'amitié.
 */
public final class NationService {
    /** Les jours qu'une offre attend sa réponse. */
    static final int OFFER_DAYS = 7;
    /** Au plus, les lettres qu'un souverain garde. */
    static final int MAIL_MAX = 30;
    /** Un même royaume n'écrit pas plus d'une fois en tant de jours. */
    static final int LETTER_GAP = 3;
    /** Les présents qu'on peut faire depuis l'écran. */
    public static final int GIFT = 50;

    public static final int OP_PROPOSE = 0, OP_GIFT = 1, OP_INSULT = 2, OP_BREAK = 3, OP_WAR = 4, OP_ACCEPT = 5, OP_REFUSE = 6, OP_READ = 7;

    private NationService() {}

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    // ------------------------------------------------------------------ les nations existent

    /** Au démarrage et à chaque relecture : les nations manquantes naissent (une seule fois), et les villes déjà posées les rejoignent. */
    public static void ensure(MinecraftServer server) {
        CrusadeSavedData data = data(server);
        WorldState world = data.world();
        List<NationDef> fresh = new ArrayList<>();
        for (NationDef def : NationBook.all()) {
            if (world.nationBorn(def.key())) continue;
            if (realm(server, def.key()).isEmpty()) create(server, def);
            world.markNationBorn(def.key());
            fresh.add(def);
        }
        for (NationDef def : fresh) applyTies(server, def);
        for (NationDef def : NationBook.all()) migrate(server, def);
        if (!fresh.isEmpty()) data.setDirty();
    }

    private static AiRealm create(MinecraftServer server, NationDef def) {
        AiRealm realm = WorldEditService.addLandlessRealm(server, server.overworld(), def.name(), def.ruler() == null ? "" : def.ruler().name(), def.rank(),
                def.color(), def.faith(), def.personality());
        realm.setNation(def.key());
        realm.setHouse(def.house());
        if (def.ruler() != null) {
            AiCharacter r = realm.ruler();
            realm.setRuler(new AiCharacter(def.ruler().name(), def.ruler().female(), def.ruler().age(), r.stats(), r.traits()));
        }
        long today = RealmService.currentDay(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) realm.discover(p.getUUID(), today);
        CrusadeCraft.LOGGER.info("CrusadeCraft : la nation {} existe désormais", def.name());
        return realm;
    }

    /** Ses liens avec les autres nations, signés à sa naissance. */
    private static void applyTies(MinecraftServer server, NationDef def) {
        Optional<AiRealm> me = realm(server, def.key());
        if (me.isEmpty()) return;
        long today = RealmService.currentDay(server);
        for (NationDef.Tie tie : def.ties()) {
            Optional<AiRealm> other = realm(server, tie.with());
            if (other.isEmpty() || other.get() == me.get()) continue;
            Relation rel = data(server).diplomacy().between(me.get().id(), other.get().id());
            for (Treaty t : tie.treaties()) rel.sign(t, today);
            rel.addOpinion(me.get().id(), OpinionModifier.of(OpinionReason.AMITIE_ANCIENNE, today, tie.friendship()));
            rel.addOpinion(other.get().id(), OpinionModifier.of(OpinionReason.AMITIE_ANCIENNE, today, tie.friendship()));
        }
    }

    /**
     * Une ville posée avant que sa nation n'existe a fondé une nation à son nom : celle-ci se fond dans la vraie, sans
     * rien perdre (la ville, ses bâtiments, ses gens, son trésor).
     */
    private static void migrate(MinecraftServer server, NationDef def) {
        Optional<AiRealm> nation = realm(server, def.key());
        if (nation.isEmpty() || def.folder().isEmpty()) return;
        CrusadeSavedData data = data(server);
        for (Site s : new ArrayList<>(data.world().sites())) {
            if (!s.imported() || nation.get().id().equals(s.owner())) continue;
            Optional<PlanRef> ref = PlanRef.decode(s.importedRef());
            if (ref.isEmpty() || NationBook.byFolder(ref.get().category()).filter(d -> d == def).isEmpty()) continue;
            Optional<AiRealm> old = data.world().realm(s.owner());
            if (old.isEmpty() || !old.get().handMade() || !old.get().nation().isEmpty() || !old.get().place().equalsIgnoreCase(s.name())) continue;
            UUID was = old.get().id();
            for (Site other : data.world().sites()) if (was.equals(other.owner())) moveCity(data, other, nation.get().id());
            ZoneService.absorb(server, old.get(), nation.get().id());
            CrusadeCraft.LOGGER.info("CrusadeCraft : {} rejoint sa nation, {}", s.name(), def.name());
        }
    }

    /** La ville et ses gens passent à ce royaume. */
    private static void moveCity(CrusadeSavedData data, Site site, UUID realm) {
        site.setOwner(realm);
        LineageState state = data.lineage();
        state.community(site.community()).ifPresent((Community c) -> {
            c.setRealm(realm);
            for (Person p : state.residents(c.id())) p.setRealm(realm);
        });
    }

    public static Optional<AiRealm> realm(MinecraftServer server, String key) {
        if (key == null || key.isEmpty()) return Optional.empty();
        for (AiRealm r : data(server).world().realms()) if (key.equalsIgnoreCase(r.nation())) return Optional.of(r);
        return Optional.empty();
    }

    public static Optional<NationDef> def(AiRealm realm) {
        return realm == null ? Optional.empty() : NationBook.byKey(realm.nation());
    }

    /** La nation dont une ville de ce plan fait partie : celle de son dossier. */
    public static Optional<AiRealm> ofRef(MinecraftServer server, String ref) {
        return PlanRef.decode(ref).flatMap(r -> NationBook.byFolder(r.category())).flatMap(d -> realm(server, d.key()));
    }

    /** La nation dont ce joueur est le souverain. */
    public static Optional<AiRealm> ruledBy(MinecraftServer server, UUID player) {
        for (AiRealm r : data(server).world().realms()) if (player.equals(r.sovereign())) return Optional.of(r);
        return Optional.empty();
    }

    /** Tout le monde connaît les nations : leurs noms se trouvent sur toutes les cartes. */
    public static void onLogin(ServerPlayer player) {
        long today = RealmService.currentDay(player.server);
        boolean changed = false;
        for (AiRealm r : data(player.server).world().realms()) {
            if (!r.nation().isEmpty()) changed |= r.discover(player.getUUID(), today);
        }
        if (changed) data(player.server).setDirty();
    }

    // ------------------------------------------------------------------ la couronne

    /** Le joueur prend la couronne de cette nation. */
    public static Outcome crown(ServerPlayer player, AiRealm realm) {
        if (realm.sovereign() != null && !realm.sovereign().equals(player.getUUID())) {
            return Outcome.no("crusadecraft.nation.crown.taken", realm.sovereignName());
        }
        Optional<AiRealm> other = ruledBy(player.server, player.getUUID());
        if (other.isPresent() && other.get() != realm) return Outcome.no("crusadecraft.nation.crown.already", title(other.get()));
        String name = player.getGameProfile().getName();
        realm.setSovereign(player.getUUID(), name);
        AiCharacter r = realm.ruler();
        realm.setRuler(new AiCharacter(name, r.female(), r.age(), r.stats(), r.traits()));
        realm.discover(player.getUUID(), RealmService.currentDay(player.server));
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.nation.crown.done", title(realm)));
    }

    /** Le joueur rend sa couronne : l'ordinateur reprend le royaume. */
    public static Outcome abdicate(ServerPlayer player) {
        Optional<AiRealm> realm = ruledBy(player.server, player.getUUID());
        if (realm.isEmpty()) return Outcome.no("crusadecraft.nation.none");
        realm.get().setSovereign(null, "");
        realm.get().mail().clear();
        data(player.server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.nation.abdicated", title(realm.get())));
    }

    /** Une ville d'une nation de joueur vient d'être posée : sans souverain, celui qui l'a posée le devient. */
    public static Optional<Component> onCityPlaced(ServerPlayer player, AiRealm realm) {
        boolean playerNation = def(realm).map(NationDef::player).orElse(false);
        if (!playerNation || realm.sovereign() != null || ruledBy(player.server, player.getUUID()).isPresent()) return Optional.empty();
        Outcome o = crown(player, realm);
        return o.success() ? Optional.of(o.message()) : Optional.empty();
    }

    public static Component title(AiRealm realm) {
        return AiNames.realm(realm.rank(), realm.place());
    }

    // ------------------------------------------------------------------ gouverner

    /** Un geste du souverain, depuis l'écran de la nation. */
    public static Outcome act(ServerPlayer player, UUID target, int op, int arg, UUID mailId) {
        MinecraftServer server = player.server;
        Optional<AiRealm> mine = ruledBy(server, player.getUUID());
        if (mine.isEmpty()) return Outcome.no("crusadecraft.nation.none");
        if (op == OP_ACCEPT || op == OP_REFUSE || op == OP_READ) return answer(server, mine.get(), mailId, op);
        Optional<AiRealm> other = target == null ? Optional.empty() : data(server).world().realm(target);
        if (other.isEmpty() || other.get() == mine.get()) return Outcome.no("crusadecraft.world.unknown_realm");
        Treaty[] all = Treaty.values();
        Treaty treaty = arg >= 0 && arg < all.length ? all[arg] : null;
        return switch (op) {
            case OP_PROPOSE -> treaty == null ? Outcome.no("crusadecraft.world.unknown_realm") : propose(server, mine.get(), other.get(), treaty);
            case OP_GIFT -> gift(server, mine.get(), other.get(), GIFT);
            case OP_INSULT -> insult(server, mine.get(), other.get());
            case OP_BREAK -> treaty == null ? Outcome.no("crusadecraft.world.unknown_realm") : breakTreaty(server, mine.get(), other.get(), treaty);
            case OP_WAR -> war(server, mine.get(), other.get());
            default -> Outcome.no("crusadecraft.world.unknown_realm");
        };
    }

    /** Proposer un traité : le souverain d'en face le signe s'il estime assez ce royaume. */
    static Outcome propose(MinecraftServer server, AiRealm mine, AiRealm other, Treaty treaty) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        if (rel.war()) return Outcome.no("crusadecraft.nation.at_war", title(other));
        if (rel.has(treaty, today)) return Outcome.no("crusadecraft.nation.treaty.already", Component.translatable(treaty.key()));
        int opinion = AiBrain.opinion(server, other, mine);
        boolean yes = NationRules.accepts(treaty, opinion);
        if (yes) {
            rel.sign(treaty, today);
            rel.addOpinion(mine.id(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
            rel.addOpinion(other.id(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
        }
        receive(server, mine, other, yes ? Repliques.Moment.LETTRE_ACCEPTE : Repliques.Moment.LETTRE_REFUS, null, 0,
                Map.of("traite", Component.translatable(treaty.key()).getString()));
        data(server).setDirty();
        return yes ? Outcome.ok(Component.translatable("crusadecraft.nation.treaty.signed", Component.translatable(treaty.key()), title(other)))
                : new Outcome(false, Component.translatable("crusadecraft.nation.treaty.refused", title(other), Component.translatable(treaty.key()), opinion,
                NationRules.threshold(treaty)));
    }

    static Outcome gift(MinecraftServer server, AiRealm mine, AiRealm other, int gold) {
        if (mine.gold() < gold) return Outcome.no("crusadecraft.request.no_gold", gold);
        long today = RealmService.currentDay(server);
        mine.setGold(mine.gold() - gold);
        other.setGold(other.gold() + gold);
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        rel.addOpinion(other.id(), OpinionModifier.of(OpinionReason.CADEAU, today, DiplomacyRules.giftOpinion(gold, other.ruler().traits()) / 10.0));
        receive(server, mine, other, Repliques.Moment.LETTRE_MERCI, null, 0, Map.of("or", Integer.toString(gold)));
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.diplo.gift_sent", gold, title(other)));
    }

    static Outcome insult(MinecraftServer server, AiRealm mine, AiRealm other) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        rel.addOpinion(other.id(), OpinionModifier.of(OpinionReason.INSULTE, today, 1));
        if (AiBrain.opinion(server, other, mine) < -40) rel.setHostile(true);
        receive(server, mine, other, Repliques.Moment.LETTRE_OUTRAGE, null, 0, Map.of());
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.diplo.insult_sent", title(other)));
    }

    static Outcome breakTreaty(MinecraftServer server, AiRealm mine, AiRealm other, Treaty treaty) {
        long today = RealmService.currentDay(server);
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        if (!rel.has(treaty, today)) return Outcome.no("crusadecraft.nation.treaty.none", Component.translatable(treaty.key()));
        rel.breakTreaty(treaty);
        rel.addOpinion(other.id(), OpinionModifier.of(OpinionReason.INSULTE, today, 1));
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.nation.treaty.broken", Component.translatable(treaty.key()), title(other)));
    }

    static Outcome war(MinecraftServer server, AiRealm mine, AiRealm other) {
        if (mine.landless() || other.landless()) return Outcome.no("crusadecraft.nation.war.landless");
        String problem = WarService.declarationProblem(server, mine.id(), other.id());
        if (problem != null) return Outcome.no(problem);
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        double distance = Math.sqrt(mine.capital().seat().distSqr(other.capital().seat()));
        AiBrain.declare(server, mine, new AiRules.Target(other.id(), false, AiBrain.opinion(server, mine, other), rel.hostile(),
                WarService.strength(server, mine.id(), 0), WarService.strength(server, other.id(), 0), distance,
                WarService.provincesOf(server, other.id()).size(), other.rank()));
        data(server).setDirty();
        return Outcome.ok(Component.translatable("crusadecraft.nation.war.declared", title(other)));
    }

    private static Outcome answer(MinecraftServer server, AiRealm mine, UUID mailId, int op) {
        Optional<NationMail> found = mine.mail().stream().filter(m -> m.id().equals(mailId)).findFirst();
        if (found.isEmpty()) return Outcome.no("crusadecraft.nation.mail.gone");
        NationMail m = found.get();
        mine.mail().remove(m);
        data(server).setDirty();
        if (op == OP_READ || !m.offer()) return Outcome.ok(Component.translatable("crusadecraft.nation.mail.read"));
        long today = RealmService.currentDay(server);
        Optional<AiRealm> from = data(server).world().realm(m.from());
        if (from.isEmpty()) return Outcome.no("crusadecraft.world.unknown_realm");
        Relation rel = data(server).diplomacy().between(mine.id(), m.from());
        if (op == OP_ACCEPT) {
            if (rel.war()) return Outcome.no("crusadecraft.nation.at_war", title(from.get()));
            rel.sign(m.treaty(), today);
            rel.addOpinion(mine.id(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
            rel.addOpinion(m.from(), OpinionModifier.of(OpinionReason.TRAITE_SIGNE, today, 1));
            return Outcome.ok(Component.translatable("crusadecraft.nation.treaty.signed", Component.translatable(m.treaty().key()), title(from.get())));
        }
        rel.addOpinion(m.from(), OpinionModifier.of(OpinionReason.REFUS, today, 0.5));
        return Outcome.ok(Component.translatable("crusadecraft.nation.mail.refused", title(from.get())));
    }

    // ------------------------------------------------------------------ les lettres

    /** Une lettre de {@code from} au souverain de {@code to}, prise dans les textes écrits d'avance. */
    private static void receive(MinecraftServer server, AiRealm to, AiRealm from, Repliques.Moment moment, Treaty treaty, int gold, Map<String, String> extra) {
        if (to.sovereign() == null || to.mail().size() >= MAIL_MAX) return;
        Random random = new Random(server.getTickCount() * 31L + from.id().hashCode());
        String body = letterText(server, to, from, moment, treaty, gold, extra, random);
        long today = RealmService.currentDay(server);
        Component subject = Component.translatable("crusadecraft.nation.mail.subject." + moment.name().toLowerCase(java.util.Locale.ROOT));
        NationMail mail = new NationMail(java.util.UUID.randomUUID(), from.id(), title(from).getString(), subject.getString(), body, treaty, gold, today,
                today + OFFER_DAYS);
        to.mail().add(0, mail);
        ServerPlayer ruler = server.getPlayerList().getPlayer(to.sovereign());
        if (ruler != null) {
            CrusadeNetwork.toPlayer(ruler, ToastPacket.of(ToastPacket.Kind.REALM, Component.translatable("crusadecraft.nation.mail.toast"), title(from)));
        }
    }

    static String letterText(MinecraftServer server, AiRealm to, AiRealm from, Repliques.Moment moment, Treaty treaty, int gold, Map<String, String> extra, Random random) {
        Component line = Repliques.pick(Repliques.entries(), moment, Role.SOUVERAIN, from.ruler().traits(), random);
        String text = line == null ? "…" : line.getString();
        Map<String, String> vars = new java.util.HashMap<>(extra);
        vars.put("de", title(from).getString());
        vars.put("a", title(to).getString());
        vars.put("souverain", from.ruler().name());
        vars.put("destinataire", to.ruler().name());
        if (treaty != null) vars.put("traite", Component.translatable(treaty.key()).getString());
        vars.put("or", vars.getOrDefault("or", Integer.toString(gold)));
        return NationRules.fill(text, vars);
    }

    /** Un royaume a déclaré la guerre à une nation qui a un souverain. */
    public static void onWarDeclared(MinecraftServer server, AiRealm attacker, UUID target) {
        data(server).world().realm(target).filter(r -> r.sovereign() != null)
                .ifPresent(r -> receive(server, r, attacker, Repliques.Moment.LETTRE_GUERRE, null, 0, Map.of()));
    }

    /** Une fois par jour : les nations de l'ordinateur tiennent leurs traités d'amitié, et les souverains reçoivent leur courrier. */
    public static void onNewDay(MinecraftServer server, long day) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        Random random = new Random(day * 7919L + server.getWorldData().worldGenOptions().seed());
        for (AiRealm r : data.world().realms()) r.mail().removeIf(m -> m.expires() <= today);
        for (NationDef def : NationBook.all()) renewTies(server, def, today);
        for (AiRealm mine : data.world().realms()) {
            if (mine.sovereign() == null) continue;
            for (AiRealm other : data.world().realms()) {
                if (other == mine || other.landless() || other.sovereign() != null || mine.mail().size() >= MAIL_MAX) continue;
                if (other.nation().isEmpty() && !other.discoveredBy(mine.sovereign())) continue;
                letter(server, mine, other, today, random);
            }
        }
        data.setDirty();
    }

    /** Deux nations de l'ordinateur en bons termes renouvellent d'elles-mêmes ce qui les lie. */
    private static void renewTies(MinecraftServer server, NationDef def, long today) {
        Optional<AiRealm> me = realm(server, def.key());
        if (me.isEmpty() || me.get().sovereign() != null) return;
        for (NationDef.Tie tie : def.ties()) {
            Optional<AiRealm> other = realm(server, tie.with());
            if (other.isEmpty() || other.get().sovereign() != null) continue;
            Relation rel = data(server).diplomacy().between(me.get().id(), other.get().id());
            if (rel.war() || rel.hostile()) continue;
            for (Treaty t : tie.treaties()) if (!rel.has(t, today)) rel.sign(t, today);
        }
    }

    /** Le souverain d'en face prend la plume, s'il a quelque chose à dire. */
    private static void letter(MinecraftServer server, AiRealm mine, AiRealm other, long today, Random random) {
        Relation rel = data(server).diplomacy().between(mine.id(), other.id());
        if (rel.war() || rel.suzerain() != null) return;
        if (rel.lastAiLetterDay() != Long.MIN_VALUE && today - rel.lastAiLetterDay() < LETTER_GAP) return;
        if (random.nextInt(3) != 0) return;
        boolean waiting = mine.mail().stream().anyMatch(m -> other.id().equals(m.from()) && m.offer());
        int opinion = AiBrain.opinion(server, other, mine);
        Treaty offer = waiting ? null : offerFor(server, mine, other, rel, opinion, today);
        if (offer != null) {
            rel.setLastAiLetterDay(today);
            receive(server, mine, other, Repliques.Moment.LETTRE_OFFRE, offer, 0, Map.of());
            return;
        }
        if (opinion >= 30 && other.gold() > 60 && random.nextInt(3) == 0) {
            int gold = 10 + random.nextInt(21);
            other.setGold(other.gold() - gold);
            mine.setGold(mine.gold() + gold);
            rel.setLastAiLetterDay(today);
            receive(server, mine, other, Repliques.Moment.LETTRE_PRESENT, null, gold, Map.of());
        }
    }

    /** Ce qu'il propose : d'abord ce que leur amitié ancienne prévoit, puis ce que son tempérament et son opinion lui dictent. */
    private static Treaty offerFor(MinecraftServer server, AiRealm mine, AiRealm other, Relation rel, int opinion, long today) {
        for (NationDef d : List.of(def(other).orElse(null), def(mine).orElse(null)).stream().filter(java.util.Objects::nonNull).toList()) {
            for (NationDef.Tie tie : d.ties()) {
                String partner = d.key().equals(other.nation()) ? mine.nation() : other.nation();
                if (!tie.with().equalsIgnoreCase(partner)) continue;
                for (Treaty t : tie.treaties()) if (!rel.has(t, today) && NationRules.accepts(t, opinion)) return t;
            }
        }
        if (rel.hostile()) return null;
        java.util.Set<Treaty> current = java.util.EnumSet.noneOf(Treaty.class);
        for (Treaty t : Treaty.values()) if (rel.has(t, today)) current.add(t);
        return AiRules.treaty(other.personality(), mine.personality(), opinion, opinion, current);
    }

    // ------------------------------------------------------------------ l'écran

    public static NationView view(MinecraftServer server, AiRealm realm, UUID viewer) {
        CrusadeSavedData data = data(server);
        long today = RealmService.currentDay(server);
        boolean mine = viewer.equals(realm.sovereign());
        Optional<NationDef> def = def(realm);
        List<NationView.CityRow> cities = new ArrayList<>();
        for (AiProvince p : realm.ownProvinces()) {
            int people = 0;
            for (Site s : data.world().sites()) if (s.province() == p.id() && CityService.city(s)) people += CityService.people(server, s);
            NationDef.CityLore lore = def.map(d -> d.city(p.name())).orElse(null);
            cities.add(new NationView.CityRow(p.name(), p == realm.capital(), p.population(), people, p.seat().getX(), p.seat().getZ(),
                    lore == null ? List.of() : lore.history()));
        }
        List<AiRealm> others = new ArrayList<>();
        for (AiRealm o : data.world().realms()) {
            if (o == realm) continue;
            if (!o.nation().isEmpty() || (!o.landless() && o.discoveredBy(viewer))) others.add(o);
        }
        others.sort(Comparator.comparing((AiRealm o) -> o.nation().isEmpty()).thenComparing(AiRealm::place));
        List<NationView.Rel> relations = new ArrayList<>();
        for (AiRealm o : others) {
            if (relations.size() >= 48) break;
            Relation rel = data.diplomacy().between(realm.id(), o.id());
            DiploStatus status = rel.status(realm.id(), today);
            List<Integer> treaties = new ArrayList<>();
            for (Treaty t : Treaty.values()) if (rel.has(t, today)) treaties.add(t.ordinal());
            boolean canWar = mine && !realm.landless() && !o.landless() && WarService.declarationProblem(server, realm.id(), o.id()) == null;
            relations.add(new NationView.Rel(o.id(), title(o).getString(), o.color(), status.ordinal(), AiBrain.opinion(server, o, realm), treaties, rel.war(), canWar));
        }
        List<NationView.Mail> mail = new ArrayList<>();
        if (mine) {
            for (NationMail m : realm.mail()) {
                mail.add(new NationView.Mail(m.id(), m.sender(), m.subject(), m.body(), m.treaty() == null ? -1 : m.treaty().ordinal(), m.gold(), m.day()));
            }
        }
        return new NationView(realm.id(), title(realm).getString(), realm.color(), mine, realm.ruler().name(), def.map(d -> d.culture().name()).orElse(""),
                def.map(d -> d.culture().faith()).orElse(""), realm.gold(), realm.prestige(), def.map(NationDef::history).orElse(List.of()), cities, relations, mail);
    }
}
