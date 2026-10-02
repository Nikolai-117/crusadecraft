package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.FoundRealmPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Écran de fondation, ouvert par le Trône royal une fois la position validée par le serveur.
 *
 * <p>Ce n'est qu'un formulaire : le serveur revérifie tout à la réception.
 */
public class FoundingScreen extends Screen {
    private static final int W = 300;
    private static final int H = 224;
    private static final int SWATCH = 14;

    private final BlockPos thronePos;
    private final ColonyInfo colony;
    private final String playerName;

    private EditBox nameBox;
    private EditBox mottoBox;
    private EditBox houseBox;
    private Button titleButton;
    private int colorIndex = 0;
    private boolean feminine = false;
    private int left;
    private int top;

    public FoundingScreen(BlockPos thronePos, ColonyInfo colony, String playerName) {
        super(Component.translatable("crusadecraft.founding.screen.title"));
        this.thronePos = thronePos;
        this.colony = colony;
        this.playerName = playerName;
        this.colorIndex = Heraldry.nearest(colony.color()).ordinal();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int fieldX = left + 110;
        int fieldW = W - 122;

        String keepName = nameBox == null ? colony.name() : nameBox.getValue();
        String keepMotto = mottoBox == null ? "" : mottoBox.getValue();
        String keepHouse = houseBox == null ? "" : houseBox.getValue();

        nameBox = addRenderableWidget(new EditBox(font, fieldX, top + 51, fieldW, 16,
                Component.translatable("crusadecraft.founding.field.name")));
        nameBox.setMaxLength(FoundRealmPacket.nameMax());
        nameBox.setValue(keepName);

        mottoBox = addRenderableWidget(new EditBox(font, fieldX, top + 79, fieldW, 16,
                Component.translatable("crusadecraft.founding.field.motto")));
        mottoBox.setMaxLength(FoundRealmPacket.mottoMax());
        mottoBox.setValue(keepMotto);
        mottoBox.setHint(Component.translatable("crusadecraft.founding.hint.optional"));

        houseBox = addRenderableWidget(new EditBox(font, fieldX, top + 107, fieldW, 16,
                Component.translatable("crusadecraft.founding.field.house")));
        houseBox.setMaxLength(FoundRealmPacket.houseMax());
        houseBox.setValue(keepHouse);
        houseBox.setHint(Component.literal(playerName));

        titleButton = addRenderableWidget(Button.builder(titleLabel(), b -> {
            feminine = !feminine;
            b.setMessage(titleLabel());
        }).bounds(fieldX, top + 127, fieldW, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.founding.confirm"), b -> confirm())
                .bounds(left + W / 2 - 104, top + H - 27, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + W / 2 + 4, top + H - 27, 100, 20).build());
        setInitialFocus(nameBox);
    }

    private Component titleLabel() {
        return Component.translatable("crusadecraft.founding.title_form",
                Component.translatable(TitleRank.BARONNIE.holderKey(feminine)));
    }

    private void confirm() {
        CrusadeNetwork.toServer(new FoundRealmPacket(thronePos, nameBox.getValue(), mottoBox.getValue(),
                houseBox.getValue(), colorIndex, feminine));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.scaled(g, font, title, left + W / 2, top + 8, 1.4f, Ui.TEXT_TITLE, true);
        Ui.centered(g, font, Component.translatable("crusadecraft.founding.capital_line", colony.name(),
                colony.townHallLevel(), colony.citizens()), left + W / 2, top + 24, Ui.TEXT_MUTED);
        Ui.separator(g, left + 20, top + 36, W - 40, Ui.GOLD_DARK);

        // Blason d'aperçu
        Heraldry tincture = Heraldry.byOrdinal(colorIndex);
        Ui.shield(g, font, left + 28, top + 46, 54, 66, tincture.rgb(), Ui.initial(nameBox.getValue()));
        Ui.centered(g, font, Component.translatable(tincture.key()), left + 55, top + 117, Ui.TEXT_MUTED);

        int labelX = left + 110;
        Ui.text(g, font, Component.translatable("crusadecraft.founding.field.name"), labelX - 2, top + 42, Ui.TEXT);
        Ui.text(g, font, Component.translatable("crusadecraft.founding.field.motto"), labelX - 2, top + 70, Ui.TEXT);
        Ui.text(g, font, Component.translatable("crusadecraft.founding.field.house"), labelX - 2, top + 98, Ui.TEXT);

        // Nuancier : 8 × 2 émaux
        Ui.text(g, font, Component.translatable("crusadecraft.founding.field.color"), left + 14, top + 150, Ui.TEXT);
        Heraldry[] all = Heraldry.values();
        for (int i = 0; i < all.length; i++) {
            int sx = swatchX(i), sy = swatchY(i);
            if (i == colorIndex) g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, Ui.GOLD);
            g.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, 0xFF000000);
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | all[i].rgb());
        }
        super.render(g, mouseX, mouseY, partialTick);

        for (int i = 0; i < all.length; i++) {
            if (Ui.inside(mouseX, mouseY, swatchX(i), swatchY(i), SWATCH, SWATCH)) {
                g.renderTooltip(font, Component.translatable(all[i].key()), mouseX, mouseY);
            }
        }
    }

    private int swatchX(int i) {
        return left + 14 + (i % 8) * (SWATCH + 4);
    }

    private int swatchY(int i) {
        return top + 161 + (i / 8) * (SWATCH + 4);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Heraldry[] all = Heraldry.values();
        for (int i = 0; i < all.length; i++) {
            if (Ui.inside(mouseX, mouseY, swatchX(i), swatchY(i), SWATCH, SWATCH)) {
                colorIndex = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
