package fr.crusadecraft.item;

import fr.crusadecraft.core.city.CityStaff;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Le bâton d'intendant (P38) : donner un but aux bâtiments d'une ville. Clic droit sur la porte d'un bâtiment : il est
 * reconnu (son intérieur suivi sous le toit) et l'on choisit sa fonction — mairie, château, temple, boulangerie,
 * taverne, forge, maison… — qui amène ses gens. Accroupi, deux clics tracent une boîte à la main. Vers le ciel,
 * l'écran de la ville : ses bâtiments, ses gens, la reconnaissance de tous ses bâtiments d'un coup.
 */
public class IntendantStaffItem extends Item {
    public IntendantStaffItem(Properties properties) {
        super(properties);
    }

    /** Le bâton tenu par ce joueur, en main principale d'abord. */
    public static Optional<ItemStack> held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof IntendantStaffItem) return Optional.of(s);
        }
        return Optional.empty();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            CityStaff.useOn(player, ctx.getItemInHand(), ctx.getClickedPos(), ctx.getClickedFace(), player.isShiftKeyDown());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) CityStaff.useInAir(sp, stack, sp.isShiftKeyDown());
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("crusadecraft.city.staff.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crusadecraft.city.staff.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("crusadecraft.city.staff.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
