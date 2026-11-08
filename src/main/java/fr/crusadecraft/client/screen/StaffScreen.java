package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.core.build.SiteKind;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.item.BornageStaffItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.StaffPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Le menu du bâton de bornage (P14), ouvert d'un clic droit dans le vide : pour qui tracer (un royaume, la terre
 * libre, la gomme), comment tracer (polygone, rectangle, en marchant) et quoi construire. Un nouveau royaume se crée
 * sur place, avec le formulaire de l'éditeur de la carte.
 */
public class StaffScreen extends Screen {
    private static final int ROW = 12;

    private MapView view;
    private int left, top, w, h, colA, colB, colC, colAW, colBW, colCW, listTop, listBottom;
    private int scroll;
    private boolean instant = true;
    private int lieuLabelY;

    public StaffScreen() {
        super(Component.translatable("item.crusadecraft.baton_de_bornage"));
        this.view = ClientMapData.view();
    }

    public void setMap(MapView view) {
        this.view = view;
        if (minecraft != null) rebuildWidgets();
    }

    private ItemStack staff() {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        return BornageStaffItem.held(minecraft.player).orElse(ItemStack.EMPTY);
    }

    private boolean editor() {
        return view != null && view.canEdit();
    }

    /** Les royaumes qu'on peut choisir : tous dans l'éditeur, le sien seul en survie. */
    private List<MapView.Territory> owners() {
        List<MapView.Territory> out = new ArrayList<>();
        if (view == null) return out;
        for (MapView.Territory t : view.territories()) if (editor() || t.mine()) out.add(t);
        return out;
    }

    private static void send(StaffPackets.Action action) {
        CrusadeNetwork.toServer(action);
    }

