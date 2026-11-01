package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ai.VillageService;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.build.SiteService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Random;

/**
 * Les villages de l'ordinateur (P28) : un royaume fonde un hameau près des terres d'un joueur (dispute), le hameau
 * s'achève avec ses habitants au registre, grandit d'un quartier, et tout se défait.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGameTests {

    private VillageGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p28_villages", timeoutTicks = 600)
    public static void unVillageNaitGranditEtSeDefait(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        AiRealm ai = null;
        try {
            data.world().clear();
            data.world().markGenerated(RealmService.currentDay(server));
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            int px = w.center.getX() + 290, pz = w.center.getZ();
            for (int dx = -14; dx <= 14; dx++) for (int dz = -6; dz <= 6; dz++) level.getChunk((px >> 4) + dx, (pz >> 4) + dz);
            ai = WorldEditService.addRealm(server, level, "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 420, pz);

            // Une grande parcelle d'herbe plate : le village et toutes les places de ses quartiers.
            int y0 = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(px, 0, pz)).getY();
            for (int dx = -31; dx <= 31; dx++) {
                for (int dz = -31; dz <= 31; dz++) {
                    for (int dy = -3; dy <= 14; dy++) {
                        level.setBlock(new BlockPos(px + dx, y0 + dy, pz + dz), dy < -1 ? Blocks.DIRT.defaultBlockState()
                                : dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }

            Optional<Site> found = VillageService.foundAt(server, ai, level, px, pz, 0);
            if (found.isEmpty()) {
                String why = fr.crusadecraft.core.ai.CivService.refusal(server, level, ai, SiteKind.VILLAGE, px, pz, 0);
                w.check(false, "le royaume ne fonde pas de village sur une parcelle plate de ses terres : "
                        + (why == null ? "chantier refusé (place prise ou ville impossible)" : why) + " ; royaume " + ai.id());
            }
            Site village = found.get();
            w.check(village.auto() && village.kind() == SiteKind.VILLAGE && village.province() >= 0, "le village n'est pas un chantier de l'ordinateur");
            AiProvince city = ai.provinces().stream().filter(p -> p.id() == village.province()).findFirst().orElse(null);
            w.check(city != null && city.name().equals(village.name()), "le village n'a pas sa ville sur la carte");
            int population = city.population();
            int residents = data.lineage().residents(village.community()).size();
            w.check(residents >= VillageService.FOUNDING_FAMILIES, "le village n'a pas ses familles au registre : " + residents);
            w.check(data.world().news().stream().anyMatch(n -> n.key().equals("crusadecraft.news.dispute")),
                    "fonder près des terres du joueur ne fait pas de dispute");

            for (int i = 0; i < 20 && !village.built(); i++) SiteService.advanceForTests(level, village, 100000);
            w.check(village.built(), "le village ne s'achève pas");

            // Un quartier : deux maisons, des habitants de plus.
            Optional<Site> quarter = VillageService.growAt(server, ai, village, new Random(0));
            w.check(quarter.isPresent(), "le village ne grandit pas");
            w.check(quarter.get().province() == village.province() && quarter.get().auto(), "le quartier n'appartient pas au village");
            for (int i = 0; i < 20 && !quarter.get().built(); i++) SiteService.advanceForTests(level, quarter.get(), 100000);
            w.check(quarter.get().built(), "le quartier ne s'achève pas");
            w.check(city.population() == population + VillageService.QUARTER_POPULATION, "la ville ne gagne pas d'habitants : " + city.population());
            w.check(data.lineage().residents(village.community()).size() > residents, "le registre ne compte pas d'habitants de plus");
            w.check(VillageService.report(server).size() == 1, "/crusade civ ne montre pas le village");

            // Tout se défait : chantiers, ville, habitants.
            int undone = SiteService.undoAuto(server);
            final AiRealm host = ai;
            w.check(undone == 2, "on défait " + undone + " construction(s) au lieu de deux");
            w.check(host.provinces().stream().noneMatch(p -> p.id() == village.province()), "la ville du village reste sur la carte");
            w.check(data.lineage().community(village.community()).isEmpty(), "la communauté du village reste au registre");
            h.succeed();
        } finally {
            SiteService.undoAuto(server);
            if (ai != null) WorldEditService.removeRealm(server, ai.id());
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
