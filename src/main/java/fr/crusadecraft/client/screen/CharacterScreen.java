package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.CharacterService;
import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.character.CharacterView;
import fr.crusadecraft.core.character.CitizenSkill;
import fr.crusadecraft.core.character.HistoryEntry;
import fr.crusadecraft.core.character.Opinion;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CharacterActionPacket;
import fr.crusadecraft.network.packet.RequestCharacterPacket;
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

/**
 * La fiche d'un personnage, façon CK3 : portrait, aptitudes, éducation, traits, opinion, stress,
 * compétences MineColonies, souvenirs, et les actions du souverain.
 */
public class CharacterScreen extends Screen {
    private CharacterView view;
    private boolean appointOpen;
    private int left, top, w, h;
    private List<Component> pendingTooltip;
    private int buttonsTop;

    public CharacterScreen(CharacterView view, boolean appointOpen) {
        super(Component.literal(view.name()));
        this.view = view;
        this.appointOpen = appointOpen && view.canAct();
    }

    public boolean isFor(CharacterView other) {
        return view.ref().equals(other.ref());
    }

    public void refresh(CharacterView newView) {
        this.view = newView;
        this.appointOpen = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(400, width - 12);
        h = Math.min(232, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (appointOpen) {
            initAppoint();
            return;
        }
        int by = top + h - 46;
        List<Button> buttons = new ArrayList<>();
        if (view.canAct() && !view.deceased()) {
            buttons.add(action("crusadecraft.action.reward", CharacterService.Action.REWARD, "crusadecraft.action.reward.tip"));
            buttons.add(action("crusadecraft.action.reprimand", CharacterService.Action.REPRIMAND, "crusadecraft.action.reprimand.tip"));
            Button appoint = Button.builder(Component.translatable("crusadecraft.action.appoint"), b -> {
                appointOpen = true;
                rebuildWidgets();
            }).build();
            appoint.setTooltip(Tooltip.create(Component.translatable("crusadecraft.action.appoint.tip")));
            appoint.active = !view.child() && view.status() == CharacterStatus.LIBRE;
            buttons.add(appoint);
            buttons.add(view.status() == CharacterStatus.EMPRISONNE
                    ? action("crusadecraft.action.release", CharacterService.Action.RELEASE, null)
                    : action("crusadecraft.action.imprison", CharacterService.Action.IMPRISON, "crusadecraft.action.imprison.tip"));
            buttons.add(view.status() == CharacterStatus.EXILE
                    ? action("crusadecraft.action.recall", CharacterService.Action.RECALL, null)
                    : action("crusadecraft.action.exile", CharacterService.Action.EXILE, "crusadecraft.action.exile.tip"));
        }
        buttons.add(Button.builder(Component.translatable("gui.done"), b -> onClose()).build());
        int gap = 4;
        int perRow = buttons.size() <= 3 ? buttons.size() : (buttons.size() + 1) / 2;
        int rowsUsed = (buttons.size() + perRow - 1) / perRow;
        int bw = Math.min(120, (w - 16 - gap * (perRow - 1)) / perRow);
        by = top + h - 6 - rowsUsed * 20;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            int row = i / perRow, col = i % perRow;
            int inRow = Math.min(perRow, buttons.size() - row * perRow);
            int rowWidth = bw * inRow + gap * (inRow - 1);
            b.setX(left + (w - rowWidth) / 2 + col * (bw + gap));
            b.setY(by + row * 20);
            b.setWidth(bw);
            b.setHeight(18);
            addRenderableWidget(b);
        }
        buttonsTop = by;
    }

    private Button action(String key, CharacterService.Action action, String tip) {
        Button b = Button.builder(Component.translatable(key), btn -> send(action, null)).build();
        if (tip != null) b.setTooltip(Tooltip.create(Component.translatable(tip)));
        return b;
    }

    private void send(CharacterService.Action action, Post post) {
        CrusadeNetwork.toServer(new CharacterActionPacket(view.ref(), action, post, RequestCharacterPacket.Mode.SHEET));
    }

