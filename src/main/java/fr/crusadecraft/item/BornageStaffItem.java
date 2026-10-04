package fr.crusadecraft.item;

import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.world.TerritoryService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Le bâton de bornage (P14) : il trace le territoire directement dans le monde. Clic droit sur des blocs pour poser
 * les coins d'un polygone ou d'un rectangle, ou peindre les cases en marchant ; accroupi, il rend une case en terre
 * libre ; clic droit dans le vide, son menu (pour qui tracer, le mode, les constructions).
 *
 * <p>Tout son état tient dans l'objet (mode, royaume, coins posés) : le client le lit pour l'aperçu en particules,
 * le serveur seul le modifie. Les frontières tracées sont celles de l'éditeur de la carte : une seule vérité.
 */
public class BornageStaffItem extends Item {
    public enum Mode {
        POLYGONE, RECTANGLE, MARCHE, CONSTRUIRE, LIEU;

        public String key() {
            return "crusadecraft.bornage.mode." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Pour qui l'on trace, quand ce n'est pas un royaume : la terre libre, ou la gomme (retour aux cercles). */
    public static final String NEUTRAL = "neutral", ERASE = "erase";

    private static final String TAG = "crusadecraft_bornage";

    public BornageStaffItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrCreateTagElement(TAG);
    }

    private static CompoundTag peek(ItemStack stack) {
        CompoundTag t = stack.getTagElement(TAG);
        return t == null ? new CompoundTag() : t;
    }

    public static Mode mode(ItemStack stack) {
        try {
            return Mode.valueOf(peek(stack).getString("mode"));
        } catch (IllegalArgumentException e) {
            return Mode.POLYGONE;
        }
    }

    public static void setMode(ItemStack stack, Mode mode) {
        tag(stack).putString("mode", mode.name());
        setPoints(stack, List.of());
    }

    public static Optional<UUID> owner(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t.hasUUID("owner") ? Optional.of(t.getUUID("owner")) : Optional.empty();
    }

    /** {@link #NEUTRAL}, {@link #ERASE}, ou vide quand on trace pour un royaume. */
    public static String special(ItemStack stack) {
        return peek(stack).getString("special");
    }

    public static int color(ItemStack stack) {
        CompoundTag t = peek(stack);
        return t.contains("color") ? t.getInt("color") : 0xE8D9B0;
    }

    public static String ownerName(ItemStack stack) {
        return peek(stack).getString("ownerName");
    }

    /** Trace pour ce royaume (couleur et nom gardés pour l'aperçu et l'info-bulle). */
    public static void setOwner(ItemStack stack, UUID owner, int color, String name) {
        CompoundTag t = tag(stack);
        t.putUUID("owner", owner);
        t.putString("special", "");
        t.putInt("color", color);
        t.putString("ownerName", name);
    }

    public static void setSpecial(ItemStack stack, String special, String name) {
        CompoundTag t = tag(stack);
        t.remove("owner");
        t.putString("special", special);
        t.putInt("color", NEUTRAL.equals(special) ? 0xE8D9B0 : 0x9A9A9A);
        t.putString("ownerName", name);
    }

    public static List<BlockPos> points(ItemStack stack) {
        List<BlockPos> out = new ArrayList<>();
        for (long l : peek(stack).getLongArray("points")) out.add(BlockPos.of(l));
        return out;
    }

    public static void setPoints(ItemStack stack, List<BlockPos> points) {
        long[] raw = new long[points.size()];
        for (int i = 0; i < raw.length; i++) raw[i] = points.get(i).asLong();
        tag(stack).putLongArray("points", raw);
    }

    public static SiteKind build(ItemStack stack) {
        return SiteKind.byName(peek(stack).getString("build"), SiteKind.TOUR);
    }

    public static boolean instant(ItemStack stack) {
        return peek(stack).getBoolean("instant");
    }

    public static void setBuild(ItemStack stack, SiteKind kind, boolean instant) {
        CompoundTag t = tag(stack);
        t.putString("build", kind.name());
        t.putBoolean("instant", instant);
        t.putString("mode", Mode.CONSTRUIRE.name());
        t.putLongArray("points", new long[0]);
    }

    /** Le genre de lieu que pose le mode « Lieu » (P15). */
    public static fr.crusadecraft.core.place.LieuKind lieu(ItemStack stack) {
        return fr.crusadecraft.core.place.LieuKind.byName(peek(stack).getString("lieu"));
    }

    public static void setLieu(ItemStack stack, fr.crusadecraft.core.place.LieuKind kind) {
        CompoundTag t = tag(stack);
        t.putString("lieu", kind.name());
        t.putString("mode", Mode.LIEU.name());
        t.putLongArray("points", new long[0]);
    }

    /** Le bâton tenu par ce joueur, en main principale d'abord. */
    public static Optional<ItemStack> held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof BornageStaffItem) return Optional.of(s);
        }
        return Optional.empty();
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            TerritoryService.onStaffUse(player, ctx.getItemInHand(), ctx.getClickedPos(), ctx.getClickedFace(), player.isShiftKeyDown());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> fr.crusadecraft.client.ClientPackets::openStaffMenu);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Mode mode = mode(stack);
        String who = ownerName(stack);
        tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.mode", Component.translatable(mode.key())).withStyle(ChatFormatting.GOLD));
        if (!who.isEmpty()) tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.owner", who).withStyle(ChatFormatting.GRAY));
        if (mode == Mode.CONSTRUIRE) tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.build", Component.translatable(build(stack).key()))
                .withStyle(ChatFormatting.GRAY));
        if (mode == Mode.LIEU) tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.build", Component.translatable(lieu(stack).key()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("crusadecraft.bornage.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
