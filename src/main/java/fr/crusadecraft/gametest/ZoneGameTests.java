package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.core.world.ZoneService;
import fr.crusadecraft.data.CrusadeSavedData;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * Les zones dessinées d'avance (P33) : un Trône posé dans la zone d'un royaume dessiné le reprend, deux royaumes de
 * l'ordinateur fusionnent, et une colonie fondée dans la zone du royaume s'y rattache d'elle-même.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ZoneGameTests {

    private ZoneGameTests() {}

    private static long[] square(int x, int z, int r) {
        LongArrayList out = new LongArrayList();
        int cx = TerritoryMap.cellOf(x), cz = TerritoryMap.cellOf(z);
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) out.add(TerritoryMap.key(cx + dx, cz + dz));
        return out.toLongArray();
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p33_zones", timeoutTicks = 300)
    public static void reprendreFusionnerRattacher(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        var dim = level.dimension().location();
        AiRealm drawn = null, rochegarde = null, valcourt = null;
        ColonyRef second = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);

            // Un royaume dessiné d'avance, avec une ville, et sa zone peinte jusque sous la colonie du joueur.
            drawn = WorldEditService.addRealm(server, level, "Levànzia", "", TitleRank.ROYAUME, 0x5B3F8C, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 300, w.center.getZ());
            WorldEditService.addCity(server, level, drawn.id(), "Khalyos", WorldEditService.CitySize.VILLE, w.center.getX() + 200, w.center.getZ() + 60);
            data.world().borders().paint(dim, square(w.center.getX(), w.center.getZ(), 4), drawn.id());
            Optional<AiRealm> adopted = ZoneService.adoptAtFounding(server, w.realm);
            w.check(adopted.isPresent(), "le Trône posé dans la zone ne reprend pas le royaume dessiné");
            w.check(data.world().borders().at(dim, w.center.getX(), w.center.getZ()).map(w.realm.id()::equals).orElse(false),
                    "la zone peinte ne passe pas au joueur");
            final AiRealm levanzia = drawn;
            w.check(levanzia.provinces().stream().allMatch(p -> w.realm.id().equals(p.holder())), "les villes du royaume dessiné ne passent pas au joueur");
            w.check(levanzia.landless(), "le royaume dessiné garde des terres");

            // Deux royaumes de l'ordinateur fusionnent : Valcourt passe sous Rochegarde.
            rochegarde = WorldEditService.addRealm(server, level, "Rochegarde", "", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1200, w.center.getZ());
            valcourt = WorldEditService.addRealm(server, level, "Valcourt", "", TitleRank.COMTE, 0xB3261E, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1600, w.center.getZ());
            WorldEditService.addCity(server, level, valcourt.id(), "Saulepré", WorldEditService.CitySize.BOURG, w.center.getX() + 1700, w.center.getZ() + 80);
            int before = rochegarde.provinces().size(), absorbed = valcourt.provinces().size();
            Outcome merged = ZoneService.merge(w.ruler, valcourt.id(), rochegarde.id());
            w.check(merged.success(), "fusion refusée : " + merged.message().getString());
            w.check(data.world().realm(valcourt.id()).isEmpty(), "le royaume fusionné existe encore");
            w.check(rochegarde.provinces().size() == before + absorbed, "les villes de Valcourt ne passent pas à Rochegarde");
            valcourt = null;

            // Une colonie fondée dans la zone du royaume s'y rattache d'elle-même.
            BlockPos there = w.center.offset(400, 0, 400);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk((there.getX() >> 4) + dx, (there.getZ() >> 4) + dz);
            second = MineColoniesTestKit.createColony(level, there, w.ruler, "Clairval", 1);
            w.check(second != null && !w.realm.holds(second), "la seconde colonie n'est pas créée, ou déjà rattachée");
            int none = ZoneService.autoAttach(server);
            w.check(!w.realm.holds(second), "une colonie hors de la zone se rattache (" + none + ")");
            data.world().borders().paint(dim, square(there.getX(), there.getZ(), 3), w.realm.id());
            ZoneService.autoAttach(server);
            w.check(w.realm.holds(second), "la colonie fondée dans la zone ne se rattache pas");
            h.succeed();
        } finally {
            if (second != null) {
                w.realm.removeProvince(second);
                MineColoniesTestKit.deleteColony(second);
                data.forgetColony(second);
            }
            for (AiRealm r : new AiRealm[]{drawn, rochegarde, valcourt}) if (r != null) WorldEditService.removeRealm(server, r.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
