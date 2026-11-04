package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.nation.NationMail;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P41 : les nations écrites d'avance. Elles existent dès le départ, sans terre ; une ville de leur dossier leur revient ;
 * une ville déjà posée s'y fond sans rien perdre ; leur souverain décide à la place de l'ordinateur.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NationGameTests {
    private NationGameTests() {}

    private static NationDef def(String key, String name, String folder, boolean player, NationDef.Tie... ties) {
        return new NationDef(key, name, folder, TitleRank.ROYAUME, 0x6D3A7A, AiPersonality.MARCHAND, AiFaith.FIDELE, player, name,
                player ? null : new NationDef.Ruler("Ruprecht", false, 50), List.of(ties), NationDef.Culture.NONE, List.of("Une histoire."), Map.of(), List.of());
    }

    private static final NationDef.Tie FRIENDS_B = new NationDef.Tie("essaib", List.of(Treaty.COMMERCE, Treaty.NON_AGRESSION), 1);

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p41_nations", timeoutTicks = 300)
    public static void naissentUneSeuleFois(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Essaia");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(def("essaia", "Essaia", "EssaiA", true, FRIENDS_B), def("essaib", "Essaib", "EssaiB", false)));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElse(null), b = NationService.realm(server, "essaib").orElse(null);
            w.check(a != null && b != null, "les nations ne sont pas nées");
            w.check(a.landless() && b.landless(), "une nation naît sans terre");
            w.check(b.ruler().name().equals("Ruprecht") && b.ruler().age() == 50, "le souverain du fichier n'est pas repris : " + b.ruler().name());
            long today = RealmService.currentDay(server);
            Relation rel = data.diplomacy().between(a.id(), b.id());
            w.check(rel.has(Treaty.COMMERCE, today) && rel.has(Treaty.NON_AGRESSION, today), "les traités du fichier ne sont pas signés");
            w.check(fr.crusadecraft.core.ai.AiBrain.opinion(server, a, b) >= 10, "leur vieille amitié ne compte pas");
            int count = data.world().realms().size();
            NationService.ensure(server);
            w.check(data.world().realms().size() == count, "une relecture crée des doublons");
            // Enregistrée puis relue : sa clé, sa couronne, son courrier.
            a.setSovereign(w.ruler.getUUID(), "Moi");
            a.mail().add(new NationMail(UUID.randomUUID(), b.id(), "B", "Sujet", "Corps", Treaty.COMMERCE, 3, 1, 9));
            AiRealm loaded = AiRealm.load(a.save());
            w.check(loaded.nation().equals("essaia") && w.ruler.getUUID().equals(loaded.sovereign()) && loaded.mail().size() == 1
                    && loaded.mail().get(0).treaty() == Treaty.COMMERCE, "une nation enregistrée perd quelque chose");
            // Effacée dans l'éditeur, elle ne revient pas toute seule.
            WorldEditService.removeRealm(server, b.id());
            NationService.ensure(server);
            w.check(NationService.realm(server, "essaib").isEmpty(), "une nation effacée revient au démarrage suivant");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p41_nations", timeoutTicks = 300)
    public static void villeRejointSaNation(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Essaic");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(def("essaia", "Essaia", "EssaiA", true), def("essaib", "Essaib", "EssaiB", false)));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow(), b = NationService.realm(server, "essaib").orElseThrow();
            var at = w.center.offset(800, 0, 0);
            var plan = ImportedTownGameTests.tinyCity();
            var refB = "file:plans|EssaiB/grunbach.schem";
            UUID idB = UUID.randomUUID();
            SiteService.registerImportedTown(w.ruler, idB, at, plan, refB);
            Site siteB = data.world().site(idB).orElse(null);
            w.check(siteB != null && b.id().equals(siteB.owner()), "la ville de Varenhold ne lui revient pas");
            w.check(!b.landless() && b.ownProvinces().size() == 1, "sa nation n'a pas de terre");
            w.check(b.sovereign() == null, "une nation de l'ordinateur a reçu un souverain");
            w.check(fr.crusadecraft.core.world.Territory.ownerAt(server, level.dimension().location(), at.getX() + 0.5, at.getZ() + 0.5).map(b.id()::equals).orElse(false),
                    "l'emprise de la ville n'est pas à sa nation");
            w.check(data.world().realms().stream().noneMatch(r -> r.place().equalsIgnoreCase("grunbach")), "une nation a été fondée au nom de la ville");
            w.check(data.lineage().community(siteB.community()).map(c -> b.id().equals(c.realm())).orElse(false), "les gens de la ville ne servent pas sa nation");

            // Une ville d'une nation de joueur : celui qui la pose en prend la couronne.
            var refA = "file:plans|EssaiA/dolhimluith.schem";
            UUID idA = UUID.randomUUID();
            SiteService.registerImportedTown(w.ruler, idA, at.offset(400, 0, 0), plan, refA);
            w.check(a.id().equals(data.world().site(idA).orElseThrow().owner()), "la ville de Levànzia ne lui revient pas");
            w.check(w.ruler.getUUID().equals(a.sovereign()), "le joueur n'a pas la couronne");
            w.check(NationService.ruledBy(server, w.ruler.getUUID()).filter(r -> r == a).isPresent(), "ruledBy ne le retrouve pas");
            // Ailleurs, sur une terre libre, une ville fondée par le souverain l'agrandit.
            int before = a.ownProvinces().size();
            UUID idC = UUID.randomUUID();
            SiteService.registerImportedTown(w.ruler, idC, at.offset(9000, 0, 0), plan, "file:plans|ailleurs/autre.schem");
            w.check(a.id().equals(data.world().site(idC).orElseThrow().owner()) && a.ownProvinces().size() == before + 1, "le souverain fonde un royaume de plus");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Une ville posée avant que sa nation n'existe : elle s'y fond sans rien perdre, et sa fausse nation disparaît. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p41_nations", timeoutTicks = 300)
    public static void villeDejaPoseeMigre(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Essaid");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        try {
            data.world().clear();
            long today = RealmService.currentDay(server);
            var at = w.center.offset(900, 0, 0);
            AiRealm old = WorldEditService.addRealm(server, level, "Grunbach", "", TitleRank.DUCHE, 0x112233, AiFaith.FIDELE, AiPersonality.OPPORTUNISTE, at.getX(), at.getZ());
            old.setGold(777);
            UUID id = UUID.randomUUID();
            Site site = new Site(id, SiteKind.VILLAGE, "Grunbach", old.id(), level.dimension().location(), at, Direction.SOUTH, 0x112233, true, today);
            site.setProvince(old.capital().id());
            site.setImported("file:plans|EssaiB/grunbach.schem", 96, 40, 96, 3);
            site.setBuilt(true);
            var c = new fr.crusadecraft.core.lineage.LineageGenerator(1).community(data.lineage(), "Grunbach", fr.crusadecraft.core.lineage.CommunityKind.VILLAGE, old.id(), 2);
            site.setCommunity(c.id());
            data.world().add(site);
            int people = data.lineage().residents(c.id()).size();
            int province = old.capital().id();

            NationBook.setForTests(List.of(def("essaib", "Essaib", "EssaiB", false)));
            NationService.ensure(server);
            AiRealm b = NationService.realm(server, "essaib").orElseThrow();
            w.check(data.world().realm(old.id()).isEmpty(), "la fausse nation existe encore");
            Optional<Site> kept = data.world().site(id);
            w.check(kept.isPresent(), "la ville a disparu du registre");
            w.check(b.id().equals(kept.get().owner()), "la ville n'est pas passée à sa nation");
            w.check(b.ownProvinces().stream().anyMatch(p -> p.id() == province), "sa province n'a pas suivi");
            w.check(b.gold() >= 777, "le trésor n'a pas suivi : " + b.gold());
            w.check(data.lineage().residents(c.id()).size() == people, "des gens ont disparu");
            w.check(data.lineage().residents(c.id()).stream().allMatch(p -> b.id().equals(p.realm())), "les gens ne servent pas sa nation");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p41_nations", timeoutTicks = 300)
    public static void leSouverainDecide(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Essaie");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(def("essaia", "Essaia", "EssaiA", true, FRIENDS_B), def("essaib", "Essaib", "EssaiB", false)));
            NationService.ensure(server);
            AiRealm a = NationService.realm(server, "essaia").orElseThrow(), b = NationService.realm(server, "essaib").orElseThrow();
            WorldEditService.addCity(server, level, a.id(), "Dol", WorldEditService.CitySize.VILLE, w.center.getX() + 500, w.center.getZ());
            WorldEditService.addCity(server, level, b.id(), "Gru", WorldEditService.CitySize.VILLE, w.center.getX() + 900, w.center.getZ());
            w.check(NationService.act(w.ruler, b.id(), NationService.OP_GIFT, -1, null).success() == false, "sans couronne, on agit quand même");
            w.check(NationService.crown(w.ruler, a).success(), "la couronne se refuse");
            w.check(a.ruler().name().equals(w.ruler.getGameProfile().getName()), "le joueur n'est pas le souverain affiché");
            var other = TestWorld.fakePlayer(h, "autre");
            w.check(!NationService.crown(other, a).success(), "deux souverains pour une couronne");
            long today = RealmService.currentDay(server);
            Relation rel = data.diplomacy().between(a.id(), b.id());

            // Assez d'amitié pour un pacte, pas pour une alliance totale : le souverain d'en face répond selon son opinion.
            rel.breakTreaty(Treaty.COMMERCE);
            Outcome yes = NationService.act(w.ruler, b.id(), NationService.OP_PROPOSE, Treaty.COMMERCE.ordinal(), null);
            w.check(yes.success() && rel.has(Treaty.COMMERCE, today), "un ami refuse un accord commercial : " + yes.message().getString());
            w.check(a.mail().size() == 1 && a.mail().stream().noneMatch(m -> m.body().contains("{")), "la réponse n'arrive pas, ou garde des trous : " + a.mail());

            // Une offre attend sa réponse.
            a.mail().clear();
            rel.breakTreaty(Treaty.NON_AGRESSION);
            NationMail offer = new NationMail(UUID.randomUUID(), b.id(), "B", "Sujet", "Corps", Treaty.NON_AGRESSION, 0, today, today + 7);
            a.mail().add(offer);
            w.check(NationService.act(w.ruler, null, NationService.OP_ACCEPT, -1, offer.id()).success() && rel.has(Treaty.NON_AGRESSION, today), "accepter ne signe pas");
            w.check(a.mail().isEmpty(), "la lettre reste après la réponse");

            // Présent : l'or passe, l'opinion monte. Insulte : elle descend.
            a.setGold(500);
            long bBefore = b.gold();
            int opinion = fr.crusadecraft.core.ai.AiBrain.opinion(server, b, a);
            w.check(NationService.act(w.ruler, b.id(), NationService.OP_GIFT, -1, null).success(), "le présent se refuse");
            w.check(a.gold() == 500 - NationService.GIFT && b.gold() == bBefore + NationService.GIFT, "l'or du présent ne passe pas");
            int warmer = fr.crusadecraft.core.ai.AiBrain.opinion(server, b, a);
            w.check(warmer > opinion, "le présent n'améliore rien : " + opinion + " puis " + warmer);
            w.check(NationService.act(w.ruler, b.id(), NationService.OP_INSULT, -1, null).success(), "l'insulte se refuse");
            w.check(fr.crusadecraft.core.ai.AiBrain.opinion(server, b, a) < warmer, "l'insulte ne change rien");

            // Tant que le joueur règne, l'ordinateur ne signe rien pour lui, et les autres lui écrivent. Un royaume
            // ordinaire, sans fichier, lui écrit aussi (trouvé en jeu : il faisait planter la journée).
            AiRealm plain = WorldEditService.addRealm(server, level, "Ordinaire", "", TitleRank.DUCHE, 0x224466, AiFaith.FIDELE, AiPersonality.MARCHAND,
                    w.center.getX() + 1500, w.center.getZ());
            plain.discover(w.ruler.getUUID(), today);
            a.mail().clear();
            rel.breakTreaty(Treaty.COMMERCE);
            rel.setLastAiLetterDay(Long.MIN_VALUE);
            for (long d = 1; d <= 60 && a.mail().isEmpty(); d++) NationService.onNewDay(server, d);
            w.check(!a.mail().isEmpty(), "personne n'écrit au souverain");
            w.check(a.mail().stream().noneMatch(m -> m.body().contains("{") || m.body().equals("…")), "une lettre est vide ou garde des trous : " + a.mail());
            w.check(!rel.has(Treaty.COMMERCE, today), "un traité est signé sans le souverain");

            // Une fâcherie fait tomber son opinion : l'alliance totale se refuse, et le refus dit pourquoi.
            rel.addOpinion(b.id(), fr.crusadecraft.core.character.OpinionModifier.of(fr.crusadecraft.core.character.OpinionReason.INSULTE, today, 8));
            int cold = fr.crusadecraft.core.ai.AiBrain.opinion(server, b, a);
            Outcome no = NationService.act(w.ruler, b.id(), NationService.OP_PROPOSE, Treaty.ALLIANCE.ordinal(), null);
            w.check(!no.success() && !rel.has(Treaty.ALLIANCE, today), "l'alliance totale est signée à " + cold + " d'opinion");

            // Abdiquer rend la main à l'ordinateur.
            w.check(NationService.abdicate(w.ruler).success() && a.sovereign() == null, "abdiquer ne marche pas");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            data.world().clear();
            w.cleanup();
        }
    }
}
