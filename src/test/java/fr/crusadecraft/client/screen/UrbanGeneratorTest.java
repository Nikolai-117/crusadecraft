package fr.crusadecraft.client.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.crusadecraft.core.build.plan.TownSize;
import fr.crusadecraft.core.build.plan.UrbanGenerator;
import fr.crusadecraft.core.build.plan.UrbanParams;
import fr.crusadecraft.core.build.plan.UrbanPlan;
import fr.crusadecraft.core.build.plan.UrbanPresets;
import fr.crusadecraft.core.build.plan.UrbanWard;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Le générateur de l'atelier (P60), sans Minecraft : un faux catalogue de plans, et l'on vérifie ce que le plan promet. Il dessine aussi chaque préréglage, pour qu'on les regarde. */
class UrbanGeneratorTest {
    /** Un catalogue plausible : des maisons de quelques tailles, de grands bâtiments, de petits décors. */
    private static final UrbanGenerator.Plans FAKE = new UrbanGenerator.Plans() {
        @Override
        public Optional<UrbanGenerator.Pick> pick(TownSize.Role role, int level, int min, int max, Random random) {
            int w, d;
            switch (role) {
                case HOUSE -> {
                    w = 8 + random.nextInt(7);
                    d = 8 + random.nextInt(7);
                }
                case CHURCH, TOWNHALL, BARRACKS -> {
                    w = 22 + random.nextInt(10);
                    d = 24 + random.nextInt(10);
                }
                case MARKET, WAREHOUSE, HOSPITAL, SCHOOL, GRAVEYARD -> {
                    w = 16 + random.nextInt(6);
                    d = 16 + random.nextInt(6);
                }
                case FARM -> {
                    w = 12;
                    d = 14;
                }
                case LAMP -> {
                    w = 1;
                    d = 1;
                }
                case STALL, STATUE, PLAZA -> {
                    w = 3 + random.nextInt(3);
                    d = 3 + random.nextInt(3);
                }
                case GARDEN, DECOR -> {
                    w = 5 + random.nextInt(4);
                    d = 5 + random.nextInt(4);
                }
                default -> {
                    w = 10 + random.nextInt(5);
                    d = 11 + random.nextInt(5);
                }
            }
            return Optional.of(new UrbanGenerator.Pick("faux|" + role + "_" + w + "x" + d + ".nbt", w, d));
        }
    };

    private static UrbanParams base(long seed) {
        return new UrbanParams().seed(seed);
    }

    private static UrbanPlan make(UrbanParams p) {
        return UrbanGenerator.generate(p, FAKE).orElseThrow();
    }

    private static void noOverlap(UrbanPlan plan) {
        List<UrbanGenerator.Placed> all = plan.placed();
        for (int i = 0; i < all.size(); i++) {
            var a = all.get(i).rect();
            for (int j = i + 1; j < all.size(); j++) {
                var b = all.get(j).rect();
                boolean apart = a.x1() < b.x0() || b.x1() < a.x0() || a.z1() < b.z0() || b.z1() < a.z0();
                assertTrue(apart, "deux bâtiments se chevauchent : " + all.get(i) + " et " + all.get(j));
            }
        }
    }

    private static int count(UrbanPlan p, byte kind) {
        int n = 0;
        for (byte b : p.view()) if (b == kind) n++;
        return n;
    }

    private static int cellsUnderBuildings(UrbanPlan p, byte kind) {
        int side = 2 * p.half() + 1, n = 0;
        for (UrbanGenerator.Placed b : p.placed()) {
            for (int x = b.rect().x0(); x <= b.rect().x1(); x++) {
                for (int z = b.rect().z0(); z <= b.rect().z1(); z++) {
                    if (p.view()[x + p.half() + (z + p.half()) * side] == kind) n++;
                }
            }
        }
        return n;
    }

