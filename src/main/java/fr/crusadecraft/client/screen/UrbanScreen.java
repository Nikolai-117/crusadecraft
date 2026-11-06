package fr.crusadecraft.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import fr.crusadecraft.core.build.plan.NationStyle;
import fr.crusadecraft.core.build.plan.TownSize;
import fr.crusadecraft.core.build.plan.UrbanGenerator;
import fr.crusadecraft.core.build.plan.UrbanParams;
import fr.crusadecraft.core.build.plan.UrbanPresets;
import fr.crusadecraft.core.build.plan.UrbanWard;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.UrbanPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * L'atelier des villes (P60). À gauche, tous les réglages, rangés par onglets ; à droite, le plan de la ville, refait à chaque changement. On y fait glisser et on y zoome ; un
 * clic change la nature d'un îlot ou le modèle d'un bâtiment, un clic droit retire ce bâtiment. Le plan est dressé par le serveur : ce qu'on voit est ce qui se posera.
 */
public class UrbanScreen extends Screen {
    private static final int ROW_H = 20, TAB_H = 16, CONTROL_W = 112;
    private static final int PRESET_TAB = UrbanParams.Group.values().length;
    /** Le temps qu'on laisse à un curseur qu'on fait glisser avant de redemander un plan. */
    private static final long DEBOUNCE_MS = 220;

    private static UrbanParams keep;
    private static int keepTab;

    private final UrbanParams params;
    private final List<String> saved = new ArrayList<>();
    private final Random random = new Random();
    private int tab = keepTab;
    private int left, top, w, h, listX, listW, listTop, listBottom, mapX, mapY, mapW, mapH;
    private float scroll;
    private int maxScroll;
    private UrbanParams.Def dragging;
    private EditBox nameBox, seedBox, saveBox;
    private Button takeButton, rollButton, doneButton, saveButton, forgetButton;
    private boolean dirty = true, waiting, noPack, seedChanging;
    private long dirtyAt;

    // Ce que le serveur a répondu.
    private int half;
    private byte[] view = new byte[0];
    private short[] viewLots = new short[0];
    private int[] stats = new int[0], buildings = new int[0], lots = new int[0];
    private List<String> names = List.of();

    // La carte à l'écran.
    private DynamicTexture texture;
    private ResourceLocation textureId;
    private int textureSize;
    private double viewX, viewZ, zoom = -1;
    private boolean panning, panned;
    private double downX, downY;
    private int hoverRow = -1;
    private Component message;
    private long messageUntil;

    public UrbanScreen(List<String> savedNames) {
        super(Component.translatable("crusadecraft.urban.title"));
        saved.addAll(savedNames);
        params = keep != null ? keep : new UrbanParams().seed(new Random().nextLong() & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ le réseau

    public static void request() {
        CrusadeNetwork.toServer(new UrbanPackets.Request(UrbanPackets.OPEN, new CompoundTag(), ""));
    }

    public static void onSaved(List<String> names, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (open) mc.setScreen(new UrbanScreen(names));
        else if (mc.screen instanceof UrbanScreen s) {
            s.saved.clear();
            s.saved.addAll(names);
        }
    }

    public static void onLoaded(CompoundTag t) {
        if (Minecraft.getInstance().screen instanceof UrbanScreen s) s.replace(UrbanParams.load(t));
    }

    public static void onTaken() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof UrbanScreen) mc.setScreen(null);
    }

    public static void onPreview(UrbanPackets.Preview p) {
        if (Minecraft.getInstance().screen instanceof UrbanScreen s) s.show(p);
    }

    private void show(UrbanPackets.Preview p) {
        waiting = false;
        noPack = !p.ok();
        if (!p.ok()) return;
        boolean resized = half != p.half();
        half = p.half();
        int side = 2 * half + 1;
        view = UrbanPackets.inflate(p.view(), side * side);
        byte[] grid = UrbanPackets.inflate(p.lotGrid(), side * side * 2);
        viewLots = new short[side * side];
        for (int i = 0; i < viewLots.length && i * 2 + 1 < grid.length; i++) viewLots[i] = (short) ((grid[i * 2] & 0xFF) << 8 | grid[i * 2 + 1] & 0xFF);
        stats = p.stats();
        buildings = p.buildings();
        names = p.names();
        lots = p.lots();
        upload(side);
        if (zoom < 0 || resized) fit(side);
    }

