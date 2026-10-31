package fr.crusadecraft.core.realm;

import fr.crusadecraft.core.ColonyRef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les villes du royaume (P19) : limite de domaine, part de la couronne, et termes qui survivent à la sauvegarde. */
class CityRulesTest {
    private static final ResourceLocation OW = ResourceLocation.tryParse("minecraft:overworld");
    private static final ColonyRef A = new ColonyRef(OW, 1), B = new ColonyRef(OW, 2), C = new ColonyRef(OW, 3), D = new ColonyRef(OW, 4);

    @Test
    void laLimiteDuDomaineSuitLeRang() {
        List<Integer> limits = List.of(2, 3, 4, 6, 8);
        assertEquals(2, CityRules.domainLimit(TitleRank.BARONNIE, limits));
        assertEquals(4, CityRules.domainLimit(TitleRank.DUCHE, limits));
        assertEquals(1, CityRules.domainLimit(TitleRank.BARONNIE, List.of(0)), "jamais moins d'une ville");
        assertEquals(8, CityRules.domainLimit(TitleRank.EMPIRE, List.of(2, 8)), "une liste courte reprend sa dernière valeur");
    }

    @Test
    void auDelaDuDomaineLesVillesRendentMoins() {
        Map<ColonyRef, CityTerms> cities = new LinkedHashMap<>();
        List<ColonyRef> all = List.of(A, B, C, D);
        Set<ColonyRef> over = CityRules.overLimit(all, cities, 2);
        assertEquals(Set.of(C, D), over, "la capitale et la première ville d'abord");
        assertEquals(0.7, CityRules.taxFactor(null, true, 30), 1e-9);
        assertEquals(1.0, CityRules.taxFactor(null, false, 30), 1e-9);
        // Confier une ville la sort du domaine : la suivante n'est plus de trop.
        cities.put(B, new CityTerms(UUID.randomUUID(), 40, 25));
        assertEquals(Set.of(D), CityRules.overLimit(all, cities, 2));
        assertEquals(3, CityRules.directCount(all, cities));
        assertEquals(0.4, CityRules.taxFactor(cities.get(B), false, 30), 1e-9, "un gouverneur verse la part convenue");
        assertEquals(0.25, CityRules.levyFactor(cities.get(B)), 1e-9);
        assertEquals(1.0, CityRules.levyFactor(null), 1e-9);
    }

    @Test
    void lesPartsSontBornees() {
        CityTerms t = new CityTerms(UUID.randomUUID(), 150, -20);
        assertEquals(100, t.taxShare());
        assertEquals(0, t.levyShare());
    }

    @Test
    void lesTermesSurviventALaSauvegarde() {
        UUID governor = UUID.randomUUID();
        Realm realm = new Realm(UUID.randomUUID(), "Valbrume", "", "", 0x335588, UUID.randomUUID(), "Souverain", false, A, TitleRank.COMTE, 0);
        realm.addProvince(B);
        realm.addProvince(C);
        realm.setCityTerms(B, new CityTerms(governor, 60, 30));
        realm.setCityTerms(A, new CityTerms(governor, 10, 10));
        realm.setCityTerms(D, new CityTerms(governor, 10, 10));
        assertFalse(realm.cityTerms(A).isPresent(), "la capitale ne se confie pas");
        assertFalse(realm.cityTerms(D).isPresent(), "une colonie hors du royaume non plus");
        CompoundTag saved = realm.save();
        Realm back = Realm.load(saved);
        CityTerms t = back.cityTerms(B).orElseThrow();
        assertEquals(governor, t.governor());
        assertEquals(60, t.taxShare());
        assertEquals(30, t.levyShare());
        back.removeProvince(B);
        assertTrue(back.cities().isEmpty(), "une ville perdue perd son gouverneur");
        back.setCityTerms(C, new CityTerms(governor, 50, 50));
        back.setCityTerms(C, null);
        assertNull(back.cities().get(C), "reprendre une ville l'enlève des termes");
    }
}
