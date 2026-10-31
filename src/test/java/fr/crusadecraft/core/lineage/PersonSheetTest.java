package fr.crusadecraft.core.lineage;

import fr.crusadecraft.core.character.Trait;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La fiche d'une personne et sa journée (P20). */
class PersonSheetTest {

    @Test
    void lesTraitsContrairesNeVontPasEnsemble() {
        Person p = new Person(UUID.randomUUID(), "Aymeric", false, 30);
        p.setTraits(List.of(Trait.BRAVE, Trait.LACHE, Trait.BRAVE, Trait.JUSTE, Trait.CALME, Trait.PIEUX, Trait.LOYAL), Person.MAX_TRAITS);
        assertEquals(List.of(Trait.BRAVE, Trait.JUSTE, Trait.CALME, Trait.PIEUX), p.traits(), "ni contraire, ni doublon, et quatre au plus");
        assertTrue((p.traitMask() & (1 << Trait.JUSTE.ordinal())) != 0);
    }

    @Test
    void laFicheSurvitALaSauvegarde() {
        Person p = new Person(UUID.randomUUID(), "Bérengère", true, 41);
        p.setTraits(List.of(Trait.AVARE, Trait.FOURBE), Person.MAX_TRAITS);
        p.setBio("Dame de Clairval.\nElle tient les comptes.");
        Person back = Person.load(p.save());
        assertEquals(p.traits(), back.traits());
        assertEquals("Dame de Clairval.\nElle tient les comptes.", back.bio());
        Person old = Person.load(new Person(UUID.randomUUID(), "Ancien", false, 50).save());
        assertTrue(old.traits().isEmpty() && old.bio().isEmpty(), "une personne d'avant la fiche se lit sans traits ni texte");
    }

    @Test
    void uneBiographieNePortePasDeCodes() {
        assertEquals("Ligne un\nLigne deux", LineageService.cleanBio(" §cLigne un\nLigne deux "));
        assertEquals(Person.BIO_MAX, LineageService.cleanBio("x".repeat(900)).length());
    }

    @Test
    void laJourneeDeLaCour() {
        assertEquals(RoutineService.Goal.COURT, RoutineService.goal(6000, false), "midi : à la cour");
        assertEquals(RoutineService.Goal.HOME, RoutineService.goal(13000, false), "le soir : chez soi");
        assertEquals(RoutineService.Goal.HOME, RoutineService.goal(500, false), "à l'aube : encore chez soi");
        assertEquals(RoutineService.Goal.COURT, RoutineService.goal(18000, true), "en guerre : réfugié à la cour, même la nuit");
        assertEquals(RoutineService.Goal.COURT, RoutineService.goal(24000L * 7 + 2000, false), "les jours suivants aussi");
        assertFalse(RoutineService.goal(24000L * 7 + 12000, false) == RoutineService.Goal.COURT);
    }
}