    @Test
    void theSameSettingsGiveTheSameTown() {
        UrbanPlan a = make(base(42)), b = make(base(42));
        assertArrayEquals(a.view(), b.view(), "un tirage doit se rejouer à l'identique : l'aperçu est ce qui sera posé");
        assertEquals(a.placed().size(), b.placed().size());
        assertFalse(java.util.Arrays.equals(a.view(), make(base(43)).view()), "un autre tirage, une autre ville");
    }

    @Test
    void everyPresetMakesARealTownWithoutOverlap() {
        for (UrbanPresets.Preset preset : UrbanPresets.all()) {
            for (long seed : new long[]{1, 7, 1234}) {
                UrbanPlan plan = make(UrbanPresets.apply(preset, base(seed)));
                noOverlap(plan);
                assertTrue(plan.placed().size() >= 3, preset + " n'a presque rien bâti : " + plan.stats());
                assertTrue(plan.stats().houses() > 0, preset + " sans maison");
                assertTrue(count(plan, UrbanGenerator.ROAD_MAIN) > 0, preset + " sans grande route");
                for (UrbanGenerator.Placed b : plan.placed()) assertTrue(b.rect().x0() >= -plan.half() && b.rect().x1() <= plan.half(), "un bâtiment hors du plan");
                assertEquals(0, cellsUnderBuildings(plan, UrbanGenerator.ROAD), preset + " : une rue sous un bâtiment");
                assertEquals(0, cellsUnderBuildings(plan, UrbanGenerator.RIVER), preset + " : un bâtiment dans la rivière");
                assertEquals(0, cellsUnderBuildings(plan, UrbanGenerator.SEA), preset + " : un bâtiment dans la mer");
            }
        }
    }

    @Test
    void mostOfThePeopleGetAHouse() {
        for (long seed = 1; seed <= 6; seed++) {
            UrbanPlan plan = make(base(seed));
            double share = (double) plan.stats().houses() / plan.stats().housesWanted();
            assertTrue(share >= 0.8, "seulement " + plan.stats().houses() + " maisons sur " + plan.stats().housesWanted() + ", tirage " + seed);
        }
    }

    @Test
    void wallsRiversAndSeaObeyTheirSwitches() {
        assertEquals(0, count(make(base(5).set(UrbanParams.MURS, 0)), UrbanGenerator.WALL), "pas d'enceinte demandée");
        assertTrue(count(make(base(5).set(UrbanParams.MURS, 1).set(UrbanParams.TYPE_MUR, 1)), UrbanGenerator.WALL) > 50, "une enceinte demandée");
        assertEquals(0, count(make(base(5)), UrbanGenerator.RIVER), "pas de rivière demandée");
        assertTrue(count(make(base(5).set(UrbanParams.RIVIERE, 1)), UrbanGenerator.RIVER) > 100, "une rivière demandée");
        assertEquals(0, count(make(base(5)), UrbanGenerator.SEA), "pas de mer demandée");
        assertTrue(count(make(base(5).set(UrbanParams.COTE, 1)), UrbanGenerator.SEA) > 100, "une côte demandée");
        UrbanPlan island = make(base(5).set(UrbanParams.COTE, 2));
        int side = 2 * island.half() + 1;
        assertEquals(UrbanGenerator.SEA, island.view()[0], "l'île a la mer à ses quatre coins");
        assertEquals(UrbanGenerator.SEA, island.view()[side * side - 1], "l'île a la mer à ses quatre coins");
        assertTrue(count(make(base(5).set(UrbanParams.RIVIERE, 1).set(UrbanParams.ROUTES, 4)), UrbanGenerator.BRIDGE) > 0, "une route doit franchir la rivière");
        assertTrue(count(make(base(5).set(UrbanParams.MURS, 1).set(UrbanParams.TYPE_MUR, 1).set(UrbanParams.DOUVES, 1)), UrbanGenerator.MOAT) > 50, "des douves demandées");
    }

