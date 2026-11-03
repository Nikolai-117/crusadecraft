package fr.crusadecraft.item;

import fr.crusadecraft.core.build.plan.PlanTool;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Le plan d'architecte (P34) : l'outil de construction créatif. Clic droit vers le ciel, le catalogue (les packs de
 * style, les fichiers du joueur, des villes entières) ; clic droit sur un bloc, même à {@link #REACH} blocs, le plan
 * choisi s'y pose d'un coup, son sol au niveau du bloc ; accroupi, il tourne d'un quart de tour. Chaque pose se défait
 * ({@code /crusade plan annuler}). Réservé au créatif et à l'éditeur de la carte.
 *
 * <p>Tout ce qu'il faut à l'aperçu tient dans l'objet : le nom, l'emprise et la rotation du plan choisi.
 */
public class PlanItem extends Item {
    private static final String TAG = "crusadecraft_plan";
    /** L'outil vise loin : on pose un bâtiment de trente blocs à distance, pas à bout de bras. */
    public static final double REACH = 96;

    public PlanItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag peek(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        return t == null ? new CompoundTag() : t;
    }

    /** Ce que l'outil pose : un plan, ou une ville entière. */
    public record Choice(String ref, String name, int sizeX, int sizeY, int sizeZ, int groundY, int rotation, boolean town, int style,
                         int size, long seed) {}

    public static Optional<Choice> choice(ItemStack stack) {
        CompoundTag t = peek(stack);
        if (!t.contains("name")) return Optional.empty();
        return Optional.of(new Choice(t.getString("ref"), t.getString("name"), t.getInt("sx"), t.getInt("sy"), t.getInt("sz"), t.getInt("gy"),
                t.getInt("rot"), t.getBoolean("town"), t.getInt("style"), t.getInt("size"), t.getLong("seed")));
    }

    public static void setChoice(ItemStack stack, Choice c) {
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        t.putString("ref", c.ref() == null ? "" : c.ref());
        t.putString("name", c.name());
        t.putInt("sx", c.sizeX());
        t.putInt("sy", c.sizeY());
        t.putInt("sz", c.sizeZ());
        t.putInt("gy", c.groundY());
        t.putInt("rot", Math.floorMod(c.rotation(), 4));
        t.putBoolean("town", c.town());
        t.putInt("style", c.style());
        t.putInt("size", c.size());
        t.putLong("seed", c.seed());
    }

    public static void rotate(ItemStack stack) {
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        t.putInt("rot", Math.floorMod(t.getInt("rot") + 1, 4));
    }

    /** Le bloc visé jusqu'à {@link #REACH} blocs (fluides ignorés), ou rien si l'on vise le ciel. */
    public static Optional<BlockHitResult> aim(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit) : Optional.empty();
    }

    /** L'outil tenu par ce joueur, en main principale d'abord. */
    public static Optional<ItemStack> held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof PlanItem) return Optional.of(s);
        }
        return Optional.empty();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide()) {
            // La pose part du client, là où est le fantôme (décalé, figé) ; sans plan choisi, le catalogue.
            if (!ctx.getPlayer().isShiftKeyDown()) {
                if (choice(ctx.getItemInHand()).isPresent()) {
                    // P39.2 : comme au bâton de construction de MineColonies, le panneau de placement plutôt que la pose.
                    DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> fr.crusadecraft.client.ClientPackets::openPlacement);
                } else {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> fr.crusadecraft.client.ClientPackets::requestPlanCatalog);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (ctx.getPlayer() instanceof ServerPlayer player && player.isShiftKeyDown()) PlanTool.rotate(player, ctx.getItemInHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) PlanTool.rotate(sp, stack);
        } else if (level.isClientSide()) {
            // Un fantôme à l'écran (visé au loin ou figé) : on pose là ; sinon, vers le ciel, le catalogue.
            boolean placed = choice(stack).isPresent() && Boolean.TRUE.equals(
                    DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> fr.crusadecraft.client.ClientPackets::openPlacement));
            if (!placed) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> fr.crusadecraft.client.ClientPackets::requestPlanCatalog);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<Choice> c = choice(stack);
        if (c.isPresent()) {
            tooltip.add(Component.literal(c.get().name()).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("crusadecraft.plan.tooltip.size", c.get().sizeX(), c.get().sizeY(), c.get().sizeZ())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("crusadecraft.plan.tooltip.none").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("crusadecraft.plan.tooltip.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("crusadecraft.plan.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("crusadecraft.plan.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
