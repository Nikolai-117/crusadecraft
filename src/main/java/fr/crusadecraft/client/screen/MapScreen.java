package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.KeyBindings;
import fr.crusadecraft.client.map.ClientTerrain;
import fr.crusadecraft.client.map.MapRaster;
import fr.crusadecraft.client.map.MapTexture;
import com.mojang.math.Axis;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.war.WarMap;
import fr.crusadecraft.core.war.WarMapService;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.MapView;
import fr.crusadecraft.core.world.TerritoryMap;
import fr.crusadecraft.core.world.WebMapGeometry;
import fr.crusadecraft.core.world.WorldEditService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.MapEditPackets;
import fr.crusadecraft.network.packet.WarMapPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
 *
 * <p>Le bouton « Carte de guerre » (P18) pose par-dessus le plan de bataille de son royaume et de ses alliés : zones
 * peintes chunk par chunk, flèches d'attaque, repères annotés, et les sièges en cours. Le souverain le trace ici même.
 */
public class MapScreen extends Screen {
    private static final int INK = 0xFF3A2918;
    private static final int TOOLBAR = 22;
    private static final int PANEL_W = 150;
    private static final int[] BRUSH_SIZES = {0, 1, 2, 4, 8};
    private static MapRaster.Mode lastMode = MapRaster.Mode.POLITICAL;
    /** Centre demandé par un autre écran (« Voir sur la carte »), appliqué à la prochaine ouverture. */
    private static int[] pendingCenter;
    private static double pendingScale = -1;
    private static boolean lastWar;
    private static int lastPinKind;
    /** Les couleurs des zones de guerre : offensive, défense, objectif (les mêmes sur Dynmap). */
    static final int[] ZONE_COLORS = {0xD03A2F, 0x2F6FD0, 0xE0B020};
    private static final int[] PIN_COLORS = {0xD03A2F, 0x2F6FD0, 0xE08A20, 0xEDE3CC};

    /** Les outils de la carte de guerre : trois pinceaux de zone, la gomme, la flèche, le repère, et retirer. */
    enum WarTool {
        OFFENSIVE("offensive", 0, "■"), DEFENSE("defense", 1, "■"), OBJECTIVE("objective", 2, "■"), ERASE("erase", -1, "✗"),
        ARROW("arrow", -2, "➜"), PIN("pin", -3, "⚑"), REMOVE("remove", -4, "✂");

        final String key;
        /** Le rang de la zone peinte, −1 pour la gomme ; en dessous, un outil qui ne peint pas. */
        final int zone;
        /** Ce qui reste sur le bouton quand la fenêtre est trop étroite pour les noms. */
        final String glyph;

        WarTool(String key, int zone, String glyph) {
            this.key = key;
            this.zone = zone;
            this.glyph = glyph;
        }

        boolean paints() {
            return zone >= -1;
        }

        /** Son nom, à la couleur de sa zone pour un pinceau. */
        Component label() {
            MutableComponent name = Component.translatable("crusadecraft.warmap.tool." + key);
            if (zone < 0) return name;
            int c = ZONE_COLORS[zone];
            return name.withStyle(s -> s.withColor(c));
        }

        Component compact() {
            MutableComponent g = Component.literal(glyph);
            if (zone < 0) return g;
            int c = ZONE_COLORS[zone];
            return g.withStyle(s -> s.withColor(c));
        }

        Component tip() {
            return Component.translatable("crusadecraft.warmap.tool." + key + ".tip");
        }
    }

    /** Une couche de la carte de guerre prête à dessiner : des rectangles de chunks et leur bord. */
    private record WarLayer(int fill, int line, List<WebMapGeometry.Rect> rects, List<WebMapGeometry.Segment> edges) {}

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
    private MapRaster.Mode mode = lastMode;
    private double centerX, centerZ, scale = -1;
    private int left, top, w, h;
    private int mapX, mapY, mapW, mapH;
    private final MapTexture texture = new MapTexture("map");
    private MapRaster.Result raster;
    private boolean dirty = true;
    // Ce que montre l'image en mémoire : pendant un zoom ou un glissement, elle est seulement étirée ou décalée, et
    // recalculée une fois le geste fini.
    private double rasterX, rasterZ, rasterScale = -1;
    private double targetScale = -1, anchorWX, anchorWZ, anchorSX, anchorSY;
    private long lastMove, lastRaster;
    private int terrainVersion = -1;
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

