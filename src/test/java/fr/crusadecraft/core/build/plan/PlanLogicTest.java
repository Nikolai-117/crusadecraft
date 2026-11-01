package fr.crusadecraft.core.build.plan;

import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La logique pure des plans (P34) : références, règles des blocs, niveaux, entiers variables, plan des villes. */
class PlanLogicTest {

    @Test
    void referencesAllerRetour() {
        PlanRef pack = new PlanRef(PlanRef.Source.PACK, "Medieval Oak", "fundamentals/townhall3.blueprint");
        assertEquals(pack, PlanRef.decode(pack.encode()).orElseThrow());
        PlanRef file = new PlanRef(PlanRef.Source.FILE, "plans", "maisons/moulin.schem");
        assertEquals(file, PlanRef.decode(file.encode()).orElseThrow());
        assertTrue(PlanRef.decode("pack:sans-barre").isEmpty());
        assertTrue(PlanRef.decode("http:x|y").isEmpty());
        assertEquals("townhall3", pack.label());
        assertEquals("fundamentals", pack.category());
        assertEquals("medievaloak", PlanRef.normalize("Medieval Oak"));
        assertEquals("byzantinefor120", PlanRef.normalize("Byzantine_for_1.20"));
    }

    @Test
    void reglesDesBlocs() {
        assertEquals(PlanRules.Kind.SKIP, PlanRules.classify("structurize:blocksubstitution"));
        assertEquals(PlanRules.Kind.SOLID, PlanRules.classify("structurize:blocksolidsubstitution"));
        assertEquals(PlanRules.Kind.FLUID, PlanRules.classify("structurize:blockfluidsubstitution"));
        assertEquals(PlanRules.Kind.TAG, PlanRules.classify("structurize:blocktagsubstitution"));
        assertEquals(PlanRules.Kind.HUT, PlanRules.classify("minecolonies:blockhuttownhall"));
        assertEquals(PlanRules.Kind.AIR, PlanRules.classify("minecolonies:blockwaypoint"));
        assertEquals(PlanRules.Kind.COLONY, PlanRules.classify("minecolonies:blockminecoloniesrack"));
        assertEquals(PlanRules.Kind.KEEP, PlanRules.classify("domum_ornamentum:architectscutter"));
        assertEquals(PlanRules.Kind.KEEP, PlanRules.classify("minecraft:stone"));
        assertEquals("townhall", PlanRules.hutType("minecolonies:blockhuttownhall"));
        assertEquals("", PlanRules.hutType("minecraft:barrel"));
    }

    @Test
    void niveauxDesPlans() {
        assertEquals(3, PlanRules.level("townhall3"));
        assertEquals(0, PlanRules.level("citysquare"));
        assertEquals(0, PlanRules.level("lot2023"));
        assertEquals("townhall", PlanRules.base("townhall3"));
        assertEquals("single_a", PlanRules.base("single_a2"));
    }

    @Test
    void entiersVariables() {
        int[] values = {0, 1, 127, 128, 300, 16384, 2_000_000};
        assertArrayEquals(values, Varints.decode(Varints.encode(values), values.length));
        assertArrayEquals(new int[]{5, 0, 0}, Varints.decode(new byte[]{5}, 3));
    }

    @Test
    void reconnaitreLesBatiments() {
        assertTrue(TownPlanner.matches(TownSize.Role.TOWNHALL, "fundamentals/townhall3.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.TOWNHALL, "fundamentals/town_hall/townhall2.blueprint"));
        assertFalse(TownPlanner.matches(TownSize.Role.TOWNHALL, "fundamentals/town_hall/insulaeguard1.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.HOUSE, "fundamentals/residence2.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.HOUSE, "fundamentals/house/single_a3.blueprint"));
        assertFalse(TownPlanner.matches(TownSize.Role.HOUSE, "fundamentals/house/houseguard1.blueprint"));
        assertFalse(TownPlanner.matches(TownSize.Role.HOUSE, "fundamentals/house/dormi2.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.FARM, "agriculture/horticulture/farmer4.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.PLAZA, "decorations/misc/citysquare.blueprint"));
        assertTrue(TownPlanner.matches(TownSize.Role.CHURCH, "mystic/temple_isis3.blueprint"));
        assertFalse(TownPlanner.matches(TownSize.Role.TAVERN, "fundamentals/tavern/bouncer1.blueprint"));
    }

    @Test
    void lesTaillesDeVille() {
        assertEquals(TownSize.CAPITALE, TownSize.forCity(5, true));
        assertEquals(TownSize.VILLE, TownSize.forCity(50, false));
        assertEquals(TownSize.BOURG, TownSize.forCity(28, false));
        assertEquals(TownSize.HAMEAU, TownSize.forCity(12, false));
        assertTrue(TownSize.CAPITALE.program().size() > TownSize.VILLE.program().size());
        assertFalse(TownSize.QUARTIER.hasCenter());
    }

    @Test
    void lesBatimentsNeSeChevauchentPas() {
        TownLayout.Box hall = new TownLayout.Box(27, 21), plaza = new TownLayout.Box(9, 9);
        List<TownLayout.Box> others = new ArrayList<>();
        for (int i = 0; i < 12; i++) others.add(new TownLayout.Box(11 + i % 4 * 3, 9 + i % 3 * 4));
        List<TownLayout.Spot> spots = TownLayout.arrange(hall, plaza, others, 7);
        List<TownLayout.Box> all = new ArrayList<>();
        all.add(hall);
        all.add(plaza);
        all.addAll(others);
        assertEquals(all.size(), spots.size());
        for (int i = 0; i < all.size(); i++) {
            assertNotNull(spots.get(i), "bâtiment " + i + " sans place");
            TownLayout.Rect a = TownLayout.rect(all.get(i), spots.get(i));
            for (int j = i + 1; j < all.size(); j++) {
                TownLayout.Rect b = TownLayout.rect(all.get(j), spots.get(j));
                assertFalse(a.overlaps(b, TownLayout.STREET - 1), "bâtiments " + i + " et " + j + " trop proches");
            }
        }
        // Chaque bâtiment autour tourne son entrée vers le centre.
        for (int i = 2; i < all.size(); i++) {
            TownLayout.Spot s = spots.get(i);
            int[] f = PlanBuilder.turn(0, 1, s.rotation());
            assertTrue(f[0] * -s.x() + f[1] * -s.z() > 0, "bâtiment " + i + " tourne le dos au centre");
        }
        // Les rues ne passent sous aucun bâtiment, et la ville tient dans son emprise.
        int extent = TownLayout.extent(all, spots);
        for (int[] c : TownLayout.streets(all, spots)) {
            for (int i = 0; i < all.size(); i++) assertFalse(TownLayout.rect(all.get(i), spots.get(i)).contains(c[0], c[1]), "rue sous un bâtiment");
            assertTrue(Math.abs(c[0]) <= extent / 2 + 2 && Math.abs(c[1]) <= extent / 2 + 2, "rue hors de l'emprise");
        }
    }

    @Test
    void rotationsComposees() {
        assertArrayEquals(new int[]{-3, 2}, PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90));
        assertArrayEquals(new int[]{2, 3}, PlanBuilder.turn(PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90)[0],
                PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90)[1], Rotation.COUNTERCLOCKWISE_90));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, PlanBuilder.inverse(Rotation.CLOCKWISE_90));
    }
}
