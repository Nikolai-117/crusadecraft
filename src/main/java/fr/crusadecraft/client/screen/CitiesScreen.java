package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.realm.CityView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CityPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Les villes du royaume (P19) : chaque colonie rattachée, tenue en direct ou confiée à un gouverneur du registre des
 * lignées, avec la part de ses impôts et de ses levées qui revient à la couronne. Chaque clic part aussitôt au
 * serveur, qui revérifie et renvoie l'écran à jour.
 */
public class CitiesScreen extends Screen {
    private static final int ROW = 22;
    private CityView view;
    private int left, top, w, h, listTop, visible, scroll;
    private Component status;
    private boolean statusOk;
    private long statusUntil;

    public CitiesScreen(CityView view) {
        super(Component.translatable("crusadecraft.city.title"));
        this.view = view;
    }

    public void refresh(CityView newView, Component message, boolean success) {
        this.view = newView;
        if (message != null) {
            status = message;
            statusOk = success;
            statusUntil = Util.getMillis() + 6000;
        }
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(430, width - 16);
        h = Math.min(250, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listTop = top + 52;
        visible = Math.max(1, (h - 52 - 30) / ROW);
        scroll = Math.max(0, Math.min(scroll, view.rows().size() - visible));

        Component attachLabel = view.here().isEmpty() ? Component.translatable("crusadecraft.city.attach")
                : Component.translatable("crusadecraft.city.attach.named", view.here());
        int aw = Math.min(w / 2 - 12, font.width(attachLabel) + 12);
        Button attach = Button.builder(attachLabel, b -> CrusadeNetwork.toServer(new CityPackets.Action(CityPackets.Action.Op.ATTACH_HERE, null, null, 0, 0)))
                .bounds(left + w - 8 - aw, top + 8, aw, 16).build();
        attach.active = !view.here().isEmpty();
        attach.setTooltip(Tooltip.create(Component.translatable(view.here().isEmpty() ? "crusadecraft.city.attach.none.tip" : "crusadecraft.city.attach.tip")));
        addRenderableWidget(attach);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 8 - 60, top + h - 22, 60, 16).build());

