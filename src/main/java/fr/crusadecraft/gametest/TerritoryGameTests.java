package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.mca.McaBridge;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.TerritoryRules;
import fr.crusadecraft.core.world.TerritoryService;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.item.BornageStaffItem;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Le bornage et les constructions (P14), dans un vrai monde de test. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerritoryGameTests {

    private TerritoryGameTests() {}

    private static void reset(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
    }

    private static void cleanup(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (Person p : new ArrayList<>(data.lineage().persons())) {
            Entity e = w.helper.getLevel().getEntity(p.id());
            if (e != null) e.discard();
        }
        data.lineage().clear();
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        data.world().clear();
        WorldEditService.grantForTests(w.ruler.getUUID(), false);
        w.ruler.getInventory().clearContent();
        w.cleanup();
    }

    /** Un sol loin des autres tests, chargé, pour bâtir sans rien abîmer. */
    private static BlockPos ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p14_bornage", timeoutTicks = 200)
    public static void bornageEnSurvie(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            var dim = h.getLevel().dimension().location();
            BlockPos home = MineColoniesBridge.center(w.colony).orElse(w.center);
            // Un royaume voisin, dont le cercle commence à 170 blocs à l'est de la colonie.
            AiRealm neighbour = WorldEditService.addRealm(server, h.getLevel(), "Valcourt", "", TitleRank.COMTE, 0xB3261E, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, home.getX() + 330, home.getZ());
            w.realm.setGold(1000);
            long[] row = TerritoryRules.rectangle(home.getX(), home.getZ(), home.getX() + 200, home.getZ());
            var out = TerritoryService.apply(w.ruler, w.realm.id(), "", row, false);
            w.check(out.success(), "bornage refusé : " + out.message().getString());
            long cost = 1000 - w.realm.gold();
            w.check(cost > 0, "le bornage n'a rien coûté");
            BlockPos claimed = new BlockPos(home.getX() + 150, home.getY(), home.getZ());
            w.check(Territory.ownerAt(server, dim, claimed.getX(), claimed.getZ()).map(w.realm.id()::equals).orElse(false), "la case prise n'est pas à moi");
            w.check(Territory.ownerAt(server, dim, home.getX() + 195, home.getZ()).map(neighbour.id()::equals).orElse(false),
                    "une case du voisin a été prise sans guerre");
            // Pour un autre royaume : refusé en survie.
            w.check(!TerritoryService.apply(w.ruler, neighbour.id(), "", row, false).success(), "un joueur trace pour un royaume IA");
            // Annuler rend l'or et la terre.
            w.check(TerritoryService.undo(w.ruler).success(), "annulation refusée");
            w.check(w.realm.gold() == 1000, "l'or n'est pas rendu : " + w.realm.gold());
            w.check(data.world().borders().size() == 0, "des cases restent après l'annulation");
            // Sans or, rien.
            w.realm.setGold(0);
            w.check(!TerritoryService.apply(w.ruler, w.realm.id(), "", row, false).success(), "un bornage sans or");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p14_baton", timeoutTicks = 200)
    public static void leBatonEnMain(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var dim = h.getLevel().dimension().location();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, h.getLevel(), "Ormesval", "", TitleRank.BARONNIE, 0x1F4E9C, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 2000, w.center.getZ());
            ItemStack staff = new ItemStack(ModItems.BORNAGE.get());
            w.ruler.setItemInHand(InteractionHand.MAIN_HAND, staff);
            BornageStaffItem.setOwner(staff, ai.id(), ai.color(), "Ormesval");
            BornageStaffItem.setMode(staff, BornageStaffItem.Mode.RECTANGLE);
            BlockPos a = w.center.offset(1000, 0, 1000), b = a.offset(40, 0, 20);
            TerritoryService.onStaffUse(w.ruler, staff, a, Direction.UP, false);
            w.check(BornageStaffItem.points(staff).size() == 1, "le premier coin n'est pas posé");
            TerritoryService.onStaffUse(w.ruler, staff, b, Direction.UP, false);
            w.check(BornageStaffItem.points(staff).isEmpty(), "le rectangle ne s'est pas validé au second coin");
            w.check(Territory.ownerAt(server, dim, a.getX() + 20, a.getZ() + 10).map(ai.id()::equals).orElse(false), "le rectangle n'est pas à Ormesval");
            // Accroupi : la case redevient terre libre.
            TerritoryService.onStaffUse(w.ruler, staff, a, Direction.UP, true);
            w.check(Territory.ownerAt(server, dim, a.getX(), a.getZ()).isEmpty(), "accroupi, la case n'est pas libérée");
            // Polygone : trois coins, puis un clic près du premier le ferme.
            BornageStaffItem.setMode(staff, BornageStaffItem.Mode.POLYGONE);
            BlockPos p1 = w.center.offset(-1000, 0, -1000);
            TerritoryService.onStaffUse(w.ruler, staff, p1, Direction.UP, false);
            TerritoryService.onStaffUse(w.ruler, staff, p1.offset(80, 0, 0), Direction.UP, false);
            TerritoryService.onStaffUse(w.ruler, staff, p1.offset(0, 0, 80), Direction.UP, false);
            TerritoryService.onStaffUse(w.ruler, staff, p1.offset(1, 0, 1), Direction.UP, false);
            w.check(BornageStaffItem.points(staff).isEmpty(), "le polygone ne s'est pas fermé");
            w.check(Territory.ownerAt(server, dim, p1.getX() + 20, p1.getZ() + 20).map(ai.id()::equals).orElse(false), "le polygone n'est pas à Ormesval");
            // Une seule vérité : la carte de l'éditeur voit ces cases.
            MapView view = WorldService.mapView(w.ruler, true);
            w.check(view.borders().size() > 10, "l'éditeur ne voit pas les cases tracées au bâton");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p14_construire", timeoutTicks = 300)
    public static void construireUnFortEtUneTour(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            ServerLevel level = h.getLevel();
            var dim = level.dimension().location();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, level, "Rochegarde", "", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 3000, w.center.getZ());
            BlockPos fortGround = ground(level, w.center.getX() + 400, w.center.getZ() + 400);
            var out = SiteService.place(w.ruler, SiteKind.FORT, ai.id(), fortGround, Direction.SOUTH, true);
            w.check(out.success(), "fort refusé : " + out.message().getString());
            Site fort = data.world().sites().iterator().next();
            w.check(!fort.built() && fort.radius() == 0, "un chantier tient déjà des terres");
            SiteService.advanceForTests(level, fort, Integer.MAX_VALUE);
            w.check(fort.built(), "le fort n'est pas achevé");
            w.check(level.getBlockState(fortGround.above(11)).getBlock() instanceof BannerBlock, "pas de bannière au sommet du donjon");
            w.check(level.getBlockState(fortGround.offset(12, 3, 0)).is(Blocks.STONE_BRICKS) || level.getBlockState(fortGround.offset(12, 3, 0)).is(Blocks.MOSSY_STONE_BRICKS)
                    || level.getBlockState(fortGround.offset(12, 3, 0)).is(Blocks.CRACKED_STONE_BRICKS), "pas de rempart à l'est");
            w.check(Territory.ownerAt(server, dim, fortGround.getX() + 60, fortGround.getZ()).map(ai.id()::equals).orElse(false),
                    "le fort n'étend pas les terres de son royaume");
            // Une seconde construction trop près : refusée.
            w.check(!SiteService.place(w.ruler, SiteKind.TOUR, ai.id(), fortGround.offset(5, 0, 5), Direction.SOUTH, true).success(), "une tour dans le fort");
            // Une tour tournée vers l'est : son échelle est tournée avec elle.
            BlockPos towerGround = ground(level, w.center.getX() - 400, w.center.getZ() + 400);
            w.check(SiteService.place(w.ruler, SiteKind.TOUR, ai.id(), towerGround, Direction.EAST, true).success(), "tour refusée");
            Site tower = data.world().sites().stream().filter(s -> s.kind() == SiteKind.TOUR).findFirst().orElseThrow();
            SiteService.advanceForTests(level, tower, Integer.MAX_VALUE);
            var ladder = level.getBlockState(towerGround.offset(-1, 1, 0));
            w.check(ladder.is(Blocks.LADDER) && ladder.getValue(LadderBlock.FACING) == Direction.EAST, "l'échelle n'a pas tourné avec la tour : " + ladder);
            // Sur la carte de l'éditeur.
            MapView view = WorldService.mapView(w.ruler, true);
            w.check(view.seats().stream().anyMatch(s -> s.site() != null && fort.id().equals(s.site().id()) && s.site().percent() == 100),
                    "le fort n'est pas sur la carte");
            // Une construction effacée du registre ne tient plus rien (ses pierres restent).
            w.check(SiteService.remove(w.ruler, fort.id()).success(), "suppression refusée");
            w.check(Territory.ownerAt(server, dim, fortGround.getX() + 60, fortGround.getZ()).isEmpty(), "le fort effacé tient encore ses terres");
            w.check(level.getBlockState(fortGround.above(11)).getBlock() instanceof BannerBlock, "les pierres du fort ont disparu");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p14_chantier", timeoutTicks = 200)
    public static void unChantierEnSurvie(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            ServerLevel level = h.getLevel();
            BlockPos home = MineColoniesBridge.center(w.colony).orElse(w.center);
            w.realm.setGold(1000);
            BlockPos inside = ground(level, home.getX() + 60, home.getZ() + 60);
            w.check(!SiteService.place(w.ruler, SiteKind.DONJON, w.realm.id(), inside, Direction.SOUTH, false).success(), "un donjon en survie");
            w.check(!SiteService.place(w.ruler, SiteKind.TOUR, w.realm.id(), ground(level, home.getX() + 900, home.getZ()), Direction.SOUTH, false).success(),
                    "une tour hors de ses terres");
            var out = SiteService.place(w.ruler, SiteKind.TOUR, w.realm.id(), inside, Direction.SOUTH, true);
            w.check(out.success(), "tour refusée : " + out.message().getString());
            Site tower = data.world().sites().iterator().next();
            w.check(!tower.instant(), "un joueur en survie bâtit d'un coup");
            w.check(w.realm.gold() == 1000 - SiteKind.TOUR.cost(), "le chantier n'a pas été payé : " + w.realm.gold());
            SiteService.advanceForTests(level, tower, 6);
            w.check(tower.progress() > 0 && !tower.built(), "le chantier n'avance pas pas à pas");
            h.succeed();
        } finally {
            cleanup(w);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p14_village", timeoutTicks = 300)
    public static void unVillagePourUnRoyaumeIa(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        List<Entity> spawned = new ArrayList<>();
        try {
            reset(w);
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            ServerLevel level = h.getLevel();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, level, "Cendrecourt", "", TitleRank.COMTE, 0x7B2D8B, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 4000, w.center.getZ());
            BlockPos villageGround = ground(level, w.center.getX() + 600, w.center.getZ() - 600);
            var out = SiteService.place(w.ruler, SiteKind.VILLAGE, ai.id(), villageGround, Direction.SOUTH, true);
            w.check(out.success(), "village refusé : " + out.message().getString());
            Site village = data.world().sites().iterator().next();
            AiProvince city = ai.provinces().stream().filter(p -> p.id() == village.province()).findFirst().orElse(null);
            w.check(city != null && !city.capital(), "le village n'a pas fondé de ville");
            w.check(village.community() != null && !data.lineage().residents(village.community()).isEmpty(), "le village n'a pas d'habitants au registre");
            for (int i = 0; i < 40 && !village.built(); i++) SiteService.tick(server, 0);
            w.check(village.built(), "le village n'est pas achevé");
            w.check(level.getBlockState(villageGround).is(Blocks.WATER), "pas d'eau dans le puits");
            if (McaBridge.isReady()) {
                long alive = data.lineage().residents(village.community()).stream().filter(Person::inWorld).count();
                w.check(alive > 0, "aucun habitant n'est venu vivre au village");
            }
            // Un donjon près de la nouvelle ville : il la loge.
            BlockPos keepGround = ground(level, villageGround.getX() + 30, villageGround.getZ());
            w.check(SiteService.place(w.ruler, SiteKind.DONJON, ai.id(), keepGround, Direction.SOUTH, true).success(), "donjon refusé");
            w.check(city.seat().getX() == keepGround.getX() && city.seat().getZ() == keepGround.getZ(), "le siège de la ville n'a pas rejoint le donjon");
            h.succeed();
        } finally {
            for (Entity e : spawned) e.discard();
            cleanup(w);
        }
    }
}
