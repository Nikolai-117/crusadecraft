package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.map.PlanControl;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Le panneau de placement du plan d'architecte (P39.2), comme le bâton de construction de MineColonies : le fantôme
 * est figé là où l'on a cliqué, et des boutons le montent, le descendent, le poussent (selon le regard), le tournent,
 * puis le posent ou l'abandonnent. Le monde reste visible derrière ; les touches du clavier marchent aussi.
 */
public class PlacementScreen extends Screen {
    private static final int W = 214, H = 124, B = 66, BH = 18, GAP = 4;
    private static final int[] STEPS = {1, 5, 10};
    private int step;
    private Button stepButton;
    private int left, top;

    public PlacementScreen() {
        super(Component.translatable("crusadecraft.plan.panel.title"));
    }

    @Override
    protected void init() {
        left = width - W - 10;
        top = height - H - 34;
        int x0 = left + 8, y0 = top + 24;
        int c1 = x0, c2 = x0 + B + GAP, c3 = x0 + 2 * (B + GAP);
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.rotate_left"), b -> rotate(3)).bounds(c1, y0, B, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.forward"), b -> move(0, 0, 1)).bounds(c2, y0, B, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.rotate_right"), b -> rotate(1)).bounds(c3, y0, B, BH).build(KitButton::new));
        int y1 = y0 + BH + GAP;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.left"), b -> move(-1, 0, 0)).bounds(c1, y1, B, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.back"), b -> move(0, 0, -1)).bounds(c2, y1, B, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.right"), b -> move(1, 0, 0)).bounds(c3, y1, B, BH).build(KitButton::new));
        int y2 = y1 + BH + GAP;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.up"), b -> move(0, 1, 0)).bounds(c1, y2, B, BH).build(KitButton::new));
        stepButton = addRenderableWidget(Button.builder(stepLabel(), b -> {
            step = (step + 1) % STEPS.length;
            b.setMessage(stepLabel());
        }).bounds(c2, y2, B, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.down"), b -> move(0, -1, 0)).bounds(c3, y2, B, BH).build(KitButton::new));
        int y3 = y2 + BH + GAP;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.place"), b -> place()).bounds(c1, y3, B * 2 + GAP, BH).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.plan.panel.cancel"), b -> cancel()).bounds(c3, y3, B, BH).build(KitButton::new));
    }

    private Component stepLabel() {
        return Component.translatable("crusadecraft.plan.panel.step", STEPS[step]);
    }

    /** Pousser de {@code right} vers la droite, {@code up} vers le haut, {@code forward} devant soi (selon le regard). */
    private void move(int right, int up, int forward) {
        if (minecraft == null || minecraft.player == null) return;
        PlanControl.move(minecraft.player.getDirection(), right * STEPS[step], up * STEPS[step], forward * STEPS[step]);
    }

    private void rotate(int quarters) {
        for (int i = 0; i < quarters; i++) PlanControl.rotate();
    }

    private void place() {
        if (minecraft == null) return;
        PlanControl.place(minecraft);
        PlanControl.reset();
        onClose();
    }

    private void cancel() {
        PlanControl.reset();
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> place();
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> move(0, 0, 1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> move(0, 0, -1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_Q -> move(-1, 0, 0);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> move(1, 0, 0);
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_E -> move(0, 1, 0);
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_C -> move(0, -1, 0);
            case GLFW.GLFW_KEY_R -> rotate(1);
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // Pas de voile : le fantôme se voit derrière le panneau.
        Ui.panel(g, left, top, W, H);
        Ui.fitted(g, font, title, left + 8, top + 8, W - 16, Ui.TEXT_TITLE);
        Optional<BlockPos> at = minecraft == null ? Optional.empty() : PlanControl.ground(minecraft);
        int[] off = PlanControl.offset();
        int rot = minecraft == null || minecraft.player == null ? 0
                : PlanItem.held(minecraft.player).flatMap(PlanItem::choice).map(PlanItem.Choice::rotation).orElse(0);
        Component where = at.map(p -> Component.translatable("crusadecraft.plan.panel.where", p.getX(), p.getY(), p.getZ(), off[1], rot * 90))
                .orElse(Component.translatable("crusadecraft.plan.panel.nowhere"));
        Ui.fitted(g, font, where, left + 8, top + H - 12, W - 16, Ui.TEXT_MUTED);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
