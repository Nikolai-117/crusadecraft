package fr.crusadecraft.gametest;

import com.mojang.authlib.GameProfile;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mise en place commune des GameTests : un joueur fictif, sa colonie MineColonies avec Hôtel de ville,
 * et au besoin son royaume. Tout est défait à la fin du test.
 */
final class TestWorld {
    static final String TEMPLATE = "dalle";

    final GameTestHelper helper;
    final ServerPlayer ruler;
    final BlockPos center;
    final ColonyRef colony;
    Realm realm;
    private final boolean previousTownHallRule;
    private final List<ColonyRef> extraColonies = new ArrayList<>();

    private TestWorld(GameTestHelper helper, String colonyName, int townHallLevel) {
        this.helper = helper;
        this.previousTownHallRule = CrusadeConfig.SERVER.throneMustBeInTownHall.get();
        // Sans plan chargé, le volume de l'Hôtel de ville de test n'existe pas : cette règle est testée à part.
        CrusadeConfig.SERVER.throneMustBeInTownHall.set(false);
        this.ruler = fakePlayer(helper, "souverain");
        this.center = helper.absolutePos(new BlockPos(4, 1, 4));
        ruler.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 2.5);
        this.colony = MineColoniesTestKit.createColony(helper.getLevel(), center, ruler, colonyName, townHallLevel);
    }

    static TestWorld colony(GameTestHelper helper, String name, int townHallLevel) {
        return new TestWorld(helper, name, townHallLevel);
    }

    /** Colonie avec Hôtel de ville niveau 2 et royaume fondé. */
    static TestWorld realm(GameTestHelper helper, String name) {
        TestWorld w = new TestWorld(helper, name, 2);
        w.realm = RealmService.found(w.ruler, new RealmService.FoundingRequest(w.center.east(), name, "", "", 0, false))
                .orElseThrow(() -> new IllegalStateException("fondation impossible dans le test"));
        return w;
    }

    /**
     * Joueur fictif de Forge. Celui de Minecraft 1.20.1 ({@code makeMockServerPlayerInLevel}) n'a pas de
     * canal réseau et plante dès qu'un mod lui envoie un paquet.
     */
    static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        UUID id = UUID.randomUUID();
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, name + "-" + id.toString().substring(0, 4)));
    }

    /** Une autre colonie, hors du royaume, loin de la première. */
    ColonyRef foreignColony(ServerPlayer owner, String name) {
        BlockPos far = center.offset(400, 0, 400);
        ColonyRef ref = MineColoniesTestKit.createColony(helper.getLevel(), far, owner, name, 0);
        extraColonies.add(ref);
        return ref;
    }

    void check(boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    void cleanup() {
        CrusadeSavedData data = CrusadeSavedData.get(helper.getLevel().getServer());
        data.realmOfRuler(ruler.getUUID()).ifPresent(r -> data.remove(r.id()));
        MineColoniesTestKit.deleteColony(colony);
        data.forgetColony(colony);
        for (ColonyRef c : extraColonies) {
            MineColoniesTestKit.deleteColony(c);
            data.forgetColony(c);
        }
        CrusadeConfig.SERVER.throneMustBeInTownHall.set(previousTownHallRule);
    }
}
