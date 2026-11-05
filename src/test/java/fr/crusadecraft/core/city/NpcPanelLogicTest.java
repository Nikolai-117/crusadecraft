package fr.crusadecraft.core.city;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.crusadecraft.core.nation.NationFile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** P46 à P49 : ce qui se vérifie sans monde : les réglages, les liens de skins, les durées, les commandes, le courrier. */
class NpcPanelLogicTest {
    private static byte[] png(int w, int h, boolean thin) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int s = w / 64;
        for (int y = 20 * s; y < 32 * s; y++) {
            // Le bras en entier ; un bras fin s'arrête deux colonnes avant le bord.
            for (int x = 40 * s; x < 56 * s; x++) if (!thin || x < 54 * s) img.setRGB(x, y, 0xFFAA8866);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void npcSettingsStartEmptyAndSurviveSaving() {
        NpcSettings s = new NpcSettings();
        assertTrue(s.isDefault(), "rien de réglé : rien à enregistrer");
        s.title = "la Boulangère";
        s.look = "skin:abc";
        s.hair = "mca:skins/hair/female/3.png";
        s.size = 0.25f;
        s.showName = false;
        s.maxHealth = 40;
        s.speed = 0.6;
        s.damage = 5;
        s.invincible = true;
        s.behavior = NpcSettings.Behavior.PATROL;
        s.workStart = 1000;
        s.workEnd = 9000;
        s.canTravel = false;
        s.canMarry = false;
        s.canLeave = false;
        s.chatty = 2;
        s.lines.computeIfAbsent("ACCUEIL", k -> new ArrayList<>()).add("Bonjour, {joueur}.");
        s.offers = new CompoundTag();
        assertFalse(s.isDefault());
        NpcSettings r = NpcSettings.load(s.save());
        assertEquals("la Boulangère", r.title);
        assertEquals("skin:abc", r.look);
        assertEquals("mca:skins/hair/female/3.png", r.hair);
        assertEquals(0.25f, r.size);
        assertFalse(r.showName);
        assertEquals(40, r.maxHealth);
        assertEquals(0.6, r.speed);
        assertEquals(5, r.damage);
        assertTrue(r.invincible);
        assertEquals(NpcSettings.Behavior.PATROL, r.behavior);
        assertEquals(1000, r.workStart);
        assertEquals(9000, r.workEnd);
        assertFalse(r.canTravel || r.canMarry || r.canLeave);
        assertEquals(2, r.chatty);
        assertEquals(List.of("Bonjour, {joueur}."), r.lines.get("ACCUEIL"));
        assertNotNull(r.offers);
        assertTrue(NpcSettings.load(new CompoundTag()).isDefault(), "un habitant d'avant cette version n'a rien de réglé");
    }

    @Test
    void citySettingsPoolsFallBackOnTheWholeCity() {
        CitySettings s = new CitySettings();
        assertTrue(s.isDefault());
        assertTrue(s.poolFor("FORGE").isEmpty());
        s.pools.put(CitySettings.ALL, new ArrayList<>(List.of("skin:toutlemonde")));
        s.pools.put("FORGE", new ArrayList<>(List.of("skin:forgeron")));
        assertEquals(List.of("skin:forgeron"), s.poolFor("FORGE"));
        assertEquals(List.of("skin:toutlemonde"), s.poolFor("TEMPLE"));
        assertEquals(List.of("skin:toutlemonde"), s.poolFor(""));
        s.canTravel = false;
        s.peopleMax = 12;
        s.men.add("Elio");
        s.women.add("Bianca");
        s.families.add("Corsi");
        CitySettings r = CitySettings.load(s.save());
        assertFalse(r.canTravel);
        assertTrue(r.canMarry);
        assertEquals(12, r.peopleMax);
        assertEquals(List.of("Elio"), r.men);
        assertEquals(List.of("Bianca"), r.women);
        assertEquals(List.of("Corsi"), r.families);
        assertEquals(List.of("skin:forgeron"), r.poolFor("FORGE"));
    }

    @Test
    void clothesFitTheirOwnSex() {
        assertTrue(CityPeople.fits("mca:skins/clothing/normal/female/guard/3.png", true));
        assertFalse(CityPeople.fits("mca:skins/clothing/normal/female/guard/3.png", false));
        assertTrue(CityPeople.fits("mca:skins/clothing/normal/male/guard/3.png", false));
        assertFalse(CityPeople.fits("mca:skins/clothing/normal/male/guard/3.png", true));
        assertTrue(CityPeople.fits("skin:abc", true), "un skin importé va à tout le monde");
        assertTrue(CityPeople.fits("skin:abc", false));
    }

    @Test
    void skinLinksAreRecognized() {
        assertTrue(SkinSources.isName("Notch"));
        assertTrue(SkinSources.isName("a_b_c"));
        assertFalse(SkinSources.isName("ab"), "trop court pour un pseudo");
        assertFalse(SkinSources.isName("https://x.y/z.png"));
        assertTrue(SkinSources.isUrl("https://x.y/z.png"));
        assertFalse(SkinSources.isUrl("file:///c:/windows/x.png"), "on ne suit que du web");
        assertFalse(SkinSources.isUrl("ftp://x.y/z.png"));
        assertEquals("https://s.namemc.com/i/0123456789abcdef.png", SkinSources.directUrl("https://namemc.com/skin/0123456789abcdef"));
        assertEquals("https://www.minecraftskins.com/skin/download/12345/", SkinSources.directUrl("https://www.minecraftskins.com/skin/12345/un-beau-skin/"));
        assertEquals("https://novask.in/999.png", SkinSources.directUrl("https://minecraft.novaskin.me/skin/999/truc"));
        assertEquals("https://x.y/z.png", SkinSources.directUrl("  https://x.y/z.png "), "un lien direct reste tel quel");
    }

    @Test
    void onlyRealSkinsPass() throws IOException {
        SkinSources.Checked wide = SkinSources.check(png(64, 64, false));
        assertNotNull(wide);
        assertEquals(64, wide.width());
        assertFalse(wide.slim());
        assertTrue(SkinSources.check(png(64, 64, true)).slim(), "le modèle fin se reconnaît à son bras");
        assertNotNull(SkinSources.check(png(64, 32, false)), "l'ancien format reste accepté");
        assertNotNull(SkinSources.check(png(128, 128, false)), "un skin en haute définition aussi");
        assertNull(SkinSources.check(png(100, 100, false)));
        assertNull(SkinSources.check(png(64, 128, false)));
        assertNull(SkinSources.check(new byte[64]), "ce n'est pas une image");
        assertNull(SkinSources.check(null));
        assertNull(SkinSources.check(new byte[SkinSources.MAX_BYTES + 1]));
    }

    @Test
    void hoursOfWorkWrapAroundMidnight() {
        assertEquals(CityRules.Activity.LEISURE, CityRules.withHours(CityRules.Activity.LEISURE, 500, -1, -1), "sans réglage, rien ne change");
        assertEquals(CityRules.Activity.WORK, CityRules.withHours(CityRules.Activity.LEISURE, 2000, 1000, 9000));
        assertEquals(CityRules.Activity.LEISURE, CityRules.withHours(CityRules.Activity.WORK, 10000, 1000, 9000), "hors de ses heures, il ne travaille pas");
        assertEquals(CityRules.Activity.SLEEP, CityRules.withHours(CityRules.Activity.SLEEP, 18000, 1000, 9000), "il dort quand même");
        assertEquals(CityRules.Activity.WORK, CityRules.withHours(CityRules.Activity.SLEEP, 20000, 18000, 2000), "un veilleur de nuit travaille à cheval sur minuit");
        assertEquals(CityRules.Activity.WORK, CityRules.withHours(CityRules.Activity.SLEEP, 1000, 18000, 2000));
    }

    @Test
    void roadsLastAsLongAsTheirLength() {
        assertEquals(CityTravel.ROAD_MIN, CityTravel.roadTicks(10), "jamais moins d'une minute");
        assertEquals(800 * CityTravel.TICKS_PER_BLOCK, CityTravel.roadTicks(800));
        assertEquals(CityTravel.ROAD_MAX, CityTravel.roadTicks(100000), "jamais plus d'une journée");
    }

    @Test
    void tripSurvivesSaving() {
        UUID a = new UUID(1, 2), b = new UUID(3, 4);
        Trip t = new Trip(a, b, Trip.Stage.OUT, 100, 300, CityGood.PAIN, 24, null, 0, 0);
        assertEquals(0.5, t.progress(200));
        assertEquals(0, t.progress(50));
        assertEquals(1, t.progress(900));
        Trip r = Trip.load(t.save());
        assertEquals(t, r);
        Trip later = t.next(Trip.Stage.STAY, 300, 900, CityGood.FER, 8, 40);
        assertEquals(later, Trip.load(later.save()));
        assertEquals(CityGood.PAIN, later.good(), "ce qu'il a emporté reste su");
    }

    @Test
    void ordersPayFairly() {
        Random r = new Random(3);
        for (CityGood g : CityGood.values()) {
            for (int i = 0; i < 40; i++) {
                int n = CityOrders.amount(g, r);
                assertTrue(n >= 1 && n <= 64, g + " demande " + n);
                assertTrue(CityOrders.reward(g, n) >= 1, "une commande paie au moins une émeraude");
            }
        }
        assertEquals(6, CityOrders.reward(CityGood.OUTILS, 2), "un outil vaut plus qu'un sac de blé");
        assertEquals(3, CityOrders.reward(CityGood.BLE, 24));
        assertEquals("1 émeraude", CityOrders.money(1));
        assertEquals("3 émeraudes", CityOrders.money(3));
        CityOrders.Order o = new CityOrders.Order(new UUID(5, 6), CityGood.CUIR, 12, 3, 7);
        assertEquals(o, CityOrders.Order.load(o.save()));
    }

    @Test
    void theMailboxKeepsWhatIsUnread() {
        List<CityPost.Letter> box = new ArrayList<>();
        for (int i = 0; i < CityPost.KEPT; i++) box.add(new CityPost.Letter(CityPost.Kind.AMI, "A", "V", "t" + i, i, i >= 3));
        box.add(new CityPost.Letter(CityPost.Kind.COMMANDE, "B", "V", "neuve", 99, false));
        CityPost.trim(box);
        assertEquals(CityPost.KEPT, box.size());
        assertEquals("t0", box.get(0).text(), "les lettres non lues restent");
        assertFalse(box.stream().anyMatch(l -> l.text().equals("t3")), "la plus vieille lettre lue s'en va");
        assertEquals("neuve", box.get(box.size() - 1).text());
        CityPost.Letter l = new CityPost.Letter(CityPost.Kind.VENTE, "Ana", "Dol", "texte", 12, false);
        assertEquals(l, CityPost.Letter.load(l.save()));
        assertTrue(l.asRead().read());
    }

    @Test
    void everyGoodHasSomeoneToSellIt() {
        for (CityGood g : CityGood.values()) {
            assertFalse(CityScenes.shops(g).isEmpty(), g + " n'a pas de boutique");
            assertTrue(CityScenes.price(g) >= 1);
            for (BuildingFunction f : CityScenes.shops(g)) assertFalse(f.dwelling(), "on n'achète rien dans une maison");
        }
        assertEquals(BuildingFunction.BOULANGERIE, CityScenes.shops(CityGood.PAIN).get(0));
        assertEquals(CityGood.BLE, CityScenes.supply(BuildingFunction.BOULANGERIE, true));
        assertEquals(CityGood.PAIN, CityScenes.supply(BuildingFunction.TAVERNE, true));
        assertEquals(CityGood.VIANDE, CityScenes.supply(BuildingFunction.TAVERNE, false));
        assertNull(CityScenes.supply(BuildingFunction.MAISON, true));
    }

    @Test
    void theJournalTellsTheHour() {
        assertEquals("06h00", CityJournal.clock(0));
        assertEquals("12h00", CityJournal.clock(6000));
        assertEquals("18h00", CityJournal.clock(12000));
        assertEquals("00h00", CityJournal.clock(18000));
        assertEquals("06h00", CityJournal.clock(24000));
        assertEquals("07h30", CityJournal.clock(1500));
    }

    @Test
    void anEscortRemembersItsMerchant() {
        UUID a = new UUID(1, 2), b = new UUID(3, 4), lead = new UUID(5, 6);
        Trip t = new Trip(a, b, Trip.Stage.OUT, 10, 20, null, 0, null, 0, 0, lead);
        assertEquals(t, Trip.load(t.save()));
        assertEquals(lead, t.next(Trip.Stage.STAY, 20, 30, null, 0, 0).leader());
        assertNull(new Trip(a, b, Trip.Stage.OUT, 10, 20, null, 0, null, 0, 0).leader(), "un voyage à son compte ne suit personne");
    }

    @Test
    void pricesFollowScarcityAndTreaties() {
        CityEconomy full = CityEconomy.fresh(), empty = CityEconomy.fresh();
        empty.take(CityGood.PAIN, 1000);
        int normal = CityCommerce.price(CityGood.PAIN, full, true), rare = CityCommerce.price(CityGood.PAIN, empty, true);
        assertEquals(2, normal);
        assertTrue(rare > normal, "ce qui manque se paie plus cher : " + rare);
        assertEquals(1, CityCommerce.price(CityGood.PAIN, full, false), "sans traité, moitié moins");
        assertEquals(1, CityCommerce.price(CityGood.BLE, full, false), "jamais moins d'une pièce");
        assertTrue(CityCommerce.basePrice(CityGood.OUTILS) > CityCommerce.basePrice(CityGood.BLE));
    }

    @Test
    void aMarketLivesOffItsLand() {
        CityEconomy e = CityEconomy.fresh();
        int fer = e.stock(CityGood.FER), livre = e.stock(CityGood.LIVRE);
        long purse = e.purse();
        for (int i = 0; i < 4; i++) CityMarkets.simulate(e, CityGood.FER, 20);
        assertEquals(fer + 4 * 20, e.stock(CityGood.FER), "sa terre donne sa spécialité");
        assertEquals(Math.max(0, livre - 4), e.stock(CityGood.LIVRE), "ce qu'elle ne produit pas s'épuise");
        assertTrue(e.purse() > purse);
        for (int i = 0; i < 40; i++) CityMarkets.simulate(e, CityGood.FER, 20);
        assertTrue(e.stock(CityGood.FER) <= CityEconomy.CAP, "les réserves ont une limite");
        assertEquals(0, e.stock(CityGood.LIVRE));
    }

    @Test
    void aLineSurvivesSaving() {
        CityLine l = new CityLine(new UUID(1, 1), new UUID(2, 2), new UUID(3, 3));
        l.good = CityGood.FER;
        l.back = CityGood.PAIN;
        l.amount = 48;
        l.every = 2;
        l.escort = 1;
        l.pave = true;
        l.active = false;
        l.ai = true;
        l.trips = 5;
        l.moved = 240;
        l.earned = 700;
        l.status = "road";
        CityLine r = CityLine.load(l.save());
        assertEquals(CityGood.FER, r.good);
        assertEquals(CityGood.PAIN, r.back);
        assertEquals(48, r.amount);
        assertEquals(2, r.every);
        assertTrue(r.pave && r.ai && !r.active);
        assertEquals(700, r.earned);
        assertEquals("road", r.status);
        CityLine free = CityLine.load(new CityLine(new UUID(1, 1), new UUID(2, 2), new UUID(3, 3)).save());
        assertNull(free.good, "sans marchandise choisie : le surplus du moment");
        UUID a = new UUID(1, 2), b = new UUID(3, 4), line = new UUID(5, 6);
        Trip t = new Trip(a, b, Trip.Stage.OUT, 10, 20, CityGood.FER, 8, null, 0, 0, null, line);
        assertEquals(t, Trip.load(t.save()));
        assertEquals(line, t.next(Trip.Stage.STAY, 20, 30, null, 0, 0).line());
    }

    /** Un terrain plat, avec au milieu ce qu'on veut. */
    private static CityRoads.Terrain flatWith(java.util.function.IntBinaryOperator height) {
        return height::applyAsInt;
    }

    private static boolean touches(java.util.List<int[]> route, int x0, int z0, int x1, int z1) {
        CityRoads.Route r = CityRoads.Route.of(route);
        int steps = (int) r.length();
        for (int i = 0; i <= steps; i++) {
            int[] p = r.at((double) i / Math.max(1, steps));
            if (p[0] >= x0 && p[0] <= x1 && p[1] >= z0 && p[1] <= z1) return true;
        }
        return false;
    }

    @Test
    void roadsGoAroundWaterAndHills() {
        List<int[]> flat = CityRoads.plan(0, 0, 640, 0, flatWith((x, z) -> 70), 63, 20000);
        assertNotNull(flat);
        assertEquals(2, flat.size(), "sur du plat, on va tout droit");
        // Un lac en travers du chemin.
        List<int[]> lake = CityRoads.plan(0, 0, 640, 0, flatWith((x, z) -> x > 240 && x < 400 && Math.abs(z) < 120 ? 50 : 70), 63, 20000);
        assertNotNull(lake);
        assertFalse(touches(lake, 260, -100, 380, 100), "la route traverse le lac");
        assertTrue(CityRoads.Route.of(lake).length() > 640, "un détour est plus long");
        assertEquals(0, lake.get(0)[0]);
        assertEquals(640, lake.get(lake.size() - 1)[0]);
        // Une falaise qui barre tout, sauf un col.
        List<int[]> pass = CityRoads.plan(0, 0, 640, 0, flatWith((x, z) -> x > 300 && x < 340 && Math.abs(z - 160) > 24 ? 140 : 70), 63, 20000);
        assertNotNull(pass);
        assertTrue(touches(pass, 300, 136, 340, 184), "la route ne passe pas par le col");
        // Trop loin pour le temps qu'on y met : on renonce, et on marchera tout droit.
        assertNull(CityRoads.plan(0, 0, 6400, 6400, flatWith((x, z) -> (x / 16 + z / 16) % 2 == 0 ? 60 : 120), 63, 8));
    }

    @Test
    void roadsPreferExistingRoads() {
        CityRoads.Terrain withRoad = new CityRoads.Terrain() {
            @Override
            public int height(int x, int z) {
                return 70;
            }

            @Override
            public boolean road(int x, int z) {
                return Math.abs(z - 96) < 12;
            }
        };
        List<int[]> r = CityRoads.plan(0, 0, 640, 0, withRoad, 63, 20000);
        assertNotNull(r);
        assertTrue(touches(r, 200, 84, 440, 108), "la route ignore celle qui existe déjà");
    }

    @Test
    void aRouteIsWalkedFromEndToEnd() {
        CityRoads.Route r = CityRoads.Route.of(List.of(new int[]{0, 0}, new int[]{100, 0}, new int[]{100, 100}));
        assertEquals(200, r.length());
        assertEquals(0, r.at(0)[0]);
        assertEquals(50, r.at(0.25)[0]);
        assertEquals(100, r.at(0.5)[0]);
        assertEquals(50, r.at(0.75)[1]);
        assertEquals(100, r.at(1)[1]);
        assertEquals(100, r.at(5)[1], "au-delà de la fin, on y reste");
        CityRoads.Route back = r.reversed();
        assertEquals(100, back.at(0)[1]);
        assertEquals(0, back.at(1)[0]);
        assertEquals(r.at(0.3)[0], back.at(0.7)[0]);
        CityRoads.Route again = CityRoads.Route.load(r.save());
        assertEquals(r.length(), again.length());
        assertEquals(100, again.at(1)[1]);
    }

    @Test
    void nationKeysAreClean() {
        assertEquals("levanzia", NationFile.keyOf("Levànzia"));
        assertEquals("royaume_du_nord", NationFile.keyOf("  Royaume du Nord! "));
        assertEquals("nation", NationFile.keyOf("???"));
    }

    @Test
    void demandFollowsWhatIsLeft() {
        CityEconomy e = new CityEconomy();
        assertEquals(200, e.demand(CityGood.FER), "rien en stock : le double");
        e.add(CityGood.FER, 32);
        assertEquals(100, e.demand(CityGood.FER), "assez : le prix normal");
        e.add(CityGood.FER, CityEconomy.CAP);
        assertEquals(50, e.demand(CityGood.FER), "ses réserves débordent : la moitié");
        e.take(CityGood.FER, 32 + CityEconomy.CAP - 16);
        assertTrue(e.demand(CityGood.FER) > 100, "il en reste peu : plus cher");
    }

    @Test
    void aLoadIsWorthMoreWhereItLacks() {
        CityEconomy poor = new CityEconomy(), rich = new CityEconomy();
        rich.add(CityGood.BLE, CityEconomy.CAP);
        assertEquals(48, CityCommerce.quote(CityGood.BLE, poor, true, 24), "24 blés là où il n'y en a plus");
        assertEquals(12, CityCommerce.quote(CityGood.BLE, rich, true, 24), "24 blés là où ils débordent");
        assertEquals(24, CityCommerce.quote(CityGood.BLE, poor, false, 24), "sans traité : la moitié");
        assertTrue(CityCommerce.price(CityGood.BLE, rich, false) >= 1, "rien ne se vend pour rien");
    }

    @Test
    void tollsArePaidOnlyWhereTheyAreDue() {
        UUID home = new UUID(0, 1), dest = new UUID(0, 2), friend = new UUID(0, 3), stranger = new UUID(0, 4);
        var due = CityTolls.due(List.of(home, friend, stranger, dest), home, dest, r -> 5, friend::equals);
        assertEquals(1, due.size(), "ni chez soi, ni à destination, ni chez qui en dispense");
        assertEquals(stranger, due.get(0).realm());
        assertTrue(CityTolls.due(List.of(stranger), home, dest, r -> 0, r -> false).isEmpty(), "un péage à zéro ne se paie pas");
        assertEquals(5, CityTolls.nearest(CityTolls.TOLLS, 6), "on ne garde que les valeurs proposées");
        assertEquals(30, CityTolls.nearest(CityTolls.DUTIES, 999));
        var rules = new CityTolls.Rules(10, 20);
        assertEquals(rules, CityTolls.Rules.load(rules.save()));
        assertTrue(CityTolls.byDefault(fr.crusadecraft.core.world.AiPersonality.FANATIQUE).toll() > CityTolls.byDefault(fr.crusadecraft.core.world.AiPersonality.MARCHAND).toll());
    }

    @Test
    void aBesiegedCityStarves() {
        CityEconomy free = CityEconomy.fresh(), shut = CityEconomy.fresh();
        var workers = java.util.Map.<BuildingFunction, Integer>of();
        CityEconomy.Census open = new CityEconomy.Census(workers, 40, false, java.util.Map.of(), 0, 0, false);
        CityEconomy.Census closed = new CityEconomy.Census(workers, 40, true, java.util.Map.of(), 0, 0, true);
        for (int i = 0; i < 8; i++) {
            free.simulate(open, false, true, new Random(i));
            shut.simulate(closed, false, true, new Random(i));
        }
        assertEquals(0, free.famine(), "une ville libre mange");
        assertTrue(shut.famine() > 0, "coupée du monde, elle a faim");
        assertTrue(free.stock(CityGood.BLE) > shut.stock(CityGood.BLE));
    }

    @Test
    void aBesiegedMarketEatsItsReserves() {
        CityEconomy e = CityEconomy.fresh();
        for (int i = 0; i < 6; i++) CityMarkets.simulate(e, CityGood.FER, 30, true);
        assertEquals(16, e.stock(CityGood.FER), "sa spécialité ne se produit plus");
        assertTrue(e.famine() > 0, "plus de grain : la faim");
        long purse = e.purse();
        CityMarkets.simulate(e, CityGood.FER, 30, false);
        assertEquals(0, e.famine(), "le blocus levé, elle remange");
        assertTrue(e.purse() > purse, "et sa bourse remonte");
    }

    @Test
    void contractsPayForTheRoad() {
        assertEquals(2, CityContracts.courierReward(10), "jamais moins de deux émeraudes");
        assertEquals(24, CityContracts.courierReward(100000), "ni plus que ça");
        assertTrue(CityContracts.escortReward(1000) > CityContracts.courierReward(1000), "une escorte vaut mieux qu'un pli");
        assertEquals(1, CityContracts.courierDays(500));
        assertEquals(2, CityContracts.courierDays(2500));
    }

    @Test
    void anEscortMustWalkAlong() {
        assertFalse(CityContracts.present(0, 0), "on ne l'a pas vu");
        assertFalse(CityContracts.present(59, 100));
        assertTrue(CityContracts.present(60, 100));
    }

    @Test
    void compassPointsTheRightWay() {
        assertTrue(CityContracts.directionKey(0, -100).endsWith(".north"), "le nord est en z négatif");
        assertTrue(CityContracts.directionKey(100, 0).endsWith(".east"));
        assertTrue(CityContracts.directionKey(0, 100).endsWith(".south"));
        assertTrue(CityContracts.directionKey(-100, 0).endsWith(".west"));
        assertTrue(CityContracts.directionKey(100, -100).endsWith(".northeast"));
    }

    @Test
    void aContractSurvivesSaving() {
        CityContract c = new CityContract(UUID.randomUUID(), CityContract.Kind.ESCORT, UUID.randomUUID(), UUID.randomUUID(), 12, 3, 6);
        c.taker = UUID.randomUUID();
        c.line = UUID.randomUUID();
        c.merchant = UUID.randomUUID();
        c.near = 4;
        c.total = 9;
        CityContract l = CityContract.load(c.save());
        assertEquals(c.id, l.id);
        assertEquals(CityContract.Kind.ESCORT, l.kind);
        assertEquals(12, l.reward);
        assertEquals(c.taker, l.taker);
        assertEquals(c.merchant, l.merchant);
        assertEquals(9, l.total);
    }

    /** Un entrepôt de papier. */
    private static final class Shelf implements CityDocks.Warehouse {
        final java.util.EnumMap<CityGood, Integer> items = new java.util.EnumMap<>(CityGood.class);
        int room = Integer.MAX_VALUE;

        @Override
        public int count(CityGood g) {
            return items.getOrDefault(g, 0);
        }

        @Override
        public int take(CityGood g, int n) {
            int got = Math.min(count(g), n);
            items.put(g, count(g) - got);
            return got;
        }

        @Override
        public int give(CityGood g, int n) {
            int put = Math.min(room, n);
            items.put(g, count(g) + put);
            room -= put;
            return put;
        }
    }

    private static CityEconomy emptyEconomy() {
        CityEconomy e = new CityEconomy();
        e.setStock(CityGood.FER, 0);
        return e;
    }

    @Test
    void aDockShowsWhatTheWarehouseCanSpare() {
        fr.crusadecraft.core.ColonyRef ref = new fr.crusadecraft.core.ColonyRef(net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), 7);
        CityDocks.Dock dock = new CityDocks.Dock(ref);
        CityEconomy e = emptyEconomy();
        Shelf w = new Shelf();
        w.items.put(CityGood.FER, 100);
        w.items.put(CityGood.BOIS, 10);
        CityDocks.sync(e, dock, w);
        assertEquals(100 - CityDocks.KEEP, e.stock(CityGood.FER), "on voit ce qui dépasse de ce qu'on lui laisse");
        assertEquals(0, e.stock(CityGood.BOIS), "ce qu'elle a de justesse n'est pas à vendre");
        // Une caravane en emporte seize : au passage suivant, ils quittent vraiment l'entrepôt.
        e.take(CityGood.FER, 16);
        CityDocks.sync(e, dock, w);
        assertEquals(84, w.count(CityGood.FER), "l'entrepôt n'a pas donné ce qui est parti");
        assertEquals(84 - CityDocks.KEEP, e.stock(CityGood.FER));
        // Une caravane apporte du pain : il va à l'entrepôt.
        e.add(CityGood.PAIN, 24);
        CityDocks.sync(e, dock, w);
        assertEquals(24, w.count(CityGood.PAIN), "le pain n'est pas rangé");
    }

