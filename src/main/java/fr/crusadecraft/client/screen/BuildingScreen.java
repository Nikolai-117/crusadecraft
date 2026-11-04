package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.city.CityClient;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;

/**
 * La fonction d'un bâtiment (P38), au bâton d'intendant : à gauche les seize fonctions (la proposée marquée d'une
 * étoile), à droite son nom, ce qu'on y a trouvé (sol, étages, meubles) et ses gens. Valider lui donne sa fonction —
 * et ses gens arrivent ; on peut aussi la lui retirer, ou retirer le bâtiment de la ville.
 */
public class BuildingScreen extends Screen {
    private static final int W = 380, H = 236, COLS = 5, TILE_W = 44, TILE_H = 38;

    private final UUID site;
    private final String city;
    private final BuildingPackets.View view;
    private final boolean draft;
    private int selected;
    private EditBox name;
    private int left, top;

    public BuildingScreen(UUID site, String city, BuildingPackets.View view, boolean draft) {
        super(Component.translatable("crusadecraft.city.screen.building"));
        this.site = site;
        this.city = city;
        this.view = view;
        this.draft = draft;
        this.selected = view.function() >= 0 ? view.function() : view.suggestion();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String keep = name == null ? view.name() : name.getValue();
        int rx = left + 14 + COLS * TILE_W + 12;
        int rw = left + W - 12 - rx;
        name = new EditBox(font, rx, top + 40, rw, 16, Component.translatable("crusadecraft.city.screen.name"));
        name.setMaxLength(48);
        name.setValue(keep);
        name.setHint(Component.translatable("crusadecraft.city.screen.name.hint"));
        addRenderableWidget(name);
        int by = top + H - 24;
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.screen.validate"), b -> send(selected, false))
                .bounds(left + W - 12 - 84, by, 84, 16).build());
        if (!draft && view.function() >= 0) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.screen.clear"), b -> send(-1, false))
                    .bounds(left + W - 12 - 84 - 6 - 104, by, 104, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(draft ? "crusadecraft.city.screen.drop" : "crusadecraft.city.screen.forget"),
                b -> send(-1, true)).bounds(left + 12, by, 110, 16).build());
    }

    private void send(int function, boolean forget) {
        if (!forget && function < 0 && draft) return;
        CrusadeNetwork.toServer(new BuildingPackets.Decide(site, view.id(), function, name.getValue().strip(), forget));
        CityClient.refresh();
        onClose();
    }

    private int tileX(int i) {
        return left + 14 + (i % COLS) * TILE_W;
    }

    private int tileY(int i) {
        return top + 40 + (i / COLS) * TILE_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        BuildingFunction[] all = BuildingFunction.values();
        for (int i = 0; i < all.length; i++) {
            if (Ui.inside(mx, my, tileX(i), tileY(i), TILE_W - 4, TILE_H - 4)) {
                selected = selected == i && !draft ? -1 : i;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == 257 || key == 335) && !name.isFocused()) {
            send(selected, false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    static ItemStack icon(BuildingFunction f) {
        ResourceLocation rl = ResourceLocation.tryParse(f.icon());
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return new ItemStack(item == null ? Items.BARRIER : item);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.fitted(g, font, Component.translatable(draft ? "crusadecraft.city.screen.new" : "crusadecraft.city.screen.building.of", city),
                left + 12, top + 10, W - 24, Ui.TEXT_TITLE);
        Component sub = draft ? Component.translatable("crusadecraft.city.screen.draft")
                : view.function() >= 0 ? Component.translatable(BuildingFunction.byOrdinal(view.function()).key())
                : Component.translatable("crusadecraft.city.screen.undecided");
        Ui.fitted(g, font, sub, left + 12, top + 22, W - 24, Ui.TEXT_MUTED);

        // Les fonctions.
        BuildingFunction[] all = BuildingFunction.values();
        for (int i = 0; i < all.length; i++) {
            int x = tileX(i), y = tileY(i), w = TILE_W - 4, h = TILE_H - 4;
            boolean hover = Ui.inside(mx, my, x, y, w, h);
            int border = i == selected ? Ui.GOLD : hover ? Ui.GOLD_DARK : 0xFF3A3026;
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
            g.fill(x, y, x + w, y + h, i == selected ? Ui.PANEL_HIGHLIGHT : Ui.PANEL_INNER);
            g.fill(x, y + h - 2, x + w, y + h, 0xFF000000 | all[i].color());
            g.renderItem(icon(all[i]), x + w / 2 - 8, y + 3);
            Ui.scaled(g, font, Component.translatable(all[i].key()), x + w / 2, y + 22, 0.62f, i == selected ? Ui.TEXT_TITLE : Ui.TEXT, true);
            if (i == view.suggestion()) Ui.text(g, font, Component.literal("★"), x + w - 8, y + 1, Ui.GOLD);
        }

        // Le bâtiment.
        int rx = left + 14 + COLS * TILE_W + 12, rw = left + W - 12 - rx;
        int y = top + 62;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.screen.size", view.floor(), view.levels(),
                view.max().getX() - view.min().getX() + 1, view.max().getZ() - view.min().getZ() + 1), rx, y, rw, Ui.TEXT);
        y += 11;
        if (view.suggestion() >= 0) {
            Ui.fitted(g, font, Component.translatable("crusadecraft.city.screen.suggested",
                    Component.translatable(BuildingFunction.byOrdinal(view.suggestion()).key())), rx, y, rw, Ui.GOLD);
            y += 11;
        }
        MutableComponent furniture = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> e : view.furniture().entrySet()) {
            if (!first) furniture.append(", ");
            furniture.append(Component.translatable("crusadecraft.city.furniture." + e.getKey())).append(" ×" + e.getValue());
            first = false;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.city.screen.furniture"), rx, y, Ui.TEXT_MUTED);
        y += 10;
        y += Ui.wrapped(g, font, first ? Component.translatable("crusadecraft.city.screen.furniture.none") : furniture, rx, y, rw, Ui.TEXT) + 4;
        // Ce que la fonction choisie amène.
        BuildingFunction chosen = BuildingFunction.byOrdinal(selected);
        if (chosen != null) {
            Component brings = chosen.dwelling()
                    ? Component.translatable("crusadecraft.city.screen.brings.families", BuildingFunction.families(view.floor()))
                    : Component.translatable("crusadecraft.city.screen.brings.workers", chosen.postsFor(view.floor()).size());
            y += Ui.wrapped(g, font, brings, rx, y, rw, Ui.TEXT_MUTED) + 3;
        }
        if (!view.people().isEmpty()) {
            Ui.text(g, font, Component.translatable("crusadecraft.city.screen.people", view.people().size()), rx, y, Ui.TEXT_MUTED);
            y += 10;
            int bottom = top + H - 30;
            for (String p : view.people()) {
                if (y + 9 > bottom) break;
                Ui.fitted(g, font, Component.literal("· " + p), rx, y, rw, Ui.TEXT);
                y += 10;
            }
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
