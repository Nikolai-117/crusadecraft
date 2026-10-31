package fr.crusadecraft.core.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Une construction d'un royaume (P14), posée dans le monde : avant-poste, tour, fort, camp, borne, donjon, village.
 * Ce n'est jamais une colonie MineColonies. Elle étend le territoire de son propriétaire, et une place forte se
 * prend en guerre : elle change alors de mains.
 *
 * <p>Le chantier avance bloc par bloc ({@link #progress()}) : d'un coup pour l'éditeur, peu à peu et payé par le
 * trésor en survie. Les blocs viennent d'un plan rejoué à l'identique ({@link Blueprints}), si bien qu'un chantier
 * interrompu par un arrêt du serveur reprend où il en était.
 */
public final class Site {
    private final UUID id;
    private final SiteKind kind;
    private String name;
    private UUID owner;
    private final ResourceLocation dimension;
    private final BlockPos anchor;
    private final Direction facing;
    /** La couleur du royaume quand on l'a posée : celle de ses bannières. */
    private final int color;
    private final boolean instant;
    private int progress;
    private boolean built;
    /** La province qu'elle loge (donjon) ou qu'elle a fondée (village), sinon −1. */
    private int province = -1;
    /** La communauté du registre des lignées qui l'habite (village), sinon {@code null}. */
    private UUID community;
    private final long createdDay;
    /** Posée par un royaume de l'ordinateur de lui-même (P22) : chaque bloc remplacé est noté, pour pouvoir tout défaire. */
    private boolean auto;

    public Site(UUID id, SiteKind kind, String name, UUID owner, ResourceLocation dimension, BlockPos anchor, Direction facing,
                int color, boolean instant, long createdDay) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.owner = owner;
        this.dimension = dimension;
        this.anchor = anchor.immutable();
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        this.color = color;
        this.instant = instant;
        this.createdDay = createdDay;
    }

    public UUID id() { return id; }
    public SiteKind kind() { return kind; }
    public String name() { return name; }
    public UUID owner() { return owner; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos anchor() { return anchor; }
    public Direction facing() { return facing; }
    public int color() { return color; }
    public boolean instant() { return instant; }
    public int progress() { return progress; }
    public boolean auto() { return auto; }
    public void setAuto(boolean auto) { this.auto = auto; }
    public boolean built() { return built; }
    public int province() { return province; }
    public long createdDay() { return createdDay; }
    public UUID community() { return community; }
    public void setCommunity(UUID community) { this.community = community; }

    public void setName(String name) { this.name = name; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public void setProgress(int progress) { this.progress = progress; }
    public void setBuilt(boolean built) { this.built = built; }
    public void setProvince(int province) { this.province = province; }

    /** Le territoire qu'elle étend : seulement une fois bâtie (un chantier ne tient encore rien). */
    public int radius() {
        return built ? kind.radius() : 0;
    }

    public double distanceSqr(double x, double z) {
        double dx = anchor.getX() + 0.5 - x, dz = anchor.getZ() + 0.5 - z;
        return dx * dx + dz * dz;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.name());
        t.putString("name", name);
        t.putUUID("owner", owner);
        t.putString("dim", dimension.toString());
        t.put("anchor", NbtUtils.writeBlockPos(anchor));
        t.putString("facing", facing.getName());
        t.putInt("color", color);
        t.putBoolean("instant", instant);
        t.putInt("progress", progress);
        t.putBoolean("built", built);
        t.putInt("province", province);
        t.putLong("created", createdDay);
        if (community != null) t.putUUID("community", community);
        if (auto) t.putBoolean("auto", true);
        return t;
    }

    public static Site load(CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        Direction facing = Direction.byName(t.getString("facing"));
        Site s = new Site(t.getUUID("id"), SiteKind.byName(t.getString("kind"), SiteKind.BORNE), t.getString("name"), t.getUUID("owner"),
                dim == null ? ResourceLocation.tryParse("minecraft:overworld") : dim, NbtUtils.readBlockPos(t.getCompound("anchor")),
                facing == null ? Direction.SOUTH : facing, t.getInt("color"), t.getBoolean("instant"), t.getLong("created"));
        s.progress = t.getInt("progress");
        s.built = t.getBoolean("built");
        s.province = t.contains("province") ? t.getInt("province") : -1;
        if (t.hasUUID("community")) s.community = t.getUUID("community");
        s.auto = t.getBoolean("auto");
        return s;
    }
}
