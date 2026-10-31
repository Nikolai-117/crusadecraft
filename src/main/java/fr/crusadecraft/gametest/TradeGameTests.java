package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.diplomacy.TradeService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WebMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Le commerce (P25) : un traité de commerce trace une route, visible sur la carte du mod et sur la carte web. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeGameTests {

    private TradeGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p25_commerce", timeoutTicks = 200)
    public static void unTraiteTraceUneRoute(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            ai = WorldEditService.addRealm(server, h.getLevel(), "Portmarchand", "", TitleRank.COMTE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.MARCHAND, w.center.getX() + 1200, w.center.getZ());
            w.check(TradeService.routes(server).isEmpty(), "une route existe sans traité");

            long today = RealmService.currentDay(server);
            data.diplomacy().between(w.realm.id(), ai.id()).sign(Treaty.COMMERCE, today);
            List<TradeService.Route> routes = TradeService.routes(server);
            w.check(routes.size() == 1, "le traité ne trace pas de route : " + routes.size());
            TradeService.Route r = routes.get(0);
            w.check(r.distance() > 1000 && r.income() >= 2 + TradeService.distanceBonus(r.distance()), "la route lointaine ne rapporte pas davantage");

            // Sur la carte du mod : seulement pour qui connaît les deux royaumes, ou y prend part.
            final AiRealm partner = ai;
            MapView view = WorldService.mapView(w.ruler, false);
            w.check(view.routes().size() == 1, "le souverain ne voit pas sa route sur la carte");

            // Sur la carte web : une ligne dans la couche du commerce.
            WebMap.Model model = WebMap.build(server);
            w.check(model.lines().stream().anyMatch(l -> l.id().startsWith("commerce_") && l.html().contains("Portmarchand")),
                    "la route n'est pas sur la carte web");
            data.diplomacy().between(w.realm.id(), partner.id()).breakTreaty(Treaty.COMMERCE);
            w.check(TradeService.routes(server).isEmpty(), "la route reste après la fin du traité");
            h.succeed();
        } finally {
            if (ai != null) {
                data.diplomacy().forget(ai.id());
                WorldEditService.removeRealm(server, ai.id());
            }
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
