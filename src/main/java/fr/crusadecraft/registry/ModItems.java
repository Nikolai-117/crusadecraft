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

    public static final RegistryObject<Item> COUNCIL_TABLE = ITEMS.register("council_table",
            () -> new BlockItem(ModBlocks.COUNCIL_TABLE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> COUNCIL_SEAT = ITEMS.register("council_seat",
            () -> new BlockItem(ModBlocks.COUNCIL_SEAT.get(), new Item.Properties()));
    public static final RegistryObject<Item> MAYOR_DESK = ITEMS.register("mayor_desk",
            () -> new BlockItem(ModBlocks.MAYOR_DESK.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> WAR_MAP = ITEMS.register("war_map",
            () -> new BlockItem(ModBlocks.WAR_MAP.get(), new Item.Properties()));
    public static final RegistryObject<Item> BOUNDARY_STONE = ITEMS.register("boundary_stone",
            () -> new BlockItem(ModBlocks.BOUNDARY_STONE.get(), new Item.Properties()));
    public static final RegistryObject<Item> COLOMBIER = ITEMS.register("colombier",
            () -> new BlockItem(ModBlocks.COLOMBIER.get(), new Item.Properties()));
    public static final RegistryObject<Item> CHANCERY = ITEMS.register("chancery",
            () -> new BlockItem(ModBlocks.CHANCERY.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> LETTER = ITEMS.register("letter",
            () -> new fr.crusadecraft.item.LetterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SEAL = ITEMS.register("seal",
            () -> new fr.crusadecraft.item.SealItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CLAIM_SCROLL = ITEMS.register("claim_scroll",
            () -> new fr.crusadecraft.item.ClaimScrollItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CHRONIQUES = ITEMS.register("chroniques",
            () -> new fr.crusadecraft.item.ChroniclesItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TOURNAMENT = ITEMS.register("tournament",
            () -> new BlockItem(ModBlocks.TOURNAMENT.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> TREASURY = ITEMS.register("treasury",
            () -> new BlockItem(ModBlocks.TREASURY.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> BORNAGE = ITEMS.register("baton_de_bornage",
            () -> new fr.crusadecraft.item.BornageStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** P34 : l'outil de construction créatif. */
    public static final RegistryObject<Item> PLAN = ITEMS.register("plan_architecte",
            () -> new fr.crusadecraft.item.PlanItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** P38 : donner un but aux bâtiments d'une ville. */
    public static final RegistryObject<Item> INTENDANT = ITEMS.register("baton_intendant",
            () -> new fr.crusadecraft.item.IntendantStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
