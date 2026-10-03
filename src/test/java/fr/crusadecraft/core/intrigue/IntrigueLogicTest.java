package fr.crusadecraft.core.intrigue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.event.EventDef;
import fr.crusadecraft.core.event.PendingEvent;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.WorldNews;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntrigueLogicTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    @Test
    void mecontentementEtFactions() {
        assertTrue(IntrigueRules.discontent(-20, List.of()));
        assertFalse(IntrigueRules.discontent(-19, List.of()));
        assertFalse(IntrigueRules.discontent(-100, List.of(Trait.LOYAL)), "un loyal ne se ligue jamais");
        assertTrue(IntrigueRules.discontent(0, List.of(Trait.AMBITIEUX)), "un ambitieux, dès qu'il n'aime plus");
        assertFalse(IntrigueRules.discontent(-30, List.of(Trait.CONTENT)));
        assertEquals(3, IntrigueRules.weight(true));
        assertEquals(2, IntrigueRules.weight(false));
        assertEquals(0, IntrigueRules.power(0, 10, 0, 0));
        assertEquals(50, IntrigueRules.power(5, 10, 0, 0));
        assertEquals(40, IntrigueRules.power(5, 10, 20, 0), "la crainte entame la puissance de moitié");
        assertEquals(30, IntrigueRules.power(5, 10, 0, 20), "l'ordre maintenu aussi");
        assertTrue(IntrigueRules.ultimatum(60, 2, 10));
        assertFalse(IntrigueRules.ultimatum(60, 1, 10), "un homme seul ne pose pas d'ultimatum");
        assertFalse(IntrigueRules.ultimatum(60, 3, 9), "pas deux ultimatums coup sur coup");
        assertEquals(0, IntrigueRules.hatedScore(List.of(Trait.BRAVE, Trait.HONNETE)));
        assertEquals(4, IntrigueRules.hatedScore(List.of(Trait.CORROMPU, Trait.CRUEL)));
    }

    @Test
    void complotsEtDecouverte() {
        assertFalse(IntrigueRules.startsPlot(-100, List.of(Trait.HONNETE), 0), "un honnête ne complote pas");
        assertTrue(IntrigueRules.startsPlot(-30, List.of(Trait.FOURBE), 0.01));
        assertFalse(IntrigueRules.startsPlot(-30, List.of(), 0.01), "il faut haïr davantage sans être fourbe");
        assertTrue(IntrigueRules.startsPlot(-50, List.of(), 0.01));
        assertFalse(IntrigueRules.startsPlot(-50, List.of(), 0.5), "5 % par jour seulement");
        assertEquals(Plot.Kind.DETOURNEMENT, IntrigueRules.plotKind(List.of(Trait.AVARE), 0.9));
        assertEquals(Plot.Kind.SABOTAGE, IntrigueRules.plotKind(List.of(Trait.COLERIQUE), 0.1));
        assertEquals(10, IntrigueRules.plotProgress(14));
        assertEquals(0, IntrigueRules.discovery(0, 5, true), "sans maître-espion, rien ne se découvre");
        assertEquals(0.05, IntrigueRules.discovery(1, 30, true), 1e-9);
        assertEquals(0.6, IntrigueRules.discovery(40, 0, true), 1e-9);
        assertTrue(IntrigueRules.discovery(15, 6, true) > IntrigueRules.discovery(15, 6, false), "débusquer les complots triple les chances");
    }

    @Test
    void secretsEtRevoltes() {
        assertNull(IntrigueRules.secretFor(List.of(), 0.5));
        assertEquals(Secret.Kind.CORRUPTION, IntrigueRules.secretFor(List.of(Trait.AVARE), 0.015));
        assertEquals(Secret.Kind.HERESIE, IntrigueRules.secretFor(List.of(Trait.CYNIQUE), 0.015));
        assertEquals(Secret.Kind.DETTES_DE_JEU, IntrigueRules.secretFor(List.of(), 0.005));
        assertEquals(Secret.Kind.LIAISON, IntrigueRules.secretFor(List.of(), 0.015));
        assertEquals(0, IntrigueRules.revoltChance(6, 2, 0), "un peuple heureux ne se révolte pas");
        assertEquals(0, IntrigueRules.revoltChance(2, 0, 0), "ni sous un impôt léger");
        assertTrue(IntrigueRules.revoltChance(2, 2, 0) > IntrigueRules.revoltChance(2, 1, 0));
        assertTrue(IntrigueRules.revoltChance(2, 2, 20) < IntrigueRules.revoltChance(2, 2, 0), "le maréchal apaise");
        assertTrue(IntrigueRules.revoltChance(0, 2, 0) <= 0.3);
    }

    private static EventDef.EventContext ctx(long gold, boolean war, Post... posts) {
        EnumSet<Post> set = EnumSet.noneOf(Post.class);
        set.addAll(List.of(posts));
        return new EventDef.EventContext(12, gold, 100, 0, 20, war, set, TitleRank.COMTE, 30, 6.5, Map.of(Law.FISCALITE, 1));
    }

    @Test
    void evenementLuEtConditions() {
        JsonObject o = JsonParser.parseString("""
                {"title": "Un titre", "text": "Un texte pour %1$s", "icon": "minecraft:wheat", "weight": 5, "cooldown": 9,
                 "conditions": {"min_gold": 50, "at_war": false, "has_post": "chancelier", "min_law": {"fiscalite": 1}},
                 "options": [
                   {"text": "Payer", "requires": {"min_gold": 100}, "effects": {"gold": -100, "prestige": 10,
                     "post_opinion": {"post": "maire", "value": 5}, "subject_status": "emprisonne:30", "law_down": "fiscalite"}},
                   {"text": "Rien"}],
                 "default": 1, "expires": 4}
                """).getAsJsonObject();
        EventDef d = EventDef.parse(ResourceLocation.tryParse("crusadecraft:essai"), o);
        assertEquals(EventDef.RANDOM, d.trigger());
        assertEquals(5, d.weight());
        assertEquals(9, d.cooldown());
        assertEquals(1, d.defaultOption());
        assertEquals(4, d.expires());
        assertTrue(d.conditions().test(ctx(60, false, Post.CHANCELIER)));
        assertFalse(d.conditions().test(ctx(40, false, Post.CHANCELIER)), "pas assez d'or");
        assertFalse(d.conditions().test(ctx(60, true, Post.CHANCELIER)), "pas en guerre");
        assertFalse(d.conditions().test(ctx(60, false)), "il faut un chancelier");
        assertFalse(d.options().get(0).requires().test(ctx(60, false)), "l'option demande 100 pièces");
        EventDef.Effects fx = d.options().get(0).effects();
        assertEquals(-100, fx.gold());
        assertEquals(Post.MAIRE, fx.post());
        assertEquals(30, fx.statusDays());
        assertEquals(Law.FISCALITE, fx.lawDown());
        assertEquals(5, fx.lines().size(), "or, prestige, maires, statut, loi : chacun sa ligne");
        assertTrue(d.options().get(1).effects().lines().isEmpty());
    }

    /** Tous les événements livrés avec le mod se lisent, et chacun a des options sensées. */
    @Test
    void evenementsLivresValides() throws IOException {
        Path dir = Path.of("src/main/resources/data/crusadecraft/crusade_events");
        int n = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                EventDef d = EventDef.parse(ResourceLocation.tryParse("crusadecraft:" + f.getFileName().toString().replace(".json", "")), o);
                assertFalse(d.options().isEmpty(), f + " : aucune option");
                assertTrue(d.defaultOption() < d.options().size(), f + " : option par défaut hors limites");
                assertTrue(d.options().get(d.defaultOption()).requires() == EventDef.Conditions.NONE
                        || d.options().get(d.defaultOption()).requires().minGold() == null, f + " : l'option par défaut doit toujours être possible");
                n++;
            }
        }
        assertTrue(n >= 20, "événements livrés : " + n);
    }

    @Test
    void etatDesIntriguesSauvegarde() {
        ColonyRef colony = new ColonyRef(OW, 3);
        CitizenRef a = new CitizenRef(colony, 7), b = new CitizenRef(colony, 9);
        IntrigueState s = new IntrigueState();
        Faction f = new Faction(Faction.Kind.INDEPENDANCE, 12);
        f.members().add(a);
        f.setColony(colony);
        f.setDiscovered(true);
        f.setLastUltimatumDay(15);
        s.factions().put(f.kind(), f);
        Plot p = new Plot(UUID.randomUUID(), b, Plot.Kind.SABOTAGE, 14);
        p.setProgress(130);
        s.plots().add(p);
        Secret sec = new Secret(UUID.randomUUID(), a, Secret.Kind.HERESIE);
        sec.setKnown(true);
        s.secrets().add(sec);
        s.setRevolt(colony, 40);
        s.hook(b, 70);
        s.pending().add(new PendingEvent(UUID.randomUUID(), "crusadecraft:revolt", 20, 22, null, "", "Pierrefeu", colony.toString()));
        s.eventCooldowns().put("crusadecraft:famine", 18L);
        s.record(WorldNews.of(20, "crusadecraft.chronicle.law", List.of(), Component.literal("Fiscalité")));
        IntrigueState back = IntrigueState.load(s.save());
        Faction bf = back.factions().get(Faction.Kind.INDEPENDANCE);
        assertNotNull(bf);
        assertEquals(colony, bf.colony());
        assertTrue(bf.discovered());
        assertEquals(15, bf.lastUltimatumDay());
        assertEquals(List.of(a), List.copyOf(bf.members()));
        assertEquals(100, back.plots().get(0).progress(), "un complot ne dépasse pas 100 %");
        assertEquals(Plot.Kind.SABOTAGE, back.plots().get(0).kind());
        assertTrue(back.secret(sec.id()).orElseThrow().known());
        assertTrue(back.inRevolt(colony, 39));
        assertFalse(back.inRevolt(colony, 40));
        assertTrue(back.hooked(b, 69));
        assertEquals("Pierrefeu", back.pending().get(0).place());
        assertEquals(18L, back.eventCooldowns().get("crusadecraft:famine"));
        assertEquals(1, back.chronicle().size());
        back.forget(a);
        assertTrue(back.factions().get(Faction.Kind.INDEPENDANCE).members().isEmpty());
        assertTrue(back.secrets().isEmpty());
    }
}
