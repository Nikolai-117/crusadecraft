package fr.crusadecraft.core.lineage;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le registre des lignées (P13) : des générations vraisemblables, des liens cohérents, une sauvegarde fidèle. */
class LineageLogicTest {

    /** Tout parent connu a au moins dix-huit ans de plus que son enfant ; tout mariage est réciproque et mixte. */
    private static void assertCoherent(LineageState s) {
        for (Person p : s.persons()) {
            for (UUID parentId : new UUID[]{p.father(), p.mother()}) {
                Optional<Person> parent = s.person(parentId);
                parent.ifPresent(par -> assertTrue(par.age() - p.age() >= LineageGenerator.PARENT_GAP,
                        par.name() + " (" + par.age() + ") trop proche de son enfant " + p.name() + " (" + p.age() + ")"));
            }
            s.person(p.father()).ifPresent(f -> assertFalse(f.female(), "un père est un homme"));
            s.person(p.mother()).ifPresent(m -> assertTrue(m.female(), "une mère est une femme"));
            s.person(p.spouse()).ifPresent(sp -> {
                assertEquals(p.id(), sp.spouse(), "mariage non réciproque");
                assertNotEquals(p.female(), sp.female(), "les générations du registre se marient entre sexes opposés");
            });
        }
    }

    @Test
    void maisonsDeUneACinqGenerations() {
        for (int g = 1; g <= LineageGenerator.MAX_GENERATIONS; g++) {
            LineageState s = new LineageState();
            House h = new LineageGenerator(1000 + g).house(s, "Aubépine", 0x2E7D32, null, g);
            Map<Integer, List<Person>> gens = s.generations(h.id());
            assertEquals(g, gens.size(), "générations demandées : " + g);
            assertCoherent(s);
            assertTrue(s.person(h.head()).map(Person::alive).orElse(false), "un chef vivant");
            assertTrue(s.members(h.id()).stream().noneMatch(p -> p.role() == Role.SOUVERAIN || p.role() == Role.CONSORT),
                    "une maison sans royaume n'a pas de souverain");
        }
    }

    @Test
    void lesAncetresLointainsSontMorts() {
        LineageState s = new LineageState();
        House h = new LineageGenerator(77).house(s, "Valcourt", 0, null, 5);
        List<Person> founders = s.generations(h.id()).firstEntry().getValue();
        assertTrue(founders.stream().allMatch(p -> p.age() > 100 && !p.alive()), "cinq générations plus tôt, les fondateurs sont morts");
    }

    @Test
    void uneMaisonRegnanteASonSouverainEtSonHeritier() {
        LineageState s = new LineageState();
        UUID realm = UUID.randomUUID();
        House h = new LineageGenerator(5).house(s, "Rochegarde", 0, realm, 4);
        long rulers = s.members(h.id()).stream().filter(p -> p.role() == Role.SOUVERAIN).count();
        assertEquals(1, rulers, "un seul souverain");
        Person head = s.person(h.head()).orElseThrow();
        assertEquals(Role.SOUVERAIN, head.role());
        s.members(h.id()).stream().filter(p -> p.role() == Role.HERITIER).forEach(heir -> assertTrue(heir.isChildOf(head.id()), "l'héritier est un enfant du souverain"));
        assertTrue(s.members(h.id()).stream().allMatch(p -> realm.equals(p.realm())), "toute la maison sert le royaume");
        assertEquals(h, s.houseOfRealm(realm).orElseThrow());
    }

    @Test
    void lamemeGraineDonneLaMemeMaison() {
        LineageState a = new LineageState(), b = new LineageState();
        House ha = new LineageGenerator(42).house(a, "Ormesval", 0, null, 3);
        House hb = new LineageGenerator(42).house(b, "Ormesval", 0, null, 3);
        assertEquals(a.members(ha.id()).stream().map(Person::name).toList(), b.members(hb.id()).stream().map(Person::name).toList());
        assertEquals(a.members(ha.id()).stream().map(Person::age).toList(), b.members(hb.id()).stream().map(Person::age).toList());
    }

