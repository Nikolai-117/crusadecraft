package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Role;
import fr.crusadecraft.core.lineage.TreeLayout;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LineagePackets;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'arbre illustré d'une maison du registre (P26) : une rangée par génération, les couples reliés, les enfants
 * pendus sous leurs parents. On le fait glisser à la souris et on zoome à la molette ; un clic choisit une personne,
 * pour ouvrir sa fiche, son apparence ou l'arbre de Comes Alive.
 */
public class TreeScreen extends Screen {
    private static final int BOX_W = 104, BOX_H = 26, GAP_X = 10, GAP_Y = 22;

    private final LineageScreen parent;
    private LineageView view;
    private LineageView.Selection house;
    private Map<UUID, TreeLayout.Placed> placed = Map.of();
    private final Map<UUID, LineageView.Row> rows = new HashMap<>();
    private UUID selected;
    private int left, top, w, h, areaX, areaY, areaW, areaH;
    private double panX, panY;
    private float zoom = 1f;
    private boolean centered;
    private Component status;
    private boolean statusOk;
    private long statusUntil;

    public TreeScreen(LineageScreen parent, LineageView view) {
        super(Component.translatable("crusadecraft.tree.title"));
        this.parent = parent;
        load(view);
    }

    private void load(LineageView v) {
        this.view = v;
        this.house = v.selection();
        rows.clear();
        List<TreeLayout.Node> nodes = new ArrayList<>();
        if (house != null) {
            for (LineageView.Row r : house.rows()) {
                rows.put(r.id(), r);
                nodes.add(new TreeLayout.Node(r.id(), r.father(), r.mother(), r.spouseId(), r.group()));
            }
        }
        placed = TreeLayout.layout(nodes);
        if (selected != null && !rows.containsKey(selected)) selected = null;
    }

    public void refresh(LineageView v) {
        parent.refresh(v);
        if (v.selection() != null && house != null && v.selection().id().equals(house.id())) load(v);
        if (minecraft != null) rebuildWidgets();
    }

    /** Aperçu du banc d'essai visuel : choisir une personne. */
    public void previewPerson(UUID id) {
        selected = id;
        if (minecraft != null) rebuildWidgets();
    }

    public void showStatus(boolean success, Component message) {
        status = message;
        statusOk = success;
        statusUntil = Util.getMillis() + 6000;
    }

