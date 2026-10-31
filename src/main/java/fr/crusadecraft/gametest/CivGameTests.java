package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ai.CivService;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Les civilisations autonomes (P22) : un royaume IA ouvre un chantier sur ses terres, note ses blocs, et tout se défait. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CivGameTests {

    private CivGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p22_civ", timeoutTicks = 400)
    public static void unRoyaumeBatitEtToutSeDefait(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            int cx = w.center.getX() + 700, cz = w.center.getZ();
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) h.getLevel().getChunk((cx >> 4) + dx, (cz >> 4) + dz);
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, cx, cz);
            // Hors de la zone permise : rien.
            w.check(!CivService.inZone(cx, cz, 10), "la zone permise ne limite rien");

            // Une parcelle d'herbe plate, dégagée, sur les terres du royaume : le relief du monde de test change d'une
            // partie à l'autre, le test ne doit pas en dépendre.
            int px = cx + 32, pz = cz;
            int y0 = h.getLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(px, 0, pz)).getY();
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    for (int dy = -3; dy <= 16; dy++) {
                        BlockPos q = new BlockPos(px + dx, y0 + dy, pz + dz);
                        h.getLevel().setBlock(q, dy < -1 ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()
                                : dy == -1 ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState()
                                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            Optional<Site> site = CivService.tryBuildAt(server, ai, h.getLevel(), px, pz);
            w.check(site.isPresent(), "le royaume ne bâtit pas sur une parcelle plate de ses terres");
            Site s = site.get();
            w.check(s.auto() && s.kind() == SiteKind.TOUR && !s.built(), "le premier chantier n'est pas une tour en cours");
            w.check(s.owner().equals(ai.id()), "le chantier n'est pas à ce royaume");

            // Le chantier avance et note chaque bloc remplacé.
            BlockPos probe = s.anchor().above();
            BlockState before = h.getLevel().getBlockState(probe);
            SiteService.advanceForTests(h.getLevel(), s, 4000);
            int noted = data.wars().damage().count(s.id());
            w.check(noted > 0, "le chantier n'a rien noté de ce qu'il remplace");

            // Tout se défait : le registre d'abord, les blocs ensuite, peu à peu.
            int undone = SiteService.undoAuto(server);
            w.check(undone == 1 && data.world().site(s.id()).isEmpty(), "la construction reste au registre");
            for (int i = 0; i < 400 && data.wars().damage().pending() > 0; i++) WarFieldService.restoreTick(server);
            w.check(data.wars().damage().pending() == 0, "des blocs attendent encore d'être remis");
            w.check(h.getLevel().getBlockState(probe).equals(before), "le bloc d'avant n'est pas revenu en " + probe.toShortString());
            h.succeed();
        } finally {
            if (ai != null) WorldEditService.removeRealm(server, ai.id());
            SiteService.undoAuto(server);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