    private void initAppoint() {
        int pw = 240;
        int rows = view.slots().size() + 2;
        int ph = 34 + rows * 20;
        int px = (width - pw) / 2;
        int py = Math.max(4, (height - ph) / 2);
        int y = py + 28;
        for (CharacterView.PostSlot slot : view.slots()) {
            Post post = slot.post();
            Component holder = slot.holderName().isEmpty()
                    ? Component.translatable("crusadecraft.appoint.vacant").withStyle(ChatFormatting.GRAY)
                    : Component.literal(slot.holderName());
            MutableComponent label = Component.translatable(post.key(view.female())).append(" : ").append(holder);
            Button b = Button.builder(label, btn -> send(CharacterService.Action.APPOINT, post)).bounds(px + 10, y, pw - 20, 18).build();
            int statValue = view.stats()[post.stat().ordinal()];
            b.setTooltip(Tooltip.create(Component.translatable("crusadecraft.appoint.tip",
                    Component.translatable(post.stat().key()), statValue)));
            b.active = !slot.holderIsThis();
            addRenderableWidget(b);
            y += 20;
        }
        if (view.post() != null) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.action.dismiss",
                    Component.translatable(view.post().key(view.female()))), btn -> send(CharacterService.Action.DISMISS, null))
                    .bounds(px + 10, y, pw - 20, 18).build());
        }
        y += 20;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> {
            appointOpen = false;
            rebuildWidgets();
        }).bounds(px + pw / 2 - 40, y, 80, 18).build());
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        Ui.panel(g, left, top, w, h);
        renderPortrait(g, mouseX, mouseY);
        renderHeader(g);
        renderStats(g, mouseX, mouseY);
        int ty = renderTraits(g, mouseX, mouseY);
        int below = renderOpinion(g, ty, mouseX, mouseY);
        renderSkills(g);
        renderHistory(g, Math.max(top + 124, below + 4));
        if (appointOpen) {
            // Les icônes d'objets se dessinent en avant : la fenêtre de nomination doit passer devant elles.
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            renderAppointBackdrop(g);
            super.render(g, mouseX, mouseY, partialTick);
            g.pose().popPose();
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null && !appointOpen) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private void renderPortrait(GuiGraphics g, int mouseX, int mouseY) {
        int px = left + 8, py = top + 8, pw = 72, ph = 92;
        Ui.inset(g, px, py, pw, ph);
        Portraits.render(g, view.entityId(), px, py, pw, ph, mouseX, mouseY);
        if (view.post() != null) {
            Component post = Component.translatable(view.post().key(view.female()));
            int tw = font.width(post);
            g.fill(px, py + ph + 2, px + pw, py + ph + 14, 0xFF3A2C14);
            g.renderOutline(px, py + ph + 2, pw, 12, Ui.GOLD);
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(post.getString(), pw - 4)), px + Math.max(2, (pw - tw) / 2), py + ph + 4, Ui.TEXT_TITLE);
        } else if (view.status() != CharacterStatus.LIBRE || view.deceased()) {
            Component st = view.deceased() ? Component.translatable("crusadecraft.status.deceased") : Component.translatable(view.status().key());
            g.fill(px, py + ph + 2, px + pw, py + ph + 14, 0xFF4A1A16);
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(st.getString(), pw - 4)), px + 3, py + ph + 4, 0xFFFFC8B8);
        }
    }

    private void renderHeader(GuiGraphics g) {
        int x = left + 88;
        int maxW = w - 88 - 118;
        Ui.scaled(g, font, Component.literal(font.plainSubstrByWidth(view.name(), (int) (maxW / 1.4))), x, top + 10, 1.4f, Ui.TEXT_TITLE, false);
        Component job = view.jobKey().isEmpty() ? Component.translatable("crusadecraft.character.jobless") : Component.translatable(view.jobKey());
        Component line = view.child() ? Component.translatable("crusadecraft.character.child") : job;
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), maxW)), x, top + 25, Ui.TEXT);
        String place = view.realmName().isEmpty() ? view.colonyName() : view.colonyName() + " · " + view.realmName();
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(place, maxW)), x, top + 36, Ui.TEXT_MUTED);
    }

    private void renderStats(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 88, y = top + 49;
        for (Stat stat : Stat.values()) {
            int bx = x + stat.ordinal() * 39;
            g.fill(bx, y, bx + 36, y + 22, 0xFF1C1612);
            g.renderOutline(bx, y, 36, 22, Ui.GOLD_DARK);
            g.renderItem(Portraits.statIcon(stat), bx + 2, y + 3);
            Ui.text(g, font, Component.literal(String.valueOf(view.stats()[stat.ordinal()])), bx + 20, y + 8, Ui.TEXT);
            if (Ui.inside(mouseX, mouseY, bx, y, 36, 22)) pendingTooltip = statTooltip(stat);
        }
    }

    private List<Component> statTooltip(Stat stat) {
        List<Component> lines = new ArrayList<>();
        int i = stat.ordinal();
        lines.add(Component.translatable(stat.key()).append(" : " + view.stats()[i]).withStyle(ChatFormatting.GOLD));
        if (i < view.sources().size()) {
            CharacterView.StatSource src = view.sources().get(i);
            MutableComponent from = Component.empty();
            for (int k = 0; k < src.skills().length; k++) {
                if (k > 0) from.append(" + ");
                float wgt = src.weights()[k];
                if (wgt != 1f) from.append(wgt == 0.5f ? "½ " : String.format(Locale.ROOT, "%.2g× ", wgt));
                CitizenSkill skill = CitizenSkill.values()[src.skills()[k]];
                from.append(Component.translatable(skill.key())).append(" " + view.skills()[skill.ordinal()]);
            }
            from.append(String.format(Locale.ROOT, ", ÷ %s", src.divisor() == Math.floor(src.divisor()) ? String.valueOf((int) src.divisor()) : String.valueOf(src.divisor())));
            lines.add(Component.translatable("crusadecraft.character.stat.base", view.baseStats()[i]).withStyle(ChatFormatting.GRAY));
            lines.add(from.withStyle(ChatFormatting.DARK_GRAY));
        }
        if (view.education().stat() == stat) {
            lines.add(Component.translatable("crusadecraft.character.stat.education", Component.translatable(view.education().key()),
                    view.education().bonus()).withStyle(ChatFormatting.AQUA));
        }
        for (Trait t : view.traits()) {
            int m = t.statModifier(stat);
            if (m != 0) lines.add(Component.translatable("crusadecraft.character.stat.trait", Component.translatable(t.key()), signed(m))
                    .withStyle(m > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return lines;
    }

    private int renderTraits(GuiGraphics g, int mouseX, int mouseY) {
        int x = left + 88, y = top + 77;
        int maxX = left + w - 118;
        int cx = x;
        // L'éducation d'abord, puis les traits
        cx = chip(g, Component.translatable(view.education().key()), 0xFF1E3046, cx, y, mouseX, mouseY, educationTooltip());
        for (Trait t : view.traits()) {
            Component name = Component.translatable(t.key());
            int cw = font.width(name) + 8;
            if (cx + cw > maxX) {
                cx = x;
                y += 14;
            }
            int bg = t.balance() > 0 ? 0xFF243D1E : t.balance() < 0 ? 0xFF46201C : 0xFF3A3326;
            cx = chip(g, name, bg, cx, y, mouseX, mouseY, traitTooltip(t));
        }
        return y + 14;
    }

    private int chip(GuiGraphics g, Component text, int bg, int x, int y, int mouseX, int mouseY, List<Component> tooltip) {
        int cw = font.width(text) + 8;
        g.fill(x, y, x + cw, y + 12, bg);
        g.renderOutline(x, y, cw, 12, Ui.GOLD_DARK);
        Ui.text(g, font, text, x + 4, y + 2, Ui.TEXT);
        if (Ui.inside(mouseX, mouseY, x, y, cw, 12)) pendingTooltip = tooltip;
        return x + cw + 3;
    }

    private List<Component> educationTooltip() {
        return List.of(Component.translatable(view.education().key()).withStyle(ChatFormatting.AQUA),
                Component.translatable("crusadecraft.character.education.desc", Component.translatable(view.education().stat().key()),
                        view.education().bonus()).withStyle(ChatFormatting.GRAY));
    }

    private List<Component> traitTooltip(Trait t) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(t.key()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(t.descriptionKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (Stat s : Stat.values()) {
            int m = t.statModifier(s);
            if (m != 0) lines.add(Component.literal(signed(m) + " ").append(Component.translatable(s.key()))
                    .withStyle(m > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (t.liegeOpinion() != 0) {
            lines.add(Component.translatable("crusadecraft.character.trait.liege", signed(t.liegeOpinion()))
                    .withStyle(t.liegeOpinion() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return lines;
    }

    private int renderOpinion(GuiGraphics g, int y, int mouseX, int mouseY) {
        int x = left + 88;
        int bw = w - 88 - 118 - 46 - 56;
        if (view.hasOpinion()) {
            int op = view.opinion();
            Ui.text(g, font, Component.translatable("crusadecraft.character.opinion"), x, y + 2, Ui.TEXT_MUTED);
            int color = op >= 20 ? Ui.GREEN : op <= -20 ? Ui.RED : 0xFFC99A3C;
            Ui.bar(g, x + 46, y + 3, bw, 6, (op + 100) / 200.0, color);
            Ui.text(g, font, Component.literal(signed(op)), x + 50 + bw, y + 2, color);
            if (Ui.inside(mouseX, mouseY, x, y, 102 + bw, 12)) pendingTooltip = opinionTooltip();
        } else {
            Ui.text(g, font, Component.translatable("crusadecraft.character.opinion.foreign"), x, y + 2, Ui.TEXT_MUTED);
        }
        int sy = y + 13;
        Ui.text(g, font, Component.translatable("crusadecraft.character.stress"), x, sy + 2, Ui.TEXT_MUTED);
        Ui.bar(g, x + 46, sy + 3, bw, 6, view.stress() / 300.0, 0xFF8C6BB0);
        Component level = Component.translatable("crusadecraft.character.stress.level." + Math.min(3, view.stress() / 100));
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(level.getString(), 54)), x + 50 + bw, sy + 2, Ui.TEXT);
        if (Ui.inside(mouseX, mouseY, x, sy, 102 + bw, 12)) {
            pendingTooltip = List.of(level, Component.translatable("crusadecraft.character.stress.tip", view.stress()));
        }
        return sy + 12;
    }

    private List<Component> opinionTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("crusadecraft.character.opinion.title", signed(view.opinion())).withStyle(ChatFormatting.GOLD));
        for (Opinion.Line l : view.opinionLines()) {
            Component label = l.arg() == null ? Component.translatable(l.key()) : Component.translatable(l.key(), Component.translatable(l.arg()));
            lines.add(Component.literal(signed(l.value()) + "  ").append(label)
                    .withStyle(l.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (view.opinionLines().isEmpty()) lines.add(Component.translatable("crusadecraft.character.opinion.none").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private void renderSkills(GuiGraphics g) {
        int x = left + w - 112, y = top + 8, sw = 104;
        Ui.inset(g, x, y, sw, 112);
        Ui.text(g, font, Component.translatable("crusadecraft.character.skills"), x + 4, y + 3, Ui.TEXT_TITLE);
        int best = 0, second = 0;
        for (int v : view.skills()) {
            if (v > best) { second = best; best = v; } else if (v > second) second = v;
        }
        for (CitizenSkill s : CitizenSkill.values()) {
            int v = view.skills()[s.ordinal()];
            int ry = y + 13 + s.ordinal() * 9;
            int color = v >= second && v > 0 ? Ui.TEXT : Ui.TEXT_MUTED;
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable(s.key()).getString(), sw - 24)), x + 4, ry, color);
            String val = String.valueOf(v);
            Ui.text(g, font, Component.literal(val), x + sw - 4 - font.width(val), ry, color);
        }
    }

    private void renderHistory(GuiGraphics g, int y) {
        int x = left + 8, hw = w - 16, hh = (buttonsTop > 0 ? buttonsTop : top + h - 26) - 4 - y;
        if (hh < 20) return;
        Ui.inset(g, x, y, hw, hh);
        Ui.text(g, font, Component.translatable("crusadecraft.character.history"), x + 4, y + 3, Ui.TEXT_TITLE);
        int ly = y + 14;
        if (view.history().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.character.history.empty"), x + 4, ly, Ui.TEXT_MUTED);
            return;
        }
        for (HistoryEntry e : view.history()) {
            if (ly + 9 > y + hh) break;
            Object[] args = e.args().stream().map(a -> (Object) (a.startsWith("crusadecraft.") ? Component.translatable(a) : Component.literal(a))).toArray();
            Component line = Component.translatable("crusadecraft.character.history.day", e.day()).append(" — ")
                    .append(Component.translatable(e.key(), args));
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), hw - 8)), x + 4, ly, Ui.TEXT_MUTED);
            ly += 10;
        }
    }

    private void renderAppointBackdrop(GuiGraphics g) {
        int pw = 240;
        int rows = view.slots().size() + 2;
        int ph = 34 + rows * 20;
        int px = (width - pw) / 2;
        int py = Math.max(4, (height - ph) / 2);
        g.fill(0, 0, width, height, 0x90000000);
        Ui.panel(g, px, py, pw, ph);
        Ui.centered(g, font, Component.translatable("crusadecraft.appoint.title", view.name()), px + pw / 2, py + 10, Ui.TEXT_TITLE);
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
