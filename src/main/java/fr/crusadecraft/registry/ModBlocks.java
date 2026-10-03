package fr.crusadecraft.registry;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.block.CouncilTableBlock;
import fr.crusadecraft.block.CourtFurnitureBlock;
import fr.crusadecraft.block.MayorDeskBlock;
import fr.crusadecraft.block.ThroneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CrusadeCraft.MOD_ID);

    public static final RegistryObject<Block> THRONE = BLOCKS.register("throne", () -> new ThroneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5F, 6.0F)
                    .sound(SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> COUNCIL_TABLE = BLOCKS.register("council_table", () -> new CouncilTableBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    /** Siège du conseil : les conseillers s'y assoient pendant les réunions (forme : assise basse, dossier). */
    public static final RegistryObject<Block> COUNCIL_SEAT = BLOCKS.register("council_seat", () -> new CourtFurnitureBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion(),
            new double[][]{{2, 0, 2, 14, 10, 14}, {2, 10, 13, 14, 16, 15}}));

    public static final RegistryObject<Block> MAYOR_DESK = BLOCKS.register("mayor_desk", () -> new MayorDeskBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> TREASURY = BLOCKS.register("treasury", () -> new fr.crusadecraft.block.TreasuryBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.5F, 1200.0F).sound(SoundType.WOOD).noOcclusion()));

    /** Cadre de la Table de guerre, à accrocher au mur en carré de 3 × 3. */
    public static final RegistryObject<Block> WAR_MAP = BLOCKS.register("war_map", () -> new fr.crusadecraft.block.WarMapBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().noCollission()));

    /** Borne de frontière, décorative. */
    public static final RegistryObject<Block> BOUNDARY_STONE = BLOCKS.register("boundary_stone", () -> new CourtFurnitureBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE).noOcclusion()
                    .requiresCorrectToolForDrops(),
            new double[][]{{5, 0, 5, 11, 13, 11}, {4, 13, 4, 12, 16, 12}}));

    public static final RegistryObject<Block> COLOMBIER = BLOCKS.register("colombier", () -> new fr.crusadecraft.block.ColombierBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));

    public static final RegistryObject<Block> CHANCERY = BLOCKS.register("chancery", () -> new fr.crusadecraft.block.ChanceryBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 6.0F).sound(SoundType.WOOD).noOcclusion()));

    /** La Lice de tournoi (P10). */
    public static final RegistryObject<Block> TOURNAMENT = BLOCKS.register("tournament", () -> new fr.crusadecraft.block.TournamentBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
