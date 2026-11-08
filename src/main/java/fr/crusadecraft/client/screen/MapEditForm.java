package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.logic.Heraldry;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiFaith;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.MapEditPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Le petit formulaire de l'éditeur de la carte : nommer un royaume (rang, couleur, foi, caractère, souverain) ou
 * une ville (taille). Il ne fait que demander ; la réponse du serveur s'affiche au pied de la carte.
 */
public class MapEditForm extends Screen {
    private static final int SWATCH = 14;

    private enum Mode { NEW_REALM, EDIT_REALM, NEW_CITY, RENAME_CITY }

    /** L'écran d'où l'on vient : la carte, ou le menu du bâton de bornage. */
    private final Screen parent;
    private final Mode mode;
    private final int x, z;
    private final UUID target;
    private final int province;
    private final Component owner;
    private final String initialName;

    private EditBox nameBox, rulerBox;
    private TitleRank rank = TitleRank.COMTE;
    private AiFaith faith = AiFaith.FIDELE;
    private AiPersonality personality = AiPersonality.OPPORTUNISTE;
    private int colorIndex;
    private WorldEditService.CitySize size = WorldEditService.CitySize.BOURG;
    private int left, top, fw, fh;
    private Component status;

    private MapEditForm(Screen parent, Mode mode, int x, int z, UUID target, int province, Component owner, String initialName) {
        super(Component.translatable("crusadecraft.mapedit.form." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        this.parent = parent;
        this.mode = mode;
        this.x = x;
        this.z = z;
        this.target = target;
        this.province = province;
        this.owner = owner;
        this.initialName = initialName;
    }

    /** Un nouveau royaume, sa capitale en (x, z) ; la couleur proposée est la première qu'aucun royaume ne porte. */
    public static MapEditForm newRealm(Screen parent, int x, int z, List<Integer> usedColors) {
        MapEditForm f = new MapEditForm(parent, Mode.NEW_REALM, x, z, null, -1, null, "");
        for (Heraldry h : Heraldry.values()) {
            if (!usedColors.contains(h.rgb())) {
                f.colorIndex = h.ordinal();
                break;
            }
        }
        return f;
    }

    public static MapEditForm editRealm(Screen parent, MapView.Territory t) {
        MapEditForm f = new MapEditForm(parent, Mode.EDIT_REALM, 0, 0, t.id(), -1, null, t.label());
        f.rank = t.rank();
        f.colorIndex = Heraldry.nearest(t.color()).ordinal();
        if (t.faith() != null) f.faith = t.faith();
        if (t.personality() != null) f.personality = t.personality();
        return f;
    }

    public static MapEditForm newCity(Screen parent, MapView.Territory owner, Component ownerName, int x, int z) {
        return new MapEditForm(parent, Mode.NEW_CITY, x, z, owner.id(), -1, ownerName, "");
    }

    public static MapEditForm renameCity(Screen parent, int province, String name) {
        return new MapEditForm(parent, Mode.RENAME_CITY, 0, 0, null, province, null, name);
    }

    private boolean realm() {
        return mode == Mode.NEW_REALM || mode == Mode.EDIT_REALM;
    }

    public void showStatus(boolean success, Component message) {
        status = message;
    }

    @Override
    protected void init() {
        fw = realm() ? 300 : 240;
        fh = mode == Mode.NEW_REALM ? 200 : mode == Mode.EDIT_REALM ? 178 : mode == Mode.NEW_CITY ? 138 : 100;
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        String keepName = nameBox == null ? initialName : nameBox.getValue();
        String keepRuler = rulerBox == null ? "" : rulerBox.getValue();
        int colW = realm() ? 128 : fw - 24;
        nameBox = addRenderableWidget(new EditBox(font, left + 12, top + 36, colW, 16, Component.translatable("crusadecraft.mapedit.form.name")));
        nameBox.setMaxLength(WorldEditService.NAME_MAX);
        nameBox.setValue(keepName);
        nameBox.setHint(Component.translatable(realm() ? "crusadecraft.mapedit.form.name.realm_hint" : "crusadecraft.mapedit.form.name.city_hint"));
        rulerBox = null;
        if (mode == Mode.NEW_REALM) {
            rulerBox = addRenderableWidget(new EditBox(font, left + 12, top + 70, colW, 16, Component.translatable("crusadecraft.mapedit.form.ruler")));
            rulerBox.setMaxLength(WorldEditService.NAME_MAX);
            rulerBox.setValue(keepRuler);
            rulerBox.setHint(Component.translatable("crusadecraft.mapedit.form.ruler_hint"));
        }
        if (realm()) {
            int cx = left + 148, cw = fw - 160;
            addRenderableWidget(Button.builder(rankLabel(), b -> {
                rank = TitleRank.values()[(rank.ordinal() + 1) % TitleRank.values().length];
                b.setMessage(rankLabel());
            }).bounds(cx, top + 36, cw, 16).build(KitButton::new));
            Button faithButton = Button.builder(faithLabel(), b -> {
                faith = AiFaith.values()[(faith.ordinal() + 1) % AiFaith.values().length];
                b.setMessage(faithLabel());
            }).bounds(cx, top + 56, cw, 16).build(KitButton::new);
            faithButton.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.form.faith.tip")));
            addRenderableWidget(faithButton);
            addRenderableWidget(Button.builder(personalityLabel(), b -> {
                personality = AiPersonality.values()[(personality.ordinal() + 1) % AiPersonality.values().length];
                b.setMessage(personalityLabel());
            }).bounds(cx, top + 76, cw, 16).build(KitButton::new));
        }
        if (mode == Mode.NEW_CITY) {
            addRenderableWidget(Button.builder(sizeLabel(), b -> {
                size = WorldEditService.CitySize.byOrdinal(size.ordinal() + 1);
                b.setMessage(sizeLabel());
            }).bounds(left + 12, top + 70, fw - 24, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.form.size.tip"))).build(KitButton::new));
        }
        Component confirm = Component.translatable(mode == Mode.NEW_REALM || mode == Mode.NEW_CITY ? "crusadecraft.mapedit.form.create"
                : "crusadecraft.mapedit.form.save");
        addRenderableWidget(Button.builder(confirm, b -> confirm()).bounds(left + fw / 2 - 94, top + fh - 26, 90, 18).build(KitButton::new));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + fw / 2 + 4, top + fh - 26, 90, 18).build(KitButton::new));
        setInitialFocus(nameBox);
    }

    private Component rankLabel() {
        return Component.translatable("crusadecraft.mapedit.form.rank", Component.translatable(rank.titleKey()));
    }

    private Component faithLabel() {
        return Component.translatable("crusadecraft.mapedit.form.faith", Component.translatable(faith.key()));
    }

    private Component personalityLabel() {
        return Component.translatable("crusadecraft.mapedit.form.personality", Component.translatable(personality.key()));
    }

    private Component sizeLabel() {
        return Component.translatable("crusadecraft.mapedit.form.size", Component.translatable(size.key()), size.population());
    }

    private void confirm() {
        String name = nameBox.getValue();
        switch (mode) {
            case NEW_REALM -> CrusadeNetwork.toServer(new MapEditPackets.CreateRealm(name, rulerBox.getValue(), rank.ordinal(), colorIndex,
                    faith.ordinal(), personality.ordinal(), x, z));
            case EDIT_REALM -> CrusadeNetwork.toServer(new MapEditPackets.EditRealm(target, name, rank.ordinal(), colorIndex, faith.ordinal(),
                    personality.ordinal()));
            case NEW_CITY -> CrusadeNetwork.toServer(new MapEditPackets.CreateCity(target, name, size.ordinal(), x, z));
            case RENAME_CITY -> CrusadeNetwork.toServer(MapEditPackets.CityAction.rename(province, name));
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
        if (mode == Mode.NEW_REALM) {
            Ui.text(g, font, Component.translatable("crusadecraft.mapedit.form.ruler"), left + 10, top + 60, Ui.TEXT);
        }
        if (mode == Mode.NEW_CITY) {
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable("crusadecraft.mapedit.form.city_of", owner).getString(),
                    fw - 24)), left + 12, top + 58, Ui.TEXT_MUTED);
        }
        if (mode == Mode.NEW_REALM || mode == Mode.NEW_CITY) {
            Ui.text(g, font, Component.translatable("crusadecraft.mapedit.form.position", x, z), left + 12,
                    mode == Mode.NEW_CITY ? top + 94 : top + fh - 40, Ui.TEXT_MUTED);
        }
        if (realm()) {
            int sy = swatchY(0) - 11;
            Ui.text(g, font, Component.translatable("crusadecraft.founding.field.color"), left + 10, sy, Ui.TEXT);
            Heraldry[] all = Heraldry.values();
            for (int i = 0; i < all.length; i++) {
                int sx = swatchX(i), y = swatchY(i);
                if (i == colorIndex) g.fill(sx - 2, y - 2, sx + SWATCH + 2, y + SWATCH + 2, Ui.GOLD);
                g.fill(sx - 1, y - 1, sx + SWATCH + 1, y + SWATCH + 1, 0xFF000000);
                g.fill(sx, y, sx + SWATCH, y + SWATCH, 0xFF000000 | all[i].rgb());
            }
            Ui.shield(g, font, left + fw - 52, swatchY(0) - 6, 34, 42, Heraldry.byOrdinal(colorIndex).rgb(), Ui.initial(nameBox.getValue()));
        }
        if (status != null) Ui.centered(g, font, status, left + fw / 2, top + fh + 4, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
        if (realm()) {
            Heraldry[] all = Heraldry.values();
            for (int i = 0; i < all.length; i++) {
                if (Ui.inside(mouseX, mouseY, swatchX(i), swatchY(i), SWATCH, SWATCH)) g.renderTooltip(font, Component.translatable(all[i].key()), mouseX, mouseY);
            }
        }
    }

    private int swatchX(int i) {
        return left + 12 + (i % 8) * (SWATCH + 4);
    }

    private int swatchY(int i) {
        return top + (mode == Mode.NEW_REALM ? 112 : 112 - 22) + (i / 8) * (SWATCH + 4);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (realm()) {
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
