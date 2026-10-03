package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.compat.minecolonies.MineColoniesTestKit;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.event.EventDef;
import fr.crusadecraft.core.event.EventService;
import fr.crusadecraft.core.event.PendingEvent;
import fr.crusadecraft.core.intrigue.Faction;
import fr.crusadecraft.core.intrigue.IntrigueService;
import fr.crusadecraft.core.intrigue.Plot;
import fr.crusadecraft.core.intrigue.Secret;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Intrigues et événements de bout en bout : événement à choix, ultimatum d'une faction, complot éventé, secrets, chronique. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntrigueGameTests {

    private IntrigueGameTests() {}

    private static Runnable testSettings() {
        int refuse = CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get();
        boolean events = CrusadeConfig.SERVER.events.get();
        CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(-100);
        CrusadeConfig.SERVER.events.set(false);
        return () -> {
            CrusadeConfig.SERVER.refuseOfficeBelowOpinion.set(refuse);
            CrusadeConfig.SERVER.events.set(events);
        };
    }

    /** Un conseiller nommé, sans trait qui l'empêcherait de se liguer. */
    private static CitizenRef councillor(TestWorld w, Post post) {
        CitizenRef ref = MineColoniesTestKit.addCitizen(w.colony);
        w.check(CharacterService.act(w.ruler, ref, CharacterService.Action.APPOINT, post).success(), "conseiller non nommé : " + post);
        CharacterRecord rec = CrusadeSavedData.get(w.helper.getLevel().getServer()).character(ref).orElseThrow();
        rec.removeTrait(Trait.LOYAL);
        rec.removeTrait(Trait.CONTENT);
        return ref;
    }

    private static Optional<PendingEvent> pending(TestWorld w, String event) {
        return w.realm.intrigue().pending().stream().filter(e -> e.event().equals(event)).findFirst();
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p9_evenement", timeoutTicks = 200)
    public static void evenementAChoixEtChronique(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            w.check(EventService.events().size() >= 20, "événements chargés : " + EventService.events().size());
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.founded")),
                    "la fondation n'est pas dans la chronique");
            long today = RealmService.currentDay(server);

            // Une révolte : laisser la colère retomber suspend l'impôt de la province.
            Optional<PendingEvent> revolt = EventService.trigger(server, w.realm, "revolt", null, "", "Valbrume", w.colony.toString());
            w.check(revolt.isPresent(), "aucun événement de révolte");
            EventDef def = EventService.def(revolt.get().event()).orElseThrow();
            int wait = def.options().size() - 1;
            w.check(EventService.answer(w.ruler, revolt.get().id(), wait).success(), "décision refusée");
            w.check(w.realm.intrigue().inRevolt(w.colony, today), "la province n'est pas en révolte");
            w.check(TreasuryService.forecast(server, w.realm, 1.0).taxes() == 0, "une province en révolte paie encore l'impôt");
            w.check(w.realm.intrigue().pending().isEmpty(), "l'événement attend encore");

            // Un événement au hasard, laissé sans réponse : l'option par défaut s'applique à l'échéance.
            w.realm.setGold(1000);
            Optional<PendingEvent> random = EventService.randomEvent(server, w.realm, new Random(3));
            w.check(random.isPresent(), "aucun événement possible");
            EventService.onNewDay(server, w.realm, random.get().expires());
            w.check(w.realm.intrigue().pending().isEmpty(), "l'événement échu n'a pas été réglé");
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.event.auto")),
                    "la décision d'office n'est pas dans la chronique");
            w.check(!EventService.answer(w.ruler, random.get().id(), 0).success(), "on répond à un événement déjà réglé");
            h.succeed();
        } finally {
            restore.run();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p9_faction", timeoutTicks = 200)
    public static void factionEtUltimatum(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Hautemarche");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            long today = RealmService.currentDay(server);
            List<CitizenRef> council = new ArrayList<>();
            for (Post p : new Post[]{Post.CHANCELIER, Post.INTENDANT, Post.CHAPELAIN}) council.add(councillor(w, p));
            // La cour est furieuse : chacun a été jeté au cachot, autrefois.
            for (CitizenRef ref : council) data.character(ref).orElseThrow().addModifier(OpinionModifier.of(OpinionReason.EMPRISONNE, today, 1.5));
            w.realm.setDread(0);
            int before = w.realm.laws().level(Law.FISCALITE);
            w.check(before >= 1, "impôts déjà au plus bas");

            IntrigueService.onNewDay(server, w.realm, today);
            Faction f = w.realm.intrigue().factions().get(Faction.Kind.BAISSE_IMPOTS);
            w.check(f != null && f.members().size() == 3, "faction : " + (f == null ? "aucune" : f.members().size() + " membre(s)"));
            PendingEvent ultimatum = pending(w, "crusadecraft:faction_baisse_impots")
                    .orElseThrow(() -> new IllegalStateException("pas d'ultimatum malgré une cour unanime"));
            w.check(f.discovered(), "l'ultimatum n'a pas révélé la faction");

            // Céder : les impôts baissent, la faction se disperse, la chronique s'en souvient.
            w.check(EventService.answer(w.ruler, ultimatum.id(), 0).success(), "décision refusée");
            w.check(w.realm.laws().level(Law.FISCALITE) == before - 1, "impôts : " + w.realm.laws().level(Law.FISCALITE));
            w.check(!w.realm.intrigue().factions().containsKey(Faction.Kind.BAISSE_IMPOTS), "la faction survit à sa victoire");
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.faction.conceded")),
                    "l'ultimatum n'est pas dans la chronique");
            h.succeed();
        } finally {
            restore.run();
            w.cleanup();
        }
    }

    @GameTest(template = TestWorld.TEMPLATE, batch = "p9_complot", timeoutTicks = 200)
    public static void complotEventeEtSecrets(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Pierrefeu");
        Runnable restore = testSettings();
        try {
            var server = h.getLevel().getServer();
            var data = CrusadeSavedData.get(server);
            long today = RealmService.currentDay(server);
            CitizenRef spy = councillor(w, Post.MAITRE_ESPION);
            CitizenRef plotter = councillor(w, Post.INTENDANT);
            CitizenRef gossip = councillor(w, Post.CHAPELAIN);
            w.realm.court().setTask(Post.MAITRE_ESPION, fr.crusadecraft.core.council.CouncilTask.DEBUSQUER_COMPLOTS);

            // Un complot éventé : l'intendant finit au cachot et perd sa charge.
            Plot plot = new Plot(UUID.randomUUID(), plotter, Plot.Kind.DETOURNEMENT, today);
            plot.setDiscovered(true);
            w.realm.intrigue().plots().add(plot);
            PendingEvent e = EventService.trigger(server, w.realm, "plot_discovered", plotter, CharacterService.nameOf(server, plotter),
                    plot.kind().key(), plot.id().toString()).orElseThrow(() -> new IllegalStateException("pas d'événement de complot"));
            w.check(EventService.answer(w.ruler, e.id(), 0).success(), "décision refusée");
            w.check(w.realm.intrigue().plots().isEmpty(), "le complot survit");
            w.check(data.character(plotter).orElseThrow().status() == CharacterStatus.EMPRISONNE, "le comploteur n'est pas au cachot");
            w.check(w.realm.postOf(plotter) == null, "le comploteur garde sa charge");
            w.check(w.realm.intrigue().chronicle().stream().anyMatch(n -> n.key().equals("crusadecraft.chronicle.plot.imprisoned")),
                    "le complot n'est pas dans la chronique");

            // Un secret révélé : prestige, rancune ; un autre sert au chantage : le courtisan se tient tranquille.
            Secret exposed = new Secret(UUID.randomUUID(), gossip, Secret.Kind.HERESIE);
            exposed.setKnown(true);
            Secret kept = new Secret(UUID.randomUUID(), spy, Secret.Kind.DETTES_DE_JEU);
            kept.setKnown(true);
            w.realm.intrigue().secrets().add(exposed);
            w.realm.intrigue().secrets().add(kept);
            int prestige = w.realm.prestige(), piety = w.realm.piety();
            w.check(IntrigueService.expose(w.ruler, exposed.id()).success(), "révélation refusée");
            w.check(w.realm.prestige() == prestige + 5 && w.realm.piety() == piety + 10, "révéler une hérésie ne rapporte rien");
            w.check(data.character(gossip).orElseThrow().modifiers().stream().anyMatch(m -> m.reason() == OpinionReason.SECRET_REVELE),
                    "pas de rancune après la révélation");
            w.check(IntrigueService.blackmail(w.ruler, kept.id()).success(), "chantage refusé");
            w.check(w.realm.intrigue().hooked(spy, today + 30), "le courtisan n'est pas tenu");
            w.check(w.realm.intrigue().secrets().isEmpty(), "les secrets servis restent");
            w.check(!IntrigueService.blackmail(w.ruler, kept.id()).success(), "un secret sert deux fois");
            h.succeed();
        } finally {
            restore.run();
            w.cleanup();
        }
    }
}
