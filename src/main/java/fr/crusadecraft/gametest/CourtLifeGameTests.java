package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ai.PartitionService;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.court.FeastService;
import fr.crusadecraft.core.lineage.LifeService;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiCharacter;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** La vie de la cour (P26) : banquet et deuil, partage et crise des royaumes de l'ordinateur, répliques du datapack. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CourtLifeGameTests {

    private CourtLifeGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p26_cour", timeoutTicks = 200)
    public static void unBanquetPuisUnDeuil(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        try {
            w.realm.setGold(1000);
            int prestige = w.realm.prestige();
            long cost = FeastService.cost(w.realm.rank());
            Outcome held = FeastService.hold(w.ruler);
            w.check(held.success(), "banquet refusé : " + held.message().getString());
            w.check(w.realm.gold() == 1000 - cost, "le banquet n'a pas coûté " + cost + " pièces (reste " + w.realm.gold() + ")");
            w.check(w.realm.prestige() > prestige, "le banquet n'a rien rapporté en prestige");
            w.check(FeastService.feasting(server, w.realm.id()), "la cour ne festoie pas");
            w.check("crusadecraft.feast.already".equals(FeastService.problem(server, w.realm)), "on peut doubler le banquet");
            w.check(FeastService.view(server, w.realm).feasting(), "l'écran de la maison ne voit pas le banquet");

            // Un deuil arrête le banquet, et empêche le suivant.
            FeastService.mourn(server, w.realm, UUID.randomUUID(), "Godefroy");
            w.check(!FeastService.feasting(server, w.realm.id()), "le banquet continue pendant le deuil");
            w.check("crusadecraft.feast.mourning".equals(FeastService.problem(server, w.realm)), "on peut festoyer en plein deuil");
            w.check(FeastService.funeral(server, w.realm.id()), "le premier jour du deuil n'est pas celui des funérailles");
            w.check(FeastService.view(server, w.realm).mourning().equals("Godefroy"), "l'écran ne dit pas de qui on porte le deuil");
            w.check(LifeService.mourned(false, Role.HERITIER) && !LifeService.mourned(false, Role.PAYSAN), "on porte le deuil de n'importe qui");
            h.succeed();
        } finally {
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p26_cour", timeoutTicks = 400)
    public static void unRoyaumeSePartagePuisUnPretendantSeLeve(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        List<UUID> made = new ArrayList<>();
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            int cx = w.center.getX() + 1500, cz = w.center.getZ();
            AiRealm ai = WorldEditService.addRealm(server, h.getLevel(), "Rochegarde", "Vieux Roi", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, cx, cz);
            made.add(ai.id());
            int[] far = {200, 400, 600};
            for (int i = 0; i < far.length; i++) {
                WorldEditService.addCity(server, h.getLevel(), ai.id(), "Ville" + i, WorldEditService.CitySize.BOURG, cx + far[i], cz);
            }
            w.check(ai.ownProvinces().size() == 4, "le royaume n'a pas ses quatre villes");
            int farthest = ai.ownProvinces().stream().filter(p -> p.name().equals("Ville2")).findFirst().orElseThrow().id();

            // Un royaume posé à la main reste entier.
            AiCharacter heir = new AiCharacter("Cadet", false, 20, ai.ruler().stats().clone(), List.of(Trait.BRAVE));
            w.check(PartitionService.afterSuccession(server, ai, LifeService.Succession.EGALITAIRE, ai.ruler(), 10, new Random(1)).isEmpty(),
                    "un royaume fait main se partage");

            ai.setHandMade(false);
            ai.setGold(400);
            Optional<AiRealm> cadet = PartitionService.partition(server, ai, heir, 10);
            w.check(cadet.isPresent(), "le royaume ne se partage pas");
            made.add(cadet.get().id());
            w.check(cadet.get().ownProvinces().size() == 1 && cadet.get().ownProvinces().get(0).id() == farthest,
                    "le cadet n'emporte pas la ville la plus lointaine");
            w.check(cadet.get().capital() != null && cadet.get().capital().capital(), "le cadet n'a pas de capitale");
            w.check(ai.ownProvinces().size() == 3 && ai.capital() != null, "le royaume ne garde pas le reste et sa capitale");
            w.check(cadet.get().gold() == 200 && ai.gold() == 200, "le trésor n'est pas partagé en deux");
            w.check(cadet.get().house().equals(ai.house()) && cadet.get().rank() == TitleRank.COMTE, "le cadet n'est pas une branche de la maison");
            w.check(data.world().news().stream().anyMatch(n -> n.key().equals("crusadecraft.news.partition")), "le partage n'est pas annoncé");

            // Une crise : le prétendant prend une ville et réclame le trône par les armes.
            Optional<AiRealm> rebel = PartitionService.crisis(server, ai, new AiCharacter("Prétendant", false, 40, ai.ruler().stats().clone(),
                    List.of()), 11);
            w.check(rebel.isPresent(), "aucun prétendant ne se lève");
            made.add(rebel.get().id());
            AiProvince capital = ai.capital();
            w.check(rebel.get().ownProvinces().size() == 1 && rebel.get().ownProvinces().stream().noneMatch(p -> p.id() == capital.id()),
                    "le prétendant n'a pas sa ville, ou il a pris la capitale");
            w.check(data.wars().between(rebel.get().id(), ai.id()).isPresent(), "le prétendant ne déclare pas la guerre");
            h.succeed();
        } finally {
            for (UUID id : made) WorldEditService.removeRealm(server, id);
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p26_cour", timeoutTicks = 100)
    public static void lesRepliquesDuDatapackSontLues(GameTestHelper h) {
        List<Repliques.Entry> entries = Repliques.entries();
        if (entries.size() < 4) throw new net.minecraft.gametest.framework.GameTestAssertException("seulement " + entries.size() + " lot(s) de répliques");
        Random random = new Random(3);
        for (Repliques.Moment m : Repliques.Moment.values()) {
            if (Repliques.pick(entries, m, Role.FAMILLE, List.of(), random) == null) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("aucune réplique pour le moment " + m);
            }
        }
        h.succeed();
    }
}
