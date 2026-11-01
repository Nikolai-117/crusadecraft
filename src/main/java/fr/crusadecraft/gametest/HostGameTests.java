package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.ArmyService;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.Formation;
import fr.crusadecraft.core.war.HostService;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Les ostes de l'ordinateur et les ordres à l'armée (P27) : l'ost marche sur la carte, paraît quand l'armée d'un joueur
 * arrive à sa hauteur, et sa défaite compte au score ; l'armée du joueur reçoit un but de marche et une formation.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HostGameTests {

    private HostGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p27_ostes", timeoutTicks = 400)
    public static void unOstMarcheLivreBatailleEtTombe(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        IronGolem levy = null;
        int[] forced = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            int cx = w.center.getX() + 320, cz = w.center.getZ();
            // Des chunks forcés : les soldats qui y paraissent doivent être visibles tout de suite.
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) h.getLevel().setChunkForced((cx >> 4) + dx, (cz >> 4) + dz, true);
            forced = new int[]{cx >> 4, cz >> 4};
            ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.EXPANSIONNISTE, cx, cz);
            ai.discover(w.ruler.getUUID(), 0);
            War war = WarService.declareBetween(server, ai.id(), w.realm.id(), CasusBelli.HUMILIATE, -1);

            // L'ost part de sa capitale et marche vers la capitale du joueur.
            HostService.tick(server);
            War.Host start = war.hosts().get(ai.id());
            w.check(start != null && Math.abs(start.x() - cx) <= 8, "l'ost ne part pas de sa capitale");
            HostService.tick(server);
            War.Host next = war.hosts().get(ai.id());
            w.check(next.x() < start.x(), "l'ost ne marche pas vers son ennemi (" + start.x() + " puis " + next.x() + ")");
            final AiRealm host = ai;
            MapView map = WorldService.mapView(w.ruler, false);
            w.check(map.armies().stream().anyMatch(a -> !a.mine() && a.hasTarget()), "la carte ne montre pas le pion de l'ost");

            // L'armée du joueur arrive à sa hauteur : l'ost paraît en vrais soldats.
            int hx = (int) next.x(), hz = (int) next.z();
            BlockPos spot = h.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(hx + 6, 0, hz));
            levy = EntityType.IRON_GOLEM.create(h.getLevel());
            levy.setPlayerCreated(true);
            levy.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            levy.addTag(ArmyService.TAG);
            levy.addTag(ArmyService.TAG + ":" + w.realm.id());
            levy.setNoAi(true);
            h.getLevel().addFreshEntity(levy);
            HostService.tick(server);
            List<Entity> ost = new ArrayList<>();
            for (Entity e : h.getLevel().getAllEntities()) if (e != null && e.getTags().contains(HostService.TAG) && e.isAlive()) ost.add(e);
            w.check(!ost.isEmpty(), "l'ost ne paraît pas devant l'armée du joueur");
            War.Host engaged = war.hosts().get(ai.id());
            HostService.tick(server);
            w.check(war.hosts().get(ai.id()).x() == engaged.x(), "l'ost continue de marcher en pleine bataille");

            // L'ost tombe jusqu'au dernier : victoire au score pour le défenseur, et l'ost se reforme plus tard.
            int before = war.battleScore();
            for (Entity e : ost) e.kill();
            War.Host beaten = war.hosts().get(ai.id());
            w.check(beaten.beaten(RealmService.currentDay(server)), "l'ost défait n'est pas marqué");
            w.check(war.battleScore() < before, "la défaite de l'ost attaquant ne profite pas au défenseur (" + before + " → " + war.battleScore() + ")");
            w.check(war.events().stream().anyMatch(e -> e.key().equals("crusadecraft.war.log.host_beaten")), "la défaite n'est pas au journal de la guerre");

            // L'armée du joueur reçoit un but de marche et une formation.
            Outcome march = ArmyService.march(w.ruler, hx - 40, hz);
            w.check(march.success(), "la marche est refusée : " + march.message().getString());
            w.check(ArmyService.status(server, w.realm.id()).order() == ArmyService.Order.MARCH, "l'armée n'est pas en marche");
            w.check(ArmyService.formation(w.ruler, Formation.LIGNE).success()
                    && ArmyService.status(server, w.realm.id()).formation() == Formation.LIGNE, "la formation n'est pas prise");
            h.succeed();
        } finally {
            if (levy != null) levy.discard();
            List<Entity> gone = new ArrayList<>();
            for (Entity e : h.getLevel().getAllEntities()) if (e != null && e.getTags().contains(HostService.TAG)) gone.add(e);
            gone.forEach(Entity::discard);
            if (forced != null) {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) h.getLevel().setChunkForced(forced[0] + dx, forced[1] + dz, false);
            }
            if (ai != null) WorldEditService.removeRealm(server, ai.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
