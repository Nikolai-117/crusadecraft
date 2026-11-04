package fr.crusadecraft.core.lineage;

import fr.crusadecraft.core.world.NameGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Le générateur de lignées : des maisons sur plusieurs générations, la cour d'un royaume IA, la population d'une
 * communauté, un enfant ou un conjoint de plus. Tout est tiré d'une graine, donc reproductible, et les âges restent
 * vraisemblables : un parent a au moins dix-huit ans de plus que son enfant, les très vieux ancêtres sont morts.
 *
 * <p>Il n'écrit que des faits (noms, âges, liens, rôles) : aucune histoire n'est inventée.
 */
public final class LineageGenerator {
    /** Écart d'âge minimal entre un parent et son enfant. */
    public static final int PARENT_GAP = 18;
    public static final int MAX_GENERATIONS = 5;

    private static final List<String> SURNAMES = List.of(
            "Lefèvre", "Dubois", "Moreau", "Fournier", "Mercier", "Meunier", "Boulanger", "Tisserand", "Charpentier",
            "Berger", "Pasquier", "Masson", "Barbier", "Charron", "Ferrand", "Marchand", "Leclerc", "Roux", "Lebrun",
            "Bonnet", "Faure", "Garnier", "Perrin", "Vasseur", "Tessier", "Rivière", "Lacombe", "Delorme", "Fontaine",
            "Chevrier");

    private final Random random;
    private final NameGenerator names;
    /** P42 : les prénoms et les noms de famille d'une culture ; vides : ceux du mod. */
    private List<String> men = List.of(), women = List.of(), families = List.of();

    /** Les gens qu'il crée portent désormais les noms de cette culture. */
    public LineageGenerator withNames(List<String> men, List<String> women, List<String> families) {
        this.men = men;
        this.women = women;
        this.families = families;
        return this;
    }

    private String first(boolean female) {
        List<String> pool = female ? women : men;
        return pool.isEmpty() ? names.personName(female) : pool.get(random.nextInt(pool.size()));
    }

    private String family() {
        List<String> pool = families.isEmpty() ? SURNAMES : families;
        return pool.get(random.nextInt(pool.size()));
    }

    public LineageGenerator(long seed) {
        this.random = new Random(seed);
        this.names = new NameGenerator(seed ^ 0x4C494E45L);
    }

    // ------------------------------------------------------------------ maisons

    /**
     * Une maison sur {@code generations} générations (1 à 5). Les fondateurs sont les plus anciens ; la dernière
     * génération compte de jeunes adultes. Si la maison règne sur un royaume, son chef en est le souverain.
     */
    public House house(LineageState state, String name, int color, UUID realm, int generations) {
        int g = Math.max(1, Math.min(MAX_GENERATIONS, generations));
        House house = new House(UUID.randomUUID(), name, color);
        house.setRealm(realm);
        state.add(house);
        // L'aîné de la dernière génération a entre 20 et 30 ans ; chaque génération en ajoute 25 à 32.
        int founderAge = 20 + random.nextInt(11);
        for (int i = 1; i < g; i++) founderAge += 25 + random.nextInt(8);
        // Une lignée se transmet le plus souvent par les hommes, mais une fondatrice n'est pas rare.
        Person founder = person(state, house, random.nextInt(4) == 0, founderAge);
        founder.setRealm(realm);
        Person spouse = spouse(state, founder);
        descend(state, house, founder, spouse, 1, g);
        settle(state, house);
        return house;
    }

    /** Les enfants d'un couple, puis les leurs, jusqu'à la génération voulue. */
    private void descend(LineageState state, House house, Person member, Person spouse, int generation, int generations) {
        if (generation >= generations) return;
        int youngest = spouse == null ? member.age() : Math.min(member.age(), spouse.age());
        int count = 1 + random.nextInt(3) + (random.nextInt(5) == 0 ? 1 : 0);
        int age = youngest - PARENT_GAP - 1 - random.nextInt(9);
        for (int i = 0; i < count && age > 0; i++) {
            Person child = person(state, house, random.nextBoolean(), age);
            child.setRealm(member.realm());
            Person father = member.female() ? spouse : member, mother = member.female() ? member : spouse;
            child.setFather(father == null ? null : father.id());
            child.setMother(mother == null ? null : mother.id());
            boolean last = generation == generations - 1;
            // L'aîné continue toujours la lignée ; les cadets se marient souvent ; la dernière génération, parfois.
            boolean marries = child.age() >= PARENT_GAP + 2 && (last ? random.nextInt(2) == 0 : i == 0 || random.nextInt(10) < 6);
            if (marries) {
                Person partner = spouse(state, child);
                descend(state, house, child, partner, generation + 1, generations);
            }
            age -= 2 + random.nextInt(3);
        }
    }

