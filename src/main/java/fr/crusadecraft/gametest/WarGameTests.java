package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.council.CouncilActions;
import fr.crusadecraft.core.diplomacy.DiplomacyService;
import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.TitleService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarRules;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** La guerre de bout en bout : déclaration et campagne, conquête d'une province, dégâts de guerre réparés à la paix. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarGameTests {

    private WarGameTests() {}

    private static Runnable testSettings() {
        boolean research = CrusadeConfig.SERVER.chanceryNeedsResearch.get();
        int delay = CrusadeConfig.SERVER.letterDelaySeconds.get();
        boolean sieges = CrusadeConfig.SERVER.sieges.get();
        boolean raids = CrusadeConfig.SERVER.raids.get();
        CrusadeConfig.SERVER.chanceryNeedsResearch.set(false);
        CrusadeConfig.SERVER.letterDelaySeconds.set(0);
        CrusadeConfig.SERVER.sieges.set(false);
        CrusadeConfig.SERVER.raids.set(false);
        return () -> {
            CrusadeConfig.SERVER.chanceryNeedsResearch.set(research);
            CrusadeConfig.SERVER.letterDelaySeconds.set(delay);
            CrusadeConfig.SERVER.sieges.set(sieges);
            CrusadeConfig.SERVER.raids.set(raids);
        };
    }

    private static AiRealm generateAndPick(TestWorld w, TitleRank rank) {
        var server = w.helper.getLevel().getServer();
        WorldService.generate(server, w.helper.getLevel(), w.center.getX(), w.center.getZ());
        AiRealm target = CrusadeSavedData.get(server).world().realms().stream().filter(r -> r.rank() == rank).findFirst()
                .orElseThrow(() -> new IllegalStateException("aucun royaume IA de rang " + rank));
        target.discover(w.ruler.getUUID(), RealmService.currentDay(server));
        return target;
    }

    private static void cleanup(TestWorld w, Runnable restore) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        if (w.realm != null) {
            for (War war : new ArrayList<>(data.wars().involving(w.realm.id()))) {
                data.wars().damage().release(war.id());
                data.wars().remove(war.id());
                WarFieldService.onWarEnded(server, war.id());
            }
            data.wars().damage().take(Integer.MAX_VALUE);
            data.diplomacy().forget(w.realm.id());
        }
        data.world().clear();
        w.ruler.getInventory().clearContent();
        restore.run();
        w.cleanup();
    }

    private static War declare(TestWorld w, AiRealm target, CasusBelli cb, int province) {
        var server = w.helper.getLevel().getServer();
        CouncilActions.Outcome out = WarService.declare(w.ruler, target.id(), cb, province);
        w.check(out.success(), "déclaration refusée : " + out.message().getString());
        return CrusadeSavedData.get(server).wars().between(w.realm.id(), target.id())
                .orElseThrow(() -> new IllegalStateException("guerre introuvable après la déclaration"));
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p7_campagne", timeoutTicks = 200)
    public static void declarerFaireCampagneEtCapituler(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            AiRealm county = generateAndPick(w, TitleRank.COMTE);
            // Un souverain tiré au hasard peut détester d'emblée (caractère, traits) : on le veut neutre, sinon
            // l'humiliation s'ouvre sans affront selon la graine du monde, donc selon l'emplacement du test.
            county.setPersonality(fr.crusadecraft.core.world.AiPersonality.PACIFIQUE);
            var ruler = county.ruler();
            county.setRuler(new fr.crusadecraft.core.world.AiCharacter(ruler.name(), ruler.female(), ruler.age(), ruler.stats(), List.of()));
            Relation rel = data.diplomacy().between(county.id(), w.realm.id());
            long today = RealmService.currentDay(server);

            // Sans rivalité ni revendication, aucun motif n'est ouvert.
            w.check(!WarService.declare(w.ruler, county.id(), CasusBelli.HUMILIATE, -1).success(), "humiliation sans affront");
            w.check(!WarService.declare(w.ruler, county.id(), CasusBelli.CLAIM, -1).success(), "revendication sans parchemin");
            rel.setHostile(true);
            w.realm.setGold(1000);
            w.realm.setPrestige(100);
            War war = declare(w, county, CasusBelli.HUMILIATE, -1);
            w.check(rel.war(), "la relation n'est pas en guerre");
            w.check(war.events().size() == 1, "journal : " + war.events().size());

            // L'armée levée se paie chaque jour ; la campagne lasse les deux camps.
            w.check(WarService.raise(w.ruler, true).success(), "armée non levée");
            w.check(!WarService.raise(w.ruler, true).success(), "armée levée deux fois");
            int men = WarService.men(w.realm);
            WarService.onNewDay(server, today + 1);
            w.check(war.attackerExhaustion() > 0 && war.defenderExhaustion() > 0, "aucune lassitude après un jour de campagne");
            w.check(w.realm.ledger().peek(today).amount(LedgerCategory.ARMEE) < 0, "solde non payée");
            w.check(WarService.men(w.realm) <= men, "l'armée a grossi en pleine guerre");

            // Une paix blanche proposée : l'IA répond par lettre, d'une façon ou d'une autre.
            int mail = w.realm.mail().size();
            w.check(WarService.proposePeace(w.ruler, war.id(), WarRules.Peace.WHITE).success(), "offre de paix non envoyée");
            w.check(w.realm.mail().size() == mail + 1, "pas de réponse de l'IA");

            // Capituler exige le sceau ; l'attaquant vaincu paie réparation, et une trêve suit.
            w.check(!WarService.proposePeace(w.ruler, war.id(), WarRules.Peace.SURRENDER).success(), "capitulation sans sceau");
            w.ruler.getInventory().add(new ItemStack(ModItems.SEAL.get()));
            long gold = w.realm.gold(), aiGold = county.gold();
            CouncilActions.Outcome surrender = WarService.proposePeace(w.ruler, war.id(), WarRules.Peace.SURRENDER);
            w.check(surrender.success(), "capitulation refusée : " + surrender.message().getString());
            w.check(data.wars().between(w.realm.id(), county.id()).isEmpty(), "la guerre continue");
            w.check(!rel.war() && rel.truce(today), "pas de trêve après la paix");
            long reparations = Math.round(gold * 0.2);
            w.check(w.realm.gold() == gold - reparations, "réparations : " + gold + " → " + w.realm.gold());
            w.check(county.gold() == aiGold + reparations, "l'IA n'a pas reçu les réparations");
            w.check(w.realm.prestige() == 100 - WarRules.victoryPrestige(CasusBelli.HUMILIATE), "prestige : " + w.realm.prestige());
            rel.setHostile(true);
            w.check("crusadecraft.war.block.truce".equals(WarService.target(server, w.realm, county.id()).problem()), "la trêve n'interdit rien");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p7_conquete", timeoutTicks = 200)
    public static void conquerirUneProvinceRevendiquee(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            AiRealm county = generateAndPick(w, TitleRank.COMTE);
            AiProvince target = county.capital();
            w.realm.claims().add(target.id());
            War war = declare(w, county, CasusBelli.CLAIM, target.id());
            w.check(war.targetProvince() == target.id(), "province visée : " + war.targetProvince());

            // La place est prise et les batailles gagnées : l'IA s'avoue vaincue.
            war.occupied().add(target.id());
            war.addBattle(60);
            w.check(WarService.score(server, war) >= 80, "score : " + WarService.score(server, war));
            w.realm.mail().clear();
            w.check(WarService.proposePeace(w.ruler, war.id(), WarRules.Peace.VICTORY).success(), "offre non envoyée");
            Letter letter = w.realm.mail().stream().filter(Letter::needsAnswer).findFirst()
                    .orElseThrow(() -> new IllegalStateException("l'IA refuse sa défaite malgré le score"));
            w.check(letter.choices().get(0).action() == Letter.Action.SEAL_PEACE, "la lettre ne propose pas de sceller la paix");
            w.ruler.getInventory().add(new ItemStack(ModItems.SEAL.get()));
            CouncilActions.Outcome sealed = DiplomacyService.answer(w.ruler, letter.id(), 0);
            w.check(sealed.success(), "paix non scellée : " + sealed.message().getString());

            // La province change de mains : comptée dans les titres, versant son revenu.
            w.check(w.realm.id().equals(target.holder()), "la province n'a pas changé de mains");
            w.check(!county.ownProvinces().contains(target), "l'IA tient encore la province");
            w.check(!w.realm.claims().contains(target.id()), "la revendication subsiste après la conquête");
            w.check(TitleService.provinces(server, w.realm) == 2, "provinces : " + TitleService.provinces(server, w.realm));
            w.check(WarService.conquestIncome(server, w.realm) > 0, "la province conquise ne rapporte rien");
            w.check(data.wars().between(w.realm.id(), county.id()).isEmpty(), "la guerre continue");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p7_degats", timeoutTicks = 200)
    public static void degatsDeGuerreReparesALaPaix(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var level = h.getLevel();
            var data = CrusadeSavedData.get(server);
            AiRealm county = generateAndPick(w, TitleRank.COMTE);
            // Une province ennemie posée au bord du terrain de test, pour que la guerre s'y joue.
            AiProvince border = new AiProvince(9999, "Marche-Grise", level.dimension().location(), w.center.offset(12, 0, 0),
                    AiProvince.Anchor.LAND, false, 0, 20, 10);
            county.provinces().add(border);
            data.diplomacy().between(county.id(), w.realm.id()).setHostile(true);
            War war = declare(w, county, CasusBelli.HUMILIATE, -1);

            BlockPos wall = h.absolutePos(new BlockPos(1, 2, 1));
            BlockPos chest = h.absolutePos(new BlockPos(2, 2, 1));
            BlockPos ladder = h.absolutePos(new BlockPos(3, 2, 1));
            level.setBlockAndUpdate(wall, Blocks.STONE_BRICKS.defaultBlockState());
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof Container c) c.setItem(0, new ItemStack(Items.DIAMOND, 3));

            // À la pioche, rien ne tombe ; ce qu'on pose se reprend.
            w.check(!WarFieldService.breakAllowed(server, w.ruler, wall), "un mur ennemi se casse à la main");
            level.setBlockAndUpdate(ladder, Blocks.COBBLESTONE.defaultBlockState());
            WarFieldService.onPlace(server, w.ruler, level, ladder, Blocks.AIR.defaultBlockState());
            w.check(WarFieldService.breakAllowed(server, w.ruler, ladder), "un bloc posé pendant la guerre ne se reprend pas");

            // Une explosion : tout est noté, rien ne tombe au sol, le coffre ne se vide pas par terre.
            List<BlockPos> blast = new ArrayList<>(List.of(wall, chest));
            WarFieldService.onExplosion(server, level, blast);
            w.check(blast.isEmpty(), "l'explosion garde la main sur des blocs notés");
            w.check(level.getBlockState(wall).isAir() && level.getBlockState(chest).isAir(), "les blocs touchés sont restés");
            w.check(data.wars().damage().count(war.id()) == 3, "registre : " + data.wars().damage().count(war.id()));
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(3));
            w.check(drops.isEmpty(), "des objets sont tombés : " + drops.size());

            // La paix : tout revient, coffre compris, et l'échelle d'assaut disparaît.
            WarService.endWar(server, war, 0);
            w.check(data.wars().damage().pending() == 3, "file de restauration : " + data.wars().damage().pending());
            WarFieldService.restoreTick(server);
            w.check(data.wars().damage().pending() == 0, "restauration inachevée");
            w.check(level.getBlockState(wall).is(Blocks.STONE_BRICKS), "le mur n'est pas relevé");
            w.check(level.getBlockState(chest).is(Blocks.CHEST), "le coffre n'est pas revenu");
            w.check(level.getBlockEntity(chest) instanceof Container c && c.getItem(0).is(Items.DIAMOND) && c.getItem(0).getCount() == 3,
                    "le contenu du coffre est perdu");
            w.check(level.getBlockState(ladder).isAir(), "le bloc posé en guerre est resté");
            w.check(WarFieldService.breakAllowed(server, w.ruler, wall), "la paix n'a pas levé l'interdiction");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }
}
