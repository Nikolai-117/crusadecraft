package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.place.LieuKind;
import fr.crusadecraft.core.place.LieuView;
import fr.crusadecraft.core.place.PlaceService;
import fr.crusadecraft.core.place.PlaceState;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;


/** Les lieux (P15) : ils suivent le monde, gardent leurs fiches, se rangent sans boucle, et chacun n'y fait que son droit. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlaceGameTests {

    private PlaceGameTests() {}

    private static void reset(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        data.places().clear();
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
    }

    private static void cleanup(TestWorld w) {
        reset(w);
        CrusadeSavedData.get(w.helper.getLevel().getServer()).places().clear();
        WorldEditService.grantForTests(w.ruler.getUUID(), false);
        w.cleanup();
    }

    private static BlockPos ground(GameTestHelper h, int x, int z) {
        return h.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p15_lieux", timeoutTicks = 200)
    public static void lesLieuxSuiventLeMonde(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            PlaceState places = data.places();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 900, w.center.getZ());
            AiProvince town = WorldEditService.addCity(server, h.getLevel(), ai.id(), "Hautemarche", WorldEditService.CitySize.BOURG,
                    w.center.getX() + 1000, w.center.getZ() + 60).orElseThrow();
            PlaceService.sync(server);

            // Chaque chose du monde a son lieu, rangé sous ce qui la tient.
            Lieu realmLieu = places.byRef(Lieu.Ref.aiRealm(ai.id())).orElse(null);
            Lieu townLieu = places.byRef(Lieu.Ref.aiProvince(town.id())).orElse(null);
            Lieu mine = places.byRef(Lieu.Ref.playerRealm(w.realm.id())).orElse(null);
            Lieu colony = places.byRef(Lieu.Ref.colony(w.colony.dimension(), w.colony.colonyId())).orElse(null);
            w.check(realmLieu != null && realmLieu.kind() == LieuKind.DUCHE, "le duché n'a pas son lieu");
            w.check(townLieu != null && townLieu.kind() == LieuKind.VILLE, "la ville n'a pas son lieu");
            w.check(mine != null && colony != null, "le royaume du joueur ou sa colonie n'ont pas de lieu");
            PlaceService.Resolver res = new PlaceService.Resolver(server);
            w.check(realmLieu.id().equals(res.parentOf(townLieu.id())), "la ville ne relève pas de son duché");
            w.check(mine.id().equals(res.parentOf(colony.id())), "la colonie ne relève pas du royaume du joueur");
            int count = places.size();
            PlaceService.sync(server);
            w.check(places.size() == count, "les lieux sont créés deux fois");

            // Une construction a son lieu ; un jalon n'en a pas.
            BlockPos fortGround = ground(h, w.center.getX() + 1100, w.center.getZ() - 200);
            w.check(SiteService.place(w.ruler, SiteKind.FORT, ai.id(), fortGround, Direction.SOUTH, true).success(), "fort refusé");
            w.check(SiteService.place(w.ruler, SiteKind.BORNE, ai.id(), ground(h, w.center.getX() + 1300, w.center.getZ() + 300), Direction.SOUTH, true)
                    .success(), "jalon refusé");
            PlaceService.sync(server);
            Site fort = data.world().sites().stream().filter(s -> s.kind() == SiteKind.FORT).findFirst().orElseThrow();
            Site jalon = data.world().sites().stream().filter(s -> s.kind() == SiteKind.BORNE).findFirst().orElseThrow();
            w.check(places.byRef(Lieu.Ref.site(fort.id())).map(l -> l.kind() == LieuKind.FORT).orElse(false), "le fort n'a pas son lieu");
            w.check(places.byRef(Lieu.Ref.site(jalon.id())).isEmpty(), "un jalon a un lieu");

            // Le nom suit la ville renommée.
            w.check(WorldEditService.renameCity(w.ruler, town.id(), "Hautemarche-la-Neuve").success(), "renommage refusé");
            PlaceService.sync(server);
            w.check(townLieu.name().equals("Hautemarche-la-Neuve"), "le lieu garde l'ancien nom de la ville");

            // Où l'on est : près de la ville, sur les terres du duché.
            PlaceService.Chain chain = PlaceService.chain(server, h.getLevel().dimension().location(), town.seat().getX() + 4, town.seat().getZ());
            w.check(chain.near() != null && chain.near().id().equals(townLieu.id()), "« près de » ne donne pas la ville");
            w.check(!chain.realms().isEmpty() && chain.realms().get(0).id().equals(realmLieu.id()), "la terre n'est pas celle du duché");

            // Une ville effacée : sa fiche reste ; sans fiche, son lieu s'en va.
            w.check(PlaceService.setText(w.ruler, townLieu.id(), "Une ville de marchands.").outcome().success(), "fiche refusée");
            w.check(WorldEditService.deleteCity(w.ruler, town.id()).success(), "suppression de la ville refusée");
            w.check(townLieu.detached() && places.get(townLieu.id()).isPresent(), "la fiche d'une ville effacée est perdue");
            w.check(places.byRef(Lieu.Ref.aiProvince(town.id())).isEmpty(), "le lieu effacé répond encore à l'ancienne ville");
            // Le numéro de la ville resservira : la nouvelle ville n'hérite pas de la fiche de l'ancienne.
            AiProvince next = WorldEditService.addCity(server, h.getLevel(), ai.id(), "Neuville", WorldEditService.CitySize.BOURG,
                    w.center.getX() + 1000, w.center.getZ() + 120).orElseThrow();
            PlaceService.sync(server);
            Lieu nextLieu = places.byRef(Lieu.Ref.aiProvince(next.id())).orElseThrow();
            w.check(!nextLieu.id().equals(townLieu.id()) && nextLieu.text().isEmpty(), "la nouvelle ville a pris la fiche de l'ancienne");
            // Un royaume effacé sans fiche : plus de lieu.
            WorldEditService.removeRealm(server, ai.id());
            PlaceService.sync(server);
            w.check(places.byRef(Lieu.Ref.aiRealm(ai.id())).isEmpty(), "le lieu d'un royaume effacé reste");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p15_fiches", timeoutTicks = 200)
    public static void poserUnLieuEtEcrireSaFiche(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            PlaceState places = data.places();
            var dim = h.getLevel().dimension().location();
            // Les terres du joueur autour de lui, peintes pour ne dépendre d'aucun rayon.
            int cx = TerritoryMap.cellOf(w.center.getX()), cz = TerritoryMap.cellOf(w.center.getZ());
            long[] cells = new long[9];
            int k = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) cells[k++] = TerritoryMap.key(cx + dx, cz + dz);
            data.world().borders().paint(dim, cells, w.realm.id());

            // En survie : un lieu saint sur ses terres, oui ; un titre, ou hors de ses terres, non.
            BlockPos here = w.center.north(3);
            PlaceService.Result shrine = PlaceService.create(w.ruler, LieuKind.LIEU_SAINT, "Abbaye de Saint-Aubin", null, here, 0);
            w.check(shrine.outcome().success(), "lieu saint refusé : " + shrine.outcome().message().getString());
            Lieu abbey = places.get(shrine.select()).orElseThrow();
            w.check(w.realm.id().equals(abbey.holder()) && here.equals(abbey.anchor()), "le lieu saint n'est ni à lui ni à sa place");
            w.check(!PlaceService.create(w.ruler, LieuKind.DUCHE, "Normandie", null, here, 0).outcome().success(), "un joueur pose un titre");
            w.check(!PlaceService.create(w.ruler, LieuKind.RUINE, "Ruines", null, w.center.offset(2000, 0, 0), 0).outcome().success(),
                    "un lieu hors de ses terres");
            // Sa fiche, oui ; la ranger ailleurs, non (c'est l'affaire de l'éditeur).
            w.check(PlaceService.setText(w.ruler, abbey.id(), "Fondée par les moines gris.").outcome().success(), "fiche refusée");
            w.check(abbey.text().equals("Fondée par les moines gris."), "fiche non enregistrée");
            w.check(!PlaceService.setParent(w.ruler, abbey.id(), null).outcome().success(), "un lecteur range un lieu");
            w.check(PlaceService.rename(w.ruler, abbey.id(), "Abbaye de Saint-Aubin-des-Bois").outcome().success(), "renommage refusé");

            // Un royaume IA pas encore découvert n'apparaît pas dans la vue du joueur.
            AiRealm ai = WorldEditService.addRealm(server, h.getLevel(), "Ambrevent", "Aude", TitleRank.ROYAUME, 0x8E24AA, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1500, w.center.getZ());
            PlaceService.sync(server);
            Lieu aiLieu = places.byRef(Lieu.Ref.aiRealm(ai.id())).orElseThrow();
            LieuView view = PlaceService.view(w.ruler, aiLieu.id(), null, true);
            w.check(view.entries().stream().noneMatch(e -> e.id().equals(aiLieu.id())) && view.selection() == null, "un royaume inconnu est montré");
            w.check(view.entries().stream().anyMatch(e -> e.id().equals(abbey.id())), "le lieu saint manque à la vue");

            // L'éditeur : un lieu posé sur les terres d'un royaume lui revient, et se range sous lui.
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            BlockPos aiLand = ai.capital().seat().east(6);
            PlaceService.Result ruin = PlaceService.create(w.ruler, LieuKind.RUINE, "Tour effondrée", null, aiLand, 0);
            w.check(ruin.outcome().success(), "ruine refusée à l'éditeur");
            Lieu tower = places.get(ruin.select()).orElseThrow();
            w.check(ai.id().equals(tower.holder()), "la ruine n'est pas au royaume qui tient la terre");
            w.check(aiLieu.id().equals(new PlaceService.Resolver(server).parentOf(tower.id())), "la ruine ne relève pas de ce royaume");
            // Un titre qui regroupe, rangé sans boucle.
            PlaceService.Result empire = PlaceService.create(w.ruler, LieuKind.EMPIRE, "Lothaire", null, here, 0);
            w.check(empire.outcome().success(), "titre refusé à l'éditeur");
            Lieu lothaire = places.get(empire.select()).orElseThrow();
            w.check(lothaire.anchor() == null && lothaire.composed(), "un titre a une position, ou son nom n'est pas à composer");
            w.check(PlaceService.setParent(w.ruler, aiLieu.id(), lothaire.id()).outcome().success(), "rangement refusé");
            w.check(lothaire.id().equals(new PlaceService.Resolver(server).parentOf(aiLieu.id())), "le royaume ne relève pas de l'empire");
            w.check(!PlaceService.setParent(w.ruler, lothaire.id(), aiLieu.id()).outcome().success(), "une boucle est acceptée");
            w.check(PlaceService.setHolder(w.ruler, lothaire.id(), ai.id()).outcome().success(), "tenancier refusé");
            w.check(!PlaceService.delete(w.ruler, aiLieu.id()).outcome().success(), "le lieu d'un royaume se supprime à la main");
            // Supprimer l'empire : le royaume retombe sur ce que le monde lui donne (aucun suzerain ici).
            w.check(PlaceService.delete(w.ruler, lothaire.id()).outcome().success(), "suppression refusée");
            w.check(aiLieu.parent() == null && new PlaceService.Resolver(server).parentOf(aiLieu.id()) == null, "le royaume relève encore d'un lieu disparu");
            // La position : près de l'abbaye, sur les terres du joueur.
            PlaceService.Chain chain = PlaceService.chain(server, dim, here.getX(), here.getZ());
            w.check(chain.near() != null && chain.near().id().equals(abbey.id()), "« près de » ne donne pas l'abbaye");
            w.check(!chain.realms().isEmpty() && chain.realms().get(0).ref() != null
                    && w.realm.id().equals(chain.realms().get(0).ref().uuid()), "la terre n'est pas celle du joueur");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }
}
