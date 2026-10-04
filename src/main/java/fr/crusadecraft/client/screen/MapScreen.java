package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.KeyBindings;
import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.client.map.MapTexture;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.MapEditPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * La carte du royaume (touche virgule) : les royaumes connus, colorés par État (vue politique) ou par
 * royaume de jure, leurs capitales et leurs provinces, et le brouillard sur ce qui reste à découvrir.
 *
 * <p>Pour qui en a le droit, le bouton « Modifier » ouvre l'éditeur (P12) : poser des royaumes et des villes,
 * retoucher ceux qui existent, peindre les frontières au pinceau. L'écran ne fait que demander : le serveur
 * vérifie, répond par un message au pied de la carte, et renvoie la carte à jour.
 */
public class MapScreen extends Screen {
    private static final int INK = 0xFF3A2918;
    private static final int TOOLBAR = 22;
    private static final int PANEL_W = 150;
    private static final int[] BRUSH_SIZES = {0, 1, 2, 4, 8};
    private static boolean lastDejure;
    /** Centre demandé par un autre écran (« Voir sur la carte »), appliqué à la prochaine ouverture. */
    private static int[] pendingCenter;
    private static double pendingScale = -1;

    /** Les outils de l'éditeur. */
    enum Tool {
        CHOOSE("choose"), REALM("realm"), CITY("city"), BRUSH("brush");

        final String key;

        Tool(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable("crusadecraft.mapedit.tool." + key);
        }

        Component tip() {
            return Component.translatable("crusadecraft.mapedit.tool." + key + ".tip");
        }
    }

    /** Un siège retrouvé d'une carte à l'autre : par sa province, sinon par son royaume et son nom (colonie). */
    record SeatKey(UUID territory, int province, String name, UUID site, int x, int z) {}

    private MapView view;
    private boolean dejure = lastDejure;
    private double centerX, centerZ, scale = -1;
    private int left, top, w, h;
    private int mapX, mapY, mapW, mapH;
    private final MapTexture texture = new MapTexture("map");
    private MapRaster.Result raster;
    private boolean dirty = true;
    private boolean dragging, moved, painting;
    private double pressX, pressY, lastPaintX, lastPaintY;

    // Éditeur
    private Tool tool = Tool.CHOOSE;
    private SeatKey selected;
    private boolean moving;
    /** Suppression armée : 1 la ville, 2 le royaume, 3 le monde vierge. Un second clic confirme. */
    private int armed;
    private long armedUntil;
    private int brushChoice = -1, cityOwner = -1, giveTarget = -1;
    private int brushSize = 2;
    private final Long2IntOpenHashMap overlay = new Long2IntOpenHashMap();
    private final LongArrayList stroke = new LongArrayList();
    private Component status;
    private boolean statusOk;
    private long statusUntil;

    public MapScreen(MapView view) {
        super(Component.translatable("crusadecraft.map.title"));
        this.view = view;
        if (pendingCenter != null) {
            centerX = pendingCenter[0];
            centerZ = pendingCenter[1];
            pendingCenter = null;
            scale = pendingScale;
            pendingScale = -1;
        } else {
            centerX = view.focusX();
            centerZ = view.focusZ();
        }
    }

    public static void centerNextOn(int x, int z) {
        centerNextOn(x, z, -1);
    }

    /** La prochaine carte ouverte se centre ici, à cette échelle (blocs par point ; négatif : toute la carte). */
    public static void centerNextOn(int x, int z, double blocksPerPoint) {
        pendingCenter = new int[]{x, z};
        pendingScale = blocksPerPoint;
    }

    /** Bascule en vue de jure (aperçu du banc d'essai visuel). */
    public void toggleDejureForPreview() {
        dejure = !dejure;
        dirty = true;
    }

    /** Choisit l'outil et la sélection (aperçu du banc d'essai visuel). */
    public void previewEditor(int toolIndex, int seatIndex) {
        tool = Tool.values()[Math.floorMod(toolIndex, Tool.values().length)];
        selected = seatIndex >= 0 && seatIndex < view.seats().size() ? keyOf(view.seats().get(seatIndex)) : null;
        if (minecraft != null) rebuildWidgets();
    }

    public void refresh(MapView newView) {
        // Pendant l'édition, une carte ordinaire (Table de guerre) ne remplace pas celle de l'éditeur.
        if (view.editing() && !newView.editing()) return;
        boolean modeChanged = view.editing() != newView.editing();
        this.view = newView;
        overlay.clear();
        dirty = true;
        if (modeChanged || minecraft != null) rebuildWidgets();
    }

    public MapView currentView() {
        return view;
    }

    public void showStatus(boolean success, Component message) {
        status = message;
        statusOk = success;
        statusUntil = Util.getMillis() + 6000;
    }

