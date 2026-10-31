package fr.crusadecraft.core.war;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Les levées en chair et en os (P23) : combien de soldats pour tant d'hommes. */
class ArmyRulesTest {

    @Test
    void unSoldatPourDixHommesPlafonne() {
        assertEquals(6, ArmyService.soldiersFor(60, 10, 24));
        assertEquals(1, ArmyService.soldiersFor(4, 10, 24), "quelques hommes font au moins un soldat");
        assertEquals(0, ArmyService.soldiersFor(0, 10, 24));
        assertEquals(24, ArmyService.soldiersFor(5000, 10, 24), "le reste de l'armée reste un chiffre");
        assertEquals(60, ArmyService.soldiersFor(60, 0, 100), "un homme par soldat au moins");
    }
}
