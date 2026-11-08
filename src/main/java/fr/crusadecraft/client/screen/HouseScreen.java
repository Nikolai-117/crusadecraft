package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.court.HouseView;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtLifePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * La maison du souverain : à gauche ses membres (conjoints et enfants venus de la généalogie MineColonies), à
 * droite les citoyens qu'on peut y accueillir, ou les maisons IA à qui demander un mariage pour le membre choisi.
 */
public class HouseScreen extends Screen {
    private static final int ROW = 22;
    private static boolean marryMode;

    private HouseView view;
    private int selected = -1;
    private int left, top, w, h;

    public HouseScreen(HouseView view) {
        super(Component.translatable("crusadecraft.house.title"));
        this.view = view;
    }

    public void refresh(HouseView v) {
        this.view = v;
        if (selected >= v.members().size()) selected = -1;
        rebuildWidgets();
    }

    private void send(CourtLifePackets.HouseAction a) {
        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(a);
    }

    private HouseView.Member selectedMember() {
        return selected >= 0 && selected < view.members().size() ? view.members().get(selected) : null;
    }

    private int colW() {
        return (w - 28) / 2;
    }

    /** La hauteur de la liste des membres : la bande de la vie de la cour (P26) prend le bas de la colonne. */
    private int listH() {
        return h - 56 - (view.court() != null ? 30 : 0);
    }

