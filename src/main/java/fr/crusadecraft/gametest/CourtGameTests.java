package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.court.HouseService;
import fr.crusadecraft.core.court.TournamentService;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** La vie de cour : un tournoi, une croisade, un mariage dynastique et un héritier. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CourtGameTests {

    private CourtGameTests() {}

    private static Runnable testSettings() {
        int refuse = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        boolean research = CrusadeConfig.SERVER.tournamentNeedsResearch.get();
        boolean chancery = CrusadeConfig.SERVER.chanceryNeedsResearch.get();
        int delay = CrusadeConfig.SERVER.letterDelaySeconds.get();
        boolean events = CrusadeConfig.SERVER.events.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        CrusadeConfig.SERVER.tournamentNeedsResearch.set(false);
        CrusadeConfig.SERVER.chanceryNeedsResearch.set(false);
        CrusadeConfig.SERVER.letterDelaySeconds.set(0);
        CrusadeConfig.SERVER.events.set(false);
        return () -> {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(refuse);
            CrusadeConfig.SERVER.tournamentNeedsResearch.set(research);
            CrusadeConfig.SERVER.chanceryNeedsResearch.set(chancery);
            CrusadeConfig.SERVER.letterDelaySeconds.set(delay);
            CrusadeConfig.SERVER.events.set(events);
        };
    }

    private static CitizenRef councillor(TestWorld w, Post post) {
        CitizenRef ref = MineColoniesTestKit.addCitizen(w.colony);
        w.check(CharacterService.act(w.ruler, ref, CharacterService.Action.APPOINT, post).success(), "conseiller non nommé : " + post);
        return ref;
    }

    private static void cleanup(TestWorld w, Runnable restore) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (War war : new ArrayList<>(data.wars().all())) {
            data.wars().damage().release(war.id());
            data.wars().remove(war.id());
            WarFieldService.onWarEnded(server, war.id());
        }
        data.wars().damage().take(Integer.MAX_VALUE);
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        if (w.realm != null) data.diplomacy().forget(w.realm.id());
        data.world().clear();
        w.ruler.getInventory().clearContent();
        restore.run();
        w.cleanup();
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p10_tournoi", timeoutTicks = 200)
    public static void tournoiALaLice(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            w.check(TournamentService.problem(server, w.realm) != null, "un tournoi sans chevalier ni or");
            CitizenRef a = councillor(w, Post.MARECHAL);
            CitizenRef b = councillor(w, Post.CHANCELIER);
            w.realm.setGold(1000);
            int prestige = w.realm.prestige();
            long today = RealmService.currentDay(server);
            var out = TournamentService.host(w.ruler);
            w.check(out.success(), "tournoi refusé : " + out.message().getString());
            w.check(w.realm.prestige() > prestige, "l'hôte ne gagne pas de prestige");
            w.check(w.realm.ledger().peek(today).amount(LedgerCategory.TOURNOIS) < 0, "le tournoi n'a rien coûté");
            // Sans voisins invités, le champion vient forcément de la cour.
            boolean champion = data.character(a).orElseThrow().traits().contains(Trait.CHAMPION)
                    || data.character(b).orElseThrow().traits().contains(Trait.CHAMPION);
            w.check(champion, "aucun champion couronné");
            w.check(!TournamentService.host(w.ruler).success(), "deux tournois le même jour");
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.tournament")),
                    "le tournoi n'est pas dans la chronique");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p10_croisade", timeoutTicks = 200)
    public static void croisadeContreUnPaien(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            WorldService.generate(server, h.getLevel(), w.center.getX(), w.center.getZ());
            long today = RealmService.currentDay(server);
            List<AiRealm> realms = new ArrayList<>(data.world().realms());
            for (AiRealm r : realms) {
                r.discover(w.ruler.getUUID(), today);
                r.setFaith(AiFaith.FIDELE);
            }
            AiRealm pagan = realms.stream().filter(r -> r.rank().isAtLeast(TitleRank.COMTE)).findFirst().orElseThrow();
            pagan.setFaith(AiFaith.PAIEN);
            AiRealm friend = realms.stream().filter(r -> r != pagan && !r.landless()).findFirst().orElseThrow();
            Relation rel = data.diplomacy().between(friend.id(), w.realm.id());
            for (int i = 0; i < 6; i++) rel.addOpinion(friend.id(), OpinionModifier.of(OpinionReason.CADEAU, today, 3));

            w.check(!WarService.declare(w.ruler, pagan.id(), CasusBelli.GUERRE_SAINTE, -1).success(), "croisade d'un simple baron");
            w.realm.setRank(TitleRank.COMTE);
            w.realm.setPiety(200);
            int target = WarService.provincesOf(server, pagan.id()).get(0).id();
            var out = WarService.declare(w.ruler, pagan.id(), CasusBelli.GUERRE_SAINTE, target);
            w.check(out.success(), "croisade refusée : " + out.message().getString());
            War war = data.wars().between(w.realm.id(), pagan.id()).orElseThrow();
            w.check(war.crusade(), "la guerre n'est pas une croisade");
            w.check(w.realm.piety() == 100, "piété après l'appel : " + w.realm.piety());
            w.check(war.attackerAllies().contains(friend.id()), "le royaume ami ne prend pas la croix");
            w.check(!war.attackerAllies().contains(pagan.id()), "le païen se croise contre lui-même");

            // Victoire : la province visée change de mains, et chaque croisé est béni.
            int piety = w.realm.piety(), prestige = w.realm.prestige(), friendPiety = friend.piety();
            WarService.endWar(server, war, 1);
            w.check(!WarService.provincesOf(server, w.realm.id()).isEmpty(), "aucune province gagnée");
            w.check(w.realm.piety() == piety + 50, "piété du croisé : " + w.realm.piety());
            w.check(w.realm.prestige() >= prestige + 30, "prestige du croisé : " + w.realm.prestige());
            w.check(friend.piety() == friendPiety + 50, "l'allié croisé n'est pas béni");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p10_mariage", timeoutTicks = 200)
    public static void maisonHeritierEtMariage(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            WorldService.generate(server, h.getLevel(), w.center.getX(), w.center.getZ());
            long today = RealmService.currentDay(server);
            AiRealm house = data.world().realms().stream().filter(r -> !r.landless()).findFirst().orElseThrow();
            house.discover(w.ruler.getUUID(), today);
            house.setFaith(AiFaith.FIDELE);
            Relation rel = data.diplomacy().between(house.id(), w.realm.id());
            for (int i = 0; i < 6; i++) rel.addOpinion(house.id(), OpinionModifier.of(OpinionReason.CADEAU, today, 3));

            CitizenRef member = councillor(w, Post.CHANCELIER);
            w.realm.setPrestige(100);
            w.check(HouseService.adopt(w.ruler, member).success(), "le conseiller n'entre pas dans la maison");
            w.check(w.realm.prestige() == 100 - HouseService.ADOPT_PRESTIGE, "prestige après l'accueil : " + w.realm.prestige());
            w.check(!HouseService.adopt(w.ruler, member).success(), "accueilli deux fois");
            int prestige = w.realm.prestige();
            w.check(HouseService.designateHeir(w.ruler, member).success(), "héritier refusé");
            w.check(member.equals(w.realm.dynasty().heir()) && w.realm.prestige() == prestige + 10, "héritier mal désigné");

            // La demande en mariage : acceptée par lettre, scellée au sceau, les maisons s'unissent.
            w.check(HouseService.eligible(server, w.realm, member), "le membre n'est pas libre de se marier");
            w.realm.mail().clear();
            w.check(HouseService.propose(w.ruler, member, house.id()).success(), "demande non envoyée");
            Letter letter = w.realm.mail().stream().filter(Letter::needsAnswer).findFirst()
                    .orElseThrow(() -> new IllegalStateException("la maison IA refuse malgré son estime"));
            w.check(letter.choices().get(0).action() == Letter.Action.SEAL_MARRIAGE, "la lettre ne propose pas de sceller le mariage");
            w.ruler.getInventory().add(new ItemStack(ModItems.SEAL.get()));
            var sealed = DiplomacyService.answer(w.ruler, letter.id(), 0);
            w.check(sealed.success(), "mariage non scellé : " + sealed.message().getString());
            w.check(rel.marriage() && rel.has(Treaty.ALLIANCE_DEFENSIVE, today), "les maisons ne sont pas unies");
            w.check(w.realm.dynasty().marriageOf(member).isPresent(), "le mariage n'est pas enregistré");
            w.check(!HouseService.eligible(server, w.realm, member), "un marié reste libre de se marier");
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.house.married")),
                    "le mariage n'est pas dans la chronique");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }
}
