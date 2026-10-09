package fr.crusadecraft.server;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.world.ProtectionService;
import fr.crusadecraft.core.world.ProtectionService.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Les protections des terres (P16) branchées sur les gestes du joueur : casser, poser, vider un seau, ouvrir un coffre
 * ou une porte, frapper un animal ou un villageois. La décision est celle de {@link ProtectionService}.
 */
@Mod.EventBusSubscriber(modid = CrusadeCraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProtectionEvents {

    private ProtectionEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && ProtectionService.refuse(player, event.getPos(), Action.BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ProtectionService.refuse(player, event.getPos(), Action.PLACE)) {
            event.setCanceled(true);
        }
    }

    /** Ouvrir un coffre, un four, une porte, actionner un levier ou un bouton. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        boolean interactive = state.hasBlockEntity() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock;
        if (interactive && ProtectionService.refuse(player, event.getPos(), Action.INTERACT)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBucket(FillBucketEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        HitResult hit = event.getTarget();
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = b.getBlockPos().relative(b.getDirection());
            if (ProtectionService.refuse(player, pos, Action.PLACE)) event.setCanceled(true);
        }
    }

    /** Les bêtes, les villageois, les supports d'armure et les cadres : pas les monstres. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        boolean protectedTarget = target instanceof Animal || target instanceof AbstractVillager || target instanceof ArmorStand
                || target instanceof HangingEntity;
        if (protectedTarget && ProtectionService.refuse(player, target.blockPosition(), Action.ATTACK)) event.setCanceled(true);
    }

    /** Tourner l'objet d'un cadre, vêtir un support d'armure. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityUse(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        if ((target instanceof ArmorStand || target instanceof HangingEntity)
                && ProtectionService.refuse(player, target.blockPosition(), Action.INTERACT)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
