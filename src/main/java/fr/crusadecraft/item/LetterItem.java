package fr.crusadecraft.item;

import fr.crusadecraft.core.diplomacy.Letter;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.UUID;

/**
 * Une lettre reçue au Colombier : on la lit d'un clic droit. Le texte est gravé dans l'objet, si bien qu'elle
 * reste lisible une fois répondue ; les choix, eux, ne valent que tant que le serveur attend la réponse.
 */
public class LetterItem extends Item {
    public static final String TAG = "crusadecraftLetter";

    public LetterItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Letter l) {
        ItemStack stack = new ItemStack(ModItems.LETTER.get());
        CompoundTag t = new CompoundTag();
        t.putUUID("id", l.id());
        t.putString("sender", Component.Serializer.toJson(l.sender()));
        t.putString("subject", Component.Serializer.toJson(l.subject()));
        t.putString("body", Component.Serializer.toJson(l.body()));
        t.putLong("day", l.createdDay());
        t.putLong("expires", l.expiresDay());
        ListTag choices = new ListTag();
        for (Letter.Choice c : l.choices()) {
            CompoundTag x = new CompoundTag();
            x.putString("action", c.action().name());
            x.putBoolean("seal", c.needsSeal());
            choices.add(x);
        }
        t.put("choices", choices);
        t.putInt("answered", -1);
        stack.getOrCreateTag().put(TAG, t);
        return stack;
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.getTag() == null ? new CompoundTag() : stack.getTag().getCompound(TAG);
    }

    public static Component json(CompoundTag t, String key) {
        try {
            Component c = Component.Serializer.fromJson(t.getString(key));
            return c == null ? Component.empty() : c;
        } catch (RuntimeException e) {
            return Component.literal(t.getString(key));
        }
    }

    /** Une fois répondue, la lettre garde la trace du choix fait. */
    public static void markAnswered(ServerPlayer player, UUID id, int choice) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(ModItems.LETTER.get())) continue;
            CompoundTag t = data(s);
            if (t.hasUUID("id") && t.getUUID("id").equals(id)) t.putInt("answered", choice);
        }
    }

    public static boolean awaitsAnswer(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.getInt("answered") < 0 && !t.getList("choices", Tag.TAG_COMPOUND).isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.contains("subject") ? json(t, "subject") : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return awaitsAnswer(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.crusadecraft.client.ClientPackets.openLetter(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = data(stack);
        if (!t.contains("sender")) return;
        tooltip.add(Component.translatable("crusadecraft.letter.from", json(t, "sender")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crusadecraft.letter.day", t.getLong("day")).withStyle(ChatFormatting.DARK_GRAY));
        if (awaitsAnswer(stack)) tooltip.add(Component.translatable("crusadecraft.letter.awaiting").withStyle(ChatFormatting.GOLD));
        else if (t.getInt("answered") >= 0) tooltip.add(Component.translatable("crusadecraft.letter.answered").withStyle(ChatFormatting.DARK_GREEN));
    }
}
