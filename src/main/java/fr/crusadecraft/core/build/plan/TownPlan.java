package fr.crusadecraft.core.build.plan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Le plan d'une ville bâtie (P34), enregistré avec sa construction : quels plans, où, tournés comment, et ses rues.
 * La hauteur de chaque bâtiment et de chaque case de rue est relevée sur le terrain au premier coup de pioche, puis
 * gardée : le chantier repris après un arrêt rejoue exactement les mêmes blocs.
 *
 * @param extent côté du carré qu'elle couvre, en blocs
 */
public final class TownPlan {

    /** Un bâtiment : son plan, son milieu relatif au centre, sa rotation (0 à 3 quarts de tour), sa hauteur relevée. */
    public static final class Piece {
        private final String ref;
        private final int dx, dz, rotation;
        private int dy;

        public Piece(String ref, int dx, int dz, int rotation, int dy) {
            this.ref = ref;
            this.dx = dx;
            this.dz = dz;
            this.rotation = Math.floorMod(rotation, 4);
            this.dy = dy;
        }

        public String ref() { return ref; }
        public int dx() { return dx; }
        public int dz() { return dz; }
        public int rotation() { return rotation; }
        public int dy() { return dy; }
        void setDy(int dy) { this.dy = dy; }
    }

    private final String style;
    private final TownSize size;
    private final List<Piece> pieces;
    private final long[] streets;
    private int[] streetY;
    private final int extent;
    private boolean surveyed;
    private int total;

    public TownPlan(String style, TownSize size, List<Piece> pieces, long[] streets, int extent) {
        this.style = style;
        this.size = size;
        this.pieces = pieces;
        this.streets = streets;
        this.streetY = new int[0];
        this.extent = extent;
    }

    public String style() { return style; }
    public TownSize size() { return size; }
    public List<Piece> pieces() { return pieces; }
    public long[] streets() { return streets; }
    public int[] streetY() { return streetY; }
    public int extent() { return extent; }
    public boolean surveyed() { return surveyed; }
    /** Étapes du chantier, connues une fois le plan dressé (0 avant). */
    public int total() { return total; }
    public void setTotal(int total) { this.total = total; }

    void setSurvey(int[] streetY) {
        this.streetY = streetY;
        this.surveyed = true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("style", style);
        t.putString("size", size.name());
        ListTag p = new ListTag();
        for (Piece piece : pieces) {
            CompoundTag c = new CompoundTag();
            c.putString("ref", piece.ref);
            c.putInt("dx", piece.dx);
            c.putInt("dz", piece.dz);
            c.putInt("rot", piece.rotation);
            c.putInt("dy", piece.dy);
            p.add(c);
        }
        t.put("pieces", p);
        t.putLongArray("streets", streets);
        t.putIntArray("streetY", streetY);
        t.putInt("extent", extent);
        t.putBoolean("surveyed", surveyed);
        t.putInt("total", total);
        return t;
    }

    public static TownPlan load(CompoundTag t) {
        List<Piece> pieces = new ArrayList<>();
        ListTag p = t.getList("pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < p.size(); i++) {
            CompoundTag c = p.getCompound(i);
            pieces.add(new Piece(c.getString("ref"), c.getInt("dx"), c.getInt("dz"), c.getInt("rot"), c.getInt("dy")));
        }
        TownSize size;
        try {
            size = TownSize.valueOf(t.getString("size"));
        } catch (IllegalArgumentException e) {
            size = TownSize.HAMEAU;
        }
        TownPlan plan = new TownPlan(t.getString("style"), size, pieces, t.getLongArray("streets"), t.getInt("extent"));
        plan.streetY = t.getIntArray("streetY");
        plan.surveyed = t.getBoolean("surveyed");
        plan.total = t.getInt("total");
        return plan;
    }
}