    @Override
    protected void init() {
        w = Math.min(width - 12, 560);
        h = Math.min(height - 12, 320);
        left = (width - w) / 2;
        top = (height - h) / 2;
        areaX = left + 8;
        areaY = top + 24;
        areaW = w - 16;
        areaH = h - 24 - 42;
        if (!centered) {
            // L'arbre entier dans le cadre au départ : réduit s'il le faut (jusqu'à la moitié), centré.
            int cols = 0, rowsCount = 0;
            for (TreeLayout.Placed p : placed.values()) {
                cols = Math.max(cols, p.column() + 1);
                rowsCount = Math.max(rowsCount, p.generation() + 1);
            }
            double treeW = cols * (BOX_W + GAP_X) - GAP_X, treeH = rowsCount * (BOX_H + GAP_Y) - GAP_Y;
            zoom = (float) Math.max(0.5, Math.min(1.0, Math.min((areaW - 16) / Math.max(1, treeW), (areaH - 16) / Math.max(1, treeH))));
            panX = Math.max(8, (areaW - treeW * zoom) / 2.0);
            panY = Math.max(8, (areaH - treeH * zoom) / 2.0);
            centered = true;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + w - 68, top + h - 24, 60, 16).build());
        LineageView.Row r = selected == null ? null : rows.get(selected);
        int x = left + 8;
        if (r != null && view.canEdit()) {
            x = add(Button.builder(Component.translatable("crusadecraft.person.open"),
                    b -> minecraft.setScreen(new PersonForm(parent, r, LineageScreen.firstName(r.name())))), x, null);
            if (view.mca() && r.alive() && r.inWorld()) {
                x = add(Button.builder(Component.translatable("crusadecraft.person.appearance"), b -> act(LineagePackets.Action.Op.APPEARANCE, r.id())),
                        x, "crusadecraft.person.appearance.tip");
            }
        }
        if (r != null && view.mca()) {
            add(Button.builder(Component.translatable("crusadecraft.tree.mca"), b -> act(LineagePackets.Action.Op.MCA_TREE, r.id())),
                    x, "crusadecraft.tree.mca.tip");
        }
    }

    private int add(Button.Builder builder, int x, String tip) {
        Button b = builder.bounds(x, top + h - 24, 0, 16).build();
        b.setWidth(font.width(b.getMessage()) + 12);
        if (tip != null) b.setTooltip(Tooltip.create(Component.translatable(tip)));
        addRenderableWidget(b);
        return x + b.getWidth() + 3;
    }

    private void act(LineagePackets.Action.Op op, UUID target) {
        CrusadeNetwork.toServer(LineagePackets.Action.of(op, target, house == null ? null : house.id()));
    }

    private int boxX(TreeLayout.Placed p) {
        return p.column() * (BOX_W + GAP_X);
    }

    private int boxY(TreeLayout.Placed p) {
        return p.generation() * (BOX_H + GAP_Y);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Component heading = house == null ? title : Component.translatable("crusadecraft.tree.of", house.title());
        Ui.scaled(g, font, heading, left + 10, top + 8, 1.2f, Ui.TEXT_TITLE, false);
        // Le mode d'emploi à droite du titre s'il y tient ; sinon au pied, à la place des réponses du serveur.
        Component help = Component.translatable("crusadecraft.tree.help");
        int helpW = Math.round(font.width(help) * 0.75f), headW = Math.round(font.width(heading) * 1.2f);
        boolean helpTop = headW + helpW + 30 < w;
        if (helpTop) Ui.scaled(g, font, help, left + w - 10 - helpW, top + 10, 0.75f, Ui.TEXT_MUTED, false);
        Ui.inset(g, areaX, areaY, areaW, areaH);
        if (placed.isEmpty()) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.tree.empty"), areaX + areaW / 2, areaY + areaH / 2 - 4, areaW - 20, Ui.TEXT_MUTED);
        } else {
            g.enableScissor(areaX + 1, areaY + 1, areaX + areaW - 1, areaY + areaH - 1);
            g.pose().pushPose();
            g.pose().translate(areaX + panX, areaY + panY, 0);
            g.pose().scale(zoom, zoom, 1f);
            renderLinks(g);
            UUID hover = at(mouseX, mouseY);
            for (TreeLayout.Placed p : placed.values()) renderBox(g, p, p.id().equals(hover));
            g.pose().popPose();
            g.disableScissor();
        }
        if (status != null && Util.getMillis() < statusUntil) {
            Ui.scaled(g, font, status, left + 10, top + h - 37, 0.75f, statusOk ? Ui.GREEN : Ui.RED, false);
        } else if (!helpTop) {
            Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(help.getString(), Math.round((w - 20) / 0.75f))), left + 10, top + h - 37, 0.75f,
                    Ui.TEXT_MUTED, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Les couples d'un trait, les enfants par un coude : du milieu du couple (ou du parent seul) jusqu'à leur case. */
    private void renderLinks(GuiGraphics g) {
        int line = 0xFF8A7346;
        for (TreeLayout.Placed p : placed.values()) {
            LineageView.Row r = rows.get(p.id());
            TreeLayout.Placed spouse = r.spouseId() == null ? null : placed.get(r.spouseId());
            if (spouse != null && spouse.generation() == p.generation() && spouse.column() == p.column() + 1) {
                int y = boxY(p) + BOX_H / 2;
                g.fill(boxX(p) + BOX_W, y, boxX(spouse), y + 1, line);
            }
            TreeLayout.Placed f = r.father() == null ? null : placed.get(r.father());
            TreeLayout.Placed m = r.mother() == null ? null : placed.get(r.mother());
            if (f == null && m == null) continue;
            int fromX, fromY;
            if (f != null && m != null && f.generation() == m.generation() && Math.abs(f.column() - m.column()) == 1) {
                fromX = (boxX(f) + boxX(m) + BOX_W) / 2;
                fromY = boxY(f) + BOX_H / 2;
            } else {
                TreeLayout.Placed one = f != null ? f : m;
                fromX = boxX(one) + BOX_W / 2;
                fromY = boxY(one) + BOX_H;
            }
            int toX = boxX(p) + BOX_W / 2, toY = boxY(p);
            int midY = toY - GAP_Y / 2;
            g.fill(fromX, Math.min(fromY, midY), fromX + 1, Math.max(fromY, midY) + 1, line);
            g.fill(Math.min(fromX, toX), midY, Math.max(fromX, toX) + 1, midY + 1, line);
            g.fill(toX, midY, toX + 1, toY, line);
        }
    }

    private void renderBox(GuiGraphics g, TreeLayout.Placed p, boolean hover) {
        LineageView.Row r = rows.get(p.id());
        int x = boxX(p), y = boxY(p);
        boolean sel = p.id().equals(selected);
        int border = sel ? Ui.TEXT_TITLE : r.member() ? Ui.GOLD_DARK : 0xFF4A4036;
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, border);
        g.fill(x, y, x + BOX_W, y + BOX_H, !r.alive() ? 0xFF1A1714 : hover ? 0xFF3A2E22 : sel ? Ui.PANEL_HIGHLIGHT : 0xFF2A2119);
        String mark = switch (r.role()) {
            case SOUVERAIN -> "♛ ";
            case HERITIER -> "♔ ";
            case CONSORT -> "♕ ";
            default -> "";
        };
        String name = font.plainSubstrByWidth(mark + r.name(), Math.round((BOX_W - 6) / 0.8f));
        Ui.scaled(g, font, Component.literal(name), x + 3, y + 3, 0.8f, r.alive() ? Ui.TEXT : Ui.TEXT_MUTED, false);
        Component sub = Component.translatable(r.alive() ? "crusadecraft.tree.age" : "crusadecraft.tree.dead", r.age());
        if (r.role() != Role.AUCUN && mark.isEmpty()) sub = sub.copy().append(" · ").append(Component.translatable(r.role().key()));
        Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), Math.round((BOX_W - 10) / 0.7f))), x + 3, y + 14, 0.7f,
                r.female() ? 0xFFC9A0B0 : 0xFF9FB4C9, false);
        if (r.inWorld() && r.alive()) g.fill(x + BOX_W - 5, y + 3, x + BOX_W - 2, y + 6, Ui.GREEN);
    }

    /** La personne sous la souris, en coordonnées de l'arbre. */
    private UUID at(double mx, double my) {
        if (!Ui.inside(mx, my, areaX, areaY, areaW, areaH)) return null;
        double tx = (mx - areaX - panX) / zoom, ty = (my - areaY - panY) / zoom;
        for (TreeLayout.Placed p : placed.values()) {
            if (tx >= boxX(p) && tx < boxX(p) + BOX_W && ty >= boxY(p) && ty < boxY(p) + BOX_H) return p.id();
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        UUID hit = at(mx, my);
        if (hit != null && button == 0) {
            selected = hit.equals(selected) ? null : hit;
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (Ui.inside(mx, my, areaX, areaY, areaW, areaH)) {
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!Ui.inside(mx, my, areaX, areaY, areaW, areaH)) return false;
        float before = zoom;
        zoom = Math.max(0.4f, Math.min(1.6f, zoom * (delta > 0 ? 1.15f : 1 / 1.15f)));
        // Le point sous la souris reste où il est.
        double ax = mx - areaX, ay = my - areaY;
        panX = ax - (ax - panX) * zoom / before;
        panY = ay - (ay - panY) * zoom / before;
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
