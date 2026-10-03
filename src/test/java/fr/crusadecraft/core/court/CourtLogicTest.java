package fr.crusadecraft.core.court;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarRules;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourtLogicTest {

    @Test
    void valeurDesJouteursEtTableau() {
        TournamentRules.Entrant plain = new TournamentRules.Entrant(0, "A", true, 10, List.of());
        TournamentRules.Entrant champion = new TournamentRules.Entrant(1, "B", false, 10, List.of(Trait.CHAMPION, Trait.VETERAN));
        TournamentRules.Entrant wounded = new TournamentRules.Entrant(2, "C", false, 10, List.of(Trait.BLESSE));
        assertTrue(TournamentRules.strength(champion, 0.5) > TournamentRules.strength(plain, 0.5));
        assertTrue(TournamentRules.strength(wounded, 0.5) < TournamentRules.strength(plain, 0.5));
        assertTrue(TournamentRules.strength(new TournamentRules.Entrant(3, "D", true, 0, List.of(Trait.BLESSE, Trait.LACHE)), 0) > 0,
                "même le plus faible garde une chance");
        List<TournamentRules.Entrant> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(new TournamentRules.Entrant(i, "J" + i, i == 0, 5 + i, List.of()));
        TournamentRules.Result r = TournamentRules.run(five, new Random(7));
        assertEquals(4, r.bouts().size(), "cinq jouteurs : quatre passes d'armes pour un seul vainqueur");
        assertNotNull(r.champion());
        assertTrue(five.contains(r.champion()));
        assertTrue(r.bouts().stream().allMatch(b -> b.winner() == b.a() || b.winner() == b.b()));
        assertNull(TournamentRules.run(List.of(), new Random(1)).champion());
        assertEquals(50, TournamentRules.cost(TitleRank.BARONNIE));
        assertEquals(200, TournamentRules.cost(TitleRank.ROYAUME));
        assertEquals(25, TournamentRules.hostPrestige(TitleRank.COMTE, 10));
    }

    @Test
    void foiDesRoyaumes() {
        UUID id = UUID.randomUUID();
        assertEquals(AiFaith.of(id), AiFaith.of(id), "la foi d'un royaume ne change pas d'un chargement à l'autre");
        Map<AiFaith, Integer> count = new EnumMap<>(AiFaith.class);
        Random random = new Random(42);
        for (int i = 0; i < 4000; i++) count.merge(AiFaith.of(new UUID(random.nextLong(), random.nextLong())), 1, Integer::sum);
        assertTrue(count.get(AiFaith.FIDELE) > 2600, "trois royaumes sur quatre sont fidèles");
        assertTrue(count.get(AiFaith.PAIEN) > 300 && count.get(AiFaith.HERETIQUE) > 300);
        assertFalse(AiFaith.FIDELE.infidel());
        assertTrue(AiFaith.PAIEN.infidel());
    }

    private static WarRules.CbContext holy(TitleRank rank, boolean infidel, int piety) {
        return new WarRules.CbContext(rank, TitleRank.COMTE, false, false, false, 0, 0, false, infidel, piety);
    }

    @Test
    void guerreSainte() {
        assertEquals("crusadecraft.war.cb.not_infidel", WarRules.cbProblem(CasusBelli.GUERRE_SAINTE, holy(TitleRank.DUCHE, false, 500)));
        assertEquals("crusadecraft.war.cb.need_county", WarRules.cbProblem(CasusBelli.GUERRE_SAINTE, holy(TitleRank.BARONNIE, true, 500)));
        assertEquals("crusadecraft.war.cb.no_piety", WarRules.cbProblem(CasusBelli.GUERRE_SAINTE, holy(TitleRank.COMTE, true, 99)));
        assertNull(WarRules.cbProblem(CasusBelli.GUERRE_SAINTE, holy(TitleRank.COMTE, true, 100)));
        War w = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CasusBelli.GUERRE_SAINTE, 3, 10);
        w.setCrusade(true);
        assertTrue(War.load(w.save()).crusade());
        assertFalse(War.load(new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), CasusBelli.CLAIM, 3, 10).save()).crusade());
    }

    private static DiplomacyRules.Context ctx(AiPersonality p, TitleRank ai, TitleRank player) {
        return new DiplomacyRules.Context(p, List.of(), ai, player, 0, 0, false, 0, 10, 10, 0);
    }

    @Test
    void demandesEnMariage() {
        UUID ai = UUID.randomUUID(), player = UUID.randomUUID();
        Relation rel = new Relation(ai, player);
        assertEquals("crusadecraft.house.block.faith",
                DiplomacyRules.marriage(50, ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.COMTE), rel, false, false).blockedKey());
        assertEquals("crusadecraft.house.block.already",
                DiplomacyRules.marriage(50, ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.COMTE), rel, true, true).blockedKey());
        assertTrue(DiplomacyRules.marriage(20, ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.COMTE), rel, true, false).accepted());
        assertFalse(DiplomacyRules.marriage(19, ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.COMTE), rel, true, false).accepted());
        assertTrue(DiplomacyRules.marriage(10, ctx(AiPersonality.PACIFIQUE, TitleRank.COMTE, TitleRank.COMTE), rel, true, false).accepted(),
                "un pacifique marie volontiers");
        assertTrue(DiplomacyRules.marriage(10, ctx(AiPersonality.OPPORTUNISTE, TitleRank.BARONNIE, TitleRank.DUCHE), rel, true, false).accepted(),
                "s'allier à plus titré que soi");
        rel.setMarriage(true);
        assertEquals("crusadecraft.house.block.already",
                DiplomacyRules.marriage(80, ctx(AiPersonality.OPPORTUNISTE, TitleRank.COMTE, TitleRank.COMTE), rel, true, false).blockedKey());
        assertTrue(Relation.load(rel.save()).marriage());
    }

    @Test
    void maisonSauvegardee() {
        ColonyRef colony = new ColonyRef(ResourceLocation.tryParse("minecraft:overworld"), 2);
        CitizenRef a = new CitizenRef(colony, 4), b = new CitizenRef(colony, 5);
        UUID realm = UUID.randomUUID();
        HouseState h = new HouseState();
        h.members().add(a);
        h.members().add(b);
        h.setHeir(b);
        h.marriages().add(new HouseState.Marriage(a, realm, "Isabeau", 30));
        h.promised().put(UUID.randomUUID(), b);
        HouseState back = HouseState.load(h.save());
        assertEquals(h.members(), back.members());
        assertEquals(b, back.heir());
        assertTrue(back.marriedInto(realm));
        assertEquals("Isabeau", back.marriageOf(a).orElseThrow().spouse());
        assertEquals(1, back.promised().size());
        back.remove(b);
        assertNull(back.heir(), "un héritier qui quitte la maison n'est plus héritier");
        assertTrue(back.promised().isEmpty());
    }
}