    private void upload(int side) {
        if (texture == null || textureSize != side) {
            release();
            texture = new DynamicTexture(side, side, false);
            textureId = Minecraft.getInstance().getTextureManager().register("crusadecraft_atelier", texture);
            textureSize = side;
        }
        NativeImage image = texture.getPixels();
        if (image == null || view.length < side * side) return;
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) image.setPixelRGBA(x, z, UrbanPalette.abgr(view[x + z * side], x, z));
        }
        texture.upload();
    }

    private void release() {
        if (textureId != null) Minecraft.getInstance().getTextureManager().release(textureId);
        texture = null;
        textureId = null;
    }

    @Override
    public void removed() {
        release();
        keep = params;
        keepTab = tab;
    }

    private void fit(int side) {
        zoom = Math.min((double) mapW / side, (double) mapH / side);
        viewX = 0;
        viewZ = 0;
    }

    private void changed() {
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    private void replace(UrbanParams other) {
        for (UrbanParams.Def d : UrbanParams.defs()) params.set(d, other.get(d));
        params.name(other.name()).seed(other.seed());
        params.clearEdits();
        if (nameBox != null) nameBox.setValue(params.name());
        syncSeed();
        changed();
    }

    private void syncSeed() {
        if (seedBox == null) return;
        seedChanging = true;
        seedBox.setValue(Long.toString(params.seed()));
        seedChanging = false;
    }

    // ------------------------------------------------------------------ la mise en place

    @Override
    protected void init() {
        w = Math.min(width - 8, 1000);
        h = Math.min(height - 8, 620);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listX = left + 6;
        listW = Mth.clamp((int) (w * 0.44), 230, 380);
        listTop = top + 22 + TAB_H * 2 + 4;
        listBottom = top + h - 42;
        mapX = listX + listW + 6;
        mapY = top + 22;
        mapW = left + w - 6 - mapX;
        mapH = top + h - 42 - mapY;

        int y = top + h - 38;
        String name = nameBox == null ? params.name() : nameBox.getValue();
        nameBox = new EditBox(font, listX, y, listW / 2 - 2, 14, Component.translatable("crusadecraft.urban.name"));
        nameBox.setMaxLength(UrbanParams.NAME_MAX);
        nameBox.setHint(Component.translatable("crusadecraft.urban.name"));
        nameBox.setValue(name);
        nameBox.setResponder(s -> {
            params.name(s);
        });
        addRenderableWidget(nameBox);
        seedBox = new EditBox(font, listX + listW / 2 + 2, y, listW / 2 - 2, 14, Component.translatable("crusadecraft.urban.seed"));
        seedBox.setMaxLength(20);
        seedBox.setHint(Component.translatable("crusadecraft.urban.seed"));
        seedBox.setValue(Long.toString(params.seed()));
        seedBox.setResponder(s -> {
            if (seedChanging) return;
            long v;
            try {
                v = Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                v = s.hashCode();
            }
            if (v != params.seed()) {
                params.seed(v);
                params.clearEdits();
                changed();
            }
        });
        addRenderableWidget(seedBox);
        int by = top + h - 20;
        int third = (w - 12 - 8) / 3;
        rollButton = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.urban.roll"), b -> {
            params.seed(random.nextLong() & 0xFFFFFFFFL);
            params.clearEdits();
            syncSeed();
            changed();
        }).bounds(listX, by, third, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.urban.roll.hint"))).build());
        takeButton = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.urban.take"), b -> send(UrbanPackets.TAKE, ""))
                .bounds(listX + third + 4, by, third, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.urban.take.hint"))).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(listX + 2 * third + 8, by, third, 16).build());
        forgetButton = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.urban.forget"), b -> {
            params.clearEdits();
            changed();
        }).bounds(mapX + mapW - 118, mapY + 4, 114, 14).build());

        saveBox = new EditBox(font, listX + 4, listTop + 4, listW - 92, 14, Component.translatable("crusadecraft.urban.save.name"));
        saveBox.setMaxLength(UrbanParams.NAME_MAX);
        saveBox.setHint(Component.translatable("crusadecraft.urban.save.name"));
        addRenderableWidget(saveBox);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("crusadecraft.urban.save"), b -> {
            if (!saveBox.getValue().isBlank()) send(UrbanPackets.SAVE, saveBox.getValue());
        }).bounds(listX + listW - 84, listTop + 3, 80, 16).build());
        if (texture != null && half > 0) fit(2 * half + 1);
        changed();
    }

    private void send(int action, String text) {
        CrusadeNetwork.toServer(new UrbanPackets.Request(action, params.save(), text));
    }

    @Override
    public void tick() {
        if (nameBox != null) nameBox.tick();
        if (seedBox != null) seedBox.tick();
        if (saveBox != null) saveBox.tick();
        if (dirty && System.currentTimeMillis() - dirtyAt >= DEBOUNCE_MS) {
            dirty = false;
            waiting = true;
            send(UrbanPackets.PREVIEW, "");
        }
    }

    // ------------------------------------------------------------------ dessiner

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.text(g, font, title, left + 10, top + 7, Ui.TEXT_TITLE);
        boolean presets = tab == PRESET_TAB;
        saveBox.visible = presets;
        saveButton.visible = presets;
        forgetButton.visible = !params.lotWards().isEmpty() || !params.removed().isEmpty() || !params.rerolls().isEmpty();
        renderTabs(g, mouseX, mouseY);
        Ui.inset(g, listX, listTop, listW, listBottom - listTop);
        g.enableScissor(listX + 1, listTop + 1, listX + listW - 1, listBottom - 1);
        hoverRow = -1;
        if (presets) renderPresets(g, mouseX, mouseY);
        else renderRows(g, mouseX, mouseY);
        g.disableScissor();
        renderMap(g, mouseX, mouseY);
        renderStats(g);
        super.render(g, mouseX, mouseY, partial);
        renderHints(g, mouseX, mouseY);
    }

    private int tabX(int i, int perRow) {
        return listX + (listW * (i % perRow)) / perRow;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int count = PRESET_TAB + 1, perRow = 4;
        int tw = listW / perRow;
        for (int i = 0; i < count; i++) {
            int x = tabX(i, perRow), y = top + 22 + (i / perRow) * TAB_H;
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < y + TAB_H;
            int bg = i == tab ? Ui.GOLD_DARK : hover ? Ui.PANEL_HIGHLIGHT : Ui.PANEL_INNER;
            g.fill(x + 1, y + 1, x + tw - 1, y + TAB_H - 1, bg);
            Component label = i == PRESET_TAB ? Component.translatable("crusadecraft.urban.group.reglages") : Component.translatable(UrbanParams.Group.values()[i].key());
            Ui.centered(g, font, label, x + tw / 2, y + 4, i == tab ? Ui.TEXT_TITLE : Ui.TEXT);
        }
    }

    private List<UrbanParams.Def> currentDefs() {
        return UrbanParams.defs(UrbanParams.Group.values()[tab]);
    }

    private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
        List<UrbanParams.Def> defs = currentDefs();
        int total = defs.size() * ROW_H;
        maxScroll = Math.max(0, total - (listBottom - listTop - 4));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int y = listTop + 3 - (int) scroll;
        for (int i = 0; i < defs.size(); i++, y += ROW_H) {
            if (y + ROW_H < listTop || y > listBottom) continue;
            UrbanParams.Def d = defs.get(i);
            boolean inList = mouseY >= listTop && mouseY < listBottom;
            boolean hover = inList && mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_H;
            if (hover) {
                hoverRow = i;
                g.fill(listX + 1, y, listX + listW - 1, y + ROW_H, Ui.PANEL_HIGHLIGHT);
            }
            int labelW = listW - CONTROL_W - 14;
            int labelX = listX + 6;
            UrbanWard chip = wardOf(d);
            if (chip != null) {
                g.fill(labelX, y + 6, labelX + 7, y + 13, 0xFF000000 | chip.color());
                labelX += 10;
                labelW -= 10;
            }
            Ui.fitted(g, font, Component.translatable(d.label()), labelX, y + 6, labelW, Ui.TEXT);
            renderControl(g, d, listX + listW - CONTROL_W - 6, y + 3, mouseX, mouseY);
        }
    }

    private static UrbanWard wardOf(UrbanParams.Def d) {
        for (UrbanWard w : UrbanWard.values()) if (w.weight() == d) return w;
        return null;
    }

    private Component valueText(UrbanParams.Def d) {
        int v = params.get(d);
        if (d.isStyle()) return Component.translatable(NationStyle.values()[Math.min(v, NationStyle.values().length - 1)].key());
        return switch (d.kind()) {
            case TOGGLE -> Component.translatable(v != 0 ? "crusadecraft.urban.yes" : "crusadecraft.urban.no");
            case CHOICE -> Component.translatable(d.valueKey(v));
            case SLIDER -> d.auto() && v < 0 ? Component.translatable("crusadecraft.urban.auto") : Component.literal(Integer.toString(v));
        };
    }

    private void renderControl(GuiGraphics g, UrbanParams.Def d, int x, int y, int mouseX, int mouseY) {
        int ch = ROW_H - 6;
        switch (d.kind()) {
            case SLIDER -> {
                g.fill(x, y, x + CONTROL_W, y + ch, 0xFF15110D);
                double f = (double) (params.get(d) - d.min()) / Math.max(1, d.max() - d.min());
                g.fill(x + 1, y + 1, x + 1 + (int) ((CONTROL_W - 2) * f), y + ch - 1, Ui.GOLD_DARK);
                int knob = x + (int) ((CONTROL_W - 3) * f);
                g.fill(knob, y, knob + 3, y + ch, Ui.GOLD);
                Ui.centered(g, font, valueText(d), x + CONTROL_W / 2, y + 3, Ui.TEXT);
            }
            case TOGGLE -> {
                boolean on = params.on(d);
                g.fill(x, y, x + CONTROL_W, y + ch, on ? 0xFF3E5A2A : 0xFF15110D);
                Ui.centered(g, font, valueText(d), x + CONTROL_W / 2, y + 3, on ? Ui.TEXT_TITLE : Ui.TEXT_MUTED);
            }
            case CHOICE -> {
                g.fill(x, y, x + CONTROL_W, y + ch, 0xFF15110D);
                g.drawString(font, "‹", x + 4, y + 3, Ui.GOLD, false);
                g.drawString(font, "›", x + CONTROL_W - 8, y + 3, Ui.GOLD, false);
                Component text = valueText(d);
                int tw = font.width(text);
                if (tw > CONTROL_W - 20) Ui.fitted(g, font, text, x + 11, y + 3, CONTROL_W - 22, Ui.TEXT);
                else Ui.centered(g, font, text, x + CONTROL_W / 2, y + 3, Ui.TEXT);
            }
        }
    }

    private void renderPresets(GuiGraphics g, int mouseX, int mouseY) {
        List<UrbanPresets.Preset> presets = UrbanPresets.all();
        int rows = presets.size() + saved.size() + 2;
        int area = listBottom - listTop - 24;
        maxScroll = Math.max(0, rows * 16 - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int y = listTop + 24 - (int) scroll;
        Ui.text(g, font, Component.translatable("crusadecraft.urban.presets.ready"), listX + 6, y + 3, Ui.TEXT_TITLE);
        y += 16;
        for (UrbanPresets.Preset p : presets) {
            presetRow(g, Component.translatable(p.key()), y, mouseX, mouseY, false);
            y += 16;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.urban.presets.saved", saved.size()), listX + 6, y + 3, Ui.TEXT_TITLE);
        y += 16;
        for (String s : saved) {
            presetRow(g, Component.literal(s), y, mouseX, mouseY, true);
            y += 16;
        }
    }

    private void presetRow(GuiGraphics g, Component label, int y, int mouseX, int mouseY, boolean deletable) {
        if (y + 16 < listTop + 22 || y > listBottom) return;
        boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + 16 && mouseY > listTop + 22 && mouseY < listBottom;
        if (hover) g.fill(listX + 1, y, listX + listW - 1, y + 16, Ui.PANEL_HIGHLIGHT);
        Ui.fitted(g, font, label, listX + 10, y + 4, listW - 40, Ui.TEXT);
        if (deletable) g.drawString(font, "×", listX + listW - 14, y + 4, Ui.RED, false);
    }

    private double blockX(double screenX) {
        return (screenX - (mapX + mapW / 2.0)) / zoom + viewX;
    }

    private double blockZ(double screenY) {
        return (screenY - (mapY + mapH / 2.0)) / zoom + viewZ;
    }

    private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
        Ui.inset(g, mapX, mapY, mapW, mapH);
        if (noPack) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.urban.no_pack"), mapX + mapW / 2, mapY + mapH / 2 - 8, mapW - 30, Ui.RED);
            return;
        }
        if (texture == null || textureId == null) {
            Ui.centered(g, font, Component.translatable("crusadecraft.urban.drawing"), mapX + mapW / 2, mapY + mapH / 2, Ui.TEXT_MUTED);
            return;
        }
        int side = textureSize;
        clampView(side);
        double bw = mapW / zoom, bh = mapH / zoom;
        double bx0 = viewX - bw / 2 + half, bz0 = viewZ - bh / 2 + half;
        g.enableScissor(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1);
        // Tout le plan, à l'échelle, décalé selon la vue : le découpage laisse ce qui dépasse dehors.
        var pose = g.pose();
        pose.pushPose();
        pose.translate(mapX + mapW / 2.0 - viewX * zoom - (half + 0.5) * zoom, mapY + mapH / 2.0 - viewZ * zoom - (half + 0.5) * zoom, 0);
        pose.scale((float) zoom, (float) zoom, 1);
        g.blit(textureId, 0, 0, 0, 0, side, side, side, side);
        pose.popPose();
        // Ce qu'on a sous la souris.
        if (over(mouseX, mouseY)) {
            int[] b = buildingAt(blockX(mouseX), blockZ(mouseY));
            if (b != null) {
                int x0 = (int) ((b[1] - viewX) * zoom + mapX + mapW / 2.0), z0 = (int) ((b[2] - viewZ) * zoom + mapY + mapH / 2.0);
                int x1 = (int) ((b[3] + 1 - viewX) * zoom + mapX + mapW / 2.0), z1 = (int) ((b[4] + 1 - viewZ) * zoom + mapY + mapH / 2.0);
                g.renderOutline(x0, z0, x1 - x0, z1 - z0, 0xFFFFFFFF);
            }
        }
        g.disableScissor();
        scaleBar(g);
        // Le nord, en haut : c'est la direction que le fantôme montrera aussi, avant de tourner le plan.
        g.drawString(font, "N", mapX + 8, mapY + 6, Ui.INK, false);
        if (waiting) Ui.text(g, font, Component.translatable("crusadecraft.urban.drawing"), mapX + 8, mapY + mapH - 12, Ui.INK);
    }

    private void scaleBar(GuiGraphics g) {
        double blocks = 10;
        for (double b : new double[]{10, 20, 50, 100, 200, 500}) {
            if (b * zoom >= 30) {
                blocks = b;
                break;
            }
            blocks = b;
        }
        int len = (int) (blocks * zoom);
        int x = mapX + mapW - len - 8, y = mapY + mapH - 8;
        g.fill(x, y, x + len, y + 1, Ui.INK);
        g.fill(x, y - 3, x + 1, y + 1, Ui.INK);
        g.fill(x + len - 1, y - 3, x + len, y + 1, Ui.INK);
        Component t = Component.translatable("crusadecraft.urban.blocks", (int) blocks);
        g.drawString(font, t, x + len - font.width(t), y - 12, Ui.INK, false);
    }

    private boolean over(double x, double y) {
        return x >= mapX && x < mapX + mapW && y >= mapY && y < mapY + mapH;
    }

    private void clampView(int side) {
        double min = Math.min((double) mapW / side, (double) mapH / side);
        zoom = Mth.clamp(zoom, min, 8);
        double bw = mapW / zoom / 2, bh = mapH / zoom / 2;
        viewX = half <= bw ? 0 : Mth.clamp(viewX, -half + bw, half - bw);
        viewZ = half <= bh ? 0 : Mth.clamp(viewZ, -half + bh, half - bh);
    }

    private void renderStats(GuiGraphics g) {
        if (stats.length < 10) return;
        int y = top + h - 38;
        Component line = Component.translatable("crusadecraft.urban.stats", stats[0], stats[1], stats[3] - stats[1], stats[4], stats[5], stats[6], stats[9]);
        Ui.fitted(g, font, line, mapX, y, mapW, Ui.TEXT);
        Component second;
        int color = Ui.TEXT_MUTED;
        if (stats[8] > 0) {
            second = Component.translatable("crusadecraft.urban.no_room", stats[8]);
            color = Ui.RED;
        } else if (stats.length > 10 && stats[10] > 0) {
            second = Component.translatable("crusadecraft.urban.no_model", stats[10]);
            color = Ui.GOLD;
        } else {
            second = Component.translatable("crusadecraft.urban.help");
        }
        Ui.fitted(g, font, second, mapX, y + 10, mapW, color);
    }

    private void renderHints(GuiGraphics g, int mouseX, int mouseY) {
        if (tab != PRESET_TAB && hoverRow >= 0 && hoverRow < currentDefs().size() && !over(mouseX, mouseY)) {
            UrbanParams.Def d = currentDefs().get(hoverRow);
            g.renderTooltip(font, font.split(Component.translatable(d.hint()), 240), mouseX, mouseY);
        }
        if (message != null && System.currentTimeMillis() < messageUntil) Ui.centered(g, font, message, mapX + mapW / 2, mapY + 8, Ui.TEXT_TITLE);
        if (!over(mouseX, mouseY) || view.length == 0 || panning) return;
        double bx = blockX(mouseX), bz = blockZ(mouseY);
        int[] b = buildingAt(bx, bz);
        List<Component> lines = new ArrayList<>();
        if (b != null) {
            int i = b[7];
            lines.add(Component.translatable("crusadecraft.urban.role." + TownSize.Role.values()[b[5]].name().toLowerCase(java.util.Locale.ROOT)));
            if (i < names.size()) lines.add(Component.literal(names.get(i)).withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.add(Component.translatable("crusadecraft.urban.building.help").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else {
            int side = 2 * half + 1;
            int x = (int) Math.floor(bx) + half, z = (int) Math.floor(bz) + half;
            if (x >= 0 && z >= 0 && x < side && z < side) {
                int v = view[x + z * side] & 0xFF;
                if (v >= UrbanGenerator.VIEW_LOT && v < UrbanGenerator.VIEW_LOT + UrbanWard.values().length) {
                    lines.add(Component.translatable(UrbanWard.values()[v - UrbanGenerator.VIEW_LOT].key()));
                    lines.add(Component.translatable("crusadecraft.urban.lot.help").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                } else if (v == UrbanGenerator.GATE) lines.add(Component.translatable("crusadecraft.urban.cell.gate"));
                else if (v == UrbanGenerator.TOWER) lines.add(Component.translatable("crusadecraft.urban.cell.tower"));
                else if (v == UrbanGenerator.WALL) lines.add(Component.translatable("crusadecraft.urban.cell.wall"));
                else if (v == UrbanGenerator.BRIDGE) lines.add(Component.translatable("crusadecraft.urban.cell.bridge"));
                else if (v == UrbanGenerator.PIER) lines.add(Component.translatable("crusadecraft.urban.cell.pier"));
                else if (v == UrbanGenerator.FIELD) lines.add(Component.translatable("crusadecraft.urban.cell.field"));
                else if (v == UrbanGenerator.PLAZA) lines.add(Component.translatable("crusadecraft.urban.cell.plaza"));
                else if (v == UrbanGenerator.RIVER || v == UrbanGenerator.SEA || v == UrbanGenerator.MOAT) lines.add(Component.translatable("crusadecraft.urban.cell.water"));
            }
        }
        if (!lines.isEmpty()) g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /** Le bâtiment sous ce point, avec son rang dans la liste en plus : {@code {slot, x0, z0, x1, z1, genre, îlot, rang}}. */
    private int[] buildingAt(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int i = 0; i + 6 < buildings.length; i += 7) {
            if (bx >= buildings[i + 1] && bx <= buildings[i + 3] && bz >= buildings[i + 2] && bz <= buildings[i + 4]) {
                return new int[]{buildings[i], buildings[i + 1], buildings[i + 2], buildings[i + 3], buildings[i + 4], buildings[i + 5], buildings[i + 6], i / 7};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ les gestes

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int perRow = 4, tw = listW / perRow;
        for (int i = 0; i <= PRESET_TAB; i++) {
            int x = tabX(i, perRow), y = top + 22 + (i / perRow) * TAB_H;
            if (mx >= x && mx < x + tw && my >= y && my < y + TAB_H) {
                tab = i;
                scroll = 0;
                return true;
            }
        }
        if (over(mx, my)) {
            panning = true;
            panned = false;
            downX = mx;
            downY = my;
            clickButton = button;
            return true;
        }
        if (mx < listX || mx >= listX + listW || my < listTop || my >= listBottom) return false;
        if (tab == PRESET_TAB) return clickPreset(mx, my);
        List<UrbanParams.Def> defs = currentDefs();
        int row = (int) ((my - listTop - 3 + scroll) / ROW_H);
        if (row < 0 || row >= defs.size()) return false;
        UrbanParams.Def d = defs.get(row);
        int cx = listX + listW - CONTROL_W - 6;
        if (mx < cx) return true;
        switch (d.kind()) {
            case TOGGLE -> {
                setValue(d, params.on(d) ? 0 : 1);
            }
            case CHOICE -> {
                int v = params.get(d);
                int next = button == 1 || mx < cx + CONTROL_W / 4.0 ? v - 1 : v + 1;
                setValue(d, Math.floorMod(next, Math.max(1, d.choices())));
            }
            case SLIDER -> {
                dragging = d;
                dragTo(d, mx, cx);
            }
        }
        return true;
    }

    private int clickButton;

    private boolean clickPreset(double mx, double my) {
        int y = listTop + 24 - (int) scroll + 16;
        List<UrbanPresets.Preset> presets = UrbanPresets.all();
        for (UrbanPresets.Preset p : presets) {
            if (my >= y && my < y + 16 && my > listTop + 22) {
                replace(UrbanPresets.apply(p, params));
                say(Component.translatable("crusadecraft.urban.preset.applied", Component.translatable(p.key())));
                return true;
            }
            y += 16;
        }
        y += 16;
        for (String s : new ArrayList<>(saved)) {
            if (my >= y && my < y + 16 && my > listTop + 22) {
                if (mx >= listX + listW - 18) send(UrbanPackets.DELETE, s);
                else send(UrbanPackets.LOAD, s);
                return true;
            }
            y += 16;
        }
        return true;
    }

    private void say(Component c) {
        message = c;
        messageUntil = System.currentTimeMillis() + 2500;
    }

    private void setValue(UrbanParams.Def d, int v) {
        if (params.get(d) == d.clamp(v)) return;
        if (d == UrbanParams.MODELE) params.applyModel(v);
        else {
            params.set(d, v);
            // Toucher au tracé rend le modèle « personnalisé » : ce n'est plus tout à fait lui.
            if (d == UrbanParams.ANGLE || d == UrbanParams.SEGMENT || d == UrbanParams.BRANCHE || d == UrbanParams.NOUVELLE_ROUTE || d == UrbanParams.RESEAU || d == UrbanParams.ROUTES) {
                params.set(UrbanParams.MODELE, 5);
            }
        }
        if (UrbanParams.touchesLayout(d) && (!params.lotWards().isEmpty() || !params.removed().isEmpty() || !params.rerolls().isEmpty())) {
            params.clearEdits();
            say(Component.translatable("crusadecraft.urban.edits_forgotten"));
        }
        changed();
    }

    private void dragTo(UrbanParams.Def d, double mx, int cx) {
        double f = Mth.clamp((mx - cx) / CONTROL_W, 0, 1);
        int v = d.min() + (int) Math.round(f * (d.max() - d.min()) / d.step()) * d.step();
        setValue(d, v);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragTo(dragging, mx, listX + listW - CONTROL_W - 6);
            return true;
        }
        if (panning) {
            if (Math.hypot(mx - downX, my - downY) > 3) panned = true;
            if (panned) {
                viewX -= dx / zoom;
                viewZ -= dy / zoom;
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        if (panning) {
            panning = false;
            if (!panned) clickMap(mx, my, clickButton);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Un clic sur le plan : le bâtiment change de modèle, ou l'îlot de nature. Un clic droit retire le bâtiment. */
    private void clickMap(double mx, double my, int button) {
        if (view.length == 0) return;
        double bx = blockX(mx), bz = blockZ(my);
        int[] b = buildingAt(bx, bz);
        if (b != null) {
            if (button == 1) params.toggleRemoved(b[0]);
            else params.reroll(b[0]);
            changed();
            return;
        }
        int side = 2 * half + 1;
        int x = (int) Math.floor(bx) + half, z = (int) Math.floor(bz) + half;
        if (x < 0 || z < 0 || x >= side || z >= side) return;
        int v = view[x + z * side] & 0xFF;
        if (v < UrbanGenerator.VIEW_LOT || v >= UrbanGenerator.VIEW_LOT + UrbanWard.values().length) return;
        int lot = x + z * side < viewLots.length ? viewLots[x + z * side] : -1;
        if (lot < 0) return;
        int next = Math.floorMod(v - UrbanGenerator.VIEW_LOT + (button == 1 ? -1 : 1), UrbanWard.values().length);
        params.setLotWard(lot, next);
        changed();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (over(mx, my)) {
            double beforeX = blockX(mx), beforeZ = blockZ(my);
            zoom = Mth.clamp(zoom * (delta > 0 ? 1.25 : 0.8), 0.05, 8);
            clampView(Math.max(1, textureSize));
            viewX += beforeX - blockX(mx);
            viewZ += beforeZ - blockZ(my);
            return true;
        }
        if (mx >= listX && mx < listX + listW) {
            scroll = Mth.clamp(scroll - (float) delta * 18, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ pour le banc d'essai

    public void benchTab(int i) {
        tab = i;
        scroll = 0;
    }

    public void benchPreset(UrbanPresets.Preset p) {
        replace(UrbanPresets.apply(p, params));
    }

    public void benchSet(UrbanParams.Def d, int v) {
        setValue(d, v);
    }

    public void benchName(String n) {
        nameBox.setValue(n);
        params.name(n);
    }

    public void benchSeed(long s) {
        params.seed(s);
        syncSeed();
        changed();
    }

    public void benchTake() {
        send(UrbanPackets.TAKE, "");
    }

    public boolean benchIdle() {
        return !dirty && !waiting;
    }

    public int[] benchStats() {
        return stats;
    }

    public int benchBuilding(int index) {
        return index * 7 < buildings.length ? buildings[index * 7] : -1;
    }

    public void benchClickBuilding(int index, int button) {
        if (index * 7 + 4 >= buildings.length) return;
        int i = index * 7;
        double x = (buildings[i + 1] + buildings[i + 3]) / 2.0 + 0.5, z = (buildings[i + 2] + buildings[i + 4]) / 2.0 + 0.5;
        clickMap((x - viewX) * zoom + mapX + mapW / 2.0, (z - viewZ) * zoom + mapY + mapH / 2.0, button);
    }

    public void benchSave(String name) {
        send(UrbanPackets.SAVE, name);
    }
}