    @Test
    void laCourDunRoyaumeIa() {
        LineageState s = new LineageState();
        UUID realm = UUID.randomUUID();
        House h = new LineageGenerator(9).court(s, "Aubépine", 0xB3261E, realm, "Mathilde", true, 44,
                List.of(new LineageGenerator.Counsellor("Bérenger", false, 39), new LineageGenerator.Counsellor("Aude", true, 33)));
        Person ruler = s.person(h.head()).orElseThrow();
        assertEquals("Mathilde", ruler.name(), "le souverain que le monde connaît");
        assertTrue(ruler.female());
        assertEquals(44, ruler.age());
        assertEquals(Role.SOUVERAIN, ruler.role());
        assertTrue(s.person(ruler.father()).isPresent() && s.person(ruler.mother()).isPresent(), "ses parents figurent au registre");
        List<Person> council = s.persons().stream().filter(p -> p.role() == Role.CONSEILLER).toList();
        assertEquals(2, council.size());
        assertTrue(council.stream().allMatch(p -> realm.equals(p.realm()) && p.house() == null), "des conseillers au service du royaume");
        assertCoherent(s);
    }

    @Test
    void unVillageDeFamillesEtUnOrdreDeMembresSeuls() {
        LineageState s = new LineageState();
        Community village = new LineageGenerator(3).community(s, "Saint-Aubin", CommunityKind.VILLAGE, null, 8);
        List<Person> villagers = s.residents(village.id());
        assertTrue(villagers.size() >= 8, "au moins un adulte par foyer");
        assertTrue(villagers.stream().anyMatch(p -> p.father() != null || p.mother() != null), "des enfants dans le village");
        for (Person p : villagers) s.person(p.spouse()).ifPresent(sp -> assertEquals(p.surname(), sp.surname(), "un foyer porte le même nom"));
        assertCoherent(s);
        Community order = new LineageGenerator(4).community(s, "Ordre du Lys", CommunityKind.ORDRE, null, 6);
        List<Person> knights = s.residents(order.id());
        assertEquals(6, knights.size());
        assertTrue(knights.stream().allMatch(p -> p.spouse() == null && p.father() == null && p.age() >= 18), "des membres seuls et adultes");
        assertTrue(knights.stream().allMatch(p -> CommunityKind.ORDRE.roles().contains(p.role())));
    }

    @Test
    void unEnfantEtUnConjointDePlus() {
        LineageState s = new LineageState();
        House h = new House(UUID.randomUUID(), "Aubépine", 0);
        s.add(h);
        Person young = new Person(UUID.randomUUID(), "Raoul", false, 15);
        young.setHouse(h.id());
        s.add(young);
        LineageGenerator gen = new LineageGenerator(11);
        assertTrue(gen.child(s, young).isEmpty(), "un enfant de quinze ans ne peut être parent");
        Person lord = new Person(UUID.randomUUID(), "Godefroy", false, 40);
        lord.setHouse(h.id());
        s.add(lord);
        Person wife = gen.spouse(s, lord);
        assertTrue(wife.female() && wife.house() == null, "une épouse venue d'ailleurs");
        assertTrue(wife.surname().startsWith("de ") || wife.surname().startsWith("d'"), "une épouse noble : " + wife.surname());
        Person child = gen.child(s, lord).orElseThrow();
        assertEquals(h.id(), child.house(), "l'enfant prend la maison de son père");
        assertEquals(lord.id(), child.father());
        assertEquals(wife.id(), child.mother());
        assertCoherent(s);
    }

