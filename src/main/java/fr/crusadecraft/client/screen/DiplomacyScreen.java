package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.diplomacy.DiplomacyRules;
import fr.crusadecraft.core.diplomacy.DiplomacyView;
import fr.crusadecraft.core.diplomacy.Relation;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Le Colombier royal : à qui écrire, ce qu'ils pensent de vous, et ce qu'ils répondraient. */
public class DiplomacyScreen extends Screen {
    private static final int ROW = 18;
    private static UUID lastSelected;

    private DiplomacyView view;
    private UUID selected = lastSelected;
    private int left, top, w, h;
    private int listScroll;
    private int tax = 20, levy = 20;
    private List<Component> pendingTooltip;

    public DiplomacyScreen(DiplomacyView view) {
        super(Component.translatable("block.crusadecraft.colombier"));
        this.view = view;
    }

    public void refresh(DiplomacyView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    private DiplomacyView.Entry selectedEntry() {
        for (DiplomacyView.Entry e : view.entries()) if (e.id().equals(selected)) return e;
        if (!view.entries().isEmpty()) {
            selected = view.entries().get(0).id();
            return view.entries().get(0);
        }
        return null;
    }

    static Component entryName(DiplomacyView.Entry e) {
        return e.ai() ? AiNames.realm(e.rank(), e.label()) : Component.literal(e.label());
    }

    private void send(DiplomacyPackets.DiploAction action) {
        CrusadeNetwork.toServer(action);
    }

    @Override
    protected void init() {
        w = Math.min(470, width - 10);
        h = Math.min(262, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        Button mail = Button.builder(Component.translatable("crusadecraft.diplo.take_mail", view.mailArrived()),
                b -> send(DiplomacyPackets.DiploAction.of(DiplomacyPackets.DiploAction.Kind.TAKE_MAIL, null)))
                .bounds(left + w - 150, top + 6, 140, 16).build();
        mail.active = view.mailArrived() > 0;
        mail.setTooltip(Tooltip.create(Component.translatable("crusadecraft.diplo.take_mail.tip", view.mailAwaiting())));
        addRenderableWidget(mail);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 8, top + h - 22, 172, 16).build());

        DiplomacyView.Entry e = selectedEntry();
        if (e == null) return;
        int rx = left + 186, rw = w - 196;
        int by = top + h - 128;
        // Cadeaux et insulte, en parts égales sur une rangée.
        int slots = e.ai() ? 4 : 3;
        int sw = (rw - (slots - 1) * 3) / slots;
        int gx = rx;
        for (int gold : fr.crusadecraft.core.diplomacy.DiplomacyService.GIFTS) {
            Button g = Button.builder(Component.translatable("crusadecraft.diplo.gift", gold),
                    b -> send(new DiplomacyPackets.DiploAction(DiplomacyPackets.DiploAction.Kind.GIFT, e.id(), null, null, gold, 20, 20)))
                    .bounds(gx, by, sw, 16).build();
            g.active = view.gold() >= gold;
            g.setTooltip(Tooltip.create(Component.translatable("crusadecraft.diplo.gift.tip", gold)));
            addRenderableWidget(g);
            gx += sw + 3;
        }
        if (e.ai()) {
            Button insult = Button.builder(Component.translatable("crusadecraft.diplo.insult"),
                    b -> send(DiplomacyPackets.DiploAction.of(DiplomacyPackets.DiploAction.Kind.INSULT, e.id()))).bounds(gx, by, rx + rw - gx, 16).build();
            insult.setTooltip(Tooltip.create(Component.translatable("crusadecraft.diplo.insult.tip")));
            addRenderableWidget(insult);
        }
        // Propositions (et mission du chancelier) : deux colonnes.
        int px = rx, py = by + 20, pw = (rw - 4) / 2;
        int i = 0;
        for (DiplomacyRules.Proposal p : DiplomacyRules.Proposal.values()) {
            if (!e.ai() && p == DiplomacyRules.Proposal.TRIBUT) continue;
            DiplomacyRules.Verdict v = verdict(e, p);
            String mark = v == null ? "" : v.blockedKey() != null ? "· " : v.accepted() ? "✔ " : "✖ ";
            int col = i % 2, row = i / 2;
            Button b = Button.builder(Component.literal(mark).append(Component.translatable(p.key())),
                    btn -> send(new DiplomacyPackets.DiploAction(DiplomacyPackets.DiploAction.Kind.PROPOSE, e.id(), p, null, 0, tax, levy)))
                    .bounds(px + col * (pw + 4), py + row * 18, pw, 16).build();
            b.active = v == null || v.blockedKey() == null;
            b.setTooltip(Tooltip.create(verdictTooltip(e, p, v)));
            addRenderableWidget(b);
            i++;
        }
        if (e.ai()) {
            boolean already = e.id().equals(view.chancellorTarget());
            int col = i % 2, row = i / 2;
            Button chancellor = Button.builder(Component.translatable(already ? "crusadecraft.diplo.chancellor.on" : "crusadecraft.diplo.chancellor"),
                    b -> send(DiplomacyPackets.DiploAction.of(DiplomacyPackets.DiploAction.Kind.CHANCELLOR, e.id())))
                    .bounds(px + col * (pw + 4), py + row * 18, pw, 16).build();
            chancellor.active = view.hasChancellor() && !already;
            chancellor.setTooltip(Tooltip.create(Component.translatable(view.hasChancellor() ? "crusadecraft.diplo.chancellor.tip"
                    : "crusadecraft.diplo.no_chancellor")));
            addRenderableWidget(chancellor);
            i++;
        }
        // Contrat proposé avec la vassalisation : impôt et levées, chacun sur une moitié de la rangée.
        int cy = py + ((i + 1) / 2) * 18 + 2;
        int half = rw / 2;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> { tax = Relation.clampRate(tax - 5); rebuildWidgets(); }).bounds(rx + half - 62, cy, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { tax = Relation.clampRate(tax + 5); rebuildWidgets(); }).bounds(rx + half - 22, cy, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> { levy = Relation.clampRate(levy - 5); rebuildWidgets(); }).bounds(rx + rw - 58, cy, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> { levy = Relation.clampRate(levy + 5); rebuildWidgets(); }).bounds(rx + rw - 18, cy, 18, 16).build());
        // Rompre un traité
        int ty = top + 92;
        for (Map.Entry<Treaty, Long> t : e.treatyDays().entrySet()) {
            Treaty treaty = t.getKey();
            Button br = Button.builder(Component.translatable("crusadecraft.diplo.break"),
                    b -> send(new DiplomacyPackets.DiploAction(DiplomacyPackets.DiploAction.Kind.BREAK, e.id(), null, treaty, 0, 20, 20)))
                    .bounds(rx + rw - 46, ty - 3, 46, 16).build();
            br.setTooltip(Tooltip.create(Component.translatable("crusadecraft.diplo.break.tip")));
            addRenderableWidget(br);
            ty += 16;
        }
    }

    /** La réponse prévue, ajustée au contrat choisi pour une vassalisation. */
    private DiplomacyRules.Verdict verdict(DiplomacyView.Entry e, DiplomacyRules.Proposal p) {
        DiplomacyRules.Verdict v = e.verdicts().get(p);
        if (v == null || p != DiplomacyRules.Proposal.VASSALISATION || v.blockedKey() != null) return v;
        int base = (25 - 20) + (25 - 20) / 2;
        int now = (25 - tax) + (25 - levy) / 2;
        int total = v.total() - base + now;
        List<DiplomacyRules.Factor> factors = new ArrayList<>();
        for (DiplomacyRules.Factor f : v.factors()) {
            factors.add(f.key().equals("crusadecraft.diplo.f.contract") ? new DiplomacyRules.Factor(f.key(), now) : f);
        }
        return new DiplomacyRules.Verdict(total >= 0, total, factors, null);
    }

    private Component verdictTooltip(DiplomacyView.Entry e, DiplomacyRules.Proposal p, DiplomacyRules.Verdict v) {
        MutableComponent tip = Component.translatable(p.key()).withStyle(ChatFormatting.GOLD).append("\n")
                .append(Component.translatable(p.key() + ".desc").withStyle(ChatFormatting.GRAY));
        if (v == null) {
            tip.append("\n\n").append(Component.translatable("crusadecraft.diplo.to_player").withStyle(ChatFormatting.GRAY));
            return tip;
        }
        if (v.blockedKey() != null) {
            tip.append("\n\n").append(Component.translatable(v.blockedKey()).withStyle(ChatFormatting.RED));
            return tip;
        }
        tip.append("\n");
        for (DiplomacyRules.Factor f : v.factors()) {
            if (f.value() == 0) continue;
            tip.append("\n").append(Component.translatable(f.key()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + signed(f.value())).withStyle(f.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        tip.append("\n\n").append(Component.translatable(v.accepted() ? "crusadecraft.diplo.will_accept" : "crusadecraft.diplo.will_refuse")
                .withStyle(v.accepted() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (p.treaty() != null || p == DiplomacyRules.Proposal.VASSALISATION) {
            tip.append("\n").append(Component.translatable("crusadecraft.diplo.seal_needed").withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component res = Component.translatable("crusadecraft.diplo.resources", view.gold(), view.prestige());
        Ui.text(g, font, res, left + w - 160 - font.width(res), top + 10, Ui.TEXT_MUTED);

        // Liste des royaumes connus
        int lx = left + 8, ly = top + 28, lw = 172, lh = h - 54;
        Ui.inset(g, lx, ly, lw, lh);
        if (view.entries().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.diplo.nobody"), lx + 6, ly + 6, lw - 12, Ui.TEXT_MUTED);
        }
        int visible = (lh - 4) / ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, view.entries().size() - visible)));
        for (int i = listScroll; i < view.entries().size() && i < listScroll + visible; i++) {
            DiplomacyView.Entry e = view.entries().get(i);
            int ry = ly + 2 + (i - listScroll) * ROW;
            boolean sel = e.id().equals(selected);
            boolean hover = Ui.inside(mouseX, mouseY, lx + 2, ry, lw - 4, ROW - 1);
            g.fill(lx + 2, ry, lx + lw - 2, ry + ROW - 1, sel ? Ui.PANEL_HIGHLIGHT : hover ? 0xFF2C231B : 0xFF1C1612);
            g.fill(lx + 5, ry + 4, lx + 13, ry + 13, 0xFF000000 | e.color());
            String name = font.plainSubstrByWidth(entryName(e).getString(), lw - 24);
            Ui.text(g, font, Component.literal(name), lx + 17, ry + 1, sel ? Ui.TEXT_TITLE : Ui.TEXT);
            Component status = Component.translatable(e.status().key());
            g.pose().pushPose();
            g.pose().translate(lx + 17, ry + 10, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawString(font, status, 0, 0, e.status().color(), false);
            if (e.ai()) g.drawString(font, Component.literal(signed(e.opinion())), Math.round((lw - 40) / 0.7f), 0, opinionColor(e.opinion()), false);
            g.pose().popPose();
        }

        DiplomacyView.Entry e = selectedEntry();
        if (e != null) renderEntry(g, e, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private static int opinionColor(int opinion) {
        return opinion >= 20 ? Ui.GREEN : opinion <= -20 ? Ui.RED : 0xFFC99A3C;
    }

    private void renderEntry(GuiGraphics g, DiplomacyView.Entry e, int mouseX, int mouseY) {
        int rx = left + 186, rw = w - 196;
        int y = top + 30;
        Ui.shield(g, font, rx, y, 20, 25, e.color(), Ui.initial(e.label()));
        Ui.scaled(g, font, entryName(e), rx + 26, y, 1.15f, Ui.TEXT_TITLE, false);
        Component ruler = Component.translatable(e.rank().holderKey(e.rulerFemale())).append(" " + e.ruler());
        if (e.personality() != null) ruler = ruler.copy().append(" · ").append(Component.translatable(e.personality().key()));
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(ruler.getString(), rw - 30)), rx + 26, y + 13, Ui.TEXT_MUTED);

        y += 32;
        Component status = Component.translatable(e.status().key());
        Ui.text(g, font, status, rx, y, e.status().color());
        if (e.ai()) {
            Component op = Component.translatable("crusadecraft.diplo.opinion.short", signed(e.opinion()));
            int ox = rx + rw - font.width(op);
            Ui.text(g, font, op, ox, y, opinionColor(e.opinion()));
            if (Ui.inside(mouseX, mouseY, ox, y - 1, font.width(op), 10)) pendingTooltip = opinionTooltip(e);
        } else if (!e.mcRank().isEmpty()) {
            Component mc = Component.translatable("crusadecraft.diplo.mc_rank", e.mcRank());
            Ui.text(g, font, mc, rx + rw - font.width(mc), y, Ui.TEXT_MUTED);
        }
        y += 12;
        String strength = String.format(Locale.ROOT, "%.1f", e.strengthRatio());
        Ui.text(g, font, Component.translatable("crusadecraft.diplo.strength", strength), rx, y, Ui.TEXT_MUTED);
        y += 12;
        // Traités en cours
        if (e.treatyDays().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.diplo.no_treaty.short"), rx, y, 0xFF6E6250);
        }
        for (Map.Entry<Treaty, Long> t : e.treatyDays().entrySet()) {
            Component line = Component.translatable(t.getKey().key()).append(Component.literal(" — ").append(t.getValue() < 0
                    ? Component.translatable("crusadecraft.diplo.permanent") : Component.translatable("crusadecraft.diplo.days_left", t.getValue())));
            Ui.text(g, font, line, rx, y + 1, Ui.TEXT);
            y += 16;
        }
        if (e.status() == DiploStatus.VASSAL || e.status() == DiploStatus.SUZERAIN) {
            Ui.text(g, font, Component.translatable("crusadecraft.diplo.contract", e.vassalTax(), e.vassalLevy()), rx, y, Ui.TEXT);
            y += 12;
        }
        if (e.tributeToMe()) Ui.text(g, font, Component.translatable("crusadecraft.diplo.tribute_in", e.tributePerDay()), rx, y, Ui.GREEN);
        if (e.tributeFromMe()) Ui.text(g, font, Component.translatable("crusadecraft.diplo.tribute_out", e.tributePerDay()), rx, y, Ui.RED);

        // Libellé du contrat proposé
        int cells = e.ai() ? DiplomacyRules.Proposal.values().length + 1 : DiplomacyRules.Proposal.values().length - 1;
        int cy = top + h - 128 + 20 + ((cells + 1) / 2) * 18 + 2;
        int half = rw / 2;
        contractLabel(g, Component.translatable("crusadecraft.diplo.offer_tax"), rx, half - 66, cy);
        Ui.centered(g, font, Component.literal(tax + "%"), rx + half - 33, cy + 4, Ui.TEXT);
        contractLabel(g, Component.translatable("crusadecraft.diplo.offer_levy"), rx + half + 4, rw - half - 66, cy);
        Ui.centered(g, font, Component.literal(levy + "%"), rx + rw - 29, cy + 4, Ui.TEXT);
        if (!view.sealUsable()) {
            String problem = font.plainSubstrByWidth(view.sealProblem(), Math.round((w - 196) / 0.8f));
            Ui.scaled(g, font, Component.literal(problem), left + 186, top + h - 11, 0.8f, 0xFFE0A070, false);
        }
    }

    private void contractLabel(GuiGraphics g, Component label, int x, int width, int y) {
        float k = Math.min(1f, Math.max(1, width) / (float) Math.max(1, font.width(label)));
        Ui.scaled(g, font, label, x, y + 4 + Math.round((1 - k) * 3), k, Ui.TEXT_MUTED, false);
    }

    private List<Component> opinionTooltip(DiplomacyView.Entry e) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("crusadecraft.diplo.opinion.title", e.ruler()).withStyle(ChatFormatting.GOLD));
        for (Opinion.Line l : e.opinionLines()) {
            Component label = l.arg() == null ? Component.translatable(l.key()) : Component.translatable(l.key(), Component.translatable(l.arg()));
            tip.add(label.copy().withStyle(ChatFormatting.GRAY).append(Component.literal(" " + signed(l.value()))
                    .withStyle(l.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
        return tip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = left + 8, ly = top + 28, lw = 172, lh = h - 54;
        if (Ui.inside(mouseX, mouseY, lx, ly, lw, lh)) {
            int index = listScroll + (int) ((mouseY - ly - 2) / ROW);
            if (index >= 0 && index < view.entries().size()) {
                selected = view.entries().get(index).id();
                lastSelected = selected;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside(mouseX, mouseY, left + 8, top + 28, 172, h - 54)) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
