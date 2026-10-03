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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import fr.crusadecraft.core.war.WarFieldService;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /** Les événements à choix se lisent dans les datapacks, au démarrage et à chaque /reload. */
    @SubscribeEvent
    public static void onReloadListeners(net.minecraftforge.event.AddReloadListenerEvent event) {
        event.addListener(new fr.crusadecraft.core.event.EventLoader());
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
        fr.crusadecraft.core.council.CouncilService.onServerStopped();
        fr.crusadecraft.core.council.RequestService.onServerStopped();
        fr.crusadecraft.core.world.WorldService.onServerStopped();
        fr.crusadecraft.core.diplomacy.DiplomacyService.onServerStopped();
        WarFieldService.onServerStopped();
        fr.crusadecraft.core.court.TournamentService.onServerStopped();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RealmService.onLogin(player);
            CharacterService.syncTitles(player);
            ConflictDetector.warnPlayer(player);
        }
    }

    /** Chaque citoyen chargé reçoit l'état « service » (réunions, bureau, audiences). */
    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        // Un héraut resté d'une session interrompue n'a plus rien à faire là.
        if (fr.crusadecraft.core.world.WorldService.isStrayHerald(event.getEntity()) || WarFieldService.isStray(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        fr.crusadecraft.compat.minecolonies.DutyAI.install(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        int t = server.getTickCount();
        if (t % 600 == 0) CharacterService.periodic(server);
        if (t % 40 == 0) fr.crusadecraft.core.council.CouncilService.tick(server);
        if (t % 100 == 50) fr.crusadecraft.core.council.RequestService.deliver(server);
        if (t % 1200 == 300) fr.crusadecraft.core.council.RequestService.periodic(server);
        if (t % 2400 == 900) fr.crusadecraft.core.council.CouncilService.applyHappiness(server);
        if (t % 40 == 20) fr.crusadecraft.core.world.WorldService.tick(server);
        if (t % 40 == 30) fr.crusadecraft.core.diplomacy.DiplomacyService.tick(server);
        if (t % (CrusadeConfig.SERVER.aiLetterMinutes.get() * 1200) == 600) fr.crusadecraft.core.diplomacy.DiplomacyService.aiLetters(server);
        if (t % 20 == 10) WarFieldService.tick(server);
        if (t % 5 == 2) WarFieldService.restoreTick(server);
    }

    /** Les golems d'un village assiégé ne s'en prennent pas à sa garnison. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!event.getEntity().level().isClientSide() && WarFieldService.sameSide(event.getEntity(), event.getNewTarget())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide() && WarFieldService.sameSide(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
    }

    /** Une troupe de guerre qui tombe compte au score de sa guerre ; un villageois du registre des lignées y meurt aussi. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            WarFieldService.onDeath(level.getServer(), event.getEntity());
            fr.crusadecraft.core.lineage.LineageService.onDeath(level.getServer(), event.getEntity());
        }
    }

    /** En territoire ennemi, on ne démolit pas à la pioche : les murs tombent par explosifs, et se relèvent à la paix. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!WarFieldService.breakAllowed(player.server, player, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("crusadecraft.war.antigrief"), true);
        }
    }

    /** Ce qu'on pose en territoire ennemi (échelles, barricades) sera retiré à la paix. Une Borne de frontière annexe une colonie. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaceInWar(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof Level level)) return;
        WarFieldService.onPlace(player.server, player, level, event.getPos(), event.getBlockSnapshot().getReplacedBlock());
        if (event.getPlacedBlock().is(ModBlocks.BOUNDARY_STONE.get()) && MineColoniesBridge.isReady()) {
            MineColoniesBridge.colonyAt(level, event.getPos())
                    .flatMap(c -> RealmService.annexOwnColony(player, c))
                    .ifPresent(msg -> player.displayClientMessage(msg, true));
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) WarFieldService.onExplosion(level.getServer(), level, event.getAffectedBlocks());
    }

    /**
     * Côté serveur, clic droit sur un citoyen : avec le Sceptre, ou sur un dignitaire, CrusadeCraft prend
     * la main et MineColonies ne voit pas le clic. Accroupi et sans sceptre, MineColonies garde son
     * comportement. Le côté client (ouverture des écrans) est dans {@code ClientInteractionEvents}.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) return;
        // Le héraut d'un royaume IA ne commerce pas : il présente son royaume.
        Optional<java.util.UUID> herald = fr.crusadecraft.core.world.WorldService.heraldRealm(event.getTarget());
        if (herald.isPresent()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            fr.crusadecraft.network.packet.WorldPackets.sendAiRealm(player, herald.get());
            return;
        }
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
        if (event.getEntity() instanceof ServerPlayer p && event.getLevel() instanceof Level lvl && MineColoniesBridge.isReady()
                && !(p.isCreative() && p.hasPermissions(2))) {
            Optional<Component> refusal = Optional.empty();
            if (event.getPlacedBlock().is(ModBlocks.COUNCIL_TABLE.get())) refusal = fr.crusadecraft.block.CourtBlocks.checkTable(p, lvl, event.getPos());
            else if (event.getPlacedBlock().is(ModBlocks.MAYOR_DESK.get())) refusal = fr.crusadecraft.block.CourtBlocks.checkDesk(p, lvl, event.getPos());
            else if (event.getPlacedBlock().is(ModBlocks.TREASURY.get())) refusal = fr.crusadecraft.block.CourtBlocks.checkTreasury(p, lvl, event.getPos());
            else if (event.getPlacedBlock().is(ModBlocks.WAR_MAP.get())) refusal = fr.crusadecraft.block.CourtBlocks.checkWarMap(p, lvl, event.getPos());
            else if (event.getPlacedBlock().is(ModBlocks.COLOMBIER.get()) || event.getPlacedBlock().is(ModBlocks.CHANCERY.get())
                    || event.getPlacedBlock().is(ModBlocks.TOURNAMENT.get())) {
                refusal = fr.crusadecraft.block.CourtBlocks.checkTreasury(p, lvl, event.getPos());
            }
            if (refusal.isPresent()) {
                event.setCanceled(true);
                p.displayClientMessage(refusal.get(), true);
                return;
            }
        }
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
