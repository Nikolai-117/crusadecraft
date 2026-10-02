package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.council.CouncilService;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.Ledger;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Le trésor et les lois de bout en bout : impôts du jour, Coffre du Trésor, dette, vote du conseil. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TreasuryGameTests {

    private static final ResourceLocation GOLD_INGOT = ResourceLocation.tryParse("minecraft:gold_ingot");
    private static final ResourceLocation GOLD_BLOCK = ResourceLocation.tryParse("minecraft:gold_block");

    private TreasuryGameTests() {}

    private static int lenient() {
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        return previous;
    }

    private static void appoint(TestWorld w, CitizenRef ref, Post post) {
        CharacterService.Outcome out = CharacterService.act(w.ruler, ref, CharacterService.Action.APPOINT, post);
        w.check(out.success(), "nomination impossible : " + out.message().getString());
    }

    private static CharacterRecord record(TestWorld w, CitizenRef ref) {
        return CrusadeSavedData.get(w.helper.getLevel().getServer()).character(ref).orElseThrow();
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p4_impots", timeoutTicks = 100)
    public static void impotsEtSalairesDuJour(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        int previous = lenient();
        // L'XP des postes pourrait faire monter l'Intendance entre la prévision et la perception.
        boolean roleXp = CrusadeConfig.SERVER.roleXp.get();
        CrusadeConfig.SERVER.roleXp.set(false);
        try {
            var server = h.getLevel().getServer();
            CitizenRef steward = MineColoniesTestKit.addCitizen(w.colony);
            CitizenRef mayor = MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, steward, Post.INTENDANT);
            appoint(w, mayor, Post.MAIRE);
            w.check(CouncilService.setTask(w.ruler, Post.INTENDANT, CouncilTask.COLLECTER_IMPOTS).success(), "collecte des impôts refusée");

            TreasuryView before = TreasuryService.view(server, w.realm);
            w.check(before.colonies().size() == 1, "prévision sans la colonie : " + before.colonies());
            long expected = before.colonies().get(0).collected();
            w.check(expected > 0, "aucun impôt prévu (Hôtel de ville niveau 2)");
            w.check(before.salaries() == CrusadeConfig.SERVER.councilSalary.get() + CrusadeConfig.SERVER.mayorSalary.get(),
                    "salaires prévus : " + before.salaries());

            long gold = w.realm.gold();
            long today = RealmService.currentDay(server);
            w.realm.court().setLastProcessedDay(today - 1);
            CouncilService.tick(server);
            Ledger.Day day = w.realm.ledger().peek(today);
            w.check(day.amount(LedgerCategory.IMPOTS) == expected, "impôts perçus " + day.amount(LedgerCategory.IMPOTS) + " au lieu de " + expected);
            w.check(day.amount(LedgerCategory.SALAIRES) == -before.salaries(), "salaires payés : " + day.amount(LedgerCategory.SALAIRES));
            w.check(day.amount(LedgerCategory.DOMAINE) == 0, "le domaine rapporte alors que l'intendant collecte");
            w.check(w.realm.gold() == gold + day.net(), "solde incohérent avec le livre de comptes");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.roleXp.set(roleXp);
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p4_coffre", timeoutTicks = 100)
    public static void depotEtRetraitAuCoffre(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        try {
            var inv = w.ruler.getInventory();
            inv.clearContent();
            inv.add(new ItemStack(Items.GOLD_INGOT, 2));
            inv.add(new ItemStack(Items.GOLD_NUGGET, 5));
            inv.add(new ItemStack(Items.DIRT, 3));
            long start = w.realm.gold();
            CouncilActions.Outcome dep = TreasuryService.depositAll(w.ruler);
            w.check(dep.success(), "dépôt refusé : " + dep.message().getString());
            w.check(w.realm.gold() == start + 23, "solde après dépôt : " + w.realm.gold());
            w.check(inv.countItem(Items.GOLD_INGOT) == 0 && inv.countItem(Items.GOLD_NUGGET) == 0, "l'or est resté dans l'inventaire");
            w.check(inv.countItem(Items.DIRT) == 3, "un objet sans valeur a été pris");
            w.check(!TreasuryService.depositAll(w.ruler).success(), "dépôt à vide accepté");

            CouncilActions.Outcome out = TreasuryService.withdraw(w.ruler, GOLD_INGOT, 1);
            w.check(out.success(), "retrait refusé : " + out.message().getString());
            w.check(w.realm.gold() == start + 14 && inv.countItem(Items.GOLD_INGOT) == 1, "retrait mal compté");
            w.check(!TreasuryService.withdraw(w.ruler, GOLD_BLOCK, 5).success(), "retrait au-delà du solde accepté");
            w.check(w.realm.gold() == start + 14, "un retrait refusé a quand même débité");

            // Inventaire plein : rien n'est débité, rien n'est perdu.
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) inv.setItem(i, new ItemStack(Items.DIRT, 64));
            inv.setItem(inv.findSlotMatchingItem(new ItemStack(Items.GOLD_INGOT)), new ItemStack(Items.DIRT, 64));
            w.check(!TreasuryService.withdraw(w.ruler, GOLD_INGOT, 1).success(), "retrait dans un inventaire plein accepté");
            w.check(w.realm.gold() == start + 14, "inventaire plein : le trésor a été débité");

            Ledger.Day day = w.realm.ledger().peek(RealmService.currentDay(h.getLevel().getServer()));
            w.check(day.amount(LedgerCategory.DEPOTS) == 23 && day.amount(LedgerCategory.RETRAITS) == -9, "livre de comptes : " + day.amounts());
            h.succeed();
        } finally {
            w.ruler.getInventory().clearContent();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p4_dette", timeoutTicks = 100)
    public static void detteEtSalairesImpayes(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        int previous = lenient();
        boolean taxes = CrusadeConfig.SERVER.taxes.get();
        CrusadeConfig.SERVER.taxes.set(false);
        try {
            var server = h.getLevel().getServer();
            CitizenRef marshal = MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, marshal, Post.MARECHAL);
            long limit = TreasuryService.debtLimit();
            w.realm.setGold(-limit);
            int prestige = w.realm.prestige();
            long today = RealmService.currentDay(server);
            w.realm.court().setLastProcessedDay(today - 1);
            CouncilService.tick(server);
            w.check(w.realm.gold() == -limit, "la dette a dépassé la limite : " + w.realm.gold());
            w.check(w.realm.prestige() == prestige - CrusadeConfig.SERVER.debtPrestigeLoss.get(), "prestige après un jour de dette : " + w.realm.prestige());
            boolean unpaid = record(w, marshal).modifiers().stream().anyMatch(m -> m.reason() == OpinionReason.SALAIRE_IMPAYE);
            w.check(unpaid, "le maréchal ne s'est pas plaint de son salaire");
            w.check(!TreasuryService.spend(server, w.realm, LedgerCategory.REQUETES, 1), "dépense choisie acceptée en pleine dette");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.taxes.set(taxes);
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p4_lois", timeoutTicks = 100)
    public static void loiVoteeRejeteeImposee(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Marchefort");
        int previous = lenient();
        try {
            var server = h.getLevel().getServer();
            long today = RealmService.currentDay(server);

            // Sans conseil, le souverain décrète seul ; puis il doit attendre avant d'y retoucher.
            CouncilActions.Outcome decree = LawService.propose(w.ruler, Law.FISCALITE, 2, false);
            w.check(decree.success(), "décret refusé : " + decree.message().getString());
            w.check(w.realm.laws().level(Law.FISCALITE) == 2, "fiscalité non changée");
            w.check(!LawService.propose(w.ruler, Law.FISCALITE, 1, false).success(), "délai entre deux changements ignoré");
            w.check(!LawService.propose(w.ruler, Law.AUTORITE, 2, false).success(), "autorité élevée sans la recherche Chancellerie");

            // Un conseil mécontent rejette une fiscalité plus lourde.
            w.realm.laws().set(Law.FISCALITE, 0, today - 30);
            CitizenRef a = MineColoniesTestKit.addCitizen(w.colony);
            CitizenRef b = MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, a, Post.CHANCELIER);
            appoint(w, b, Post.MARECHAL);
            for (CitizenRef ref : new CitizenRef[]{a, b}) {
                record(w, ref).addModifier(OpinionModifier.of(OpinionReason.REFUS, today, 4));
            }
            CouncilActions.Outcome rejected = LawService.propose(w.ruler, Law.FISCALITE, 1, false);
            w.check(!rejected.success(), "le conseil mécontent a approuvé une hausse d'impôts");
            w.check(w.realm.laws().level(Law.FISCALITE) == 0, "loi changée malgré le rejet");

            w.realm.setPrestige(0);
            w.check(!LawService.propose(w.ruler, Law.FISCALITE, 1, true).success(), "loi imposée sans prestige");
            w.realm.setPrestige(100);
            CouncilActions.Outcome forced = LawService.propose(w.ruler, Law.FISCALITE, 1, true);
            w.check(forced.success(), "loi non imposée : " + forced.message().getString());
            w.check(w.realm.laws().level(Law.FISCALITE) == 1, "fiscalité non imposée");
            int cost = CrusadeConfig.SERVER.forceCostPerVote.get() * 2;
            w.check(w.realm.prestige() == 100 - cost, "prestige après l'avoir imposée : " + w.realm.prestige());
            boolean resent = record(w, a).modifiers().stream().anyMatch(m -> m.reason() == OpinionReason.LOI_IMPOSEE);
            w.check(resent, "le chancelier ne garde pas rancune");
            w.check(LawService.view(server, w.realm).laws().size() == Law.values().length, "vue des lois incomplète");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }
}