    /**
     * La cour d'un royaume IA : son souverain tel que le monde le connaît (nom, sexe, âge), son conjoint, ses enfants
     * et son héritier, ses parents défunts, et ses conseillers (des roturiers à son service).
     */
    public House court(LineageState state, String houseName, int color, UUID realm, String rulerName, boolean rulerFemale,
                       int rulerAge, List<Counsellor> council) {
        House house = new House(UUID.randomUUID(), houseName, color);
        house.setRealm(realm);
        state.add(house);
        Person ruler = person(state, house, rulerFemale, rulerAge);
        ruler.setName(rulerName);
        ruler.setRealm(realm);
        // Ses parents, morts ou très vieux.
        Person father = person(state, house, false, rulerAge + 22 + random.nextInt(12));
        father.setRealm(realm);
        Person mother = spouse(state, father);
        mother.setAge(father.age() - random.nextInt(6));
        ruler.setFather(father.id());
        ruler.setMother(mother.id());
        if (rulerAge >= PARENT_GAP + 2) {
            Person consort = spouse(state, ruler);
            descend(state, house, ruler, consort, 1, 3);
        }
        for (Counsellor c : council) {
            Person p = new Person(UUID.randomUUID(), c.name(), c.female(), c.age());
            p.setSurname(noble());
            p.setRole(Role.CONSEILLER);
            p.setRealm(realm);
            state.add(p);
        }
        settle(state, house);
        // Le souverain que le monde connaît règne, même si son père vit encore.
        house.setHead(ruler.id());
        ruler.setAlive(true);
        assignRoles(state, house);
        return house;
    }

    /** Un conseiller d'un royaume IA, tel que le monde le connaît. */
    public record Counsellor(String name, boolean female, int age) {}

    /**
     * Après une génération : les morts (au-delà de 70 ans, de plus en plus souvent), le chef (le plus âgé des vivants
     * de la génération la plus ancienne) et les rôles (souverain, conjoint, héritier, famille).
     */
    public void settle(LineageState state, House house) {
        for (Person p : state.members(house.id())) mortality(p);
        for (Person p : state.members(house.id())) state.person(p.spouse()).ifPresent(this::mortality);
        Person head = null;
        int bestGen = Integer.MAX_VALUE;
        var gens = state.generations(house.id());
        for (var e : gens.entrySet()) {
            for (Person p : e.getValue()) {
                if (!p.alive() || !house.id().equals(p.house())) continue;
                if (e.getKey() < bestGen || (e.getKey() == bestGen && p.age() > head.age())) {
                    head = p;
                    bestGen = e.getKey();
                }
            }
        }
        house.setHead(head == null ? null : head.id());
        assignRoles(state, house);
    }

    /** Les rôles d'une maison : souverain et conjoint si elle règne, l'aîné vivant des enfants du chef pour héritier. */
    public static void assignRoles(LineageState state, House house) {
        Optional<Person> head = state.person(house.head());
        boolean reigns = house.realm() != null;
        for (Person p : state.members(house.id())) if (p.role().noble() || p.role() == Role.AUCUN) p.setRole(Role.FAMILLE);
        for (Person p : state.members(house.id())) state.person(p.spouse()).filter(s -> s.role().noble() || s.role() == Role.AUCUN)
                .ifPresent(s -> s.setRole(Role.FAMILLE));
        if (head.isEmpty()) return;
        head.get().setRole(reigns ? Role.SOUVERAIN : Role.FAMILLE);
        state.person(head.get().spouse()).filter(Person::alive).ifPresent(s -> s.setRole(reigns ? Role.CONSORT : Role.FAMILLE));
        Person heir = null;
        for (Person c : state.children(head.get().id())) {
            if (!c.alive() || !house.id().equals(c.house())) continue;
            if (heir == null || c.age() > heir.age()) heir = c;
        }
        if (heir != null) heir.setRole(Role.HERITIER);
    }

    private void mortality(Person p) {
        if (p.age() > 85 || (p.age() > 70 && random.nextInt(100) < (p.age() - 65) * 4)) p.setAlive(false);
    }

    // ------------------------------------------------------------------ communautés

    /** Une communauté et ses habitants : des foyers (couples, enfants, parfois un aïeul) ou des membres seuls. */
    public Community community(LineageState state, String name, CommunityKind kind, UUID realm, int size) {
        Community c = new Community(UUID.randomUUID(), name, kind);
        c.setRealm(realm);
        state.add(c);
        populate(state, c, size);
        return c;
    }

