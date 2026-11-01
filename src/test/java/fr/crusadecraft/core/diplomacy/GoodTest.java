package fr.crusadecraft.core.diplomacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les marchandises et les caravanes (P29), en logique pure. */
class GoodTest {
    private static Good.Land land(boolean water, boolean mountain, boolean forest, boolean dry, boolean cold, boolean warm) {
        return new Good.Land(water, mountain, forest, false, false, false, dry, cold, warm);
    }

    @Test
    void chaquePaysSaMarchandise() {
        assertEquals(Good.POISSON, Good.of(land(true, true, true, false, false, false)), "l'eau d'abord");
        assertEquals(Good.FER, Good.of(land(false, true, true, false, false, false)), "puis le relief");
        assertEquals(Good.SEL, Good.of(land(false, false, false, true, false, true)));
        assertEquals(Good.FOURRURES, Good.of(land(false, false, true, false, true, false)), "le froid avant la forêt");
        assertEquals(Good.BOIS, Good.of(land(false, false, true, false, false, false)));
        assertEquals(Good.VIN, Good.of(land(false, false, false, false, false, true)));
        assertEquals(Good.BLE, Good.of(land(false, false, false, false, false, false)), "le blé à défaut");
    }

    @Test
    void desMarchandisesDifferentesRapportentDavantage() {
        long same = TradeService.income(2, 1200, Good.BLE, Good.BLE);
        long different = TradeService.income(2, 1200, Good.BLE, Good.FER);
        assertEquals(TradeService.income(2, 1200), same, "la même marchandise : comme avant");
        assertEquals(same + TradeService.GOODS_BONUS, different);
    }

    @Test
    void laCaravaneVaEtVient() {
        double[] p = CaravanService.advance(0, 1, 100, 1000);
        assertEquals(0.1, p[0], 1e-9);
        assertEquals(1, (int) p[1]);
        double[] end = CaravanService.advance(0.95, 1, 100, 1000);
        assertEquals(1, end[0], 1e-9);
        assertEquals(-1, (int) end[1], "arrivée au bout, elle repart");
        double[] back = CaravanService.advance(0.05, -1, 100, 1000);
        assertEquals(0, back[0], 1e-9);
        assertEquals(1, (int) back[1]);
        assertTrue(CaravanService.loot(4) >= 4, "le pillage rapporte au moins un jour de revenu");
        assertEquals(CaravanService.key(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
                CaravanService.key(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")), "la clé ne dépend pas de l'ordre");
    }
}