    // Carte de guerre
    private boolean war = lastWar;
    private WarTool warTool = WarTool.OFFENSIVE;
    /** Ce que le pinceau vient de peindre, en attendant la réponse du serveur : chunk → rang de zone, ou −1 effacé. */
    private final Long2IntOpenHashMap warOverlay = new Long2IntOpenHashMap();
    private List<WarLayer> warLayers;
    /** Le bouton de l'outil choisi, cerné d'or. */
    private Button warToolButton;
    private boolean arrowing;
    private double arrowX0, arrowZ0, arrowX1, arrowZ1;

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

    /** La prochaine carte ouverte pose la carte de guerre (depuis la Table de guerre). */
    public static void openNextAtWar() {
        lastWar = true;
    }

    /** Bascule en vue de jure (aperçu du banc d'essai visuel). */
    public void toggleDejureForPreview() {
        mode = mode == MapRaster.Mode.DEJURE ? MapRaster.Mode.POLITICAL : MapRaster.Mode.DEJURE;
        dirty = true;
    }

    /** Choisit la vue : politique, de jure ou terrain (aperçu du banc d'essai visuel). */
    public void previewMode(int index) {
        mode = MapRaster.Mode.values()[Math.floorMod(index, MapRaster.Mode.values().length)];
        dirty = true;
        if (minecraft != null) rebuildWidgets();
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
        warOverlay.clear();
        warLayers = null;
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

    /** La carte de guerre est posée : hors de l'éditeur, et seulement pour qui a un royaume. */
    private boolean warMode() {
        return war && !editing() && view.war().enabled();
    }

    /** Le glyphe d'un repère, sur la carte et dans son formulaire. */
    static String pinGlyph(WarMap.Mark mark) {
        return switch (mark) {
            case OBJECTIF -> "⚑";
            case RASSEMBLEMENT -> "✦";
            case DANGER -> "⚠";
            case NOTE -> "✎";
        };
    }

    void rememberPinKind(int kind) {
        lastPinKind = kind;
    }

    /** Ouvre la carte de guerre, avec cet outil (aperçu du banc d'essai visuel). */
    public void previewWar(int toolIndex) {
        war = true;
        warTool = WarTool.values()[Math.floorMod(toolIndex, WarTool.values().length)];
        if (minecraft != null) rebuildWidgets();
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
        mapH = h - 26 - 24 - (editing() || warMode() ? TOOLBAR : 0);
        if (scale < 0) scale = MapRaster.fit(view, centerX, centerZ, mapW, mapH) * 0.6;
        targetScale = scale;
        int bx = left + w - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(bx - 50, top + 5, 50, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.map.center"), b -> {
            centerX = view.focusX();
            centerZ = view.focusZ();
            dirty = true;
        }).bounds(bx - 118, top + 5, 64, 16).build());
        Button modeButton = Button.builder(Component.translatable("crusadecraft.map.mode." + mode.name().toLowerCase(Locale.ROOT)), b -> {
            mode = MapRaster.Mode.values()[(mode.ordinal() + 1) % MapRaster.Mode.values().length];
            lastMode = mode;
            dirty = true;
            rebuildWidgets();
        }).bounds(bx - 214, top + 5, 92, 16).build();
        modeButton.setTooltip(Tooltip.create(Component.translatable("crusadecraft.map.mode.tip")));
        addRenderableWidget(modeButton);
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
            if (view.war().enabled()) {
                Button warButton = Button.builder(Component.translatable(warMode() ? "crusadecraft.warmap.leave" : "crusadecraft.warmap.open"), b -> {
                    war = !warMode();
                    lastWar = war;
                    arrowing = false;
                    warOverlay.clear();
                    armed = 0;
                    rebuildWidgets();
                }).bounds(mapX + mapW - 56 - 4 - 84, top + h - 21, 84, 16).build();
                warButton.setTooltip(Tooltip.create(Component.translatable(warMode() ? "crusadecraft.warmap.leave.tip" : "crusadecraft.warmap.open.tip")));
                addRenderableWidget(warButton);
            }
            if (warMode()) initWar();
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ carte de guerre : outils

