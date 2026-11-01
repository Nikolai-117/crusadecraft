package fr.crusadecraft.core.war;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les formations et la marche des ostes (P27), en logique pure. */
class FormationTest {

    @Test
    void chacunASaPlace() {
        for (Formation f : Formation.values()) {
            Set<String> spots = new HashSet<>();
            for (int i = 0; i < 24; i++) {
                double[] o = f.offset(i, 24, 0.7);
                spots.add(Math.round(o[0] * 10) + "," + Math.round(o[1] * 10));
            }
            assertEquals(24, spots.size(), "deux soldats à la même place en " + f);
        }
    }

    @Test
    void laLigneFaitFaceEtLaColonneSuit() {
        // Face aux x croissants : la ligne s'étale en z, la colonne s'allonge vers les x décroissants.
        double spreadZ = 0, spreadX = 0;
        for (int i = 0; i < 8; i++) {
            double[] o = Formation.LIGNE.offset(i, 16, 0);
            spreadZ = Math.max(spreadZ, Math.abs(o[1]));
            spreadX = Math.max(spreadX, Math.abs(o[0]));
        }
        assertTrue(spreadZ > spreadX, "la ligne ne s'étale pas sur le côté");
        double[] last = Formation.COLONNE.offset(15, 16, 0);
        assertTrue(last[0] < -10, "la colonne ne s'allonge pas derrière le point : " + last[0]);
        assertEquals(Formation.LIGNE, Formation.MASSE.next());
        assertEquals(Formation.MASSE, Formation.CARRE.next());
    }

    @Test
    void lOstAvanceSansDepasserSonBut() {
        double[] p = HostService.step(0, 0, 100, 0, 6);
        assertEquals(6, p[0], 1e-9);
        double[] near = HostService.step(70, 0, 100, 0, 50);
        assertEquals(100 - HostService.ARRIVED, near[0], 1e-9, "l'ost s'arrête avant son but");
        double[] there = HostService.step(90, 0, 100, 0, 6);
        assertEquals(90, there[0], 1e-9, "arrivé, l'ost ne bouge plus");
    }
}
