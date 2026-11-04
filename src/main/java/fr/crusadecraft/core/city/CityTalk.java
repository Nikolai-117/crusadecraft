package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.InhabitantPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Parler à un habitant (P42), accroupi et d'un clic droit : sa fiche et des sujets de conversation. Rien d'une
 * intelligence : ses réponses viennent de textes écrits d'avance, choisis selon qui il est et ce qu'il pense de vous.
 * Ce qu'il pense de vous, lui, est bien réel : il s'en souvient, et il le répète.
 */
public final class CityTalk {
    /** Les sujets qu'on peut aborder, et le moment des répliques qui y répondent. */
    public enum Topic {
        BONJOUR(Repliques.Moment.ACCUEIL), METIER(Repliques.Moment.METIER), VILLE(Repliques.Moment.VILLE), ROYAUME(Repliques.Moment.ROYAUME),
        RUMEUR(Repliques.Moment.RUMEUR), FAMILLE(Repliques.Moment.FAMILLE), AVIS(Repliques.Moment.AVIS);

        final Repliques.Moment moment;

        Topic(Repliques.Moment moment) {
            this.moment = moment;
        }

        public String key() {
            return "crusadecraft.city.talk.topic." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** On parle à moins de tant de blocs. */
    static final int REACH = 8;
    /** Ceux qui voient un coup porté, et lui en veulent. */
    static final int WITNESS = 16;

    /** Le dernier jour où ce joueur a parlé à cette personne : une conversation par jour fait du bien, pas davantage. */
    private static final Map<String, Long> LAST_TALK = new HashMap<>();
    private static final Random RANDOM = new Random();

    private CityTalk() {}

    public static void onServerStopped() {
        LAST_TALK.clear();
    }

    private static CrusadeSavedData data(MinecraftServer server) {
        return CrusadeSavedData.get(server);
    }

    /** Ce villageois est-il un habitant d'une ville du registre ? Sa personne, et sa ville. */
    public static Optional<Person> person(MinecraftServer server, Entity entity) {
        if (!entity.getTags().contains(CityService.TAG)) return Optional.empty();
        return data(server).lineage().person(entity.getUUID()).filter(Person::alive);
    }

    public static Optional<Site> siteOf(MinecraftServer server, Person p) {
        if (p.site() != null) {
            Optional<Site> s = data(server).world().site(p.site());
            if (s.isPresent()) return s;
        }
        for (Site s : data(server).world().sites()) if (p.community() != null && p.community().equals(s.community())) return Optional.of(s);
        return Optional.empty();
    }

    /** Accroupi, clic droit sur un habitant : sa fiche s'ouvre. Faux si ce n'en est pas un. */
    public static boolean open(ServerPlayer player, Entity target) {
        Optional<Person> p = person(player.server, target);
        if (p.isEmpty()) return false;
        Optional<Site> site = siteOf(player.server, p.get());
        if (site.isEmpty()) return false;
        if (p.get().site() == null) CityPeople.backfill(player.server, site.get());
        String line = CityVoice.text(player.server, site.get(), p.get(), Repliques.Moment.ACCUEIL, player, RANDOM).orElse("…");
        CrusadeNetwork.toPlayer(player, new InhabitantPackets.Open(view(player, target, site.get(), p.get(), line)));
        return true;
    }

    static InhabitantPackets.View view(ServerPlayer player, Entity entity, Site site, Person p, String line) {
        MinecraftServer server = player.server;
        LineageState state = data(server).lineage();
        String realm = data(server).world().realm(site.owner()).map(r -> Fr.of(AiNames.realm(r.rank(), r.place())))
                .orElseGet(() -> data(server).realm(site.owner()).map(fr.crusadecraft.core.realm.Realm::name).orElse(""));
        List<Integer> traits = new ArrayList<>();
        for (Trait t : p.traits()) traits.add(t.ordinal());
        List<String> family = new ArrayList<>();
        state.person(p.spouse()).ifPresent(s -> family.add(Fr.key(s.alive() ? (p.female() ? "crusadecraft.city.talk.family.husband" : "crusadecraft.city.talk.family.wife")
                : "crusadecraft.city.talk.family.widow", state.fullName(s))));
        for (Person c : state.children(p.id())) family.add(Fr.key(c.alive() ? "crusadecraft.city.talk.family.child" : "crusadecraft.city.talk.family.lost", c.name(), c.age()));
        state.person(p.father()).ifPresent(f -> family.add(Fr.key("crusadecraft.city.talk.family.father", state.fullName(f))));
        state.person(p.mother()).ifPresent(m -> family.add(Fr.key("crusadecraft.city.talk.family.mother", state.fullName(m))));
        boolean trades = entity instanceof Villager v && !v.getOffers().isEmpty();
        return new InhabitantPackets.View(p.id(), entity.getId(), state.fullName(p), jobTitle(p), site.name(), realm, p.age(), traits, p.bio(), family,
                p.opinionOf(player.getUUID()), line, trades, p.hunger());
    }

    /** Son titre, accordé : « boulangère », « garde ». */
    public static String jobTitle(Person p) {
        BuildingFunction f = BuildingFunction.byName(p.job());
        if (f == null) return Fr.key(p.age() < 16 ? (p.female() ? "crusadecraft.city.job.fille" : "crusadecraft.city.job.garcon")
                : (p.female() ? "crusadecraft.city.job.maison.f" : "crusadecraft.city.job.maison"));
        String base = "crusadecraft.city.job." + f.name().toLowerCase(java.util.Locale.ROOT);
        return Fr.key(p.female() ? base + ".f" : base);
    }

    /** Un sujet abordé : il répond, et une conversation par jour le rapproche un peu de vous. */
    public static void say(ServerPlayer player, UUID id, Topic topic) {
        MinecraftServer server = player.server;
        Optional<Person> p = data(server).lineage().person(id).filter(Person::alive);
        if (p.isEmpty()) return;
        Optional<Site> site = siteOf(server, p.get());
        if (site.isEmpty()) return;
        Entity e = player.serverLevel().getEntity(id);
        if (e == null || e.distanceToSqr(player) > REACH * REACH * 4) return;
        long today = RealmService.currentDay(server);
        String key = player.getUUID() + "|" + id;
        Long last = LAST_TALK.get(key);
        if (last == null || last != today) {
            LAST_TALK.put(key, today);
            p.get().addOpinion(player.getUUID(), 1);
            data(server).setDirty();
        }
        Repliques.Moment moment = topic.moment;
        // Une vraie nouvelle de la ville, s'il y en a une, passe avant les commérages.
        if (topic == Topic.RUMEUR && !site.get().rumors().isEmpty() && RANDOM.nextInt(10) < 7) moment = Repliques.Moment.RUMEUR_VRAIE;
        String line = CityVoice.text(server, site.get(), p.get(), moment, player, RANDOM)
                .or(() -> CityVoice.text(server, site.get(), p.get(), Repliques.Moment.ACCUEIL, player, RANDOM)).orElse("…");
        CrusadeNetwork.toPlayer(player, new InhabitantPackets.Open(view(player, e, site.get(), p.get(), line)));
    }

    /** Commercer, depuis sa fiche : son étal s'ouvre, sans passer par la fenêtre de Comes Alive. */
    public static void trade(ServerPlayer player, UUID id) {
        Entity e = player.serverLevel().getEntity(id);
        if (!(e instanceof Villager v) || e.distanceToSqr(player) > REACH * REACH * 4 || v.isTrading()) return;
        if (v.getOffers().isEmpty()) return;
        v.setTradingPlayer(player);
        v.openTradingScreen(player, v.getDisplayName(), v.getVillagerData().getLevel());
    }

    // ------------------------------------------------------------------ ce dont ils se souviennent

    /** Un joueur frappe un habitant : celui-ci et ceux qui l'ont vu s'en souviennent, et la ville en parle. */
    public static void onHurt(ServerPlayer player, Entity victim) {
        MinecraftServer server = player.server;
        Optional<Person> p = person(server, victim);
        if (p.isEmpty()) return;
        Optional<Site> site = siteOf(server, p.get());
        if (site.isEmpty()) return;
        p.get().addOpinion(player.getUUID(), -15);
        for (Person w : witnesses(server, victim, site.get())) w.addOpinion(player.getUUID(), -5);
        if (victim instanceof Villager v) v.getGossips().add(player.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_NEGATIVE, 25);
        CityRumors.add(server, site.get(), "frappe", player.getGameProfile().getName(), p.get().name());
        CityBubbles.say((ServerLevel) victim.level(), victim, CityVoice.text(server, site.get(), p.get(), Repliques.Moment.DOULEUR, player, RANDOM).orElse("Aïe !"));
        data(server).setDirty();
    }

    /** Un joueur tue un habitant : sa famille ne pardonnera pas de sitôt, et toute la ville l'apprend. */
    public static void onKilled(ServerPlayer player, Entity victim) {
        MinecraftServer server = player.server;
        Optional<Person> p = data(server).lineage().person(victim.getUUID());
        if (p.isEmpty() || !victim.getTags().contains(CityService.TAG)) return;
        Optional<Site> site = siteOf(server, p.get());
        if (site.isEmpty()) return;
        LineageState state = data(server).lineage();
        for (CityBuilding b : site.get().buildings()) for (UUID id : b.occupants()) state.person(id).ifPresent(x -> x.addOpinion(player.getUUID(), -10));
        for (Person kin : kin(state, p.get())) kin.addOpinion(player.getUUID(), -40);
        CityRumors.add(server, site.get(), "tue", player.getGameProfile().getName(), p.get().name());
        data(server).setDirty();
    }

    private static List<Person> kin(LineageState state, Person p) {
        List<Person> out = new ArrayList<>(state.children(p.id()));
        state.person(p.spouse()).ifPresent(out::add);
        state.person(p.father()).ifPresent(out::add);
        state.person(p.mother()).ifPresent(out::add);
        return out;
    }

    private static List<Person> witnesses(MinecraftServer server, Entity at, Site site) {
        List<Person> out = new ArrayList<>();
        for (Villager v : at.level().getEntitiesOfClass(Villager.class, at.getBoundingBox().inflate(WITNESS), x -> x != at && x.getTags().contains(CityService.TAG))) {
            data(server).lineage().person(v.getUUID()).filter(Person::alive).ifPresent(out::add);
        }
        return out;
    }
}