        int gw = Math.min(130, w / 3), sw = 62;
        int bx = left + w - 8 - gw - 2 * (sw + 2);
        for (int i = 0; i < visible && scroll + i < view.rows().size(); i++) {
            CityView.Row r = view.rows().get(scroll + i);
            int y = listTop + i * ROW + 2;
            Button governor = Button.builder(Component.literal(font.plainSubstrByWidth(governorLabel(r).getString(), gw - 8)),
                    b -> cycleGovernor(r)).bounds(bx, y, gw, 16).build();
            Button tax = Button.builder(Component.translatable("crusadecraft.city.tax", r.taxShare()), b -> share(r, true)).bounds(bx + gw + 2, y, sw, 16).build();
            Button levy = Button.builder(Component.translatable("crusadecraft.city.levy", r.levyShare()), b -> share(r, false)).bounds(bx + gw + sw + 4, y, sw, 16).build();
            boolean governed = r.governor() != null;
            tax.active = governed;
            levy.active = governed;
            if (r.capital()) {
                governor.active = false;
                governor.setTooltip(Tooltip.create(Component.translatable("crusadecraft.city.capital")));
            } else {
                governor.setTooltip(Tooltip.create(Component.translatable(view.candidates().isEmpty() ? "crusadecraft.city.governor.none.tip"
                        : "crusadecraft.city.governor.tip")));
            }
            tax.setTooltip(Tooltip.create(Component.translatable(governed ? "crusadecraft.city.tax.tip" : "crusadecraft.city.share.direct")));
            levy.setTooltip(Tooltip.create(Component.translatable(governed ? "crusadecraft.city.levy.tip" : "crusadecraft.city.share.direct")));
            addRenderableWidget(governor);
            addRenderableWidget(tax);
            addRenderableWidget(levy);
        }
    }

    private Component governorLabel(CityView.Row r) {
        return r.governor() == null ? Component.translatable("crusadecraft.city.direct") : Component.literal("♛ " + r.governorName());
    }

    /** En direct → premier candidat → … → dernier → en direct (Maj : dans l'autre sens). */
    private void cycleGovernor(CityView.Row r) {
        List<CityView.Candidate> c = view.candidates();
        if (c.isEmpty()) {
            refresh(view, Component.translatable("crusadecraft.city.governor.none"), false);
            return;
        }
        int at = -1;
        for (int i = 0; i < c.size(); i++) if (c.get(i).id().equals(r.governor())) at = i;
        int n = c.size() + 1;
        int next = Math.floorMod(at + 1 + (hasShiftDown() ? -1 : 1), n) - 1;
        UUID governor = next < 0 ? null : c.get(next).id();
        int tax = r.governor() == null ? view.defaultTax() : r.taxShare();
        int levy = r.governor() == null ? view.defaultLevy() : r.levyShare();
        CrusadeNetwork.toServer(governor == null ? new CityPackets.Action(CityPackets.Action.Op.RECALL, r.colony(), null, 0, 0)
                : new CityPackets.Action(CityPackets.Action.Op.ENTRUST, r.colony(), governor, tax, levy));
    }

    /** Clic : +10 points pour la couronne ; Maj + clic : −10. */
    private void share(CityView.Row r, boolean taxes) {
        if (r.governor() == null) return;
        int step = hasShiftDown() ? -10 : 10;
        int tax = taxes ? clamp(r.taxShare() + step) : r.taxShare();
        int levy = taxes ? r.levyShare() : clamp(r.levyShare() + step);
        CrusadeNetwork.toServer(new CityPackets.Action(CityPackets.Action.Op.ENTRUST, r.colony(), r.governor(), tax, levy));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 10, 1.3f, Ui.TEXT_TITLE, false);
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(view.realmName(), w / 2)), left + 10, top + 25, Ui.TEXT_MUTED);
        boolean over = view.directCount() > view.domainLimit();
        Component domain = Component.translatable("crusadecraft.city.domain", view.directCount(), view.domainLimit(), view.penalty());
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(domain.getString(), w - 20)), left + 10, top + 37, over ? Ui.RED : Ui.TEXT);
        Ui.separator(g, left + 8, listTop - 2, w - 16, Ui.GOLD_DARK);

        int gw = Math.min(130, w / 3), sw = 62;
        int nameW = w - 16 - gw - 2 * (sw + 2) - 8;
        if (view.rows().isEmpty()) Ui.text(g, font, Component.translatable("crusadecraft.city.empty"), left + 10, listTop + 6, Ui.TEXT_MUTED);
        Component tip = null;
        for (int i = 0; i < visible && scroll + i < view.rows().size(); i++) {
            CityView.Row r = view.rows().get(scroll + i);
            int y = listTop + i * ROW;
            String name = (r.capital() ? "★ " : "") + r.name();
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(name, nameW)), left + 10, y + 3, r.overLimit() ? Ui.RED : Ui.TEXT_TITLE);
            Component sub = r.capital() ? Component.translatable("crusadecraft.city.row.capital", r.citizens())
                    : Component.translatable("crusadecraft.city.row", r.citizens());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), nameW)), left + 10, y + 12, Ui.TEXT_MUTED);
            if (r.overLimit() && Ui.inside(mouseX, mouseY, left + 10, y, nameW, ROW)) {
                tip = Component.translatable("crusadecraft.city.over", view.penalty());
            }
        }
        if (view.rows().size() > visible) {
            Ui.text(g, font, Component.translatable("crusadecraft.city.more", scroll + 1, Math.min(view.rows().size(), scroll + visible), view.rows().size()),
                    left + 10, top + h - 30, Ui.TEXT_MUTED);
        }
        Component foot = status != null && Util.getMillis() < statusUntil ? status : Component.translatable("crusadecraft.city.hint");
        int footColor = status != null && Util.getMillis() < statusUntil ? (statusOk ? Ui.GREEN : Ui.RED) : Ui.TEXT_MUTED;
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(foot.getString(), w - 16 - 70)), left + 10, top + h - 18, footColor);
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) g.renderTooltip(font, tip.copy().withStyle(ChatFormatting.RED), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, view.rows().size() - visible);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
