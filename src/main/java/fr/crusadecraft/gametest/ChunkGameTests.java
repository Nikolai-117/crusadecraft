package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.ClaimService;
import fr.crusadecraft.core.world.ProtectionService;
import fr.crusadecraft.core.world.ProtectionService.Action;
import fr.crusadecraft.core.world.Territory;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Le territoire au chunk (P16) : revendiquer, rendre, la carte du chat, et les protections selon le lien. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChunkGameTests {

    private ChunkGameTests() {}

    private static void resetWorld(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        data.world().clear();
        data.world().markGenerated(RealmService.currentDay(server));
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    private static BlockPos chunkCenter(GameTestHelper h, int cx, int cz) {
        return h.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(cx * 16 + 8, 0, cz * 16 + 8));
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p16_claim", timeoutTicks = 200)
    public static void revendiquerEtRendre(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        List<? extends Integer> caps = CrusadeConfig.SERVER.claimCaps.get();
        try {
            resetWorld(w);
            var server = h.getLevel().getServer();
            var dim = h.getLevel().dimension().location();
            w.realm.setGold(5000);
            // Le premier chunk à l'est de la colonie qui n'est pas encore au royaume : collé à ses terres.
            int cx0 = TerritoryMap.cellOf(w.center.getX()), cz0 = TerritoryMap.cellOf(w.center.getZ());
            Territory.Snapshot snap = Territory.snapshot(server, dim);
            int target = cx0;
            while (snap.ownerOfCell(TerritoryMap.key(target, cz0)).map(w.realm.id()::equals).orElse(false) && target < cx0 + 30) target++;
            w.check(target > cx0 && target < cx0 + 30, "les terres de la colonie n'ont pas de bord");
            BlockPos spot = chunkCenter(h, target, cz0);
            w.ruler.setPos(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);

            long gold = w.realm.gold();
            Outcome out = ClaimService.claim(w.ruler, 0);
            w.check(out.success(), "claim refusé : " + out.message().getString());
            w.check(Territory.ownerAt(server, dim, spot.getX(), spot.getZ()).map(w.realm.id()::equals).orElse(false), "le chunk n'est pas au royaume");
            w.check(w.realm.gold() == gold - CrusadeConfig.SERVER.claimCellCost.get(), "le chunk n'a pas été payé");
            w.check(!ClaimService.claim(w.ruler, 9).success(), "un rayon démesuré est accepté");

            // Le plafond de chunks revendiqués selon le rang.
            CrusadeConfig.SERVER.claimCaps.set(List.of(10, 10, 10, 10, 10));
            Outcome capped = ClaimService.claim(w.ruler, 3);
            w.check(!capped.success() && key(capped.message()).equals("crusadecraft.claim.cap"), "le plafond n'arrête pas : " + capped.message().getString());

            // La carte du chat : un titre, sept rangées, une légende ; le joueur au centre.
            List<Component> map = ClaimService.map(w.ruler);
            w.check(map.size() == 9 && map.get(4).getString().contains("✚"), "la carte du chat est mal faite");
            w.check(!ClaimService.info(w.ruler).isEmpty(), "pas d'info sur le chunk");

            // Rendre le chunk : il devient terre libre.
            w.check(ClaimService.unclaim(w.ruler, 0).success(), "unclaim refusé");
            w.check(Territory.ownerAt(server, dim, spot.getX(), spot.getZ()).isEmpty(), "le chunk rendu appartient encore à quelqu'un");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.claimCaps.set(caps);
            resetWorld(w);
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p16_protection", timeoutTicks = 200)
    public static void lesProtectionsSuiventLeLien(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        Realm other = null;
        try {
            resetWorld(w);
            var level = h.getLevel();
            var dim = level.dimension().location();
            // Un chunk du royaume loin de la colonie (hors de ses permissions MineColonies).
            int cx = TerritoryMap.cellOf(w.center.getX()) + 20, cz = TerritoryMap.cellOf(w.center.getZ());
            data.world().borders().paint(dim, new long[]{TerritoryMap.key(cx, cz)}, w.realm.id());
            BlockPos field = chunkCenter(h, cx, cz);
            UUID stranger = UUID.randomUUID();

            w.check(ProtectionService.decide(server, level, stranger, field, Action.BREAK).isPresent(), "un inconnu casse chez le joueur");
            w.check(ProtectionService.decide(server, level, stranger, field, Action.INTERACT).isPresent(), "un inconnu ouvre les coffres");
            w.check(ProtectionService.decide(server, level, w.ruler.getUUID(), field, Action.BREAK).isEmpty(), "le souverain ne casse pas chez lui");
            w.check(ProtectionService.decide(server, level, stranger, w.center, Action.BREAK).isEmpty(),
                    "dans la colonie, nos protections passent avant MineColonies");

            // L'inconnu a un royaume : sans lien, rien ; allié, il ouvre ; vassal, il fait tout.
            other = new Realm(UUID.randomUUID(), "Royaume d'Ailleurs", "", "", 0x445566, stranger, "Étranger", false,
                    new ColonyRef(dim, 987654), TitleRank.BARONNIE, 0);
            data.add(other);
            w.check(ProtectionService.decide(server, level, stranger, field, Action.PLACE).isPresent(), "un voisin sans lien pose chez le joueur");
            long today = RealmService.currentDay(server);
            data.diplomacy().between(w.realm.id(), other.id()).sign(Treaty.ALLIANCE, today);
            w.check(ProtectionService.decide(server, level, stranger, field, Action.INTERACT).isEmpty(), "un allié n'ouvre pas les portes");
            w.check(ProtectionService.decide(server, level, stranger, field, Action.BREAK).isPresent(), "un allié casse");
            data.diplomacy().between(w.realm.id(), other.id()).makeVassal(w.realm.id(), 10, 10);
            w.check(ProtectionService.decide(server, level, stranger, field, Action.BREAK).isEmpty(), "un vassal ne peut rien chez son suzerain");

            // Les terres d'un royaume IA sont libres (réglage par défaut).
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, level, "Rochegarde", "", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1500, w.center.getZ());
            w.check(ProtectionService.decide(server, level, stranger, ai.capital().seat(), Action.BREAK).isEmpty(), "les terres IA sont protégées");
            h.succeed();
        } finally {
            if (other != null) {
                data.diplomacy().forget(other.id());
                data.remove(other.id());
            }
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().borders().forget(w.realm.id());
            resetWorld(w);
            w.cleanup();
        }
    }
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p17_carteweb", timeoutTicks = 200)
    public static void laCarteWebDessineLesRoyaumes(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        var server = h.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        try {
            resetWorld(w);
            var level = h.getLevel();
            var dim = level.dimension().location();
            WorldEditService.grantForTests(w.ruler.getUUID(), true);
            AiRealm ai = WorldEditService.addRealm(server, level, "Rochegarde", "Godefroy", TitleRank.DUCHE, 0x2E7D32, AiFaith.FIDELE,
                    AiPersonality.PACIFIQUE, w.center.getX() + 1500, w.center.getZ());
            var region = fr.crusadecraft.core.place.PlaceService.create(w.ruler, fr.crusadecraft.core.place.LieuKind.REGION, "Forêt de Brocéliande",
                    null, w.center.offset(600, 0, 0), 96);
            w.check(region.outcome().success(), "région refusée");
            fr.crusadecraft.core.place.PlaceService.sync(server);
            fr.crusadecraft.core.world.WebMap.Model model = fr.crusadecraft.core.world.WebMap.build(server);
            String world = fr.crusadecraft.core.world.WebMap.worldName(level);
            w.check(world.equals(server.getWorldData().getLevelName()), "le monde principal ne porte pas le nom de Dynmap");
            String mine = "royaume_" + w.realm.id() + "_" + world;
            String theirs = "royaume_" + ai.id() + "_" + world;
            w.check(model.areas().stream().anyMatch(a -> a.id().startsWith(mine)), "les terres du joueur manquent");
            w.check(model.areas().stream().anyMatch(a -> a.id().startsWith(theirs) && a.html().contains("Godefroy")), "la fiche du duché manque");
            w.check(model.lines().stream().anyMatch(l -> l.id().startsWith(theirs)), "la frontière du duché manque");
            w.check(model.areas().stream().anyMatch(a -> a.id().equals("lieu_" + region.select())), "la région n'est pas dessinée");
            w.check(model.points().stream().anyMatch(p -> p.id().equals("lieu_" + region.select())), "la région n'a pas son repère");
            // Chaque aplat d'un royaume est un rectangle de chunks entiers.
            for (var a : model.areas()) {
                if (!a.id().startsWith("royaume_")) continue;
                for (double x : a.xs()) w.check(x % 16 == 0, "un aplat ne suit pas les chunks");
            }
            // Rien n'a changé : la même empreinte, la carte ne sera pas redessinée.
            w.check(fr.crusadecraft.core.world.WebMap.build(server).hash() == model.hash(), "l'empreinte change sans raison");
            data.world().borders().paint(dim, new long[]{TerritoryMap.key(TerritoryMap.cellOf(w.center.getX()) + 40, TerritoryMap.cellOf(w.center.getZ()))},
                    w.realm.id());
            w.check(fr.crusadecraft.core.world.WebMap.build(server).hash() != model.hash(), "un chunk revendiqué ne change pas l'empreinte");
            h.succeed();
        } finally {
            data.places().clear();
            WorldEditService.grantForTests(w.ruler.getUUID(), false);
            resetWorld(w);
            w.cleanup();
        }
    }
}
