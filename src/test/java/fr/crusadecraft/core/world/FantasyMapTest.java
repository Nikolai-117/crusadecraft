package fr.crusadecraft.core.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carte fantasy (P32) : où écrire le nom d'un royaume, et comment l'écrire. */
class FantasyMapTest {

    private static void square(LongOpenHashSet set, int x0, int z0, int size) {
        for (int x = x0; x < x0 + size; x++) for (int z = z0; z < z0 + size; z++) set.add(TerritoryMap.key(x, z));
    }

    @Test
    void leNomSEcritAuCoeurDeLaPlusGrandeTerre() {
        LongOpenHashSet land = new LongOpenHashSet();
        square(land, 0, 0, 9);
        square(land, 40, 40, 2);
        int[] at = WebMapGeometry.labelPoint(land);
        assertEquals(4 * 16 + 8, at[0], "au milieu du grand carré, en x");
        assertEquals(4 * 16 + 8, at[1], "au milieu du grand carré, en z");
        assertEquals(81, at[2], "la plus grande terre compte 81 chunks");
        assertNull(WebMapGeometry.labelPoint(new LongOpenHashSet()));
    }

    @Test
    void leNomEviteLesVilles() {
        LongOpenHashSet land = new LongOpenHashSet();
        square(land, 0, 0, 9);
        LongOpenHashSet city = new LongOpenHashSet();
        for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) city.add(TerritoryMap.key(x, z));
        int[] at = WebMapGeometry.labelPoint(land, city);
        int cx = at[0] / 16, cz = at[1] / 16;
        assertFalse(city.contains(TerritoryMap.key(cx, cz)), "le nom recouvre la ville");
    }

    @Test
    void uneTerreEnLongueurGardeSonNomAuMilieu() {
        LongOpenHashSet land = new LongOpenHashSet();
        for (int x = 0; x < 21; x++) for (int z = 0; z < 3; z++) land.add(TerritoryMap.key(x, z));
        int[] at = WebMapGeometry.labelPoint(land);
        assertEquals(10 * 16 + 8, at[0], "le nom ne file pas vers un bout");
        assertEquals(16 + 8, at[1]);
    }

    @Test
    void lesNomsALAncienne() {
        assertEquals(14, WebMap.realmFontSize(1));
        assertEquals(26, WebMap.realmFontSize(100000));
        assertEquals(-1, WebMap.realmMinZoom(1000), "un grand royaume se lit de loin");
        assertEquals(2, WebMap.realmMinZoom(20), "un petit, en se rapprochant");
        assertTrue(WebMap.realmFontSize(400) > WebMap.realmFontSize(40), "une grande terre, un grand nom");
        String realm = WebMap.realmLabel("Duché", "Rochegarde", 300, 0x2E7D32);
        assertTrue(realm.contains("Rochegarde") && realm.contains("Duché") && realm.contains("cc-nom"));
        String city = WebMap.placeLabel("Khalyos <b>", WebMap.Tier.CAPITAL);
        assertTrue(city.contains("Khalyos &lt;b&gt;"), "le nom est échappé");
        assertTrue(city.contains("small-caps"), "une capitale en petites capitales");
        assertFalse(WebMap.placeLabel("Saint-Aubin", WebMap.Tier.VILLAGE).contains("small-caps"));
        assertEquals(WebMap.ICON_CAPITAL, WebMap.Tier.CAPITAL.icon);
    }
}
