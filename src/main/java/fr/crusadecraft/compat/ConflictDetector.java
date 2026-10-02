package fr.crusadecraft.compat;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * CrusadeCraft remplace War 'N Taxes et War 'n Nobility. S'ils sont installés en même temps, on
 * prévient, et les systèmes en doublon se coupent (chaque système interroge ces méthodes).
 */
public final class ConflictDetector {
    public static final String WAR_N_TAXES = "minecolonytax";
    public static final String WAR_N_NOBILITY = "warnnobility";

    private ConflictDetector() {}

    public static boolean warNTaxesPresent() {
        return ModList.get().isLoaded(WAR_N_TAXES);
    }

    public static boolean warNNobilityPresent() {
        return ModList.get().isLoaded(WAR_N_NOBILITY);
    }

    /** Impôts, guerre, espionnage : coupés si War 'N Taxes les fournit déjà. */
    public static boolean economyAndWarDisabled() {
        return warNTaxesPresent() && CrusadeConfig.SERVER.disableDuplicatesWithWarNTaxes.get();
    }

    /** Titres, sceaux, vassalité : coupés si War 'n Nobility les fournit déjà. */
    public static boolean nobilityDisabled() {
        return warNNobilityPresent() && CrusadeConfig.SERVER.disableDuplicatesWithWarNNobility.get();
    }

    public static void logAtStartup() {
        if (warNTaxesPresent()) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : War 'N Taxes est installé. CrusadeCraft le remplace ; "
                    + "retirez-le pour éviter les doublons.");
        }
        if (warNNobilityPresent()) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : War 'n Nobility est installé. CrusadeCraft le remplace ; "
                    + "retirez-le pour éviter les doublons.");
        }
    }

    public static void warnPlayer(ServerPlayer player) {
        if (warNTaxesPresent()) {
            player.sendSystemMessage(Component.translatable("crusadecraft.conflict.warntaxes"));
        }
        if (warNNobilityPresent()) {
            player.sendSystemMessage(Component.translatable("crusadecraft.conflict.warnnobility"));
        }
    }
}
