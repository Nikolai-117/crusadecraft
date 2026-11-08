package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.CharacterStatus;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtPackets;
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
 * La Table du Conseil : les cinq conseillers, la tâche de chacun, la réunion du jour et les requêtes en
 * attente.
 */
public class CouncilScreen extends Screen {
    private static final int ROW = 22;

    private CouncilView view;
    private int left, top, w, h;
    private List<Component> pendingTooltip;
    private final List<Object[]> nameHits = new ArrayList<>();
    private final List<Object[]> requestHits = new ArrayList<>();

    public CouncilScreen(CouncilView view) {
        super(Component.translatable("block.crusadecraft.council_table"));
        this.view = view;
    }

    public void refresh(CouncilView newView) {
        this.view = newView;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 12);
        h = Math.min(236, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int y = top + 42;
        int taskX = left + w - 150;
        for (CouncilView.Seat seat : view.seats()) {
            CouncilTask task = seat.task();
            Component label = task == null ? Component.literal("—") : Component.translatable(task.key());
            Button b = Button.builder(label, btn -> cycleTask(seat)).bounds(taskX, y + 2, 142, 18).build(KitButton::new);
            b.setTooltip(Tooltip.create(taskTooltip(seat)));
            addRenderableWidget(b);
            y += ROW;
        }
        Button convene = Button.builder(Component.translatable("crusadecraft.council.convene"), btn ->
                CrusadeNetwork.toServer(new CourtPackets.CouncilAction(CourtPackets.CouncilAction.Kind.CONVENE, null, null, null)))
                .bounds(left + 10, top + h - 26, 150, 18).build(KitButton::new);
        convene.active = view.canConvene();
        convene.setTooltip(Tooltip.create(Component.translatable(view.hasTable()
                ? "crusadecraft.council.convene.tip" : "crusadecraft.council.no_table")));
        addRenderableWidget(convene);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
                .bounds(left + w - 90, top + h - 26, 80, 18).build(KitButton::new));
    }

    private void cycleTask(CouncilView.Seat seat) {
        List<CouncilTask> options = new ArrayList<>();
        for (CouncilTask t : CouncilTask.of(seat.post())) if (t.available()) options.add(t);
        if (options.size() < 2) return;
        int i = options.indexOf(seat.task());
        CouncilTask next = options.get((i + 1) % options.size());
        CrusadeNetwork.toServer(new CourtPackets.CouncilAction(CourtPackets.CouncilAction.Kind.SET_TASK, seat.post(), next, null));
    }

    private Component taskTooltip(CouncilView.Seat seat) {
        MutableComponent tip = Component.empty();
        if (seat.task() != null) {
            tip.append(Component.translatable(seat.task().key()).withStyle(ChatFormatting.GOLD)).append("\n");
            tip.append(Component.translatable(seat.task().descriptionKey()).withStyle(ChatFormatting.GRAY)).append("\n\n");
        }
        tip.append(Component.translatable("crusadecraft.council.tasks_of", Component.translatable(seat.post().key(seat.female())))
                .withStyle(ChatFormatting.WHITE));
        for (CouncilTask t : CouncilTask.of(seat.post())) {
            tip.append("\n• ").append(Component.translatable(t.key()).withStyle(t.available() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            if (!t.available()) tip.append(Component.translatable("crusadecraft.council.soon").withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.append("\n\n").append(Component.translatable("crusadecraft.council.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        nameHits.clear();
        requestHits.clear();
        Ui.panel(g, left, top, w, h);
        Ui.scaled(g, font, Component.translatable("crusadecraft.council.screen_title", view.realmName()), left + 12, top + 9, 1.3f, Ui.TEXT_TITLE, false);
        Component status = view.hasTable()
                ? Component.translatable("crusadecraft.council.meets", view.tableColony().isEmpty() ? "?" : view.tableColony(), view.meetingHour())
                : Component.translatable("crusadecraft.council.no_table.short");
        Ui.text(g, font, status, left + 12, top + 25, view.hasTable() ? Ui.TEXT_MUTED : Ui.RED);
        if (view.meetingBonus()) {
            Component bonus = Component.translatable("crusadecraft.council.bonus");
            int bw = font.width(bonus) + 8;
            g.fill(left + w - bw - 10, top + 8, left + w - 10, top + 20, 0xFF243D1E);
            g.renderOutline(left + w - bw - 10, top + 8, bw, 12, Ui.GREEN);
            Ui.text(g, font, bonus, left + w - bw - 6, top + 10, Ui.TEXT);
        }

        int y = top + 42;
        for (CouncilView.Seat seat : view.seats()) {
            renderSeat(g, seat, y, mouseX, mouseY);
            y += ROW;
        }
        renderRequests(g, y + 4, mouseX, mouseY);
        String res = String.format(Locale.ROOT, "%s %d · %s %d · %s %+d%%",
                Component.translatable("crusadecraft.resource.gold").getString(), view.gold(),
                Component.translatable("crusadecraft.resource.piety").getString(), view.piety(),
                Component.translatable("crusadecraft.council.happiness").getString(), Math.round((view.happinessFactor() - 1) * 100));
        // Entre les deux boutons du bas, réduit au besoin pour ne jamais les chevaucher.
        int gapLeft = left + 166, gapRight = left + w - 96;
        float scale = Math.min(1f, (gapRight - gapLeft) / (float) Math.max(1, font.width(res)));
        int resX = (gapLeft + gapRight) / 2 - Math.round(font.width(res) * scale / 2);
        Ui.scaled(g, font, Component.literal(res), resX, top + h - 21 + Math.round((1 - scale) * 4), scale, Ui.TEXT_MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private void renderSeat(GuiGraphics g, CouncilView.Seat seat, int y, int mouseX, int mouseY) {
        int x = left + 10;
        int rw = w - 20 - 154;
        g.fill(x, y, x + rw, y + ROW - 2, 0xFF1B2026);
        g.renderOutline(x, y, rw, ROW - 2, Ui.GOLD_DARK);
        g.renderItem(Portraits.statIcon(seat.post().stat()), x + 3, y + 2);
        Ui.text(g, font, Component.translatable(seat.post().key(seat.female())), x + 22, y + 6, Ui.TEXT_MUTED);
        int nx = x + 110;
        if (seat.holder() == null) {
            Ui.text(g, font, Component.translatable("crusadecraft.appoint.vacant"), nx, y + 6, 0xFF7D858E);
            if (Ui.inside(mouseX, mouseY, x, y, rw, ROW - 2)) {
                pendingTooltip = List.of(Component.translatable("crusadecraft.council.hint"));
            }
            return;
        }
        boolean hover = Ui.inside(mouseX, mouseY, nx, y, rw - 110, ROW - 2);
        int color = seat.status() != CharacterStatus.LIBRE ? Ui.RED : hover ? Ui.TEXT_TITLE : Ui.TEXT;
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(seat.name(), rw - 110 - 64)), nx, y + 6, color);
        String stat = String.valueOf(seat.stat());
        Ui.text(g, font, Component.literal(stat), x + rw - 58, y + 6, Ui.TEXT);
        String op = CharacterScreen.signed(seat.opinion());
        int opColor = seat.opinion() >= 20 ? Ui.GREEN : seat.opinion() <= -20 ? Ui.RED : 0xFFE3B34F;
        Ui.text(g, font, Component.literal(op), x + rw - 6 - font.width(op), y + 6, opColor);
        nameHits.add(new Object[]{seat, nx, y, rw - 110});
        if (hover) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(seat.name()).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable("crusadecraft.council.seat.stat", Component.translatable(seat.post().stat().key()), seat.stat()));
            tip.add(Component.translatable("crusadecraft.council.seat.opinion", op));
            if (seat.status() != CharacterStatus.LIBRE) tip.add(Component.translatable(seat.status().key()).withStyle(ChatFormatting.RED));
            tip.add(Component.translatable("crusadecraft.council.open", seat.name()).withStyle(ChatFormatting.DARK_GRAY));
            pendingTooltip = tip;
        }
    }

    private void renderRequests(GuiGraphics g, int y, int mouseX, int mouseY) {
        int x = left + 10, rw = w - 20;
        int bottom = top + h - 30;
        if (y + 12 > bottom) return;
        Ui.text(g, font, Component.translatable("crusadecraft.council.requests", view.requests().size()), x, y, Ui.TEXT_TITLE);
        y += 11;
        if (view.requests().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.council.requests.none"), x + 4, y, Ui.TEXT_MUTED);
            return;
        }
        for (CouncilView.RequestSummary r : view.requests()) {
            if (y + 11 > bottom) break;
            boolean hover = Ui.inside(mouseX, mouseY, x, y - 1, rw, 11);
            if (hover) g.fill(x, y - 1, x + rw, y + 10, Ui.PANEL_HIGHLIGHT);
            Component line = Component.literal("• " + r.fromName() + " — ").append(Component.translatable(r.type().titleKey()));
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), rw - 60)), x + 4, y, hover ? Ui.TEXT_TITLE : Ui.TEXT);
            Ui.text(g, font, Component.translatable("crusadecraft.council.answer"), x + rw - 52, y, Ui.GOLD);
            requestHits.add(new Object[]{r, x, y - 1, rw});
            y += 11;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] hit : nameHits) {
            if (Ui.inside(mouseX, mouseY, (int) hit[1], (int) hit[2], (int) hit[3], ROW - 2)) {
                CouncilView.Seat seat = (CouncilView.Seat) hit[0];
                CrusadeNetwork.toServer(new RequestCharacterPacket(seat.holder(), RequestCharacterPacket.Mode.SHEET));
                return true;
            }
        }
        for (Object[] hit : requestHits) {
            if (Ui.inside(mouseX, mouseY, (int) hit[1], (int) hit[2], (int) hit[3], 11)) {
                CouncilView.RequestSummary r = (CouncilView.RequestSummary) hit[0];
                CrusadeNetwork.toServer(new CourtPackets.CouncilAction(CourtPackets.CouncilAction.Kind.OPEN_REQUEST, null, null, r.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
