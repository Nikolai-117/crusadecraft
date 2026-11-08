package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.logic.TextRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Un nouveau nom (P32) : une case, Valider, Annuler. Sert au royaume (menu royal) et aux villes (écran des villes) ;
 * le serveur revérifie le nom, et l'écran d'avant revient.
 */
public class NameForm extends Screen {
    private static final int W = 260, H = 84;

    private final Screen parent;
    private final String initial;
    private final Consumer<String> onValidate;
    private EditBox box;
    private int left, top;

    public NameForm(Screen parent, Component title, String initial, Consumer<String> onValidate) {
        super(title);
        this.parent = parent;
        this.initial = initial == null ? "" : initial;
        this.onValidate = onValidate;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String keep = box == null ? initial : box.getValue();
        box = new EditBox(font, left + 10, top + 26, W - 20, 18, title);
        box.setMaxLength(TextRules.NAME_MAX);
        box.setValue(keep);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.name.validate"), b -> validate())
                .bounds(left + W - 10 - 160, top + H - 26, 78, 16).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + W - 10 - 78, top + H - 26, 78, 16).build(KitButton::new));
    }

    private void validate() {
        String name = box.getValue().trim();
        if (name.isEmpty()) return;
        onValidate.accept(name);
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {
            validate();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.text(g, font, title, left + 10, top + 10, Ui.TEXT_TITLE);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
