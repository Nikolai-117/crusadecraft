package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.treasury.GoldValues;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.EconomyPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Le Coffre du Trésor : les comptes, et l'or qu'on dépose ou qu'on retire. */
public class TreasuryScreen extends Screen {
    private TreasuryView view;
    private final BlockPos chest;
    private int left, top, w, h;

    public TreasuryScreen(TreasuryView view, BlockPos chest) {
        super(Component.translatable("block.crusadecraft.treasury"));
        this.view = view;
        this.chest = chest;
    }

    public boolean isFor(BlockPos pos) {
        return chest.equals(pos);
    }

    public void refresh(TreasuryView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(430, width - 12);
        h = Math.min(252, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int by = top + h - 26;
        Button deposit = Button.builder(Component.translatable("crusadecraft.treasury.deposit"), b ->
                CrusadeNetwork.toServer(new EconomyPackets.TreasuryAction(chest, EconomyPackets.TreasuryAction.Kind.DEPOSIT_ALL,
                        net.minecraft.resources.ResourceLocation.tryParse("minecraft:air"), 0)))
                .bounds(left + 10, by, 104, 18).build(KitButton::new);
        deposit.setTooltip(Tooltip.create(Component.translatable("crusadecraft.treasury.deposit.tip")));
        addRenderableWidget(deposit);

        // Un bouton de retrait par sorte d'or, du moins précieux au plus précieux ; Maj : dix d'un coup.
        List<GoldValues.Denomination> denoms = view.denominations();
        int bx = left + 120;
        int bw = denoms.isEmpty() ? 0 : Math.min(84, (w - 120 - 100) / denoms.size() - 4);
        for (int i = denoms.size() - 1; i >= 0; i--) {
            GoldValues.Denomination d = denoms.get(i);
            Item item = ForgeRegistries.ITEMS.getValue(d.item());
            Component name = item == null ? Component.literal(d.item().toString()) : new ItemStack(item).getHoverName();
            // L'icône de la pièce est dessinée à gauche du bouton : le libellé lui laisse la place.
            Button b = Button.builder(Component.literal("    ").append(Component.translatable("crusadecraft.treasury.withdraw.short", d.value())), btn ->
                    CrusadeNetwork.toServer(new EconomyPackets.TreasuryAction(chest, EconomyPackets.TreasuryAction.Kind.WITHDRAW,
                            d.item(), hasShiftDown() ? 10 : 1)))
                    .bounds(bx, by, bw, 18).build(KitButton::new);
            b.setTooltip(Tooltip.create(Component.translatable("crusadecraft.treasury.withdraw.tip", name, d.value())));
            b.active = view.canWithdraw() && view.gold() >= d.value();
            addRenderableWidget(b);
            bx += bw + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + w - 90, by, 80, 18).build(KitButton::new));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, Component.translatable("crusadecraft.treasury.title", view.realmName()), left + 12, top + 9, 1.3f, Ui.TEXT_TITLE, false);
        int iy = top + 26;
        int ih = h - 26 - 32;
        Ui.inset(g, left + 8, iy, w - 16, ih);
        List<Component> tip = TreasuryPanel.render(g, font, view, left + 8, iy, w - 16, ih, mouseX, mouseY);
        // Petites pièces au-dessus des boutons de retrait, pour reconnaître la sorte d'or.
        int bx = left + 120;
        List<GoldValues.Denomination> denoms = view.denominations();
        int bw = denoms.isEmpty() ? 0 : Math.min(84, (w - 120 - 100) / denoms.size() - 4);
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = denoms.size() - 1; i >= 0; i--) {
            Item item = ForgeRegistries.ITEMS.getValue(denoms.get(i).item());
            if (item != null) g.renderItem(new ItemStack(item), bx + 2, top + h - 25);
            bx += bw + 4;
        }
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
