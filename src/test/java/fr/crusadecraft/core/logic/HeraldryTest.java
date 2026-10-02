package fr.crusadecraft.core.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeraldryTest {

    @Test
    void chaqueEmailEstSonPropreVoisin() {
        for (Heraldry h : Heraldry.values()) {
            assertEquals(h, Heraldry.nearest(h.rgb()));
        }
    }

    @Test
    void indexHorsBornesRamene() {
        assertEquals(Heraldry.GUEULES, Heraldry.byOrdinal(Heraldry.values().length));
        assertEquals(Heraldry.TURQUOISE, Heraldry.byOrdinal(-1));
    }
}
