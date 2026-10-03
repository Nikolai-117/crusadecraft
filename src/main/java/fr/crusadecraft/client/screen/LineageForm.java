package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.lineage.CommunityKind;
import fr.crusadecraft.core.lineage.LineageGenerator;
import fr.crusadecraft.core.lineage.LineageService;
import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LineagePackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** Le formulaire du registre des lignées : une nouvelle maison, une nouvelle communauté, ou un nouveau nom. */
public class LineageForm extends Screen {
    private static final int SWATCH = 14;
    private static final int[] SIZES = {2, 4, 6, 8, 12, 16, 20};

    private enum Mode { NEW_HOUSE, NEW_COMMUNITY, RENAME }

    private final LineageScreen parent;
    private final Mode mode;
    private final List<LineageView.RealmChoice> realms;
    private final UUID target;
    private final String initialName;

    private EditBox nameBox;
    /** −1 : rattachée à aucun royaume. */
    private int realmIndex = -1;
    private int colorIndex = Heraldry.SINOPLE.ordinal();
    private int generations = 3;
    private CommunityKind kind = CommunityKind.VILLAGE;
    private int sizeIndex = 2;
    private int left, top, fw, fh;

    private LineageForm(LineageScreen parent, Mode mode, List<LineageView.RealmChoice> realms, UUID target, String initialName) {
        super(Component.translatable("crusadecraft.lineage.form." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        this.parent = parent;
        this.mode = mode;
        this.realms = realms;
        this.target = target;
        this.initialName = initialName;
    }

    public static LineageForm newHouse(LineageScreen parent, List<LineageView.RealmChoice> realms) {
        return new LineageForm(parent, Mode.NEW_HOUSE, realms, null, "");
    }

    public static LineageForm newCommunity(LineageScreen parent, List<LineageView.RealmChoice> realms) {
        return new LineageForm(parent, Mode.NEW_COMMUNITY, realms, null, "");
    }

    public static LineageForm rename(LineageScreen parent, UUID target, String name) {
        return new LineageForm(parent, Mode.RENAME, List.of(), target, name);
    }

    public LineageScreen parent() {
        return parent;
    }

    @Override
    protected void init() {
        fw = 280;
        fh = mode == Mode.NEW_HOUSE ? 180 : mode == Mode.NEW_COMMUNITY ? 150 : 96;
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        String keep = nameBox == null ? initialName : nameBox.getValue();
        nameBox = addRenderableWidget(new EditBox(font, left + 12, top + 36, fw - 24, 16, Component.translatable("crusadecraft.mapedit.form.name")));
        nameBox.setMaxLength(LineageService.NAME_MAX);
        nameBox.setValue(keep);
        nameBox.setHint(Component.translatable(mode == Mode.NEW_HOUSE ? "crusadecraft.lineage.form.house_hint"
                : mode == Mode.NEW_COMMUNITY ? "crusadecraft.lineage.form.community_hint" : "crusadecraft.lineage.form.rename_hint"));
        int y = top + 58;
        if (mode != Mode.RENAME) {
            addRenderableWidget(Button.builder(realmLabel(), b -> {
                realmIndex = realmIndex + 1 >= realms.size() ? -1 : realmIndex + 1;
                b.setMessage(realmLabel());
            }).bounds(left + 12, y, fw - 24, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.form.realm.tip"))).build());
            y += 20;
        }
        if (mode == Mode.NEW_HOUSE) {
            addRenderableWidget(Button.builder(generationsLabel(), b -> {
                generations = generations % LineageGenerator.MAX_GENERATIONS + 1;
                b.setMessage(generationsLabel());
            }).bounds(left + 12, y, fw - 24, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.form.generations.tip"))).build());
        }
        if (mode == Mode.NEW_COMMUNITY) {
            int half = (fw - 24 - 4) / 2;
            addRenderableWidget(Button.builder(kindLabel(), b -> {
                kind = CommunityKind.values()[(kind.ordinal() + 1) % CommunityKind.values().length];
                b.setMessage(kindLabel());
            }).bounds(left + 12, y, half, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.form.kind.tip"))).build());
            addRenderableWidget(Button.builder(sizeLabel(), b -> {
                sizeIndex = (sizeIndex + 1) % SIZES.length;
                b.setMessage(sizeLabel());
            }).bounds(left + 16 + half, y, half, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.form.size.tip"))).build());
        }
        Component confirm = Component.translatable(mode == Mode.RENAME ? "crusadecraft.mapedit.form.save" : "crusadecraft.lineage.form.generate");
        addRenderableWidget(Button.builder(confirm, b -> confirm()).bounds(left + fw / 2 - 94, top + fh - 26, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 + 4, top + fh - 26, 90, 18).build());
        setInitialFocus(nameBox);
    }

    private UUID realm() {
        return realmIndex < 0 || realmIndex >= realms.size() ? null : realms.get(realmIndex).id();
    }

    private Component realmLabel() {
        Component name = realmIndex < 0 || realmIndex >= realms.size() ? Component.translatable("crusadecraft.lineage.no_realm")
                : realms.get(realmIndex).label();
        String full = Component.translatable("crusadecraft.lineage.form.realm", name).getString();
        return Component.literal(font == null ? full : font.plainSubstrByWidth(full, fw - 32));
    }

    private Component generationsLabel() {
        return Component.translatable("crusadecraft.lineage.form.generations", generations);
    }

    private Component kindLabel() {
        return Component.translatable("crusadecraft.lineage.form.kind", Component.translatable(kind.key()));
    }

    private Component sizeLabel() {
        return Component.translatable(kind.families() ? "crusadecraft.lineage.form.size.families" : "crusadecraft.lineage.form.size.members",
                SIZES[sizeIndex]);
    }

    private void confirm() {
        String name = nameBox.getValue();
        UUID shown = parent.shownForForm();
        switch (mode) {
            case NEW_HOUSE -> CrusadeNetwork.toServer(new LineagePackets.Action(LineagePackets.Action.Op.NEW_HOUSE, null, name, colorIndex,
                    generations, realm(), shown));
            case NEW_COMMUNITY -> CrusadeNetwork.toServer(new LineagePackets.Action(LineagePackets.Action.Op.NEW_COMMUNITY, null, name,
                    kind.ordinal(), SIZES[sizeIndex], realm(), shown));
            case RENAME -> CrusadeNetwork.toServer(new LineagePackets.Action(LineagePackets.Action.Op.RENAME, target, name, 0, 0, null, shown));
        }
        onClose();
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
        Ui.text(g, font, Component.translatable("crusadecraft.mapedit.form.name"), left + 10, top + 26, Ui.TEXT);
        if (mode == Mode.NEW_HOUSE) {
            int sy = swatchY(0) - 11;
            Ui.text(g, font, Component.translatable("crusadecraft.founding.field.color"), left + 10, sy, Ui.TEXT);
            Heraldry[] all = Heraldry.values();
            for (int i = 0; i < all.length; i++) {
                int sx = swatchX(i), y = swatchY(i);
                if (i == colorIndex) g.fill(sx - 2, y - 2, sx + SWATCH + 2, y + SWATCH + 2, Ui.GOLD);
                g.fill(sx - 1, y - 1, sx + SWATCH + 1, y + SWATCH + 1, 0xFF000000);
                g.fill(sx, y, sx + SWATCH, y + SWATCH, 0xFF000000 | all[i].rgb());
            }
            Ui.shield(g, font, left + fw - 50, swatchY(0) - 6, 32, 40, Heraldry.byOrdinal(colorIndex).rgb(), Ui.initial(nameBox.getValue()));
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int swatchX(int i) {
        return left + 12 + (i % 8) * (SWATCH + 4);
    }

    private int swatchY(int i) {
        return top + 112 + (i / 8) * (SWATCH + 4);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.NEW_HOUSE) {
            Heraldry[] all = Heraldry.values();
            for (int i = 0; i < all.length; i++) {
                if (Ui.inside(mouseX, mouseY, swatchX(i), swatchY(i), SWATCH, SWATCH)) {
                    colorIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
