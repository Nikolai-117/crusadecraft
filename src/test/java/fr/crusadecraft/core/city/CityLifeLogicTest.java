package fr.crusadecraft.core.city;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.crusadecraft.core.character.Trait;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

class CityLifeLogicTest {
    private static final UUID ID = new UUID(1, 6);

    private static Map<BuildingFunction, Integer> workers(Object... pairs) {
        Map<BuildingFunction, Integer> m = new EnumMap<>(BuildingFunction.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((BuildingFunction) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    @Test
    void aWellKeptCityFeedsItself() {
        CityEconomy e = CityEconomy.fresh();
        CityEconomy.Census c = new CityEconomy.Census(workers(BuildingFunction.FERME, 2, BuildingFunction.BOULANGERIE, 1), 20, false);
        CityEconomy.Day last = null;
        for (int i = 0; i < 50; i++) last = e.simulate(c, false, true, new Random(i));
        assertEquals(0, last.hungry(), "personne n'a faim");
        assertEquals(0, e.famine());
        assertTrue(e.stock(CityGood.PAIN) > 0);
    }

    @Test
    void aBakerTurnsWheatIntoBreadAndNeedsWheat() {
        CityEconomy e = new CityEconomy();
        e.add(CityGood.BLE, 6);
        e.simulate(new CityEconomy.Census(workers(BuildingFunction.BOULANGERIE, 1), 0, false), false, false, new Random(1));
        assertEquals(8, e.stock(CityGood.PAIN), "six de blé font huit pains");
        assertEquals(0, e.stock(CityGood.BLE));
    }

    @Test
    void withoutABakerPeopleBakeAtHome() {
        CityEconomy e = new CityEconomy();
        e.add(CityGood.BLE, 10);
        e.addPurse(1000);
        CityEconomy.Day d = e.simulate(new CityEconomy.Census(workers(), 4, false), false, false, new Random(1));
        assertEquals(0, d.hungry());
    }

    @Test
    void hungerBuildsUpAndGoesAway() {
        CityEconomy e = new CityEconomy();
        CityEconomy.Census c = new CityEconomy.Census(workers(), 9, false);
        CityEconomy.Day d = e.simulate(c, false, false, new Random(1));
        assertTrue(d.hungry() > 3, "sans rien, on a faim : " + d.hungry());
        assertEquals(1, e.famine());
        // Sans le sou non plus, la campagne ne vend plus rien.
        e.addPurse(-e.purse());
        e.simulate(c, false, false, new Random(2));
        assertEquals(2, e.famine());
        e.addPurse(-e.purse());
        e.add(CityGood.PAIN, 100);
        e.simulate(c, false, false, new Random(3));
        assertEquals(0, e.famine(), "la faim s'en va quand on mange");
    }

    @Test
    void warCutsTheRoads() {
        CityEconomy peace = new CityEconomy(), war = new CityEconomy();
        peace.addPurse(100);
        war.addPurse(100);
        peace.simulate(new CityEconomy.Census(workers(), 8, false), false, false, new Random(1));
        war.simulate(new CityEconomy.Census(workers(), 8, true), false, false, new Random(1));
        assertTrue(peace.stock(CityGood.BLE) + peace.stock(CityGood.PAIN) >= war.stock(CityGood.BLE) + war.stock(CityGood.PAIN));
    }

    @Test
    void aMarketSellsTheSurplusAndAWarehouseKeepsMore() {
        CityEconomy a = new CityEconomy(), b = new CityEconomy(), c = new CityEconomy();
        for (CityEconomy e : List.of(a, b, c)) e.add(CityGood.PIERRE, CityEconomy.CAP + 50);
        CityEconomy.Census none = new CityEconomy.Census(workers(), 0, false);
        a.simulate(none, false, false, new Random(1));
        b.simulate(none, false, true, new Random(1));
        c.simulate(none, true, false, new Random(1));
        assertEquals(CityEconomy.CAP, a.stock(CityGood.PIERRE));
        assertEquals(0, a.purse());
        assertEquals(50, b.purse(), "le marché écoule le surplus");
        assertEquals(CityEconomy.CAP + 50, c.stock(CityGood.PIERRE), "un entrepôt garde tout");
    }

    @Test
    void aRichCityPaysTax() {
        CityEconomy e = new CityEconomy();
        e.addPurse(1000);
        CityEconomy.Day d = e.simulate(new CityEconomy.Census(workers(), 0, false), false, false, new Random(1));
        assertEquals(50, d.tax());
        assertEquals(950, e.purse());
    }

    @Test
    void scarcityRaisesPricesOnlyWhenLow() {
        CityEconomy e = new CityEconomy();
        e.add(CityGood.PAIN, 100);
        assertEquals(0, e.scarcity(CityGood.PAIN));
        e.take(CityGood.PAIN, 100);
        assertEquals(40, e.scarcity(CityGood.PAIN));
    }

    @Test
    void takeNeverGoesBelowZero() {
        CityEconomy e = new CityEconomy();
        e.add(CityGood.FER, 3);
        assertEquals(3, e.take(CityGood.FER, 10));
        assertEquals(0, e.stock(CityGood.FER));
    }

    @Test
    void itemsMapToGoods() {
        assertEquals(CityGood.PAIN, CityGood.ofItem("minecraft:bread"));
        assertEquals(CityGood.BLE, CityGood.ofItem("minecraft:wheat"));
        assertEquals(CityGood.REPAS, CityGood.ofItem("minecraft:mushroom_stew"));
        assertEquals(CityGood.VIANDE, CityGood.ofItem("minecraft:cooked_chicken"));
        assertEquals(CityGood.OUTILS, CityGood.ofItem("minecraft:iron_axe"));
        assertEquals(CityGood.PIERRE, CityGood.ofItem("minecraft:stone_bricks"));
        assertEquals(CityGood.LIVRE, CityGood.ofItem("minecraft:book"));
        assertNull(CityGood.ofItem("minecraft:emerald"), "l'émeraude est la monnaie");
        assertNull(CityGood.ofItem("minecraft:diamond"));
    }

    @Test
    void theDayHasItsHours() {
        List<Trait> none = List.of();
        assertEquals(CityRules.Activity.WORK, CityRules.activity(3000, "FORGE", 30, none, ID));
        assertEquals(CityRules.Activity.MEAL, CityRules.activity(5400 + CityRules.stagger(ID, 600) + 100, "FORGE", 30, none, ID));
        assertEquals(CityRules.Activity.LEISURE, CityRules.activity(12000, "FORGE", 30, none, ID));
        assertEquals(CityRules.Activity.SLEEP, CityRules.activity(18000, "FORGE", 30, none, ID));
        assertEquals(CityRules.Activity.PLAY, CityRules.activity(3000, "", 8, none, ID));
        assertEquals(CityRules.Activity.SLEEP, CityRules.activity(18000, "", 8, none, ID));
        assertEquals(CityRules.Activity.LEISURE, CityRules.activity(3000, "", 70, none, ID), "à soixante-dix ans, on ne travaille plus");
    }

    @Test
    void theTavernStaysOpenLateAndSomeGuardsWatchAtNight() {
        List<Trait> none = List.of();
        assertEquals(CityRules.Activity.WORK, CityRules.activity(13000, "TAVERNE", 30, none, ID));
        UUID night = null, day = null;
        for (int i = 0; i < 20; i++) {
            UUID id = new UUID(i, i * 3L);
            if (CityRules.nightShift("CASERNE", id)) night = id;
            else day = id;
        }
        assertEquals(CityRules.Activity.WORK, CityRules.activity(18000, "CASERNE", 30, none, night));
        assertEquals(CityRules.Activity.SLEEP, CityRules.activity(18000, "CASERNE", 30, none, day));
        assertFalse(CityRules.nightShift("FORGE", night), "un forgeron ne monte pas la garde");
    }

    @Test
    void marriageNeedsTwoFreeAdults() {
        assertTrue(CityRules.canMarry(25, true, true, 30, false, true));
        assertFalse(CityRules.canMarry(25, true, true, 30, true, true));
        assertFalse(CityRules.canMarry(25, true, false, 30, false, true));
        assertFalse(CityRules.canMarry(16, true, true, 18, false, true));
        assertFalse(CityRules.canMarry(20, true, true, 40, false, true));
    }

    @Test
    void childrenNeedRoomAndAge() {
        assertTrue(CityRules.canHaveChild(30, 1, 10, 80));
        assertFalse(CityRules.canHaveChild(50, 1, 10, 80));
        assertFalse(CityRules.canHaveChild(30, 6, 10, 80));
        assertFalse(CityRules.canHaveChild(30, 1, 80, 80));
    }

    @Test
    void aPostGoesToAFreeAdult() {
        assertTrue(CityRules.canTakePost(30, "", false));
        assertFalse(CityRules.canTakePost(30, "FORGE", false));
        assertFalse(CityRules.canTakePost(12, "", false));
        assertFalse(CityRules.canTakePost(70, "", false));
        assertFalse(CityRules.canTakePost(30, "", true));
    }

    @Test
    void gendersAgree() {
        assertEquals("Né à Rome, il part.", CityVoice.agree("{Né|Née} à Rome, {il|elle} part.", false));
        assertEquals("Née à Rome, elle part.", CityVoice.agree("{Né|Née} à Rome, {il|elle} part.", true));
        assertEquals("Bonjour {joueur}.", CityVoice.agree("Bonjour {joueur}.", true), "un trou ordinaire n'est pas un accord");
    }

    @Test
    void furnitureIsRecognizedByName() {
        assertEquals(Furnishing.Kind.SIEGE, Furnishing.of("oak_chair"));
        assertEquals(Furnishing.Kind.SIEGE, Furnishing.of("council_seat"));
        assertEquals(Furnishing.Kind.TABLE, Furnishing.of("spruce_table"));
        assertNull(Furnishing.of("crafting_table"), "un établi a déjà son usage");
        assertEquals(Furnishing.Kind.LUMIERE, Furnishing.of("iron_lantern"));
        assertEquals(Furnishing.Kind.PLANTE, Furnishing.of("potted_poppy"));
        assertEquals(Furnishing.Kind.TAPISSERIE, Furnishing.of("red_carpet"));
        assertEquals(Furnishing.Kind.STOCKAGE, Furnishing.of("oak_crate"));
        assertEquals(Furnishing.Kind.EAU, Furnishing.of("water_well"));
        assertEquals(Furnishing.Kind.PLANTE, Furnishing.of("pink_petals"));
        assertEquals(Furnishing.Kind.SIEGE, Furnishing.of("armchair"));
        assertNull(Furnishing.of("stable_door"), "« table » est dans « stable »");
        assertNull(Furnishing.of("metal_block"), "un bout de mot ne compte pas");
        assertNull(Furnishing.of("workbench"), "ce n'est pas un banc");
        assertNull(Furnishing.of("toadstool"), "ni un tabouret");
        assertNull(Furnishing.of("dwelling"), "ni un puits");
        assertNull(Furnishing.of("redstone_lamp_wall"), "un mur n'est pas un meuble");
        assertNull(Furnishing.of("stone"));
        assertNull(Furnishing.of("oak_planks"));
        assertNull(Furnishing.of("cobblestone_stairs"), "un escalier n'est pas un meuble");
        assertTrue(Furnishing.sittable("cobblestone_stairs", true), "mais on peut s'y asseoir");
        assertFalse(Furnishing.sittable("cobblestone_stairs", false));
    }

    @Test
    void furnitureMakesWorkshopsProduceMore() {
        assertEquals(0, Furnishing.bonus(BuildingFunction.BOULANGERIE, Map.of()));
        assertEquals(50, Furnishing.bonus(BuildingFunction.BOULANGERIE, Map.of("four", 2)));
        assertEquals(100, Furnishing.bonus(BuildingFunction.BOULANGERIE, Map.of("four", 9)), "ça plafonne");
        assertEquals(0, Furnishing.bonus(BuildingFunction.MAISON, Map.of("four", 2)));
        assertEquals(40, Furnishing.storage(Map.of("tonneau", 2, "coffre", 3)));
        CityEconomy plain = new CityEconomy(), equipped = new CityEconomy();
        plain.add(CityGood.BLE, 100);
        equipped.add(CityGood.BLE, 100);
        plain.simulate(new CityEconomy.Census(workers(BuildingFunction.BOULANGERIE, 1), 0, false), false, false, new Random(1));
        equipped.simulate(new CityEconomy.Census(workers(BuildingFunction.BOULANGERIE, 1), 0, false, Map.of(BuildingFunction.BOULANGERIE, 100), 0, 0), false, false, new Random(1));
        assertTrue(equipped.stock(CityGood.PAIN) > plain.stock(CityGood.PAIN), "des fours en plus, du pain en plus");
    }

    @Test
    void chestsMakeRoomAndAMillMakesMoreBread() {
        CityEconomy e = new CityEconomy();
        e.add(CityGood.PIERRE, CityEconomy.CAP + 50);
        e.simulate(new CityEconomy.Census(workers(), 0, false, Map.of(), 40, 0), false, false, new Random(1));
        assertEquals(CityEconomy.CAP + 40, e.stock(CityGood.PIERRE));
        CityEconomy m = new CityEconomy();
        m.add(CityGood.BLE, 10);
        m.simulate(new CityEconomy.Census(workers(BuildingFunction.BOULANGERIE, 1, BuildingFunction.MOULIN, 1), 0, false), false, false, new Random(1));
        assertEquals(20, m.stock(CityGood.PAIN), "dix de blé font vingt pains");
    }

    @Test
    void aSchoolRaisesEverythingAndCharmBringsMoney() {
        CityEconomy.Census school = new CityEconomy.Census(workers(BuildingFunction.ECOLE, 1), 0, false);
        assertEquals(110, school.output(BuildingFunction.FERME, 100));
        assertEquals(0, Furnishing.charm(0, 5));
        assertEquals(100, Furnishing.charm(5000, 5));
        assertTrue(Furnishing.charm(40, 5) > Furnishing.charm(40, 50), "une grande ville en demande plus");
        CityEconomy dull = new CityEconomy(), pretty = new CityEconomy();
        dull.simulate(new CityEconomy.Census(workers(), 0, false), false, false, new Random(1));
        pretty.simulate(new CityEconomy.Census(workers(), 0, false, Map.of(), 0, 80), false, false, new Random(1));
        assertEquals(20, pretty.purse() - dull.purse(), "le charme attire des visiteurs, et leur argent");
    }

    @Test
    void aBubbleLastsLongEnoughToRead() {
        assertEquals(80, CityBubbles.duration(5));
        assertEquals(180, CityBubbles.duration(500));
        assertTrue(CityBubbles.duration(60) > 150);
    }
}
