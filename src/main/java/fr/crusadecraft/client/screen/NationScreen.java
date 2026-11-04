package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.diplomacy.DiploStatus;
import fr.crusadecraft.core.diplomacy.Treaty;
import fr.crusadecraft.core.nation.NationService;
import fr.crusadecraft.core.nation.NationView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.NationPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/**
 * L'écran d'une nation (P41) : son histoire, ses villes, ses relations et, pour son souverain, le courrier reçu et les
 * gestes de la diplomatie. Pour les autres, une simple fiche à lire.
 */
public class NationScreen extends Screen {
    private enum Tab { HISTOIRE, VILLES, RELATIONS, COURRIER }

    private static final int ROW = 20;
    private NationView view;
    private Tab tab = Tab.HISTOIRE;
    private int left, top, w, h, scroll;
    private UUID selected;
    private UUID letter;

    public NationScreen(NationView view) {
        super(Component.literal(view.title()));
        this.view = view;
    }

    /** Ouvre l'écran, ou le rafraîchit s'il l'est déjà, sans changer d'onglet ni perdre la sélection. */
    public static void open(NationView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NationScreen s && s.view.id().equals(view.id())) {
            s.view = view;
            s.rebuildWidgets();
            return;
        }
        mc.setScreen(new NationScreen(view));
    }

    @Override
    protected void init() {
        w = Math.min(470, width - 12);
        h = Math.min(280, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int tx = left + 10, ty = top + 50;
        int tw = (w - 20) / (view.mine() ? 4 : 3);
        for (Tab t : Tab.values()) {
            if (t == Tab.COURRIER && !view.mine()) continue;
            Component label = t == Tab.COURRIER ? Component.translatable("crusadecraft.nation.tab.courrier", view.mail().size())
                    : Component.translatable("crusadecraft.nation.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
            Button b = Button.builder(label, x -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(tx, ty, tw - 2, 16).build();
            b.active = tab != t;
            addRenderableWidget(b);
            tx += tw;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 64, top + h - 24, 54, 16).build());
        if (tab == Tab.RELATIONS && view.mine()) relationButtons();
        if (tab == Tab.COURRIER) mailButtons();
        if (tab == Tab.VILLES && !view.cities().isEmpty()) {
            NationView.CityRow capital = view.cities().stream().filter(NationView.CityRow::capital).findFirst().orElse(view.cities().get(0));
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.ai.show_on_map"), b -> {
                MapScreen.centerNextOn(capital.x(), capital.z());
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
            }).bounds(left + 10, top + h - 24, 110, 16).build());
        }
    }

    /** Pour le banc d'essai : tel onglet (0 histoire, 1 villes, 2 relations, 3 courrier), le premier royaume choisi. */
    public void showForShots(int index) {
        tab = Tab.values()[index];
        scroll = 0;
        if (!view.relations().isEmpty()) selected = view.relations().get(0).id();
        rebuildWidgets();
    }

    private void act(int op, int arg) {
        CrusadeNetwork.toServer(new NationPackets.Act(selected, op, arg, letter));
    }

    private NationView.Rel selectedRel() {
        for (NationView.Rel r : view.relations()) if (r.id().equals(selected)) return r;
        return null;
    }

    /** Les gestes de la diplomatie, pour le royaume choisi : ils tiennent sur deux lignes au bas de l'écran. */
    private void relationButtons() {
        NationView.Rel rel = selectedRel();
        if (rel == null) return;
        int y = top + h - 64, x = left + 10, bw = (w - 20) / Treaty.values().length - 2;
        int i = 0;
        for (Treaty t : Treaty.values()) {
            boolean has = rel.treaties().contains(t.ordinal());
            Component label = Component.literal(has ? "− " : "+ ").append(Component.translatable(t.key() + ".court"));
            Button b = Button.builder(label, btn -> act(has ? NationService.OP_BREAK : NationService.OP_PROPOSE, t.ordinal()))
                    .bounds(x + i * (bw + 2), y, bw, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(has ? "crusadecraft.nation.break" : "crusadecraft.nation.propose", Component.translatable(t.key())))).build();
            b.active = !rel.atWar();
            addRenderableWidget(b);
            i++;
        }
        y += 18;
        bw = (w - 20) / 3 - 2;
        Button gift = Button.builder(Component.translatable("crusadecraft.nation.gift", NationService.GIFT), b -> act(NationService.OP_GIFT, -1))
                .bounds(x, y, bw, 16).build();
        gift.active = view.gold() >= NationService.GIFT && !rel.atWar();
        addRenderableWidget(gift);
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.insult"), b -> act(NationService.OP_INSULT, -1))
                .bounds(x + bw + 2, y, bw, 16).build());
        Button war = Button.builder(Component.translatable("crusadecraft.nation.war"), b -> act(NationService.OP_WAR, -1))
                .bounds(x + 2 * (bw + 2), y, bw, 16).build();
        war.active = rel.canWar();
        addRenderableWidget(war);
    }

    private NationView.Mail selectedMail() {
        for (NationView.Mail m : view.mail()) if (m.id().equals(letter)) return m;
        return view.mail().isEmpty() ? null : view.mail().get(0);
    }

    private void mailButtons() {
        NationView.Mail m = selectedMail();
        if (m == null) return;
        letter = m.id();
        int y = top + h - 24, x = left + 10;
        if (m.treaty() >= 0) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.mail.accept"), b -> act(NationService.OP_ACCEPT, -1)).bounds(x, y, 80, 16).build());
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.mail.refuse"), b -> act(NationService.OP_REFUSE, -1)).bounds(x + 84, y, 80, 16).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.mail.done"), b -> act(NationService.OP_READ, -1)).bounds(x, y, 80, 16).build());
        }
    }

    private int bodyTop() {
        return top + 70;
    }

    private int bodyHeight() {
        int bottom = tab == Tab.RELATIONS && view.mine() && selectedRel() != null ? 70 : 30;
        return h - 70 - bottom;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) (delta * 14));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left + 10, lw = w - 20, ly = bodyTop(), lh = bodyHeight();
        if (mx >= lx && mx <= lx + (tab == Tab.COURRIER ? lw / 3 : lw) && my >= ly && my <= ly + lh) {
            int index = ((int) my - ly + scroll) / ROW;
            if (tab == Tab.RELATIONS && view.mine() && index >= 0 && index < view.relations().size()) {
                selected = view.relations().get(index).id();
                rebuildWidgets();
                return true;
            }
            if (tab == Tab.COURRIER && index >= 0 && index < view.mail().size()) {
                letter = view.mail().get(index).id();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.shield(g, font, left + 10, top + 8, 30, 37, view.color(), Ui.initial(view.title().substring(view.title().lastIndexOf(' ') + 1)));
        Ui.scaled(g, font, Component.literal(view.title()), left + 48, top + 9, 1.4f, Ui.TEXT_TITLE, false);
        Component who = Component.translatable(view.mine() ? "crusadecraft.nation.header.mine" : "crusadecraft.nation.header.ruler", view.ruler());
        Ui.fitted(g, font, who, left + 48, top + 24, w - 58, Ui.TEXT);
        Component facts = Component.translatable("crusadecraft.nation.header.facts", view.culture().isEmpty() ? "—" : view.culture(),
                view.faith().isEmpty() ? "—" : view.faith(), view.gold(), view.prestige());
        Ui.fitted(g, font, facts, left + 48, top + 36, w - 58, Ui.TEXT_MUTED);
        int lx = left + 10, lw = w - 20, ly = bodyTop(), lh = bodyHeight();
        Ui.inset(g, lx, ly, lw, lh);
        g.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
        int content = switch (tab) {
            case HISTOIRE -> history(g, lx + 8, ly + 6 - scroll, lw - 16);
            case VILLES -> cities(g, lx + 8, ly + 6 - scroll, lw - 16);
            case RELATIONS -> relations(g, lx + 2, ly + 2 - scroll, lw - 4, mx, my);
            case COURRIER -> mail(g, lx + 2, ly + 2 - scroll, lw - 4, lh);
        };
        g.disableScissor();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - lh + 8));
        super.render(g, mx, my, partial);
    }

    private int paragraphs(GuiGraphics g, List<String> texts, int x, int y0, int width, int color) {
        int y = y0;
        for (String p : texts) y += Ui.wrapped(g, font, Component.literal(p), x, y, width, color) + 6;
        return y - y0;
    }

    private int history(GuiGraphics g, int x, int y, int width) {
        if (view.history().isEmpty()) return Ui.wrapped(g, font, Component.translatable("crusadecraft.nation.history.empty"), x, y, width, Ui.TEXT_MUTED);
        return paragraphs(g, view.history(), x, y, width, Ui.TEXT);
    }

    private int cities(GuiGraphics g, int x, int y0, int width) {
        if (view.cities().isEmpty()) return Ui.wrapped(g, font, Component.translatable("crusadecraft.nation.cities.empty"), x, y0, width, Ui.TEXT_MUTED);
        int y = y0;
        for (NationView.CityRow c : view.cities()) {
            Component name = Component.translatable(c.capital() ? "crusadecraft.nation.city.capital" : "crusadecraft.nation.city.row", c.name());
            Ui.text(g, font, name, x, y, Ui.TEXT_TITLE);
            y += 11;
            Ui.text(g, font, Component.translatable("crusadecraft.nation.city.facts", c.people(), c.x(), c.z()), x, y, Ui.TEXT_MUTED);
            y += 12;
            y += paragraphs(g, c.history(), x, y, width, Ui.TEXT) + 4;
        }
        return y - y0;
    }

    private int relations(GuiGraphics g, int x, int y0, int width, int mx, int my) {
        if (view.relations().isEmpty()) return Ui.wrapped(g, font, Component.translatable("crusadecraft.nation.relations.empty"), x + 6, y0 + 4, width - 12, Ui.TEXT_MUTED);
        int y = y0;
        for (NationView.Rel r : view.relations()) {
            if (r.id().equals(selected)) g.fill(x, y, x + width, y + ROW - 1, Ui.PANEL_HIGHLIGHT);
            g.fill(x + 4, y + 3, x + 10, y + ROW - 4, 0xFF000000 | r.color());
            Ui.fitted(g, font, Component.literal(r.name()), x + 16, y + 6, width / 2 - 20, Ui.TEXT);
            DiploStatus[] all = DiploStatus.values();
            DiploStatus status = all[Mth.clamp(r.status(), 0, all.length - 1)];
            Ui.fitted(g, font, Component.translatable(status.key()), x + width / 2, y + 6, width / 4 - 4, status.color());
            int oc = r.opinion() >= 20 ? Ui.GREEN : r.opinion() <= -20 ? Ui.RED : Ui.TEXT_MUTED;
            Ui.text(g, font, Component.translatable("crusadecraft.nation.opinion", r.opinion()), x + width * 3 / 4, y + 6, oc);
            y += ROW;
        }
        return y - y0;
    }

    private int mail(GuiGraphics g, int x, int y0, int width, int height) {
        if (view.mail().isEmpty()) return Ui.wrapped(g, font, Component.translatable("crusadecraft.nation.mail.empty"), x + 6, y0 + 4, width - 12, Ui.TEXT_MUTED);
        int listW = width / 3, y = y0;
        NationView.Mail current = selectedMail();
        for (NationView.Mail m : view.mail()) {
            if (m == current) g.fill(x, y, x + listW, y + ROW - 1, Ui.PANEL_HIGHLIGHT);
            Ui.fitted(g, font, Component.literal(m.sender()), x + 4, y + 1, listW - 8, Ui.TEXT);
            Ui.fitted(g, font, Component.literal(m.subject()), x + 4, y + 10, listW - 8, Ui.TEXT_MUTED);
            y += ROW;
        }
        int total = y - y0;
        if (current != null) {
            int tx = x + listW + 8, tw = width - listW - 14, ty = y0 + 4 + scroll;
            ty += Ui.wrapped(g, font, Component.literal(current.subject()), tx, ty, tw, Ui.TEXT_TITLE) + 2;
            ty += Ui.wrapped(g, font, Component.translatable("crusadecraft.nation.mail.from", current.sender()), tx, ty, tw, Ui.TEXT_MUTED) + 6;
            Ui.wrapped(g, font, Component.literal(current.body()), tx, ty, tw, Ui.TEXT);
        }
        return total;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
