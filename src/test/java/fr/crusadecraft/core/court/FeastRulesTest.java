package fr.crusadecraft.core.court;

import fr.crusadecraft.core.lineage.LifeService;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.realm.TitleRank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le banquet et le deuil (P26) : ce qu'il coûte, ce qu'il rapporte, de qui l'on porte le deuil. */
class FeastRulesTest {
    @Test
    void lePrixEtLePrestige() {
        assertEquals(60, FeastService.cost(TitleRank.BARONNIE));
        assertEquals(300, FeastService.cost(TitleRank.EMPIRE));
        assertTrue(FeastService.prestige(TitleRank.ROYAUME, 3) > FeastService.prestige(TitleRank.ROYAUME, 0), "les invités rapportent");
    }

    @Test
    void deQuiPorteTOnLeDeuil() {
        assertTrue(LifeService.mourned(true, Role.PAYSAN), "un chef de maison");
        assertTrue(LifeService.mourned(false, Role.SOUVERAIN));
        assertTrue(LifeService.mourned(false, Role.CONSORT));
        assertFalse(LifeService.mourned(false, Role.CHEVALIER), "pas de chacun");
    }
}
