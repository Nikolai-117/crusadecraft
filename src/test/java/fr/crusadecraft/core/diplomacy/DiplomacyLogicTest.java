package fr.crusadecraft.core.diplomacy;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.OpinionModifier;
import fr.crusadecraft.core.character.OpinionReason;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomacyLogicTest {
    private final UUID player = UUID.randomUUID();
    private final UUID ai = UUID.randomUUID();

    private static DiplomacyRules.Context ctx(AiPersonality p, TitleRank aiRank, TitleRank playerRank, int playerStrength, int aiStrength,
                                              int dread, Trait... traits) {
        return new DiplomacyRules.Context(p, List.of(traits), aiRank, playerRank, 50, dread, false, 0, playerStrength, aiStrength);
    }

    @Test
    void traitesStatutsEtExpiration() {
        Relation r = new Relation(player, ai);
        assertEquals(DiploStatus.NEUTRE, r.status(player, 0));
        r.sign(Treaty.COMMERCE, 10);
        assertEquals(DiploStatus.COMMERCE, r.status(player, 10));
        r.sign(Treaty.NON_AGRESSION, 10);
        assertEquals(DiploStatus.NON_AGRESSION, r.status(player, 10), "le statut le plus fort l'emporte");
        assertEquals(List.of(), r.expire(49));
        assertEquals(List.of(Treaty.COMMERCE), r.expire(50), "le commerce dure 40 jours");
        r.sign(Treaty.ALLIANCE_DEFENSIVE, 10);
        r.sign(Treaty.ALLIANCE, 12);
        assertFalse(r.has(Treaty.ALLIANCE_DEFENSIVE, 12), "l'alliance totale remplace la défensive");
        assertTrue(r.has(Treaty.ALLIANCE, 100_000), "permanente");
        r.setHostile(true);
        r.sign(Treaty.PASSAGE, 13);
        assertFalse(r.hostile(), "un traité met fin à l'hostilité");
        assertEquals(r.a().equals(player) ? ai : player, r.other(r.a()).equals(ai) ? ai : player);
        assertEquals(Relation.key(player, ai), Relation.key(ai, player), "une relation par paire, quel que soit l'ordre");
    }

    @Test
    void vassaliteEtTribut() {
        Diplomacy d = new Diplomacy();
        Relation r = d.between(ai, player);
        r.makeVassal(player, 70, -5);
        assertEquals(50, r.vassalTax(), "impôt borné à 50 %");
        assertEquals(0, r.vassalLevy());
        assertEquals(DiploStatus.SUZERAIN, r.status(player, 0));
        assertEquals(DiploStatus.VASSAL, r.status(ai, 0));
        assertEquals(List.of(ai), d.vassalsOf(player));
        assertEquals(player, d.suzerainOf(ai).orElseThrow());
        assertTrue(d.suzerainOf(player).isEmpty());
        r.setTribute(ai, 7, 30);
        assertTrue(r.paysTribute(ai, 29));
        assertFalse(r.paysTribute(ai, 30));
        r.freeVassal();
        assertTrue(d.vassalsOf(player).isEmpty());
    }

    @Test
    void sauvegarde() {
        Diplomacy d = new Diplomacy();
        Relation r = d.between(player, ai);
        r.sign(Treaty.COMMERCE, 5);
        r.makeVassal(player, 30, 10);
        r.setTribute(ai, 4, 25);
        r.addOpinion(ai, OpinionModifier.of(OpinionReason.CADEAU, 5, 2));
        r.setLastAiLetterDay(6);
        CompoundTag tag = new CompoundTag();
        tag.put("d", d.save());
        Relation back = Diplomacy.load(tag, "d").find(ai, player).orElseThrow();
        assertTrue(back.has(Treaty.COMMERCE, 6), "un vassal garde ses accords commerciaux");
        assertEquals(player, back.suzerain());
        assertEquals(30, back.vassalTax());
        assertTrue(back.paysTribute(ai, 10));
        assertEquals(1, back.opinionOf(ai).size());
        assertEquals(20, back.opinionOf(ai).get(0).value());
        assertEquals(6, back.lastAiLetterDay());
    }

    @Test
    void opinionDUnSouverainIA() {
        Relation r = new Relation(player, ai);
        DiplomacyRules.Context pacific = ctx(AiPersonality.PACIFIQUE, TitleRank.COMTE, TitleRank.BARONNIE, 50, 50, 0, Trait.CONFIANT);
        int base = Opinion.total(DiplomacyRules.opinion(r, ai, player, pacific, 0));
        assertEquals(10 + 10 + 1 - 5, base, "pacifique, confiant, prestige 50, un rang de moins");
        r.sign(Treaty.ALLIANCE, 0);
        assertEquals(base + 25, Opinion.total(DiplomacyRules.opinion(r, ai, player, pacific, 1)));
        r.makeVassal(player, 40, 20);
        int vassal = Opinion.total(DiplomacyRules.opinion(r, ai, player, pacific, 1));
        assertEquals(base + 25 - 15, vassal, "un impôt de 40 % pèse sur le vassal");
    }

    @Test
    void reponsesPrevues() {
        Relation r = new Relation(player, ai);
        DiplomacyRules.Context merchant = ctx(AiPersonality.MARCHAND, TitleRank.COMTE, TitleRank.BARONNIE, 50, 50, 0);
        assertTrue(DiplomacyRules.evaluate(DiplomacyRules.Proposal.COMMERCE, 0, merchant, r, ai, player, 20, 20, 0).accepted(),
                "un marchand aime le commerce");
        assertFalse(DiplomacyRules.evaluate(DiplomacyRules.Proposal.ALLIANCE, 30, merchant, r, ai, player, 20, 20, 0).accepted());

        // Un comté ne prête pas hommage à un baron ; une baronnie, si.
        DiplomacyRules.Verdict rank = DiplomacyRules.evaluate(DiplomacyRules.Proposal.VASSALISATION, 50, merchant, r, ai, player, 20, 20, 0);
        assertEquals("crusadecraft.diplo.block.rank", rank.blockedKey());
        DiplomacyRules.Context barony = ctx(AiPersonality.PACIFIQUE, TitleRank.BARONNIE, TitleRank.BARONNIE, 120, 40, 20);
        DiplomacyRules.Verdict homage = DiplomacyRules.evaluate(DiplomacyRules.Proposal.VASSALISATION, 15, barony, r, ai, player, 20, 20, 0);
        assertNull(homage.blockedKey());
        assertTrue(homage.accepted(), "une seigneurie plus faible, bien disposée, accepte : " + homage.total());
        DiplomacyRules.Verdict greedy = DiplomacyRules.evaluate(DiplomacyRules.Proposal.VASSALISATION, 15, barony, r, ai, player, 50, 50, 0);
        assertTrue(greedy.total() < homage.total(), "un contrat dur fait baisser les chances");

        // Le tribut : la force compte, l'opinion peu.
        DiplomacyRules.Context weak = ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.BARONNIE, 300, 50, 40);
        assertTrue(DiplomacyRules.evaluate(DiplomacyRules.Proposal.TRIBUT, -50, weak, r, ai, player, 20, 20, 0).accepted());
        DiplomacyRules.Context strong = ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.BARONNIE, 50, 100, 0);
        assertFalse(DiplomacyRules.evaluate(DiplomacyRules.Proposal.TRIBUT, 80, strong, r, ai, player, 20, 20, 0).accepted());

        // Déjà en vigueur, ou en guerre : impossible.
        r.sign(Treaty.COMMERCE, 0);
        assertEquals("crusadecraft.diplo.block.already",
                DiplomacyRules.evaluate(DiplomacyRules.Proposal.COMMERCE, 100, merchant, r, ai, player, 20, 20, 1).blockedKey());
        r.setWar(true);
        assertEquals("crusadecraft.diplo.block.war",
                DiplomacyRules.evaluate(DiplomacyRules.Proposal.NON_AGRESSION, 100, merchant, r, ai, player, 20, 20, 1).blockedKey());
    }

    @Test
    void cadeauxEtTributs() {
        assertEquals(13, DiplomacyRules.giftOpinion(25, List.of()));
        assertEquals(30, DiplomacyRules.giftOpinion(100, List.of()), "plafonné");
        assertEquals(45, DiplomacyRules.giftOpinion(100, List.of(Trait.AVARE)), "un avare apprécie davantage");
        assertEquals(2, DiplomacyRules.tributeFor(5));
        assertEquals(12, DiplomacyRules.tributeFor(120));
        assertTrue(DiplomacyRules.canVassalize(TitleRank.BARONNIE, TitleRank.BARONNIE));
        assertFalse(DiplomacyRules.canVassalize(TitleRank.COMTE, TitleRank.COMTE));
        assertTrue(DiplomacyRules.canVassalize(TitleRank.COMTE, TitleRank.DUCHE));
    }

    @Test
    void lettreAllerRetour() {
        Letter l = new Letter(UUID.randomUUID(), ai, true, player, Component.literal("Godefroy"), Component.literal("Hommage"),
                Component.translatable("crusadecraft.letter.homage.body", "Ambrevent", 20, 10),
                List.of(new Letter.Choice(Letter.Action.SEAL_VASSALAGE, true), new Letter.Choice(Letter.Action.REFUSE, false)),
                4, 7, 1200).withContract(20, 10).withClaim(3);
        l.markDelivered();
        Letter back = Letter.load(l.save());
        assertEquals(l.id(), back.id());
        assertTrue(back.fromAi());
        assertTrue(back.delivered());
        assertEquals(2, back.choices().size());
        assertTrue(back.choices().get(0).needsSeal());
        assertEquals(Letter.Action.REFUSE, back.choices().get(1).action());
        assertEquals(20, back.tax());
        assertEquals(10, back.levy());
        assertEquals(3, back.claimProvince());
        assertNotNull(back.body());
        assertTrue(back.arrived(1200));
        assertFalse(back.arrived(1199));
        assertTrue(back.expired(7));
    }
}
