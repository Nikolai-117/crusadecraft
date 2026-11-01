package fr.crusadecraft.core.lineage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** L'arbre illustré (P26) : une rangée par génération, les conjoints côte à côte, les enfants sous leurs parents. */
class TreeLayoutTest {
    private static final UUID KING = UUID.randomUUID(), QUEEN = UUID.randomUUID(), A = UUID.randomUUID(), B = UUID.randomUUID(),
            WIFE = UUID.randomUUID(), GRANDCHILD = UUID.randomUUID(), UNCLE = UUID.randomUUID();

    @Test
    void lesCouplesSontCoteACoteEtLesEnfantsDessous() {
        List<TreeLayout.Node> nodes = List.of(
                new TreeLayout.Node(KING, null, null, QUEEN, 0),
                new TreeLayout.Node(QUEEN, null, null, KING, 0),
                new TreeLayout.Node(UNCLE, null, null, null, 0),
                new TreeLayout.Node(WIFE, null, null, A, 1),
                new TreeLayout.Node(A, KING, QUEEN, WIFE, 1),
                new TreeLayout.Node(B, KING, QUEEN, null, 1),
                new TreeLayout.Node(GRANDCHILD, A, WIFE, null, 2));
        Map<UUID, TreeLayout.Placed> placed = TreeLayout.layout(nodes);
        assertEquals(nodes.size(), placed.size(), "tout le monde a sa place");
        assertEquals(1, Math.abs(placed.get(KING).column() - placed.get(QUEEN).column()), "le roi et la reine sont côte à côte");
        assertEquals(placed.get(A).column() + 1, placed.get(WIFE).column(), "l'enfant de la maison tient la place, son conjoint vient après");
        assertEquals(2, placed.get(GRANDCHILD).generation());
        for (TreeLayout.Placed p : placed.values()) {
            for (TreeLayout.Placed q : placed.values()) {
                if (p != q && p.generation() == q.generation()) assertNotEquals(p.column(), q.column(), "deux personnes à la même place");
            }
        }
        double parents = (placed.get(KING).column() + placed.get(QUEEN).column()) / 2.0;
        double children = (placed.get(A).column() + placed.get(WIFE).column() + placed.get(B).column()) / 3.0;
        assertTrue(Math.abs(parents - children) <= 1.5, "les enfants s'éloignent de leurs parents : " + parents + " / " + children);
        assertTrue(Math.abs(placed.get(GRANDCHILD).column() - (placed.get(A).column() + placed.get(WIFE).column()) / 2.0) <= 0.5,
                "le petit-enfant n'est pas sous ses parents");
    }

    @Test
    void unArbreVideOuSansLienTientQuandMeme() {
        assertTrue(TreeLayout.layout(List.of()).isEmpty());
        Map<UUID, TreeLayout.Placed> placed = TreeLayout.layout(List.of(new TreeLayout.Node(A, UUID.randomUUID(), null, UUID.randomUUID(), 3),
                new TreeLayout.Node(B, null, null, null, 3)));
        assertEquals(2, placed.size());
        assertNotEquals(placed.get(A).column(), placed.get(B).column());
    }
}