    @Test
    void aDockWaitsWhileTheWarehouseIsAway() {
        fr.crusadecraft.core.ColonyRef ref = new fr.crusadecraft.core.ColonyRef(net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), 8);
        CityDocks.Dock dock = new CityDocks.Dock(ref);
        CityEconomy e = emptyEconomy();
        Shelf w = new Shelf();
        w.items.put(CityGood.FER, 100);
        CityDocks.sync(e, dock, w);
        // Plus personne n'est là : l'entrepôt n'est pas chargé. Le commerce tourne quand même, et on note.
        e.take(CityGood.FER, 16);
        e.add(CityGood.PAIN, 24);
        CityDocks.sync(e, dock, null);
        assertEquals(24, dock.owed(CityGood.PAIN), "on oublie ce qu'on doit lui apporter");
        assertEquals(-16, dock.owed(CityGood.FER), "on oublie ce qu'on lui a pris");
        assertEquals(100, w.count(CityGood.FER), "on ne touche à rien sans lui");
        assertEquals(24, e.stock(CityGood.PAIN), "le pain attend sur le quai");
        // Il revient : tout s'écrit.
        CityDocks.sync(e, dock, w);
        assertEquals(84, w.count(CityGood.FER));
        assertEquals(24, w.count(CityGood.PAIN));
        assertEquals(0, dock.owed(CityGood.PAIN));
        assertEquals(0, dock.owed(CityGood.FER));
    }

    @Test
    void aFullWarehouseKeepsTheRestOnTheDock() {
        fr.crusadecraft.core.ColonyRef ref = new fr.crusadecraft.core.ColonyRef(net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), 9);
        CityDocks.Dock dock = new CityDocks.Dock(ref);
        CityEconomy e = emptyEconomy();
        Shelf w = new Shelf();
        w.room = 10;
        e.add(CityGood.PAIN, 24);
        CityDocks.sync(e, dock, w);
        assertEquals(10, w.count(CityGood.PAIN));
        assertEquals(14, dock.owed(CityGood.PAIN), "rien ne se perd : le reste attend sur le quai");
        assertEquals(14, e.stock(CityGood.PAIN), "et il reste à vendre");
    }

    @Test
    void aDockHasItsOwnKindOfId() {
        fr.crusadecraft.core.ColonyRef a = new fr.crusadecraft.core.ColonyRef(net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), 1);
        fr.crusadecraft.core.ColonyRef b = new fr.crusadecraft.core.ColonyRef(net.minecraft.resources.ResourceLocation.tryParse("minecraft:the_nether"), 1);
        assertTrue(CityDocks.isDockId(CityDocks.idOf(a)));
        assertFalse(CityDocks.idOf(a).equals(CityDocks.idOf(b)), "deux mondes, deux quais");
        assertFalse(CityDocks.isDockId(UUID.randomUUID()));
        assertFalse(CityDocks.isDockId(CityMarkets.idOf(1)), "un marché n'est pas un quai");
        CityDocks.Dock d = new CityDocks.Dock(a);
        assertEquals(a, CityDocks.Dock.load(d.save()).colony);
    }

    @Test
    void aMarketProspersWhenItsPurseIsFull() {
        assertEquals(0, CityGrowth.next(0, 50, 0, false, 60), "une bourse maigre ne fait rien");
        assertEquals(1, CityGrowth.next(0, 200, 0, false, 60), "une bourse honnête : doucement");
        assertEquals(2, CityGrowth.next(0, 500, 0, false, 60), "une bourse pleine : plus vite");
        assertEquals(7, CityGrowth.next(10, 500, 2, false, 60), "la faim fait reculer, bourse pleine ou non");
        assertEquals(7, CityGrowth.next(10, 500, 0, true, 60), "le blocus aussi");
        assertEquals(0, CityGrowth.next(1, 0, 3, false, 60), "jamais sous zéro");
        assertEquals(60, CityGrowth.next(60, 500, 0, false, 60), "ni au-dessus de ce qu'il faut");
    }

    @Test
    void aBesiegedCityLosesItsFields() {
        CityEconomy free = new CityEconomy(), shut = new CityEconomy();
        var farmers = java.util.Map.of(BuildingFunction.FERME, 4);
        free.simulate(new CityEconomy.Census(farmers, 0, false, java.util.Map.of(), 0, 0, false), false, false, new Random(1));
        shut.simulate(new CityEconomy.Census(farmers, 0, true, java.util.Map.of(), 0, 0, true), false, false, new Random(1));
        assertEquals(48, free.stock(CityGood.BLE), "quatre fermiers libres");
        assertEquals(12, shut.stock(CityGood.BLE), "les champs sont hors les murs");
    }

    @Test
    void aBlockadeCampCirclesTheTown() {
        assertEquals(48, CityCamps.ring(0), "une petite ville : un cercle assez large quand même");
        assertEquals(124, CityCamps.ring(200));
        assertEquals(16, CityCamps.men(0));
        assertEquals(24, CityCamps.men(800), "jamais une armée entière");
        assertArrayEquals(new int[]{48, 0}, CityCamps.post(0, 8, 48, 0));
        assertArrayEquals(new int[]{0, 48}, CityCamps.post(2, 8, 48, 0));
        assertArrayEquals(new int[]{-48, 0}, CityCamps.post(4, 8, 48, 0));
    }

    @Test
    void aBigTownSiteSpeedsUpToFinishInTime() {
        assertEquals(80, fr.crusadecraft.core.build.plan.TownPlan.blocksPerSecond(80, 100_000, 30), "une ville moyenne garde sa cadence");
        assertEquals(792, fr.crusadecraft.core.build.plan.TownPlan.blocksPerSecond(80, 1_424_608, 30), "une très grande ville finit en une demi-heure");
        assertEquals(3000, fr.crusadecraft.core.build.plan.TownPlan.blocksPerSecond(80, 90_000_000, 30), "jamais de quoi faire ramer le serveur");
        assertEquals(80, fr.crusadecraft.core.build.plan.TownPlan.blocksPerSecond(80, 1_424_608, 0), "0 : jamais d'accélération");
        assertEquals(80, fr.crusadecraft.core.build.plan.TownPlan.blocksPerSecond(80, 0, 30), "plan pas encore dressé");
    }
}
