package fr.crusadecraft.core.ai;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.realm.TitleRank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les civilisations autonomes (P22) : l'ordre des constructions et la zone permise. */
class CivRulesTest {

    @Test
    void uneTourDAbordPuisUnCampPuisSelonLeRang() {
        assertEquals(SiteKind.TOUR, CivService.nextKind(0, TitleRank.BARONNIE));
        assertEquals(SiteKind.CAMP, CivService.nextKind(1, TitleRank.ROYAUME));
        assertEquals(SiteKind.AVANT_POSTE, CivService.nextKind(2, TitleRank.COMTE));
        assertEquals(SiteKind.FORT, CivService.nextKind(2, TitleRank.DUCHE));
        assertEquals(SiteKind.FORT, CivService.nextKind(3, TitleRank.EMPIRE));
    }

    @Test
    void laZonePermise() {
        assertTrue(CivService.inZone(1000, 1000, 0), "0 : partout");
        assertTrue(CivService.inZone(300, 400, 500));
        assertFalse(CivService.inZone(300, 401, 500));
        assertFalse(CivService.inZone(-90000, 0, 3000));
    }
}