    @Override
    protected void init() {
        w = Math.min(460, width - 10);
        h = Math.min(256, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int cw = colW(), rx = left + 20 + cw;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + w - 88, top + h - 22, 80, 16).build(KitButton::new));
        // Le registre des lignées (P13) : les maisons et les communautés du monde, au-delà de la vôtre.
        // Ouvert sur la maison du royaume quand on la connaît : les deux généalogies se rejoignent là (P26).
        java.util.UUID realmId = view.court() == null ? null : view.court().realm();
        Button lineages = Button.builder(Component.translatable("crusadecraft.lineage.open"), b -> {
            if (minecraft != null && minecraft.getConnection() != null) {
                fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.LineagePackets.Request(null, realmId));
            }
        }).bounds(left + w / 2 - 60, top + h - 22, 120, 16).build(KitButton::new);
        lineages.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.lineage.open.tip")));
        addRenderableWidget(lineages);
        if (view.court() != null) {
            HouseView.Court court = view.court();
            Component label = Component.translatable("crusadecraft.feast.button", court.feastCost());
            Button feast = Button.builder(label, b -> send(CourtLifePackets.HouseAction.of(CourtLifePackets.HouseAction.Kind.FEAST)))
                    .bounds(left + 11, top + 28 + listH() + 12, Math.min(colW() - 6, font.width(label) + 12), 15).build(KitButton::new);
            feast.active = court.problem().isEmpty();
            feast.setTooltip(Tooltip.create(court.problem().isEmpty() ? Component.translatable("crusadecraft.feast.tip")
                    : Component.translatable(court.problem(), court.cooldown())));
            addRenderableWidget(feast);
        }
        HouseView.Member m = selectedMember();
        Button heir = Button.builder(Component.translatable("crusadecraft.house.make_heir"), b -> {
            HouseView.Member s = selectedMember();
            if (s != null) send(new CourtLifePackets.HouseAction(CourtLifePackets.HouseAction.Kind.HEIR, s.ref(), null));
        }).bounds(left + 8, top + h - 22, 120, 16).build(KitButton::new);
        heir.active = m != null && !m.heir();
        heir.setTooltip(Tooltip.create(Component.translatable("crusadecraft.house.make_heir.tip")));
        addRenderableWidget(heir);
        Button adoptMode = Button.builder(Component.translatable("crusadecraft.house.mode.adopt"), b -> {
            marryMode = false;
            rebuildWidgets();
        }).bounds(rx, top + 30, cw / 2 - 2, 14).build(KitButton::new);
        adoptMode.active = marryMode;
        Button marry = Button.builder(Component.translatable("crusadecraft.house.mode.marry"), b -> {
            marryMode = true;
            rebuildWidgets();
        }).bounds(rx + cw / 2 + 2, top + 30, cw - cw / 2 - 2, 14).build(KitButton::new);
        marry.active = !marryMode;
        addRenderableWidget(adoptMode);
        addRenderableWidget(marry);
        int y = top + 50;
        int bottom = top + h - 30;
        if (!marryMode) {
            for (HouseView.Candidate c : view.candidates()) {
                if (y + ROW > bottom) break;
                Button b = Button.builder(Component.translatable("crusadecraft.house.adopt", view.adoptCost()),
                        btn -> send(new CourtLifePackets.HouseAction(CourtLifePackets.HouseAction.Kind.ADOPT, c.ref(), null)))
                        .bounds(rx + cw - 92, y + 2, 92, 16).build(KitButton::new);
                b.active = view.size() < view.max() && view.prestige() >= view.adoptCost();
                b.setTooltip(Tooltip.create(Component.translatable("crusadecraft.house.adopt.tip", view.adoptCost())));
                addRenderableWidget(b);
                y += ROW;
            }
        } else if (m != null && m.eligible()) {
            for (HouseView.Match match : view.matches()) {
                if (y + ROW > bottom) break;
                Button b = Button.builder(Component.translatable("crusadecraft.house.propose"),
                        btn -> send(new CourtLifePackets.HouseAction(CourtLifePackets.HouseAction.Kind.PROPOSE, m.ref(), match.realm())))
                        .bounds(rx + cw - 70, y + 2, 70, 16).build(KitButton::new);
                b.active = match.blocked().isEmpty();
                b.setTooltip(Tooltip.create(matchTooltip(match)));
                addRenderableWidget(b);
                y += ROW;
            }
        }
    }

    private Component matchTooltip(HouseView.Match m) {
        MutableComponent tip = AiNames.realm(m.rank(), m.place()).copy().withStyle(ChatFormatting.GOLD).append("\n")
                .append(AiNames.house(m.house()).copy().withStyle(ChatFormatting.GRAY)).append("\n")
                .append(Component.translatable(m.faith().key()).withStyle(m.faith().infidel() ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (!m.blocked().isEmpty()) return tip.append("\n\n").append(Component.translatable(m.blocked()).withStyle(ChatFormatting.RED));
        return tip.append("\n\n").append(Component.translatable(m.accepts() ? "crusadecraft.diplo.will_accept" : "crusadecraft.diplo.will_refuse")
                .withStyle(m.accepts() ? ChatFormatting.GREEN : ChatFormatting.RED)).append("\n")
                .append(Component.translatable("crusadecraft.diplo.seal_needed").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, title, left + 10, top + 8, 1.3f, Ui.TEXT_TITLE, false);
        Component meta = Component.translatable("crusadecraft.house.meta", view.size(), view.max(), view.prestige());
        Ui.text(g, font, meta, left + w - 10 - font.width(meta), top + 10, Ui.TEXT_MUTED);
        int cw = colW(), lx = left + 8, rx = left + 20 + cw, y = top + 28, ch = h - 56;
        Ui.inset(g, lx, y, cw + 4, ch);
        if (view.court() != null) renderCourt(g, lx, y + listH() + 2, cw);
        Ui.inset(g, rx - 2, y + 20, cw + 4, ch - 20);

        // Les membres de la maison.
        if (view.members().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.house.empty"), lx + 5, y + 6, cw - 6, Ui.TEXT_MUTED);
        int my = y + 3;
        for (int i = 0; i < view.members().size(); i++) {
            if (my + ROW > y + listH()) break;
            HouseView.Member m = view.members().get(i);
            boolean sel = i == selected;
            boolean hover = Ui.inside(mouseX, mouseY, lx + 2, my, cw, ROW - 1);
            g.fill(lx + 2, my, lx + cw + 2, my + ROW - 1, sel ? Ui.PANEL_HIGHLIGHT : hover ? 0xFF28313B : 0xFF1B2026);
            Component name = Component.literal((m.heir() ? "♔ " : "") + m.name());
            Ui.text(g, font, name, lx + 6, my + 2, m.heir() ? Ui.TEXT_TITLE : Ui.TEXT);
            Component sub = m.child() ? Component.translatable("crusadecraft.house.child")
                    : !m.spouse().isEmpty() ? Component.translatable("crusadecraft.house.spouse", m.spouse())
                    : Component.translatable("crusadecraft.house.free");
            if (m.children() > 0) sub = sub.copy().append(" · ").append(Component.translatable("crusadecraft.house.children", m.children()));
            Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(sub.getString(), Math.round((cw - 10) / 0.75f))), lx + 6, my + 12, 0.75f,
                    m.eligible() ? Ui.GREEN : Ui.TEXT_MUTED, false);
            my += ROW;
        }

        // À droite : accueillir, ou marier le membre choisi.
        int ry = top + 50;
        HouseView.Member m = selectedMember();
        if (!marryMode) {
            if (view.candidates().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.house.no_candidate"), rx + 3, ry, cw - 6, Ui.TEXT_MUTED);
            for (HouseView.Candidate c : view.candidates()) {
                if (ry + ROW > top + h - 30) break;
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(c.name(), cw - 100)), rx + 3, ry + 1, Ui.TEXT);
                if (!c.postKey().isEmpty()) Ui.scaled(g, font, Component.translatable(c.postKey()), rx + 3, ry + 11, 0.75f, Ui.TEXT_MUTED, false);
                ry += ROW;
            }
        } else if (m == null || !m.eligible()) {
            Ui.wrapped(g, font, Component.translatable(m == null ? "crusadecraft.house.pick_member" : "crusadecraft.house.not_eligible"), rx + 3, ry,
                    cw - 6, Ui.TEXT_MUTED);
        } else {
            if (view.matches().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.house.no_match"), rx + 3, ry, cw - 6, Ui.TEXT_MUTED);
            for (HouseView.Match match : view.matches()) {
                if (ry + ROW > top + h - 30) break;
                g.fill(rx + 3, ry + 3, rx + 10, ry + 10, 0xFF000000 | match.color());
                Component n = AiNames.realm(match.rank(), match.place());
                float k = Math.max(0.7f, Math.min(1f, (cw - 90) / (float) Math.max(1, font.width(n))));
                Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(n.getString(), Math.round((cw - 90) / k))), rx + 13, ry + 2 + Math.round((1 - k) * 4),
                        k, Ui.TEXT, false);
                Component state = !match.blocked().isEmpty() ? Component.translatable(match.faith().key())
                        : Component.translatable(match.accepts() ? "crusadecraft.house.would_accept" : "crusadecraft.house.would_refuse");
                int color = !match.blocked().isEmpty() ? Ui.RED : match.accepts() ? Ui.GREEN : 0xFFE3B34F;
                Ui.scaled(g, font, state, rx + 13, ry + 12, 0.75f, color, false);
                ry += ROW;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** À côté du bouton du banquet : le deuil en cours, le banquet qui bat son plein, ou l'attente avant le prochain. */
    private void renderCourt(GuiGraphics g, int lx, int y, int cw) {
        HouseView.Court court = view.court();
        Component state;
        int color;
        if (!court.mourning().isEmpty()) {
            state = Component.translatable("crusadecraft.feast.state.mourning", court.mourning(), court.mourningDays());
            color = Ui.TEXT_MUTED;
        } else if (court.feasting()) {
            state = Component.translatable("crusadecraft.feast.state.feasting");
            color = Ui.TEXT_TITLE;
        } else if (court.cooldown() > 0) {
            state = Component.translatable("crusadecraft.feast.state.cooldown", court.cooldown());
            color = Ui.TEXT_MUTED;
        } else {
            return;
        }
        Ui.fitted(g, font, state, lx + 6, y + 1, cw - 6, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int cw = colW(), lx = left + 8, y = top + 28;
        int my = y + 3;
        for (int i = 0; i < view.members().size(); i++) {
            if (my + ROW > y + listH()) break;
            if (Ui.inside(mouseX, mouseY, lx + 2, my, cw, ROW - 1)) {
                selected = i;
                rebuildWidgets();
                return true;
            }
            my += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Pour les tests visuels : présélectionne un membre et le mode. */
    public void preview(int member, boolean marry) {
        selected = member;
        marryMode = marry;
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
