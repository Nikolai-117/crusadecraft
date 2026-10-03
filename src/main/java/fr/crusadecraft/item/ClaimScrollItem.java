package fr.crusadecraft.item;

import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiProvince;
import fr.crusadecraft.core.world.AiRealm;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Parchemin de revendication : la preuve écrite des droits d'un royaume sur une province. La revendication
 * elle-même est enregistrée par le royaume ; le parchemin la montre, et servira de motif de guerre.
 */
public class ClaimScrollItem extends Item {
    private static final String TAG = "crusadecraftClaim";

    public ClaimScrollItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(MinecraftServer server, Realm holder, int provinceId) {
        ItemStack stack = new ItemStack(ModItems.CLAIM_SCROLL.get());
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        t.putInt("province", provinceId);
        t.putString("holder", holder.name());
        for (AiRealm r : CrusadeSavedData.get(server).world().realms()) {
            for (AiProvince p : r.provinces()) {
                if (p.id() != provinceId) continue;
                t.putString("name", p.name());
                t.putString("realm", Component.Serializer.toJson(AiNames.realm(r.rank(), r.place())));
            }
        }
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t == null) return;
        Component realm = LetterItem.json(t, "realm");
        tooltip.add(Component.translatable("crusadecraft.claim.on", t.getString("name"), realm).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("crusadecraft.claim.holder", t.getString("holder")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crusadecraft.claim.tip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
