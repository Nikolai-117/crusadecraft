package fr.crusadecraft.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** L'écran de réglages (P30) : la lecture de ce qu'on saisit, en logique pure. */
class SettingsRulesTest {
    @Test
    void onLitCeQuOnSaisit() {
        assertEquals(Boolean.TRUE, SettingsService.parse(SettingsService.Kind.BOOL, "oui", List.of()));
        assertEquals(Boolean.FALSE, SettingsService.parse(SettingsService.Kind.BOOL, " false ", List.of()));
        assertNull(SettingsService.parse(SettingsService.Kind.BOOL, "peut-être", List.of()));
        assertEquals(42, SettingsService.parse(SettingsService.Kind.INT, " 42 ", List.of()));
        assertNull(SettingsService.parse(SettingsService.Kind.INT, "4,2", List.of()));
        assertEquals(0.35, (Double) SettingsService.parse(SettingsService.Kind.DOUBLE, "0,35", List.of()), 1e-9, "la virgule française passe");
        assertEquals("CHAT", SettingsService.parse(SettingsService.Kind.ENUM, "CHAT", List.of("TITRE", "CHAT")));
        assertNull(SettingsService.parse(SettingsService.Kind.ENUM, "AUTRE", List.of("TITRE", "CHAT")));
        assertNull(SettingsService.parse(SettingsService.Kind.LIST, "a, b", List.of()), "une liste ne se saisit pas");
    }
}
