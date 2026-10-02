package fr.crusadecraft;

import com.mojang.logging.LogUtils;
import fr.crusadecraft.compat.minecolonies.MineColoniesEvents;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.registry.ModBlocks;
import fr.crusadecraft.registry.ModCreativeTabs;
import fr.crusadecraft.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
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

    public CrusadeCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CrusadeConfig.SERVER_SPEC, "crusadecraft-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CrusadeConfig.CLIENT_SPEC, "crusadecraft-client.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CrusadeNetwork.register();
            MineColoniesEvents.register();
        });
    }
}
