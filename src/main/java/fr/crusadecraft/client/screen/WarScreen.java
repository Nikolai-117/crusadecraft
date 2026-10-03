package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.war.CasusBelli;
import fr.crusadecraft.core.war.War;
import fr.crusadecraft.core.war.WarRules;
import fr.crusadecraft.core.war.WarView;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WarPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La Table de guerre : à gauche vos guerres et les royaumes qu'on pourrait attaquer, votre armée en dessous ;
 * à droite la guerre choisie (score, forces, journal, paix) ou le royaume visé (motifs, province, déclaration).
 */
public class WarScreen extends Screen {
    private static final int ROW = 18;
    private static final int LIST_W = 172;
    /** Haut de la grille des motifs, sous l'en-tête du royaume visé. */
    private static final int CB_TOP = 76;
    private static UUID lastSelected;

    /** Une ligne de la liste : un intertitre, une guerre ou un royaume. */
    private record Row(Component header, WarView.WarLine war, WarView.Target target) {}

    private WarView view;
    private UUID selected = lastSelected;
    private CasusBelli cb;
    private int provinceIndex;
    private boolean confirm;
    private int left, top, w, h;
    private int listScroll;
    private final List<Row> rows = new ArrayList<>();
    private List<Component> pendingTooltip;

    public WarScreen(WarView view) {
        super(Component.translatable("crusadecraft.war.title"));
        this.view = view;
    }

    public void refresh(WarView newView) {
        this.view = newView;
        confirm = false;
        rebuildWidgets();
    }

    private void send(WarPackets.WarAction action) {
        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(action);
    }

    // ------------------------------------------------------------------ noms et textes partagés

    static Component partyName(WarView.Party p) {
        return p.ai() ? AiNames.realm(p.rank(), p.label()) : Component.literal(p.label());
    }

    static WarView.Party enemy(WarView.WarLine w) {
        return w.side() > 0 ? w.defender() : w.attacker();
    }

    /** Le score vu du joueur (+100 : son camp gagne). */
    static int myScore(WarView.WarLine w) {
        return w.side() * w.score();
    }

    static int scoreColor(int score) {
        return score >= 10 ? Ui.GREEN : score <= -10 ? Ui.RED : 0xFFC99A3C;
    }

    static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    /** Une ligne du journal de guerre. */
    static Component event(War.Event e) {
        List<String> a = e.args();
        Component text;
        if (e.key().equals("crusadecraft.war.log.declared") && !a.isEmpty()) {
            CasusBelli cb = CasusBelli.byName(a.get(0));
            Component cbName = cb == null ? Component.literal(a.get(0)) : Component.translatable(cb.key());
            text = a.size() > 1 && !a.get(1).isEmpty() ? Component.translatable("crusadecraft.war.log.declared.target", cbName, a.get(1))
                    : Component.translatable(e.key(), cbName);
        } else if (e.key().equals("crusadecraft.war.log.peace") && !a.isEmpty() && a.get(0).equals("WHITE")) {
            text = Component.translatable("crusadecraft.war.log.peace_white");
        } else {
            text = Component.translatable(e.key(), a.toArray());
        }
        return Component.translatable("crusadecraft.war.log.day", e.day()).append(" ").append(text);
    }

    // ------------------------------------------------------------------ liste

    private void buildRows() {
        rows.clear();
        if (!view.wars().isEmpty()) {
            rows.add(new Row(Component.translatable("crusadecraft.war.list.wars", view.wars().size()), null, null));
            for (WarView.WarLine w : view.wars()) rows.add(new Row(null, w, null));
        }
        rows.add(new Row(Component.translatable("crusadecraft.war.list.targets", view.targets().size()), null, null));
        for (WarView.Target t : view.targets()) rows.add(new Row(null, null, t));
    }

    private WarView.WarLine selectedWar() {
        for (WarView.WarLine w : view.wars()) if (w.id().equals(selected)) return w;
        return null;
    }

    private WarView.Target selectedTarget() {
        for (WarView.Target t : view.targets()) if (t.party().id().equals(selected)) return t;
        return null;
    }

    /** À défaut de choix, la première guerre, sinon le premier royaume. */
    private void ensureSelection() {
        if (selectedWar() != null || selectedTarget() != null) return;
        if (!view.wars().isEmpty()) selected = view.wars().get(0).id();
        else if (!view.targets().isEmpty()) selected = view.targets().get(0).party().id();
        else selected = null;
    }

