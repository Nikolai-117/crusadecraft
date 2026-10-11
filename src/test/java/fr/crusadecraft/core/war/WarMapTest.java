package fr.crusadecraft.core.war;

import fr.crusadecraft.core.world.TerritoryMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La carte de guerre (P18) : peindre, effacer, poser, retirer, et tout retrouver après une sauvegarde. */
class WarMapTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");
    private static final UUID REALM = new UUID(3, 1);

    @Test
    void peindreEtEffacerDesZones() {
        WarMap.Plan plan = new WarMap().plan(REALM);
        long a = TerritoryMap.key(0, 0), b = TerritoryMap.key(1, 0);
        assertEquals(2, plan.paint(OW, new long[]{a, b}, WarMap.Zone.OFFENSIVE));
        assertEquals(0, plan.paint(OW, new long[]{a}, WarMap.Zone.OFFENSIVE), "repeindre à l'identique ne change rien");
        assertEquals(1, plan.paint(OW, new long[]{a}, WarMap.Zone.DEFENSE));
        assertEquals((byte) WarMap.Zone.DEFENSE.ordinal(), plan.zones(OW).get(a));
        assertEquals(2, plan.paint(OW, new long[]{a, b}, null), "la gomme efface");
        assertTrue(plan.isEmpty());
    }

    @Test
    void flechesEtReperesSontBornes() {
        WarMap.Plan plan = new WarMap().plan(REALM);
        for (int i = 0; i < WarMap.MAX_ARROWS; i++) assertTrue(plan.addArrow(new WarMap.Arrow(UUID.randomUUID(), OW, 0, 0, 100, i)));
        assertFalse(plan.addArrow(new WarMap.Arrow(UUID.randomUUID(), OW, 0, 0, 1, 1)), "pas de flèches sans fin");
        UUID pin = UUID.randomUUID();
        assertTrue(plan.addPin(new WarMap.Pin(pin, OW, 5, 5, WarMap.Mark.DANGER, "Gué gardé")));
        assertTrue(plan.remove(pin));
        assertFalse(plan.remove(pin));
    }

    @Test
    void sauvegardeAllerRetour() {
        WarMap map = new WarMap();
        WarMap.Plan plan = map.plan(REALM);
        plan.paint(OW, new long[]{TerritoryMap.key(-3, 7), TerritoryMap.key(4, 4)}, WarMap.Zone.OBJECTIF);
        UUID arrow = UUID.randomUUID();
        plan.addArrow(new WarMap.Arrow(arrow, OW, -100, 20, 300, -40));
        plan.addPin(new WarMap.Pin(UUID.randomUUID(), OW, 12, -8, WarMap.Mark.RASSEMBLEMENT, "Ici, à l'aube"));
        map.plan(new UUID(3, 2));
        WarMap back = WarMap.load(map.save());
        assertEquals(1, back.all().size(), "un plan vide ne s'écrit pas");
        WarMap.Plan p = back.find(REALM).orElseThrow();
        assertEquals(2, p.zones(OW).size());
        assertEquals((byte) WarMap.Zone.OBJECTIF.ordinal(), p.zones(OW).get(TerritoryMap.key(-3, 7)));
        assertEquals(arrow, p.arrows().get(0).id());
        assertEquals(300, p.arrows().get(0).x1());
        assertEquals("Ici, à l'aube", p.pins().get(0).note());
        assertEquals(WarMap.Mark.RASSEMBLEMENT, p.pins().get(0).kind());
    }

    @Test
    void uneNoteNePorteNiCodesNiSautsDeLigne() {
        assertEquals("Gué gardé", WarMapService.cleanNote(" Gué\n§cgardé "));
        assertEquals("Ici", WarMapService.cleanNote("§lIci§r"));
        assertEquals("", WarMapService.cleanNote(null));
        assertEquals(WarMap.MAX_NOTE, WarMapService.cleanNote("x".repeat(500)).length());
    }
}
