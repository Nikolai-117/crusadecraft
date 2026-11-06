package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.TitleService;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** La diplomatie de bout en bout : hommage d'une seigneurie, titre de Comte, traités, tributs, revendications. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiplomacyGameTests {

    private DiplomacyGameTests() {}

    /** Réglages de test : pas de recherche à l'Université, pigeons instantanés. Renvoie de quoi les rétablir. */
    private static Runnable testSettings() {
        boolean research = CrusadeConfig.SERVER.chanceryNeedsResearch.get();
        int delay = CrusadeConfig.SERVER.letterDelaySeconds.get();
        int refuse = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.chanceryNeedsResearch.set(false);
        CrusadeConfig.SERVER.letterDelaySeconds.set(0);
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        return () -> {
            CrusadeConfig.SERVER.chanceryNeedsResearch.set(research);
            CrusadeConfig.SERVER.letterDelaySeconds.set(delay);
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(refuse);
        };
    }

    private static AiRealm generateAndPick(TestWorld w, TitleRank rank) {
        var server = w.helper.getLevel().getServer();
        WorldService.generate(server, w.helper.getLevel(), w.center.getX(), w.center.getZ());
        AiRealm target = CrusadeSavedData.get(server).world().realms().stream().filter(r -> r.rank() == rank).findFirst()
                // Le monde d'essai est tiré au hasard : il arrive qu'aucun royaume n'ait ce rang. Ce n'est pas ce qu'on teste ici.
                .orElseGet(() -> {
                    AiRealm first = CrusadeSavedData.get(server).world().realms().iterator().next();
                    first.setRank(rank);
                    return first;
                });
        target.discover(w.ruler.getUUID(), RealmService.currentDay(server));
        return target;
    }

    private static void cleanup(TestWorld w, Runnable restore) {
        var data = CrusadeSavedData.get(w.helper.getLevel().getServer());
        if (w.realm != null) data.diplomacy().forget(w.realm.id());
        data.world().clear();
        w.ruler.getInventory().clearContent();
        restore.run();
        w.cleanup();
    }

    private static Letter awaiting(TestWorld w) {
        return w.realm.mail().stream().filter(Letter::needsAnswer).findFirst()
                .orElseThrow(() -> new IllegalStateException("aucune lettre à sceller : " + w.realm.mail().size() + " lettre(s)"));
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p6_hommage", timeoutTicks = 200)
    public static void vassaliserUneSeigneurieEtDevenirComte(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            AiRealm barony = generateAndPick(w, TitleRank.BARONNIE);
            Relation rel = CrusadeSavedData.get(server).diplomacy().between(barony.id(), w.realm.id());
            long today = RealmService.currentDay(server);
            for (int i = 0; i < 4; i++) rel.addOpinion(barony.id(), OpinionModifier.of(OpinionReason.CADEAU, today, 3));
            w.realm.setDread(40);

            CouncilActions.Outcome sent = DiplomacyService.propose(w.ruler, barony.id(), DiplomacyRules.Proposal.VASSALISATION, 10, 10);
            w.check(sent.success(), "proposition refusée d'emblée : " + sent.message().getString());
            Letter letter = awaiting(w);
            w.check(letter.choices().get(0).action() == Letter.Action.SEAL_VASSALAGE, "la seigneurie n'accepte pas l'hommage");

            // Le courrier se prend au Colombier ; sans sceau, impossible de sceller.
            w.check(DiplomacyService.takeMail(w.ruler).success(), "courrier non remis");
            w.check(w.ruler.getInventory().countItem(ModItems.LETTER.get()) >= 1, "pas de lettre dans l'inventaire");
            w.check(!DiplomacyService.answer(w.ruler, letter.id(), 0).success(), "scellé sans Sceau royal");
            w.ruler.getInventory().add(new ItemStack(ModItems.SEAL.get()));
            CouncilActions.Outcome sealed = DiplomacyService.answer(w.ruler, letter.id(), 0);
            w.check(sealed.success(), "hommage non scellé : " + sealed.message().getString());
            w.check(CrusadeSavedData.get(server).diplomacy().vassalsOf(w.realm.id()).contains(barony.id()), "la seigneurie n'est pas vassale");
            w.check(rel.vassalTax() == 10 && rel.vassalLevy() == 10, "contrat : " + rel.vassalTax() + " / " + rel.vassalLevy());

            // Deux provinces : la capitale et la seigneurie. Le titre de Comte se crée sous le sceau.
            w.check(TitleService.provinces(server, w.realm) == 2, "provinces : " + TitleService.provinces(server, w.realm));
            w.realm.setGold(1000);
            w.realm.setPrestige(500);
            CouncilActions.Outcome title = TitleService.createTitle(w.ruler);
            w.check(title.success(), "titre refusé : " + title.message().getString());
            w.check(w.realm.rank() == TitleRank.COMTE, "rang : " + w.realm.rank());
            int gold = CrusadeConfig.SERVER.titleGold.get().get(0);
            w.check(w.realm.gold() == 1000 - gold, "or après le titre : " + w.realm.gold());

            // Le vassal verse sa part chaque jour.
            barony.setLastIncome(50);
            barony.setGold(500);
            DiplomacyService.onNewDay(server, w.realm, today);
            w.check(w.realm.ledger().peek(today).amount(LedgerCategory.VASSAUX) == 5, "impôt du vassal : "
                    + w.realm.ledger().peek(today).amount(LedgerCategory.VASSAUX));
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p6_traites", timeoutTicks = 200)
    public static void cadeauInsulteTraiteEtTribut(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            AiRealm county = generateAndPick(w, TitleRank.COMTE);
            int before = DiplomacyService.opinion(server, county, w.realm);
            w.realm.setGold(200);
            w.check(DiplomacyService.gift(w.ruler, county.id(), 50).success(), "cadeau refusé");
            int afterGift = DiplomacyService.opinion(server, county, w.realm);
            w.check(afterGift > before, "le cadeau n'a rien changé (" + before + " → " + afterGift + ")");
            w.check(w.realm.gold() == 150, "or après le cadeau : " + w.realm.gold());
            w.check(DiplomacyService.insult(w.ruler, county.id()).success(), "insulte refusée");
            w.check(DiplomacyService.opinion(server, county, w.realm) < afterGift, "l'insulte n'a rien changé");

            // Un pacte de non-agression accepté, puis scellé.
            Relation rel = CrusadeSavedData.get(server).diplomacy().between(county.id(), w.realm.id());
            long today = RealmService.currentDay(server);
            for (int i = 0; i < 6; i++) rel.addOpinion(county.id(), OpinionModifier.of(OpinionReason.CADEAU, today, 3));
            w.realm.mail().clear();
            w.check(DiplomacyService.propose(w.ruler, county.id(), DiplomacyRules.Proposal.NON_AGRESSION, 20, 20).success(), "proposition non envoyée");
            Letter letter = awaiting(w);
            w.ruler.getInventory().add(new ItemStack(ModItems.SEAL.get()));
            w.check(DiplomacyService.answer(w.ruler, letter.id(), 0).success(), "traité non scellé");
            w.check(rel.has(Treaty.NON_AGRESSION, today), "pas de pacte après le sceau");

            // Un tribut versé au joueur chaque jour.
            rel.setTribute(county.id(), 6, today + 3);
            county.setGold(100);
            DiplomacyService.onNewDay(server, w.realm, today);
            w.check(w.realm.ledger().peek(today).amount(LedgerCategory.TRIBUTS) == 6, "tribut non perçu");
            w.check(county.gold() == 94, "or du royaume IA : " + county.gold());
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p6_revendication", timeoutTicks = 200)
    public static void revendicationParLeChancelier(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            AiRealm county = generateAndPick(w, TitleRank.COMTE);
            int province = county.capital().id();
            CitizenRef chancellor = MineColoniesTestKit.addCitizen(w.colony);
            w.check(CharacterService.act(w.ruler, chancellor, CharacterService.Action.APPOINT, Post.CHANCELIER).success(), "chancelier non nommé");
            CouncilActions.Outcome started = TitleService.fabricateClaim(w.ruler, province);
            w.check(started.success(), "revendication non lancée : " + started.message().getString());
            w.check(w.realm.court().task(Post.CHANCELIER) == CouncilTask.FABRIQUER_REVENDICATION, "tâche du chancelier non changée");
            w.realm.court().setClaimProgress(95);
            w.check(TitleService.advanceClaim(server, w.realm, 30, 1.0), "la revendication n'aboutit pas");
            w.check(w.realm.claims().contains(province), "revendication non enregistrée");
            w.check(DiplomacyService.takeMail(w.ruler).success(), "courrier non remis");
            w.check(w.ruler.getInventory().countItem(ModItems.CLAIM_SCROLL.get()) == 1, "pas de parchemin de revendication");
            w.check(!TitleService.fabricateClaim(w.ruler, province).success(), "revendiquée deux fois");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    /** Pour lire la carte d'un test sans ouvrir d'écran. */
    static Optional<AiRealm> any(TestWorld w) {
        return CrusadeSavedData.get(w.helper.getLevel().getServer()).world().realms().stream().findFirst();
    }
}
