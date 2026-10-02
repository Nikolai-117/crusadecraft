package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterService.Action;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * Personnages de bout en bout, sur de vrais citoyens MineColonies : fiche, nomination, titres,
 * récompense, prison, exil, mort et disparition.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharacterGameTests {

    private CharacterGameTests() {}

    private static CharacterRecord record(TestWorld w, CitizenRef ref) {
        return CrusadeSavedData.get(w.helper.getLevel().getServer()).character(ref).orElseThrow();
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_fiche", timeoutTicks = 100)
    public static void ficheDepuisLesCompetences(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        try {
            CitizenRef aldric = MineColoniesTestKit.addCitizen(w.colony);
            Optional<CharacterView> view = CharacterService.view(h.getLevel().getServer(), aldric, w.ruler);
            w.check(view.isPresent(), "fiche introuvable");
            CharacterView v = view.get();
            w.check(v.traits().size() == CrusadeConfig.SERVER.traitsPerCharacter.get(), "traits : " + v.traits());
            w.check(v.skills().length == 11, "compétences lues : " + v.skills().length);
            for (int s : v.stats()) w.check(s >= 0, "aptitude négative");
            w.check(v.canAct(), "le souverain doit pouvoir agir");
            w.check(v.hasOpinion(), "l'opinion doit être calculée");
            w.check(v.slots().size() == Post.values().length, "postes proposés : " + v.slots().size());
            // Les traits sont stables d'une consultation à l'autre.
            CharacterView again = CharacterService.view(h.getLevel().getServer(), aldric, w.ruler).orElseThrow();
            w.check(again.traits().equals(v.traits()), "les traits ont changé");
            h.succeed();
        } finally {
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_nomination", timeoutTicks = 100)
    public static void nominationEtTitres(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        try {
            var server = h.getLevel().getServer();
            CitizenRef aldric = MineColoniesTestKit.addCitizen(w.colony);
            CitizenRef berthe = MineColoniesTestKit.addCitizen(w.colony);
            CharacterService.Outcome first = CharacterService.act(w.ruler, aldric, Action.APPOINT, Post.MARECHAL);
            w.check(first.success(), "nomination refusée : " + first.message().getString());
            w.check(aldric.equals(w.realm.council().get(Post.MARECHAL)), "le maréchal n'est pas enregistré");
            w.check(CharacterService.isDignitary(server, aldric), "pas reconnu comme dignitaire");
            boolean titled = CharacterService.titles(server).stream().map(SyncTitlesPacket.Entry::ref).anyMatch(aldric::equals);
            w.check(titled, "aucun titre à afficher pour le maréchal");

            CharacterService.Outcome second = CharacterService.act(w.ruler, berthe, Action.APPOINT, Post.MARECHAL);
            w.check(second.success(), "seconde nomination refusée");
            w.check(berthe.equals(w.realm.council().get(Post.MARECHAL)), "la remplaçante n'est pas en poste");
            w.check(!CharacterService.isDignitary(server, aldric), "l'ancien maréchal garde son titre");
            boolean resentful = record(w, aldric).modifiers().stream().anyMatch(m -> m.reason() == OpinionReason.DESTITUE);
            w.check(resentful, "l'ancien maréchal devrait garder rancune");

            CharacterService.Outcome mayor = CharacterService.act(w.ruler, aldric, Action.APPOINT, Post.MAIRE);
            w.check(mayor.success() && aldric.equals(w.realm.mayors().get(w.colony)), "maire non nommé");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_refus", timeoutTicks = 100)
    public static void refusSousLeSeuil(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Brumeval");
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(100);
        try {
            CitizenRef c = MineColoniesTestKit.addCitizen(w.colony);
            CharacterService.Outcome out = CharacterService.act(w.ruler, c, Action.APPOINT, Post.CHANCELIER);
            boolean ambitious = record(w, c).traits().contains(Trait.AMBITIEUX);
            // Un ambitieux accepte toujours ; les autres refusent quand l'exigence est inatteignable.
            w.check(out.success() == ambitious, "résultat inattendu (ambitieux : " + ambitious + ")");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_recompense", timeoutTicks = 100)
    public static void recompensePrendLObjet(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        try {
            CitizenRef c = MineColoniesTestKit.addCitizen(w.colony);
            CharacterService.Outcome without = CharacterService.act(w.ruler, c, Action.REWARD, null);
            w.check(!without.success(), "récompense sans or acceptée");
            w.ruler.getInventory().add(new ItemStack(Items.GOLD_INGOT, 2));
            CharacterService.Outcome with = CharacterService.act(w.ruler, c, Action.REWARD, null);
            w.check(with.success(), "récompense refusée : " + with.message().getString());
            int left = w.ruler.getInventory().countItem(Items.GOLD_INGOT);
            w.check(left == 1, "lingots restants : " + left);
            boolean rewarded = record(w, c).modifiers().stream().anyMatch(m -> m.reason() == OpinionReason.RECOMPENSE);
            w.check(rewarded, "motif de récompense absent");
            h.succeed();
        } finally {
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_prison", timeoutTicks = 100)
    public static void prisonEtExil(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Roncevaux");
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        try {
            CitizenRef c = MineColoniesTestKit.addCitizen(w.colony);
            CharacterService.act(w.ruler, c, Action.APPOINT, Post.INTENDANT);
            int dreadBefore = w.realm.dread();
            w.check(CharacterService.act(w.ruler, c, Action.IMPRISON, null).success(), "emprisonnement refusé");
            w.check(w.realm.postOf(c) == null, "un prisonnier garde son poste");
            w.check(record(w, c).status() == CharacterStatus.EMPRISONNE, "statut de prisonnier absent");
            w.check(w.realm.dread() == dreadBefore + 5, "crainte : " + w.realm.dread());
            w.check(!CharacterService.act(w.ruler, c, Action.APPOINT, Post.INTENDANT).success(), "nomination d'un prisonnier");
            w.check(CharacterService.act(w.ruler, c, Action.RELEASE, null).success(), "libération refusée");
            w.check(CharacterService.act(w.ruler, c, Action.EXILE, null).success(), "exil refusé");
            CharacterRecord rec = record(w, c);
            w.check(rec.status() == CharacterStatus.EXILE && rec.statusUntilDay() > 0, "exil mal enregistré");
            w.check(CharacterService.act(w.ruler, c, Action.RECALL, null).success(), "rappel refusé");
            w.check(record(w, c).status() == CharacterStatus.LIBRE, "toujours en exil");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_mort", timeoutTicks = 100)
    public static void mortEtDisparitionLiberentLePoste(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Combegrise");
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        try {
            var server = h.getLevel().getServer();
            CitizenRef mort = MineColoniesTestKit.addCitizen(w.colony);
            CitizenRef parti = MineColoniesTestKit.addCitizen(w.colony);
            CharacterService.act(w.ruler, mort, Action.APPOINT, Post.CHAPELAIN);
            CharacterService.act(w.ruler, parti, Action.APPOINT, Post.MAITRE_ESPION);

            CharacterService.onCitizenDied(server, mort, "Défunt");
            w.check(w.realm.council().get(Post.CHAPELAIN) == null, "le poste du défunt n'est pas libéré");
            w.check(record(w, mort).deceased(), "le défunt n'est pas marqué");

            MineColoniesTestKit.removeCitizen(parti);
            CharacterService.periodic(server);
            w.check(w.realm.council().get(Post.MAITRE_ESPION) == null, "le poste du disparu n'est pas libéré");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p2_etranger", timeoutTicks = 100)
    public static void unEtrangerNObeitPas(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valombre");
        try {
            ServerPlayer voisin = TestWorld.fakePlayer(h, "voisin");
            CitizenRef etranger = MineColoniesTestKit.addCitizen(w.foreignColony(voisin, "Terres voisines"));
            CharacterService.Outcome out = CharacterService.act(w.ruler, etranger, Action.REPRIMAND, null);
            w.check(!out.success(), "un citoyen étranger a été réprimandé");
            CharacterView view = CharacterService.view(h.getLevel().getServer(), etranger, w.ruler).orElseThrow();
            w.check(!view.canAct() && !view.hasOpinion(), "fiche étrangère mal qualifiée");
            h.succeed();
        } finally {
            w.cleanup();
        }
    }
}
