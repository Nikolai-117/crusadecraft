package fr.crusadecraft.core.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRulesTest {

    @Test
    void retireLesCodesDeMiseEnForme() {
        assertEquals("Valbrume", TextRules.clean("§6Val§lbrume", 32));
    }

    @Test
    void retireLesCaracteresDeControleEtReduitLesEspaces() {
        assertEquals("Royaume de Valbrume", TextRules.clean("  Royaume\t de \n\n Valbrume \u0007", 32));
    }

    @Test
    void borneLaLongueur() {
        assertEquals("abcde", TextRules.clean("abcdefgh", 5));
        // Les espaces sont fusionnés avant la coupe, puis l'espace final éventuel est retiré.
        assertEquals("abc d", TextRules.clean("abc   defgh", 5));
        assertEquals("abc", TextRules.clean("abc    d", 4));
    }

    @Test
    void garderLesAccentsEtLaPonctuation() {
        assertEquals("Maison d'Aubépine", TextRules.clean("Maison d'Aubépine", 32));
    }

    @Test
    void nomValide() {
        assertTrue(TextRules.isValidName("Ys"));
        assertFalse(TextRules.isValidName("Y"));
        assertFalse(TextRules.isValidName(""));
        assertFalse(TextRules.isValidName(TextRules.clean("§§§", 32)));
    }

    @Test
    void nullDevientVide() {
        assertEquals("", TextRules.clean(null, 10));
    }
}
