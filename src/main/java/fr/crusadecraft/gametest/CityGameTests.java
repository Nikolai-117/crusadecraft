package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.realm.CityService;
import fr.crusadecraft.core.realm.CityView;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Les villes du royaume (P19) : rattacher une seconde colonie, la confier à un gouverneur du registre, la reprendre. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityGameTests {

    private CityGameTests() {}

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p19_villes", timeoutTicks = 200)
    public static void rattacherEtConfierUneVille(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        Person governor = null, stranger = null, son = null;
        fr.crusadecraft.core.lineage.House heirHouse = null;
        ColonyRef second = null;
        try {
            // Une seconde colonie au souverain, loin de la première : il s'y tient et la rattache. Ses chunks sont
            // chargés d'abord, comme quand un joueur y marche : MineColonies ne marque son territoire que sur eux.
            BlockPos there = w.center.offset(400, 0, 400);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) h.getLevel().getChunk((there.getX() >> 4) + dx, (there.getZ() >> 4) + dz);
            second = fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.createColony(h.getLevel(), there, w.ruler, "Clairval", 1);
            w.ruler.setPos(there.getX() + 0.5, there.getY(), there.getZ() + 2.5);
            CityView before = CityService.view(w.ruler).orElseThrow();
            final ColonyRef made = second;
            w.check(before.here().equals("Clairval"), "l'écran ne propose pas de rattacher la colonie où l'on se tient : « " + before.here()
                    + " » (colonie sous les pieds : " + MineColoniesBridge.colonyAt(h.getLevel(), w.ruler.blockPosition()).map(Object::toString).orElse("aucune")
                    + ", créée : " + made + ", propriétaire : " + MineColoniesBridge.isOwner(made, w.ruler.getUUID()) + ")");
            Outcome attached = CityService.attachHere(w.ruler);
            w.check(attached.success() && w.realm.holds(second), "rattachement refusé : " + attached.message().getString());
            w.check(key(CityService.attachHere(w.ruler).message()).equals("crusadecraft.city.attach.already"), "on rattache deux fois la même ville");

            // Un gouverneur du registre, rattaché au royaume ; un inconnu ne peut pas gouverner.
            governor = new Person(UUID.randomUUID(), "Aymeric", false, 34);
            governor.setRealm(w.realm.id());
            data.lineage().add(governor);
            stranger = new Person(UUID.randomUUID(), "Inconnu", false, 30);
            data.lineage().add(stranger);
            w.check(key(CityService.entrust(w.ruler, second, stranger.id(), 50, 50).message()).equals("crusadecraft.city.governor.invalid"),
                    "un inconnu gouverne");
            w.check(key(CityService.entrust(w.ruler, w.realm.capital(), governor.id(), 50, 50).message()).equals("crusadecraft.city.capital"),
                    "la capitale est confiée");
            Outcome entrusted = CityService.entrust(w.ruler, second, governor.id(), 40, 25);
            w.check(entrusted.success(), "ville non confiée : " + entrusted.message().getString());
            var terms = w.realm.cityTerms(second).orElseThrow();
            w.check(terms.taxShare() == 40 && terms.levyShare() == 25, "les parts de la couronne sont fausses");

            // L'écran et le Trésor le disent.
            CityView view = CityService.view(w.ruler).orElseThrow();
            w.check(view.rows().size() == 2 && view.candidates().stream().anyMatch(c -> c.id().equals(w.realm.cityTerms(made).get().governor())),
                    "l'écran des villes est incomplet");
            w.check(view.rows().stream().anyMatch(r -> r.colony().equals(made) && r.governorName().contains("Aymeric") && r.taxShare() == 40),
                    "l'écran ne montre pas le gouverneur");
            w.check(view.here().isEmpty(), "une ville déjà rattachée est encore proposée");
            var forecast = TreasuryService.forecast(server, w.realm, 0);
            w.check(forecast.colonies().stream().anyMatch(c -> c.governor().contains("Aymeric") && c.crownShare() == 40),
                    "le Trésor ignore le gouverneur");

            // Le gouverneur meurt : la ville revient en direct.
            governor.setAlive(false);
            w.check(CityService.sweep(server, w.realm) && w.realm.cityTerms(second).isEmpty(), "une ville reste à un mort");
            governor.setAlive(true);
            w.check(CityService.entrust(w.ruler, second, governor.id(), 50, 50).success(), "on ne peut pas reconfier la ville");

            // P26 : un gouverneur qui laisse un fils au royaume lui laisse la ville, aux mêmes conditions.
            heirHouse = new fr.crusadecraft.core.lineage.House(UUID.randomUUID(), "Clairval", 0x335577);
            heirHouse.setRealm(w.realm.id());
            data.lineage().add(heirHouse);
            governor.setHouse(heirHouse.id());
            heirHouse.setHead(governor.id());
            son = new Person(UUID.randomUUID(), "Thibaut", false, 20);
            son.setHouse(heirHouse.id());
            son.setRealm(w.realm.id());
            son.setFather(governor.id());
            data.lineage().add(son);
            governor.setAlive(false);
            final UUID sonId = son.id();
            w.check(CityService.sweep(server, w.realm) && w.realm.cityTerms(made).map(t -> t.governor().equals(sonId)).orElse(false),
                    "la ville ne passe pas au fils du gouverneur");
            w.check(w.realm.cityTerms(made).get().taxShare() == 50, "l'héritier ne garde pas les conditions de son père");
            governor.setAlive(true);
            w.check(CityService.recall(w.ruler, second).success() && w.realm.cityTerms(second).isEmpty(), "la ville n'est pas reprise");
            w.check(key(CityService.recall(w.ruler, second).message()).equals("crusadecraft.city.direct.already"), "reprendre deux fois réussit");
            h.succeed();
        } finally {
            if (second != null) {
                w.realm.removeProvince(second);
                fr.crusadecraft.compat.minecolonies.MineColoniesTestKit.deleteColony(second);
                data.forgetColony(second);
            }
            if (governor != null) data.lineage().remove(governor.id());
            if (stranger != null) data.lineage().remove(stranger.id());
            if (son != null) data.lineage().remove(son.id());
            if (heirHouse != null) data.lineage().removeHouse(heirHouse.id());
            w.cleanup();
        }
    }
}
