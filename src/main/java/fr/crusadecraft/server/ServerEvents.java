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
import net.minecraftforge.event.server.ServerStoppingEvent;
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
        event.addListener(new fr.crusadecraft.core.lineage.Repliques());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConflictDetector.logAtStartup();
        MineColoniesEvents.assumeReadyIfSilent();
        RealmService.reconcileAll(event.getServer());
    }

    /** Un joueur part : on lui renverra le centre de ses cartes papier à son retour. */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        fr.crusadecraft.core.world.PaperMapService.onLogout(event.getEntity().getUUID());
        fr.crusadecraft.core.world.EntryTitles.onLogout(event.getEntity().getUUID());
        fr.crusadecraft.core.world.ProtectionService.onLogout(event.getEntity().getUUID());
        fr.crusadecraft.core.world.TerritorySync.onLogout(event.getEntity().getUUID());
        fr.crusadecraft.core.world.terrain.TerrainService.onLogout(event.getEntity().getUUID());
    }

    /** Avant que le monde ne se ferme : les chunks parcourus de la carte en terrain sont écrits. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        fr.crusadecraft.core.world.terrain.TerrainService.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MineColoniesEvents.onServerStopped();
        fr.crusadecraft.core.council.CouncilService.onServerStopped();
        fr.crusadecraft.core.council.RequestService.onServerStopped();
        fr.crusadecraft.core.world.WorldService.onServerStopped();
        fr.crusadecraft.core.world.Territory.onServerStopped();
        fr.crusadecraft.core.world.TerritoryService.onServerStopped();
        fr.crusadecraft.core.build.SiteService.onServerStopped();
        fr.crusadecraft.core.world.PaperMapService.onServerStopped();
        fr.crusadecraft.core.world.EntryTitles.onServerStopped();
        fr.crusadecraft.compat.dynmap.DynmapBridge.onServerStopped();
        fr.crusadecraft.core.world.TerritorySync.onServerStopped();
        fr.crusadecraft.core.diplomacy.DiplomacyService.onServerStopped();
        WarFieldService.onServerStopped();
        fr.crusadecraft.core.war.ArmyService.onServerStopped();
        fr.crusadecraft.core.war.HostService.onServerStopped();
        fr.crusadecraft.core.diplomacy.CaravanService.onServerStopped();
        fr.crusadecraft.core.diplomacy.TradeService.onServerStopped();
        fr.crusadecraft.core.court.TournamentService.onServerStopped();
        fr.crusadecraft.core.lineage.RoutineService.onServerStopped();
        fr.crusadecraft.core.world.terrain.TerrainService.onServerStopped();
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
        // Chaque système est chronométré pour /crusade perf (P24).
        fr.crusadecraft.core.Perf.tick();
        if (t % 600 == 0) perf("personnages", () -> CharacterService.periodic(server));
        if (t % 40 == 0) perf("conseil", () -> fr.crusadecraft.core.council.CouncilService.tick(server));
        if (t % 100 == 50) perf("requetes", () -> fr.crusadecraft.core.council.RequestService.deliver(server));
        if (t % 1200 == 300) perf("requetes", () -> fr.crusadecraft.core.council.RequestService.periodic(server));
        if (t % 2400 == 900) perf("bonheur", () -> fr.crusadecraft.core.council.CouncilService.applyHappiness(server));
        if (t % 40 == 20) perf("monde", () -> fr.crusadecraft.core.world.WorldService.tick(server));
        if (t % 40 == 30) perf("diplomatie", () -> fr.crusadecraft.core.diplomacy.DiplomacyService.tick(server));
        if (t % (CrusadeConfig.SERVER.aiLetterMinutes.get() * 1200) == 600) perf("lettres", () -> fr.crusadecraft.core.diplomacy.DiplomacyService.aiLetters(server));
        if (t % 20 == 10) perf("guerre", () -> WarFieldService.tick(server));
        if (t % 5 == 2) perf("reparations", () -> WarFieldService.restoreTick(server));
        // P14 : le bâton de bornage en marchant (deux fois par seconde), les chantiers (chaque tick, par petits lots).
        if (t % 10 == 4) perf("baton", () -> fr.crusadecraft.core.world.TerritoryService.walkTick(server));
        if (t % 40 == 12) perf("cartes_papier", () -> fr.crusadecraft.core.world.PaperMapService.tick(server));
        perf("chantiers", () -> fr.crusadecraft.core.build.SiteService.tick(server, t));
        // Les lieux suivent le monde (une ville renommée, un royaume disparu) : toutes les vingt secondes suffisent.
        if (t % 400 == 200) perf("lieux", () -> fr.crusadecraft.core.place.PlaceService.sync(server));
        // Le passage d'une frontière de chunk à l'autre : une vérification par seconde suffit.
        if (t % 20 == 7) perf("titres_entree", () -> fr.crusadecraft.core.world.EntryTitles.tick(server));
        if (t % 20 == 11) perf("armees", () -> fr.crusadecraft.core.war.ArmyService.tick(server));
        if (t % 100 == 37) perf("ostes_ia", () -> fr.crusadecraft.core.war.HostService.tick(server));
        if (t % 100 == 63) perf("caravanes", () -> fr.crusadecraft.core.diplomacy.CaravanService.tick(server));
        if (t % 400 == 151) perf("zones", () -> fr.crusadecraft.core.world.ZoneService.autoAttach(server));
        perf("dynmap", () -> fr.crusadecraft.compat.dynmap.DynmapBridge.tick(server, t));
        // Les terres des cartes du client (Xaero) : toutes les cinq secondes, seulement si elles ont changé.
        if (t % 100 == 60) perf("xaero", () -> fr.crusadecraft.core.world.TerritorySync.tick(server));
        if (t % 100 == 80) perf("routines_pnj", () -> fr.crusadecraft.core.lineage.RoutineService.tick(server));
        // La carte en terrain (P18b) : la vraie surface des chunks parcourus, toutes les deux secondes.
        if (t % 40 == 17) perf("terrain", () -> fr.crusadecraft.core.world.terrain.TerrainService.tick(server));
    }

    private static void perf(String system, Runnable task) {
        fr.crusadecraft.core.Perf.run(system, task);
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
            fr.crusadecraft.core.war.ArmyService.onDeath(level.getServer(), event.getEntity());
            fr.crusadecraft.core.war.HostService.onDeath(level.getServer(), event.getEntity());
            fr.crusadecraft.core.diplomacy.CaravanService.onDeath(level.getServer(), event.getEntity(), event.getSource().getEntity());
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
