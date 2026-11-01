package fr.crusadecraft.core.lineage;

import com.google.gson.JsonParser;
import fr.crusadecraft.core.character.Trait;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les répliques du datapack (P26) : lecture d'un fichier, et la réplique qui convient à chacun. */
class RepliquesTest {
    private static Repliques.Entry parse(String json) {
        return Repliques.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void unFichierSeLit() {
        Repliques.Entry e = parse("{\"moment\": \"fete\", \"roles\": [\"souverain\"], \"traits\": [\"AVARE\"],"
                + " \"lines\": [\"Que de dépenses\", {\"key\": \"crusadecraft.replique.fete.1\"}, \"  \"]}");
        assertEquals(Repliques.Moment.FETE, e.moment());
        assertEquals(2, e.lines().size(), "la ligne vide est ignorée");
        assertThrows(RuntimeException.class, () -> parse("{\"moment\": \"fete\", \"lines\": []}"), "un lot sans réplique est refusé");
        assertThrows(RuntimeException.class, () -> parse("{\"moment\": \"sieste\", \"lines\": [\"a\"]}"), "un moment inconnu est refusé");
    }

    @Test
    void chacunSaReplique() {
        Repliques.Entry all = parse("{\"moment\": \"guerre\", \"lines\": [\"Pour le roi !\"]}");
        Repliques.Entry coward = parse("{\"moment\": \"guerre\", \"traits\": [\"LACHE\"], \"lines\": [\"Je reste ici.\"]}");
        List<Repliques.Entry> entries = List.of(all, coward);
        Random random = new Random(1);
        for (int i = 0; i < 20; i++) {
            Component c = Repliques.pick(entries, Repliques.Moment.GUERRE, Role.SOLDAT, List.of(Trait.BRAVE), random);
            assertEquals("Pour le roi !", c.getString(), "un brave ne dit pas la réplique du lâche");
        }
        boolean heardCoward = false;
        for (int i = 0; i < 40; i++) {
            heardCoward |= Repliques.pick(entries, Repliques.Moment.GUERRE, Role.SOLDAT, List.of(Trait.LACHE), random).getString().equals("Je reste ici.");
        }
        assertTrue(heardCoward, "le lâche ne dit jamais sa réplique");
        assertNull(Repliques.pick(entries, Repliques.Moment.FETE, Role.SOLDAT, List.of(), random), "rien pour un moment sans réplique");
    }
}
