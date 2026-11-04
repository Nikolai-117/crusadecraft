package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.InhabitantPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.UUID;

/** L'écran de l'œuf d'habitant (P42) : la ville à gauche, le métier à droite, et il naît. */
public class EggScreen extends Screen {
    private static final int ROW = 18;
    private final BlockPos at;
    private final List<InhabitantPackets.CityRow> cities;
    private UUID city;
    private BuildingFunction job = BuildingFunction.MAISON;
    private int left, top, w, h, cityScroll, jobScroll;

    public EggScreen(BlockPos at, List<InhabitantPackets.CityRow> cities) {
        super(Component.translatable("crusadecraft.city.egg.title"));
        this.at = at;
        this.cities = cities;
        if (!cities.isEmpty()) city = cities.get(0).id();
    }

    public static void open(BlockPos at, List<InhabitantPackets.CityRow> cities) {
        Minecraft.getInstance().setScreen(new EggScreen(at, cities));
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 12);
        h = Math.min(250, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        Button go = Button.builder(Component.translatable("crusadecraft.city.egg.hatch"), b -> {
            CrusadeNetwork.toServer(new InhabitantPackets.ChooseEgg(city, job.ordinal(), at));
            onClose();
        }).bounds(left + w - 150, top + h - 24, 84, 16).build();
        go.active = city != null;
        addRenderableWidget(go);
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + w - 62, top + h - 24, 54, 16).build());
    }

    private int listTop() {
        return top + 28;
    }

    private int listHeight() {
        return h - 28 - 30;
    }

    private int split() {
        return left + w / 2 + 20;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < split()) cityScroll = Mth.clamp(cityScroll - (int) (delta * ROW), 0, Math.max(0, cities.size() * ROW - listHeight()));
        else jobScroll = Mth.clamp(jobScroll - (int) (delta * ROW), 0, Math.max(0, BuildingFunction.values().length * ROW - listHeight()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = (int) my - listTop();
        if (y >= 0 && y < listHeight()) {
            if (mx >= left + 8 && mx < split() - 4) {
                int i = (y + cityScroll) / ROW;
                if (i >= 0 && i < cities.size()) {
                    city = cities.get(i).id();
                    rebuildWidgets();
                    return true;
                }
            } else if (mx >= split() && mx <= left + w - 8) {
                int i = (y + jobScroll) / ROW;
                BuildingFunction[] all = BuildingFunction.values();
                if (i >= 0 && i < all.length) {
                    job = all[i];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        Ui.fitted(g, font, title, left + 10, top + 8, w - 20, Ui.TEXT_TITLE);
        int ly = listTop(), lh = listHeight(), cx = left + 8, cw = split() - left - 12, jx = split(), jw = left + w - 8 - split();
        Ui.inset(g, cx, ly, cw, lh);
        Ui.inset(g, jx, ly, jw, lh);
        g.enableScissor(cx, ly, cx + cw, ly + lh);
        int y = ly + 1 - cityScroll;
        if (cities.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.egg.no_city"), cx + 6, ly + 6, cw - 12, Ui.TEXT_MUTED);
        for (InhabitantPackets.CityRow c : cities) {
            if (c.id().equals(city)) g.fill(cx + 1, y, cx + cw - 1, y + ROW - 1, Ui.PANEL_HIGHLIGHT);
            g.fill(cx + 3, y + 2, cx + 7, y + ROW - 3, 0xFF000000 | c.color());
            Ui.fitted(g, font, Component.literal(c.name()), cx + 11, y + 1, cw - 56, Ui.TEXT);
            Ui.fitted(g, font, Component.literal(c.realm()), cx + 11, y + 9, cw - 56, Ui.TEXT_MUTED);
            Ui.text(g, font, Component.literal(c.people() + "/" + c.max()), cx + cw - 40, y + 5, c.people() >= c.max() ? Ui.RED : Ui.TEXT_MUTED);
            y += ROW;
        }
        g.disableScissor();
        g.enableScissor(jx, ly, jx + jw, ly + lh);
        y = ly + 1 - jobScroll;
        for (BuildingFunction f : BuildingFunction.values()) {
            if (f == job) g.fill(jx + 1, y, jx + jw - 1, y + ROW - 1, Ui.PANEL_HIGHLIGHT);
            g.renderItem(BuildingScreen.icon(f), jx + 2, y);
            Ui.fitted(g, font, Component.translatable(f.key()), jx + 21, y + 5, jw - 24, Ui.TEXT);
            y += ROW;
        }
        g.disableScissor();
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.egg.help"), left + 10, top + h - 20, w - 170, Ui.TEXT_MUTED);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
