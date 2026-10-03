package fr.crusadecraft.item;

import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.IntriguePackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les Chroniques du royaume : un livre qui s'écrit tout seul. Ouvert, il montre la chronique du règne de son
 * lecteur, des origines à aujourd'hui, comme un livre écrit.
 */
public class ChroniclesItem extends Item {

    public ChroniclesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        Optional<Realm> realm = RealmService.realmOf(sp.server, sp.getUUID());
        if (realm.isEmpty()) {
            sp.displayClientMessage(Component.translatable("crusadecraft.chronicles.no_realm"), true);
            return InteractionResultHolder.fail(stack);
        }
        List<WorldPackets.NewsLine> lines = new ArrayList<>();
        List<WorldNews> chronicle = realm.get().intrigue().chronicle();
        // La chronique est gardée la plus récente en tête ; le livre se lit dans l'ordre des jours.
        for (int i = chronicle.size() - 1; i >= 0; i--) lines.add(new WorldPackets.NewsLine(chronicle.get(i).day(), chronicle.get(i).text()));
        CrusadeNetwork.toPlayer(sp, new IntriguePackets.ChronicleBook(realm.get().name(), realm.get().house(), lines));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.crusadecraft.chroniques.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
