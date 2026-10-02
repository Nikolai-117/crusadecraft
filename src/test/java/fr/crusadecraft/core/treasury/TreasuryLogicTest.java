package fr.crusadecraft.core.treasury;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryLogicTest {

    private static TaxTable taxes() {
        return TaxTable.parse(EconomyDefaults.TAX_RATES, new TaxTable.Rate(0.5, 0.5));
    }

    private static TaxTable upkeep() {
        return TaxTable.parse(EconomyDefaults.UPKEEP_RATES, new TaxTable.Rate(0, 0));
    }

    @Test
    void baremeLuAvecOuSansEspaceDeNoms() {
        TaxTable t = taxes();
        assertEquals(3 + 1 * 2, t.rate("minecolonies:townhall").at(2), 1e-9);
        assertEquals(3 + 1 * 2, t.rate("townhall").at(2), 1e-9);
        assertEquals(0.5 + 0.5 * 3, t.rate("minecolonies:baker").at(3), 1e-9, "barème par défaut");
        assertEquals(0, t.rate("guardtower").at(5), 1e-9, "les bâtiments militaires ne paient pas");
        assertTrue(upkeep().lists("minecolonies:guardtower"));
        assertFalse(upkeep().lists("minecolonies:baker"));
    }

    @Test
    void lignesIllisiblesIgnorees() {
        TaxTable t = TaxTable.parse(List.of("=3:1", "foo", "bar=x:1", "baz=-1:0", "ok=2"), new TaxTable.Rate(1, 0));
        assertEquals(2, t.rate("ok").at(4), 1e-9, "sans supplément par niveau");
        assertEquals(1, t.rate("bar").at(4), 1e-9, "ligne fausse : barème par défaut");
        assertEquals(1, t.rate("baz").at(4), 1e-9, "montant négatif refusé");
    }

    @Test
    void bonheurDeMoitieAUneFoisEtDemie() {
        assertEquals(0.5, TaxRules.happinessMultiplier(0), 1e-9);
        assertEquals(1.0, TaxRules.happinessMultiplier(5), 1e-9);
        assertEquals(1.5, TaxRules.happinessMultiplier(10), 1e-9);
        assertEquals(1.5, TaxRules.happinessMultiplier(42), 1e-9, "borné");
    }

    @Test
    void impotDUneColonie() {
        List<TaxRules.Building> buildings = List.of(
                new TaxRules.Building("minecolonies:townhall", 2),      // 3 + 2 = 5
                new TaxRules.Building("minecolonies:residence", 2),     // 0.5 + 0.5 = 1
                new TaxRules.Building("minecolonies:baker", 1),         // 0.5 + 0.5 = 1
                new TaxRules.Building("minecolonies:guardtower", 2),    // impôt 0, entretien 1 + 1 = 2
                new TaxRules.Building("minecolonies:barracks", 0));     // pas construit : ignoré
        TaxRules.ColonyTax neutral = TaxRules.compute(buildings, taxes(), upkeep(), TaxRules.Modifiers.neutral());
        assertEquals(7, neutral.gross(), 1e-9);
        assertEquals(7, neutral.collected());
        assertEquals(2, neutral.upkeep());

        TaxRules.Modifiers heavyHappy = new TaxRules.Modifiers(10, 1.4, 0.1, 0.1, false, 1.0);
        assertEquals(Math.round(7 * 1.5 * 1.4 * 1.2), TaxRules.compute(buildings, taxes(), upkeep(), heavyHappy).collected());

        TaxRules.Modifiers raidedProvince = new TaxRules.Modifiers(5, 1.0, 0, 0, true, 0.6);
        assertEquals(Math.round(7 * 0.5 * 0.6), TaxRules.compute(buildings, taxes(), upkeep(), raidedProvince).collected());
    }

    @Test
    void bonusDeLIntendant() {
        assertEquals(0.12, TaxRules.stewardBonus(12, false), 1e-9);
        assertEquals(0.34, TaxRules.stewardBonus(12, true), 1e-9);
        assertEquals(0.10, TaxRules.stewardBonus(0, true), 1e-9);
        assertEquals(0, TaxRules.stewardBonus(0, false), 1e-9);
        assertEquals(8, TaxRules.domainIncome(20, 10, 1.0));
        assertEquals(10, TaxRules.domainIncome(20, 10, 1.25));
    }

    @Test
    void depenseDueBorneeParLaDette() {
        assertEquals(30, TaxRules.chargeable(50, 30, 100), "payée entièrement");
        assertEquals(150, TaxRules.chargeable(50, 300, 100), "jusqu'à −100 seulement");
        assertEquals(0, TaxRules.chargeable(-100, 10, 100), "déjà au plafond");
        assertEquals(10, TaxRules.chargeable(-90, 40, 100));
        assertEquals(20, TaxRules.chargeable(20, 40, 0), "sans dette permise : jusqu'à zéro");
    }

    @Test
    void valeursDeLOr() {
        GoldValues v = GoldValues.parse(List.of("minecraft:gold_nugget=1", "minecraft:gold_block=81", "minecraft:gold_ingot=9",
                "minecraft:gold_ingot=12", "faux", "minecraft:emerald=0"));
        assertEquals(3, v.denominations().size(), "doublon et valeur nulle ignorés");
        assertEquals(81, v.denominations().get(0).value(), "du plus précieux au moins précieux");
        assertEquals(9, v.valueOf(ResourceLocation.tryParse("minecraft:gold_ingot")), "la première ligne l'emporte");
        assertEquals(0, v.valueOf(ResourceLocation.tryParse("minecraft:emerald")));
    }

    @Test
    void livreDeComptes() {
        Ledger l = new Ledger();
        l.record(5, LedgerCategory.IMPOTS, 40);
        l.record(5, LedgerCategory.SALAIRES, -6);
        l.record(5, LedgerCategory.IMPOTS, 2);
        l.record(3, LedgerCategory.DEPOTS, 81);
        l.record(5, LedgerCategory.AUTRES, 0);
        assertEquals(42, l.peek(5).amount(LedgerCategory.IMPOTS));
        assertEquals(42, l.peek(5).income());
        assertEquals(-6, l.peek(5).expenses());
        assertEquals(36, l.peek(5).net());
        assertEquals(3, l.days().get(0).day(), "du plus ancien au plus récent");
        assertEquals(0, l.peek(4).net(), "jour vide");

        for (int d = 6; d < 30; d++) l.record(d, LedgerCategory.IMPOTS, d);
        assertEquals(Ledger.DAYS_KEPT, l.days().size(), "on ne garde que les derniers jours");
        assertEquals(29, l.days().get(l.days().size() - 1).day());

        CompoundTag tag = new CompoundTag();
        tag.put("l", l.save());
        Ledger back = Ledger.load(tag, "l");
        assertEquals(l.days().size(), back.days().size());
        assertEquals(29, back.peek(29).amount(LedgerCategory.IMPOTS));
    }
}
