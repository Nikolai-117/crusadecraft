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
    /** P34 : une ville bâtie d'après les plans des packs de style, au lieu du plan dessiné par le code. */
    private fr.crusadecraft.core.build.plan.TownPlan town;
    /** P38 : une ville posée d'après un fichier (une schématique de ville entière) : son plan, sa taille, son sol. */
    private String importedRef;
    private int importedX, importedY, importedZ, importedGround;
    /** P38 : les bâtiments de la ville, délimités au bâton d'intendant, et leur fonction. */
    private final java.util.List<fr.crusadecraft.core.city.CityBuilding> buildings = new java.util.ArrayList<>();

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
    public fr.crusadecraft.core.build.plan.TownPlan town() { return town; }
    public void setTown(fr.crusadecraft.core.build.plan.TownPlan town) { this.town = town; }
    public String importedRef() { return importedRef; }
    public boolean imported() { return importedRef != null; }
    public int importedHeight() { return importedY; }
    public int importedGround() { return importedGround; }
    public java.util.List<fr.crusadecraft.core.city.CityBuilding> buildings() { return buildings; }

    public void setImported(String ref, int sizeX, int sizeY, int sizeZ, int groundY) {
        this.importedRef = ref;
        this.importedX = sizeX;
        this.importedY = sizeY;
        this.importedZ = sizeZ;
        this.importedGround = groundY;
    }

    public java.util.Optional<fr.crusadecraft.core.city.CityBuilding> building(UUID id) {
        for (var b : buildings) if (b.id().equals(id)) return java.util.Optional.of(b);
        return java.util.Optional.empty();
    }

    /** Le côté de son emprise : celui de son genre, ou celui de sa ville quand elle est bâtie d'après les plans. */
    public int extent() {
        if (town != null) return town.extent();
        return importedRef != null ? Math.max(importedX, importedZ) : kind.size();
    }

    /** Le territoire qu'elle étend : seulement une fois bâtie (un chantier ne tient encore rien). */
    public int radius() {
        int own = town != null || importedRef != null ? extent() / 2 + 32 : 0;
        return built ? Math.max(kind.radius(), own) : 0;
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
        if (town != null) t.put("town", town.save());
        if (importedRef != null) {
            CompoundTag i = new CompoundTag();
            i.putString("ref", importedRef);
            i.putInt("sx", importedX);
            i.putInt("sy", importedY);
            i.putInt("sz", importedZ);
            i.putInt("ground", importedGround);
            t.put("imported", i);
        }
        if (!buildings.isEmpty()) {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (var b : buildings) l.add(b.save());
            t.put("buildings", l);
        }
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
        if (t.contains("town")) s.town = fr.crusadecraft.core.build.plan.TownPlan.load(t.getCompound("town"));
        if (t.contains("imported")) {
            CompoundTag i = t.getCompound("imported");
            s.setImported(i.getString("ref"), i.getInt("sx"), i.getInt("sy"), i.getInt("sz"), i.getInt("ground"));
        }
        net.minecraft.nbt.ListTag l = t.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int k = 0; k < l.size(); k++) s.buildings.add(fr.crusadecraft.core.city.CityBuilding.load(l.getCompound(k)));
        return s;
    }
}