    @Test
    void theGatesAreWhatWasAskedFor() {
        for (int want : new int[]{1, 2, 4, 6}) {
            UrbanPlan plan = make(base(11).set(UrbanParams.TYPE_MUR, 1).set(UrbanParams.ROUTES, 6).set(UrbanParams.PORTES, want));
            assertEquals(want, plan.stats().gates(), "le nombre de portes");
        }
    }

    @Test
    void theHousesFollowThePopulationAndTheCountsObeyTheirSettings() {
        UrbanPlan small = make(base(3).set(UrbanParams.HABITANTS, 100));
        UrbanPlan big = make(base(3).set(UrbanParams.HABITANTS, 1000));
        assertTrue(big.stats().houses() > small.stats().houses() * 3, small.stats() + " puis " + big.stats());
        assertTrue(big.half() > small.half(), "plus de monde, plus de place");
        UrbanPlan none = make(base(3).set(UrbanParams.count(TownSize.Role.TAVERN), 0));
        assertEquals(0, none.stats().built().getOrDefault(TownSize.Role.TAVERN, 0), "zéro taverne demandée");
        UrbanPlan three = make(base(3).set(UrbanParams.HABITANTS, 800).set(UrbanParams.count(TownSize.Role.TAVERN), 3));
        assertEquals(3, three.stats().built().getOrDefault(TownSize.Role.TAVERN, 0), "trois tavernes demandées : " + three.stats());
    }

    @Test
    void handEditsAreKept() {
        UrbanParams p = base(21);
        UrbanPlan plan = make(p);
        UrbanGenerator.Lot lot = plan.lots().stream().filter(l -> l.area > 200 && !l.citadel).findFirst().orElseThrow();
        UrbanWard other = lot.ward == UrbanWard.PARC ? UrbanWard.MARCHE : UrbanWard.PARC;
        p.setLotWard(lot.id, other.ordinal());
        UrbanPlan edited = make(p);
        assertEquals(other, edited.lots().get(lot.id).ward, "l'îlot a changé de nature");
        // Un bâtiment retiré ne revient pas.
        UrbanParams q = base(21);
        int slot = plan.placed().get(plan.placed().size() / 2).slot();
        q.toggleRemoved(slot);
        UrbanPlan without = make(q);
        assertTrue(without.placed().stream().noneMatch(b -> b.slot() == slot), "le bâtiment retiré est encore là");
        assertEquals(1, new java.util.HashSet<>(List.of(q.removed().size())).iterator().next());
    }

    @Test
    void theCitadelHasItsKeep() {
        for (long seed : new long[]{1, 2, 3, 4}) {
            UrbanPlan plan = make(base(seed).set(UrbanParams.HABITANTS, 900).set(UrbanParams.CITADELLE, 1).set(UrbanParams.TYPE_MUR, 2).set(UrbanParams.TAILLE_CITADELLE, 60));
            UrbanGenerator.Lot citadel = plan.lots().stream().filter(l -> l.citadel).max(java.util.Comparator.comparingInt(l -> l.area)).orElse(null);
            assertTrue(citadel != null, "pas de citadelle, tirage " + seed);
            long inside = plan.placed().stream().filter(b -> b.lot() == citadel.id).count();
            assertTrue(inside >= 1, "une citadelle vide, tirage " + seed + " : " + citadel.area + " cases");
            assertTrue(plan.placed().stream().anyMatch(b -> b.lot() == citadel.id && b.role() == TownSize.Role.TOWNHALL), "pas de donjon, tirage " + seed + " : " + plan.placed().stream().filter(b -> b.lot() == citadel.id).map(b -> b.role() + " " + b.rect()).toList() + " dans " + citadel.area + " cases, de " + citadel.x0 + "," + citadel.z0 + " à " + citadel.x1 + "," + citadel.z1);
            assertTrue(count(plan, UrbanGenerator.PLAZA) > 0, "elle a sa cour");
        }
    }

