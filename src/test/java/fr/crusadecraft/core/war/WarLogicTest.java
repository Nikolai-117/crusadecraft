package fr.crusadecraft.core.war;

import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarLogicTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private static WarRules.CbContext ctx(TitleRank attacker, TitleRank defender, boolean claim, boolean dejure, boolean hostile,
                                          int opinion, int prestige, boolean vassal) {
        return new WarRules.CbContext(attacker, defender, claim, dejure, hostile, opinion, prestige, vassal, false, 0);
    }

    @Test
    void motifsDeGuerre() {
        WarRules.CbContext baron = ctx(TitleRank.BARONNIE, TitleRank.COMTE, false, false, false, 0, 0, false);
        assertEquals("crusadecraft.war.cb.no_claim", WarRules.cbProblem(CasusBelli.CLAIM, baron));
        assertNull(WarRules.cbProblem(CasusBelli.CLAIM, ctx(TitleRank.BARONNIE, TitleRank.COMTE, true, false, false, 0, 0, false)));
        assertEquals("crusadecraft.war.cb.need_kingdom", WarRules.cbProblem(CasusBelli.DEJURE, baron));
        assertEquals("crusadecraft.war.cb.not_dejure", WarRules.cbProblem(CasusBelli.DEJURE, ctx(TitleRank.ROYAUME, TitleRank.COMTE, false, false, false, 0, 0, false)));
        assertNull(WarRules.cbProblem(CasusBelli.DEJURE, ctx(TitleRank.ROYAUME, TitleRank.COMTE, false, true, false, 0, 0, false)));
        assertEquals("crusadecraft.war.cb.need_duchy", WarRules.cbProblem(CasusBelli.CONQUEST, baron));
        assertEquals("crusadecraft.war.cb.no_prestige", WarRules.cbProblem(CasusBelli.CONQUEST, ctx(TitleRank.DUCHE, TitleRank.COMTE, false, false, false, 0, 99, false)));
        assertNull(WarRules.cbProblem(CasusBelli.CONQUEST, ctx(TitleRank.DUCHE, TitleRank.COMTE, false, false, false, 0, 100, false)));
        assertEquals("crusadecraft.war.cb.rank", WarRules.cbProblem(CasusBelli.VASSALIZE, baron), "on ne soumet pas plus titré que soi");
        assertNull(WarRules.cbProblem(CasusBelli.VASSALIZE, ctx(TitleRank.DUCHE, TitleRank.COMTE, false, false, false, 0, 0, false)));
        assertEquals("crusadecraft.war.cb.already_vassal", WarRules.cbProblem(CasusBelli.VASSALIZE, ctx(TitleRank.DUCHE, TitleRank.COMTE, false, false, false, 0, 0, true)));
        assertEquals("crusadecraft.war.cb.no_rivalry", WarRules.cbProblem(CasusBelli.HUMILIATE, baron));
        assertNull(WarRules.cbProblem(CasusBelli.HUMILIATE, ctx(TitleRank.BARONNIE, TitleRank.COMTE, false, false, true, 0, 0, false)));
        assertNull(WarRules.cbProblem(CasusBelli.HUMILIATE, ctx(TitleRank.BARONNIE, TitleRank.COMTE, false, false, false, -20, 0, false)));
    }

    @Test
    void cequiInterditUneDeclaration() {
        assertNull(WarRules.declarationProblem(new WarRules.Bonds(false, false, false, false, false, false)));
        assertEquals("crusadecraft.war.block.at_war", WarRules.declarationProblem(new WarRules.Bonds(true, true, true, true, true, true)));
        assertEquals("crusadecraft.war.block.liege", WarRules.declarationProblem(new WarRules.Bonds(false, true, false, false, true, false)));
        assertEquals("crusadecraft.war.block.truce", WarRules.declarationProblem(new WarRules.Bonds(false, true, true, true, false, false)));
        assertEquals("crusadecraft.war.block.alliance", WarRules.declarationProblem(new WarRules.Bonds(false, false, true, true, false, false)));
        assertEquals("crusadecraft.war.block.non_aggression", WarRules.declarationProblem(new WarRules.Bonds(false, false, true, false, false, false)));
    }

    @Test
    void scoreEtOccupation() {
        assertEquals(0, WarRules.occupationScore(0, 4, false));
        assertEquals(0, WarRules.occupationScore(2, 0, false));
        // Deux provinces sur quatre : (2) / (4 + 1) de 60 = 24 ; avec la capitale, (3) / 5 = 36.
        assertEquals(24, WarRules.occupationScore(2, 4, false));
        assertEquals(36, WarRules.occupationScore(2, 4, true));
        assertEquals(60, WarRules.occupationScore(4, 4, true), "tout occuper, capitale comprise, vaut le maximum");
        assertEquals(100, WarRules.score(60, 60, 20), "le score est borné à 100");
        assertEquals(-100, WarRules.score(-60, -60, 0));
        assertEquals(35, WarRules.score(10, 20, 5));
    }

    @Test
    void escarmouches() {
        WarRules.Skirmish even = WarRules.skirmish(100, 100, 0, 0, 0.5);
        assertEquals(0, even.delta(), "à forces égales et hasard neutre, personne ne l'emporte");
        WarRules.Skirmish strong = WarRules.skirmish(300, 100, 0, 0, 0.5);
        assertTrue(strong.delta() > 0);
        assertTrue(strong.lossB() > strong.lossA(), "le vaincu perd davantage");
        WarRules.Skirmish general = WarRules.skirmish(100, 100, 20, 0, 0.5);
        assertTrue(general.delta() > 0, "un bon maréchal fait pencher la balance");
        for (double roll = 0; roll <= 1; roll += 0.25) {
            WarRules.Skirmish s = WarRules.skirmish(1, 100000, 0, 0, roll);
            assertTrue(s.delta() >= -8 && s.delta() <= 8, "une journée rapporte au plus 8 points");
        }
    }

    @Test
    void reponsesDePaix() {
        assertTrue(WarRules.aiAnswer(WarRules.Peace.SURRENDER, 0, 0, 0).accepted(), "une IA accepte toujours votre capitulation");
        assertFalse(WarRules.aiAnswer(WarRules.Peace.WHITE, 0, 0, 1).accepted(), "pas de paix blanche au premier jour");
        assertTrue(WarRules.aiAnswer(WarRules.Peace.WHITE, -10, 20, 10).accepted(), "une IA qui perd un peu, lassée, accepte la paix blanche");
        assertFalse(WarRules.aiAnswer(WarRules.Peace.VICTORY, -30, 20, 10).accepted());
        assertTrue(WarRules.aiAnswer(WarRules.Peace.VICTORY, -60, 20, 10).accepted(), "écrasée, elle s'avoue vaincue");
        WarRules.Verdict v = WarRules.aiAnswer(WarRules.Peace.VICTORY, -60, 20, 10);
        int total = v.factors().stream().mapToInt(WarRules.Factor::value).sum();
        assertEquals(10, total, "les facteurs affichés font le compte");
    }

    @Test
    void guerreCampsEtJournal() {
        UUID ally = UUID.randomUUID(), other = UUID.randomUUID();
        War w = new War(UUID.randomUUID(), a, b, CasusBelli.CLAIM, 7, 3);
        w.attackerAllies().add(ally);
        assertEquals(1, w.side(a));
        assertEquals(1, w.side(ally));
        assertEquals(-1, w.side(b));
        assertEquals(0, w.side(other));
        assertTrue(w.opposes(ally, b));
        assertFalse(w.opposes(a, ally));
        assertFalse(w.opposes(a, other));
        for (int i = 0; i < 10; i++) w.addBattle(8);
        assertEquals(WarRules.BATTLE_CAP, w.battleScore(), "les batailles sont plafonnées");
        for (int i = 0; i < 30; i++) w.tickGoal();
        assertEquals(WarRules.GOAL_TICK_CAP, w.goalTicks());
        w.tire(150, -5);
        assertEquals(100, w.attackerExhaustion());
        assertEquals(0, w.defenderExhaustion());
        for (int i = 0; i < 20; i++) w.log(i, "crusadecraft.war.log.skirmish", 1, "x" + i);
        assertEquals(War.EVENTS_KEPT, w.events().size());
        assertEquals("x19", w.events().get(0).args().get(0), "le plus récent en tête");
    }

    @Test
    void guerreSauvegardee() {
        War w = new War(UUID.randomUUID(), a, b, CasusBelli.CONQUEST, 12, 40);
        w.defenderAllies().add(UUID.randomUUID());
        w.occupied().add(12);
        w.occupiedColonies().add("minecraft:overworld#3");
        w.addBattle(-9);
        w.tickGoal();
        w.tire(10, 20);
        w.log(41, "crusadecraft.war.log.captured", 5, "Ambrevent");
        w.setLastRaidTick(1234);
        w.setLastAiOfferDay(42);
        w.savedRanks().put("colonie|joueur", "Neutral");
        War back = War.load(w.save());
        assertEquals(w.id(), back.id());
        assertEquals(CasusBelli.CONQUEST, back.cb());
        assertEquals(12, back.targetProvince());
        assertEquals(40, back.startDay());
        assertEquals(w.defenderAllies(), back.defenderAllies());
        assertEquals(w.occupied(), back.occupied());
        assertEquals(w.occupiedColonies(), back.occupiedColonies());
        assertEquals(-9, back.battleScore());
        assertEquals(1, back.goalTicks());
        assertEquals(10, back.attackerExhaustion());
        assertEquals(20, back.defenderExhaustion());
        assertEquals(List.of("Ambrevent"), back.events().get(0).args());
        assertEquals(1234, back.lastRaidTick());
        assertEquals(42, back.lastAiOfferDay());
        assertEquals("Neutral", back.savedRanks().get("colonie|joueur"));
        War fresh = War.load(new War(UUID.randomUUID(), a, b, CasusBelli.CLAIM, -1, 0).save());
        assertEquals(Long.MIN_VALUE, fresh.lastRaidTick(), "pas encore de raid");
    }

    /** Une entrée du registre écrite à la main (pas besoin des blocs du jeu pour la file). */
    private static CompoundTag entry(UUID war, int x) {
        CompoundTag t = new CompoundTag();
        t.putUUID("war", war);
        t.putString("dim", "minecraft:overworld");
        t.put("pos", NbtUtils.writeBlockPos(new BlockPos(x, 64, 0)));
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        t.put("state", state);
        return t;
    }

    @Test
    void registreDesDegats() {
        UUID w1 = UUID.randomUUID(), w2 = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        ListTag entries = new ListTag();
        for (int i = 0; i < 5; i++) entries.add(entry(w1, i));
        for (int i = 10; i < 13; i++) entries.add(entry(w2, i));
        root.put("entries", entries);
        root.put("restoring", new ListTag());
        DamageLedger d = DamageLedger.load(root);
        ResourceLocation ow = ResourceLocation.tryParse("minecraft:overworld");
        assertEquals(8, d.size());
        assertEquals(5, d.count(w1));
        assertTrue(d.has(ow, new BlockPos(2, 64, 0)));
        assertFalse(d.has(ResourceLocation.tryParse("minecraft:the_nether"), new BlockPos(2, 64, 0)));
        assertEquals(new BlockPos(2, 64, 0), d.remove(ow, new BlockPos(2, 64, 0)).pos());
        d.release(w1);
        assertEquals(3, d.size(), "seuls les blocs de la guerre finie partent en restauration");
        assertEquals(4, d.pending());
        DamageLedger reloaded = DamageLedger.load(d.save());
        assertEquals(4, reloaded.pending(), "une restauration interrompue reprend au démarrage");
        assertEquals(3, reloaded.take(3).size());
        assertEquals(1, reloaded.pending());
        DamageLedger.Entry last = reloaded.take(10).get(0);
        assertEquals(0, reloaded.pending());
        reloaded.retry(last);
        assertEquals(1, reloaded.pending());
    }
}
