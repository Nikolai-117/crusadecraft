package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P38 : la fonction proposée d'après les meubles, les gens qu'une fonction amène, et ce qu'on enregistre. */
class CityLogicTest {

    @Test
    void fonctionProposee() {
        assertEquals(BuildingFunction.FORGE, BuildingScan.suggest(Map.of("enclume", 1, "haut_fourneau", 1, "forge", 1), 30, 1));
        assertEquals(BuildingFunction.MAIRIE, BuildingScan.suggest(Map.of("cloche", 1, "lit", 2), 60, 2));
        assertEquals(BuildingFunction.TEMPLE, BuildingScan.suggest(Map.of("alambic", 1), 40, 1));
        assertEquals(BuildingFunction.BIBLIOTHEQUE, BuildingScan.suggest(Map.of("lutrin", 1, "livres", 4), 40, 1));
        assertEquals(BuildingFunction.BOULANGERIE, BuildingScan.suggest(Map.of("four", 2), 30, 1));
        assertEquals(BuildingFunction.MAISON, BuildingScan.suggest(Map.of("four", 2, "lit", 1), 30, 1));
        assertEquals(BuildingFunction.ECURIE, BuildingScan.suggest(Map.of("foin", 8), 50, 1));
        assertEquals(BuildingFunction.FERME, BuildingScan.suggest(Map.of("composteur", 1), 30, 1));
        assertEquals(BuildingFunction.ENTREPOT, BuildingScan.suggest(Map.of("tonneau", 6, "coffre", 3), 40, 1));
        assertEquals(BuildingFunction.CHATEAU, BuildingScan.suggest(Map.of(), 340, 3));
        assertEquals(BuildingFunction.MAISON, BuildingScan.suggest(Map.of(), 25, 1));
    }

    @Test
    void etages() {
        assertEquals(3, BuildingScan.countLevels(Set.of(64, 65, 68, 69, 73)));
        assertEquals(1, BuildingScan.countLevels(Set.of(70)));
        assertEquals(0, BuildingScan.countLevels(Set.of()));
    }

    @Test
    void gensDUneFonction() {
        assertEquals(2, BuildingFunction.CASERNE.postsFor(0).size());
        assertEquals(5, BuildingFunction.CASERNE.postsFor(200).size());
        assertEquals(6, BuildingFunction.CASERNE.postsFor(10_000).size());
        assertEquals(3, BuildingFunction.CHATEAU.postsFor(100).size());
        assertEquals(1, BuildingFunction.families(40));
        assertEquals(2, BuildingFunction.families(80));
        assertTrue(BuildingFunction.MAISON.dwelling());
        assertFalse(BuildingFunction.FORGE.dwelling());
        assertEquals(BuildingFunction.TAVERNE, BuildingFunction.byName("TAVERNE"));
        assertEquals(null, BuildingFunction.byName("PORT"));
        assertEquals(16, BuildingFunction.values().length);
    }

    @Test
    void batimentAllerRetour() {
        CityBuilding b = new CityBuilding(UUID.randomUUID(), new BlockPos(10, 64, 10), new BlockPos(4, 70, 2), new BlockPos(6, 65, 6));
        assertEquals(new BlockPos(4, 64, 2), b.min());
        assertEquals(new BlockPos(10, 70, 10), b.max());
        b.setFunction(BuildingFunction.FORGE);
        b.setSuggestion(BuildingFunction.FORGE);
        b.setName("Forge d'Aldric");
        b.setStats(42, 2, Map.of("enclume", 1, "haut_fourneau", 2));
        UUID smith = UUID.randomUUID();
        b.occupants().add(smith);
        CityBuilding back = CityBuilding.load(b.save());
        assertEquals(b.id(), back.id());
        assertEquals(b.min(), back.min());
        assertEquals(b.max(), back.max());
        assertEquals(b.spot(), back.spot());
        assertEquals(BuildingFunction.FORGE, back.function());
        assertEquals("Forge d'Aldric", back.name());
        assertEquals(42, back.floor());
        assertEquals(2, back.levels());
        assertEquals(2, back.furniture().get("haut_fourneau"));
        assertEquals(smith, back.occupants().get(0));
        assertTrue(back.contains(new BlockPos(5, 66, 9)));
        assertFalse(back.contains(new BlockPos(11, 66, 9)));
        assertEquals(1.0, back.overlap(new BlockPos(5, 64, 3), new BlockPos(6, 65, 4)), 1e-9);
        assertEquals(0.0, back.overlap(new BlockPos(20, 64, 20), new BlockPos(22, 65, 22)), 1e-9);
    }

    @Test
    void villeImporteeAllerRetour() {
        // Une ville d'avant P38 se charge sans bâtiments ; une ville importée garde son plan, sa taille et ses bâtiments.
        Site s = new Site(UUID.randomUUID(), SiteKind.VILLAGE, "Dolhimluith", UUID.randomUUID(), new ResourceLocation("minecraft", "overworld"),
                new BlockPos(100, 70, -40), Direction.SOUTH, 0x2A4C8C, true, 12);
        Site old = Site.load(s.save());
        assertFalse(old.imported());
        assertTrue(old.buildings().isEmpty());
        assertEquals(SiteKind.VILLAGE.size(), old.extent());
        s.setImported("file:plans|Levenzia/dolhimluith.schem", 171, 52, 173, 1);
        CityBuilding b = new CityBuilding(UUID.randomUUID(), new BlockPos(90, 70, -50), new BlockPos(96, 75, -44), new BlockPos(93, 71, -47));
        b.setFunction(BuildingFunction.TAVERNE);
        s.buildings().add(b);
        Site back = Site.load(s.save());
        assertTrue(back.imported());
        assertEquals("file:plans|Levenzia/dolhimluith.schem", back.importedRef());
        assertEquals(173, back.extent());
        assertEquals(52, back.importedHeight());
        assertEquals(1, back.importedGround());
        assertEquals(1, back.buildings().size());
        assertEquals(BuildingFunction.TAVERNE, back.building(b.id()).orElseThrow().function());
        // Bâtie, elle étend le territoire au-delà de ses murs.
        back.setBuilt(true);
        assertTrue(back.radius() >= 173 / 2 + 32);
    }
}
