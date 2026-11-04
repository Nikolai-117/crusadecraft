package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.city.CityClient;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityStaff;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * L'écran d'une ville (P38), le bâton d'intendant tourné vers le ciel : ses bâtiments, leur fonction (ou celle qu'on
 * propose, en attente), ses gens. On y fait reconnaître tous les bâtiments d'après leurs portes, et accepter d'un coup
 * les fonctions proposées.
 */
public class TownScreen extends Screen {
    private static final int W = 420, H = 244, ROW = 20;

    private final BuildingPackets.OpenCity city;
    private int left, top, scroll;

    public TownScreen(BuildingPackets.OpenCity city) {
        super(Component.literal(city.city()));
        this.city = city;
    }

    private int pending() {
        int n = 0;
        for (BuildingPackets.Row r : city.rows()) if (r.function() < 0 && r.suggestion() >= 0) n++;
        return n;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int by = top + H - 24;
        Button recognize = Button.builder(Component.translatable("crusadecraft.city.screen.recognize"), b -> act(CityStaff.RECOGNIZE))
                .bounds(left + 12, by, 156, 16).build();
        Button accept = Button.builder(Component.translatable("crusadecraft.city.screen.accept", pending()), b -> act(CityStaff.ACCEPT_ALL))
                .bounds(left + 12 + 156 + 6, by, 180, 16).build();
        recognize.active = city.canEdit();
        accept.active = city.canEdit() && pending() > 0;
        addRenderableWidget(recognize);
        addRenderableWidget(accept);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 12 - 54, by, 54, 16).build());
        // P41 : la nation de cette ville, son histoire et ses relations.
        if (city.nation() != null) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.open"), b ->
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.NationPackets.Request(city.nation())))
                    .bounds(left + W - 12 - 90, top + 8, 90, 14).build());
        }
    }

    private void act(int op) {
        CrusadeNetwork.toServer(new BuildingPackets.CityAction(city.site(), op));
        CityClient.refresh();
    }

    private int listTop() {
        return top + 46;
    }

    private int listHeight() {
        return H - 46 - 50;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, city.rows().size() * ROW - listHeight());
        scroll = Mth.clamp(scroll - (int) (delta * ROW), 0, max);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.screen.title", city.city()), left + 12, top + 10, W - 24, Ui.TEXT_TITLE);
        int decided = 0;
        for (BuildingPackets.Row r : city.rows()) if (r.function() >= 0) decided++;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.screen.summary", city.rows().size(), decided, city.people(), city.max()),
                left + 12, top + 24, W - 24, Ui.TEXT_MUTED);
        int lx = left + 12, lw = W - 24, ly = listTop(), lh = listHeight();
        Ui.inset(g, lx, ly, lw, lh);
        if (city.rows().isEmpty()) {
            Ui.wrappedCentered(g, font, Component.translatable("crusadecraft.city.screen.empty"), left + W / 2, ly + lh / 2 - 10, lw - 20, Ui.TEXT_MUTED);
        }
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        int y = ly + 2 - scroll;
        for (BuildingPackets.Row r : city.rows()) {
            if (y + ROW >= ly && y <= ly + lh) {
                BuildingFunction f = BuildingFunction.byOrdinal(r.function() >= 0 ? r.function() : r.suggestion());
                if (f != null) g.renderItem(BuildingScreen.icon(f), lx + 4, y + 1);
                g.fill(lx + 2, y + 2, lx + 3, y + ROW - 2, 0xFF000000 | CityClient.color(r.function()));
                Component label;
                if (r.function() >= 0) {
                    label = r.name().isEmpty() ? Component.translatable(f.key())
                            : Component.literal(r.name() + " — ").append(Component.translatable(f.key()));
                } else if (f != null) {
                    label = Component.translatable("crusadecraft.city.screen.row.suggested", Component.translatable(f.key()));
                } else {
                    label = Component.translatable("crusadecraft.city.screen.undecided");
                }
                Ui.fitted(g, font, label, lx + 24, y + 6, lw - 120, r.function() >= 0 ? Ui.TEXT : Ui.TEXT_MUTED);
                Component where = Component.translatable("crusadecraft.city.screen.row.where", r.spot().getX(), r.spot().getY(), r.spot().getZ());
                Ui.fitted(g, font, where, lx + lw - 92, y + 6, 60, Ui.TEXT_MUTED);
                if (r.occupants() > 0) Ui.text(g, font, Component.translatable("crusadecraft.city.screen.row.people", r.occupants()), lx + lw - 28, y + 6, Ui.GOLD);
            }
            y += ROW;
        }
        g.disableScissor();
        Ui.wrapped(g, font, Component.translatable("crusadecraft.city.screen.help"), left + 12, top + H - 46, W - 24, Ui.TEXT_MUTED);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