    private void initWar() {
        int ty = mapY + mapH + 4;
        int right = mapX + mapW;
        int sideW = 34 + 2 + 76;
        int bw = Math.max(18, Math.min(64, (mapW - sideW - 4) / WarTool.values().length - 2));
        // Trop étroit pour les noms : un pictogramme, et le nom dans l'infobulle.
        boolean compact = false;
        for (WarTool t : WarTool.values()) if (font.width(t.label()) + 8 > bw) compact = true;
        warToolButton = null;
        int x = mapX;
        for (WarTool t : WarTool.values()) {
            Button b = Button.builder(compact ? t.compact() : t.label(), btn -> {
                warTool = t;
                arrowing = false;
                armed = 0;
                rebuildWidgets();
            }).bounds(x, ty, bw, 16).build();
            b.setTooltip(Tooltip.create(compact ? Component.empty().append(t.label()).append("\n").append(t.tip()) : t.tip()));
            addRenderableWidget(b);
            if (t == warTool) warToolButton = b;
            x += bw + 2;
        }
        Button clear = Button.builder(armedLabel(4, "crusadecraft.warmap.clear"), b -> {
            if (confirm(4)) CrusadeNetwork.toServer(WarMapPackets.Edit.clear());
            rebuildWidgets();
        }).bounds(right - 76, ty, 76, 16).build();
        clear.setTooltip(Tooltip.create(Component.translatable("crusadecraft.warmap.clear.tip")));
        addRenderableWidget(clear);
        if (warTool.paints()) {
            addRenderableWidget(Button.builder(Component.literal("◯ " + BRUSH_SIZES[brushSize]), b -> {
                brushSize = (brushSize + 1) % BRUSH_SIZES.length;
                rebuildWidgets();
            }).bounds(right - 76 - 36, ty, 34, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.mapedit.brush.size.tip"))).build());
        }
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
        animateZoom();
        long now = Util.getMillis();
        // De nouvelles tuiles de terrain : on se redessine, mais pas plus de quatre fois par seconde.
        if (ClientTerrain.version() != terrainVersion && now - lastRaster > 250) dirty = true;
        boolean moved = centerX != rasterX || centerZ != rasterZ || scale != rasterScale;
        boolean settling = targetScale > 0 && Math.abs(targetScale - scale) > 1e-6;
        if (dirty || raster == null || (moved && !settling && now - lastMove > 120)) {
            int lod = ClientTerrain.lodFor(scale);
            ClientTerrain.ensure(view.dimension(), lod, worldX(mapX), worldZ(mapY), worldX(mapX + mapW), worldZ(mapY + mapH));
            terrainVersion = ClientTerrain.version();
            raster = MapRaster.render(view, mapW, mapH, centerX, centerZ, scale, mode, overlay.isEmpty() ? null : overlay, extras(lod));
            texture.upload(raster);
            rasterX = centerX;
            rasterZ = centerZ;
            rasterScale = scale;
            lastRaster = now;
            dirty = false;
        }
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF000000 | MapRaster.PARCHMENT);
        if (texture.ready()) {
            // L'image calculée pour un autre cadrage, étirée et décalée jusqu'au cadrage présent.
            double k = rasterScale / scale;
            int x0 = (int) Math.round(screenX(rasterX - mapW / 2.0 * rasterScale)), y0 = (int) Math.round(screenY(rasterZ - mapH / 2.0 * rasterScale));
            g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
            g.blit(texture.id(), x0, y0, (int) Math.round(mapW * k), (int) Math.round(mapH * k), 0, 0, mapW, mapH, mapW, mapH);
            g.disableScissor();
        }
        g.renderOutline(mapX - 1, mapY - 1, mapW + 2, mapH + 2, Ui.GOLD_DARK);

        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        if (warMode()) renderWarZones(g);
        if (!editing()) renderTrade(g);
        renderPlaces(g);
        renderSeats(g);
        if (warMode()) renderWarMarks(g);
        renderPlayer(g);
        if (editing() || warMode()) renderBrush(g, mouseX, mouseY);
        g.disableScissor();

        // Pied : échelle, coordonnées, mode d'emploi (ou réponse du serveur).
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        String foot = Component.translatable("crusadecraft.map.scale", String.format(Locale.ROOT, scale < 10 ? "%.1f" : "%.0f", scale)).getString();
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) foot += "   ·   x " + Math.round(wx) + ", z " + Math.round(wz);
        int footY = top + h - 18;
        Ui.text(g, font, Component.literal(foot), mapX, footY, Ui.TEXT_MUTED);
        Component hint = status != null && Util.getMillis() < statusUntil ? status : editing() ? editHint()
                : warMode() ? Component.translatable("crusadecraft.warmap.hint." + warTool.key) : Component.translatable("crusadecraft.map.hint");
        int hintColor = status != null && Util.getMillis() < statusUntil ? (statusOk ? 0xFF9FD07A : 0xFFE0796E) : Ui.TEXT_MUTED;
        // Le mode d'emploi à droite, réduit s'il le faut, jamais par-dessus l'échelle.
        // Hors de l'éditeur, le bouton « Lieux » tient le coin droit du pied.
        int reserved = editing() ? 0 : view.war().enabled() ? 60 + 88 : 60;
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
        if (warMode() && warToolButton != null) {
            g.renderOutline(warToolButton.getX() - 1, warToolButton.getY() - 1, warToolButton.getWidth() + 2, warToolButton.getHeight() + 2, Ui.TEXT_TITLE);
        }
        if (Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) && !dragging && !painting && !arrowing && !overPanel(mouseX, mouseY)) {
            List<Component> tip = warMode() ? warTooltip(mouseX, mouseY) : List.of();
            if (tip.isEmpty() && !editing()) tip = tradeTooltip(mouseX, mouseY);
            if (tip.isEmpty()) tip = tooltip(rasterOwner(mouseX, mouseY));
            if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private Component currentTitle() {
        return editing() ? Component.translatable("crusadecraft.mapedit.title") : warMode() ? Component.translatable("crusadecraft.warmap.title") : title;
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
        boolean brush = editing() ? tool == Tool.BRUSH : warMode() && warTool.paints();
        if (!brush || !Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH)) return;
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
        if (warMode() && button == 0 && warTool == WarTool.ARROW) {
            arrowing = true;
            arrowX0 = arrowX1 = worldX(mouseX);
            arrowZ0 = arrowZ1 = worldZ(mouseY);
            return true;
        }
        if (((editing() && tool == Tool.BRUSH) || (warMode() && warTool.paints())) && button == 0) {
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
        if (arrowing) {
            arrowX1 = worldX(Math.max(mapX, Math.min(mapX + mapW, mouseX)));
            arrowZ1 = worldZ(Math.max(mapY, Math.min(mapY + mapH, mouseY)));
            return true;
        }
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
            lastMove = Util.getMillis();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (arrowing) {
            arrowing = false;
            // Un simple clic n'est pas une flèche : il faut l'avoir tirée.
            if (Math.hypot(arrowX1 - arrowX0, arrowZ1 - arrowZ0) / scale >= 6) {
                CrusadeNetwork.toServer(WarMapPackets.Edit.arrow((int) Math.round(arrowX0), (int) Math.round(arrowZ0),
                        (int) Math.round(arrowX1), (int) Math.round(arrowZ1)));
            } else {
                showStatus(false, Component.translatable("crusadecraft.warmap.arrow.drag"));
            }
            return true;
        }
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
        if (warMode() && warTool == WarTool.PIN) {
            minecraft.setScreen(new WarPinForm(this, x, z, lastPinKind));
            return;
        }
        if (warMode() && warTool == WarTool.REMOVE) {
            UUID target = ownMarkNear(mouseX, mouseY);
            if (target != null) CrusadeNetwork.toServer(WarMapPackets.Edit.remove(target));
            else showStatus(false, Component.translatable("crusadecraft.warmap.remove.none"));
            return;
        }
        // Un lieu posé à la main : sa fiche.
        if (!editing() || tool == Tool.CHOOSE) {
            MapView.Place place = placeNear(mouseX, mouseY);
            if (place != null) {
                CrusadeNetwork.toServer(fr.crusadecraft.network.packet.LieuPackets.Request.of(place.id()));
                return;
            }
        }
        if (!editing()) {
            int owner = rasterOwner(mouseX, mouseY);
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
        int owner = rasterOwner(mouseX, mouseY);
        return owner >= 0 && owner < view.seats().size() ? owner : -1;
    }

    private List<Integer> usedColors() {
        List<Integer> out = new ArrayList<>();
        for (MapView.Territory t : view.territories()) out.add(t.color());
        return out;
    }

    private void paintAt(double mouseX, double mouseY) {
        if (warMode()) {
            warPaintAt(mouseX, mouseY);
            return;
        }
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
        if (warMode()) {
            long[] all = stroke.toLongArray();
            for (int from = 0; from < all.length; from += WarMapService.MAX_CELLS) {
                long[] part = java.util.Arrays.copyOfRange(all, from, Math.min(all.length, from + WarMapService.MAX_CELLS));
                CrusadeNetwork.toServer(WarMapPackets.Edit.paint(warTool.zone, part));
            }
            stroke.clear();
            return;
        }
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

    // ------------------------------------------------------------------ commerce (P25)

    /** Les routes de commerce : un trait doré pointillé d'une capitale à l'autre, une pièce au milieu. */
    private void renderTrade(GuiGraphics g) {
        for (MapView.TradeLine r : view.routes()) {
            int x0 = screenX(r.x0()), y0 = screenY(r.z0()), x1 = screenX(r.x1()), y1 = screenY(r.z1());
            double len = Math.hypot(x1 - x0, y1 - y0);
            if (len < 4) continue;
            double angle = Math.atan2(y1 - y0, x1 - x0);
            for (double d = 0; d < len; d += 9) {
                int sx = (int) Math.round(x0 + Math.cos(angle) * d), sy = (int) Math.round(y0 + Math.sin(angle) * d);
                if (sx < mapX - 10 || sx > mapX + mapW + 10 || sy < mapY - 10 || sy > mapY + mapH + 10) continue;
                bar(g, sx, sy, angle, (int) Math.min(5, len - d), 1, 0xE0D4A017);
            }
            int mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
            g.fill(mx - 3, my - 3, mx + 3, my + 3, INK);
            g.fill(mx - 2, my - 2, mx + 2, my + 2, 0xFFE7BD55);
        }
    }

    private List<Component> tradeTooltip(double mouseX, double mouseY) {
        for (MapView.TradeLine r : view.routes()) {
            if (segmentDistance(mouseX, mouseY, screenX(r.x0()), screenY(r.z0()), screenX(r.x1()), screenY(r.z1())) > 4) continue;
            return List.of(Component.translatable("crusadecraft.trade.route").withStyle(ChatFormatting.GOLD),
                    r.a().copy().append(" ↔ ").append(r.b()).withStyle(ChatFormatting.WHITE),
                    Component.translatable("crusadecraft.trade.income.short", r.income()).withStyle(ChatFormatting.GRAY));
        }
        return List.of();
    }

    // ------------------------------------------------------------------ carte de guerre : dessin et gestes

    private void warPaintAt(double mouseX, double mouseY) {
        int cx = TerritoryMap.cellOf(worldX(mouseX)), cz = TerritoryMap.cellOf(worldZ(mouseY));
        int r = BRUSH_SIZES[brushSize];
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) continue;
                long key = TerritoryMap.key(cx + dx, cz + dz);
                if (warOverlay.containsKey(key) && warOverlay.get(key) == warTool.zone) continue;
                warOverlay.put(key, warTool.zone);
                stroke.add(key);
            }
        }
        warLayers = null;
    }