    private WarView.CbOption cbOption(WarView.Target t) {
        if (t == null) return null;
        WarView.CbOption first = null;
        for (WarView.CbOption o : t.cbs()) {
            if (o.cb() == cb) return o;
            if (first == null && o.problem() == null) first = o;
        }
        if (first == null && !t.cbs().isEmpty()) first = t.cbs().get(0);
        if (first != null) cb = first.cb();
        return first;
    }

    // ------------------------------------------------------------------ boutons

    @Override
    protected void init() {
        w = Math.min(470, width - 10);
        h = Math.min(262, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        buildRows();
        ensureSelection();

        int lx = left + 8, half = (LIST_W - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(lx, top + h - 22, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.war.map"), b -> {
            if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
        }).bounds(lx + half + 4, top + h - 22, LIST_W - half - 4, 16).tooltip(Tooltip.create(Component.translatable("crusadecraft.war.map.tip"))).build());

        int ay = armyY();
        Button army = Button.builder(Component.translatable(view.raised() ? "crusadecraft.war.army.disband" : "crusadecraft.war.army.raise"),
                b -> send(WarPackets.WarAction.of(view.raised() ? WarPackets.WarAction.Kind.DISBAND : WarPackets.WarAction.Kind.RAISE)))
                .bounds(lx + 4, ay + 23, LIST_W - 8, 15).build();
        army.setTooltip(Tooltip.create(Component.translatable(view.raised() ? "crusadecraft.war.army.disband.tip" : "crusadecraft.war.army.raise.tip",
                (int) Math.ceil(view.men() / 10.0))));
        addRenderableWidget(army);

        int rx = left + 186, rw = w - 196;
        WarView.WarLine war = selectedWar();
        if (war != null) {
            initPeace(war, rx, rw);
            return;
        }
        WarView.Target t = selectedTarget();
        if (t != null) initTarget(t, rx, rw);
    }

    private int armyY() {
        return top + h - 22 - 4 - 42;
    }

    private void initPeace(WarView.WarLine war, int rx, int rw) {
        int by = top + h - 22;
        int bw = (rw - 6) / 3;
        int i = 0;
        for (WarRules.Peace p : WarRules.Peace.values()) {
            WarRules.Verdict v = war.peace().get(p);
            MutableComponent label = Component.translatable(p.key() + ".short");
            if (p != WarRules.Peace.SURRENDER && v != null && war.leader()) label.withStyle(v.accepted() ? ChatFormatting.GREEN : ChatFormatting.RED);
            Button b = Button.builder(label,
                    btn -> send(new WarPackets.WarAction(WarPackets.WarAction.Kind.PEACE, war.id(), null, -1, p)))
                    .bounds(rx + i * (bw + 3), by, i == 2 ? rw - 2 * (bw + 3) : bw, 16).build();
            b.active = war.leader() && (p != WarRules.Peace.SURRENDER || view.sealOk());
            b.setTooltip(Tooltip.create(peaceTooltip(war, p, v)));
            addRenderableWidget(b);
            i++;
        }
    }

