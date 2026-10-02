package fr.crusadecraft.registry;

import fr.crusadecraft.CrusadeCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrusadeCraft.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.crusadecraft"))
            .icon(() -> new ItemStack(ModItems.THRONE.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.THRONE.get());
                output.accept(ModItems.REGISTRE.get());
                output.accept(ModItems.SCEPTRE.get());
                output.accept(ModItems.COUNCIL_TABLE.get());
                output.accept(ModItems.COUNCIL_SEAT.get());
                output.accept(ModItems.MAYOR_DESK.get());
            })
            .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
