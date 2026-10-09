package fr.crusadecraft;

import com.mojang.logging.LogUtils;
import fr.crusadecraft.compat.minecolonies.MineColoniesEvents;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.registry.ModBlocks;
import fr.crusadecraft.registry.ModCreativeTabs;
import fr.crusadecraft.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * CrusadeCraft : un Crusader Kings dans Minecraft, branché sur MineColonies.
 *
 * <p>Usage personnel uniquement ; ne pas redistribuer.
 */
@Mod(CrusadeCraft.MOD_ID)
public final class CrusadeCraft {
    public static final String MOD_ID = "crusadecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Forge 47.3+ injecte le contexte de chargement (l'ancien {@code get()} est déprécié). */
    public CrusadeCraft(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        fr.crusadecraft.registry.ModBlockEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(this::commonSetup);

        context.registerConfig(ModConfig.Type.SERVER, CrusadeConfig.SERVER_SPEC, "crusadecraft-server.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CrusadeConfig.CLIENT_SPEC, "crusadecraft-client.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // La carte web (P17) : on s'inscrit auprès de Dynmap s'il est là ; il nous préviendra quand sa carte sera prête.
        fr.crusadecraft.compat.dynmap.DynmapBridge.init();
        event.enqueueWork(() -> {
            CrusadeNetwork.register();
            MineColoniesEvents.register();
            fr.crusadecraft.core.council.CouncilService.init();
        });
    }
}
