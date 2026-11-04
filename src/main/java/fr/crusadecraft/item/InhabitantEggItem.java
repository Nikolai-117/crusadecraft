package fr.crusadecraft.item;

import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityEggs;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * L'œuf d'habitant (P42). Clic droit sur un bloc : on choisit une ville et un métier, et quelqu'un y naît. L'œuf retient
 * le choix : les clics suivants font naître d'autres habitants pareils ; accroupi, on change de choix.
 */
public class InhabitantEggItem extends Item {
    private static final String TAG = "crusadecraft_oeuf";

    /** Ce qu'un œuf fait naître. */
    public record Choice(UUID site, BuildingFunction job, String city, int color) {}

    public InhabitantEggItem(Properties properties) {
        super(properties);
    }

    public static Optional<ItemStack> held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof InhabitantEggItem) return Optional.of(s);
        }
        return Optional.empty();
    }

    public static Optional<Choice> choice(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t == null || !t.hasUUID("site")) return Optional.empty();
        BuildingFunction f = BuildingFunction.byName(t.getString("job"));
        return f == null ? Optional.empty() : Optional.of(new Choice(t.getUUID("site"), f, t.getString("city"), t.getInt("color")));
    }

    public static void setChoice(ItemStack stack, Choice c) {
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        t.putUUID("site", c.site());
        t.putString("job", c.job().name());
        t.putString("city", c.city());
        t.putInt("color", c.color());
    }

    /** La teinte de l'œuf : celle du royaume de sa ville ; le cœur, une teinte par métier. */
    public static int tint(ItemStack stack, int layer) {
        Optional<Choice> c = choice(stack);
        if (layer == 0) return c.map(Choice::color).orElse(0xC9A55C);
        return c.map(x -> x.job().color()).orElse(0x6E5528);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            CityEggs.use(player, ctx.getItemInHand(), ctx.getClickedPos().relative(ctx.getClickedFace()).below());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<Choice> c = choice(stack);
        if (c.isEmpty()) return super.getName(stack);
        return Component.translatable("item.crusadecraft.oeuf_habitant.choice", Component.translatable(c.get().job().key()), c.get().city());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(choice(stack).isPresent() ? "item.crusadecraft.oeuf_habitant.tooltip.set" : "item.crusadecraft.oeuf_habitant.tooltip.empty")
                .withStyle(ChatFormatting.GRAY));
    }
}
