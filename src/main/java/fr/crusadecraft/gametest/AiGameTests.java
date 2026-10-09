package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ai.AiBrain;
import fr.crusadecraft.core.ai.Coalition;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarService;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.core.world.WorldService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** L'IA des royaumes : une guerre entre IA qui déplace une frontière, une coalition, une IA qui attaque un joueur haï. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AiGameTests {

    private AiGameTests() {}

    private static Runnable testSettings() {
        int range = CrusadeConfig.SERVER.aiWarRange.get();
        int grace = CrusadeConfig.SERVER.aiGraceDays.get();
        int delay = CrusadeConfig.SERVER.letterDelaySeconds.get();
        boolean decisions = CrusadeConfig.SERVER.aiDecisions.get();
        boolean sieges = CrusadeConfig.SERVER.sieges.get();
        boolean raids = CrusadeConfig.SERVER.raids.get();
        CrusadeConfig.SERVER.aiWarRange.set(100000);
        CrusadeConfig.SERVER.letterDelaySeconds.set(0);
        // Pas de décisions spontanées pendant le test : on appelle l'IA nous-mêmes.
        CrusadeConfig.SERVER.aiDecisions.set(false);
        CrusadeConfig.SERVER.sieges.set(false);
        CrusadeConfig.SERVER.raids.set(false);
        return () -> {
            CrusadeConfig.SERVER.aiWarRange.set(range);
            CrusadeConfig.SERVER.aiGraceDays.set(grace);
            CrusadeConfig.SERVER.letterDelaySeconds.set(delay);
            CrusadeConfig.SERVER.aiDecisions.set(decisions);
            CrusadeConfig.SERVER.sieges.set(sieges);
            CrusadeConfig.SERVER.raids.set(raids);
        };
    }

    private static List<AiRealm> generate(TestWorld w) {
        var server = w.helper.getLevel().getServer();
        WorldService.generate(server, w.helper.getLevel(), w.center.getX(), w.center.getZ());
        long today = RealmService.currentDay(server);
        List<AiRealm> realms = new ArrayList<>(CrusadeSavedData.get(server).world().realms());
        for (AiRealm r : realms) r.discover(w.ruler.getUUID(), today);
        return realms;
    }

    private static void cleanup(TestWorld w, Runnable restore) {
        var server = w.helper.getLevel().getServer();
        var data = CrusadeSavedData.get(server);
        for (War war : new ArrayList<>(data.wars().all())) {
            data.wars().damage().release(war.id());
            data.wars().remove(war.id());
            WarFieldService.onWarEnded(server, war.id());
        }
        data.wars().damage().take(Integer.MAX_VALUE);
        for (AiRealm r : data.world().realms()) data.diplomacy().forget(r.id());
        if (w.realm != null) data.diplomacy().forget(w.realm.id());
        data.world().clear();
        restore.run();
        w.cleanup();
    }

    /** Une haine franche : rivalité ouverte et trois insultes. */
    private static void hate(Relation rel, AiRealm who, long today) {
        rel.setHostile(true);
        for (int i = 0; i < 3; i++) rel.addOpinion(who.id(), OpinionModifier.of(OpinionReason.INSULTE, today, 1));
    }

    /** Toutes les autres IA à l'abri d'un pacte : la seule proie possible est celle qu'on veut. */
    private static void pacifyAllBut(TestWorld w, List<AiRealm> realms, AiRealm attacker, AiRealm spared, long today) {
        var data = CrusadeSavedData.get(w.helper.getLevel().getServer());
        for (AiRealm r : realms) if (r != attacker && r != spared) data.diplomacy().between(attacker.id(), r.id()).sign(Treaty.NON_AGRESSION, today);
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p8_frontiere", timeoutTicks = 200)
    public static void guerreEntreIADeplaceUneFrontiere(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            List<AiRealm> realms = generate(w);
            AiRealm attacker = realms.stream().filter(r -> r.rank().isAtLeast(TitleRank.COMTE)).findFirst().orElseThrow();
            AiRealm victim = realms.stream().filter(r -> r != attacker && r.rank().isAtLeast(TitleRank.COMTE)).findFirst().orElseThrow();
            long today = RealmService.currentDay(server);
            for (AiProvince p : victim.provinces()) p.setPopulation(1);
            hate(data.diplomacy().between(attacker.id(), victim.id()), attacker, today);
            pacifyAllBut(w, realms, attacker, victim, today);

            // L'agresseur trouve sa proie et lui déclare la guerre pour une province.
            w.check(AiBrain.considerWar(server, attacker), "l'IA ne déclare pas la guerre à une proie haïe et sans défense");
            War war = data.wars().between(attacker.id(), victim.id()).orElseThrow(() -> new IllegalStateException("guerre contre une autre cible"));
            w.check(war.cb() == CasusBelli.CONQUEST && war.targetProvince() >= 0, "motif : " + war.cb() + " / " + war.targetProvince());
            w.check(data.world().news().stream().anyMatch(n -> n.key().equals("crusadecraft.news.war")), "la guerre n'est pas dans les nouvelles");

            // La campagne tourne d'elle-même ; on force l'issue : la place visée tombe, l'IA vaincue cède.
            AiProvince target = WarService.provincesOf(server, victim.id()).stream().filter(p -> p.id() == war.targetProvince()).findFirst().orElseThrow();
            war.occupied().add(target.id());
            war.addBattle(60);
            WarService.onNewDay(server, today + 1);
            w.check(data.wars().between(attacker.id(), victim.id()).isEmpty(), "les deux IA n'ont pas fait la paix");
            w.check(attacker.id().equals(target.holder()), "la province n'a pas changé de mains");
            w.check(WarService.provincesOf(server, attacker.id()).contains(target), "le vainqueur ne tient pas sa conquête");
            w.check(!victim.ownProvinces().contains(target), "le vaincu tient encore la province");
            w.check(data.world().news().stream().anyMatch(n -> n.key().equals("crusadecraft.news.province")), "la frontière n'est pas dans les nouvelles");
            w.check(data.diplomacy().between(attacker.id(), victim.id()).truce(today + 1), "pas de trêve entre les deux IA");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p8_coalition", timeoutTicks = 200)
    public static void coalitionContreUnJoueurMenacant(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            List<AiRealm> realms = generate(w);
            long today = RealmService.currentDay(server);
            for (AiRealm r : realms) data.diplomacy().between(r.id(), w.realm.id()).setHostile(true);
            // Sous le seuil, personne ne bouge.
            w.realm.setThreat(10);
            AiBrain.coalitions(server, today);
            w.check(data.world().coalitionAgainst(w.realm.id()).isEmpty(), "une coalition sans menace");

            w.realm.setThreat(100);
            AiBrain.coalitions(server, today);
            Coalition c = data.world().coalitionAgainst(w.realm.id()).orElseThrow(() -> new IllegalStateException("aucune coalition"));
            w.check(c.active() && c.announced(), "coalition : " + c.members().size() + " membre(s), annoncée " + c.announced());
            w.check(data.wars().all().isEmpty(), "la coalition attaque pendant le répit du royaume");

            // Attaquer l'un des membres, c'est les avoir tous contre soi.
            AiRealm member = data.world().realm(c.members().iterator().next()).orElseThrow();
            w.check(WarService.declare(w.ruler, member.id(), CasusBelli.HUMILIATE, -1).success(), "déclaration refusée");
            War war = data.wars().between(w.realm.id(), member.id()).orElseThrow();
            for (var m : c.members()) w.check(war.side(m) == -1, "un membre de la coalition reste à l'écart");

            // La menace retombe : la coalition tient tant que dure la guerre, puis se défait.
            w.realm.setThreat(0);
            AiBrain.coalitions(server, today);
            w.check(data.world().coalitionAgainst(w.realm.id()).isPresent(), "coalition dissoute en pleine guerre");
            WarService.endWar(server, war, 0);
            AiBrain.coalitions(server, today);
            w.check(data.world().coalitionAgainst(w.realm.id()).isEmpty(), "la coalition survit à la menace");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p8_monde", timeoutTicks = 400)
    public static void soixanteJoursDePolitique(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Ormesval");
        Runnable restore = testSettings();
        int chance = CrusadeConfig.SERVER.aiDecisionChance.get();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            generate(w);
            CrusadeConfig.SERVER.aiDecisions.set(true);
            CrusadeConfig.SERVER.aiDecisionChance.set(100);
            long today = RealmService.currentDay(server);
            int wars = 0;
            // Le jour où le test voit chaque guerre pour la première fois : la simulation fait passer les jours sans faire
            // avancer l'horloge du serveur, si bien qu'une guerre déclarée tard porte la date réelle, pas la date simulée.
            java.util.Map<java.util.UUID, Long> seen = new java.util.HashMap<>();
            for (long d = today + 1; d <= today + 60; d++) {
                int before = data.wars().all().size();
                WarService.onNewDay(server, d);
                AiBrain.onNewDay(server, d);
                if (data.wars().all().size() > before) wars += data.wars().all().size() - before;
                for (War war : data.wars().all()) {
                    // Seules les guerres entre IA finissent d'elles-mêmes ; contre un joueur, il faut négocier.
                    boolean betweenAis = data.world().realm(war.attacker()).isPresent() && data.world().realm(war.defender()).isPresent();
                    long day = d;
                    long since = day - seen.computeIfAbsent(war.id(), k -> day);
                    w.check(!betweenAis || since <= 31, "une guerre entre IA ne finit jamais (" + since + " jours)");
                    w.check(!war.attacker().equals(war.defender()), "un royaume en guerre contre lui-même");
                }
            }
            for (AiRealm r : data.world().realms()) w.check(r.gold() > Long.MIN_VALUE / 2, "trésor IA corrompu");
            CrusadeCraft.LOGGER.info("CrusadeCraft (test) : 60 jours, {} guerre(s) déclarée(s), {} nouvelle(s)", wars, data.world().news().size());
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.aiDecisionChance.set(chance);
            cleanup(w, restore);
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p8_ia_attaque", timeoutTicks = 200)
    public static void uneIAAttaqueUnJoueurHai(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            List<AiRealm> realms = generate(w);
            AiRealm attacker = realms.stream().filter(r -> r.rank().isAtLeast(TitleRank.COMTE)).findFirst().orElseThrow();
            long today = RealmService.currentDay(server);
            pacifyAllBut(w, realms, attacker, null, today);
            Relation rel = data.diplomacy().between(attacker.id(), w.realm.id());
            hate(rel, attacker, today);

            // Pendant le répit qui suit la fondation, rien.
            CrusadeConfig.SERVER.aiGraceDays.set(10);
            w.check(!AiBrain.considerWar(server, attacker), "attaque pendant le répit");
            CrusadeConfig.SERVER.aiGraceDays.set(0);
            w.check(AiBrain.considerWar(server, attacker), "l'IA n'attaque pas un joueur haï et faible");
            War war = data.wars().between(attacker.id(), w.realm.id()).orElseThrow(() -> new IllegalStateException("pas de guerre contre le joueur"));
            w.check(war.cb() == CasusBelli.HUMILIATE, "motif contre un joueur sans conquête : " + war.cb());
            w.check(war.attacker().equals(attacker.id()), "le joueur est l'attaquant");
            w.check(w.realm.mail().stream().anyMatch(l -> l.from().equals(attacker.id())), "pas de lettre de déclaration");
            Optional<WorldNews> news = data.world().news().stream().filter(n -> n.key().equals("crusadecraft.news.war")).findFirst();
            w.check(news.isPresent() && AiBrain.knows(server, w.realm, news.get()), "le joueur n'apprend pas la nouvelle");
            h.succeed();
        } finally {
            cleanup(w, restore);
        }
    }
}
