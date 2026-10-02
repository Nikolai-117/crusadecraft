package fr.crusadecraft.core.law;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.Trait;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawLogicTest {

    @Test
    void niveauxParDefautEtBornes() {
        LawState s = new LawState();
        assertEquals(1, s.level(Law.FISCALITE), "fiscalité normale");
        assertEquals(1, s.level(Law.AUTORITE), "autorité limitée");
        assertEquals(0, s.level(Law.CODE_CHEVALERESQUE));
        s.set(Law.AUTORITE, 9, 4);
        assertEquals(3, s.level(Law.AUTORITE), "borné au dernier niveau");
        assertEquals(2, s.cooldownLeft(Law.AUTORITE, 5, 3));
        assertEquals(0, s.cooldownLeft(Law.AUTORITE, 7, 3));
        assertEquals(0, s.cooldownLeft(Law.FISCALITE, 0, 3), "jamais changée");
    }

    @Test
    void lesNiveauxFortsDemandentUneRecherche() {
        assertEquals(null, Law.FISCALITE.levels().get(2).research());
        assertEquals(Law.Research.CHANCELLERIE, Law.AUTORITE.levels().get(2).research());
        assertEquals(Law.Research.AUTORITE_ROYALE, Law.AUTORITE.levels().get(3).research());
        assertEquals(Law.Research.CHEVALERIE, Law.CODE_CHEVALERESQUE.levels().get(1).research());
        assertEquals("crusadecraft:effects/chancellerie", Law.Research.CHANCELLERIE.toString());
    }

    @Test
    void allerRetour() {
        LawState s = new LawState();
        s.set(Law.FISCALITE, 2, 10);
        s.set(Law.JUSTICE_ROYALE, 1, 11);
        LawState back = LawState.load(s.save());
        assertEquals(2, back.level(Law.FISCALITE));
        assertEquals(1, back.level(Law.JUSTICE_ROYALE));
        assertEquals(11, back.changedDay(Law.JUSTICE_ROYALE));
        assertEquals(1, back.level(Law.AUTORITE), "non enregistrée : défaut");
    }

    @Test
    void effetsChiffres() {
        LawState s = new LawState();
        assertEquals(1.0, LawEffects.fiscalMultiplier(s), 1e-9);
        assertEquals(1.0, LawEffects.taxHappinessFactor(s), 1e-9, "fiscalité normale : bonheur neutre");
        assertEquals(0.8, LawEffects.provinceMultiplier(s), 1e-9);
        assertEquals(0, LawEffects.dailyPrestige(s));
        s.set(Law.FISCALITE, 2, 0);
        s.set(Law.AUTORITE, 3, 0);
        s.set(Law.CODE_CHEVALERESQUE, 1, 0);
        s.set(Law.JUSTICE_ROYALE, 1, 0);
        assertEquals(1.4, LawEffects.fiscalMultiplier(s), 1e-9);
        assertTrue(LawEffects.taxHappinessFactor(s) < 1);
        assertEquals(1.2, LawEffects.provinceMultiplier(s), 1e-9);
        assertEquals(3, LawEffects.dailyPrestige(s));
        assertEquals(2, LawEffects.dreadDecayEveryDays(s));
        assertEquals(1.5, LawEffects.trainingMultiplier(s), 1e-9);
    }

    @Test
    void opinionSelonLesTraits() {
        LawState s = new LawState();
        s.set(Law.FISCALITE, 2, 0);
        s.set(Law.AUTORITE, 3, 0);
        int greedyAmbitious = Opinion.total(LawEffects.opinionLines(s, List.of(Trait.AVARE, Trait.AMBITIEUX), true));
        assertEquals(-15 - 30, greedyAmbitious);
        int loyal = Opinion.total(LawEffects.opinionLines(s, List.of(Trait.LOYAL), true));
        assertEquals(-10 - 7, loyal, "−15 divisé par deux, arrondi vers zéro");
        int commoner = Opinion.total(LawEffects.opinionLines(s, List.of(Trait.LOYAL), false));
        assertEquals(-10, commoner, "l'autorité ne touche que les dignitaires");
        assertTrue(LawEffects.opinionLines(new LawState(), List.of(Trait.BRAVE), true).isEmpty(), "lois par défaut : rien");
    }

    @Test
    void voteDuConseil() {
        LawEffects.Voter loyal = new LawEffects.Voter("Aldric", 20, List.of(Trait.LOYAL));
        LawEffects.Voter ambitious = new LawEffects.Voter("Odon", 0, List.of(Trait.AMBITIEUX));
        LawEffects.Voter neutral = new LawEffects.Voter("Berthe", 0, List.of());

        // Renforcer l'autorité : le loyal approuve (2 − 4 + 4), l'ambitieux et le neutre refusent.
        LawEffects.Vote up = LawEffects.vote(List.of(loyal, ambitious, neutral), Law.AUTORITE, 1, 2, 0);
        assertEquals(1, up.approvals());
        assertEquals(2, up.rejections());
        assertFalse(up.passes());
        assertEquals(40, LawEffects.forceCost(up, 20));

        // La crainte fait plier le neutre (−4 + 4 = 0 → pour) : deux voix contre une, la loi passe.
        LawEffects.Vote feared = LawEffects.vote(List.of(loyal, ambitious, neutral), Law.AUTORITE, 1, 2, 100);
        assertTrue(feared.passes());
        assertEquals(0, LawEffects.forceCost(feared, 20));

        // Alléger les impôts plaît à tous.
        assertEquals(3, LawEffects.vote(List.of(loyal, ambitious, neutral), Law.FISCALITE, 1, 0, 0).approvals());

        // Sans conseil, le souverain décide seul.
        assertTrue(LawEffects.vote(List.of(), Law.AUTORITE, 1, 3, 0).passes());

        // Un brave soutient le code chevaleresque, un lâche le combat.
        assertTrue(LawEffects.ballot(new LawEffects.Voter("B", 0, List.of(Trait.BRAVE)), Law.CODE_CHEVALERESQUE, 0, 1, 0).approves());
        assertFalse(LawEffects.ballot(new LawEffects.Voter("L", 0, List.of(Trait.LACHE)), Law.CODE_CHEVALERESQUE, 0, 1, 0).approves());

        // Une opinion négative s'arrondit vers le bas : −1 suffit à faire refuser un changement neutre.
        assertFalse(LawEffects.ballot(new LawEffects.Voter("M", -1, List.of()), Law.JUSTICE_ROYALE, 0, 1, 0).approves());
    }
}
