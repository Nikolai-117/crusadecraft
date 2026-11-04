package fr.crusadecraft.core.nation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NationDefTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void readsAFullFile() {
        NationDef d = NationDef.parse(json("""
                {"cle": "Levanzia", "nom": "Levànzia", "dossier": "Levenzia", "rang": "royaume", "couleur": "#6D3A7A", "temperament": "marchand",
                 "joueur": true, "maison": "Selenor",
                 "liens": [{"avec": "Varenhold", "traites": ["commerce"], "amitie": 0.5}],
                 "culture": {"nom": "Romian", "foi": "Romianisme", "hommes": ["Elio"], "femmes": ["Bianca"], "familles": ["Corsi"]},
                 "histoire": ["Un.", "  ", "Deux."],
                 "villes": {"Dolhimluith": {"histoire": ["Vieille."], "notables": [
                   {"fonction": "boulangerie", "prenom": "Ilaria", "nom": "Bressan", "femme": true, "age": 41, "traits": ["diligent"], "vie": "Levée tôt.", "repliques": ["Le pain n'attend pas."]}]}}}
                """));
        assertEquals("levanzia", d.key());
        assertEquals("Levànzia", d.name());
        assertEquals(TitleRank.ROYAUME, d.rank());
        assertEquals(0x6D3A7A, d.color());
        assertEquals(AiPersonality.MARCHAND, d.personality());
        assertTrue(d.player());
        assertEquals("varenhold", d.ties().get(0).with());
        assertEquals(Treaty.COMMERCE, d.ties().get(0).treaties().get(0));
        assertEquals(2, d.history().size(), "un paragraphe vide est écarté");
        NationDef.CityLore city = d.city(" dolhimluith ");
        assertNotNull(city, "le nom d'une ville se cherche sans tenir compte des majuscules");
        NationDef.Notable n = city.notables().get(0);
        assertEquals(BuildingFunction.BOULANGERIE, n.function());
        assertEquals("Ilaria", n.first());
        assertTrue(n.female());
        assertEquals(Trait.DILIGENT, n.traits().get(0));
        assertNull(d.city("Ailleurs"));
    }

    @Test
    void aMinimalFileIsEnough() {
        NationDef d = NationDef.parse(json("{\"cle\": \"a\", \"nom\": \"A\"}"));
        assertFalse(d.player());
        assertNull(d.ruler());
        assertEquals("", d.folder());
        assertTrue(d.ties().isEmpty());
        assertEquals(NationDef.Culture.NONE, d.culture());
    }

    @Test
    void aMistakeRejectsTheFileWithAMessage() {
        assertThrows(RuntimeException.class, () -> NationDef.parse(json("{\"nom\": \"A\"}")));
        assertThrows(RuntimeException.class, () -> NationDef.parse(json("{\"cle\": \"a\", \"nom\": \"A\", \"temperament\": \"inconnu\"}")));
        assertThrows(RuntimeException.class, () -> NationDef.parse(json("{\"cle\": \"a\", \"nom\": \"A\", \"liens\": [{\"avec\": \"b\", \"traites\": [\"inconnu\"]}]}")));
    }

    @Test
    void acceptsATreatyOnlyWithEnoughOpinion() {
        assertFalse(NationRules.accepts(Treaty.ALLIANCE, 59));
        assertTrue(NationRules.accepts(Treaty.ALLIANCE, 60));
        assertTrue(NationRules.accepts(Treaty.NON_AGRESSION, 0));
        assertFalse(NationRules.accepts(Treaty.COMMERCE, 4));
    }

    @Test
    void fillsTheHolesItKnows() {
        assertEquals("De Varenhold à {a}.", NationRules.fill("De {de} à {a}.", Map.of("de", "Varenhold")));
    }
}
