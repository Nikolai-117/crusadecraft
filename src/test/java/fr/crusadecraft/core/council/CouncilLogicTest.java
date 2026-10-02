package fr.crusadecraft.core.council;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouncilLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    @Test
    void horloge() {
        assertEquals(0, CouncilService.hourToTicks(6));
        assertEquals(3000, CouncilService.hourToTicks(9));
        assertEquals(18000, CouncilService.hourToTicks(0));
        assertEquals(1500, CouncilService.minutesToTicks(90));
    }

    @Test
    void fenetreQuiChevaucheMinuit() {
        long start = CouncilService.hourToTicks(5); // 23000
        assertEquals(500, CouncilService.elapsedInWindow(23500, start, 2000));
        assertEquals(1500, CouncilService.elapsedInWindow(500, start, 2000));
        assertEquals(-1, CouncilService.elapsedInWindow(1500, start, 2000));
    }

    @Test
    void chaquePosteDuConseilADesTachesEtUneParDefautDisponible() {
        for (Post p : Post.values()) {
            if (!p.isCouncil()) {
                assertTrue(CouncilTask.of(p).isEmpty());
                continue;
            }
            assertEquals(3, CouncilTask.of(p).size(), p.name());
            CouncilTask def = CouncilTask.defaultFor(p);
            assertNotNull(def, p.name());
            assertTrue(def.available());
        }
    }

    @Test
    void stressQuandOnAgitContreSaNature() {
        assertEquals(10, CouncilService.stressConflict(CouncilTask.SURVEILLER_LES_TERRES, List.of(Trait.HONNETE)));
        assertEquals(8, CouncilService.stressConflict(CouncilTask.ENTRAINER_TROUPES, List.of(Trait.LACHE, Trait.BRAVE)));
        assertEquals(0, CouncilService.stressConflict(CouncilTask.LEGITIMITE, List.of(Trait.ZELE)));
    }

    @Test
    void coutsDesReponses() {
        assertEquals(5, RequestService.goldCost(RequestType.Choice.PAY_HEALER));
        assertEquals(10, RequestService.goldCost(RequestType.Choice.OPEN_GRANARIES));
        assertEquals(0, RequestService.goldCost(RequestType.Choice.PROMISE));
        assertEquals(10, RequestService.prestigeCost(RequestType.Choice.PROMISE_HONOURS));
        for (RequestType t : RequestType.values()) assertTrue(t.choices().size() >= 2, t.name());
    }

    @Test
    void etatDeLaCourAllerRetour() {
        CourtState s = new CourtState();
        ColonyRef colony = new ColonyRef(OW, 3);
        s.setTable(OW, new BlockPos(10, 64, -5));
        s.mayorDesks().put(colony, new BlockPos(1, 2, 3));
        s.setTask(Post.CHAPELAIN, CouncilTask.BONHEUR_DU_PEUPLE);
        s.setTask(Post.MARECHAL, CouncilTask.LEGITIMITE); // tâche d'un autre poste : refusée
        s.setLastProcessedDay(12);
        s.convene(5000, 12);
        CitizenRef mayor = new CitizenRef(colony, 4);
        s.requests().add(new Request(UUID.randomUUID(), RequestType.MAYOR_HOMELESS, mayor, "Valbrume", 3, 11));
        s.markRequested(RequestType.MAYOR_HOMELESS, mayor, 11);

        CourtState back = CourtState.load(s.save());
        assertTrue(back.hasTable());
        assertEquals(new BlockPos(10, 64, -5), back.tablePos());
        assertEquals(new BlockPos(1, 2, 3), back.mayorDesks().get(colony));
        assertEquals(CouncilTask.BONHEUR_DU_PEUPLE, back.task(Post.CHAPELAIN));
        assertEquals(CouncilTask.ENTRAINER_TROUPES, back.task(Post.MARECHAL), "tâche par défaut");
        assertEquals(12, back.lastProcessedDay());
        assertEquals(12, back.lastConveneDay());
        assertEquals(1, back.requests().size());
        assertTrue(back.onCooldown(RequestType.MAYOR_HOMELESS, mayor, 13));
        assertFalse(back.onCooldown(RequestType.MAYOR_HOMELESS, mayor, 14));
        back.clearTable();
        assertFalse(back.hasTable());
        assertNull(back.tablePos());
    }
}
