package fr.crusadecraft.core.world;

import fr.crusadecraft.compat.ServerText;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carte web (P17) : des rectangles qui couvrent exactement les chunks, des frontières justes, des fiches sûres. */
class WebMapTest {

    private static LongOpenHashSet chunks(int[][] xz) {
        LongOpenHashSet s = new LongOpenHashSet();
        for (int[] c : xz) s.add(TerritoryMap.key(c[0], c[1]));
        return s;
    }

    /** Tous les chunks couverts une fois et une seule. */
    private static void assertExactCover(LongOpenHashSet set, List<WebMapGeometry.Rect> rects) {
        LongOpenHashSet covered = new LongOpenHashSet();
        for (WebMapGeometry.Rect r : rects) {
            for (int x = r.x0(); x <= r.x1(); x++) for (int z = r.z0(); z <= r.z1(); z++) {
                long k = TerritoryMap.key(x, z);
                assertTrue(set.contains(k), "un rectangle déborde en " + x + "," + z);
                assertTrue(covered.add(k), "deux rectangles se recouvrent en " + x + "," + z);
            }
        }
        assertEquals(set.size(), covered.size(), "des chunks ne sont pas couverts");
    }

    @Test
    void unCarrePleinFaitUnSeulRectangle() {
        LongOpenHashSet s = new LongOpenHashSet();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 4; z++) s.add(TerritoryMap.key(x, z));
        List<WebMapGeometry.Rect> rects = WebMapGeometry.rectangles(s);
        assertEquals(1, rects.size());
        assertExactCover(s, rects);
        // Sa frontière : quatre segments droits, de 80 × 64 blocs.
        List<WebMapGeometry.Segment> border = WebMapGeometry.border(s);
        assertEquals(4, border.size());
        int length = 0;
        for (WebMapGeometry.Segment g : border) length += Math.abs(g.x1() - g.x0()) + Math.abs(g.z1() - g.z0());
        assertEquals(2 * (80 + 64), length);
    }

    @Test
    void uneFormeAvecUnTrouEtUneIle() {
        // Un anneau de 5 × 5 (trou au milieu) et un chunk isolé plus loin.
        LongOpenHashSet s = new LongOpenHashSet();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) if (!(x == 2 && z == 2)) s.add(TerritoryMap.key(x, z));
        s.add(TerritoryMap.key(10, 10));
        assertExactCover(s, WebMapGeometry.rectangles(s));
        // Le trou a sa frontière : quatre côtés de 16 blocs autour du chunk (2, 2).
        int around = 0;
        for (WebMapGeometry.Segment g : WebMapGeometry.border(s)) {
            boolean inside = g.x0() >= 32 && g.x1() <= 48 && g.z0() >= 32 && g.z1() <= 48;
            if (inside) around++;
        }
        assertEquals(4, around, "le trou n'a pas sa frontière");
    }

    @Test
    void desFormesAuHasardSontToujoursCouvertes() {
        java.util.Random rnd = new java.util.Random(42);
        for (int t = 0; t < 30; t++) {
            LongOpenHashSet s = new LongOpenHashSet();
            for (int i = 0; i < 200; i++) s.add(TerritoryMap.key(rnd.nextInt(20) - 10, rnd.nextInt(20) - 10));
            assertExactCover(s, WebMapGeometry.rectangles(s));
            assertFalse(WebMapGeometry.border(s).isEmpty());
        }
    }

    @Test
    void lesFichesNeLaissentPasPasserDeCode() {
        assertEquals("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &#39;",
                WebMap.esc("<script>alert(\"x\")</script> & '"));
    }

    @Test
    void leServeurTraduitNosTextes() {
        // Une clé du mod, avec ses arguments, dont un texte traduit lui aussi.
        String s = ServerText.render(Component.translatable("crusadecraft.webmap.lands.claimed", 12, 3, 64), "fr_fr");
        assertEquals("12 chunks (revendiqués : 3 / 64)", s);
        String nested = ServerText.render(Component.translatable("crusadecraft.lieu.ruler", "Godefroy",
                Component.translatable("crusadecraft.webmap.ruler")), "fr_fr");
        assertEquals("Godefroy, Souverain", nested);
        assertEquals("inconnue.cle", ServerText.render(Component.translatable("inconnue.cle"), "fr_fr"));
        assertEquals("Lands", ServerText.render(Component.translatable("crusadecraft.webmap.lands.label"), "en_us"));
    }

    @Test
    void laPointeDUneFlecheRegardeEnArriere() {
        // Une flèche vers l'est : sa pointe part de l'arrivée et revient vers l'ouest, de part et d'autre.
        List<double[]> head = WebMap.arrowHead(0, 0, 600, 0);
        assertEquals(2, head.size());
        for (double[] h : head) {
            assertEquals(600, h[0], 1e-9);
            assertTrue(h[2] < 600, "la pointe revient vers le départ");
            assertEquals(64, Math.hypot(h[2] - 600, h[3]), 1e-6, "longueur bornée à 64 blocs");
        }
        assertTrue(head.get(0)[3] * head.get(1)[3] < 0, "un trait de chaque côté");
        assertEquals(8, Math.hypot(WebMap.arrowHead(0, 0, 20, 0).get(0)[2] - 20, WebMap.arrowHead(0, 0, 20, 0).get(0)[3]), 1e-6,
                "au moins 8 blocs");
        assertTrue(WebMap.arrowHead(5, 5, 5, 5).isEmpty(), "rien pour une flèche nulle");
    }
}
