package fr.crusadecraft.core.city;

import fr.crusadecraft.config.CrusadeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * S'asseoir (P45), joueurs comme habitants : sur une chaise, un banc, un tabouret de n'importe quel mod, sur le siège du
 * conseil, ou sur un escalier posé à l'endroit. On s'y assied sur un support invisible, retiré dès qu'on se lève.
 */
public final class CitySeats {
    public static final String TAG = "crusadecraft_siege";
    /** Quand un habitant cherche où s'asseoir : à tant de blocs autour de lui. */
    static final int LOOK = 5, LOOK_Y = 2;

    private static final List<ArmorStand> ACTIVE = new ArrayList<>();

    private CitySeats() {}

    public static void onServerStopped() {
        for (ArmorStand s : ACTIVE) s.discard();
        ACTIVE.clear();
    }

    /** Ce bloc est-il fait pour s'asseoir ? */
    public static boolean sittable(BlockState st) {
        // Le Trône a son propre usage : le clic droit ouvre le menu du royaume.
        if (st.isAir() || st.getBlock() instanceof fr.crusadecraft.block.ThroneBlock) return false;
        boolean stairs = st.getBlock() instanceof StairBlock && st.hasProperty(BlockStateProperties.HALF) && st.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
        return Furnishing.sittable(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), stairs);
    }

    /** La hauteur où l'on est assis : celle du bloc, posé sur sa forme ; une marche se prend à mi-hauteur. */
    private static double surface(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.getBlock() instanceof StairBlock) return 0.5;
        var shape = st.getShape(level, pos);
        double top = shape.isEmpty() ? 0.5 : shape.bounds().maxY;
        // Un dossier monte plus haut que l'assise : on s'assied à hauteur de siège.
        return top > 0.75 ? 0.5 : top;
    }

    /** Un clic droit de joueur, les mains vides, sur quelque chose où s'asseoir. Vrai si le geste est pris. */
    public static boolean onUse(ServerPlayer player, BlockPos pos) {
        if (!CrusadeConfig.SERVER.usefulFurniture.get()) return false;
        if (player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.isPassenger()) return false;
        ServerLevel level = player.serverLevel();
        if (!sittable(level.getBlockState(pos)) || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        if (taken(level, pos) || player.distanceToSqr(pos.getCenter()) > 16) return false;
        return sit(level, player, pos);
    }

    /** Quelqu'un y est-il déjà assis ? */
    static boolean taken(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(0.2, 0.5, 0.2), s -> s.getTags().contains(TAG) && !s.getPassengers().isEmpty()).isEmpty();
    }

    /** Il s'assied. */
    public static boolean sit(ServerLevel level, LivingEntity who, BlockPos pos) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) return false;
        CompoundTag t = new CompoundTag();
        t.putBoolean("Marker", true);
        t.putBoolean("Invisible", true);
        t.putBoolean("NoGravity", true);
        t.putBoolean("Invulnerable", true);
        t.putBoolean("Silent", true);
        stand.load(t);
        stand.addTag(TAG);
        BlockState st = level.getBlockState(pos);
        double y = pos.getY() + surface(level, pos, st) - 0.25;
        stand.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, who.getYRot(), 0);
        if (!level.addFreshEntity(stand)) return false;
        if (!who.startRiding(stand, true)) {
            stand.discard();
            return false;
        }
        ACTIVE.add(stand);
        return true;
    }

    /** Un siège libre près d'ici, s'il y en a un. */
    static BlockPos findNear(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= LOOK; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = -LOOK_Y; dy <= LOOK_Y; dy++) {
                        m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (!level.isLoaded(m)) continue;
                        BlockState st = level.getBlockState(m);
                        // Pour un habitant, de vrais sièges seulement : un escalier est le plus souvent un escalier.
                        if (st.getBlock() instanceof StairBlock || !sittable(st)) continue;
                        if (!level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty() || taken(level, m)) continue;
                        return m.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Toutes les secondes : un support dont le cavalier s'est levé disparaît. */
    public static void tick() {
        ACTIVE.removeIf(s -> {
            if (s.isRemoved()) return true;
            if (s.getPassengers().isEmpty()) {
                s.discard();
                return true;
            }
            return false;
        });
    }

    /** Ceux qu'un arrêt brutal aurait laissés. */
    static void sweep(ServerLevel level, AABB box) {
        for (ArmorStand s : level.getEntitiesOfClass(ArmorStand.class, box, x -> x.getTags().contains(TAG) && x.getPassengers().isEmpty())) s.discard();
    }

    public static boolean seated(Entity e) {
        return e.getVehicle() instanceof ArmorStand a && a.getTags().contains(TAG);
    }
}
