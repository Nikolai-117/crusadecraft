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

    /** Une ville de taille « ville » : hôtel de ville, place, huit maisons, taverne, deux fermes, marché, temple, caserne, tour. */
    private static List<TownSize.Role> roles() {
        List<TownSize.Role> roles = new ArrayList<>();
        roles.add(TownSize.Role.TOWNHALL);
        roles.addAll(TownSize.VILLE.program());
        return roles;
    }

    private static List<TownLayout.Box> boxes(List<TownSize.Role> roles) {
        List<TownLayout.Box> out = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            out.add(switch (roles.get(i)) {
                case TOWNHALL -> new TownLayout.Box(27, 21);
                case PLAZA -> new TownLayout.Box(9, 9);
                case CHURCH, BARRACKS -> new TownLayout.Box(19, 25);
                default -> new TownLayout.Box(9 + i % 4 * 3, 9 + i % 3 * 4);
            });
        }
        return out;
    }

    @Test
    void chaquePlanHistoriqueTientDebout() {
        List<TownSize.Role> roles = roles();
        List<TownLayout.Box> all = boxes(roles);
        for (TownLayout.Pattern pattern : TownLayout.Pattern.values()) {
            boolean walled = pattern != TownLayout.Pattern.JOBO && pattern != TownLayout.Pattern.RUE;
            TownLayout.Layout layout = TownLayout.plan(pattern, all, roles, walled, 7);
            List<TownLayout.Spot> spots = layout.spots();
            assertEquals(all.size(), spots.size());
            List<TownLayout.Rect> rects = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                assertNotNull(spots.get(i), pattern + " : bâtiment " + i + " (" + roles.get(i) + ") sans place");
                rects.add(TownLayout.rect(all.get(i), spots.get(i)));
            }
            for (int i = 0; i < rects.size(); i++) {
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(rects.get(i).overlaps(rects.get(j), 0), pattern + " : bâtiments " + i + " et " + j + " se chevauchent");
                }
            }
            int half = layout.extent() / 2;
            for (int[] c : layout.streets()) {
                for (TownLayout.Rect r : rects) assertFalse(r.contains(c[0], c[1]), pattern + " : rue sous un bâtiment");
                assertTrue(Math.abs(c[0]) <= half && Math.abs(c[1]) <= half, pattern + " : rue hors de l'emprise");
            }
            for (int[] c : layout.walls()) for (TownLayout.Rect r : rects) assertFalse(r.contains(c[0], c[1]), pattern + " : mur sous un bâtiment");
            assertEquals(walled, !layout.walls().isEmpty(), pattern + " : enceinte");
            // Les portes : l'enceinte s'ouvre là où les grandes rues la traversent.
            if (walled && pattern != TownLayout.Pattern.TRELLEBORG) {
                for (int[] c : layout.walls()) assertFalse(c[0] == 0 || c[1] == 0, pattern + " : pas de porte sur un axe");
            }
        }
    }

    @Test
    void lesPlansSuiventLesTextes() {
        List<TownSize.Role> roles = roles();
        List<TownLayout.Box> all = boxes(roles);
        int church = roles.indexOf(TownSize.Role.CHURCH), market = roles.indexOf(TownSize.Role.MARKET);
        // Kaogong ji : le palais regarde le sud, le marché est derrière lui, le temple des ancêtres à l'est.
        TownLayout.Layout k = TownLayout.plan(TownLayout.Pattern.KAOGONG, all, roles, true, 7);
        assertEquals(Rotation.NONE, k.spots().get(0).rotation());
        assertTrue(k.spots().get(market).z() < k.spots().get(0).z(), "le marché n'est pas derrière le palais");
        assertTrue(k.spots().get(church).x() > 0, "le temple des ancêtres n'est pas à l'est");
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i) == TownSize.Role.HOUSE) assertEquals(Rotation.NONE, k.spots().get(i).rotation(), "une maison ne regarde pas le sud");
        }
        // Heian-kyō : le palais au nord, sans muraille.
        TownLayout.Layout j = TownLayout.plan(TownLayout.Pattern.JOBO, all, roles, false, 7);
        for (int i = 1; i < roles.size(); i++) assertTrue(j.spots().get(i).z() > j.spots().get(0).z(), "un bâtiment est au nord du palais");
        // Castrum : le forum au croisement, la basilique au nord, le temple au sud.
        TownLayout.Layout c = TownLayout.plan(TownLayout.Pattern.CASTRUM, all, roles, true, 7);
        int plaza = roles.indexOf(TownSize.Role.PLAZA);
        assertEquals(0, c.spots().get(plaza).x());
        assertEquals(0, c.spots().get(plaza).z());
        assertTrue(c.spots().get(0).z() < 0 && c.spots().get(church).z() > 0, "basilique et temple mal placés");
        // Kahun : le palais à l'ouest regarde l'est, le temple au bout de l'axe à l'est.
        TownLayout.Layout e = TownLayout.plan(TownLayout.Pattern.KAHUN, all, roles, true, 7);
        assertEquals(Rotation.COUNTERCLOCKWISE_90, e.spots().get(0).rotation());
        assertTrue(e.spots().get(church).x() > e.spots().get(0).x(), "le temple n'est pas à l'est du palais");
    }

    @Test
    void rotationsComposees() {
        assertArrayEquals(new int[]{-3, 2}, PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90));
        assertArrayEquals(new int[]{2, 3}, PlanBuilder.turn(PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90)[0],
                PlanBuilder.turn(2, 3, Rotation.CLOCKWISE_90)[1], Rotation.COUNTERCLOCKWISE_90));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, PlanBuilder.inverse(Rotation.CLOCKWISE_90));
    }
}