    /** Les zones de chaque plan, en rectangles et en bords, refaites seulement quand la carte ou le pinceau changent. */
    private List<WarLayer> warLayers() {
        if (warLayers != null) return warLayers;
        List<WarLayer> out = new ArrayList<>();
        List<MapView.WarPlan> plans = new ArrayList<>(view.war().plans());
        // Les alliés d'abord, son propre plan par-dessus.
        plans.sort((a, b) -> Boolean.compare(a.mine(), b.mine()));
        for (MapView.WarPlan p : plans) {
            Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
            for (int i = 0; i < p.keys().length && i < p.kinds().length; i++) cells.put(p.keys()[i], p.kinds()[i]);
            if (p.mine()) {
                for (var e : warOverlay.long2IntEntrySet()) {
                    if (e.getIntValue() < 0) cells.remove(e.getLongKey());
                    else cells.put(e.getLongKey(), e.getIntValue());
                }
            }
            for (int zone = 0; zone < ZONE_COLORS.length; zone++) {
                LongOpenHashSet set = new LongOpenHashSet();
                for (var e : cells.long2IntEntrySet()) if (e.getIntValue() == zone) set.add(e.getLongKey());
                if (set.isEmpty()) continue;
                int fill = (p.mine() ? 0x58000000 : 0x38000000) | ZONE_COLORS[zone];
                int line = 0xD0000000 | (p.mine() ? ZONE_COLORS[zone] : p.color());
                out.add(new WarLayer(fill, line, WebMapGeometry.rectangles(set), WebMapGeometry.border(set)));
            }
        }
        warLayers = out;
        return out;
    }