    private boolean editing() {
        return view.editing();
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
        mapH = h - 26 - 24 - (editing() ? TOOLBAR : 0);
        if (scale < 0) scale = MapRaster.fit(view, centerX, centerZ, mapW, mapH) * 0.6;
        int bx = left + w - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx - 50, top + 5, 50, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.map.center"), b -> {
            centerX = view.focusX();
            centerZ = view.focusZ();
            dirty = true;
        }).bounds(bx - 118, top + 5, 64, 16).build());
        Button mode = Button.builder(Component.translatable(dejure ? "crusadecraft.map.mode.dejure" : "crusadecraft.map.mode.political"), b -> {
            dejure = !dejure;
            lastDejure = dejure;
            dirty = true;
            rebuildWidgets();
        }).bounds(bx - 214, top + 5, 92, 16).build();
        mode.setTooltip(Tooltip.create(Component.translatable("crusadecraft.map.mode.tip")));
        addRenderableWidget(mode);
        if (view.canEdit()) {
            Button edit = Button.builder(Component.translatable(editing() ? "crusadecraft.mapedit.leave" : "crusadecraft.mapedit.enter"), b -> {
                selected = null;
                moving = false;
                armed = 0;
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(false, !editing()));
            }).bounds(bx - 292, top + 5, 74, 16).build();
            edit.setTooltip(Tooltip.create(Component.translatable(editing() ? "crusadecraft.mapedit.leave.tip" : "crusadecraft.mapedit.enter.tip")));
            addRenderableWidget(edit);
        }
        if (editing()) initEditor();
        else {
            Button lieux = Button.builder(Component.translatable("crusadecraft.lieu.open"),
                    b -> CrusadeNetwork.toServer(fr.crusadecraft.network.packet.LieuPackets.Request.of(null)))
                    .bounds(mapX + mapW - 56, top + h - 21, 56, 16).build();
            lieux.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.open.tip")));
            addRenderableWidget(lieux);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ éditeur : outils et panneau

    private void initEditor() {
        int ty = mapY + mapH + 4;
        int x = mapX;
        for (Tool t : Tool.values()) {
            Button b = Button.builder(t == tool ? Component.literal("» ").append(t.label()) : t.label(), btn -> {
                tool = t;
                moving = false;
                armed = 0;
                rebuildWidgets();
            }).bounds(x, ty, 58, 16).build();
            b.setTooltip(Tooltip.create(t.tip()));
            addRenderableWidget(b);
            x += 60;
        }
        int right = mapX + mapW;
        int room = right - x - 4;
        if (tool == Tool.BRUSH) {
            if (brushChoice < 0 || brushChoice > view.territories().size() + 1) brushChoice = defaultTerritory();
            addRenderableWidget(Button.builder(Component.literal("◯ " + BRUSH_SIZES[brushSize]), b -> {
                brushSize = (brushSize + 1) % BRUSH_SIZES.length;
                rebuildWidgets();
            }).bounds(right - 34, ty, 34, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.brush.size.tip"))).build());
            int cw = Math.min(150, room - 38);
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(brushLabel().getString(), cw - 8)), b -> {
                brushChoice = (brushChoice + (hasShiftDown() ? view.territories().size() + 1 : 1)) % (view.territories().size() + 2);
                rebuildWidgets();
            }).bounds(right - 38 - cw, ty, cw, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.brush.tip"))).build());
        } else if (tool == Tool.CITY && !view.territories().isEmpty()) {
            if (cityOwner < 0 || cityOwner >= view.territories().size()) cityOwner = defaultTerritory();
            int cw = Math.min(170, room);
            Component label = Component.translatable("crusadecraft.mapedit.city.for", territoryName(cityOwner));
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label.getString(), cw - 8)), b -> {
                cityOwner = (cityOwner + (hasShiftDown() ? view.territories().size() - 1 : 1)) % view.territories().size();
                rebuildWidgets();
            }).bounds(right - cw, ty, cw, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.city.for.tip"))).build());
        }
        if (tool == Tool.CHOOSE) initPanel();
    }

    /** Le panneau se range du côté opposé à la ville choisie, pour ne pas la cacher. */
    private int panelX() {
        int i = selectedIndex();
        if (i >= 0 && screenX(view.seats().get(i).x()) > mapX + mapW / 2) return mapX + 4;
        return mapX + mapW - PANEL_W - 4;
    }

    /** Hauteur du panneau : l'en-tête, les retouches de la ville (ou le mot sur la colonie), puis le royaume. */
    private int panelHeight() {
        int i = selectedIndex();
        if (i < 0) return Math.min(108, mapH - 8);
        return Math.min(panelRealmTop(view.seats().get(i)) + 14 + 3 * 16 + 4, mapH - 8);
    }

    /** Où commence la partie « royaume » du panneau, relativement à son bord haut. */
    private static int panelRealmTop(MapView.Seat s) {
        if (s.site() != null && s.site().id() != null) return 36 + 12 + 2 * 16;
        return 36 + (s.provinceId() >= 0 ? 3 * 16 : 34);
    }

    /** « Fort de Valcourt » pour une construction, « Poste de garde » pour MineColonies, sinon le nom du siège. */
    static Component seatName(MapView.Seat s) {
        if (s.site() == null) return Component.literal(s.name());
        if (s.site().kind() == MapView.SiteInfo.GUARD_POST) return Component.translatable("crusadecraft.site.guard_post.named", s.name());
        fr.crusadecraft.core.build.SiteKind kind = fr.crusadecraft.core.build.SiteKind.byOrdinal(s.site().kind());
        return Component.translatable(AiNames.elidesPublic(s.name()) ? "crusadecraft.site.named.elided" : "crusadecraft.site.named",
                Component.translatable(kind.key()), s.name());
    }

    private int panelY() {
        return mapY + 4;
    }

    /** Les boutons du panneau de droite : ce qu'on peut faire de la ville et du royaume choisis. */
    private void initPanel() {
        int px = panelX() + 6, bw = PANEL_W - 12;
        int y = panelY() + 6;
        int seatIndex = selectedIndex();
        if (seatIndex < 0) {
            y += 46;
            Button wipe = Button.builder(armedLabel(3, "crusadecraft.mapedit.wipe"), b -> {
                if (confirm(3)) CrusadeNetwork.toServer(new MapEditPackets.Wipe());
                rebuildWidgets();
            }).bounds(px, y, bw, 15).build();
            wipe.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.wipe.tip")));
            addRenderableWidget(wipe);
            Button lineages = Button.builder(Component.translatable("crusadecraft.lineage.open"),
                    b -> CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LineagePackets.Request(null, null))).bounds(px, y + 17, bw, 15).build();
            lineages.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.open.tip")));
            addRenderableWidget(lineages);
            Button lieux = Button.builder(Component.translatable("crusadecraft.lieu.open"),
                    b -> CrusadeNetwork.toServer(fr.crusadecraft.network.packet.LieuPackets.Request.of(null))).bounds(px, y + 34, bw, 15).build();
            lieux.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.open.tip")));
            addRenderableWidget(lieux);
            return;
        }
        MapView.Seat s = view.seats().get(seatIndex);
        int realmY = panelY() + panelRealmTop(s) + 14;
        y += 30;
        if (s.site() != null && s.site().id() != null) {
            // Une construction : la donner à un autre royaume, ou l'effacer du registre (ses pierres restent).
            y += 12;
            if (giveTarget < 0 || giveTarget >= view.territories().size()) giveTarget = s.territory();
            Component give = Component.translatable("crusadecraft.mapedit.city.give_to", shortName(giveTarget));
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(give.getString(), bw - 40)), b -> {
                giveTarget = (giveTarget + (hasShiftDown() ? view.territories().size() - 1 : 1)) % view.territories().size();
                rebuildWidgets();
            }).bounds(px, y, bw - 32, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.city.give.tip"))).build());
            Button ok = Button.builder(Component.translatable("crusadecraft.mapedit.city.give"), b ->
                    CrusadeNetwork.toServer(new MapEditPackets.SiteAction(s.site().id(), false, view.territories().get(giveTarget).id())))
                    .bounds(px + bw - 30, y, 30, 15).build();
            ok.active = giveTarget != s.territory();
            addRenderableWidget(ok);
            y += 16;
            Button delete = Button.builder(armedLabel(1, "crusadecraft.mapedit.site.delete"), b -> {
                if (confirm(1)) {
                    CrusadeNetwork.toServer(new MapEditPackets.SiteAction(s.site().id(), true, null));
                    selected = null;
                }
                rebuildWidgets();
            }).bounds(px, y, bw, 15).build();
            delete.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.site.delete.tip")));
            addRenderableWidget(delete);
        } else if (s.provinceId() >= 0) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.mapedit.city.rename"), b ->
                    minecraft.setScreen(MapEditForm.renameCity(this, s.provinceId(), s.name()))).bounds(px, y, bw / 2 - 1, 15).build());
            Button move = Button.builder(Component.translatable(moving ? "crusadecraft.mapedit.city.moving" : "crusadecraft.mapedit.city.move"), b -> {
                moving = !moving;
                rebuildWidgets();
            }).bounds(px + bw / 2 + 1, y, bw / 2 - 1, 15).build();
            move.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.city.move.tip")));
            addRenderableWidget(move);
            y += 16;
            if (giveTarget < 0 || giveTarget >= view.territories().size()) giveTarget = s.territory();
            Component give = Component.translatable("crusadecraft.mapedit.city.give_to", shortName(giveTarget));
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(give.getString(), bw - 40)), b -> {
                giveTarget = (giveTarget + (hasShiftDown() ? view.territories().size() - 1 : 1)) % view.territories().size();
                rebuildWidgets();
            }).bounds(px, y, bw - 32, 15).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.city.give.tip"))).build());
            Button ok = Button.builder(Component.translatable("crusadecraft.mapedit.city.give"), b ->
                    CrusadeNetwork.toServer(MapEditPackets.CityAction.give(s.provinceId(), view.territories().get(giveTarget).id())))
                    .bounds(px + bw - 30, y, 30, 15).build();
            ok.active = giveTarget != s.territory();
            addRenderableWidget(ok);
            y += 16;
            Button delete = Button.builder(armedLabel(1, "crusadecraft.mapedit.city.delete"), b -> {
                if (confirm(1)) CrusadeNetwork.toServer(MapEditPackets.CityAction.delete(s.provinceId()));
                rebuildWidgets();
            }).bounds(px, y, bw, 15).build();
            if (s.capital()) {
                delete.active = false;
                delete.setTooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.city.delete.capital")));
            }
            addRenderableWidget(delete);
        }
        int ownerIndex = s.provinceId() >= 0 && s.origin() >= 0 && s.origin() < view.territories().size() ? s.origin() : s.territory();
        MapView.Territory owner = view.territories().get(ownerIndex);
        y = realmY;
        // La fiche du lieu choisi : la construction, la ville, ou (pour une colonie) le royaume du joueur.
        fr.crusadecraft.network.packet.LieuPackets.Request sheet = s.site() != null && s.site().id() != null
                ? new fr.crusadecraft.network.packet.LieuPackets.Request(null, fr.crusadecraft.core.place.Lieu.RefType.SITE.ordinal(), s.site().id(), 0)
                : s.provinceId() >= 0
                ? new fr.crusadecraft.network.packet.LieuPackets.Request(null, fr.crusadecraft.core.place.Lieu.RefType.AI_PROVINCE.ordinal(), null, s.provinceId())
                : new fr.crusadecraft.network.packet.LieuPackets.Request(null, fr.crusadecraft.core.place.Lieu.RefType.PLAYER_REALM.ordinal(),
                view.territories().get(s.territory()).id(), 0);
        Button sheetButton = Button.builder(Component.translatable("crusadecraft.lieu.sheet"), b -> CrusadeNetwork.toServer(sheet)).build();
        sheetButton.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lieu.sheet.tip")));
        if (!owner.ai()) {
            sheetButton.setPosition(px, y);
            sheetButton.setWidth(bw);
            sheetButton.setHeight(15);
            addRenderableWidget(sheetButton);
        }
        if (owner.ai()) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.mapedit.realm.edit"), b ->
                    minecraft.setScreen(MapEditForm.editRealm(this, owner))).bounds(px, y, bw, 15).build());
            y += 16;
            addRenderableWidget(Button.builder(armedLabel(2, "crusadecraft.mapedit.realm.delete"), b -> {
                if (confirm(2)) {
                    CrusadeNetwork.toServer(new MapEditPackets.DeleteRealm(owner.id()));
                    selected = null;
                }
                rebuildWidgets();
            }).bounds(px, y, bw, 15).build());
            y += 16;
            Button lineage = Button.builder(Component.translatable("crusadecraft.lineage.of_realm"),
                    b -> CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LineagePackets.Request(null, owner.id()))).bounds(px, y, bw / 2 - 1, 15).build();
            lineage.setTooltip(Tooltip.create(Component.translatable("crusadecraft.lineage.of_realm.tip")));
            addRenderableWidget(lineage);
            sheetButton.setPosition(px + bw / 2 + 1, y);
            sheetButton.setWidth(bw / 2 - 1);
            sheetButton.setHeight(15);
            addRenderableWidget(sheetButton);
        }
    }

    private Component armedLabel(int what, String key) {
        boolean live = armed == what && Util.getMillis() < armedUntil;
        return live ? Component.translatable("crusadecraft.mapedit.confirm").withStyle(ChatFormatting.RED) : Component.translatable(key);
    }

    /** Premier clic : arme ; second clic dans les quatre secondes : confirme. */
    private boolean confirm(int what) {
        if (armed == what && Util.getMillis() < armedUntil) {
            armed = 0;
            return true;
        }
        armed = what;
        armedUntil = Util.getMillis() + 4000;
        return false;
    }

    private int defaultTerritory() {
        for (int i = 0; i < view.territories().size(); i++) if (view.territories().get(i).mine()) return i;
        return 0;
    }

    private Component territoryName(int i) {
        if (i < 0 || i >= view.territories().size()) return Component.literal("?");
        MapView.Territory t = view.territories().get(i);
        return t.ai() ? AiNames.realm(t.rank(), t.label()) : Component.literal(t.label());
    }

    /** Le nom court d'un royaume : son lieu pour un royaume IA (« Rochegarde »), son nom pour un joueur. */
    private Component shortName(int i) {
        if (i < 0 || i >= view.territories().size()) return Component.literal("?");
        return Component.literal(view.territories().get(i).label());
    }

    private Component brushLabel() {
        int n = view.territories().size();
        if (brushChoice == n) return Component.translatable("crusadecraft.mapedit.brush.neutral");
        if (brushChoice == n + 1) return Component.translatable("crusadecraft.mapedit.brush.erase");
        return territoryName(brushChoice);
    }

    private SeatKey keyOf(MapView.Seat s) {
        return new SeatKey(view.territories().get(s.territory()).id(), s.provinceId(), s.name(), s.site() == null ? null : s.site().id(), s.x(), s.z());
    }

    private int selectedIndex() {
        if (selected == null) return -1;
        for (int i = 0; i < view.seats().size(); i++) {
            MapView.Seat s = view.seats().get(i);
            boolean same;
            if (selected.site() != null) same = s.site() != null && selected.site().equals(s.site().id());
            else if (selected.province() >= 0) same = s.provinceId() == selected.province();
            else same = s.provinceId() < 0 && (s.site() == null || s.site().id() == null) && s.x() == selected.x() && s.z() == selected.z()
                    && view.territories().get(s.territory()).id().equals(selected.territory());
            if (same) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        int titleRoom = (left + w - 8 - (view.canEdit() ? 292 : 214)) - (left + 10) - 6;
        float titleScale = Math.max(0.7f, Math.min(1.3f, titleRoom / (float) Math.max(1, font.width(currentTitle()))));
        Ui.scaled(g, font, currentTitle(), left + 10, top + 8 + Math.round((1.3f - titleScale) * 3), titleScale, Ui.TEXT_TITLE, false);
        if (dirty || raster == null) {
            raster = MapRaster.render(view, mapW, mapH, centerX, centerZ, scale, dejure, overlay.isEmpty() ? null : overlay);
            texture.upload(raster);
            dirty = false;
        }
        if (texture.ready()) g.blit(texture.id(), mapX, mapY, 0, 0, mapW, mapH, mapW, mapH);
        g.renderOutline(mapX - 1, mapY - 1, mapW + 2, mapH + 2, Ui.GOLD_DARK);

        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        renderPlaces(g);
        renderSeats(g);
        renderPlayer(g);
        if (editing()) renderBrush(g, mouseX, mouseY);
        g.disableScissor();

        // Pied : échelle, coordonnées, mode d'emploi (ou réponse du serveur).
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        String foot = Component.translatable("crusadecraft.map.scale", String.format(Locale.ROOT, scale < 10 ? "%.1f" : "%.0f", scale)).getString();
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) foot += "   ·   x " + Math.round(wx) + ", z " + Math.round(wz);
        int footY = top + h - 18;
        Ui.text(g, font, Component.literal(foot), mapX, footY, Ui.TEXT_MUTED);
        Component hint = status != null && Util.getMillis() < statusUntil ? status : editing() ? editHint() : Component.translatable("crusadecraft.map.hint");
        int hintColor = status != null && Util.getMillis() < statusUntil ? (statusOk ? 0xFF9FD07A : 0xFFE0796E) : Ui.TEXT_MUTED;
        // Le mode d'emploi à droite, réduit s'il le faut, jamais par-dessus l'échelle.
        // Hors de l'éditeur, le bouton « Lieux » tient le coin droit du pied.
        int reserved = editing() ? 0 : 60;
        int room = mapW - font.width(foot) - 16 - reserved;
        float k = Math.min(1f, room / (float) Math.max(1, font.width(hint)));
        if (k >= 0.55f) {
            int hw = Math.round(font.width(hint) * k);
            Ui.scaled(g, font, hint, mapX + mapW - reserved - hw, footY + Math.round((1 - k) * 4), k, hintColor, false);
        }
        if (!editing() && view.territories().stream().noneMatch(t -> t.ai())) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.map.nothing_known"), mapX + mapW / 2, mapY + 8, mapW - 40, INK);
        }
        if (editing() && tool == Tool.CHOOSE) renderPanel(g);

        super.render(g, mouseX, mouseY, partialTick);
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) && !dragging && !painting && !overPanel(mouseX, mouseY)) {
            List<Component> tip = tooltip(raster.ownerAt(mouseX - mapX, mouseY - mapY));
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private Component currentTitle() {
        return editing() ? Component.translatable("crusadecraft.mapedit.title") : title;
    }

    private Component editHint() {
        if (moving) return Component.translatable("crusadecraft.mapedit.hint.move");
        return Component.translatable("crusadecraft.mapedit.hint." + tool.key);
    }

    private void renderPanel(GuiGraphics g) {
        int px = panelX(), py = panelY();
        int seatIndex = selectedIndex();
        Ui.panel(g, px, py, PANEL_W, panelHeight());
        int tx = px + 6, tw = PANEL_W - 12;
        if (seatIndex < 0) {
            Ui.text(g, font, Component.translatable("crusadecraft.mapedit.panel.empty.title"), tx, py + 6, Ui.TEXT_TITLE);
            Ui.wrapped(g, font, Component.translatable("crusadecraft.mapedit.panel.empty.body"), tx, py + 18, tw, Ui.TEXT_MUTED);
            return;
        }
        MapView.Seat s = view.seats().get(seatIndex);
        MapView.Territory t = view.territories().get(s.territory());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(seatName(s).getString(), tw)), tx, py + 6, Ui.TEXT_TITLE);
        String kind = s.site() != null ? (s.site().kind() == MapView.SiteInfo.GUARD_POST ? "crusadecraft.mapedit.panel.guard_post"
                : "crusadecraft.mapedit.panel.site") : s.provinceId() < 0 ? "crusadecraft.mapedit.panel.colony" : s.capital()
                ? "crusadecraft.mapedit.panel.capital" : "crusadecraft.mapedit.panel.city";
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable(kind).getString(), tw)), tx, py + 16, Ui.TEXT_MUTED);
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable("crusadecraft.mapedit.panel.held",
                shortName(s.territory())).getString(), tw)), tx, py + 26, Ui.TEXT);
        if (s.site() != null && s.site().id() != null) {
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(siteState(s).getString(), tw)), tx, py + 36, Ui.TEXT_MUTED);
        } else if (s.site() != null) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.mapedit.panel.guard_post.body"), tx, py + 38, tw, Ui.TEXT_MUTED);
        } else if (s.provinceId() < 0) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.mapedit.panel.colony.body"), tx, py + 38, tw, Ui.TEXT_MUTED);
        }
        int ownerIndex = s.provinceId() >= 0 && s.origin() >= 0 && s.origin() < view.territories().size() ? s.origin() : s.territory();
        int ly = py + panelRealmTop(s) + 3;
        if (s.site() != null && s.site().kind() == MapView.SiteInfo.GUARD_POST) return;
        MapView.Territory owner = view.territories().get(ownerIndex);
        if (owner.ai()) {
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable("crusadecraft.mapedit.panel.realm",
                    shortName(ownerIndex)).getString(), tw)), tx, ly, Ui.GOLD);
        } else if (t.mine() && s.provinceId() < 0) {
            Ui.text(g, font, Component.translatable("crusadecraft.mapedit.panel.yours"), tx, ly, Ui.GOLD);
        }
    }

    private boolean overPanel(double mx, double my) {
        if (!editing() || tool != Tool.CHOOSE) return false;
        return Ui.inside(mx, my, panelX(), panelY(), PANEL_W, panelHeight());
    }

    private double worldX(double mouseX) {
        return centerX + (mouseX - mapX - mapW / 2.0) * scale;
    }

    private double worldZ(double mouseY) {
        return centerZ + (mouseY - mapY - mapH / 2.0) * scale;
    }

    private int screenX(double worldX) {
        return (int) Math.round(mapX + mapW / 2.0 + (worldX - centerX) / scale);
    }

    private int screenY(double worldZ) {
        return (int) Math.round(mapY + mapH / 2.0 + (worldZ - centerZ) / scale);
    }

    private void renderSeats(GuiGraphics g) {
        int chosen = editing() ? selectedIndex() : -1;
        for (int i = 0; i < view.seats().size(); i++) {
            MapView.Seat s = view.seats().get(i);
            int x = screenX(s.x()), y = screenY(s.z());
            if (x < mapX - 40 || x > mapX + mapW + 40 || y < mapY - 20 || y > mapY + mapH + 20) continue;
            if (i == chosen) g.renderOutline(x - 7, y - 7, 14, 14, 0xFFFFFFFF);
            if (s.site() != null) {
                siteIcon(g, s, x, y);
                if (scale <= 6 || i == chosen) label(g, seatName(s), x, y, 0.65f);
                continue;
            }
            if (s.capital()) {
                g.fill(x - 4, y - 4, x + 4, y + 4, INK);
                g.fill(x - 3, y - 3, x + 3, y + 3, 0xFFD4A017);
                g.fill(x - 1, y - 1, x + 1, y + 1, INK);
            } else {
                g.fill(x - 2, y - 2, x + 2, y + 2, INK);
                g.fill(x - 1, y - 1, x + 1, y + 1, s.village() ? 0xFFEDE3CC : 0xFFB0A080);
            }
            if (s.capital() || scale <= 6 || i == chosen) {
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

    /** Les lieux posés à la main (P15) : un lieu saint, une ruine, une région et son cercle pointillé. */
    private void renderPlaces(GuiGraphics g) {
        for (MapView.Place p : view.places()) {
            int x = screenX(p.x()), y = screenY(p.z());
            int r = p.radius() > 0 ? (int) Math.round(p.radius() / scale) : 0;
            if (x + r < mapX - 40 || x - r > mapX + mapW + 40 || y + r < mapY - 20 || y - r > mapY + mapH + 20) continue;
            int color = 0xFF000000 | p.color();
            if (r > 3) {
                int steps = Math.max(16, Math.min(240, r));
                for (int i = 0; i < steps; i += 2) {
                    double a = i * Math.PI * 2 / steps;
                    int cx = x + (int) Math.round(Math.cos(a) * r), cy = y + (int) Math.round(Math.sin(a) * r);
                    g.fill(cx, cy, cx + 1, cy + 1, 0xC0000000 | (INK & 0xFFFFFF));
                }
            }
            String icon = String.valueOf(fr.crusadecraft.core.place.LieuKind.byOrdinal(p.kind()).icon());
            int iw = font.width(icon);
            g.drawString(font, icon, x - iw / 2 + 1, y - 3, INK, false);
            g.drawString(font, icon, x - iw / 2, y - 4, color, false);
            if (scale <= 8 || r > 20) label(g, p.name(), x, y, 0.65f);
        }
    }

    /** Le lieu posé à la main sous la souris, à six points près. */
    private MapView.Place placeNear(double mouseX, double mouseY) {
        MapView.Place best = null;
        double bestD = 36;
        for (MapView.Place p : view.places()) {
            double d = Math.pow(screenX(p.x()) - mouseX, 2) + Math.pow(screenY(p.z()) - mouseY, 2);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void label(GuiGraphics g, Component name, int x, int y, float k) {
        int tw = Math.round(font.width(name) * k);
        g.pose().pushPose();
        g.pose().translate(x - tw / 2f, y + 6, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, name, 0, 0, INK, false);
        g.pose().popPose();
    }

    /** L'avancement d'une construction : « Chantier : 45 % » ou « Bâtie · garnison 5 ». */
    private static Component siteState(MapView.Seat s) {
        if (s.site().percent() < 100) return Component.translatable("crusadecraft.site.state.building", s.site().percent());
        return s.site().garrison() > 0 ? Component.translatable("crusadecraft.site.state.garrison", s.site().garrison())
                : Component.translatable("crusadecraft.site.state.built");
    }

    /** Une icône par nature de construction, aux couleurs de son royaume ; un chantier est pâle. */
    private void siteIcon(GuiGraphics g, MapView.Seat s, int x, int y) {
        int color = 0xFF000000 | view.territories().get(s.territory()).color();
        if (s.site().percent() < 100) color = (color & 0x00FFFFFF) | 0x88000000;
        int ink = s.site().percent() < 100 ? 0x88000000 | (INK & 0xFFFFFF) : INK;
        int kind = s.site().kind();
        if (kind == MapView.SiteInfo.GUARD_POST || kind == fr.crusadecraft.core.build.SiteKind.TOUR.ordinal()) {
            g.fill(x - 2, y - 4, x + 2, y + 3, ink);
            g.fill(x - 1, y - 3, x + 1, y + 2, color);
            g.fill(x - 2, y - 5, x - 1, y - 4, ink);
            g.fill(x + 1, y - 5, x + 2, y - 4, ink);
            return;
        }
        switch (fr.crusadecraft.core.build.SiteKind.byOrdinal(kind)) {
            case BORNE -> {
                g.fill(x - 1, y - 3, x + 1, y + 2, ink);
                g.fill(x, y - 4, x + 2, y - 2, color);
            }
            case CAMP -> {
                for (int i = 0; i < 4; i++) g.fill(x - i, y - 3 + i, x + i + 1, y - 2 + i, ink);
                for (int i = 1; i < 3; i++) g.fill(x - i + 1, y - 2 + i, x + i, y - 1 + i, color);
            }
            case AVANT_POSTE -> {
                for (int i = 0; i < 4; i++) {
                    g.fill(x - i, y - 3 + i, x + i + 1, y - 2 + i, ink);
                    g.fill(x - i, y + 3 - i, x + i + 1, y + 4 - i, ink);
                }
                g.fill(x - 1, y - 1, x + 2, y + 2, color);
            }
            case FORT -> {
                g.fill(x - 4, y - 4, x + 4, y + 4, ink);
                g.fill(x - 3, y - 3, x + 3, y + 3, color);
                g.fill(x - 1, y - 1, x + 1, y + 1, ink);
            }
            case DONJON -> {
                g.fill(x - 3, y - 5, x + 3, y + 4, ink);
                g.fill(x - 2, y - 4, x + 2, y + 3, 0xFFD4A017);
                g.fill(x - 1, y - 2, x + 1, y + 1, color);
            }
            case VILLAGE -> {
                g.fill(x - 3, y - 1, x + 3, y + 3, ink);
                g.fill(x - 2, y, x + 2, y + 2, 0xFFEDE3CC);
                for (int i = 0; i < 3; i++) g.fill(x - i - 1, y - 3 + i, x + i + 1, y - 2 + i, color);
            }
            default -> g.fill(x - 2, y - 2, x + 2, y + 2, ink);
        }
    }

    private void renderPlayer(GuiGraphics g) {
        int x = screenX(view.playerX()), y = screenY(view.playerZ());
        g.fill(x - 3, y - 3, x + 3, y + 3, 0xFFFFFFFF);
        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFFB3261E);
    }

    /** Le cercle du pinceau sous la souris, à la taille des cases qu'il peindra. */
    private void renderBrush(GuiGraphics g, int mouseX, int mouseY) {
        if (tool != Tool.BRUSH || !Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) return;
        int r = Math.max(2, (int) Math.round((BRUSH_SIZES[brushSize] + 0.5) * TerritoryMap.CELL / scale));
        g.renderOutline(mouseX - r, mouseY - r, 2 * r, 2 * r, 0xCCFFFFFF);
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
        Component relation = t.mine() ? Component.translatable("crusadecraft.map.status.mine").withStyle(ChatFormatting.GREEN)
                : t.ai() ? Component.translatable("crusadecraft.map.status.neutral").withStyle(ChatFormatting.YELLOW)
                : Component.translatable("crusadecraft.map.status.player").withStyle(ChatFormatting.AQUA);
        tip.add(relation);
        if (s.site() != null) {
            tip.add(seatName(s).copy().withStyle(ChatFormatting.WHITE));
            tip.add(s.site().kind() == MapView.SiteInfo.GUARD_POST
                    ? Component.translatable("crusadecraft.site.guard_post.level", s.site().garrison()).withStyle(ChatFormatting.GRAY)
                    : siteState(s).copy().withStyle(ChatFormatting.GRAY));
            if (editing()) tip.add(Component.translatable("crusadecraft.mapedit.click").withStyle(ChatFormatting.DARK_GRAY));
            return tip;
        }
        Component seat = Component.translatable(s.capital() ? "crusadecraft.map.seat.capital" : "crusadecraft.map.seat", s.name());
        tip.add(seat.copy().withStyle(ChatFormatting.GRAY));
        if (s.dejure() >= 0 && s.dejure() < view.dejure().size()) {
            tip.add(Component.translatable("crusadecraft.map.dejure",
                    AiNames.realm(TitleRank.ROYAUME, view.dejure().get(s.dejure()).place())).withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.translatable("crusadecraft.map.population", t.population()).withStyle(ChatFormatting.GRAY));
        if (editing()) tip.add(Component.translatable("crusadecraft.mapedit.click").withStyle(ChatFormatting.DARK_GRAY));
        else if (t.ai()) tip.add(Component.translatable("crusadecraft.map.click").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    // ------------------------------------------------------------------ souris

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (!Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) || overPanel(mouseX, mouseY)) return false;
        if (editing() && tool == Tool.BRUSH && button == 0) {
            painting = true;
            stroke.clear();
            lastPaintX = mouseX;
            lastPaintY = mouseY;
            paintAt(mouseX, mouseY);
            return true;
        }
        if (button == 0 || button == 1) {
            dragging = true;
            moved = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (painting) {
            // Entre deux positions de la souris, on peint tout le trajet : un geste rapide ne laisse pas de trous.
            double dist = Math.hypot(mouseX - lastPaintX, mouseY - lastPaintY);
            double step = Math.max(1, TerritoryMap.CELL / scale / 2);
            int n = (int) Math.ceil(dist / step);
            for (int i = 1; i <= n; i++) {
                double t = i / (double) n;
                paintAt(lastPaintX + (mouseX - lastPaintX) * t, lastPaintY + (mouseY - lastPaintY) * t);
            }
            lastPaintX = mouseX;
            lastPaintY = mouseY;
            return true;
        }
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
        if (painting) {
            painting = false;
            sendStroke();
            return true;
        }
        if (dragging) {
            dragging = false;
            if (!moved && button == 0 && raster != null) clickMap(mouseX, mouseY);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void clickMap(double mouseX, double mouseY) {
        int x = (int) Math.round(worldX(mouseX)), z = (int) Math.round(worldZ(mouseY));
        // Un lieu posé à la main : sa fiche.
        if (!editing() || tool == Tool.CHOOSE) {
            MapView.Place place = placeNear(mouseX, mouseY);
            if (place != null) {
                CrusadeNetwork.toServer(fr.crusadecraft.network.packet.LieuPackets.Request.of(place.id()));
                return;
            }
        }
        if (!editing()) {
            int owner = raster.ownerAt((int) mouseX - mapX, (int) mouseY - mapY);
            if (owner >= 0 && owner < view.seats().size()) {
                MapView.Territory t = view.territories().get(view.seats().get(owner).territory());
                if (t.ai()) CrusadeNetwork.toServer(new WorldPackets.RequestAiRealm(t.id()));
            }
            return;
        }
        switch (tool) {
            case REALM -> minecraft.setScreen(MapEditForm.newRealm(this, x, z, usedColors()));
            case CITY -> {
                if (cityOwner >= 0 && cityOwner < view.territories().size()) {
                    minecraft.setScreen(MapEditForm.newCity(this, view.territories().get(cityOwner), territoryName(cityOwner), x, z));
                }
            }
            case CHOOSE -> {
                int index = selectedIndex();
                if (moving && index >= 0) {
                    CrusadeNetwork.toServer(MapEditPackets.CityAction.move(view.seats().get(index).provinceId(), x, z));
                    moving = false;
                } else {
                    int seat = seatNear(mouseX, mouseY);
                    selected = seat < 0 ? null : keyOf(view.seats().get(seat));
                    giveTarget = seat < 0 ? -1 : view.seats().get(seat).territory();
                    armed = 0;
                }
                rebuildWidgets();
            }
            default -> {}
        }
    }

    /** Le siège sous la souris : le plus proche à huit points près, sinon celui qui tient le pixel cliqué. */
    private int seatNear(double mouseX, double mouseY) {
        int best = -1;
        double bestD = 64;
        for (int i = 0; i < view.seats().size(); i++) {
            MapView.Seat s = view.seats().get(i);
            double d = Math.pow(screenX(s.x()) - mouseX, 2) + Math.pow(screenY(s.z()) - mouseY, 2);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best >= 0) return best;
        int owner = raster.ownerAt((int) mouseX - mapX, (int) mouseY - mapY);
        return owner >= 0 && owner < view.seats().size() ? owner : -1;
    }

    private List<Integer> usedColors() {
        List<Integer> out = new ArrayList<>();
        for (MapView.Territory t : view.territories()) out.add(t.color());
        return out;
    }

    private void paintAt(double mouseX, double mouseY) {
        int n = view.territories().size();
        int value = brushChoice == n ? MapView.Borders.NEUTRAL : brushChoice == n + 1 ? MapRaster.ERASED : brushChoice;
        int cx = TerritoryMap.cellOf(worldX(mouseX)), cz = TerritoryMap.cellOf(worldZ(mouseY));
        int r = BRUSH_SIZES[brushSize];
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) continue;
                long key = TerritoryMap.key(cx + dx, cz + dz);
                if (overlay.containsKey(key) && overlay.get(key) == value) continue;
                overlay.put(key, value);
                stroke.add(key);
            }
        }
        dirty = true;
    }

    /** Le geste terminé part au serveur, en paquets de taille raisonnable. */
    private void sendStroke() {
        if (stroke.isEmpty()) return;
        int n = view.territories().size();
        UUID owner = brushChoice >= 0 && brushChoice < n ? view.territories().get(brushChoice).id() : null;
        boolean neutral = brushChoice == n;
        long[] all = stroke.toLongArray();
        for (int from = 0; from < all.length; from += WorldEditService.MAX_CELLS) {
            long[] part = java.util.Arrays.copyOfRange(all, from, Math.min(all.length, from + WorldEditService.MAX_CELLS));
            CrusadeNetwork.toServer(new MapEditPackets.Paint(owner, neutral, part));
        }
        stroke.clear();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) || overPanel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
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
