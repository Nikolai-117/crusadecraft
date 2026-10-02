package fr.crusadecraft.core.world;

import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");
    private static final WorldPlanner.LandProbe ALL_LAND = (x, z) -> 64;

    private static List<WorldPlanner.Candidate> grid(int step, int extent) {
        List<WorldPlanner.Candidate> out = new ArrayList<>();
        for (int x = -extent; x <= extent; x += step) {
            for (int z = -extent; z <= extent; z += step) out.add(new WorldPlanner.Candidate(x, 70, z, AiProvince.Anchor.VILLAGE));
        }
        return out;
    }

    private static WorldPlanner.Plan plan(long seed, List<WorldPlanner.Candidate> villages, List<int[]> avoid) {
        return new WorldPlanner(WorldPlanner.Settings.defaults(), seed, ALL_LAND, OW).plan(0, 0, villages, avoid, List.of(), 3);
    }

    @Test
    void planCompletEtReproductible() {
        WorldPlanner.Plan a = plan(42, grid(300, 3000), List.of());
        WorldPlanner.Plan b = plan(42, grid(300, 3000), List.of());
        assertEquals(6, a.realms().size());
        assertEquals(a.realms().get(0).place(), b.realms().get(0).place(), "même graine, même monde");
        assertEquals(a.realms().get(3).capital().seat(), b.realms().get(3).capital().seat());
        assertEquals(TitleRank.ROYAUME, a.realms().get(0).rank(), "un royaume en tête");
        assertEquals(2, a.dejure().size(), "un royaume de jure pour trois États");
    }

    @Test
    void contraintesDePlacement() {
        WorldPlanner.Settings s = WorldPlanner.Settings.defaults();
        List<int[]> colonies = List.of(new int[]{900, 0}, new int[]{0, -1200});
        WorldPlanner.Plan p = plan(7, grid(250, 3200), colonies);
        List<AiProvince> capitals = new ArrayList<>();
        List<AiProvince> all = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (AiRealm r : p.realms()) {
            AiProvince cap = r.capital();
            assertNotNull(cap);
            assertTrue(cap.capital());
            double d = Math.hypot(cap.seat().getX(), cap.seat().getZ());
            assertTrue(d >= s.minRing() && d <= s.maxRing(), "capitale hors de l'anneau : " + d);
            capitals.add(cap);
            all.addAll(r.provinces());
            assertEquals(5, r.council().size(), "cinq conseillers");
            assertTrue(r.council().containsKey(Post.CHANCELIER));
            assertTrue(names.add(r.place()), "nom de royaume en double : " + r.place());
            for (AiProvince prov : r.provinces()) {
                assertTrue(Math.hypot(prov.seat().getX() - cap.seat().getX(), prov.seat().getZ() - cap.seat().getZ()) <= s.provinceRadius() + 1,
                        "province trop loin de sa capitale");
                assertTrue(prov.dejureKingdom() >= 0 && prov.dejureKingdom() < p.dejure().size());
            }
            int n = r.provinces().size();
            switch (r.rank()) {
                case ROYAUME -> assertTrue(n >= 3 && n <= 5, "royaume : " + n);
                case DUCHE -> assertTrue(n >= 2 && n <= 3, "duché : " + n);
                default -> assertTrue(n >= 1 && n <= 2, "comté : " + n);
            }
        }
        for (int i = 0; i < capitals.size(); i++) {
            for (int j = i + 1; j < capitals.size(); j++) {
                double d = capitals.get(i).seat().distSqr(capitals.get(j).seat());
                assertTrue(Math.sqrt(d) >= s.capitalSpacing(), "capitales trop proches");
            }
        }
        for (int i = 0; i < all.size(); i++) {
            for (int[] c : colonies) {
                assertTrue(Math.hypot(all.get(i).seat().getX() - c[0], all.get(i).seat().getZ() - c[1]) >= s.avoidRadius(),
                        "siège sur une colonie de joueur");
            }
            for (int j = i + 1; j < all.size(); j++) {
                assertTrue(Math.sqrt(all.get(i).seat().distSqr(all.get(j).seat())) >= s.seatSpacing(), "sièges trop proches");
            }
        }
    }

    @Test
    void sansVillagesOnPrendLaTerreFerme() {
        WorldPlanner.Plan p = plan(11, List.of(), List.of());
        assertEquals(6, p.realms().size());
        for (AiRealm r : p.realms()) {
            for (AiProvince prov : r.provinces()) assertEquals(AiProvince.Anchor.LAND, prov.anchor());
        }
        // Un monde tout d'eau : aucun royaume, mais pas d'erreur.
        WorldPlanner.Plan sea = new WorldPlanner(WorldPlanner.Settings.defaults(), 1, (x, z) -> null, OW).plan(0, 0, List.of(), List.of(), List.of(), 0);
        assertTrue(sea.realms().isEmpty());
    }

    @Test
    void villagesGeneresAvantVillagesPrevus() {
        List<WorldPlanner.Candidate> villages = new ArrayList<>();
        villages.add(new WorldPlanner.Candidate(1000, 70, 0, AiProvince.Anchor.VILLAGE_PREDICTED));
        villages.add(new WorldPlanner.Candidate(-1000, 70, 0, AiProvince.Anchor.VILLAGE));
        WorldPlanner.Plan p = new WorldPlanner(new WorldPlanner.Settings(1, 600, 2500, 700, 450, 180, 320), 3, ALL_LAND, OW)
                .plan(0, 0, villages, List.of(), List.of(), 0);
        assertEquals(-1000, p.realms().get(0).capital().seat().getX(), "le village déjà généré l'emporte");
    }

    @Test
    void nomsEtElision() {
        NameGenerator g = new NameGenerator(5);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) assertTrue(seen.add(g.place()), "lieu en double");
        assertEquals("Hautebrume", NameGenerator.join("Haute", "brume"));
        assertEquals("Hautorme", NameGenerator.join("Haute", "orme"));
        assertTrue(AiNames.elides("Ambrevent"));
        assertTrue(AiNames.elides("Ormesval"));
        assertFalse(AiNames.elides("Hautebrume"), "h aspiré");
        assertFalse(AiNames.elides("Hêtrecourt"), "h aspiré");
        assertFalse(AiNames.elides("Valcourt"));
    }

    @Test
    void sauvegardeAllerRetour() {
        WorldPlanner.Plan p = plan(9, grid(300, 3000), List.of());
        WorldState s = new WorldState();
        s.replace(p.realms(), p.dejure(), 10, -20, 4);
        AiRealm first = p.realms().get(0);
        UUID player = UUID.randomUUID();
        assertTrue(first.discover(player, 5));
        assertFalse(first.discover(player, 6), "on ne découvre qu'une fois");
        s.setLastEconomyDay(8);
        WorldState back = WorldState.load(s.save());
        assertTrue(back.generated());
        assertEquals(p.realms().size(), back.realms().size());
        AiRealm r = back.realm(first.id()).orElseThrow();
        assertEquals(first.place(), r.place());
        assertEquals(first.ruler().name(), r.ruler().name());
        assertEquals(first.provinces().size(), r.provinces().size());
        assertTrue(r.discoveredBy(player));
        assertEquals(5L, r.discoveredBy().get(player));
        assertEquals(8, back.lastEconomyDay());
        assertEquals(p.dejure().get(0).place(), back.dejure().get(0).place());
    }

    @Test
    void economieSimulee() {
        AiRealm r = plan(13, grid(300, 3000), List.of()).realms().get(0);
        long gold = r.gold();
        int pop = r.population();
        for (long d = 1; d <= 30; d++) WorldService.simulateDay(r, d);
        assertTrue(r.gold() > gold, "le royaume s'enrichit");
        assertTrue(r.population() >= pop, "la population ne baisse pas");
    }

    @Test
    void imageDeLaCarte() {
        MapView.Territory mine = new MapView.Territory(UUID.randomUUID(), false, true, "Royaume de Valbrume", TitleRank.BARONNIE,
                0x1F4E9C, "Romain", false, null, 20);
        MapView.Territory ai = new MapView.Territory(UUID.randomUUID(), true, false, "Valcourt", TitleRank.COMTE,
                0xB3261E, "Aldric", false, AiPersonality.MARCHAND, 40);
        List<MapView.Seat> seats = List.of(new MapView.Seat(0, "Valbrume", 0, 0, true, 0, false),
                new MapView.Seat(1, "Valcourt", 400, 0, true, 0, true));
        MapView v = new MapView(OW, 0, 0, 0, 0, 160, 110, List.of(mine, ai), seats, List.of(new MapView.Dejure("Ambrevent", 0xC0A060)));
        // Un pixel = 10 blocs, carte de 200 × 100 centrée sur (200, 0).
        MapRaster.Result r = MapRaster.render(v, 200, 100, 200, 0, 10, false);
        assertEquals(0, r.ownerAt(80, 50), "au-dessus de Valbrume");
        assertEquals(1, r.ownerAt(120, 50), "au-dessus de Valcourt");
        assertEquals(MapRaster.FOGGED, r.ownerAt(199, 0), "loin de tout : brouillard");
        assertEquals(MapRaster.NONE, r.ownerAt(100, 50), "entre les deux : terres connues");
        int inside = r.pixels()[50 * 200 + 120] & 0xFFFFFF;
        assertTrue(((inside >> 16) & 0xFF) > (inside & 0xFF), "Valcourt est rouge");
        MapRaster.Result dejure = MapRaster.render(v, 200, 100, 200, 0, 10, true);
        assertEquals(dejure.pixels()[50 * 200 + 75], dejure.pixels()[50 * 200 + 75], "vue de jure calculée");
    }
}
