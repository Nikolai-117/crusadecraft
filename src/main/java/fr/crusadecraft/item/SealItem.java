package fr.crusadecraft.item;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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
import java.util.Optional;

/**
 * Le Sceau royal : il scelle traités, hommages et titres. Il se lie au royaume de celui qui s'en sert le premier ;
 * un sceau étranger ne vaut rien.
 */
public class SealItem extends Item {
    private static final String TAG = "crusadecraftSeal";

    public SealItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Pourquoi ce joueur ne peut pas sceller ; vide s'il le peut (le sceau trouvé est alors lié au royaume). */
    public static Optional<Component> problem(ServerPlayer player, Realm realm) {
        if (CrusadeConfig.SERVER.chanceryNeedsResearch.get()
                && MineColoniesBridge.researchEffect(realm.capital(), Law.Research.CHANCELLERIE) <= 0) {
            return Optional.of(Component.translatable("crusadecraft.seal.no_research"));
        }
        var inv = player.getInventory();
        boolean foreign = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(ModItems.SEAL.get())) continue;
            CompoundTag t = s.getTagElement(TAG);
            if (t == null || !t.hasUUID("realm")) {
                bind(s, realm);
                return Optional.empty();
            }
            if (t.getUUID("realm").equals(realm.id())) return Optional.empty();
            foreign = true;
        }
        return Optional.of(Component.translatable(foreign ? "crusadecraft.seal.foreign" : "crusadecraft.seal.missing"));
    }

    private static void bind(ItemStack stack, Realm realm) {
        CompoundTag t = stack.getOrCreateTagElement(TAG);
        t.putUUID("realm", realm.id());
        t.putString("name", realm.name());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Optional<Realm> realm = RealmService.realmOf(sp.server, sp.getUUID());
            CompoundTag t = stack.getTagElement(TAG);
            if (t != null && t.hasUUID("realm")) {
                sp.displayClientMessage(Component.translatable("crusadecraft.seal.of", t.getString("name")), true);
            } else if (realm.isPresent()) {
                bind(stack, realm.get());
                sp.displayClientMessage(Component.translatable("crusadecraft.seal.bound", realm.get().name()), true);
            } else {
                sp.displayClientMessage(Component.translatable("crusadecraft.realm.none"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getTagElement(TAG) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTagElement(TAG);
        if (t != null && t.hasUUID("realm")) tooltip.add(Component.translatable("crusadecraft.seal.of", t.getString("name")).withStyle(ChatFormatting.GOLD));
        else tooltip.add(Component.translatable("crusadecraft.seal.unbound").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("crusadecraft.seal.tip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
