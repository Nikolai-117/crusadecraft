package fr.crusadecraft.core.lineage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * La disposition de l'arbre illustré d'une maison (P26), en logique pure : une rangée par génération, les conjoints
 * côte à côte, les enfants d'un couple groupés et centrés sous lui autant que la place le permet. L'écran n'a plus
 * qu'à dessiner des cases aux colonnes calculées ici.
 */
public final class TreeLayout {
    private TreeLayout() {}

    /** Une personne de l'arbre : ses parents et son conjoint s'ils sont dans l'arbre, sa génération. */
    public record Node(UUID id, UUID father, UUID mother, UUID spouse, int generation) {}

    /** Sa place : la rangée (génération) et la colonne. */
    public record Placed(UUID id, int generation, int column) {}

    public static Map<UUID, Placed> layout(List<Node> nodes) {
        Map<UUID, Node> byId = new LinkedHashMap<>();
        for (Node n : nodes) byId.put(n.id(), n);
        TreeMap<Integer, List<Node>> rows = new TreeMap<>();
        for (Node n : nodes) rows.computeIfAbsent(n.generation(), k -> new ArrayList<>()).add(n);
        Map<UUID, Integer> column = new HashMap<>();
        Map<UUID, Placed> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Node>> row : rows.entrySet()) {
            List<Node> people = row.getValue();
            Set<UUID> inRow = new HashSet<>();
            for (Node n : people) inRow.add(n.id());
            // Chacun avec son conjoint : celui qui a ses parents dans l'arbre tient la place, l'autre se met à côté.
            List<Node> anchors = new ArrayList<>();
            Map<UUID, Node> partner = new HashMap<>();
            Set<UUID> used = new HashSet<>();
            for (Node n : people) {
                if (!used.add(n.id())) continue;
                Node spouse = n.spouse() != null && inRow.contains(n.spouse()) && !used.contains(n.spouse()) ? byId.get(n.spouse()) : null;
                if (spouse == null) {
                    anchors.add(n);
                    continue;
                }
                used.add(spouse.id());
                Node anchor = !hasParents(n, column) && hasParents(spouse, column) ? spouse : n;
                anchors.add(anchor);
                partner.put(anchor.id(), anchor == n ? spouse : n);
            }
            // Sous leurs parents, de gauche à droite ; ceux qui n'en ont pas dans l'arbre gardent leur ordre, après.
            List<Node> sorted = new ArrayList<>(anchors);
            sorted.sort(Comparator.comparingDouble(n -> hasParents(n, column) ? parentMid(n, column) : Double.MAX_VALUE));
            int next = 0;
            int i = 0;
            while (i < sorted.size()) {
                Node first = sorted.get(i);
                String key = parentKey(first, column);
                int j = i, width = 0;
                do {
                    width += partner.containsKey(sorted.get(j).id()) ? 2 : 1;
                    j++;
                } while (key != null && j < sorted.size() && key.equals(parentKey(sorted.get(j), column)));
                int start = next;
                if (key != null) start = Math.max(next, (int) Math.round(parentMid(first, column) - (width - 1) / 2.0));
                int c = start;
                for (int k = i; k < j; k++) {
                    Node n = sorted.get(k);
                    column.put(n.id(), c);
                    out.put(n.id(), new Placed(n.id(), row.getKey(), c++));
                    Node p = partner.get(n.id());
                    if (p != null) {
                        column.put(p.id(), c);
                        out.put(p.id(), new Placed(p.id(), row.getKey(), c++));
                    }
                }
                next = c;
                i = j;
            }
        }
        return out;
    }

    private static boolean hasParents(Node n, Map<UUID, Integer> column) {
        return (n.father() != null && column.containsKey(n.father())) || (n.mother() != null && column.containsKey(n.mother()));
    }

    /** Le milieu de ses parents déjà placés : la colonne d'un seul, ou entre les deux. */
    static double parentMid(Node n, Map<UUID, Integer> column) {
        Integer f = n.father() == null ? null : column.get(n.father());
        Integer m = n.mother() == null ? null : column.get(n.mother());
        if (f != null && m != null) return (f + m) / 2.0;
        return f != null ? f : m != null ? m : 0;
    }

    /** Les frères et sœurs ont la même clé : leurs parents placés. */
    private static String parentKey(Node n, Map<UUID, Integer> column) {
        if (!hasParents(n, column)) return null;
        UUID f = n.father() != null && column.containsKey(n.father()) ? n.father() : null;
        UUID m = n.mother() != null && column.containsKey(n.mother()) ? n.mother() : null;
        return Objects.toString(f) + "|" + Objects.toString(m);
    }
}
