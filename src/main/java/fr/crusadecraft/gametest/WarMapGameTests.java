package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarMap;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WebMap;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** La carte de guerre (P18) : tracer son plan, le partager avec ses alliés, le voir sur la carte web. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarMapGameTests {

    private WarMapGameTests() {}

    private static void resetWorld(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static Realm otherRealm(TestWorld w, ServerPlayer ruler, String name, int colony) {
        var dim = w.helper.getLevel().dimension().location();
        Realm r = new Realm(UUID.randomUUID(), name, "", "", 0x335588, ruler.getUUID(), ruler.getGameProfile().getName(), false,
                new ColonyRef(dim, colony), TitleRank.BARONNIE, 0);
        CrusadeSavedData.get(w.helper.getLevel().getServer()).add(r);
        return r;
    }

    private static MapView.WarPlan planOf(MapView.War war, UUID realm) {
        for (MapView.WarPlan p : war.plans()) if (p.realm().equals(realm)) return p;
        return null;
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18_plan", timeoutTicks = 200)
    public static void lePlanSeTraceEtSePartage(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        ServerPlayer allyRuler = TestWorld.fakePlayer(h, "allie"), strangerRuler = TestWorld.fakePlayer(h, "etranger");
        Realm ally = null, stranger = null;
        Runnable siege = null;
        try {
            resetWorld(w);
            var dim = h.getLevel().dimension().location();
            ally = otherRealm(w, allyRuler, "Comté d'Aubépine", 987101);
            stranger = otherRealm(w, strangerRuler, "Baronnie de Nulle-Part", 987102);
            int cx = TerritoryMap.cellOf(w.center.getX()) + 10, cz = TerritoryMap.cellOf(w.center.getZ());
            long[] front = {TerritoryMap.key(cx, cz), TerritoryMap.key(cx + 1, cz), TerritoryMap.key(cx + 2, cz)};

            // Le souverain trace : trois chunks d'offensive, une flèche, un repère.
            Outcome painted = WarMapService.paint(w.ruler, WarMap.Zone.OFFENSIVE, front);
            w.check(painted.success(), "zone refusée : " + painted.message().getString());
            w.check(key(WarMapService.paint(w.ruler, WarMap.Zone.OFFENSIVE, front).message()).equals("crusadecraft.warmap.nothing"),
                    "repeindre à l'identique change quelque chose");
            w.check(key(WarMapService.arrow(w.ruler, 0, 0, 5, 5).message()).equals("crusadecraft.warmap.arrow.short"), "une flèche minuscule passe");
            w.check(WarMapService.arrow(w.ruler, cx * 16, cz * 16, cx * 16 + 200, cz * 16 + 40).success(), "flèche refusée");
            w.check(WarMapService.pin(w.ruler, cx * 16 + 8, cz * 16 + 8, WarMap.Mark.DANGER, "§cGué\ngardé").success(), "repère refusé");
            WarMap.Plan plan = data.warMap().find(w.realm.id()).orElseThrow();
            w.check(plan.pins().get(0).note().equals("Gué gardé"), "la note garde ses codes : " + plan.pins().get(0).note());
            UUID pin = plan.pins().get(0).id(), arrow = plan.arrows().get(0).id();

            // Il voit son plan ; un royaume sans lien ne le voit pas ; un allié, si.
            MapView.War mine = WarMapService.view(w.ruler, dim);
            MapView.WarPlan own = mine.own();
            w.check(mine.enabled() && own != null && own.keys().length == 3 && own.arrows().size() == 1 && own.pins().size() == 1,
                    "le souverain ne voit pas son plan");
            w.check(planOf(WarMapService.view(allyRuler, dim), w.realm.id()) == null, "un royaume sans alliance voit le plan");
            data.diplomacy().between(w.realm.id(), ally.id()).sign(Treaty.ALLIANCE, RealmService.currentDay(server));
            MapView.WarPlan shared = planOf(WarMapService.view(allyRuler, dim), w.realm.id());
            w.check(shared != null && !shared.mine() && shared.keys().length == 3, "l'allié ne voit pas le plan");
            w.check(planOf(WarMapService.view(strangerRuler, dim), w.realm.id()) == null, "un étranger voit le plan");

            // L'allié voit, mais ne touche pas ; le souverain retire sa flèche.
            w.check(key(WarMapService.remove(allyRuler, pin).message()).equals("crusadecraft.warmap.not_yours"), "l'allié retire un repère qui n'est pas à lui");
            w.check(WarMapService.remove(w.ruler, arrow).success() && plan.arrows().isEmpty(), "la flèche n'est pas retirée");

            // Sans royaume, pas de plan.
            ServerPlayer nobody = TestWorld.fakePlayer(h, "sansroyaume");
            w.check(key(WarMapService.paint(nobody, WarMap.Zone.DEFENSE, front).message()).equals("crusadecraft.realm.none"), "un sans-royaume trace");
            w.check(!WarMapService.view(nobody, dim).enabled(), "un sans-royaume a une carte de guerre");

            // Un siège en cours de l'allié apparaît sur la carte du souverain.
            siege = WarFieldService.siegeForTests(h.getLevel().dimension(), w.center.offset(300, 0, 0), ally.id(), stranger.id(), 4);
            MapView.War withSiege = WarMapService.view(w.ruler, dim);
            w.check(withSiege.sieges().size() == 1 && withSiege.sieges().get(0).garrison() == 4, "le siège de l'allié n'apparaît pas");

            // La carte voyage jusqu'au client sans rien perdre.
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            WorldService.mapView(w.ruler, false).write(buf);
            MapView back = MapView.read(buf);
            MapView.WarPlan sent = back.war().own();
            w.check(sent != null && sent.keys().length == 3 && sent.pins().get(0).note().equals("Gué gardé") && back.war().sieges().size() == 1,
                    "la carte de guerre s'abîme en route");
            w.check(sent.pins().get(0).dim().equals(dim), "un repère reçu a perdu sa dimension");

            // Un royaume dissous emporte son plan.
            w.check(WarMapService.paint(strangerRuler, WarMap.Zone.DEFENSE, front).success(), "l'étranger ne trace pas");
            data.remove(stranger.id());
            w.check(data.warMap().find(stranger.id()).isEmpty(), "le plan d'un royaume dissous reste");
            stranger = null;

            // Tout effacer.
            w.check(WarMapService.clear(w.ruler).success(), "effacer refusé");
            w.check(key(WarMapService.clear(w.ruler).message()).equals("crusadecraft.warmap.nothing"), "effacer un plan vide réussit");
            h.succeed();
        } finally {
            if (siege != null) siege.run();
            for (Realm r : new Realm[]{ally, stranger}) {
                if (r == null) continue;
                data.diplomacy().forget(r.id());
                data.remove(r.id());
            }
            data.warMap().forget(w.realm.id());
            resetWorld(w);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p18_carteweb", timeoutTicks = 200)
    public static void laCarteWebMontreLaGuerre(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        boolean webWar = CrusadeConfig.SERVER.webMapWar.get();
        try {
            resetWorld(w);
            int cx = TerritoryMap.cellOf(w.center.getX()) + 12, cz = TerritoryMap.cellOf(w.center.getZ());
            long[] wall = {TerritoryMap.key(cx, cz), TerritoryMap.key(cx, cz + 1), TerritoryMap.key(cx + 1, cz)};
            w.check(WarMapService.paint(w.ruler, WarMap.Zone.DEFENSE, wall).success(), "zone refusée");
            w.check(WarMapService.arrow(w.ruler, cx * 16, cz * 16, cx * 16 - 300, cz * 16).success(), "flèche refusée");
            WarMap.Plan plan = data.warMap().find(w.realm.id()).orElseThrow();
            UUID arrow = plan.arrows().get(0).id();

            String world = WebMap.worldName(h.getLevel());
            WebMap.Model model = WebMap.build(server);
            String zones = "guerre_" + w.realm.id() + "_defense_" + world;
            w.check(model.areas().stream().anyMatch(a -> a.id().startsWith(zones) && a.html().contains("Valbrume")), "la zone de défense manque");
            for (var a : model.areas()) {
                if (!a.id().startsWith("guerre_")) continue;
                for (double x : a.xs()) w.check(x % 16 == 0, "une zone ne suit pas les chunks");
            }
            w.check(model.lines().stream().anyMatch(l -> l.id().startsWith(zones + "_bord_")), "la zone n'a pas de bord");
            w.check(model.lines().stream().anyMatch(l -> l.id().equals("guerre_" + arrow)), "la flèche manque");
            w.check(model.lines().stream().filter(l -> l.id().startsWith("guerre_" + arrow + "_pointe")).count() == 2, "la pointe de la flèche manque");

            // Un repère change l'empreinte : la carte web sera redessinée.
            w.check(WarMapService.pin(w.ruler, cx * 16, cz * 16, WarMap.Mark.OBJECTIF, "Le pont").success(), "repère refusé");
            UUID pin = plan.pins().get(0).id();
            WebMap.Model next = WebMap.build(server);
            w.check(next.hash() != model.hash(), "un repère ne change pas l'empreinte");
            w.check(next.points().stream().anyMatch(p -> p.id().equals("guerre_" + pin) && p.icon().equals("redflag") && p.label().equals("Le pont")),
                    "le repère n'est pas sur la carte web");

            // La couche se coupe au réglage.
            CrusadeConfig.SERVER.webMapWar.set(false);
            WebMap.Model off = WebMap.build(server);
            w.check(off.areas().stream().noneMatch(a -> a.id().startsWith("guerre_")) && off.lines().stream().noneMatch(l -> l.id().startsWith("guerre_"))
                    && off.points().stream().noneMatch(p -> p.id().startsWith("guerre_")), "la carte de guerre reste sur la carte web");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.webMapWar.set(webWar);
            data.warMap().forget(w.realm.id());
            resetWorld(w);
            w.cleanup();
        }
    }
}