    private void renderWarZones(GuiGraphics g) {
        for (WarLayer layer : warLayers()) {
            for (WebMapGeometry.Rect r : layer.rects()) {
                int x0 = screenX(r.x0() * (double) TerritoryMap.CELL), x1 = screenX((r.x1() + 1) * (double) TerritoryMap.CELL);
                int y0 = screenY(r.z0() * (double) TerritoryMap.CELL), y1 = screenY((r.z1() + 1) * (double) TerritoryMap.CELL);
                if (x1 < mapX || x0 > mapX + mapW || y1 < mapY || y0 > mapY + mapH) continue;
                g.fill(x0, y0, Math.max(x1, x0 + 1), Math.max(y1, y0 + 1), layer.fill());
            }
            for (WebMapGeometry.Segment s : layer.edges()) {
                int x0 = screenX(s.x0()), y0 = screenY(s.z0()), x1 = screenX(s.x1()), y1 = screenY(s.z1());
                if (Math.max(x0, x1) < mapX || Math.min(x0, x1) > mapX + mapW || Math.max(y0, y1) < mapY || Math.min(y0, y1) > mapY + mapH) continue;
                g.fill(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1, layer.line());
            }
        }
    }

    /** Flèches, repères et sièges, par-dessus les villes. */
    private void renderWarMarks(GuiGraphics g) {
        for (MapView.WarPlan p : view.war().plans()) {
            int color = 0xFF000000 | (p.mine() ? ZONE_COLORS[0] : p.color());
            for (WarMap.Arrow a : p.arrows()) arrow(g, screenX(a.x0()), screenY(a.z0()), screenX(a.x1()), screenY(a.z1()), color);
        }
        if (arrowing) arrow(g, screenX(arrowX0), screenY(arrowZ0), screenX(arrowX1), screenY(arrowZ1), 0xC0000000 | ZONE_COLORS[0]);
        for (MapView.WarPlan p : view.war().plans()) {
            for (WarMap.Pin pin : p.pins()) {
                int x = screenX(pin.x()), y = screenY(pin.z());
                if (x < mapX - 40 || x > mapX + mapW + 40 || y < mapY - 20 || y > mapY + mapH + 20) continue;
                String glyph = pinGlyph(pin.kind());
                int gw = font.width(glyph);
                g.drawString(font, glyph, x - gw / 2 + 1, y - 3, INK, false);
                g.drawString(font, glyph, x - gw / 2, y - 4, 0xFF000000 | PIN_COLORS[pin.kind().ordinal()], false);
                if (!pin.note().isEmpty() && scale <= 4) label(g, Component.literal(font.plainSubstrByWidth(pin.note(), 120)), x, y, 0.6f);
            }
        }
        // Un siège bat comme un cœur : on le voit de loin.
        float pulse = 0.6f + 0.4f * (float) Math.sin(Util.getMillis() / 220.0);
        int alpha = Math.round(pulse * 255) << 24;
        for (MapView.SiegeMark s : view.war().sieges()) {
            int x = screenX(s.x()), y = screenY(s.z());
            if (x < mapX - 20 || x > mapX + mapW + 20 || y < mapY - 20 || y > mapY + mapH + 20) continue;
            g.fill(x - 6, y - 6, x + 6, y + 6, (alpha & 0x7F000000) | 0x200000);
            int gw = font.width("⚔");
            g.drawString(font, "⚔", x - gw / 2 + 1, y - 3, INK, false);
            g.drawString(font, "⚔", x - gw / 2, y - 4, alpha | (s.color() & 0xFFFFFF), false);
        }
    }

