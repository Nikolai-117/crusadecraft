package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityBubbles;
import fr.crusadecraft.core.city.CityBuilding;
import fr.crusadecraft.core.city.CityEconomy;
import fr.crusadecraft.core.city.CityEggs;
import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.core.city.CityLife;
import fr.crusadecraft.core.city.CityPeople;
import fr.crusadecraft.core.city.CityRumors;
import fr.crusadecraft.core.city.CityService;
import fr.crusadecraft.core.city.CityTalk;
import fr.crusadecraft.core.city.CityVoice;
import fr.crusadecraft.core.city.CityWork;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.nation.NationBook;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * P42 et P43 : les habitants. Ils portent les noms de leur culture, les personnages écrits d'avance prennent leur poste,
 * tous les textes se remplissent sans trou, l'œuf fait naître quelqu'un, on se souvient des coups, et les villes vivent :
 * elles produisent, mangent, se marient, reprennent les postes, et leurs étals suivent leurs réserves.
 */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InhabitantGameTests {
    private InhabitantGameTests() {}

    private static NationDef nation() {
        NationDef.Notable baker = new NationDef.Notable(BuildingFunction.BOULANGERIE, "Ilaria", "Bressan", true, 41, List.of(Trait.DILIGENT), "Levée avant tout le monde.",
                List.of("Le pain n'attend pas, {joueur}."));
        NationDef.CityLore lore = new NationDef.CityLore(List.of("Une vieille ville."), List.of(baker));
        return new NationDef("essaic", "Essaic", "EssaiC", TitleRank.ROYAUME, 0x6D3A7A, AiPersonality.MARCHAND, AiFaith.FIDELE, false, "Essaic",
                new NationDef.Ruler("Ruprecht", false, 50), List.of(), new NationDef.Culture("Romian", "Romianisme", List.of("Elio"), List.of("Bianca"), List.of("Corsi")),
                List.of(), Map.of("cité d'essai", lore), List.of());
    }

    /** Une ville de la nation d'essai, posée à plat, avec un bâtiment par fonction voulue. */
    private static Site city(TestWorld w, MinecraftServer server, ServerLevel level, BuildingFunction... functions) {
        var data = CrusadeSavedData.get(server);
        NationService.ensure(server);
        var realm = NationService.realm(server, "essaic").orElseThrow();
        BlockPos at = w.center.offset(0, 0, 14);
        var province = fr.crusadecraft.core.world.WorldEditService.addCity(server, level, realm.id(), "Cité d'essai", fr.crusadecraft.core.world.WorldEditService.CitySize.VILLE, at.getX(), at.getZ()).orElseThrow();
        Site s = new Site(UUID.randomUUID(), SiteKind.VILLAGE, "Cité d'essai", realm.id(), level.dimension().location(), at, Direction.SOUTH, realm.color(), true, RealmService.currentDay(server));
        s.setProvince(province.id());
        s.setImported("file:plans|EssaiC/essai.schem", 96, 20, 96, 1);
        s.setBuilt(true);
        s.setCommunity(new LineageGenerator(7).community(data.lineage(), "Cité d'essai", fr.crusadecraft.core.lineage.CommunityKind.VILLAGE, realm.id(), 0).id());
        data.world().add(s);
        int i = 0;
        for (BuildingFunction f : functions) {
            BlockPos a = at.offset(i * 7, 0, 0);
            CityBuilding b = new CityBuilding(UUID.randomUUID(), a, a.offset(4, 3, 4), a.offset(2, 0, 2));
            b.setStats(80, 1, Map.of());
            s.buildings().add(b);
            CityService.define(w.ruler, s.id(), b, f, "");
            i++;
        }
        return s;
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p42_habitants", timeoutTicks = 300)
    public static void culturesEtPersonnages(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habita");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.BOULANGERIE, BuildingFunction.MAISON, BuildingFunction.FORGE);
            var state = data.lineage();
            CityBuilding bakery = s.buildings().get(0);
            Person baker = state.person(bakery.occupants().get(0)).orElseThrow();
            w.check(baker.name().equals("Ilaria") && baker.surname().equals("Bressan") && baker.female(), "le personnage écrit d'avance n'a pas pris son poste : " + state.fullName(baker));
            w.check(baker.bio().startsWith("Levée"), "sa vie n'est pas celle du fichier : " + baker.bio());
            w.check(baker.traits().contains(Trait.DILIGENT) && baker.age() >= 41, "ses traits ou son âge ne suivent pas");
            w.check("BOULANGERIE".equals(baker.job()) && s.id().equals(baker.site()), "son métier ou sa ville manque");
            for (CityBuilding b : s.buildings()) {
                for (UUID id : b.occupants()) {
                    Person p = state.person(id).orElseThrow();
                    if (!p.notable().isEmpty()) continue;
                    w.check(p.name().equals("Elio") || p.name().equals("Bianca"), "un habitant ne porte pas un nom de sa culture : " + p.name());
                    w.check(p.surname().equals("Corsi"), "un habitant ne porte pas un nom de famille de sa culture : " + p.surname());
                    w.check(!p.traits().isEmpty(), "un habitant n'a pas de caractère");
                    w.check(!p.bio().isEmpty(), "un habitant n'a pas de vie : " + p.name());
                    w.check(!p.bio().contains("{"), "une vie garde un trou : " + p.bio());
                }
            }
            // Le fichier corrigé se voit sur le personnage déjà là.
            NationDef.Notable fixed = new NationDef.Notable(BuildingFunction.BOULANGERIE, "Ilaria", "Bressan", true, 41, List.of(Trait.CALME), "Corrigée.", List.of());
            NationBook.setForTests(List.of(new NationDef("essaic", "Essaic", "EssaiC", TitleRank.ROYAUME, 0x6D3A7A, AiPersonality.MARCHAND, AiFaith.FIDELE, false, "Essaic",
                    null, List.of(), nation().culture(), List.of(), Map.of("cité d'essai", new NationDef.CityLore(List.of(), List.of(fixed))), List.of())));
            CityPeople.applyNotables(server, s);
            w.check(baker.bio().equals("Corrigée.") && baker.traits().contains(Trait.CALME), "une correction du fichier ne se voit pas");
            // Enregistré puis relu.
            Person loaded = Person.load(baker.save());
            baker.addOpinion(w.ruler.getUUID(), 12);
            Person again = Person.load(baker.save());
            w.check(loaded.job().equals("BOULANGERIE") && loaded.site().equals(s.id()) && !loaded.notable().isEmpty(), "une fiche enregistrée perd quelque chose");
            w.check(again.opinionOf(w.ruler.getUUID()) == 12, "un avis enregistré se perd");
            Site reread = Site.load(s.save());
            w.check(reread.economy().stock(CityGood.PAIN) == s.economy().stock(CityGood.PAIN), "les réserves ne s'enregistrent pas");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** Tous les textes écrits d'avance, pour toutes sortes de gens : aucun trou ne doit rester, aucun moment n'est vide. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p42_habitants", timeoutTicks = 300)
    public static void aucunTexteATrou(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habitb");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.values());
            CityRumors.add(server, s, "mariage", "Elio", "Bianca");
            var state = data.lineage();
            int checked = 0;
            for (Repliques.Entry e : Repliques.entries()) {
                for (Component line : e.lines()) {
                    for (CityBuilding b : s.buildings()) {
                        if (b.occupants().isEmpty()) continue;
                        Person p = state.person(b.occupants().get(0)).orElseThrow();
                        String text = CityVoice.fill(server, s, p, fr.crusadecraft.core.logic.Fr.of(line), null, new Random(1));
                        boolean letter = e.moment().name().startsWith("LETTRE");
                        w.check(letter || (!text.contains("{") && !text.contains("}")), "un texte garde un trou : " + text);
                        checked++;
                        break;
                    }
                }
            }
            w.check(checked > 400, "peu de textes chargés : " + checked);
            Random r = new Random(3);
            // Chacun a quelque chose à répondre, sur chaque sujet.
            for (CityBuilding b : s.buildings()) {
                for (UUID id : b.occupants()) {
                    Person p = state.person(id).orElseThrow();
                    for (Repliques.Moment m : new Repliques.Moment[]{Repliques.Moment.ACCUEIL, Repliques.Moment.METIER, Repliques.Moment.VILLE, Repliques.Moment.ROYAUME,
                            Repliques.Moment.RUMEUR, Repliques.Moment.FAMILLE, Repliques.Moment.AVIS, Repliques.Moment.DOULEUR}) {
                        var t = CityVoice.text(server, s, p, m, w.ruler, r);
                        w.check(t.isPresent(), p.job() + " (" + p.age() + " ans) n'a rien à dire sur " + m);
                    }
                }
            }
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p42_habitants", timeoutTicks = 300)
    public static void oeufEtSouvenirs(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habitc");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        Entity born = null;
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.FORGE);
            CityBuilding forge = s.buildings().get(0);
            int before = forge.occupants().size();
            BlockPos at = forge.spot().offset(0, 0, 6);
            var out = CityEggs.hatch(w.ruler, s.id(), BuildingFunction.FORGE, at);
            w.check(out.success(), "l'œuf ne fait naître personne : " + out.message().getString());
            w.check(forge.occupants().size() == before + 1, "il n'a pas pris de poste à la forge");
            Person p = data.lineage().person(forge.occupants().get(before)).orElseThrow();
            w.check("FORGE".equals(p.job()) && !p.bio().isEmpty() && p.inWorld(), "sa fiche est incomplète");
            born = level.getEntity(p.id());
            if (born == null) born = CityService.spawnedForTests(p.id());
            w.check(born != null && born.getTags().contains(CityService.TAG), "aucun villageois dans le monde");

            // Frappé : il s'en souvient, la ville en parle.
            CityTalk.onHurt(w.ruler, born);
            w.check(p.opinionOf(w.ruler.getUUID()) == -15, "un coup est oublié : " + p.opinionOf(w.ruler.getUUID()));
            w.check(s.rumors().stream().anyMatch(r -> r.text().contains(p.name())), "la ville n'en parle pas");
            w.check(CityBubbles.active() >= 1, "pas de bulle au-dessus de sa tête");
            // Le temps efface un peu.
            p.forgetABit();
            w.check(p.opinionOf(w.ruler.getUUID()) == -14, "le temps n'efface rien");
            CityBubbles.onServerStopped();
            w.check(CityBubbles.active() == 0, "une bulle reste");
            // Une ville pleine refuse.
            for (int i = 0; i < 100 && CityService.people(server, s) < CityService.PEOPLE_MAX; i++) CityEggs.hatch(w.ruler, s.id(), BuildingFunction.FORGE, at);
            w.check(!CityEggs.hatch(w.ruler, s.id(), BuildingFunction.FORGE, at).success(), "une ville pleine accepte encore");
            h.succeed();
        } finally {
            for (Villager v : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(w.center).inflate(64), v -> v.getTags().contains(CityService.TAG))) v.discard();
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** P44 : une enseigne donne sa fonction à un bâtiment, sur place ou dans un plan, et la retirer la lui reprend. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p44_enseignes", timeoutTicks = 300)
    public static void lesEnseignes(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habitf");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level);
            BlockPos at = s.anchor().offset(10, 1, 10);
            level.setBlock(at, fr.crusadecraft.registry.ModBlocks.SIGNS.get(BuildingFunction.ECOLE).get().defaultBlockState(), 3);
            var out = fr.crusadecraft.core.city.CitySigns.onPlaced(w.ruler, at, BuildingFunction.ECOLE);
            w.check(out.success(), "l'enseigne se refuse : " + out.message().getString());
            CityBuilding school = CityService.buildingAt(s, at).orElse(null);
            w.check(school != null && school.function() == BuildingFunction.ECOLE, "le bâtiment n'est pas une école");
            w.check(!school.occupants().isEmpty(), "personne n'arrive à l'école");
            Person teacher = data.lineage().person(school.occupants().get(0)).orElseThrow();
            w.check("ECOLE".equals(teacher.job()) && !teacher.bio().isEmpty(), "le maître d'école n'a pas sa fiche");
            w.check(CityTalk.jobTitle(teacher).startsWith("Ma"), "son titre : " + CityTalk.jobTitle(teacher));
            // Retirée, le bâtiment redevient à décider, et ses gens s'en vont.
            level.removeBlock(at, false);
            w.check(school.function() == null && school.occupants().isEmpty(), "retirer l'enseigne ne change rien");

            // Une ville bâtie avec ses enseignes : elles sont lues à la reconnaissance, sans geste du joueur.
            BlockPos other = s.anchor().offset(30, 1, 30);
            level.setBlock(other, fr.crusadecraft.registry.ModBlocks.SIGNS.get(BuildingFunction.BOULANGERIE).get().defaultBlockState(), 3);
            CityService.recognize(level, s);
            CityBuilding bakery = CityService.buildingAt(s, other).orElse(null);
            w.check(bakery != null && bakery.function() == BuildingFunction.BOULANGERIE, "l'enseigne n'est pas lue à la reconnaissance");
            level.removeBlock(other, false);

            // Chaque fonction a son enseigne, et son titre.
            for (BuildingFunction f : BuildingFunction.values()) w.check(fr.crusadecraft.registry.ModBlocks.SIGNS.get(f) != null, "pas d'enseigne pour " + f);
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    /** P45 : la décoration sert : on s'assied, on dort, la cloche rassemble, le comptoir vend, les fleurs font le charme. */
    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p44_enseignes", timeoutTicks = 400)
    public static void laDecorationSert(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habitg");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        List<Entity> spawned = new java.util.ArrayList<>();
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.TAVERNE, BuildingFunction.MAISON, BuildingFunction.HOSPICE);
            level.setDayTime(3000);
            spawned.addAll(CityService.populateEntities(level, s));
            CityBuilding tavern = s.buildings().get(0);
            Person keeper = data.lineage().person(tavern.occupants().get(0)).orElseThrow();
            Entity keeperE = spawned.stream().filter(x -> x.getUUID().equals(keeper.id())).findFirst().orElse(null);
            w.check(keeperE instanceof Villager, "le tavernier n'est pas là");
            Villager v = (Villager) keeperE;

            // S'asseoir, et se relever.
            BlockPos seat = v.blockPosition().offset(1, 0, 1);
            level.setBlock(seat, fr.crusadecraft.registry.ModBlocks.COUNCIL_SEAT.get().defaultBlockState(), 3);
            w.check(fr.crusadecraft.core.city.CitySeats.sittable(level.getBlockState(seat)), "le siège du conseil n'est pas un siège");
            w.check(fr.crusadecraft.core.city.CitySeats.sit(level, v, seat) && fr.crusadecraft.core.city.CitySeats.seated(v), "il ne s'assied pas");
            v.stopRiding();
            fr.crusadecraft.core.city.CitySeats.tick();
            w.check(!fr.crusadecraft.core.city.CitySeats.seated(v), "il ne se relève pas");
            w.check(level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, new net.minecraft.world.phys.AABB(seat).inflate(3),
                    a -> a.getTags().contains(fr.crusadecraft.core.city.CitySeats.TAG)).stream().allMatch(Entity::isRemoved), "le support reste après qu'il s'est levé");

            // Le comptoir : son étal s'ouvre sans courir après lui.
            BlockPos counter = tavern.spot().offset(0, 0, 1);
            level.setBlock(counter, Blocks.AIR.defaultBlockState(), 3);
            w.check(!fr.crusadecraft.core.city.CityFurniture.onCounter(w.ruler, counter), "n'importe quel bloc sert de comptoir");
            net.minecraft.world.level.block.Block tableBlock = null;
            for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (fr.crusadecraft.core.city.Furnishing.of(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath()) == fr.crusadecraft.core.city.Furnishing.Kind.TABLE) {
                    tableBlock = b;
                    break;
                }
            }
            // Une table d'un mod, s'il y en a un de chargé ; sans quoi, on ne peut pas l'essayer ici.
            if (tableBlock != null) {
                w.ruler.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                level.setBlock(counter, tableBlock.defaultBlockState(), 3);
                w.check(!v.getOffers().isEmpty(), "le tavernier n'a rien à vendre");
                w.check(fr.crusadecraft.core.city.CityFurniture.onCounter(w.ruler, counter), "le comptoir ne répond pas");
                w.check(v.isTrading(), "le comptoir n'ouvre pas son étal");
                v.setTradingPlayer(null);
                w.ruler.closeContainer();
                level.setBlock(counter, Blocks.AIR.defaultBlockState(), 3);
            }

            // La cloche rassemble.
            BlockPos bell = s.anchor().offset(8, 1, 8);
            fr.crusadecraft.core.city.CityFurniture.onBell(w.ruler, bell);
            w.check(fr.crusadecraft.core.city.CityFurniture.gathering(s, level.getGameTime()) != null, "la cloche ne rassemble personne");
            CityWork.live(server, level, s, tavern, keeper, v);
            w.check(v.getMainHandItem().isEmpty(), "à la cloche, il garde son outil en main");
            w.check(fr.crusadecraft.core.city.CityFurniture.gathering(s, level.getGameTime() + 5000) == null, "un rassemblement dure toujours");

            // Le lit : la nuit, il s'y couche ; le jour, il se lève.
            CityBuilding house = s.buildings().get(1);
            Person dweller = data.lineage().person(house.occupants().get(0)).orElseThrow();
            Entity de = spawned.stream().filter(x -> x.getUUID().equals(dweller.id())).findFirst().orElse(null);
            w.check(de instanceof PathfinderMob, "l'habitant n'est pas là");
            PathfinderMob sleeper = (PathfinderMob) de;
            BlockPos foot = house.min().offset(1, 0, 1), head = foot.south();
            level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH), 3);
            house.setStats(house.floor(), house.levels(), Map.of("lit", 1));
            sleeper.teleportTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
            level.setDayTime(18000);
            fr.crusadecraft.core.city.CityFurniture.onServerStopped();
            CityWork.live(server, level, s, house, dweller, sleeper);
            w.check(sleeper.isSleeping(), "il ne se couche pas dans son lit");
            level.setDayTime(3000);
            CityWork.live(server, level, s, house, dweller, sleeper);
            w.check(!sleeper.isSleeping(), "il ne se lève pas le matin");

            // Le charme : quelques fleurs et lanternes, et la ville a de l'allure.
            w.check(s.charm() == 0, "une ville nue a du charme");
            for (int i = 0; i < 30; i++) level.setBlock(s.anchor().offset(i - 15, 1, 40), Blocks.POPPY.defaultBlockState(), 3);
            for (int i = 0; i < 400 && s.charm() == 0; i++) fr.crusadecraft.core.city.CityFurniture.tick(level, s);
            w.check(s.charm() > 0, "des fleurs ne donnent aucun charme");
            long purse = s.economy().purse();
            Site reread = Site.load(s.save());
            w.check(reread.charm() == s.charm(), "le charme ne s'enregistre pas");

            // L'hospice : on y échappe parfois à la mort.
            Random r = new Random(9);
            boolean spared = false;
            for (int i = 0; i < 40; i++) spared |= CityLife.spared(server, dweller, r);
            w.check(spared, "l'hospice ne sauve personne");
            h.succeed();
        } finally {
            for (Entity x : spawned) x.discard();
            level.setDayTime(1000);
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p43_vie", timeoutTicks = 300)
    public static void laVilleVit(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habitd");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.FERME, BuildingFunction.BOULANGERIE, BuildingFunction.MARCHE, BuildingFunction.MAISON, BuildingFunction.MAISON);
            var state = data.lineage();
            var realm = NationService.realm(server, "essaic").orElseThrow();
            long gold = realm.gold();
            Random random = new Random(5);
            CityEconomy eco = s.economy();
            int people0 = CityService.people(server, s);
            for (int i = 0; i < 40; i++) CityLife.day(server, s, random);
            w.check(eco.famine() == 0, "une ville bien tenue a faim : " + eco.famine());
            w.check(eco.purse() > 0, "la bourse d'une ville bien tenue se vide");
            w.check(eco.stock(CityGood.PAIN) > 0, "plus de pain dans une ville qui a une ferme et un four");
            Person worker = state.person(s.buildings().get(0).occupants().get(0)).orElseThrow();
            w.check(worker.coins() > 0, "personne n'est payé");
            w.check(realm.gold() >= gold, "le trésor du royaume baisse");

            // Quarante jours plus tard, il s'est passé quelque chose.
            boolean married = false;
            for (int d = 0; d < 300 && !married; d++) {
                for (CityBuilding b : s.buildings()) for (UUID id : b.occupants()) married |= state.person(id).map(p -> p.spouse() != null).orElse(false);
                if (!married) CityLife.day(server, s, random);
            }
            w.check(married, "personne ne se marie, même en un an");

            // Un poste vacant : un adulte sans métier le reprend.
            CityBuilding bakery = s.buildings().get(1);
            UUID was = bakery.occupants().get(0);
            state.person(was).orElseThrow().setAlive(false);
            for (int i = 0; i < 3 && bakery.occupants().get(0).equals(was); i++) CityLife.day(server, s, random);
            Person next = state.person(bakery.occupants().get(0)).orElseThrow();
            w.check(!next.id().equals(was) && next.alive() && "BOULANGERIE".equals(next.job()), "le poste reste vacant");

            // Plus rien à manger, et plus de quoi en acheter : la faim s'installe, et une famille finit par partir.
            CityEconomy poor = s.economy();
            for (CityGood g : CityGood.values()) poor.take(g, 100_000);
            poor.addPurse(-poor.purse());
            int housed = 0;
            for (CityBuilding b : s.buildings()) if (b.function() == BuildingFunction.FERME) b.occupants().clear();
            for (CityBuilding b : s.buildings()) if (b.function() == BuildingFunction.MAISON) housed += b.occupants().size();
            int beforeFamine = CityService.people(server, s);
            boolean someoneLeft = false;
            for (int i = 0; i < 40 && !someoneLeft; i++) {
                poor.take(CityGood.BLE, 100_000);
                poor.take(CityGood.PAIN, 100_000);
                poor.take(CityGood.VIANDE, 100_000);
                poor.take(CityGood.REPAS, 100_000);
                poor.addPurse(-poor.purse());
                CityLife.day(server, s, random);
                someoneLeft = CityService.people(server, s) < beforeFamine;
            }
            w.check(poor.famine() > 0 || someoneLeft, "une ville sans rien n'a pas faim");
            w.check(someoneLeft, "personne ne part d'une ville affamée (" + housed + " logés)");
            w.check(s.rumors().stream().anyMatch(r -> r.text().contains("faim") || r.text().contains("quitté")), "la ville ne parle pas de sa faim");
            h.succeed();
        } finally {
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p43_vie", timeoutTicks = 400)
    public static void chacunASonOuvrage(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Habite");
        var server = h.getLevel().getServer();
        var level = h.getLevel();
        var data = CrusadeSavedData.get(server);
        fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), true);
        try {
            data.world().clear();
            NationBook.setForTests(List.of(nation()));
            Site s = city(w, server, level, BuildingFunction.FERME, BuildingFunction.FORGE);
            CityBuilding farm = s.buildings().get(0), forge = s.buildings().get(1);
            var state = data.lineage();
            Person farmer = state.person(farm.occupants().get(0)).orElseThrow();
            // Dix heures du matin : tout le monde travaille, sauf un paresseux qu'on écarte du test.
            farmer.setTraits(List.of(Trait.CALME), Person.MAX_TRAITS);
            level.setDayTime(3000);
            List<Entity> spawned = CityService.populateEntities(level, s);
            Entity e = spawned.stream().filter(x -> x.getUUID().equals(farmer.id())).findFirst().orElse(null);
            w.check(e instanceof PathfinderMob, "le fermier n'est pas dans le monde");
            PathfinderMob mob = (PathfinderMob) e;
            // Un champ mûr à ses pieds.
            BlockPos crop = mob.blockPosition();
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(((CropBlock) Blocks.WHEAT).getMaxAge()), 3);
            int wheat = s.economy().stock(CityGood.BLE);
            for (int i = 0; i < 400 && level.getBlockState(crop).getBlock() instanceof CropBlock c && c.isMaxAge(level.getBlockState(crop)); i++) {
                CityWork.live(server, level, s, farm, farmer, mob);
            }
            var st = level.getBlockState(crop);
            w.check(st.getBlock() instanceof CropBlock c && !c.isMaxAge(st), "le fermier ne moissonne pas");
            w.check(s.economy().stock(CityGood.BLE) > wheat, "la moisson ne va pas aux réserves");
            w.check(mob.getMainHandItem().is(Items.IRON_HOE), "le fermier n'a pas son outil en main : " + mob.getMainHandItem());

            // La nuit, il pose son outil.
            level.setDayTime(16000);
            CityWork.live(server, level, s, farm, farmer, mob);
            w.check(mob.getMainHandItem().isEmpty(), "il garde son outil la nuit");

            // Le forgeron va à ses meubles et frappe : on le voit au moins viser un endroit de son bâtiment.
            level.setDayTime(3000);
            Person smith = state.person(forge.occupants().get(0)).orElseThrow();
            smith.setTraits(List.of(Trait.CALME), Person.MAX_TRAITS);
            level.setBlock(forge.spot().offset(1, 0, 0), Blocks.ANVIL.defaultBlockState(), 3);
            CityWork.forgetPoints(forge.id());
            Entity se = spawned.stream().filter(x -> x.getUUID().equals(smith.id())).findFirst().orElse(null);
            w.check(se instanceof PathfinderMob, "le forgeron n'est pas dans le monde");
            CityWork.live(server, level, s, forge, smith, (PathfinderMob) se);
            w.check(((PathfinderMob) se).getMainHandItem().is(Items.IRON_PICKAXE), "le forgeron n'a pas son marteau");

            // Commercer fait bouger les réserves et la bourse ; un étal vide est épuisé.
            if (se instanceof Villager v) {
                long purse = s.economy().purse();
                int tools = s.economy().stock(CityGood.OUTILS);
                MerchantOffer sale = new MerchantOffer(new net.minecraft.world.item.ItemStack(Items.EMERALD, 3), new net.minecraft.world.item.ItemStack(Items.IRON_AXE), 10, 1, 0.05f);
                CityLife.onTrade(w.ruler, v, sale);
                w.check(s.economy().stock(CityGood.OUTILS) == tools - 1, "la vente ne sort pas de la réserve");
                w.check(s.economy().purse() == purse + 24, "la vente ne remplit pas la bourse : " + s.economy().purse());
                w.check(smith.opinionOf(w.ruler.getUUID()) == 1, "il ne se souvient pas de la vente");
            }
            for (Entity x : spawned) x.discard();
            h.succeed();
        } finally {
            level.setDayTime(1000);
            NationBook.setForTests(null);
            fr.crusadecraft.core.world.WorldEditService.grantForTests(w.ruler.getUUID(), false);
            data.world().clear();
            w.cleanup();
        }
    }
}
