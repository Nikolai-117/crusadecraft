package fr.crusadecraft.core.diplomacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Le commerce (P25) : une route lointaine rapporte davantage, jusqu'à un plafond. */
class TradeRulesTest {

    @Test
    void laDistanceRapporte() {
        assertEquals(0, TradeService.distanceBonus(150), "une route courte ne rapporte que sa part de base");
        assertEquals(2, TradeService.distanceBonus(800));
        assertEquals(TradeService.MAX_BONUS, TradeService.distanceBonus(100_000), "plafonné");
        assertEquals(0, TradeService.distanceBonus(-5));
        assertEquals(7, TradeService.income(5, 900));
    }
}