    /** Une flèche épaisse, cernée d'encre : un trait tourné, et deux pour la pointe. */
    private static void arrow(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 2) return;
        double angle = Math.atan2(y1 - y0, x1 - x0);
        int head = (int) Math.max(6, Math.min(14, len / 4));
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? (INK & 0x00FFFFFF) | (color & 0xFF000000) : color;
            int t = pass == 0 ? 2 : 1;
            bar(g, x0, y0, angle, (int) Math.round(len), t, c);
            bar(g, x1, y1, angle + Math.PI - Math.toRadians(28), head, t, c);
            bar(g, x1, y1, angle + Math.PI + Math.toRadians(28), head, t, c);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, double angle, int length, int half, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) angle));
        g.fill(-half, -half, length + half, half + 1, color);
        g.pose().popPose();
    }

    /** La flèche ou le repère de son propre plan sous la souris, à six points près. */
    private UUID ownMarkNear(double mouseX, double mouseY) {
        MapView.WarPlan own = view.war().own();
        if (own == null) return null;
        UUID best = null;
        double bestD = 6;
        for (WarMap.Pin p : own.pins()) {
            double d = Math.hypot(screenX(p.x()) - mouseX, screenY(p.z()) - mouseY);
            if (d < bestD) {
                bestD = d;
                best = p.id();
            }
        }
        for (WarMap.Arrow a : own.arrows()) {
            double d = segmentDistance(mouseX, mouseY, screenX(a.x0()), screenY(a.z0()), screenX(a.x1()), screenY(a.z1()));
            if (d < bestD) {
                bestD = d;
                best = a.id();
            }
        }
        return best;
    }

    static double segmentDistance(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0, l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / l2));
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    /** Ce que la carte de guerre dit sous la souris : un siège, un repère, une flèche, une zone. */
    private List<Component> warTooltip(double mouseX, double mouseY) {
        List<Component> tip = new ArrayList<>();
        for (MapView.SiegeMark s : view.war().sieges()) {
            if (Math.hypot(screenX(s.x()) - mouseX, screenY(s.z()) - mouseY) > 7) continue;
            tip.add(Component.translatable("crusadecraft.warmap.siege", s.defender(), s.attacker()).withStyle(ChatFormatting.RED));
            tip.add(Component.translatable("crusadecraft.warmap.siege.garrison", s.garrison()).withStyle(ChatFormatting.GRAY));
            return tip;
        }
        for (MapView.WarPlan p : view.war().plans()) {
            for (WarMap.Pin pin : p.pins()) {
                if (Math.hypot(screenX(pin.x()) - mouseX, screenY(pin.z()) - mouseY) > 6) continue;
                tip.add(Component.literal(pinGlyph(pin.kind()) + " ").append(Component.translatable("crusadecraft.warmap.mark."
                        + pin.kind().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.GOLD));
                if (!pin.note().isEmpty()) tip.add(Component.literal(pin.note()).withStyle(ChatFormatting.WHITE));
                tip.add(p.name().copy().withStyle(ChatFormatting.GRAY));
                if (p.mine() && warTool == WarTool.REMOVE) tip.add(Component.translatable("crusadecraft.warmap.remove.click").withStyle(ChatFormatting.DARK_GRAY));
                return tip;
            }
        }
        for (MapView.WarPlan p : view.war().plans()) {
            for (WarMap.Arrow a : p.arrows()) {
                if (segmentDistance(mouseX, mouseY, screenX(a.x0()), screenY(a.z0()), screenX(a.x1()), screenY(a.z1())) > 4) continue;
                tip.add(Component.translatable("crusadecraft.warmap.arrow").withStyle(ChatFormatting.RED));
                tip.add(p.name().copy().withStyle(ChatFormatting.GRAY));
                if (p.mine() && warTool == WarTool.REMOVE) tip.add(Component.translatable("crusadecraft.warmap.remove.click").withStyle(ChatFormatting.DARK_GRAY));
                return tip;
            }
        }
        long key = TerritoryMap.key(TerritoryMap.cellOf(worldX(mouseX)), TerritoryMap.cellOf(worldZ(mouseY)));
        for (MapView.WarPlan p : view.war().plans()) {
            for (int i = 0; i < p.keys().length && i < p.kinds().length; i++) {
                if (p.keys()[i] != key) continue;
                int zone = p.kinds()[i];
                if (zone < 0 || zone >= WarMap.Zone.values().length) break;
                tip.add(Component.translatable("crusadecraft.warmap.zone." + WarMap.Zone.values()[zone].name().toLowerCase(Locale.ROOT))
                        .withStyle(s -> s.withColor(ZONE_COLORS[zone])));
                tip.add(p.name().copy().withStyle(ChatFormatting.GRAY));
                return tip;
            }
        }
        return tip;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!Ui.inside(mouseX, mouseY, mapX, mapY, mapW, mapH) || overPanel(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, delta);
        // Le point sous la souris reste en place pendant le zoom, qui glisse jusqu'à sa nouvelle échelle.
        if (targetScale <= 0) targetScale = scale;
        targetScale = Math.max(0.5, Math.min(64, targetScale * (delta > 0 ? 0.8 : 1.25)));
        anchorSX = mouseX;
        anchorSY = mouseY;
        anchorWX = worldX(mouseX);
        anchorWZ = worldZ(mouseY);
        lastMove = Util.getMillis();
        return true;
    }

    /** Un pas du zoom fluide : l'échelle rejoint la cible, le point visé reste sous la souris. */
    private void animateZoom() {
        if (targetScale <= 0 || Math.abs(targetScale - scale) <= 1e-6) return;
        double next = scale + (targetScale - scale) * 0.35;
        if (Math.abs(targetScale - next) / targetScale < 0.004) next = targetScale;
        scale = next;
        centerX = anchorWX - (anchorSX - mapX - mapW / 2.0) * scale;
        centerZ = anchorWZ - (anchorSY - mapY - mapH / 2.0) * scale;
        lastMove = Util.getMillis();
    }

    /** Le siège qui tient le pixel sous la souris, dans l'image en mémoire (peut-être calculée pour un autre cadrage). */
    private int rasterOwner(double mouseX, double mouseY) {
        if (raster == null || rasterScale <= 0) return MapRaster.NONE;
        int px = (int) Math.floor((worldX(mouseX) - rasterX) / rasterScale + mapW / 2.0);
        int py = (int) Math.floor((worldZ(mouseY) - rasterZ) / rasterScale + mapH / 2.0);
        return raster.ownerAt(px, py);
    }

    /** Le paysage et l'état des guerres, pour le rendu. */
    private MapRaster.Extras extras(int lod) {
        var dim = view.dimension();
        MapRaster.TerrainSource terrain = (x, z) -> ClientTerrain.color(dim, x, z, lod);
        var occupied = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        for (MapView.Occupation o : view.war().occupied()) occupied.put(o.province(), o.color() & 0xFFFFFF);
        var friends = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        var foes = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
        for (int i = 0; i < view.territories().size(); i++) {
            UUID id = view.territories().get(i).id();
            if (view.war().friends().contains(id)) friends.add(i);
            if (view.war().foes().contains(id)) foes.add(i);
        }
        return new MapRaster.Extras(terrain, occupied, friends, foes);
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
