package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.diplomacy.CaravanService;
import fr.crusadecraft.core.diplomacy.TradeService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.TreasuryService;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Les caravanes (P29) : une route de commerce a sa caravane, qui paraît près d'un joueur ; tuer son marchand la pille,
 * coupe la route quelques jours et fâche le royaume partenaire.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CaravanGameTests {

    private CaravanGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p29_caravanes", timeoutTicks = 300)
    public static void uneCaravaneParaitEtSeFaitPiller(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        int[] forced = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            ai = WorldEditService.addRealm(server, level, "Portmarchand", "", TitleRank.COMTE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.MARCHAND, w.center.getX() + 600, w.center.getZ());
            ai.discover(w.ruler.getUUID(), 0);
            long today = RealmService.currentDay(server);
            data.diplomacy().between(w.realm.id(), ai.id()).sign(Treaty.COMMERCE, today);
            List<TradeService.Route> routes = TradeService.routes(server);
            w.check(routes.size() == 1, "le traité ne trace pas de route");
            TradeService.Route r = routes.get(0);
            w.check(r.goodA() != null && r.goodB() != null, "la route ne porte pas de marchandises");

            // La caravane à quelques blocs du souverain. Ses chunks sont forcés, et on laisse au monde quelques ticks pour
            // les rendre actifs : une entité qui paraît dans un chunk qui ne l'est pas encore n'y est pas visible.
            double cx = r.x0() + (r.x1() - r.x0()) * 30.0 / r.distance(), cz = r.z0() + (r.z1() - r.z0()) * 30.0 / r.distance();
            forced = new int[]{(int) cx >> 4, (int) cz >> 4};
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.getChunk(forced[0] + dx, forced[1] + dz);
                    level.setChunkForced(forced[0] + dx, forced[1] + dz, true);
                }
            }
            w.ruler.setPos(cx + 6, w.center.getY(), cz);
            CaravanService.watchForTests(w.ruler, true);
            final AiRealm partner = ai;
            final int[] chunks = forced;
            h.runAfterDelay(10, () -> {
                try {
                    CaravanService.placeForTests(r.a(), r.b(), 30.0 / r.distance());
                    CaravanService.tick(server);
                    raidAndCheck(h, w, partner, r, today);
                    h.succeed();
                } finally {
                    cleanup(w, partner, chunks);
                }
            });
        } catch (RuntimeException | Error e) {
            cleanup(w, ai, forced);
            throw e;
        }
    }

    private static void raidAndCheck(GameTestHelper h, TestWorld w, AiRealm ai, TradeService.Route r, long today) {
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        WanderingTrader trader = null;
        int llamas = 0;
        for (Entity e : level.getAllEntities()) {
            if (e == null || !e.getTags().contains(CaravanService.TAG) || !e.isAlive()) continue;
            if (e instanceof WanderingTrader t) trader = t;
            else llamas++;
        }
        w.check(trader != null, "la caravane ne paraît pas près du joueur (" + CaravanService.describeForTests(level, r.a(), r.b())
                + ", joueur en " + (int) w.ruler.getX() + "," + (int) w.ruler.getZ() + ")");
        w.check(llamas >= 1, "la caravane n'a pas de lamas (" + CaravanService.describeForTests(level, r.a(), r.b()) + ")");
        MapView map = WorldService.mapView(w.ruler, false);
        w.check(map.routes().size() == 1 && map.routes().get(0).caravan(), "la carte ne montre pas la caravane");
        w.check(WebMap.build(server).points().stream().anyMatch(p -> p.id().startsWith("caravane_")), "Dynmap ne montre pas la caravane");

        // Le souverain tue le marchand : la caravane est pillée, la route s'arrête, l'or tombe au sol.
        trader.hurt(level.damageSources().playerAttack(w.ruler), 1000f);
        w.check(CaravanService.raided(r.a(), r.b(), today), "la caravane n'est pas pillée");
        w.check(TradeService.routes(server).get(0).income() == 0, "une route pillée rapporte encore");
        long coins = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ItemEntity item) coins += (long) TreasuryService.goldValues().valueOf(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem().getItem())) * item.getItem().getCount();
        }
        w.check(coins >= CaravanService.loot(r.income()), "le pillard n'emporte pas l'or de la caravane : " + coins);
        w.check(data.diplomacy().between(w.realm.id(), ai.id()).opinionOf(ai.id()).stream()
                .anyMatch(m -> m.reason() == fr.crusadecraft.core.character.OpinionReason.BRIGANDAGE), "le royaume partenaire n'en veut pas au pillard");
    }

    private static void cleanup(TestWorld w, AiRealm ai, int[] forced) {
        var level = w.ruler.serverLevel();
        var server = level.getServer();
        var data = CrusadeSavedData.get(server);
        CaravanService.watchForTests(w.ruler, false);
        List<Entity> gone = new java.util.ArrayList<>();
        for (Entity e : level.getAllEntities()) if (e != null && (e.getTags().contains(CaravanService.TAG) || e instanceof ItemEntity)) gone.add(e);
        gone.forEach(Entity::discard);
        if (forced != null) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setChunkForced(forced[0] + dx, forced[1] + dz, false);
        if (ai != null) {
            data.diplomacy().forget(ai.id());
            WorldEditService.removeRealm(server, ai.id());
        }
        WorldEditService.grantForTests(w.ruler.getUUID(), false);
        data.world().clear();
        w.cleanup();
    }
}
