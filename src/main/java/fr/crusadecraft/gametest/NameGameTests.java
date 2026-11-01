package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.place.PlaceService;
import fr.crusadecraft.core.realm.NameService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WebMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Les noms et la carte fantasy (P32) : renommer son royaume et sa ville, depuis le jeu ou l'écran des lieux, et les
 * retrouver écrits sur la carte web — le royaume en grand, la capitale avec son sceau.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NameGameTests {

    private NameGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p32_noms", timeoutTicks = 200)
    public static void renommerEtLeVoirSurLaCarte(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            w.check(!NameService.renameRealm(w.ruler, "  ").success(), "un nom vide est accepté");
            Outcome realm = NameService.renameRealm(w.ruler, "Levànzia");
            w.check(realm.success() && w.realm.name().equals("Levànzia"), "le royaume n'est pas renommé : " + realm.message().getString());

            // La ville où se tient le souverain : la colonie MineColonies elle-même change de nom.
            Outcome city = NameService.renameColonyHere(w.ruler, "Khalyos");
            w.check(city.success(), "la ville n'est pas renommée : " + city.message().getString());
            w.check(MineColoniesBridge.info(w.realm.capital()).name().equals("Khalyos"), "la colonie garde son ancien nom");

            // Un royaume de l'ordinateur, posé à la main, pour la carte.
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1500, w.center.getZ());
            PlaceService.sync(server);
            WebMap.Model model = WebMap.build(server);
            String world = WebMap.worldName(h.getLevel());
            w.check(model.points().stream().anyMatch(p -> p.id().equals("nom_" + w.realm.id() + "_" + world) && p.styled() != null
                    && p.styled().contains("Levànzia")), "le nom du royaume n'est pas écrit sur la carte web");
            final AiRealm duchy = ai;
            w.check(model.points().stream().anyMatch(p -> p.id().equals("nom_" + duchy.id() + "_" + world) && p.styled().contains("Rochegarde")),
                    "le nom du duché n'est pas écrit sur la carte web");
            w.check(model.points().stream().anyMatch(p -> p.id().startsWith("lieu_") && WebMap.ICON_CAPITAL.equals(p.icon())
                    && p.styled() != null && p.styled().contains("Khalyos")), "la capitale n'a pas son sceau et son nom");

            // Depuis l'écran des lieux : le lieu du royaume renvoie à sa source.
            Lieu place = data.places().byRef(Lieu.Ref.playerRealm(w.realm.id())).orElseThrow();
            var back = PlaceService.rename(w.ruler, place.id(), "Valbrume");
            w.check(back.outcome().success() && w.realm.name().equals("Valbrume"), "l'écran des lieux ne renomme pas le royaume : "
                    + back.outcome().message().getString());
            h.succeed();
        } finally {
            if (ai != null) WorldEditService.removeRealm(server, ai.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.places().clear();
            data.world().clear();
            w.cleanup();
        }
    }
}
