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
import java.util.Random;

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

            Optional<Site> site = Optional.empty();
            Random random = new Random(7);
            for (int i = 0; i < 6 && site.isEmpty(); i++) site = CivService.tryBuild(server, ai, random);
            w.check(site.isPresent(), "le royaume n'a trouvé aucun endroit où bâtir sur ses terres plates");
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
