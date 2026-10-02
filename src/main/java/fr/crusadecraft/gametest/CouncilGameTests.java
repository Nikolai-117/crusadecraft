package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.block.CourtBlocks;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilService;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.council.DutyOrders;
import fr.crusadecraft.core.council.Request;
import fr.crusadecraft.core.council.RequestService;
import fr.crusadecraft.core.council.RequestType;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

/** Le conseil de bout en bout : table, tâches, effets du jour, requêtes, et un citoyen qui se déplace. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouncilGameTests {

    private CouncilGameTests() {}

    private static void appoint(TestWorld w, CitizenRef ref, Post post) {
        CharacterService.Outcome out = CharacterService.act(w.ruler, ref, CharacterService.Action.APPOINT, post);
        w.check(out.success(), "nomination impossible : " + out.message().getString());
    }

    private static int lenient() {
        int previous = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        return previous;
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p3_table", timeoutTicks = 100)
    public static void tableTachesEtConvocation(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        int previous = lenient();
        try {
            BlockPos table = w.center.north(2);
            CourtBlocks.registerTable(w.ruler, h.getLevel(), table);
            w.check(w.realm.court().hasTable() && w.realm.court().tablePos().equals(table), "table non inscrite");
            w.check(CouncilService.setTask(w.ruler, Post.CHAPELAIN, CouncilTask.LEGITIMITE).success(), "tâche disponible refusée");
            w.check(!CouncilService.setTask(w.ruler, Post.CHAPELAIN, CouncilTask.RECHERCHE).success(), "tâche à venir acceptée");
            w.check(!CouncilService.setTask(w.ruler, Post.MARECHAL, CouncilTask.LEGITIMITE).success(), "tâche d'un autre poste acceptée");
            w.check(!CouncilService.convene(w.ruler).success(), "conseil vide convoqué");

            CitizenRef marshal = MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, marshal, Post.MARECHAL);
            w.check(CouncilService.convene(w.ruler).success(), "convocation refusée");
            w.check(!CouncilService.convene(w.ruler).success(), "deuxième convocation le même jour");
            CouncilService.tick(h.getLevel().getServer());
            DutyOrders.Order order = DutyOrders.get(marshal);
            w.check(order != null && order.kind() == DutyOrders.Kind.MEETING, "aucun ordre de réunion : " + order);
            w.check(CouncilService.view(h.getLevel().getServer(), w.realm, null).hasTable(), "vue sans table");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            CouncilService.onServerStopped();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p3_jour", timeoutTicks = 100)
    public static void effetsDuJour(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        int previous = lenient();
        try {
            var server = h.getLevel().getServer();
            CitizenRef intendant = MineColoniesTestKit.addCitizen(w.colony);
            CitizenRef chapelain = MineColoniesTestKit.addCitizen(w.colony);
            for (int i = 0; i < 4; i++) MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, intendant, Post.INTENDANT);
            appoint(w, chapelain, Post.CHAPELAIN);
            CouncilService.setTask(w.ruler, Post.CHAPELAIN, CouncilTask.LEGITIMITE);
            long gold = w.realm.gold();
            int piety = w.realm.piety();
            // On fait comme si la veille n'avait pas encore été comptée.
            w.realm.court().setLastProcessedDay(RealmService.currentDay(server) - 1);
            CouncilService.tick(server);
            w.check(w.realm.gold() > gold, "l'intendant n'a rien rapporté (" + gold + " → " + w.realm.gold() + ")");
            w.check(w.realm.piety() > piety, "le chapelain n'a pas apporté de piété");
            w.check(w.realm.court().lastProcessedDay() == RealmService.currentDay(server), "jour non marqué comme traité");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p3_requetes", timeoutTicks = 100)
    public static void requetesDuMaire(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        int previous = lenient();
        try {
            var server = h.getLevel().getServer();
            CitizenRef mayor = MineColoniesTestKit.addCitizen(w.colony);
            for (int i = 0; i < 3; i++) MineColoniesTestKit.addCitizen(w.colony);
            appoint(w, mayor, Post.MAIRE);
            // Les nouveaux citoyens n'ont pas de maison : le maire doit s'en plaindre.
            RequestService.periodic(server);
            Optional<Request> homeless = w.realm.court().requests().stream()
                    .filter(r -> r.type() == RequestType.MAYOR_HOMELESS && r.from().equals(mayor)).findFirst();
            w.check(homeless.isPresent(), "aucune requête pour les sans-logis : " + w.realm.court().requests());
            w.check(RequestService.answer(w.ruler, homeless.get().id(), RequestType.Choice.PROMISE).success(), "réponse refusée");
            w.check(w.realm.court().requests().stream().noneMatch(r -> r.id().equals(homeless.get().id())), "requête non retirée");
            boolean promised = CrusadeSavedData.get(server).character(mayor).orElseThrow().modifiers().stream()
                    .anyMatch(m -> m.reason() == OpinionReason.PROMESSE);
            w.check(promised, "le maire n'a pas retenu la promesse");

            Request sick = new Request(UUID.randomUUID(), RequestType.MAYOR_SICK, mayor, "Pierrefeu", 2, RealmService.currentDay(server));
            w.realm.court().requests().add(sick);
            w.realm.setGold(0);
            w.check(!RequestService.answer(w.ruler, sick.id(), RequestType.Choice.PAY_HEALER).success(), "guérisseur payé sans or");
            w.realm.setGold(7);
            w.check(RequestService.answer(w.ruler, sick.id(), RequestType.Choice.PAY_HEALER).success(), "guérisseur refusé avec assez d'or");
            w.check(w.realm.gold() == 2, "or restant : " + w.realm.gold());
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(previous);
            w.cleanup();
        }
    }

    /**
     * Un vrai citoyen, avec son entité : on lui donne l'ordre de rejoindre un point, et il doit y marcher
     * par l'état « service » ajouté à son IA MineColonies.
     */
    @GameTest(template = TestWorld.TEMPLATE, batch = "p3_marche", timeoutTicks = 500, setupTicks = 20)
    public static void unCitoyenRejointLaReunion(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Marchefort");
        CitizenRef walker = MineColoniesTestKit.addCitizen(w.colony);
        Optional<Entity> entity = MineColoniesTestKit.spawnCitizen(walker, h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)));
        if (entity.isEmpty()) {
            w.cleanup();
            h.fail("MineColonies n'a pas fait apparaître le citoyen");
            return;
        }
        BlockPos target = h.absolutePos(new BlockPos(7, 1, 7));
        DutyOrders.give(walker, new DutyOrders.Order(DutyOrders.Kind.MEETING, h.getLevel().dimension().location(), target,
                null, target, null, null, h.getLevel().getGameTime() + 2000));
        // Vérification toutes les secondes ; la colonie est toujours défaite avant de conclure.
        int[] checks = {0};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            double d = entity.get().distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            boolean arrived = d <= 3.2 * 3.2;
            if (arrived || ++checks[0] >= 18) {
                DutyOrders.cancel(walker);
                entity.get().discard();
                w.cleanup();
                if (arrived) h.succeed();
                else h.fail("le citoyen n'est pas arrivé (distance² " + Math.round(d) + ")");
                return;
            }
            h.runAfterDelay(20, poll[0]);
        };
        h.runAfterDelay(20, poll[0]);
    }
}
