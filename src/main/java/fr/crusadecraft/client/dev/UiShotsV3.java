package fr.crusadecraft.client.dev;

import fr.crusadecraft.client.screen.CitiesScreen;
import fr.crusadecraft.client.screen.HouseScreen;
import fr.crusadecraft.client.screen.LineageScreen;
import fr.crusadecraft.client.screen.MapScreen;
import fr.crusadecraft.client.screen.PersonForm;
import fr.crusadecraft.client.screen.SettingsScreen;
import fr.crusadecraft.client.screen.TreeScreen;
import fr.crusadecraft.client.screen.WarScreen;
import fr.crusadecraft.config.SettingsService;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.court.HouseView;
import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.CityView;
import fr.crusadecraft.core.war.ArmyService;
import fr.crusadecraft.core.war.Formation;
import fr.crusadecraft.core.war.WarView;
import fr.crusadecraft.core.world.MapView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Les écrans des phases 19 à 30 pour le banc d'essai visuel : villes, fiche d'une personne, arbre illustré, banquet
 * et deuil, armée en chair et en os, pions et caravanes sur la carte, réglages. Rangés à part de {@link UiShots},
 * qui les ajoute à sa liste.
 */
final class UiShotsV3 {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    private UiShotsV3() {}

    record Scene(String name, Supplier<net.minecraft.client.gui.screens.Screen> screen) {}

    static List<Scene> scenes(MapView baseMap, WarView baseWar, HouseView baseHouse) {
        List<Scene> out = new ArrayList<>();
        out.add(new Scene("v3_villes", () -> new CitiesScreen(sampleCities())));
        LineageView tree = sampleTree();
        out.add(new Scene("v3_registre_citoyens", () -> new LineageScreen(tree)));
        out.add(new Scene("v3_fiche_personne", () -> {
            LineageScreen parent = new LineageScreen(tree);
            LineageView.Row r = tree.selection().rows().get(2);
            return new PersonForm(parent, r, "Godefroy");
        }));
        out.add(new Scene("v3_arbre", () -> new TreeScreen(new LineageScreen(tree), tree)));
        out.add(new Scene("v3_arbre_choisi", () -> {
            TreeScreen s = new TreeScreen(new LineageScreen(tree), tree);
            s.previewPerson(tree.selection().rows().get(5).id());
            return s;
        }));
        out.add(new Scene("v3_maison_banquet", () -> new HouseScreen(withCourt(baseHouse, "", false, ""))));
        out.add(new Scene("v3_maison_fete", () -> new HouseScreen(withCourt(baseHouse, "crusadecraft.feast.already", true, ""))));
        out.add(new Scene("v3_maison_deuil", () -> new HouseScreen(withCourt(baseHouse, "crusadecraft.feast.mourning", false, "Hugues d'Aubépine"))));
        WarView army = withArmy(baseWar);
        out.add(new Scene("v3_guerre_armee", () -> {
            WarScreen s = new WarScreen(army);
            s.select(army.wars().get(0).id());
            return s;
        }));
        MapView pawns = withPawns(baseMap);
        out.add(new Scene("v3_carte_armees_caravanes", () -> new MapScreen(pawns)));
        out.add(new Scene("v3_carte_guerre_marcher", () -> {
            MapScreen s = new MapScreen(withWar(pawns));
            s.previewWar(7);
            return s;
        }));
        SettingsService.View settings = sampleSettings();
        out.add(new Scene("v3_reglages", () -> new SettingsScreen(settings)));
        out.add(new Scene("v3_reglages_refuses", () -> new SettingsScreen(new SettingsService.View(false, List.of()))));
        return out;
    }

    static CityView sampleCities() {
        UUID aymeric = UUID.randomUUID();
        List<CityView.Row> rows = List.of(
                new CityView.Row(new ColonyRef(OW, 1), "Valbrume", true, 34, null, "", 100, 100, false),
                new CityView.Row(new ColonyRef(OW, 2), "Clairval", false, 18, aymeric, "Aymeric d'Aubépine", 40, 25, false),
                new CityView.Row(new ColonyRef(OW, 3), "Hautemarche", false, 22, null, "", 100, 100, false),
                new CityView.Row(new ColonyRef(OW, 4), "Saulepré", false, 9, null, "", 100, 100, true));
        List<CityView.Candidate> candidates = List.of(new CityView.Candidate(aymeric, "Aymeric d'Aubépine"),
                new CityView.Candidate(UUID.randomUUID(), "Aude d'Aubépine"), new CityView.Candidate(UUID.randomUUID(), "Thibaut d'Aubépine"));
        return new CityView("Royaume de Valbrume", 3, 3, 30, rows, candidates, "", 50, 50);
    }

