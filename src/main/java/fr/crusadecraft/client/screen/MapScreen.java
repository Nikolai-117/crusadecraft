package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.KeyBindings;
import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.client.map.MapTexture;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La carte du royaume (touche virgule) : les royaumes connus, colorés par État (vue politique) ou par
 * royaume de jure, leurs capitales et leurs provinces, et le brouillard sur ce qui reste à découvrir.
 */
public class MapScreen extends Screen {
    private static final int INK = 0xFF3A2918;
    private static boolean lastDejure;
    /** Centre demandé par un autre écran (« Voir sur la carte »), appliqué à la prochaine ouverture. */
    private static int[] pendingCenter;

    private MapView view;
    private boolean dejure = lastDejure;
    private double centerX, centerZ, scale = -1;
    private int left, top, w, h;
    private int mapX, mapY, mapW, mapH;
    private final MapTexture texture = new MapTexture("map");
    private MapRaster.Result raster;
    private boolean dirty = true;
    private boolean dragging, moved;
    private double pressX, pressY;

    public MapScreen(MapView view) {
        super(Component.translatable("crusadecraft.map.title"));
        this.view = view;
        if (pendingCenter != null) {
            centerX = pendingCenter[0];
            centerZ = pendingCenter[1];
            pendingCenter = null;
        } else {
            centerX = view.focusX();
            centerZ = view.focusZ();
        }
    }

    public static void centerNextOn(int x, int z) {
        pendingCenter = new int[]{x, z};
    }

    /** Bascule en vue de jure (aperçu du banc d'essai visuel). */
    public void toggleDejureForPreview() {
        dejure = !dejure;
        dirty = true;
    }

    public void refresh(MapView newView) {
        this.view = newView;
        dirty = true;
    }

    @Override
    protected void init() {
        w = width - 16;
        h = height - 16;
        left = 8;
        top = 8;
        mapX = left + 8;
        mapY = top + 26;
        mapW = w - 16;
        mapH = h - 26 - 24;
        if (scale < 0) scale = MapRaster.fit(view, centerX, centerZ, mapW, mapH) * 0.6;
        int bx = left + w - 8;
        Button done = Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx - 70, top + 5, 70, 16).build();
        addRenderableWidget(done);
        Button center = Button.builder(Component.translatable("crusadecraft.map.center"), b -> {
            centerX = view.focusX();
            centerZ = view.focusZ();
            dirty = true;
        }).bounds(bx - 150, top + 5, 76, 16).build();
        addRenderableWidget(center);
        Button mode = Button.builder(Component.translatable(dejure ? "crusadecraft.map.mode.dejure" : "crusadecraft.map.mode.political"), b -> {
            dejure = !dejure;
            lastDejure = dejure;
            dirty = true;
            rebuildWidgets();
        }).bounds(bx - 254, top + 5, 100, 16).build();
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.map.mode.tip")));
        addRenderableWidget(mode);
        dirty = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        if (dirty || raster == null) {
            raster = MapRaster.render(view, mapW, mapH, centerX, centerZ, scale, dejure);
            texture.upload(raster);
            dirty = false;
        }
        if (texture.ready()) g.blit(texture.id(), mapX, mapY, 0, 0, mapW, mapH, mapW, mapH);
        g.renderOutline(mapX - 1, mapY - 1, mapW + 2, mapH + 2, Ui.GOLD_DARK);

        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        renderSeats(g);
        renderPlayer(g);
        g.disableScissor();

