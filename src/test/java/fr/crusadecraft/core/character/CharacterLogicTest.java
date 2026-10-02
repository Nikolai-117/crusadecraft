package fr.crusadecraft.core.character;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.TitleRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterLogicTest {

    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");

    /** Compétences dans l'ordre de CitizenSkill : Athletics, Dexterity, Strength, Agility, Stamina, Mana,
     *  Adaptability, Focus, Creativity, Knowledge, Intelligence. */
    private static int[] skills(int ath, int dex, int str, int agi, int sta, int mana, int ada, int foc, int cre, int kno, int intel) {
        return new int[]{ath, dex, str, agi, sta, mana, ada, foc, cre, kno, intel};
    }

    // ------------------------------------------------------------------ aptitudes

    @Test
    void formuleParDefautPairesComplementaires() {
        StatFormula f = StatFormula.defaults();
        int[] s = skills(20, 12, 30, 8, 10, 4, 16, 6, 24, 18, 22);
        int[] stats = f.compute(s);
        assertEquals(Math.round((16 + 24) / 4.0), stats[Stat.DIPLOMATIE.ordinal()]);
        assertEquals(Math.round((30 + 20 + 5) / 5.0), stats[Stat.MARTIAL.ordinal()]);
        assertEquals(Math.round((22 + 18) / 4.0), stats[Stat.INTENDANCE.ordinal()]);
        assertEquals(Math.round((12 + 8) / 4.0), stats[Stat.INTRIGUE.ordinal()]);
        assertEquals(Math.round((4 + 6 + 9) / 5.0), stats[Stat.ERUDITION.ordinal()]);
    }

    @Test
    void lesOnzeCompetencesComptent() {
        StatFormula f = StatFormula.defaults();
        for (CitizenSkill skill : CitizenSkill.values()) {
            double total = 0;
            for (Stat stat : Stat.values()) total += f.weight(stat, skill);
            assertTrue(total > 0, skill + " n'entre dans aucune aptitude");
        }
    }

    @Test
    void regleIllisibleRetombeSurLeDefaut() {
        StatFormula f = StatFormula.parse(List.of("Charisme:1 / 4", "Strength:abc / 5", "Intelligence:1 / 0", "", "Mana:2 / 2"));
        StatFormula d = StatFormula.defaults();
        for (Stat stat : List.of(Stat.DIPLOMATIE, Stat.MARTIAL, Stat.INTENDANCE, Stat.INTRIGUE)) {
            for (CitizenSkill k : CitizenSkill.values()) assertEquals(d.weight(stat, k), f.weight(stat, k), stat + "/" + k);
        }
        assertEquals(2.0, f.weight(Stat.ERUDITION, CitizenSkill.Mana));
        assertEquals(2.0, f.divisor(Stat.ERUDITION));
    }

    @Test
    void citoyenDebutantEtExpert() {
        StatFormula f = StatFormula.defaults();
        int[] debutant = f.compute(skills(3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3));
        int[] expert = f.compute(skills(60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60));
        for (int v : debutant) assertTrue(v >= 1 && v <= 5, "débutant : " + v);
        for (int v : expert) assertTrue(v >= 25 && v <= 30, "expert : " + v);
    }

    @Test
    void education() {
        assertEquals(new Education(Stat.MARTIAL, 1), Education.of(new int[]{2, 5, 1, 0, 3}));
        assertEquals(new Education(Stat.INTRIGUE, 3), Education.of(new int[]{2, 5, 1, 12, 3}));
        assertEquals(new Education(Stat.ERUDITION, 4), Education.of(new int[]{2, 5, 1, 12, 25}));
    }

    // ------------------------------------------------------------------ traits

    @Test
    void tirageDeterministeEtSansContraires() {
        int[] s = skills(10, 10, 40, 10, 10, 10, 10, 10, 10, 10, 10);
        long seed = TraitGenerator.seed(123456789L, "minecraft:overworld", 4, 17);
        List<Trait> a = TraitGenerator.roll(seed, s, 3);
        List<Trait> b = TraitGenerator.roll(seed, s, 3);
        assertEquals(a, b);
        assertEquals(3, a.size());
        for (Trait t : a) {
            assertEquals(Trait.Kind.PERSONALITY, t.kind());
            for (Trait u : a) if (t != u) assertFalse(t.conflictsWith(u), t + " / " + u);
        }
    }

    @Test
    void lesGrainesVarientSelonLeCitoyenEtLeMonde() {
        int[] s = skills(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
        Set<List<Trait>> distinct = new HashSet<>();
        for (int c = 1; c <= 30; c++) {
            distinct.add(TraitGenerator.roll(TraitGenerator.seed(42L, "minecraft:overworld", 1, c), s, 3));
        }
        assertTrue(distinct.size() > 15, "trop peu de variété : " + distinct.size());
        assertNotEquals(TraitGenerator.seed(1L, "minecraft:overworld", 1, 1), TraitGenerator.seed(2L, "minecraft:overworld", 1, 1));
    }

    @Test
    void lesPairesSontSymetriques() {
        for (Trait t : Trait.values()) {
            if (t.kind() == Trait.Kind.PERSONALITY) {
                assertEquals(t, t.opposite().opposite(), t.name());
            } else {
                assertNull(t.opposite());
            }
        }
    }

    // ------------------------------------------------------------------ opinion

    @Test
    void motifDegressif() {
        OpinionModifier m = OpinionModifier.of(OpinionReason.RECOMPENSE, 100, 1);
        assertEquals(20, m.valueAt(100));
        assertEquals(10, m.valueAt(105));
        assertEquals(0, m.valueAt(110));
        assertTrue(m.expired(110));
    }

    @Test
    void multiplicateurDeTrait() {
        assertEquals(30, OpinionModifier.of(OpinionReason.RECOMPENSE, 0, 1.5).value());
    }

    @Test
    void opinionBorneeEtDetaillee() {
        Opinion.Context ctx = new Opinion.Context(List.of(Trait.LOYAL, Trait.CONTENT), 10.0, true, CharacterStatus.LIBRE);
        List<OpinionModifier> stored = new java.util.ArrayList<>(List.of(
                OpinionModifier.of(OpinionReason.RECOMPENSE, 0, 1), OpinionModifier.of(OpinionReason.NOMME, 0, 1)));
        List<Opinion.Line> lines = Opinion.breakdown(stored, ctx, 0);
        // loyal 20 + content 10 + bonheur (10-5)*3=15 + poste 10 + récompense 20 + nommé 15 = 90
        assertEquals(90, Opinion.total(lines));
        Opinion.Context hated = new Opinion.Context(List.of(Trait.AMBITIEUX, Trait.PARANOIAQUE), 0.0, false, CharacterStatus.EMPRISONNE);
        List<OpinionModifier> bad = List.of(OpinionModifier.of(OpinionReason.EMPRISONNE, 0, 1), OpinionModifier.of(OpinionReason.EXILE, 0, 1));
        assertEquals(Opinion.MIN, Opinion.total(Opinion.breakdown(bad, hated, 0)));
    }

    @Test
    void lesMotifsExpiresDisparaissent() {
        List<OpinionModifier> stored = new java.util.ArrayList<>(List.of(OpinionModifier.of(OpinionReason.REPRIMANDE, 0, 1)));
        assertFalse(Opinion.prune(stored, 5));
        assertTrue(Opinion.prune(stored, 8));
        assertTrue(stored.isEmpty());
    }

    // ------------------------------------------------------------------ postes et fiches

    @Test
    void postesDuRoyaume() {
        ColonyRef capitale = new ColonyRef(OW, 1);
        Realm realm = new Realm(UUID.randomUUID(), "Valbrume", "", "Aubépine", 0, UUID.randomUUID(), "Romain",
                false, capitale, TitleRank.BARONNIE, 0);
        CitizenRef aldric = new CitizenRef(capitale, 7);
        CitizenRef berthe = new CitizenRef(capitale, 8);
        assertNull(realm.appoint(Post.MARECHAL, aldric));
        assertEquals(Post.MARECHAL, realm.postOf(aldric));
        // Un seul poste à la fois : devenir chancelier libère le siège de maréchal.
        realm.appoint(Post.CHANCELIER, aldric);
        assertNull(realm.council().get(Post.MARECHAL));
        assertEquals(aldric, realm.appoint(Post.CHANCELIER, berthe));
        assertNull(realm.postOf(aldric));
        realm.appoint(Post.MAIRE, aldric);
        assertEquals(aldric, realm.mayors().get(capitale));

        Realm reloaded = Realm.load(realm.save());
        assertEquals(berthe, reloaded.council().get(Post.CHANCELIER));
        assertEquals(Post.MAIRE, reloaded.postOf(aldric));
        assertEquals(Post.MAIRE, reloaded.vacate(aldric));
        assertNull(reloaded.postOf(aldric));
    }

    @Test
    void ficheAllerRetour() {
        CitizenRef ref = new CitizenRef(new ColonyRef(OW, 2), 5);
        CharacterRecord rec = new CharacterRecord(ref, "Aldric", List.of(Trait.BRAVE, Trait.AVARE));
        assertFalse(rec.addTrait(Trait.LACHE));
        assertTrue(rec.addTrait(Trait.VETERAN));
        rec.addModifier(OpinionModifier.of(OpinionReason.RECOMPENSE, 3, 1));
        rec.addModifier(OpinionModifier.of(OpinionReason.RECOMPENSE, 4, 1));
        assertEquals(1, rec.modifiers().size(), "un même motif ne s'empile pas");
        rec.setStatus(CharacterStatus.EXILE, 40);
        rec.addStress(150);
        rec.remember(4, "crusadecraft.history.appointed", "crusadecraft.post.marechal.m");
        CompoundTag tag = rec.save();
        CharacterRecord back = CharacterRecord.load(tag);
        assertEquals(ref, back.ref());
        assertEquals(List.of(Trait.BRAVE, Trait.AVARE, Trait.VETERAN), back.traits());
        assertEquals(1, back.modifiers().size());
        assertEquals(CharacterStatus.EXILE, back.status());
        assertEquals(40, back.statusUntilDay());
        assertEquals(1, back.stressLevel());
        assertEquals("crusadecraft.history.appointed", back.history().get(0).key());
        assertArrayEquals(new String[]{"crusadecraft.post.marechal.m"}, back.history().get(0).args().toArray());
    }
}
