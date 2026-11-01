package fr.crusadecraft.core.lineage;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ai.AiBrain;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.NameGenerator;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le cycle de vie (P21) : tous les {@code joursParAn} jours du monde, chacun vieillit d'un an et peut mourir — les gens
 * du registre des lignées et les souverains des royaumes de l'ordinateur, avec leur conseil. Un mort laisse sa place :
 * à la tête de sa maison, l'héritier désigné par la loi de succession du royaume ; sur un trône IA, un successeur. Le
 * joueur, lui, ne vieillit pas.
 */
public final class LifeService {
    /** À cet âge, plus personne ne survit à l'année. */
    public static final int MAX_AGE = 100;
    /** L'âge où l'on peut hériter sans régent. */
    public static final int ADULT = 16;

    private LifeService() {}

    /** Les chances de mourir dans l'année, pour mille : un peu de maladie avant 50 ans, puis la vieillesse. */
    public static int deathChancePerMille(int age) {
        if (age >= MAX_AGE) return 1000;
        if (age < 50) return 3;
        return Math.min(900, 3 + (age - 50) * (age - 50) / 2);
    }

    /** Le rang de la loi de succession : primogéniture (fils d'abord), égalitaire, ancienneté, élective. */
    public enum Succession { PRIMOGENITURE, EGALITAIRE, ANCIENNETE, ELECTIVE }

    /**
     * L'héritier d'un mort à la tête de sa maison, selon la loi, en logique pure. Primogéniture : l'aîné des enfants
     * vivants, fils d'abord ; égalitaire : l'aîné des enfants ; ancienneté : le doyen vivant de la maison ; élective :
     * l'adulte qui a le plus de traits (le plus marquant), le doyen à égalité. Sans enfant, on revient à l'ancienneté.
     */
    public static Optional<Person> heir(List<Person> members, Person dead, Succession law) {
        List<Person> living = new ArrayList<>();
        for (Person p : members) if (p.alive() && !p.id().equals(dead.id())) living.add(p);
        if (living.isEmpty()) return Optional.empty();
        List<Person> children = new ArrayList<>();
        for (Person p : living) if (p.isChildOf(dead.id())) children.add(p);
        Comparator<Person> eldest = Comparator.comparingInt(Person::age).reversed();
        switch (law) {
            case PRIMOGENITURE -> {
                if (!children.isEmpty()) {
                    return children.stream().sorted(Comparator.comparing(Person::female).thenComparing(eldest)).findFirst();
                }
            }
            case EGALITAIRE -> {
                if (!children.isEmpty()) return children.stream().sorted(eldest).findFirst();
            }
            case ELECTIVE -> {
                Optional<Person> chosen = living.stream().filter(p -> p.age() >= ADULT)
                        .sorted(Comparator.comparingInt((Person p) -> p.traits().size()).reversed().thenComparing(eldest)).findFirst();
                if (chosen.isPresent()) return chosen;
            }
            case ANCIENNETE -> {}
        }
        return living.stream().sorted(eldest).findFirst();
    }