    /** La maison Aubépine sur trois générations, liens de parenté compris, et les citoyens de la maison du souverain. */
    static LineageView sampleTree() {
        UUID hugues = UUID.randomUUID(), blanche = UUID.randomUUID(), godefroy = UUID.randomUUID(), mathilde = UUID.randomUUID(),
                alienor = UUID.randomUUID(), raoul = UUID.randomUUID(), ysolde = UUID.randomUUID(), aude = UUID.randomUUID(),
                thibaut = UUID.randomUUID(), jehan = UUID.randomUUID();
        int bold = (1 << Trait.BRAVE.ordinal()) | (1 << Trait.JUSTE.ordinal());
        List<LineageView.Row> rows = List.of(
                new LineageView.Row(hugues, "Hugues d'Aubépine", false, 81, false, Role.FAMILLE, 0, "Blanche", false, true, 0, "", null, null, blanche),
                new LineageView.Row(blanche, "Blanche de Valcourt", true, 77, false, Role.FAMILLE, 0, "Hugues", false, false, 0, "", null, null, hugues),
                new LineageView.Row(godefroy, "Godefroy d'Aubépine", false, 54, true, Role.SOUVERAIN, 1, "Mathilde", true, true, bold,
                        "Né pendant le grand hiver, il a repris la couronne à vingt ans.", hugues, blanche, mathilde),
                new LineageView.Row(mathilde, "Mathilde de Rochegarde", true, 51, true, Role.CONSORT, 1, "Godefroy", true, false, 0, "", null, null, godefroy),
                new LineageView.Row(alienor, "Aliénor d'Aubépine", true, 49, true, Role.FAMILLE, 1, "", false, true, 0, "", hugues, blanche, null),
                new LineageView.Row(raoul, "Raoul d'Aubépine", false, 29, true, Role.HERITIER, 2, "Ysolde", true, true, 0, "", godefroy, mathilde, ysolde),
                new LineageView.Row(ysolde, "Ysolde de Brisemer", true, 27, true, Role.FAMILLE, 2, "Raoul", false, false, 0, "", null, null, raoul),
                new LineageView.Row(aude, "Aude d'Aubépine", true, 24, true, Role.CHEVALIER, 2, "", false, true, 0, "", godefroy, mathilde, null),
                new LineageView.Row(thibaut, "Thibaut d'Aubépine", false, 19, true, Role.FAMILLE, 2, "", false, true, 0, "", godefroy, mathilde, null),
                new LineageView.Row(jehan, "Jehan d'Aubépine", false, 3, true, Role.FAMILLE, 3, "", false, true, 0, "", raoul, ysolde, null));
        UUID selected = UUID.randomUUID();
        var houses = List.of(new LineageView.HouseLine(selected, "Aubépine", Heraldry.SINOPLE.rgb(), Component.literal("Royaume de Valbrume"), 8, 10));
        var communities = List.of(new LineageView.CommunityLine(UUID.randomUUID(), "Saint-Aubin", CommunityKind.VILLAGE,
                Component.literal("Royaume de Valbrume"), 8));
        var realms = List.of(new LineageView.RealmChoice(UUID.randomUUID(), Component.literal("Royaume de Valbrume")));
        var selection = new LineageView.Selection(true, selected, "Aubépine", Component.literal("Royaume de Valbrume"), null, rows,
                List.of("Aldric", "Berthe", "Hugues le Jeune"));
        return new LineageView(true, true, houses, communities, realms, selection);
    }

    static HouseView withCourt(HouseView h, String problem, boolean feasting, String mourning) {
        return new HouseView(h.size(), h.max(), h.prestige(), h.adoptCost(), h.members(), h.candidates(), h.matches(),
                new HouseView.Court(120, problem, 0, feasting, mourning, mourning.isEmpty() ? 0 : 2, UUID.randomUUID()));
    }

    static WarView withArmy(WarView w) {
        return new WarView(w.men(), w.maxMen(), true, w.upkeep(), w.gold(), w.prestige(), w.sealOk(), w.sealProblem(), w.wars(), w.targets(),
                w.threat(), w.threatThreshold(), w.coalition(), new WarView.Army(9, ArmyService.Order.MARCH, Formation.LIGNE));
    }