    private Component peaceTooltip(WarView.WarLine war, WarRules.Peace p, WarRules.Verdict v) {
        MutableComponent tip = Component.translatable(p.key()).withStyle(ChatFormatting.GOLD).append("\n")
                .append(Component.translatable(p.key() + ".desc").withStyle(ChatFormatting.GRAY));
        if (!war.leader()) {
            return tip.append("\n\n").append(Component.translatable("crusadecraft.war.not_leader").withStyle(ChatFormatting.RED));
        }
        if (p == WarRules.Peace.SURRENDER) {
            if (!view.sealOk()) tip.append("\n\n").append(Component.literal(view.sealProblem()).withStyle(ChatFormatting.RED));
            return tip;
        }
        if (v == null) {
            return tip.append("\n\n").append(Component.translatable("crusadecraft.war.peace.to_player").withStyle(ChatFormatting.GRAY));
        }
        tip.append("\n");
        for (WarRules.Factor f : v.factors()) {
            if (f.value() == 0) continue;
            tip.append("\n").append(Component.translatable(f.key()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + signed(f.value())).withStyle(f.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        tip.append("\n\n").append(Component.translatable(v.accepted() ? "crusadecraft.diplo.will_accept" : "crusadecraft.diplo.will_refuse")
                .withStyle(v.accepted() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (v.accepted()) tip.append("\n").append(Component.translatable("crusadecraft.diplo.seal_needed").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    private void initTarget(WarView.Target t, int rx, int rw) {
        WarView.CbOption current = cbOption(t);
        // Les motifs, sur deux colonnes.
        int cy = top + CB_TOP, cw = (rw - 4) / 2;
        int i = 0;
        for (WarView.CbOption o : t.cbs()) {
            boolean open = o.problem() == null;
            String mark = o.cb() == cb ? "» " : open ? "" : "· ";
            int col = i % 2, row = i / 2;
            Button b = Button.builder(Component.literal(mark).append(Component.translatable(o.cb().key())), btn -> {
                cb = o.cb();
                provinceIndex = 0;
                confirm = false;
                rebuildWidgets();
            }).bounds(rx + col * (cw + 4), cy + row * 18, cw, 16).build();
            b.setTooltip(Tooltip.create(cbTooltip(o)));
            addRenderableWidget(b);
            i++;
        }
        int after = cy + ((i + 1) / 2) * 18 + 4;
        // La province visée, si le motif en vise une.
        if (current != null && current.cb().targetsProvince() && !current.provinces().isEmpty()) {
            provinceIndex = Math.floorMod(provinceIndex, current.provinces().size());
            WarView.ProvinceOption p = current.provinces().get(provinceIndex);
            Button prov = Button.builder(Component.translatable("crusadecraft.war.province", p.name()), btn -> {
                provinceIndex++;
                confirm = false;
                rebuildWidgets();
            }).bounds(rx, after, rw, 16).build();
            prov.active = current.provinces().size() > 1;
            prov.setTooltip(Tooltip.create(Component.translatable("crusadecraft.war.province.tip", current.provinces().size())));
            addRenderableWidget(prov);
        }
        boolean allowed = t.problem() == null && current != null && current.problem() == null;
        Component label = confirm ? Component.translatable("crusadecraft.war.declare.confirm", partyName(t.party()))
                : Component.translatable("crusadecraft.war.declare");
        Button declare = Button.builder(label, btn -> {
            if (!confirm) {
                confirm = true;
                rebuildWidgets();
                return;
            }
            WarView.CbOption o = cbOption(t);
            int province = -1;
            if (o != null && o.cb().targetsProvince() && !o.provinces().isEmpty()) {
                province = o.provinces().get(Math.floorMod(provinceIndex, o.provinces().size())).id();
            }
            confirm = false;
            send(new WarPackets.WarAction(WarPackets.WarAction.Kind.DECLARE, t.party().id(), cb, province, null));
        }).bounds(rx, top + h - 22, rw, 16).build();
        declare.active = allowed;
        declare.setTooltip(Tooltip.create(allowed ? Component.translatable("crusadecraft.war.declare.tip")
                : Component.translatable(t.problem() != null ? t.problem() : current == null ? "crusadecraft.war.cb.none" : current.problem())));
        addRenderableWidget(declare);
    }

    private Component cbTooltip(WarView.CbOption o) {
        MutableComponent tip = Component.translatable(o.cb().key()).withStyle(ChatFormatting.GOLD).append("\n")
                .append(Component.translatable(o.cb().key() + ".desc").withStyle(ChatFormatting.GRAY));
        if (o.cb().prestigeCost() > 0) {
            tip.append("\n").append(Component.translatable("crusadecraft.war.cb.cost", o.cb().prestigeCost()).withStyle(ChatFormatting.YELLOW));
        }
        tip.append("\n\n").append(o.problem() == null ? Component.translatable("crusadecraft.war.cb.open").withStyle(ChatFormatting.GREEN)
                : Component.translatable(o.problem()).withStyle(ChatFormatting.RED));
        return tip;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component res = Component.translatable("crusadecraft.diplo.resources", view.gold(), view.prestige());
        Ui.text(g, font, res, left + w - 10 - font.width(res), top + 10, Ui.TEXT_MUTED);

        renderList(g, mouseX, mouseY);
        renderArmy(g, mouseX, mouseY);
        WarView.WarLine war = selectedWar();
        WarView.Target target = selectedTarget();
        if (war != null) renderWar(g, war, mouseX, mouseY);
        else if (target != null) renderTarget(g, target, mouseX, mouseY);
        else Ui.wrapped(g, font, Component.translatable("crusadecraft.war.nobody"), left + 186, top + 34, w - 196, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private int listHeight() {
        return armyY() - 4 - (top + 28);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int lx = left + 8, ly = top + 28, lw = LIST_W, lh = listHeight();
        Ui.inset(g, lx, ly, lw, lh);
        int visible = Math.max(1, (lh - 4) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, rows.size() - visible)));
        for (int i = listScroll; i < rows.size() && i < listScroll + visible; i++) {
            Row row = rows.get(i);
            int ry = ly + 2 + (i - listScroll) * ROW;
            if (row.header() != null) {
                Ui.text(g, font, row.header(), lx + 5, ry + 6, Ui.TEXT_TITLE);
                continue;
            }
            WarView.Party party = row.war() != null ? enemy(row.war()) : row.target().party();
            UUID id = row.war() != null ? row.war().id() : party.id();
            boolean sel = id.equals(selected);
            boolean hover = Ui.inside(mouseX, mouseY, lx + 2, ry, lw - 4, ROW - 1);
            g.fill(lx + 2, ry, lx + lw - 2, ry + ROW - 1, sel ? Ui.PANEL_HIGHLIGHT : hover ? 0xFF2C231B : 0xFF1C1612);
            g.fill(lx + 5, ry + 4, lx + 13, ry + 13, 0xFF000000 | party.color());
            String name = font.plainSubstrByWidth(partyName(party).getString(), lw - 24);
            Ui.text(g, font, Component.literal(name), lx + 17, ry + 1, sel ? Ui.TEXT_TITLE : Ui.TEXT);
            Component sub;
            int subColor;
            String right;
            int rightColor;
            if (row.war() != null) {
                sub = Component.translatable(row.war().side() > 0 ? "crusadecraft.war.list.attacking" : "crusadecraft.war.list.defending");
                subColor = DiploStatus.GUERRE.color();
                right = signed(myScore(row.war()));
                rightColor = scoreColor(myScore(row.war()));
            } else {
                sub = Component.translatable(row.target().status().key());
                subColor = row.target().status().color();
                right = String.valueOf(row.target().strength());
                rightColor = Ui.TEXT_MUTED;
            }
            g.pose().pushPose();
            g.pose().translate(lx + 17, ry + 10, 0);
            g.pose().scale(0.7f, 0.7f, 1);
            g.drawString(font, sub, 0, 0, subColor, false);
            g.drawString(font, Component.literal(right), Math.round((lw - 24) / 0.7f) - font.width(right), 0, rightColor, false);
            g.pose().popPose();
            if (hover && row.target() != null) {
                pendingTooltip = List.of(partyName(party), Component.translatable("crusadecraft.war.strength.tip", row.target().strength())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private void renderArmy(GuiGraphics g, int mouseX, int mouseY) {
        int ax = left + 8, ay = armyY();
        Ui.inset(g, ax, ay, LIST_W, 42);
        Component men = Component.translatable("crusadecraft.war.army.men", view.men(), view.maxMen());
        Ui.text(g, font, men, ax + 5, ay + 4, Ui.TEXT);
        Component state = view.raised() ? Component.translatable("crusadecraft.war.army.state.raised", view.upkeep())
                : Component.translatable("crusadecraft.war.army.state.home");
        Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(state.getString(), Math.round((LIST_W - 10) / 0.8f))), ax + 5, ay + 14, 0.8f,
                view.raised() ? 0xFFE0C060 : Ui.TEXT_MUTED, false);
        if (Ui.inside(mouseX, mouseY, ax, ay, LIST_W, 22)) {
            pendingTooltip = List.of(Component.translatable("crusadecraft.war.army.title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("crusadecraft.war.army.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    private void renderWar(GuiGraphics g, WarView.WarLine war, int mouseX, int mouseY) {
        int rx = left + 186, rw = w - 196;
        int y = top + 30;
        WarView.Party mine = war.side() > 0 ? war.attacker() : war.defender();
        WarView.Party theirs = enemy(war);
        Ui.shield(g, font, rx, y, 20, 25, mine.color(), Ui.initial(mine.label()));
        Ui.shield(g, font, rx + rw - 20, y, 20, 25, theirs.color(), Ui.initial(theirs.label()));
        Component vs = Component.translatable("crusadecraft.war.versus", partyName(mine), partyName(theirs));
        float k = Math.min(1.1f, (rw - 52) / (float) Math.max(1, font.width(vs)));
        Ui.scaled(g, font, vs, rx + rw / 2, y + 3, k, Ui.TEXT_TITLE, true);
        Component goal = war.target().isEmpty() ? Component.translatable(war.cb().key())
                : Component.translatable("crusadecraft.war.goal", Component.translatable(war.cb().key()), war.target());
        Component meta = Component.translatable("crusadecraft.war.meta", goal, war.days());
        float mk = Math.min(1f, (rw - 52) / (float) Math.max(1, font.width(meta)));
        Ui.scaled(g, font, meta, rx + rw / 2, y + 15, mk, Ui.TEXT_MUTED, true);

        // Le score, du point de vue du joueur : à droite du centre, il gagne.
        y += 32;
        int score = myScore(war);
        Component sc = Component.translatable("crusadecraft.war.score", signed(score));
        Ui.text(g, font, sc, rx, y, scoreColor(score));
        if (Ui.inside(mouseX, mouseY, rx, y - 1, rw, 20)) pendingTooltip = scoreTooltip(war);
        int bx = rx + font.width(sc) + 8, bw = rw - (bx - rx);
        int mid = bx + bw / 2;
        g.fill(bx, y + 1, bx + bw, y + 8, 0xFF1C1612);
        g.renderOutline(bx, y + 1, bw, 7, Ui.GOLD_DARK);
        int len = (int) Math.round(bw / 2.0 * Math.min(100, Math.abs(score)) / 100.0);
        if (score > 0) g.fill(mid, y + 2, mid + len, y + 7, Ui.GREEN);
        else if (score < 0) g.fill(mid - len, y + 2, mid, y + 7, Ui.RED);
        g.fill(mid, y, mid + 1, y + 9, Ui.GOLD);

        // Forces et fatigue des deux camps.
        y += 14;
        int col = (rw - 8) / 2;
        forces(g, Component.translatable("crusadecraft.war.mine"), war.myStrength(), war.myExhaustion(), rx, y, col);
        forces(g, Component.translatable("crusadecraft.war.theirs"), war.enemyStrength(), war.enemyExhaustion(), rx + col + 8, y, col);
        List<String> myAllies = war.side() > 0 ? war.attackerAllies() : war.defenderAllies();
        List<String> theirAllies = war.side() > 0 ? war.defenderAllies() : war.attackerAllies();
        if (Ui.inside(mouseX, mouseY, rx, y, rw, 22) && (!myAllies.isEmpty() || !theirAllies.isEmpty())) {
            List<Component> tip = new ArrayList<>();
            if (!myAllies.isEmpty()) tip.add(Component.translatable("crusadecraft.war.allies.mine", String.join(", ", myAllies)).withStyle(ChatFormatting.GREEN));
            if (!theirAllies.isEmpty()) tip.add(Component.translatable("crusadecraft.war.allies.theirs", String.join(", ", theirAllies)).withStyle(ChatFormatting.RED));
            pendingTooltip = tip;
        }
        y += 24;
        if (!myAllies.isEmpty() || !theirAllies.isEmpty()) {
            Component allies = Component.translatable("crusadecraft.war.allies.count", myAllies.size(), theirAllies.size());
            Ui.scaled(g, font, allies, rx, y - 2, 0.8f, Ui.TEXT_MUTED, false);
            y += 8;
        }
        if (!war.occupied().isEmpty()) {
            Component occ = Component.translatable("crusadecraft.war.occupied", String.join(", ", war.occupied()));
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(occ.getString(), rw)), rx, y, 0xFFE0C060);
            y += 11;
        }
        // Le journal : les faits d'armes les plus récents d'abord.
        Ui.text(g, font, Component.translatable("crusadecraft.war.journal"), rx, y + 2, Ui.TEXT_TITLE);
        y += 13;
        int bottom = top + h - 26;
        float js = 0.8f;
        for (War.Event e : war.events()) {
            if (y + 8 > bottom) break;
            String line = fit(event(e).getString(), Math.round((rw - 24) / js));
            Ui.scaled(g, font, Component.literal(line), rx, y, js, Ui.TEXT, false);
            if (e.delta() != 0) {
                int signedDelta = war.side() * e.delta();
                String d = signed(signedDelta);
                Ui.scaled(g, font, Component.literal(d), rx + rw - Math.round(font.width(d) * js), y, js, scoreColor(signedDelta * 10), false);
            }
            y += 9;
        }
        if (war.events().isEmpty()) Ui.scaled(g, font, Component.translatable("crusadecraft.war.journal.empty"), rx, y, js, Ui.TEXT_MUTED, false);
    }

    /** Le texte s'il tient, sinon coupé avec des points de suspension. */
    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    private List<Component> scoreTooltip(WarView.WarLine war) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("crusadecraft.war.score.title").withStyle(ChatFormatting.GOLD));
        for (String part : Component.translatable("crusadecraft.war.score.desc").getString().split("\\n")) {
            tip.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private void forces(GuiGraphics g, Component label, int strength, int exhaustion, int x, int y, int width) {
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(label.getString() + " : " + strength, width)), x, y, Ui.TEXT);
        Component ex = Component.translatable("crusadecraft.war.exhaustion", exhaustion);
        Ui.scaled(g, font, ex, x, y + 11, 0.7f, Ui.TEXT_MUTED, false);
        int bx = x + Math.round(font.width(ex) * 0.7f) + 4;
        if (x + width - bx > 4) Ui.bar(g, bx, y + 12, x + width - bx, 4, exhaustion / 100.0, exhaustion >= 60 ? Ui.RED : 0xFFC99A3C);
    }

    private void renderTarget(GuiGraphics g, WarView.Target t, int mouseX, int mouseY) {
        int rx = left + 186, rw = w - 196;
        int y = top + 30;
        Ui.shield(g, font, rx, y, 20, 25, t.party().color(), Ui.initial(t.party().label()));
        Component name = partyName(t.party());
        float k = Math.min(1.15f, (rw - 30) / (float) Math.max(1, font.width(name)));
        Ui.scaled(g, font, name, rx + 26, y, k, Ui.TEXT_TITLE, false);
        Component status = Component.translatable(t.status().key());
        Ui.text(g, font, status, rx + 26, y + 13, t.status().color());
        Component strength = Component.translatable("crusadecraft.war.strength.compare", t.strength(), view.raised() ? view.men() : view.men() / 3);
        Ui.text(g, font, strength, rx + rw - font.width(strength), y + 13, Ui.TEXT_MUTED);
        if (Ui.inside(mouseX, mouseY, rx + rw - font.width(strength), y + 12, font.width(strength), 10)) {
            pendingTooltip = List.of(Component.translatable("crusadecraft.war.strength.compare.tip").withStyle(ChatFormatting.GRAY));
        }
        y += 32;
        boolean blocked = t.problem() != null;
        Ui.text(g, font, Component.translatable(blocked ? "crusadecraft.war.blocked" : "crusadecraft.war.cb.choose"), rx, y,
                blocked ? 0xFFE0A070 : Ui.TEXT_TITLE);
        // Sous les motifs (et la province) : ce qui interdit la guerre, ou ce que ce motif ferait.
        WarView.CbOption o = cbOption(t);
        if (o == null) return;
        int rowsCb = (t.cbs().size() + 1) / 2;
        int dy = top + CB_TOP + rowsCb * 18 + 4 + (o.cb().targetsProvince() && !o.provinces().isEmpty() ? 20 : 0);
        int room = top + h - 26 - dy;
        if (room >= 9) {
            String problem = blocked ? t.problem() : o.problem();
            Component desc = problem == null ? Component.translatable(o.cb().key() + ".desc") : Component.translatable(problem);
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(desc, rw);
            int maxLines = room / 10;
            for (int i = 0; i < lines.size() && i < maxLines; i++) {
                g.drawString(font, lines.get(i), rx, dy + i * 10, problem == null ? Ui.TEXT_MUTED : 0xFFE0A070, false);
            }
        }
    }

    // ------------------------------------------------------------------ souris

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = left + 8, ly = top + 28, lh = listHeight();
        if (Ui.inside(mouseX, mouseY, lx, ly, LIST_W, lh)) {
            int index = listScroll + (int) ((mouseY - ly - 2) / ROW);
            if (index >= 0 && index < rows.size() && rows.get(index).header() == null) {
                Row row = rows.get(index);
                selected = row.war() != null ? row.war().id() : row.target().party().id();
                lastSelected = selected;
                cb = null;
                provinceIndex = 0;
                confirm = false;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside(mouseX, mouseY, left + 8, top + 28, LIST_W, listHeight())) {
            listScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Pour les tests visuels : présélectionne une ligne. */
    public void select(UUID id) {
        selected = id;
        if (minecraft != null) rebuildWidgets();
    }
}
