package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.place.LieuKind;
import fr.crusadecraft.core.place.LieuView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LieuPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le formulaire des lieux : poser un lieu (depuis l'écran ou le bâton), le renommer, le ranger sous un autre, ou
 * dire quel royaume le tient. {@code parent} est {@code null} quand le formulaire vient du bâton.
 */
public class LieuForm extends Screen {
    private static final int[] RADII = {32, 64, 128, 256, 512, 1024};
    private static final int ROW_H = 11;

    private enum Mode { CREATE, RENAME, PARENT, HOLDER }

    private final LieuxScreen parent;
    private final Mode mode;
    private final UUID target;
    private final BlockPos pos;
    private final String initialName;
    private final List<LieuKind> kinds = new ArrayList<>();

    private EditBox nameBox;
    private int kindIndex;
    private int radiusIndex = 2;
    /** Création : le lieu dont il relèvera (celui qui était choisi dans l'écran), ou aucun. */
    private UUID under;
    private boolean useUnder;
    private int holderIndex = -1;
    private int scroll;
    private int left, top, fw, fh, listTop, listBottom;

    private LieuForm(LieuxScreen parent, Mode mode, UUID target, BlockPos pos, String initialName, Component title) {
        super(title);
        this.parent = parent;
        this.mode = mode;
        this.target = target;
        this.pos = pos;
        this.initialName = initialName == null ? "" : initialName;
    }

    /**
     * Poser un lieu. {@code fixed} : le genre choisi au bâton ; sinon on le choisit (tous les genres posables dans
     * l'éditeur, les seuls lieu saint, ruine et région en survie). {@code under} : le lieu choisi dans l'écran.
     */
    public static LieuForm create(LieuxScreen parent, LieuKind fixed, BlockPos pos, UUID under) {
        LieuForm f = new LieuForm(parent, Mode.CREATE, null, pos, "", Component.translatable("crusadecraft.lieu.form.create"));
        boolean editor = parent != null ? parent.view().canEdit() : ClientMapData.view() != null && ClientMapData.view().canEdit();
        if (fixed != null) f.kinds.add(fixed);
        else for (LieuKind k : LieuKind.values()) if (editor ? k.manual() : k.placed()) f.kinds.add(k);
        if (f.kinds.isEmpty()) f.kinds.add(LieuKind.LIEU_SAINT);
        if (fixed == null && f.kinds.contains(LieuKind.LIEU_SAINT)) f.kindIndex = f.kinds.indexOf(LieuKind.LIEU_SAINT);
        f.under = under;
        f.useUnder = under != null;
        return f;
    }

    public static LieuForm rename(LieuxScreen parent, UUID target, String name) {
        return new LieuForm(parent, Mode.RENAME, target, null, name, Component.translatable("crusadecraft.lieu.form.rename"));
    }

    public static LieuForm parentPicker(LieuxScreen parent, UUID target) {
        return new LieuForm(parent, Mode.PARENT, target, null, "", Component.translatable("crusadecraft.lieu.form.parent"));
    }

    public static LieuForm holder(LieuxScreen parent, UUID target) {
        return new LieuForm(parent, Mode.HOLDER, target, null, "", Component.translatable("crusadecraft.lieu.form.holder"));
    }

    public LieuxScreen parent() {
        return parent;
    }

    private LieuKind kind() {
        return kinds.get(Math.floorMod(kindIndex, kinds.size()));
    }

    @Override
    protected void init() {
        fw = 290;
        fh = switch (mode) {
            case CREATE -> 150;
            case RENAME, HOLDER -> 96;
            case PARENT -> Math.min(height - 20, 240);
        };
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        int y = top + 36;
        if (mode == Mode.CREATE || mode == Mode.RENAME) {
            String keep = nameBox == null ? initialName : nameBox.getValue();
            nameBox = addRenderableWidget(new EditBox(font, left + 12, y, fw - 24, 16, Component.translatable("crusadecraft.mapedit.form.name")));
            nameBox.setMaxLength(Lieu.MAX_NAME);
            nameBox.setValue(keep);
            nameBox.setHint(Component.translatable(mode == Mode.CREATE && kind().title() ? "crusadecraft.lieu.form.title_hint" : "crusadecraft.lieu.form.name_hint"));
            setInitialFocus(nameBox);
            y += 22;
        }
        if (mode == Mode.CREATE) {
            int half = (fw - 24 - 4) / 2;
            Button kindButton = Button.builder(kindLabel(), b -> {
                kindIndex = (kindIndex + 1) % kinds.size();
                rebuildWidgets();
            }).bounds(left + 12, y, kind() == LieuKind.REGION ? half : fw - 24, 16).build();
            kindButton.active = kinds.size() > 1;
            kindButton.setTooltip(Tooltip.create(Component.translatable(kind().title() ? "crusadecraft.lieu.form.kind.title.tip"
                    : "crusadecraft.lieu.form.kind.place.tip")));
            addRenderableWidget(kindButton);
            if (kind() == LieuKind.REGION) {
                addRenderableWidget(Button.builder(Component.translatable("crusadecraft.lieu.form.radius", RADII[radiusIndex]), b -> {
                    radiusIndex = (radiusIndex + 1) % RADII.length;
                    b.setMessage(Component.translatable("crusadecraft.lieu.form.radius", RADII[radiusIndex]));
                }).bounds(left + 16 + half, y, half, 16).build());
            }
            y += 20;
            if (under != null) {
                addRenderableWidget(Button.builder(underLabel(), b -> {
                    useUnder = !useUnder;
                    b.setMessage(underLabel());
                }).bounds(left + 12, y, fw - 24, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.form.under.tip"))).build());
            }
        }
        if (mode == Mode.HOLDER) {
            addRenderableWidget(Button.builder(holderLabel(), b -> {
                List<LieuView.Choice> realms = parent.view().realms();
                holderIndex = holderIndex + 1 >= realms.size() ? -1 : holderIndex + 1;
                b.setMessage(holderLabel());
            }).bounds(left + 12, y, fw - 24, 16).build());
        }
        if (mode == Mode.PARENT) {
            listTop = top + 34;
            listBottom = top + fh - 52;
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.lieu.form.parent.world"), b -> {
                CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.PARENT, target, "", null));
                onClose();
            }).bounds(left + 12, top + fh - 48, fw - 24, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.form.parent.world.tip"))).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 - 45, top + fh - 26, 90, 18).build());
            return;
        }
        addRenderableWidget(Button.builder(Component.translatable(mode == Mode.CREATE ? "crusadecraft.lieu.form.place" : "crusadecraft.mapedit.form.save"),
                b -> confirm()).bounds(left + fw / 2 - 94, top + fh - 26, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 + 4, top + fh - 26, 90, 18).build());
    }

    private Component kindLabel() {
        return Component.translatable("crusadecraft.lieu.form.kind", Component.literal(kind().icon() + " "), Component.translatable(kind().key()));
    }

    private Component underLabel() {
        Component name = Component.translatable("crusadecraft.lieu.none");
        if (useUnder && parent != null) {
            for (LieuView.Entry e : parent.view().entries()) if (e.id().equals(under)) name = e.name();
        }
        return clip(Component.translatable("crusadecraft.lieu.form.under", name));
    }

    private Component holderLabel() {
        List<LieuView.Choice> realms = parent.view().realms();
        Component name = holderIndex < 0 || holderIndex >= realms.size() ? Component.translatable("crusadecraft.lieu.nobody") : realms.get(holderIndex).name();
        return clip(Component.translatable("crusadecraft.lieu.form.holder.value", name));
    }

    private Component clip(Component c) {
        return Component.literal(font == null ? c.getString() : font.plainSubstrByWidth(c.getString(), fw - 32));
    }

    private void confirm() {
        switch (mode) {
            case CREATE -> CrusadeNetwork.toServer(new LieuPackets.Action(LieuPackets.Action.Op.CREATE, null, nameBox.getValue(), kind().ordinal(),
                    useUnder ? under : null, pos, kind() == LieuKind.REGION ? RADII[radiusIndex] : 0));
            case RENAME -> CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.RENAME, target, nameBox.getValue(), null));
            case HOLDER -> {
                List<LieuView.Choice> realms = parent.view().realms();
                UUID realm = holderIndex < 0 || holderIndex >= realms.size() ? null : realms.get(holderIndex).id();
                CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.HOLDER, target, "", realm));
            }
            case PARENT -> { }
        }
        // Une création ouvre l'écran des lieux sur le nouveau lieu ; le reste revient à l'écran, que le serveur rafraîchit.
        if (mode == Mode.CREATE && parent == null) minecraft.setScreen(null);
        else onClose();
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
        if (mode == Mode.CREATE || mode == Mode.RENAME) Ui.text(g, font, Component.translatable("crusadecraft.mapedit.form.name"), left + 10, top + 26, Ui.TEXT);
        if (mode == Mode.CREATE && pos != null && kind().placed()) {
            Ui.text(g, font, Component.translatable("crusadecraft.lieu.pos", pos.getX(), pos.getZ()), left + 12, top + fh - 40, Ui.TEXT_MUTED);
        }
        if (mode == Mode.HOLDER) Ui.wrapped(g, font, Component.translatable("crusadecraft.lieu.form.holder.help"), left + 12, top + 26, fw - 24, Ui.TEXT_MUTED);
        if (mode == Mode.PARENT) renderPicker(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private List<LieuView.Entry> candidates() {
        List<LieuView.Entry> out = new ArrayList<>();
        if (parent == null) return out;
        for (LieuView.Entry e : parent.view().entries()) if (!e.id().equals(target)) out.add(e);
        return out;
    }

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        Ui.text(g, font, Component.translatable("crusadecraft.lieu.form.parent.help"), left + 12, top + 22, Ui.TEXT_MUTED);
        Ui.inset(g, left + 10, listTop, fw - 20, listBottom - listTop);
        List<LieuView.Entry> list = candidates();
        int visible = (listBottom - listTop - 4) / ROW_H;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible)));
        for (int i = scroll; i < list.size() && i - scroll < visible; i++) {
            LieuView.Entry e = list.get(i);
            int ry = listTop + 2 + (i - scroll) * ROW_H;
            if (Ui.inside(mouseX, mouseY, left + 12, ry, fw - 24, ROW_H)) g.fill(left + 12, ry, left + fw - 12, ry + ROW_H, Ui.PANEL_HIGHLIGHT);
            int x = left + 14 + Math.min(e.depth(), 6) * 8;
            g.drawString(font, String.valueOf(LieuKind.byOrdinal(e.kind()).icon()), x, ry + 2, 0xFF000000 | e.color(), false);
            g.drawString(font, font.plainSubstrByWidth(e.name().getString(), left + fw - 14 - x - 10), x + 10, ry + 2, Ui.TEXT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mode == Mode.PARENT && Ui.inside(mouseX, mouseY, left + 10, listTop, fw - 20, listBottom - listTop)) {
            List<LieuView.Entry> list = candidates();
            int index = scroll + (int) ((mouseY - listTop - 2) / ROW_H);
            if (index >= 0 && index < list.size()) {
                CrusadeNetwork.toServer(LieuPackets.Action.of(LieuPackets.Action.Op.PARENT, target, "", list.get(index).id()));
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mode == Mode.PARENT) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta) * 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