    @Test
    void nomsCompletsEtElision() {
        LineageState s = new LineageState();
        House a = new House(UUID.randomUUID(), "Aubépine", 0), v = new House(UUID.randomUUID(), "Valcourt", 0);
        s.add(a);
        s.add(v);
        Person aude = new Person(UUID.randomUUID(), "Aude", true, 20);
        aude.setHouse(a.id());
        Person raoul = new Person(UUID.randomUUID(), "Raoul", false, 30);
        raoul.setHouse(v.id());
        Person jean = new Person(UUID.randomUUID(), "Josselin", false, 30);
        jean.setSurname("Lefèvre");
        assertEquals("Aude d'Aubépine", s.fullName(aude));
        assertEquals("Raoul de Valcourt", s.fullName(raoul));
        assertEquals("Josselin Lefèvre", s.fullName(jean));
    }

    @Test
    void supprimerUneMaisonEmporteSesGens() {
        LineageState s = new LineageState();
        House h = new LineageGenerator(21).house(s, "Cendrecourt", 0, null, 3);
        Community c = new LineageGenerator(22).community(s, "Saint-Aubin", CommunityKind.VILLAGE, null, 2);
        int villagers = s.residents(c.id()).size();
        s.removeHouse(h.id());
        assertTrue(s.house(h.id()).isEmpty());
        assertEquals(villagers, s.persons().size(), "seuls les villageois restent, conjoints de la maison compris partis");
        for (Person p : s.persons()) {
            assertTrue(p.spouse() == null || s.person(p.spouse()).isPresent(), "aucun lien vers une personne disparue");
        }
    }

    @Test
    void leRegistreSauvegarde() {
        LineageState s = new LineageState();
        UUID realm = UUID.randomUUID();
        House h = new LineageGenerator(31).house(s, "Givrefond", 0x1F4E9C, realm, 3);
        Community c = new LineageGenerator(32).community(s, "Guilde des drapiers", CommunityKind.GUILDE, realm, 3);
        Person someone = s.members(h.id()).get(0);
        someone.setInWorld(net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), new net.minecraft.core.BlockPos(10, 64, -5));
        LineageState back = LineageState.load(s.save());
        assertEquals(s.persons().size(), back.persons().size());
        assertEquals(h.head(), back.house(h.id()).orElseThrow().head());
        assertEquals(realm, back.house(h.id()).orElseThrow().realm());
        assertEquals(CommunityKind.GUILDE, back.community(c.id()).orElseThrow().kind());
        Person again = back.person(someone.id()).orElseThrow();
        assertTrue(again.inWorld());
        assertEquals(someone.role(), again.role());
        assertEquals(s.fullName(someone), back.fullName(again));
        assertTrue(LineageState.load(new net.minecraft.nbt.CompoundTag()).persons().isEmpty(), "une ancienne sauvegarde : registre vide");
    }

    @Test
    void laVueVoyageIntacte() {
        UUID id = UUID.randomUUID();
        LineageView v = new LineageView(true, true,
                List.of(new LineageView.HouseLine(UUID.randomUUID(), "Aubépine", 0x2E7D32, Component.literal("Comté de Valcourt"), 5, 8)),
                List.of(new LineageView.CommunityLine(UUID.randomUUID(), "Saint-Aubin", CommunityKind.VILLAGE, Component.literal("Sans royaume"), 12)),
                List.of(new LineageView.RealmChoice(UUID.randomUUID(), Component.literal("Royaume de Valbrume"))),
                new LineageView.Selection(true, id, "Aubépine", Component.literal("Comté de Valcourt"), null,
                        List.of(new LineageView.Row(UUID.randomUUID(), "Aude d'Aubépine", true, 20, true, Role.HERITIER, 2, "Raoul", true, true))));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        v.write(buf);
        LineageView back = LineageView.read(buf);
        assertTrue(back.canEdit() && back.mca());
        assertEquals("Aubépine", back.houses().get(0).name());
        assertEquals(CommunityKind.VILLAGE, back.communities().get(0).kind());
        assertEquals(id, back.selection().id());
        assertEquals(Role.HERITIER, back.selection().rows().get(0).role());
        assertEquals("Raoul", back.selection().rows().get(0).spouse());
    }
}
