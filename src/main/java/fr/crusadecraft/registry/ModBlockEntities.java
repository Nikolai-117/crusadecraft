package fr.crusadecraft.registry;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.block.WarMapBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CrusadeCraft.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<WarMapBlockEntity>> WAR_MAP = BLOCK_ENTITIES.register("war_map",
            () -> BlockEntityType.Builder.of(WarMapBlockEntity::new, ModBlocks.WAR_MAP.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
