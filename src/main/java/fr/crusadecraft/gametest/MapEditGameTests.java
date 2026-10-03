package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** L'éditeur de la carte (P12) : poser royaumes et villes, peindre les frontières, effacer proprement. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapEditGameTests {

    private MapEditGameTests() {}

    /**
     * Le joueur fictif du test a droit à l'éditeur sans toucher aux réglages (un réglage changé pendant les tests est
     * réécrit sur le disque puis relu, et peut sauter un instant ; et le serveur de test refuse toute permission aux
     * opérateurs). Le monde IA est vidé, sans se regénérer pendant le test.
     */
    private static Runnable setUp(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        WorldEditService.grantForTests(w.ruler.getUUID(), true);
        var data = CrusadeSavedData.get(server);
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
        return () -> WorldEditService.grantForTests(w.ruler.getUUID(), false);
    }

    private static void cleanup(TestWorld w, Runnable restore) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (War war : new ArrayList<>(data.wars().all())) {
            data.wars().damage().release(war.id());
            data.wars().remove(war.id());
            WarFieldService.onWarEnded(server, war.id());
        }
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        if (w.realm != null) {
            data.diplomacy().forget(w.realm.id());
            w.realm.mail().clear();
        }
        data.world().clear();
        restore.run();
        w.cleanup();
    }

    private static AiRealm only(CrusadeSavedData data, String place) {
        return data.world().realms().stream().filter(r -> r.place().equals(place)).findFirst().orElseThrow();
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p12_poser", timeoutTicks = 200)
    public static void poserRoyaumesEtVilles(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = setUp(w);
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            int x = w.center.getX() + 600, z = w.center.getZ();
            var out = WorldEditService.createRealm(w.ruler, "Valcourt", "Godefroy", TitleRank.COMTE, 0, AiFaith.PAIEN,
                    AiPersonality.MARCHAND, x, z);
            w.check(out.success(), "royaume refusé : " + out.message().getString());
            AiRealm valcourt = only(data, "Valcourt");
            w.check(valcourt.handMade(), "le royaume n'est pas marqué fait main");
            w.check(valcourt.discoveredBy(w.ruler.getUUID()), "son auteur ne le connaît pas");
            w.check(valcourt.ruler().name().equals("Godefroy"), "le souverain choisi est perdu");
            w.check(valcourt.faith() == AiFaith.PAIEN, "la foi choisie est perdue");
            w.check(valcourt.capital().seat().getX() == x && valcourt.capital().seat().getZ() == z, "la capitale n'est pas à l'endroit cliqué");
            w.check(!WorldEditService.createRealm(w.ruler, "Ormesval", "", TitleRank.BARONNIE, 1, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x + 5, z).success(), "deux capitales collées l'une à l'autre");
            w.check(!WorldEditService.createRealm(w.ruler, "A", "", TitleRank.BARONNIE, 1, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x + 900, z).success(), "un nom d'une lettre accepté");

            // Une ville pour le royaume IA, une autre pour le joueur (rattachée au voisin, tenue par le joueur).
            w.check(WorldEditService.createCity(w.ruler, valcourt.id(), "Ormesval", WorldEditService.CitySize.BOURG, x + 200, z).success(),
                    "ville IA refusée");
            var mine = WorldEditService.createCity(w.ruler, w.realm.id(), "Hautemarche", WorldEditService.CitySize.VILLE, x - 200, z);
            w.check(mine.success(), "ville du joueur refusée : " + mine.message().getString());
            AiProvince ormesval = valcourt.provinces().stream().filter(p -> p.name().equals("Ormesval")).findFirst().orElseThrow();
            AiProvince hautemarche = valcourt.provinces().stream().filter(p -> p.name().equals("Hautemarche")).findFirst().orElseThrow();
            w.check(ormesval.holder() == null && !ormesval.capital(), "la ville IA est mal rangée");
            w.check(w.realm.id().equals(hautemarche.holder()), "la ville du joueur n'est pas à lui");
            w.check(WarService.provincesOf(server, w.realm.id()).contains(hautemarche), "le joueur ne compte pas sa ville");
            w.check(hautemarche.population() == WorldEditService.CitySize.VILLE.population(), "la taille choisie est perdue");

            // Retouches : donner, reprendre, renommer, déplacer, supprimer.
            w.check(WorldEditService.giveCity(w.ruler, ormesval.id(), w.realm.id()).success(), "don refusé");
            w.check(w.realm.id().equals(ormesval.holder()), "la ville donnée n'a pas changé de mains");
            w.check(WorldEditService.giveCity(w.ruler, ormesval.id(), valcourt.id()).success() && ormesval.holder() == null,
                    "la ville ne revient pas à son royaume");
            w.check(WorldEditService.renameCity(w.ruler, ormesval.id(), "Saulepré").success() && ormesval.name().equals("Saulepré"),
                    "renommage perdu");
            w.check(WorldEditService.moveCity(w.ruler, ormesval.id(), x + 260, z + 40).success()
                    && ormesval.seat().getX() == x + 260 && ormesval.seat().getZ() == z + 40, "déplacement perdu");
            w.check(!WorldEditService.deleteCity(w.ruler, valcourt.capital().id()).success(), "une capitale supprimée seule");
            w.check(WorldEditService.deleteCity(w.ruler, ormesval.id()).success() && !valcourt.provinces().contains(ormesval),
                    "la ville n'est pas supprimée");

            // Le royaume se renomme, et sa capitale avec lui.
            w.check(WorldEditService.editRealm(w.ruler, valcourt.id(), "Valbois", TitleRank.DUCHE, 2, AiFaith.FIDELE,
                    AiPersonality.EXPANSIONNISTE).success(), "modification refusée");
            w.check(valcourt.place().equals("Valbois") && valcourt.capital().name().equals("Valbois"), "le nom n'a pas suivi");
            w.check(valcourt.rank() == TitleRank.DUCHE && valcourt.faith() == AiFaith.FIDELE, "rang ou foi perdus");
            w.check(!WorldEditService.editRealm(w.ruler, w.realm.id(), "Valbois", TitleRank.DUCHE, 2, AiFaith.FIDELE,
                    AiPersonality.EXPANSIONNISTE).success(), "un royaume de joueur modifié par l'éditeur");

            // Un joueur qui n'est ni l'hôte ni opérateur : tout lui est refusé (réglage par défaut).
            if (CrusadeConfig.SERVER.mapEditor.get() == WorldEditService.Access.HOTE_ET_OPERATEURS) {
                var stranger = TestWorld.fakePlayer(h, "etranger");
                w.check(!WorldEditService.createRealm(stranger, "Ambrevent", "", TitleRank.COMTE, 3, AiFaith.FIDELE,
                        AiPersonality.PACIFIQUE, x + 1500, z).success(), "un étranger modifie la carte");
                w.check(!WorldEditService.deleteRealm(stranger, valcourt.id()).success(), "un étranger efface un royaume");
                MapView view = WorldService.mapView(stranger, true);
                w.check(!view.canEdit() && !view.editing(), "la carte d'édition est servie sans droit");
            }
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p12_peindre", timeoutTicks = 200)
    public static void peindreLesFrontieres(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = setUp(w);
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            ResourceLocation dim = h.getLevel().dimension().location();
            BlockPos home = MineColoniesBridge.center(w.colony).orElse(w.center);
            AiRealm valcourt = WorldEditService.addRealm(server, h.getLevel(), "Valcourt", "", TitleRank.COMTE, 0xB3261E, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, home.getX() + 2000, home.getZ());
            w.check(WarService.inTerritory(server, w.realm.id(), dim, home), "la colonie n'est pas en territoire du joueur");
            w.check(!WarService.inTerritory(server, valcourt.id(), dim, home), "Valcourt déborde jusqu'à la colonie");

            long cell = TerritoryMap.key(TerritoryMap.cellOf(home.getX()), TerritoryMap.cellOf(home.getZ()));
            var painted = WorldEditService.paint(w.ruler, valcourt.id(), false, new long[]{cell});
            w.check(painted.success(), "peinture refusée : " + painted.message().getString());
            w.check(WarService.inTerritory(server, valcourt.id(), dim, home), "la case peinte n'est pas à Valcourt");
            w.check(!WarService.inTerritory(server, w.realm.id(), dim, home), "la case peinte compte encore pour le joueur");
            MapView view = WorldService.mapView(w.ruler, true);
            w.check(view.editing() && view.borders().size() == 1, "la carte d'édition n'envoie pas la case peinte");
            int owner = view.borders().owners()[0];
            w.check(owner >= 0 && view.territories().get(owner).id().equals(valcourt.id()), "la case peinte a perdu son royaume");

            var neutral = WorldEditService.paint(w.ruler, null, true, new long[]{cell});
            w.check(neutral.success(), "terre libre refusée : " + neutral.message().getString());
            w.check(!WarService.inTerritory(server, valcourt.id(), dim, home) && !WarService.inTerritory(server, w.realm.id(), dim, home),
                    "une terre libre appartient encore à quelqu'un");
            w.check(WorldEditService.paint(w.ruler, null, false, new long[]{cell}).success(), "gomme refusée");
            w.check(WarService.inTerritory(server, w.realm.id(), dim, home), "la gomme ne rend pas le territoire d'origine");
            w.check(!WorldEditService.paint(w.ruler, UUID.randomUUID(), false, new long[]{cell}).success(), "un royaume inconnu peint");
            w.check(!WorldEditService.paint(w.ruler, valcourt.id(), false, new long[WorldEditService.MAX_CELLS + 1]).success(),
                    "un coup de pinceau démesuré accepté");
            w.check(data.world().borders().size() == 0, "des cases restent après la gomme");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p12_effacer", timeoutTicks = 200)
    public static void effacerUnRoyaumeProprement(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = setUp(w);
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            var level = h.getLevel();
            int x = w.center.getX(), z = w.center.getZ();
            long today = RealmService.currentDay(server);
            AiRealm a = WorldEditService.addRealm(server, level, "Valcourt", "", TitleRank.COMTE, 0xB3261E, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x + 1000, z);
            AiRealm b = WorldEditService.addRealm(server, level, "Ormesval", "", TitleRank.COMTE, 0x1F4E9C, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x + 1600, z);
            AiRealm generated = WorldEditService.addRealm(server, level, "Cendrecourt", "", TitleRank.BARONNIE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, x - 1500, z);
            generated.setHandMade(false);
            // Une ville de Valcourt tenue par Ormesval, une ville d'Ormesval tenue par Valcourt.
            AiProvince lost = WorldEditService.addCity(server, level, a.id(), "Brisemer", WorldEditService.CitySize.BOURG, x + 1300, z).orElseThrow();
            lost.setHolder(b.id());
            AiProvince taken = WorldEditService.addCity(server, level, b.id(), "Givrefond", WorldEditService.CitySize.BOURG, x + 1800, z).orElseThrow();
            taken.setHolder(a.id());
            // Une guerre, un traité, une lettre et une coalition qui désignent Valcourt.
            War war = WarService.declareBetween(server, w.realm.id(), a.id(), CasusBelli.HUMILIATE, -1);
            data.diplomacy().between(w.realm.id(), b.id()).sign(Treaty.COMMERCE, today);
            data.diplomacy().between(w.realm.id(), a.id()).sign(Treaty.PASSAGE, today);
            w.realm.mail().add(new Letter(UUID.randomUUID(), a.id(), true, w.realm.id(), Component.literal("Valcourt"),
                    Component.literal("Sujet"), Component.literal("Corps"), List.of(), today, today + 5, 0));
            var coalition = new fr.crusadecraft.core.ai.Coalition(w.realm.id(), today);
            coalition.members().add(a.id());
            data.world().coalitions().add(coalition);

            var out = WorldEditService.deleteRealm(w.ruler, a.id());
            w.check(out.success(), "suppression refusée : " + out.message().getString());
            w.check(data.world().realm(a.id()).isEmpty(), "le royaume est toujours là");
            w.check(data.wars().get(war.id()).isEmpty(), "sa guerre continue");
            w.check(data.diplomacy().find(w.realm.id(), a.id()).isEmpty(), "ses relations restent");
            w.check(data.diplomacy().find(w.realm.id(), b.id()).isPresent(), "les relations des autres ont disparu");
            w.check(w.realm.mail().stream().noneMatch(l -> l.from().equals(a.id())), "sa lettre attend encore une réponse");
            w.check(data.world().coalitions().isEmpty(), "une coalition sans membre reste");
            w.check(taken.holder() == null, "la ville qu'il avait prise ne revient pas à Ormesval");
            boolean kept = b.provinces().stream().anyMatch(p -> p.id() == lost.id() && p.holder() == null && !p.capital());
            w.check(kept, "la ville qu'Ormesval lui avait prise est perdue");

            // Monde vierge : le royaume généré part, celui fait main reste, et rien ne se regénère.
            w.check(WorldEditService.wipe(w.ruler).success(), "monde vierge refusé");
            w.check(data.world().realm(generated.id()).isEmpty(), "le royaume généré reste");
            w.check(data.world().realm(b.id()).isPresent(), "le monde vierge emporte un royaume fait main");
            w.check(data.world().generated(), "le monde vierge sera regénéré");
            w.check(DiplomacyService.name(server, b.id()).getString().contains("Ormesval"), "Ormesval a perdu son nom");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }
}