    @Override
    protected void init() {
        if (view == null || !view.editing()) CrusadeNetwork.toServer(new WorldPackets.RequestMap(false, true));
        w = Math.min(width - 16, 460);
        h = Math.min(height - 16, 236);
        left = (width - w) / 2;
        top = (height - h) / 2;
        colAW = 150;
        colA = left + 8;
        colB = colA + colAW + 8;
        colBW = 112;
        colC = colB + colBW + 8;
        colCW = left + w - 8 - colC;
        listTop = top + 44;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 58, top + 6, 50, 16).build(KitButton::new));

        // Colonne A : pour qui.
        int ay = top + h - 26;
        if (editor()) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.bornage.new_realm"), b -> {
                double x = minecraft.player.getX(), z = minecraft.player.getZ();
                List<Integer> colors = new ArrayList<>();
                for (MapView.Territory t : view.territories()) colors.add(t.color());
                minecraft.setScreen(MapEditForm.newRealm(this, (int) Math.round(x), (int) Math.round(z), colors));
            }).bounds(colA, ay, colAW, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.new_realm.tip"))).build(KitButton::new));
            ay -= 17;
        }
        int half = (colAW - 2) / 2;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.mapedit.brush.neutral"),
                b -> send(new StaffPackets.Action(StaffPackets.Action.Op.SET_SPECIAL, null, BornageStaffItem.NEUTRAL, 0, false)))
                .bounds(colA, ay, editor() ? half : colAW, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.neutral.tip"))).build(KitButton::new));
        if (editor()) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.bornage.erase"),
                    b -> send(new StaffPackets.Action(StaffPackets.Action.Op.SET_SPECIAL, null, BornageStaffItem.ERASE, 0, false)))
                    .bounds(colA + half + 2, ay, half, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.erase.tip"))).build(KitButton::new));
        }
        listBottom = ay - 4;

        // Colonne B : tracer.
        int by = top + 44;
        for (BornageStaffItem.Mode mode : new BornageStaffItem.Mode[]{BornageStaffItem.Mode.POLYGONE, BornageStaffItem.Mode.RECTANGLE,
                BornageStaffItem.Mode.MARCHE}) {
            Component label = Component.translatable(mode.key());
            addRenderableWidget(Button.builder(label, b -> send(new StaffPackets.Action(StaffPackets.Action.Op.SET_MODE, null, "", mode.ordinal(), false)))
                    .bounds(colB, by, colBW, 15).tooltip(Tooltip.create(Component.translatable(mode.key() + ".tip"))).build(KitButton::new));
            by += 17;
        }
        by += 6;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.bornage.validate"),
                b -> send(StaffPackets.Action.of(StaffPackets.Action.Op.VALIDATE))).bounds(colB, by, colBW, 15).build(KitButton::new));
        by += 17;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.bornage.clear"),
                b -> send(StaffPackets.Action.of(StaffPackets.Action.Op.CLEAR))).bounds(colB, by, colBW, 15).build(KitButton::new));
        by += 17;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.bornage.undo"),
                b -> send(StaffPackets.Action.of(StaffPackets.Action.Op.UNDO))).bounds(colB, by, colBW, 15)
                .tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.undo.tip"))).build(KitButton::new));

        // Colonne C : construire.
        int cy = top + 44;
        for (SiteKind kind : SiteKind.values()) {
            if (!editor() && !kind.survival()) continue;
            Component tip = Component.translatable("crusadecraft.bornage.build.tip", Component.translatable(kind.key() + ".tip"), kind.radius(),
                    kind.garrison(), kind.cost());
            addRenderableWidget(Button.builder(Component.translatable(kind.key()),
                    b -> send(new StaffPackets.Action(StaffPackets.Action.Op.SET_BUILD, null, "", kind.ordinal(), instant)))
                    .bounds(colC, cy, colCW, 15).tooltip(Tooltip.create(tip)).build(KitButton::new));
            cy += 16;
        }
        if (editor()) {
            addRenderableWidget(Button.builder(Component.translatable(instant ? "crusadecraft.bornage.instant" : "crusadecraft.bornage.progressive"), b -> {
                instant = !instant;
                rebuildWidgets();
            }).bounds(colC, cy + 4, colCW, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.instant.tip"))).build(KitButton::new));
            cy += 20;
        }
        // Les lieux qu'on pose d'un clic au sol (P15) : on les nomme ensuite.
        lieuLabelY = cy + 4;
        fr.crusadecraft.core.place.LieuKind[] lieux = {fr.crusadecraft.core.place.LieuKind.LIEU_SAINT, fr.crusadecraft.core.place.LieuKind.RUINE,
                fr.crusadecraft.core.place.LieuKind.REGION};
        int third = (colCW - 4) / 3;
        for (int i = 0; i < lieux.length; i++) {
            fr.crusadecraft.core.place.LieuKind kind = lieux[i];
            addRenderableWidget(Button.builder(Component.translatable(kind.key()),
                    b -> send(new StaffPackets.Action(StaffPackets.Action.Op.SET_LIEU, null, "", kind.ordinal(), false)))
                    .bounds(colC + i * (third + 2), cy + 14, third, 15)
                    .tooltip(Tooltip.create(Component.translatable("crusadecraft.bornage.lieu.tip", Component.translatable(kind.key())))).build(KitButton::new));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 9, 1.2f, Ui.TEXT_TITLE, false);
        ItemStack staff = staff();
        Component state = staff.isEmpty() ? Component.translatable("crusadecraft.bornage.not_held")
                : Component.translatable("crusadecraft.bornage.state", BornageStaffItem.ownerName(staff).isEmpty()
                        ? Component.translatable("crusadecraft.bornage.nobody") : Component.literal(BornageStaffItem.ownerName(staff)),
                Component.translatable(BornageStaffItem.mode(staff).key()));
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(state.getString(), w - 80)), left + 10, top + 24, Ui.TEXT_MUTED);
        Ui.text(g, font, Component.translatable("crusadecraft.bornage.col.owner"), colA, top + 34, Ui.GOLD);
        Ui.text(g, font, Component.translatable("crusadecraft.bornage.col.draw"), colB, top + 34, Ui.GOLD);
        Ui.text(g, font, Component.translatable("crusadecraft.bornage.col.build"), colC, top + 34, Ui.GOLD);
        Ui.text(g, font, Component.translatable("crusadecraft.bornage.col.lieu"), colC, lieuLabelY, Ui.GOLD);

        // La liste des royaumes.
        Ui.inset(g, colA, listTop, colAW, listBottom - listTop);
        List<MapView.Territory> owners = owners();
        if (owners.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable(view == null ? "crusadecraft.bornage.loading" : "crusadecraft.bornage.no_realm"),
                    colA + 4, listTop + 4, colAW - 8, Ui.TEXT_MUTED);
        }
        int visible = (listBottom - listTop - 4) / ROW;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, owners.size() - visible)));
        java.util.UUID chosen = staff.isEmpty() ? null : BornageStaffItem.owner(staff).orElse(null);
        for (int i = scroll; i < owners.size() && i - scroll < visible; i++) {
            MapView.Territory t = owners.get(i);
            int ry = listTop + 2 + (i - scroll) * ROW;
            boolean on = t.id().equals(chosen);
            if (on || Ui.inside(mouseX, mouseY, colA + 2, ry, colAW - 4, ROW)) g.fill(colA + 2, ry, colA + colAW - 2, ry + ROW, on ? 0xFF2E3740 : Ui.PANEL_HIGHLIGHT);
            g.fill(colA + 4, ry + 2, colA + 10, ry + ROW - 2, 0xFF000000 | t.color());
            Component name = t.ai() ? AiNames.realm(t.rank(), t.label()) : Component.literal(t.label());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(name.getString(), colAW - 18)), colA + 13, ry + 2, on ? Ui.TEXT_TITLE : Ui.TEXT);
        }
        Ui.wrapped(g, font, Component.translatable("crusadecraft.bornage.help." + BornageStaffItem.mode(staff).name().toLowerCase(java.util.Locale.ROOT)),
                colB, top + 44 + 3 * 17 + 6 + 3 * 17 + 6, colBW, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (Ui.inside(mouseX, mouseY, colA, listTop, colAW, listBottom - listTop)) {
            int index = scroll + (int) ((mouseY - listTop - 2) / ROW);
            List<MapView.Territory> owners = owners();
            if (index >= 0 && index < owners.size()) {
                send(new StaffPackets.Action(StaffPackets.Action.Op.SET_OWNER, owners.get(index).id(), "", 0, false));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside(mouseX, mouseY, colA, listTop, colAW, listBottom - listTop)) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
