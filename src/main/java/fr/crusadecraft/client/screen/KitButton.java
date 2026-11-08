package fr.crusadecraft.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Un bouton du kit Varenhold (P61), à la place du bouton de pierre de Minecraft dans tous les écrans de CrusadeCraft. Il se construit comme lui :
 * {@code Button.builder(...).bounds(...).build(KitButton::new)}, ou {@code build(KitButton::primary)} pour l'action principale, dorée.
 */
public class KitButton extends Button {
    private final boolean primary;

    public KitButton(Builder builder) {
        this(builder, false);
    }

    protected KitButton(Builder builder, boolean primary) {
        super(builder);
        this.primary = primary;
    }

    /** L'action principale d'un écran : dorée, comme « Soumettre au vote ». */
    public static KitButton primary(Builder builder) {
        return new KitButton(builder, true);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Kit.State state;
        if (!active) state = Kit.State.DISABLED;
        else if (isHovered() && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) state = Kit.State.PRESSED;
        else if (isHoveredOrFocused()) state = Kit.State.HOVER;
        else state = Kit.State.NORMAL;
        g.setColor(1f, 1f, 1f, alpha);
        Kit.button(g, getX(), getY(), width, height, state, primary);
        g.setColor(1f, 1f, 1f, 1f);
        int color = Kit.buttonText(state, primary) & 0xFFFFFF | Mth.ceil(alpha * 255f) << 24;
        renderScrollingString(g, Minecraft.getInstance().font, 3, color);
    }
}