    @Test
    void theFieldsAreThereWhenAskedFor() {
        UrbanPlan plan = make(base(9).set(UrbanParams.HABITANTS, 600).set(UrbanParams.CHAMPS, 1).set(UrbanParams.SURFACE_CHAMPS, 60));
        assertTrue(count(plan, UrbanGenerator.FIELD) > 2000, "si peu de champs : " + count(plan, UrbanGenerator.FIELD));
        assertEquals(0, count(make(base(9).set(UrbanParams.CHAMPS, 0)), UrbanGenerator.FIELD), "pas de champs demandés");
        UrbanPlan lone = make(base(9).set(UrbanParams.HABITANTS, 600).set(UrbanParams.CHAMPS, 3));
        assertTrue(lone.stats().built().getOrDefault(TownSize.Role.FARM, 0) > 0, "des fermes isolées");
    }

    @Test
    void theSettingsSurviveAndAreBounded() {
        UrbanParams p = base(99).name("Vieille-Forge");
        p.set(UrbanParams.HABITANTS, 750).set(UrbanParams.RIVIERE, 1);
        p.setLotWard(4, 2);
        p.toggleRemoved(3);
        p.reroll(8);
        UrbanParams again = UrbanParams.load(p.save());
        assertEquals("Vieille-Forge", again.name());
        assertEquals(99, again.seed());
        assertEquals(750, again.get(UrbanParams.HABITANTS));
        assertTrue(again.on(UrbanParams.RIVIERE));
        assertEquals(2, again.lotWards().get(4));
        assertTrue(again.removed().contains(3));
        assertEquals(1, again.rerolls().get(8));
        // Ce qui vient du client n'est jamais cru : une valeur absurde est ramenée, un réglage inconnu ignoré.
        CompoundTag t = new CompoundTag();
        CompoundTag v = new CompoundTag();
        v.putInt("habitants", 2_000_000_000);
        v.putInt("largeur_rue", -50);
        v.putInt("inconnu", 7);
        t.put("values", v);
        UrbanParams wild = UrbanParams.load(t);
        assertEquals(UrbanParams.HABITANTS.max(), wild.get(UrbanParams.HABITANTS));
        assertEquals(UrbanParams.LARGEUR_RUE.min(), wild.get(UrbanParams.LARGEUR_RUE));
    }

    @Test
    void anyMixOfSettingsDoesNotBreak() {
        // Des réglages tirés au hasard, y compris les extrêmes : jamais de plantage, jamais de chevauchement.
        Random r = new Random(2026);
        List<UrbanParams.Def> defs = new ArrayList<>(UrbanParams.defs());
        for (int run = 0; run < 60; run++) {
            UrbanParams p = base(r.nextLong());
            for (UrbanParams.Def d : defs) {
                if (r.nextInt(3) != 0) continue;
                int v = d.min() + r.nextInt(d.max() - d.min() + 1);
                p.set(d, d == UrbanParams.HABITANTS ? Math.min(v, 1200) : v);
            }
            noOverlap(make(p));
        }
    }

    /** Une image de chaque préréglage dans build/urban : on regarde les villes, on ne se contente pas de les compter. */
    @Test
    void drawsThePresets() throws IOException {
        File dir = new File("build/urban");
        dir.mkdirs();
        for (UrbanPresets.Preset preset : UrbanPresets.all()) draw(make(UrbanPresets.apply(preset, base(17))), new File(dir, preset.name().toLowerCase() + ".png"));
        draw(make(base(17).set(UrbanParams.TYPE_MUR, 1)), new File(dir, "defaut.png"));
    }

    private static void draw(UrbanPlan plan, File to) throws IOException {
        int side = 2 * plan.half() + 1;
        BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                int c = UrbanPalette.abgr(plan.view()[x + z * side], x, z);
                img.setRGB(x, z, (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF);
            }
        }
        ImageIO.write(img, "png", to);
    }
}
