package fr.crusadecraft.block;

import fr.crusadecraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Entité du cadre de la Table de guerre. Elle ne stocke rien : elle existe pour que le client dessine sa
 * part de la carte par-dessus le cadre.
 */
public class WarMapBlockEntity extends BlockEntity {

    public WarMapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAR_MAP.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }
}
