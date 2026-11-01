package fr.crusadecraft.compat.dynmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La feuille de style de la carte fantasy (P32) : posée, complétée, mise à jour, jamais à la place de celle du joueur. */
class DynmapStyleTest {
    @Test
    void notreBlocSAjouteSansToucherAuReste() {
        assertEquals(DynmapStyle.CSS, DynmapStyle.merge(""));
        String mine = ".dynmap { color: red; }\n";
        String merged = DynmapStyle.merge(mine);
        assertTrue(merged.startsWith(mine), "le style du joueur reste en tête");
        assertTrue(merged.contains(DynmapStyle.BEGIN));
        String old = mine + DynmapStyle.BEGIN + "\nvieux\n" + DynmapStyle.END + "\n.apres { }\n";
        String updated = DynmapStyle.merge(old);
        assertTrue(updated.contains(".apres { }") && !updated.contains("vieux"), "notre ancien bloc est remplacé, le reste gardé");
        assertEquals(updated, DynmapStyle.merge(updated), "remis deux fois, rien ne change");
    }
}