    /** Ajoute {@code size} foyers (ou membres seuls) à une communauté. */
    public List<Person> populate(LineageState state, Community c, int size) {
        List<Person> added = new ArrayList<>();
        CommunityKind kind = c.kind();
        for (int i = 0; i < size; i++) {
            if (!kind.families()) {
                // Ordres et compagnies d'hommes surtout ; les monastères accueillent moines et moniales.
                boolean female = kind == CommunityKind.MONASTERE ? random.nextBoolean() : random.nextInt(5) == 0;
                Person p = resident(state, c, female, 18 + random.nextInt(43));
                p.setRole(kind.roles().get(random.nextInt(kind.roles().size())));
                added.add(p);
                continue;
            }
            String surname = family();
            Person head = resident(state, c, random.nextInt(10) < 3, 20 + random.nextInt(36));
            head.setSurname(surname);
            head.setRole(kind.roles().get(random.nextInt(kind.roles().size())));
            added.add(head);
            Person partner = null;
            if (random.nextInt(10) < 9) {
                partner = resident(state, c, !head.female(), Math.max(18, head.age() - 6 + random.nextInt(13)));
                partner.setSurname(surname);
                partner.setRole(kind.roles().get(random.nextInt(kind.roles().size())));
                marry(head, partner);
                added.add(partner);
            }
            int youngest = partner == null ? head.age() : Math.min(head.age(), partner.age());
            int kids = random.nextInt(5);
            int age = youngest - PARENT_GAP - random.nextInt(8);
            for (int k = 0; k < kids && age >= 0; k++) {
                Person child = resident(state, c, random.nextBoolean(), age);
                child.setSurname(surname);
                Person father = head.female() ? partner : head, mother = head.female() ? head : partner;
                child.setFather(father == null ? null : father.id());
                child.setMother(mother == null ? null : mother.id());
                added.add(child);
                age -= 1 + random.nextInt(4);
            }
            if (random.nextInt(4) == 0 && head.age() < 50) {
                // Un aïeul, veuf, qui vit avec le foyer de son enfant.
                Person elder = resident(state, c, random.nextBoolean(), head.age() + PARENT_GAP + 4 + random.nextInt(10));
                elder.setSurname(surname);
                if (elder.female()) head.setMother(elder.id());
                else head.setFather(elder.id());
                added.add(elder);
            }
        }
        return added;
    }

    /** P38 : quelqu'un qui tient un ouvrage dans une ville (le boulanger, un garde), seul, adulte, avec son nom de famille. */
    public Person worker(LineageState state, Community c, Role role, boolean mostlyMen) {
        boolean female = mostlyMen ? random.nextInt(6) == 0 : random.nextInt(3) == 0;
        Person p = resident(state, c, female, 20 + random.nextInt(36));
        p.setSurname(family());
        p.setRole(role);
        return p;
    }

    private Person resident(LineageState state, Community c, boolean female, int age) {
        Person p = new Person(UUID.randomUUID(), first(female), female, age);
        p.setCommunity(c.id());
        p.setRealm(c.realm());
        state.add(p);
        return p;
    }

    // ------------------------------------------------------------------ une personne de plus

    /**
     * Un conjoint pour cette personne : de l'autre sexe, d'un âge voisin, noble (« de Valcourt ») pour une maison,
     * roturier pour un habitant. Il rejoint sa communauté et son royaume.
     */
    public Person spouse(LineageState state, Person p) {
        int age = Math.max(16, p.age() - 5 + random.nextInt(11));
        Person s = new Person(UUID.randomUUID(), first(!p.female()), !p.female(), age);
        s.setSurname(p.house() != null ? noble() : p.surname());
        s.setRealm(p.realm());
        s.setCommunity(p.community());
        s.setRole(p.house() != null ? Role.FAMILLE : p.role() == Role.AUCUN ? Role.AUCUN : p.role());
        state.add(s);
        marry(p, s);
        return s;
    }

    /**
     * Un enfant de cette personne (et de son conjoint, s'il en a un). Il prend la maison de son père, sinon celle de sa
     * mère ; son nom de famille ; sa communauté et son royaume. Vide si le parent est trop jeune.
     */
    public Optional<Person> child(LineageState state, Person parent) {
        Optional<Person> other = state.person(parent.spouse());
        int youngest = other.map(o -> Math.min(o.age(), parent.age())).orElse(parent.age());
        if (youngest < PARENT_GAP) return Optional.empty();
        int age = Math.max(0, youngest - PARENT_GAP - random.nextInt(Math.max(1, Math.min(15, youngest - PARENT_GAP + 1))));
        Person father = parent.female() ? other.orElse(null) : parent, mother = parent.female() ? parent : other.orElse(null);
        UUID house = father != null && father.house() != null ? father.house() : mother != null ? mother.house() : null;
        boolean female = random.nextBoolean();
        Person child = new Person(UUID.randomUUID(), first(female), female, age);
        child.setHouse(house);
        child.setSurname(house != null ? "" : (father != null ? father.surname() : parent.surname()));
        child.setFather(father == null ? null : father.id());
        child.setMother(mother == null ? null : mother.id());
        child.setRealm(parent.realm());
        child.setCommunity(parent.community());
        child.setRole(house != null ? Role.FAMILLE : Role.AUCUN);
        state.add(child);
        return Optional.of(child);
    }

    private Person person(LineageState state, House house, boolean female, int age) {
        Person p = new Person(UUID.randomUUID(), names.personName(female), female, age);
        p.setHouse(house.id());
        p.setRole(Role.FAMILLE);
        state.add(p);
        return p;
    }

    private String noble() {
        String place = names.place();
        return (LineageState.elides(place) ? "d'" : "de ") + place;
    }

    public static void marry(Person a, Person b) {
        a.setSpouse(b.id());
        b.setSpouse(a.id());
    }

    public String placeName() {
        return names.place();
    }
}
