package fr.crusadecraft.item;

import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.network.packet.OpenRealmScreenPacket;
import fr.crusadecraft.network.packet.RequestRealmPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Le Registre du royaume : un livre de poche qui résume l'état du royaume de son porteur. */
public class RegistreItem extends Item {

    public RegistreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            RealmService.realmOf(sp.server, sp.getUUID()).ifPresentOrElse(
                    realm -> RequestRealmPacket.sendView(sp, realm, OpenRealmScreenPacket.Target.REGISTRE),
                    () -> sp.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.crusadecraft.registre.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
