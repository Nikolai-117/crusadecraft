package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LieuPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** La fiche d'un lieu, à écrire : le texte est celui du joueur, le mod n'y met rien. */
public class LieuTextScreen extends Screen {
    private final LieuxScreen parent;
    private final UUID target;
    private final Component lieu;
    private final String initial;
    private MultiLineEditBox box;
    private int left, top, fw, fh;

    public LieuTextScreen(LieuxScreen parent, UUID target, Component lieu, String text) {
        super(Component.translatable("crusadecraft.lieu.write.title"));
        this.parent = parent;
        this.target = target;
        this.lieu = lieu;
        this.initial = text == null ? "" : text;
    }

    public LieuxScreen parent() {
        return parent;
    }

    @Override
    protected void init() {
        fw = Math.min(width - 16, 420);
        fh = Math.min(height - 16, 260);
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        String keep = box == null ? initial : box.getValue();
        box = addRenderableWidget(new MultiLineEditBox(font, left + 10, top + 36, fw - 20, fh - 36 - 34,
                Component.translatable("crusadecraft.lieu.write.hint"), Component.translatable("crusadecraft.lieu.write.title")));
        box.setCharacterLimit(Lieu.MAX_TEXT);
        box.setValue(keep);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.mapedit.form.save"), b -> {
            CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.TEXT, target, box.getValue(), null));
            onClose();
        }).bounds(left + fw / 2 - 94, top + fh - 26, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 + 4, top + fh - 26, 90, 18).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, fw, fh);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.2f, Ui.TEXT_TITLE, false);
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(lieu.getString(), fw - 20)), left + 10, top + 22, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
