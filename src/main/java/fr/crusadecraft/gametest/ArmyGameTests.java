package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.war.ArmyService;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Les levées en chair et en os (P23) : rassembler au camp, donner un ordre, perdre un homme, démobiliser. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmyGameTests {

    private ArmyGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p23_armee", timeoutTicks = 400)
    public static void rassemblerPerdreEtDemobiliser(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        Site camp = null;
        try {
            // Sans armée levée, ni camp : rien.
            w.check(!ArmyService.muster(w.ruler).success(), "on rassemble une armée qui n'est pas levée");
            w.realm.setArmyRaised(true);
            w.realm.setArmyMen(60);
            w.check(!ArmyService.muster(w.ruler).success(), "on rassemble sans camp");

            // Un camp militaire bâti, tout près.
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            BlockPos ground = h.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.center.offset(30, 0, 0)).below();
            Outcome placed = SiteService.place(w.ruler, SiteKind.CAMP, w.realm.id(), ground, Direction.SOUTH, true);
            w.check(placed.success(), "camp refusé : " + placed.message().getString());
            camp = data.world().sites().stream().filter(s -> s.kind() == SiteKind.CAMP && s.owner().equals(w.realm.id())).findFirst().orElseThrow();
            for (int i = 0; i < 40 && !camp.built(); i++) SiteService.advanceForTests(h.getLevel(), camp, 5000);
            w.check(camp.built(), "le camp ne s'achève pas");
            w.ruler.setPos(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 4.5);

            Outcome mustered = ArmyService.muster(w.ruler);
            w.check(mustered.success(), "rassemblement refusé : " + mustered.message().getString());
            List<Mob> soldiers = ArmyService.soldiers(h.getLevel(), w.realm.id());
            w.check(soldiers.size() == 6, "60 hommes devraient faire 6 soldats, pas " + soldiers.size());
            w.check(!ArmyService.muster(w.ruler).success(), "on rassemble deux fois la même armée");
            w.check(ArmyService.order(w.ruler, ArmyService.Order.FOLLOW).success(), "l'ordre de suivre est refusé");

            // Un soldat tombe : dix hommes de moins.
            soldiers.get(0).kill();
            w.check(w.realm.armyMen() == 50, "un soldat tué devrait coûter dix hommes : " + w.realm.armyMen());

            Outcome dismissed = ArmyService.dismiss(w.ruler);
            w.check(dismissed.success() && ArmyService.soldiers(h.getLevel(), w.realm.id()).isEmpty(), "des soldats restent après la démobilisation");
            w.check(w.realm.armyMen() == 50, "la démobilisation ne doit rien coûter");
            h.succeed();
        } finally {
            ArmyService.dismiss(w.ruler);
            if (camp != null) data.world().removeSite(camp.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }
}
