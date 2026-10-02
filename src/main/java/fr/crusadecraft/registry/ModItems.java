package fr.crusadecraft.registry;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.item.RegistreItem;
import fr.crusadecraft.item.SceptreItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CrusadeCraft.MOD_ID);

    public static final RegistryObject<Item> THRONE = ITEMS.register("throne",
            () -> new BlockItem(ModBlocks.THRONE.get(), new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> REGISTRE = ITEMS.register("registre",
            () -> new RegistreItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCEPTRE = ITEMS.register("sceptre",
            () -> new SceptreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