    /** L'héritier d'une personne morte, selon la loi de sa maison : ses enfants d'abord, puis le doyen. */
    public static Optional<Person> heirOf(MinecraftServer server, Person dead) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<House> house = data.lineage().house(dead.house());
        if (house.isEmpty()) return Optional.empty();
        return heir(data.lineage().members(house.get().id()), dead, lawOf(data, house.get()));
    }

    /** La loi de succession d'une maison : celle de son royaume s'il est à un joueur, la primogéniture sinon. */
    static Succession lawOf(CrusadeSavedData data, House house) {
        if (house == null || house.realm() == null) return Succession.PRIMOGENITURE;
        Optional<Realm> realm = data.realm(house.realm());
        if (realm.isEmpty()) return Succession.PRIMOGENITURE;
        Succession[] all = Succession.values();
        return all[Math.max(0, Math.min(all.length - 1, realm.get().laws().level(Law.SUCCESSION)))];
    }

    /** Appelé chaque jour du monde : une année passe tous les {@code joursParAn} jours. */
    public static void onNewDay(MinecraftServer server, long day) {
        if (!CrusadeConfig.SERVER.lifeCycle.get()) return;
        int perYear = CrusadeConfig.SERVER.daysPerYear.get();
        if (day <= 0 || day % perYear != 0) return;
        year(server, day, new Random(day * 7919L + server.getWorldData().worldGenOptions().seed()));
    }

    /** Une année : chacun vieillit, et certains meurent. Renvoie le nombre de morts. */
    public static int year(MinecraftServer server, long day, Random random) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        int deaths = 0;
        if (CrusadeConfig.SERVER.lineageDeaths.get()) {
            for (Person p : new ArrayList<>(data.lineage().persons())) {
                if (!p.alive()) continue;
                p.setAge(p.age() + 1);
                if (random.nextInt(1000) < deathChancePerMille(p.age())) {
                    die(server, p);
                    deaths++;
                }
            }
        }
        for (AiRealm r : data.world().realms()) {
            for (Map.Entry<Post, AiCharacter> e : new ArrayList<>(r.council().entrySet())) {
                AiCharacter c = older(e.getValue());
                if (random.nextInt(1000) < deathChancePerMille(c.age())) r.council().remove(e.getKey());
                else r.council().put(e.getKey(), c);
            }
            AiCharacter ruler = older(r.ruler());
            if (random.nextInt(1000) < deathChancePerMille(ruler.age())) {
                succeed(server, r, ruler, day, random);
                deaths++;
            } else {
                r.setRuler(ruler);
            }
        }
        data.setDirty();
        return deaths;
    }

    private static AiCharacter older(AiCharacter c) {
        return new AiCharacter(c.name(), c.female(), c.age() + 1, c.stats(), c.traits());
    }

    /** Une personne du registre meurt : son villageois aussi s'il est là, et sa maison change de chef. */
    public static void die(MinecraftServer server, Person p) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        LineageState state = data.lineage();
        p.setAlive(false);
        if (p.inWorld()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, p.worldDim()));
            Entity e = level == null ? null : level.getEntity(p.id());
            if (e != null && e.isAlive()) e.kill();
            p.setInWorld(null, null);
        }
        Optional<House> house = state.house(p.house());
        boolean wasHead = house.isPresent() && p.id().equals(house.get().head());
        if (wasHead) {
            heir(state.members(house.get().id()), p, lawOf(data, house.get())).ifPresent(h -> {
                house.get().setHead(h.id());
                if (p.role() == Role.SOUVERAIN) h.setRole(Role.SOUVERAIN);
            });
        }
        UUID realmId = house.map(House::realm).orElse(p.realm());
        if (realmId != null) {
            data.realm(realmId).ifPresent(r -> {
                fr.crusadecraft.core.intrigue.Chronicle.record(server, r, "crusadecraft.chronicle.death",
                        Component.literal(state.fullName(p)), Component.literal(String.valueOf(p.age())));
                if (mourned(wasHead, p.role())) fr.crusadecraft.core.court.FeastService.mourn(server, r, p.id(), state.fullName(p));
            });
        }
        data.setDirty();
    }

    /** On porte le deuil d'un chef de maison, du souverain, de son conjoint et de son héritier ; pas de chacun. */
    public static boolean mourned(boolean wasHead, Role role) {
        return wasHead || role == Role.SOUVERAIN || role == Role.CONSORT || role == Role.HERITIER;
    }

    /** Le souverain d'un royaume IA meurt : son successeur monte sur le trône, et le monde l'apprend. */
    static void succeed(MinecraftServer server, AiRealm realm, AiCharacter dead, long day, Random random) {
        Succession law = lawOf(realm);
        AiCharacter next;
        Optional<Map.Entry<Post, AiCharacter>> councillor = realm.council().entrySet().stream()
                .filter(e -> e.getValue().age() >= ADULT)
                .max(Comparator.comparingInt(e -> java.util.Arrays.stream(e.getValue().stats()).sum()));
        if ((law == Succession.ELECTIVE || law == Succession.ANCIENNETE) && councillor.isPresent()) {
            next = councillor.get().getValue();
            realm.council().remove(councillor.get().getKey());
        } else {
            boolean female = law == Succession.EGALITAIRE ? random.nextBoolean() : random.nextInt(5) == 0;
            String name = new NameGenerator(random.nextLong()).personName(female);
            List<Trait> traits = new ArrayList<>(dead.traits().subList(0, Math.min(1, dead.traits().size())));
            next = new AiCharacter(name, female, 18 + random.nextInt(18), dead.stats().clone(), traits);
        }
        realm.setRuler(next);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} meurt ; {} lui succède à la tête de {}", dead.name(), next.name(), realm.place());
        AiBrain.publish(server, WorldNews.of(day, "crusadecraft.news.succession", List.of(realm.id()), Component.literal(dead.name()),
                Component.literal(next.name()), AiNames.realm(realm.rank(), realm.place())), true);
        fr.crusadecraft.core.ai.PartitionService.afterSuccession(server, realm, law, dead, day, random);
    }

    /** La loi de succession d'un royaume de l'ordinateur : tirée de son identifiant, elle ne change pas. */
    public static Succession lawOf(AiRealm realm) {
        return Succession.values()[Math.floorMod(realm.id().hashCode(), Succession.values().length)];
    }
}
