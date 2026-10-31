package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.lineage.House;
import fr.crusadecraft.core.lineage.LifeService;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Le cycle de vie (P21) : une année passe, le vieux roi meurt, l'héritier de la loi prend la maison ; un trône IA aussi. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeGameTests {

    private LifeGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p21_vie", timeoutTicks = 200)
    public static void uneAnneePasseEtLaMaisonHerite(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        House house = null;
        AiRealm ai = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            // Une maison au royaume : un roi de 99 ans (il ne passera pas l'année), une fille aînée, un fils cadet.
            house = new House(UUID.randomUUID(), "Aubépine", 0x8844AA);
            house.setRealm(w.realm.id());
            data.lineage().add(house);
            Person king = new Person(UUID.randomUUID(), "Godefroy", false, LifeService.MAX_AGE - 1);
            Person daughter = new Person(UUID.randomUUID(), "Aliénor", true, 30);
            Person son = new Person(UUID.randomUUID(), "Baudouin", false, 25);
            for (Person p : List.of(king, daughter, son)) {
                p.setHouse(house.id());
                p.setRealm(w.realm.id());
                data.lineage().add(p);
            }
            daughter.setFather(king.id());
            son.setFather(king.id());
            king.setRole(Role.SOUVERAIN);
            house.setHead(king.id());
            // Loi égalitaire : l'aînée hérite, pas le fils.
            w.realm.laws().set(Law.SUCCESSION, 1, RealmService.currentDay(server));

            // Un royaume IA dont le souverain aussi est au bout de sa vie.
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Vieux Roi", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1500, w.center.getZ());
            AiCharacter old = ai.ruler();
            ai.setRuler(new AiCharacter(old.name(), old.female(), LifeService.MAX_AGE - 1, old.stats(), List.of(Trait.JUSTE)));

            int deaths = LifeService.year(server, 1000, new Random(42));
            w.check(!king.alive(), "le roi centenaire vit encore");
            w.check(daughter.age() == 31 && son.age() == 26, "les vivants n'ont pas vieilli");
            w.check(house.head().equals(daughter.id()), "la loi égalitaire n'a pas donné la maison à l'aînée");
            w.check(daughter.role() == Role.SOUVERAIN, "l'héritière n'a pas pris le rôle de souverain");
            w.check(!ai.ruler().name().equals(old.name()) || ai.ruler().age() < 50, "le souverain IA centenaire règne encore");
            w.check(data.world().news().stream().anyMatch(n -> n.key().equals("crusadecraft.news.succession")), "la succession IA n'est pas annoncée");
            w.check(deaths >= 2, "on compte " + deaths + " mort(s) au lieu d'au moins deux");
            h.succeed();
        } finally {
            if (house != null) data.lineage().removeHouse(house.id());
            if (ai != null) WorldEditService.removeRealm(server, ai.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
