package fr.crusadecraft.core.logic;

import fr.crusadecraft.core.logic.FoundingRules.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoundingRulesTest {

    private static Verdict check(boolean ready, boolean ruler, boolean inColony, boolean owner, boolean inTownHall,
                                 int level, boolean capital) {
        return FoundingRules.check(ready, ruler, inColony, owner, inTownHall, level, 2, capital);
    }

    @Test
    void casNominal() {
        assertEquals(Verdict.OK, check(true, false, true, true, true, 2, false));
    }

    @Test
    void rienAvantLeChargementDesColonies() {
        // Même si tout le reste est faux, le premier motif est le chargement : c'est la leçon de War 'N Taxes.
        assertEquals(Verdict.COLONIES_NOT_READY, check(false, true, false, false, false, 0, true));
    }

    @Test
    void unSeulRoyaumeParSouverain() {
        assertEquals(Verdict.ALREADY_RULER, check(true, true, true, true, true, 5, false));
    }

    @Test
    void ordreDesMotifs() {
        assertEquals(Verdict.NOT_IN_COLONY, check(true, false, false, false, false, 0, false));
        assertEquals(Verdict.NOT_OWNER, check(true, false, true, false, false, 0, false));
        assertEquals(Verdict.NOT_IN_TOWN_HALL, check(true, false, true, true, false, 5, false));
        assertEquals(Verdict.TOWN_HALL_TOO_LOW, check(true, false, true, true, true, 1, false));
        assertEquals(Verdict.COLONY_ALREADY_CAPITAL, check(true, false, true, true, true, 3, true));
    }
}