        // Pied : échelle, coordonnées, mode d'emploi.
        double wx = centerX + (mouseX - mapX - mapW / 2.0) * scale;
        double wz = centerZ + (mouseY - mapY - mapH / 2.0) * scale;
        String foot = Component.translatable("crusadecraft.map.scale", String.format(Locale.ROOT, scale < 10 ? "%.1f" : "%.0f", scale)).getString();
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) foot += "   ·   x " + Math.round(wx) + ", z " + Math.round(wz);
        Ui.text(g, font, Component.literal(foot), mapX, top + h - 18, Ui.TEXT_MUTED);
        // Le mode d'emploi à droite, réduit s'il le faut, jamais par-dessus l'échelle.
        Component hint = Component.translatable("crusadecraft.map.hint");
        int room = mapW - font.width(foot) - 16;
        float k = Math.min(1f, room / (float) Math.max(1, font.width(hint)));
        if (k >= 0.6f) {
            int hw = Math.round(font.width(hint) * k);
            Ui.scaled(g, font, hint, mapX + mapW - hw, top + h - 18 + Math.round((1 - k) * 4), k, Ui.TEXT_MUTED, false);
        }
        if (view.territories().stream().noneMatch(t -> t.ai())) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.map.nothing_known"), mapX + mapW / 2, mapY + 8, mapW - 40, INK);
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) && !dragging) {
            List<Component> tip = tooltip(raster.ownerAt(mouseX - mapX, mouseY - mapY));
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private int screenX(double worldX) {
        return (int) Math.round(mapX + mapW / 2.0 + (worldX - centerX) / scale);
    }

    private int screenY(double worldZ) {
        return (int) Math.round(mapY + mapH / 2.0 + (worldZ - centerZ) / scale);
    }

    private void renderSeats(GuiGraphics g) {
        for (MapView.Seat s : view.seats()) {
            int x = screenX(s.x()), y = screenY(s.z());
            if (x < mapX - 40 || x > mapX + mapW + 40 || y < mapY - 20 || y > mapY + mapH + 20) continue;
            if (s.capital()) {
                g.fill(x - 4, y - 4, x + 4, y + 4, INK);
                g.fill(x - 3, y - 3, x + 3, y + 3, 0xFFD4A017);
                g.fill(x - 1, y - 1, x + 1, y + 1, INK);
            } else {
                g.fill(x - 2, y - 2, x + 2, y + 2, INK);
                g.fill(x - 1, y - 1, x + 1, y + 1, s.village() ? 0xFFEDE3CC : 0xFFB0A080);
            }
            if (s.capital() || scale <= 6) {
                Component name = Component.literal(s.name());
                float k = s.capital() ? 0.85f : 0.7f;
                int tw = Math.round(font.width(name) * k);
                g.pose().pushPose();
                g.pose().translate(x - tw / 2f, y + 6, 0);
                g.pose().scale(k, k, 1);
                g.drawString(font, name, 0, 0, INK, false);
                g.pose().popPose();
            }
        }
    }

    private void renderPlayer(GuiGraphics g) {
        int x = screenX(view.playerX()), y = screenY(view.playerZ());
        g.fill(x - 3, y - 3, x + 3, y + 3, 0xFFFFFFFF);
        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFFB3261E);
    }

    private List<Component> tooltip(int owner) {
        List<Component> tip = new ArrayList<>();
        if (owner == MapRaster.FOGGED) {
            tip.add(Component.translatable("crusadecraft.map.unknown").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("crusadecraft.map.unknown.tip").withStyle(ChatFormatting.DARK_GRAY));
            return tip;
        }
        if (owner < 0 || owner >= view.seats().size()) return tip;
        MapView.Seat s = view.seats().get(owner);
        MapView.Territory t = view.territories().get(s.territory());
        tip.add((t.ai() ? AiNames.realm(t.rank(), t.label()) : Component.literal(t.label())).copy().withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable(t.rank().holderKey(t.rulerFemale())).append(" " + t.ruler()).withStyle(ChatFormatting.WHITE));
        if (t.personality() != null) tip.add(Component.translatable(t.personality().key()).withStyle(ChatFormatting.GRAY));
        Component status = t.mine() ? Component.translatable("crusadecraft.map.status.mine").withStyle(ChatFormatting.GREEN)
                : t.ai() ? Component.translatable("crusadecraft.map.status.neutral").withStyle(ChatFormatting.YELLOW)
                : Component.translatable("crusadecraft.map.status.player").withStyle(ChatFormatting.AQUA);
        tip.add(status);
        Component seat = Component.translatable(s.capital() ? "crusadecraft.map.seat.capital" : "crusadecraft.map.seat", s.name());
        tip.add(seat.copy().withStyle(ChatFormatting.GRAY));
        if (s.dejure() >= 0 && s.dejure() < view.dejure().size()) {
            tip.add(Component.translatable("crusadecraft.map.dejure",
                    AiNames.realm(TitleRank.ROYAUME, view.dejure().get(s.dejure()).place())).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("crusadecraft.map.population", t.population()).withStyle(ChatFormatting.GRAY));
        if (t.ai()) tip.add(Component.translatable("crusadecraft.map.click").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
            dragging = true;
            moved = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) moved = true;
            centerX -= dx * scale;
            centerZ -= dy * scale;
            dirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            if (!moved && raster != null) {
                int owner = raster.ownerAt((int) mouseX - mapX, (int) mouseY - mapY);
                if (owner >= 0 && owner < view.seats().size()) {
                    MapView.Territory t = view.territories().get(view.seats().get(owner).territory());
                    if (t.ai()) CrusadeNetwork.toServer(new WorldPackets.RequestAiRealm(t.id()));
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) return super.mouseScrolled(mouseX, mouseY, delta);
        // Le point sous la souris reste en place pendant le zoom.
        double ox = mouseX - mapX - mapW / 2.0, oz = mouseY - mapY - mapH / 2.0;
        double wx = centerX + ox * scale, wz = centerZ + oz * scale;
        scale = Math.max(0.5, Math.min(64, scale * (delta > 0 ? 0.8 : 1.25)));
        centerX = wx - ox * scale;
        centerZ = wz - oz * scale;
        dirty = true;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        texture.release();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
