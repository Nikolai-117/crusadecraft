package fr.crusadecraft.core.lineage;

import fr.crusadecraft.core.character.Trait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le cycle de vie (P21) : la mort qui vient avec l'âge, et l'héritier selon la loi. */
class LifeRulesTest {

    private static Person person(String name, boolean female, int age, Person father) {
        Person p = new Person(UUID.randomUUID(), name, female, age);
        if (father != null) p.setFather(father.id());
        return p;
    }

    @Test
    void laMortVientAvecLAge() {
        assertEquals(3, LifeService.deathChancePerMille(20));
        assertTrue(LifeService.deathChancePerMille(70) > LifeService.deathChancePerMille(60));
        assertTrue(LifeService.deathChancePerMille(90) <= 900);
        assertEquals(1000, LifeService.deathChancePerMille(LifeService.MAX_AGE), "personne ne passe 100 ans");
    }

    @Test
    void lHeritierSuitLaLoi() {
        Person king = person("Roi", false, 70, null);
        Person daughter = person("Aînée", true, 40, king);
        Person son = person("Cadet", false, 35, king);
        Person uncle = person("Oncle", false, 66, null);
        uncle.setTraits(List.of(Trait.AMBITIEUX, Trait.JUSTE, Trait.BRAVE), Person.MAX_TRAITS);
        Person dead = person("Mort", false, 30, null);
        dead.setAlive(false);
        List<Person> house = List.of(king, daughter, son, uncle, dead);

        assertEquals(son, LifeService.heir(house, king, LifeService.Succession.PRIMOGENITURE).orElseThrow(), "les fils d'abord");
        assertEquals(daughter, LifeService.heir(house, king, LifeService.Succession.EGALITAIRE).orElseThrow(), "l'aînée");
        assertEquals(uncle, LifeService.heir(house, king, LifeService.Succession.ANCIENNETE).orElseThrow(), "le doyen");
        assertEquals(uncle, LifeService.heir(house, king, LifeService.Succession.ELECTIVE).orElseThrow(), "le plus marquant");
        // Sans enfant, la primogéniture revient au doyen ; un mort n'hérite jamais.
        assertEquals(uncle, LifeService.heir(List.of(king, uncle, dead), king, LifeService.Succession.PRIMOGENITURE).orElseThrow());
        assertTrue(LifeService.heir(List.of(king, dead), king, LifeService.Succession.EGALITAIRE).isEmpty());
    }
}
