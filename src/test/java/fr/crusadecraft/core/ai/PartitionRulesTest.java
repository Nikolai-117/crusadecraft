package fr.crusadecraft.core.ai;

import fr.crusadecraft.core.realm.TitleRank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Le partage d'un royaume de l'ordinateur et la part d'un prétendant (P26), en logique pure. */
class PartitionRulesTest {
    private static PartitionService.Seat seat(int id, int x, boolean capital, int population) {
        return new PartitionService.Seat(id, x, 0, capital, population);
    }

    private static final List<PartitionService.Seat> FIVE = List.of(seat(1, 0, true, 50), seat(2, 100, false, 20), seat(3, 900, false, 30),
            seat(4, 1000, false, 10), seat(5, -200, false, 25));

    @Test
    void leCadetEmporteLesVillesLointainesDUnSeulTenant() {
        List<Integer> share = PartitionService.partitionShare(FIVE);
        assertEquals(List.of(4, 3), share, "les deux villes les plus éloignées, voisines l'une de l'autre");
        assertFalse(share.contains(1), "jamais la capitale");
        assertTrue(PartitionService.partitionShare(FIVE.subList(0, 2)).isEmpty(), "deux villes ne se partagent pas");
        assertEquals(1, PartitionService.partitionShare(FIVE.subList(0, 3)).size(), "trois villes : une au cadet");
    }

    @Test
    void lePretendantPrendUnTiers() {
        assertEquals(1, PartitionService.crisisShare(FIVE).size());
        assertEquals(List.of(2), PartitionService.crisisShare(FIVE.subList(0, 2)), "deux villes : le prétendant prend l'autre");
        assertTrue(PartitionService.crisisShare(FIVE.subList(0, 1)).isEmpty(), "une seule ville : pas de fief à prendre");
    }

    @Test
    void laCapitaleEtLeRangDuNouveauRoyaume() {
        assertEquals(3, PartitionService.newCapital(List.of(seat(3, 900, false, 30), seat(4, 1000, false, 10))), "la plus peuplée");
        assertEquals(TitleRank.DUCHE, PartitionService.cadetRank(TitleRank.ROYAUME));
        assertEquals(TitleRank.COMTE, PartitionService.cadetRank(TitleRank.COMTE), "comté au moins");
        assertEquals(TitleRank.COMTE, PartitionService.cadetRank(TitleRank.BARONNIE));
    }
}
