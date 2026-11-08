package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.war.WarMap;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WarMapPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Un repère de la carte de guerre (P18) : son genre et sa note, posé là où l'on a cliqué. */
public class WarPinForm extends Screen {
    private final MapScreen parent;
    private final int x, z;
    private int kind;
    private EditBox note;
    private int left, top, fw, fh;

    public WarPinForm(MapScreen parent, int x, int z, int kind) {
        super(Component.translatable("crusadecraft.warmap.pin.title"));
        this.parent = parent;
        this.x = x;
        this.z = z;
        this.kind = Math.floorMod(kind, WarMap.Mark.values().length);
    }

    public MapScreen parent() {
        return parent;
    }

    private WarMap.Mark mark() {
        return WarMap.Mark.values()[kind];
    }

    private Component kindLabel() {
        return Component.translatable("crusadecraft.warmap.pin.kind", Component.literal(MapScreen.pinGlyph(mark()) + " "),
                Component.translatable("crusadecraft.warmap.mark." + mark().name().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void init() {
        fw = 280;
        fh = 120;
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        String keep = note == null ? "" : note.getValue();
        note = addRenderableWidget(new EditBox(font, left + 12, top + 36, fw - 24, 16, Component.translatable("crusadecraft.warmap.pin.note")));
        note.setMaxLength(WarMap.MAX_NOTE);
        note.setValue(keep);
        note.setHint(Component.translatable("crusadecraft.warmap.pin.hint"));
        setInitialFocus(note);
        addRenderableWidget(Button.builder(kindLabel(), b -> {
            kind = (kind + 1) % WarMap.Mark.values().length;
            parent.rememberPinKind(kind);
            b.setMessage(kindLabel());
        }).bounds(left + 12, top + 58, fw - 24, 16).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.warmap.pin.place"), b -> {
            CrusadeNetwork.toServer(WarMapPackets.Edit.pin(kind, x, z, note.getValue()));
            onClose();
        }).bounds(left + fw / 2 - 94, top + fh - 26, 90, 18).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 + 4, top + fh - 26, 90, 18).build(KitButton::new));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, fw, fh);
        Ui.scaled(g, font, title, left + fw / 2, top + 8, 1.2f, Ui.TEXT_TITLE, true);
        Ui.text(g, font, Component.translatable("crusadecraft.warmap.pin.note"), left + 10, top + 26, Ui.TEXT);
        Ui.text(g, font, Component.translatable("crusadecraft.lieu.pos", x, z), left + 12, top + 80, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
