package fr.crusadecraft.server;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.command.CrusadeCommand;
import fr.crusadecraft.compat.ConflictDetector;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.compat.minecolonies.MineColoniesEvents;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.registry.ModItems;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/** Événements serveur de Forge. */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CrusadeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConflictDetector.logAtStartup();
        MineColoniesEvents.assumeReadyIfSilent();
        RealmService.reconcileAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MineColoniesEvents.onServerStopped();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealmService.onLogin(player);
            CharacterService.syncTitles(player);
            ConflictDetector.warnPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 600 == 0) {
            CharacterService.periodic(event.getServer());
        }
    }

    /**
     * Côté serveur, clic droit sur un citoyen : avec le Sceptre, ou sur un dignitaire, CrusadeCraft prend
     * la main et MineColonies ne voit pas le clic. Accroupi et sans sceptre, MineColonies garde son
     * comportement. Le côté client (ouverture des écrans) est dans {@code ClientInteractionEvents}.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Optional<CitizenRef> ref = MineColoniesBridge.refOf(event.getTarget());
        if (ref.isEmpty()) return;
        boolean scepter = player.getMainHandItem().is(ModItems.SCEPTRE.get());
        if (scepter || (!player.isShiftKeyDown() && CharacterService.isDignitary(player.server, ref.get()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Le Trône royal ne se pose que dans l'Hôtel de ville d'une colonie que le joueur gère. Annuler
     * l'événement rend l'objet au joueur.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(ModBlocks.THRONE.get())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CrusadeConfig.SERVER.throneMustBeInTownHall.get() || !MineColoniesBridge.isReady()) return;
        if (player.isCreative() && player.hasPermissions(2)) return;
        if (!(event.getLevel() instanceof Level level)) return;

        Optional<ColonyRef> colony = MineColoniesBridge.colonyAt(level, event.getPos());
        boolean allowed = colony.isPresent()
                && MineColoniesBridge.isManager(colony.get(), player.getUUID())
                && MineColoniesBridge.isInsideTownHall(colony.get(), event.getPos());
        if (!allowed) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("crusadecraft.throne.placement"), true);
        }
    }
}
