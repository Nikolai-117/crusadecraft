package fr.crusadecraft.core.ai;

import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.WorldNews;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiLogicTest {

    private static AiRules.Target ai(int opinion, int mine, int theirs, double distance, int provinces) {
        return new AiRules.Target(UUID.randomUUID(), false, opinion, false, mine, theirs, distance, provinces, TitleRank.COMTE);
    }

    private static AiRules.Target player(int opinion, boolean hostile, int mine, int theirs) {
        return new AiRules.Target(UUID.randomUUID(), true, opinion, hostile, mine, theirs, 500, 0, TitleRank.BARONNIE);
    }

    @Test
    void temperamentEtSeuilDeGuerre() {
        assertEquals(Double.POSITIVE_INFINITY, AiRules.requiredRatio(AiPersonality.PACIFIQUE, 0), "un pacifique n'attaque pas un voisin indifférent");
        assertEquals(2.5, AiRules.requiredRatio(AiPersonality.PACIFIQUE, -60));
        assertTrue(AiRules.requiredRatio(AiPersonality.EXPANSIONNISTE, -20) < AiRules.requiredRatio(AiPersonality.EXPANSIONNISTE, 0),
                "la haine abaisse le seuil");
        assertTrue(AiRules.requiredRatio(AiPersonality.EXPANSIONNISTE, 0) < AiRules.requiredRatio(AiPersonality.OPPORTUNISTE, 0));
    }

    @Test
    void choixDeLaCible() {
        AiRules.Target weak = ai(0, 300, 100, 800, 2);
        AiRules.Target strong = ai(0, 300, 280, 600, 3);
        AiRules.Target far = ai(-50, 300, 50, 5000, 1);
        AiRules.Target landless = ai(-50, 300, 10, 300, 0);
        assertEquals(weak, AiRules.pickTarget(AiPersonality.EXPANSIONNISTE, List.of(strong, weak, far, landless), 2500));
        assertNull(AiRules.pickTarget(AiPersonality.EXPANSIONNISTE, List.of(strong, far, landless), 2500), "rien à sa mesure, ou hors de portée");
        assertNull(AiRules.pickTarget(AiPersonality.MARCHAND, List.of(weak), 2500), "un marchand ne se bat pas pour rien");
        // Un joueur paisible n'est jamais visé ; en rivalité ouverte ou haï, il peut l'être.
        assertNull(AiRules.pickTarget(AiPersonality.EXPANSIONNISTE, List.of(player(0, false, 400, 50)), 2500));
        assertTrue(AiRules.pickTarget(AiPersonality.EXPANSIONNISTE, List.of(player(0, true, 400, 50)), 2500).player());
        assertTrue(AiRules.pickTarget(AiPersonality.EXPANSIONNISTE, List.of(player(-45, false, 400, 50)), 2500).player());
    }

    @Test
    void motifChoisiParLIA() {
        assertEquals(CasusBelli.CONQUEST, AiRules.casusBelli(TitleRank.COMTE, ai(0, 1, 1, 1, 2)));
        AiRules.Target baron = new AiRules.Target(UUID.randomUUID(), false, 0, false, 1, 1, 1, 1, TitleRank.BARONNIE);
        assertEquals(CasusBelli.HUMILIATE, AiRules.casusBelli(TitleRank.BARONNIE, baron), "un baron ne soumet pas un baron");
        assertEquals(CasusBelli.CONQUEST, AiRules.casusBelli(TitleRank.DUCHE, baron));
        assertEquals(CasusBelli.HUMILIATE, AiRules.casusBelli(TitleRank.DUCHE, player(-50, true, 1, 1)),
                "un joueur sans province conquise n'est jamais soumis : il est humilié");
    }

    @Test
    void campagneEtPaixEntreIA() {
        assertEquals(0.1, AiRules.occupationChance(100, 100), 1e-9);
        assertEquals(0.35, AiRules.occupationChance(1000, 10), 1e-9);
        assertEquals(0.03, AiRules.occupationChance(1, 1000), 1e-9);
        assertEquals(1, AiRules.peaceBetweenAis(55, 10, 10, 3));
        assertEquals(-1, AiRules.peaceBetweenAis(-55, 10, 10, 3));
        assertNull(AiRules.peaceBetweenAis(20, 30, 30, 10), "la guerre continue");
        assertEquals(0, AiRules.peaceBetweenAis(10, 30, 30, 30), "trop longue et indécise : paix blanche");
        assertEquals(1, AiRules.peaceBetweenAis(25, 65, 65, 10));
        assertEquals(-1, AiRules.peaceBetweenAis(-15, 90, 20, 5), "l'attaquant épuisé et en perte cède");
        assertTrue(AiRules.offersWhitePeace(10, 75, 5));
        assertFalse(AiRules.offersWhitePeace(40, 90, 50), "qui gagne ne propose pas la paix blanche");
    }

    @Test
    void traitesEntreIA() {
        Set<Treaty> none = EnumSet.noneOf(Treaty.class);
        assertEquals(Treaty.ALLIANCE, AiRules.treaty(AiPersonality.OPPORTUNISTE, AiPersonality.OPPORTUNISTE, 70, 65, none));
        assertEquals(Treaty.ALLIANCE_DEFENSIVE, AiRules.treaty(AiPersonality.OPPORTUNISTE, AiPersonality.OPPORTUNISTE, 70, 45, none),
                "il faut que chacun estime l'autre");
        assertEquals(Treaty.COMMERCE, AiRules.treaty(AiPersonality.MARCHAND, AiPersonality.FANATIQUE, 10, 6, none));
        assertEquals(Treaty.NON_AGRESSION, AiRules.treaty(AiPersonality.PACIFIQUE, AiPersonality.FANATIQUE, 0, 2, none));
        assertNull(AiRules.treaty(AiPersonality.PACIFIQUE, AiPersonality.FANATIQUE, 0, 2, EnumSet.of(Treaty.NON_AGRESSION)));
        assertNull(AiRules.treaty(AiPersonality.EXPANSIONNISTE, AiPersonality.FANATIQUE, 30, 30, none));
    }

    @Test
    void menaceEtCoalitions() {
        assertEquals(48, AiRules.decayThreat(50, false));
        assertEquals(49, AiRules.decayThreat(50, true), "en guerre, la menace retombe moins vite");
        assertEquals(0, AiRules.decayThreat(1, false));
        assertEquals(AiRules.THREAT_MAX, AiRules.addThreat(190, 40));
        assertEquals(0, AiRules.threatOpinion(0));
        assertEquals(-20, AiRules.threatOpinion(60));
        assertEquals(-40, AiRules.threatOpinion(200), "l'inquiétude est plafonnée");
        assertFalse(AiRules.joinsCoalition(AiPersonality.PACIFIQUE, -50, 59, 60, false), "sous le seuil, pas de coalition");
        assertFalse(AiRules.joinsCoalition(AiPersonality.PACIFIQUE, -50, 80, 60, true), "un allié ne trahit pas");
        assertTrue(AiRules.joinsCoalition(AiPersonality.PACIFIQUE, -10, 80, 60, false), "les pacifiques prennent peur tôt");
        assertFalse(AiRules.joinsCoalition(AiPersonality.EXPANSIONNISTE, -20, 80, 60, false));
        assertTrue(AiRules.joinsCoalition(AiPersonality.EXPANSIONNISTE, -30, 80, 60, false));
        assertTrue(AiRules.coalitionDissolves(29, 60));
        assertFalse(AiRules.coalitionDissolves(30, 60));
        assertFalse(AiRules.coalitionAttacks(500, 100, 1), "une coalition, c'est au moins deux");
        assertTrue(AiRules.coalitionAttacks(120, 100, 2));
        assertFalse(AiRules.coalitionAttacks(110, 100, 3));
    }

    @Test
    void coalitionEtNouvellesSauvegardees() {
        UUID target = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        Coalition c = new Coalition(target, 12);
        assertFalse(c.active());
        c.members().add(a);
        c.members().add(b);
        c.setAnnounced(true);
        Coalition back = Coalition.load(c.save());
        assertEquals(target, back.target());
        assertEquals(12, back.formedDay());
        assertTrue(back.active());
        assertTrue(back.announced());
        assertEquals(c.members(), back.members());

        WorldNews n = WorldNews.of(30, "crusadecraft.news.war", List.of(a, b), Component.literal("Ambrevent"), Component.literal("Valcourt"),
                Component.translatable("crusadecraft.cb.conquest"));
        WorldNews loaded = WorldNews.load(n.save());
        assertEquals(30, loaded.day());
        assertEquals(List.of(a, b), loaded.realms());
        assertEquals(n.args(), loaded.args());
        assertEquals("crusadecraft.news.war", loaded.key());
    }
}
