package fr.crusadecraft.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Une province d'un royaume IA, avec son siège ancré dans le monde : de préférence un village, sinon un
 * point de terre ferme. Rien n'est posé à cet endroit ; c'est un repère pour la carte et la découverte.
 */
public final class AiProvince {

    /** Ce qui ancre le siège. */
    public enum Anchor {
        /** Un village déjà généré, lu dans les chunks enregistrés. */
        VILLAGE,
        /** Un village prévu par la génération du monde, dans une région encore jamais visitée. */
        VILLAGE_PREDICTED,
        /** Un point de terre ferme. */
        LAND;

        public String key() {
            return "crusadecraft.anchor." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final int id;
    private final String name;
    private final ResourceLocation dimension;
    private final BlockPos seat;
    private final Anchor anchor;
    private final boolean capital;
    private final int dejureKingdom;
    private int population;
    private int development;
    /** Le royaume qui la tient, s'il n'est pas le sien (conquête) ; {@code null} sinon. */
    private java.util.UUID holder;

    public AiProvince(int id, String name, ResourceLocation dimension, BlockPos seat, Anchor anchor, boolean capital,
                      int dejureKingdom, int population, int development) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.seat = seat.immutable();
        this.anchor = anchor;
        this.capital = capital;
        this.dejureKingdom = dejureKingdom;
        this.population = population;
        this.development = development;
    }

    public int id() { return id; }
    public String name() { return name; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos seat() { return seat; }
    public Anchor anchor() { return anchor; }
    public boolean capital() { return capital; }
    public int dejureKingdom() { return dejureKingdom; }
    public int population() { return population; }
    public int development() { return development; }

    public java.util.UUID holder() { return holder; }
    public void setHolder(java.util.UUID holder) { this.holder = holder; }

    /** Revenu quotidien de la province, pour qui la tient. */
    public long income() {
        return Math.round(population / 4.0 + development / 5.0);
    }

    public void setPopulation(int population) { this.population = Math.max(1, population); }
    public void setDevelopment(int development) { this.development = Math.max(0, Math.min(100, development)); }

    public double distanceSqr(double x, double z) {
        double dx = seat.getX() + 0.5 - x, dz = seat.getZ() + 0.5 - z;
        return dx * dx + dz * dz;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putString("name", name);
        t.putString("dim", dimension.toString());
        t.put("seat", NbtUtils.writeBlockPos(seat));
        t.putString("anchor", anchor.name());
        t.putBoolean("capital", capital);
        t.putInt("dejure", dejureKingdom);
        t.putInt("population", population);
        t.putInt("development", development);
        if (holder != null) t.putUUID("holder", holder);
        return t;
    }

    public static AiProvince load(CompoundTag t) {
        Anchor anchor;
        try {
            anchor = Anchor.valueOf(t.getString("anchor"));
        } catch (IllegalArgumentException e) {
            anchor = Anchor.LAND;
        }
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        AiProvince p = new AiProvince(t.getInt("id"), t.getString("name"), dim == null ? ResourceLocation.tryParse("minecraft:overworld") : dim,
                NbtUtils.readBlockPos(t.getCompound("seat")), anchor, t.getBoolean("capital"), t.getInt("dejure"),
                t.getInt("population"), t.getInt("development"));
        if (t.hasUUID("holder")) p.holder = t.getUUID("holder");
        return p;
    }
}
