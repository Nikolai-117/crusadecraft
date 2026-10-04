package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.logic.FoundingRules.Verdict;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.RealmState;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Fondation d'un royaume, de bout en bout, avec de vraies colonies MineColonies créées dans le
 * monde jetable du serveur de test ({@code ./gradlew runGameTestServer}).
 *
 * <p>Chaque test a son propre lot (batch) : les lots passent l'un après l'autre, ce qui évite que les
 * territoires de deux colonies de test se chevauchent.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundingGameTests {
    private static final String TEMPLATE = "dalle";

    private FoundingGameTests() {}

    /** Prépare un joueur fictif, propriétaire d'une colonie au centre de la dalle. */
    private record Setup(ServerPlayer owner, BlockPos center, ColonyRef colony, boolean previousTownHallRule) {}

    private static Setup setup(GameTestHelper h, String name, int townHallLevel) {
        boolean previous = CrusadeConfig.SERVER.throneMustBeInTownHall.get();
        // Sans plan chargé, le volume de l'Hôtel de ville de test n'existe pas : on teste la règle du volume à part.
        CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
        ServerPlayer owner = fakePlayer(h, "fondateur");
        BlockPos center = h.absolutePos(new BlockPos(4, 1, 4));
        owner.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 2.5);
        ColonyRef colony = MineColoniesTestKit.createColony(h.getLevel(), center, owner, name, townHallLevel);
        return new Setup(owner, center, colony, previous);
    }

    /**
     * Joueur fictif de Forge. Celui de Minecraft 1.20.1 ({@code makeMockServerPlayerInLevel}) n'a pas de
     * canal réseau et plante dès qu'un mod lui envoie un paquet.
     */
    private static ServerPlayer fakePlayer(GameTestHelper h, String name) {
        UUID id = UUID.randomUUID();
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(id, name + "-" + id.toString().substring(0, 4)));
    }

    private static void cleanup(GameTestHelper h, Setup s) {
        CrusadeSavedData data = CrusadeSavedData.get(h.getLevel().getServer());
        data.realmOfRuler(s.owner().getUUID()).ifPresent(r -> data.remove(r.id()));
        MineColoniesTestKit.deleteColony(s.colony());
        CrusadeConfig.SERVER.throneMustBeInTownHall.set(s.previousTownHallRule());
    }

    private static void check(GameTestHelper h, boolean condition, String message) {
        if (!condition) h.fail(message);
    }

    @GameTest(template = TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p1_fondation", timeoutTicks = 100)
    public static void fondationComplete(GameTestHelper h) {
        Setup s = setup(h, "Valbrume", 2);
        try {
            BlockPos throne = s.center().east();
            RealmService.FoundingCheck first = RealmService.checkFounding(s.owner(), throne);
            check(h, first.verdict() == Verdict.OK, "fondation refusée : " + first.verdict());
            check(h, s.colony().equals(first.colony()), "mauvaise colonie : " + first.colony());

            Optional<Realm> founded = RealmService.found(s.owner(), new RealmService.FoundingRequest(
                    throne, "  Royaume §6de   Valbrume ", "Par le fer", "", 3, true));
            check(h, founded.isPresent(), "le royaume n'a pas été fondé");
            Realm realm = founded.get();
            check(h, realm.name().equals("Royaume de Valbrume"), "nom mal nettoyé : « " + realm.name() + " »");
            check(h, realm.house().equals(s.owner().getGameProfile().getName()), "maison par défaut : " + realm.house());
            check(h, realm.color() == Heraldry.byOrdinal(3).rgb(), "couleur");
            check(h, realm.feminineTitle(), "forme féminine perdue");
            check(h, realm.rank() == TitleRank.BARONNIE, "rang initial : " + realm.rank());
            check(h, realm.state() == RealmState.ACTIVE, "état : " + realm.state());
            check(h, realm.capital().equals(s.colony()), "capitale : " + realm.capital());
            check(h, realm.prestige() == CrusadeConfig.SERVER.startingPrestige.get(), "prestige de départ");

            RealmView view = RealmService.view(h.getLevel().getServer(), realm);
            check(h, view.colonies().size() == 1, "domaine : " + view.colonies().size());
            check(h, view.capital().townHallLevel() == 2, "niveau d'Hôtel de ville lu : " + view.capital().townHallLevel());
            check(h, view.capital().name().equals("Valbrume"), "nom de colonie lu : " + view.capital().name());

            RealmService.FoundingCheck second = RealmService.checkFounding(s.owner(), throne);
            check(h, second.verdict() == Verdict.ALREADY_RULER, "second royaume accepté : " + second.verdict());
            h.succeed();
        } finally {
            cleanup(h, s);
        }
    }

    @GameTest(template = TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p1_niveau", timeoutTicks = 100)
    public static void hotelDeVilleTropBas(GameTestHelper h) {
        Setup s = setup(h, "Brumeval", 1);
        try {
            RealmService.FoundingCheck c = RealmService.checkFounding(s.owner(), s.center().east());
            check(h, c.verdict() == Verdict.TOWN_HALL_TOO_LOW, "attendu TOWN_HALL_TOO_LOW, obtenu " + c.verdict());
            Optional<Realm> founded = RealmService.found(s.owner(), new RealmService.FoundingRequest(
                    s.center().east(), "Brumeval", "", "", 0, false));
            check(h, founded.isEmpty(), "fondé malgré un Hôtel de ville trop bas");
            h.succeed();
        } finally {
            cleanup(h, s);
        }
    }

    @GameTest(template = TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p1_proprietaire", timeoutTicks = 100)
    public static void seulLeProprietaireFonde(GameTestHelper h) {
        Setup s = setup(h, "Hautemarche", 3);
        try {
            ServerPlayer intrus = fakePlayer(h, "intrus");
            intrus.setPos(s.center().getX() + 0.5, s.center().getY(), s.center().getZ() + 2.5);
            RealmService.FoundingCheck c = RealmService.checkFounding(intrus, s.center().east());
            check(h, c.verdict() == Verdict.NOT_OWNER, "attendu NOT_OWNER, obtenu " + c.verdict());
            h.succeed();
        } finally {
            cleanup(h, s);
        }
    }

    @GameTest(template = TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p1_hors_colonie", timeoutTicks = 100)
    public static void horsColonie(GameTestHelper h) {
        ServerPlayer joueur = fakePlayer(h, "voyageur");
        BlockPos pos = h.absolutePos(new BlockPos(4, 1, 4));
        RealmService.FoundingCheck c = RealmService.checkFounding(joueur, pos);
        check(h, c.verdict() == Verdict.NOT_IN_COLONY, "attendu NOT_IN_COLONY, obtenu " + c.verdict());
        h.succeed();
    }

    /**
     * Supprimer la colonie capitale met le royaume en sommeil sans le détruire : c'est l'événement
     * MineColonies qui déclenche la réconciliation, au tick suivant.
     */
    @GameTest(template = TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p1_sommeil", timeoutTicks = 100)
    public static void capitaleSupprimeeMetEnSommeil(GameTestHelper h) {
        Setup s = setup(h, "Pierrefeu", 2);
        Optional<Realm> founded = RealmService.found(s.owner(), new RealmService.FoundingRequest(
                s.center().east(), "Pierrefeu", "", "", 1, false));
        if (founded.isEmpty()) {
            cleanup(h, s);
            h.fail("fondation impossible");
            return;
        }
        Realm realm = founded.get();
        MineColoniesTestKit.deleteColony(s.colony());
        h.runAfterDelay(5, () -> {
            try {
                check(h, realm.state() == RealmState.DORMANT_NO_CAPITAL, "état après suppression : " + realm.state());
                check(h, CrusadeSavedData.get(h.getLevel().getServer()).realm(realm.id()).isPresent(),
                        "le royaume a été supprimé au lieu d'être mis en sommeil");
                h.succeed();
            } finally {
                cleanup(h, s);
            }
        });
    }
}
