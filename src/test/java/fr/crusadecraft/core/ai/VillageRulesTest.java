package fr.crusadecraft.core.ai;

import fr.crusadecraft.core.build.SiteKind;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les villages de l'ordinateur (P28), en logique pure : rang selon les quartiers, places des quartiers. */
class VillageRulesTest {

    @Test
    void deHameauEnVille() {
        assertEquals("crusadecraft.village.size.hameau", VillageService.sizeKey(0));
        assertEquals("crusadecraft.village.size.bourg", VillageService.sizeKey(1));
        assertEquals("crusadecraft.village.size.bourg", VillageService.sizeKey(2));
        assertEquals("crusadecraft.village.size.ville", VillageService.sizeKey(VillageService.MAX_QUARTERS));
    }

    @Test
    void lesQuartiersNeTouchentNiLeVillageNiEntreEux() {
        int ring = VillageService.ring();
        assertTrue(ring >= (SiteKind.VILLAGE.size() + SiteKind.QUARTIER.size()) / 2 + 3, "un quartier mordrait sur le village");
        List<int[]> spots = VillageService.quarterSpots(ring);
        Set<String> distinct = new HashSet<>();
        for (int[] s : spots) distinct.add(s[0] + "," + s[1]);
        assertEquals(8, distinct.size());
        // Deux quartiers voisins sur l'anneau ne se chevauchent pas.
        for (int[] a : spots) {
            for (int[] b : spots) {
                if (a == b) continue;
                boolean apart = Math.abs(a[0] - b[0]) >= SiteKind.QUARTIER.size() + 3 || Math.abs(a[1] - b[1]) >= SiteKind.QUARTIER.size() + 3;
                assertTrue(apart, "deux quartiers se chevauchent : " + a[0] + "," + a[1] + " et " + b[0] + "," + b[1]);
            }
        }
    }
}