    static MapView withPawns(MapView m) {
        List<MapView.TradeLine> routes = List.of(
                new MapView.TradeLine(0, 0, 900, -700, 7, Component.literal("Royaume de Valbrume"), Component.literal("Duché de Rochegarde"),
                        Component.literal("Blé ⇄ Fer"), true, 360, -280, false),
                new MapView.TradeLine(0, 0, 700, 800, 0, Component.literal("Royaume de Valbrume"), Component.literal("Comté de Cendrecourt"),
                        Component.literal("Blé ⇄ Vin"), true, 280, 320, true));
        List<MapView.ArmyPawn> armies = List.of(
                new MapView.ArmyPawn(-180, -60, 96, 9, Heraldry.AZUR.rgb(), Component.literal("Royaume de Valbrume"), true, false, true, -600, -200),
                new MapView.ArmyPawn(-820, -280, 140, 0, Heraldry.GUEULES.rgb(), Component.literal("Royaume d'Ambrevent"), false, false, true, 0, 0),
                new MapView.ArmyPawn(820, 760, 35, 0, Heraldry.POURPRE.rgb(), Component.literal("Comté de Cendrecourt"), false, true, false, 0, 0));
        return new MapView(m.dimension(), m.focusX(), m.focusZ(), m.playerX(), m.playerZ(), m.territoryRadius(), m.colonyRadius(), m.territories(),
                m.seats(), m.dejure(), m.canEdit(), m.editing(), m.borders(), m.places(), m.war(), routes, armies);
    }

    static MapView withWar(MapView m) {
        return new MapView(m.dimension(), m.focusX(), m.focusZ(), m.playerX(), m.playerZ(), m.territoryRadius(), m.colonyRadius(), m.territories(),
                m.seats(), m.dejure(), m.canEdit(), m.editing(), m.borders(), m.places(), new MapView.War(true, List.of(), List.of()), m.routes(), m.armies());
    }

    static SettingsService.View sampleSettings() {
        List<SettingsService.Entry> e = new ArrayList<>();
        e.add(new SettingsService.Entry("guerre.sieges", SettingsService.Kind.BOOL, "true", "", "", List.of(), "Les sièges se jouent dans le monde."));
        e.add(new SettingsService.Entry("guerre.hommesParSoldat", SettingsService.Kind.INT, "10", "1", "1000", List.of(),
                "Levées en chair et en os (P23) : combien d'hommes de l'armée fait un soldat rassemblé au camp."));
        e.add(new SettingsService.Entry("guerre.soldatsMax", SettingsService.Kind.INT, "24", "1", "200", List.of(), "Soldats au plus rassemblés."));
        e.add(new SettingsService.Entry("guerre.ostesIA", SettingsService.Kind.BOOL, "true", "", "", List.of(), "Ostes de l'ordinateur (P27)."));
        e.add(new SettingsService.Entry("guerre.vitesseOstes", SettingsService.Kind.INT, "6", "1", "200", List.of(), "Blocs toutes les cinq secondes."));
        e.add(new SettingsService.Entry("carteWeb.langue", SettingsService.Kind.TEXT, "fr_fr", "", "", List.of(), "La langue des fiches de la carte web."));
        e.add(new SettingsService.Entry("carteWeb.opacite", SettingsService.Kind.DOUBLE, "0.35", "0.0", "1.0", List.of(), "Opacité des terres."));
        e.add(new SettingsService.Entry("territoire.messagesEntree", SettingsService.Kind.ENUM, "TITRE", "", "", List.of("AUCUN", "TITRE", "CHAT"),
                "Comment on annonce l'entrée sur des terres."));
        e.add(new SettingsService.Entry("villes.limiteDomaine", SettingsService.Kind.LIST, "2, 3, 4, 6, 8", "", "", List.of(), "Villes en direct par rang."));
        e.add(new SettingsService.Entry("civilisations.actif", SettingsService.Kind.BOOL, "false", "", "", List.of(),
                "Laisser les royaumes de l'ordinateur bâtir d'eux-mêmes (coupé par défaut)."));
        e.add(new SettingsService.Entry("commerce.caravanes", SettingsService.Kind.BOOL, "true", "", "", List.of(), "Une caravane sur chaque route."));
        e.add(new SettingsService.Entry("pnj.joursDeDeuil", SettingsService.Kind.INT, "3", "0", "60", List.of(), "Jours de deuil."));
        return new SettingsService.View(true, e);
    }

}
