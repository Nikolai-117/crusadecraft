package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.city.CityPost;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CityPostPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Le courrier reçu des habitants (P49) : la liste à gauche, la lettre ouverte à droite. */
public class PostScreen extends Screen {
    private static final int W = 420, H = 240, ROW = 26, LIST_W = 150;

    private final List<CityPost.Letter> letters;
    private int left, top, scroll, selected = -1;
    private Button delete;

    public PostScreen(List<CityPost.Letter> letters) {
        super(Component.translatable("crusadecraft.city.post.title"));
        // Du plus récent au plus ancien : on garde l'indice d'origine, que le serveur connaît.
        this.letters = new ArrayList<>(letters);
    }

    public static void open(List<CityPost.Letter> letters) {
        Minecraft.getInstance().setScreen(new PostScreen(letters));
    }

    /** L'indice dans la boîte du i-ème affiché : les plus récentes d'abord. */
    private int index(int shown) {
        return letters.size() - 1 - shown;
    }

    private int visible() {
        return (H - 62) / ROW;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int by = top + H - 24;
        delete = Button.builder(Component.translatable("crusadecraft.city.post.delete"), b -> {
            if (selected < 0) return;
            CrusadeNetwork.toServer(new CityPostPackets.Act(CityPostPackets.DELETE, index(selected)));
        }).bounds(left + 12, by, 84, 16).build(KitButton::new);
        delete.active = selected >= 0;
        addRenderableWidget(delete);
        Button clear = Button.builder(Component.translatable("crusadecraft.city.post.clear"), b -> CrusadeNetwork.toServer(new CityPostPackets.Act(CityPostPackets.CLEAR, 0)))
                .bounds(left + 100, by, 84, 16).build(KitButton::new);
        clear.active = !letters.isEmpty();
        addRenderableWidget(clear);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 66, by, 54, 16).build(KitButton::new));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.scaled(g, font, title, left + 12, top + 10, 1.25f, Ui.TEXT_TITLE, false);
        int lx = left + 12, ly = top + 28, lh = H - 60;
        Ui.inset(g, lx, ly, LIST_W, lh);
        if (letters.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.city.post.empty"), lx + 6, ly + 6, LIST_W - 12, Ui.TEXT_MUTED);
        }
        g.enableScissor(lx + 1, ly + 1, lx + LIST_W - 1, ly + lh - 1);
        for (int i = scroll; i < letters.size() && i < scroll + visible() + 1; i++) {
            CityPost.Letter l = letters.get(index(i));
            int y = ly + 3 + (i - scroll) * ROW;
            boolean hover = Ui.inside(mx, my, lx + 2, y, LIST_W - 4, ROW - 2);
            if (i == selected) g.fill(lx + 2, y, lx + LIST_W - 2, y + ROW - 2, Ui.GOLD_DARK);
            else if (hover) g.fill(lx + 2, y, lx + LIST_W - 2, y + ROW - 2, Ui.PANEL_HIGHLIGHT);
            Ui.fitted(g, font, Component.literal(l.from()), lx + 6, y + 2, LIST_W - 12, l.read() ? Ui.TEXT_MUTED : Ui.TEXT_TITLE);
            Ui.fitted(g, font, Component.translatable(l.kind().key()).append(" · ").append(l.city()), lx + 6, y + 13, LIST_W - 12, Ui.TEXT_MUTED);
        }
        g.disableScissor();
        int rx = lx + LIST_W + 8, rw = W - LIST_W - 32;
        Ui.parchment(g, rx, ly, rw, lh);
        if (selected >= 0 && selected < letters.size()) {
            CityPost.Letter l = letters.get(index(selected));
            int y = ly + 8;
            Ui.fitted(g, font, Component.translatable(l.kind().key()), rx + 10, y, rw - 20, Ui.INK_MUTED);
            y += 13;
            y = Ui.wrapped(g, font, Component.literal(l.text()), rx + 10, y, rw - 20, Ui.INK) + 8;
            Ui.fitted(g, font, Component.translatable("crusadecraft.city.post.signed", l.from(), l.city()), rx + 10, y, rw - 20, Ui.INK_MUTED);
            Ui.fitted(g, font, Component.translatable("crusadecraft.city.post.day", l.day()), rx + 10, y + 11, rw - 20, Ui.INK_MUTED);
        } else if (!letters.isEmpty()) {
            Ui.centered(g, font, Component.translatable("crusadecraft.city.post.pick"), rx + rw / 2, ly + lh / 2 - 4, Ui.INK_MUTED);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left + 12, ly = top + 28, lh = H - 60;
        if (Ui.inside(mx, my, lx, ly, LIST_W, lh)) {
            int i = scroll + (int) ((my - ly - 3) / ROW);
            if (i >= 0 && i < letters.size()) {
                selected = i;
                int at = index(i);
                if (!letters.get(at).read()) {
                    letters.set(at, letters.get(at).asRead());
                    CrusadeNetwork.toServer(new CityPostPackets.Act(CityPostPackets.READ, at));
                }
                delete.active = true;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, letters.size() - visible()));
        return true;
    }

    /** Pour le banc d'essai : le centre de cette ligne de la liste. */
    public int[] rowForShots(int i) {
        return new int[]{left + 12 + 20, top + 28 + 3 + i * ROW + 6};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
