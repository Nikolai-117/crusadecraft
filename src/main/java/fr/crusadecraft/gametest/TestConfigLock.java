package fr.crusadecraft.gametest;

import com.electronwill.nightconfig.core.file.FileWatcher;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Sur le serveur de test seulement : le fichier de réglages de CrusadeCraft n'est plus relu à chaud.
 *
 * <p>Les tests changent des réglages ({@code set}) ; Forge réécrit alors le fichier, et son surveillant le relit sur
 * un autre fil. Si la relecture tombe entre deux changements, un réglage revient un instant à sa valeur précédente,
 * en plein test : des échecs au hasard, d'autant plus fréquents qu'il y a de tests. En jeu, rien ne change.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TestConfigLock {

    private TestConfigLock() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        for (ModConfig config : ConfigTracker.INSTANCE.configSets().getOrDefault(ModConfig.Type.SERVER, java.util.Set.of())) {
            if (!CrusadeCraft.MOD_ID.equals(config.getModId()) || config.getFullPath() == null) continue;
            FileWatcher.defaultInstance().removeWatch(config.getFullPath());
            CrusadeCraft.LOGGER.info("CrusadeCraft : réglages figés pour les tests ({})", config.getFileName());
        }
    }
}
