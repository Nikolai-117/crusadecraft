package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.character.Stat;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.core.world.AiRealmView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** La fiche d'un royaume IA : son souverain, son conseil, ses provinces et ses ressources. */
public class AiRealmScreen extends Screen {
    private final AiRealmView view;
    private int left, top, w, h;
    private List<Component> pendingTooltip;

    public AiRealmScreen(AiRealmView view) {
        super(AiNames.realm(view.rank(), view.place()));
        this.view = view;
    }

    @Override
    protected void init() {
        w = Math.min(440, width - 12);
        h = Math.min(250, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + w - 90, top + h - 26, 80, 18).build(KitButton::new));
        // P41 : une nation a une histoire, et des relations qu'on peut lire.
        if (view.nation()) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.nation.open"), b ->
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.NationPackets.Request(view.id())))
                    .bounds(left + 10, top + h - 26, 110, 18).build(KitButton::new));
        }
        AiRealmView.Province capital = view.provinces().stream().filter(AiRealmView.Province::capital).findFirst()
                .orElse(view.provinces().isEmpty() ? null : view.provinces().get(0));
        if (capital != null) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.ai.show_on_map"), b -> {
                MapScreen.centerNextOn(capital.x(), capital.z());
                CrusadeNetwork.toServer(new WorldPackets.RequestMap(true));
            }).bounds(left + w - 196, top + h - 26, 100, 18).build(KitButton::new));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        pendingTooltip = null;
        Ui.panel(g, left, top, w, h);
        Ui.shield(g, font, left + 12, top + 10, 30, 37, view.color(), Ui.initial(view.place()));
        Ui.scaled(g, font, title, left + 50, top + 11, 1.4f, Ui.TEXT_TITLE, false);
        Component sub = AiNames.house(view.house()).copy().append(" · ").append(Component.translatable(view.personality().key()))
                .append(" · ").append(Component.translatable(view.faith().key()));
        Ui.text(g, font, sub, left + 50, top + 27, Ui.TEXT);
        Ui.text(g, font, Component.translatable(view.personality().key() + ".desc"), left + 50, top + 38, Ui.TEXT_MUTED);
        if (Ui.inside(mouseX, mouseY, left + 50, top + 26, font.width(sub), 22)) {
            pendingTooltip = List.of(Component.translatable(view.personality().key()).withStyle(ChatFormatting.GOLD),
                    Component.translatable(view.personality().key() + ".desc").withStyle(ChatFormatting.GRAY));
        }

        int colW = w / 2 - 16;
        int x = left + 12, y = top + 56;
        // Souverain
        Ui.text(g, font, Component.translatable("crusadecraft.ai.ruler"), x, y, Ui.TEXT_TITLE);
        AiRealmView.Person r = view.ruler();
        Component rulerLine = Component.translatable(view.rank().holderKey(r.female())).append(" " + r.name())
                .append(Component.translatable("crusadecraft.ai.age", r.age()).withStyle(ChatFormatting.GRAY));
        Ui.text(g, font, rulerLine, x, y + 12, Ui.TEXT);
        y = statLine(g, r.stats(), x, y + 24, colW, mouseX, mouseY);
        int traitsH = Ui.wrapped(g, font, traits(r.traits()), x, y + 2, colW, Ui.TEXT_MUTED);
        if (Ui.inside(mouseX, mouseY, x, y, colW, traitsH + 2)) pendingTooltip = traitTooltip(r.traits());

        // Conseil
        y += traitsH + 8;
        Ui.text(g, font, Component.translatable("crusadecraft.ai.council"), x, y, Ui.TEXT_TITLE);
        y += 12;
        for (AiRealmView.Seat seat : view.council()) {
            if (y > top + h - 40) break;
            Post post = seat.post();
            int stat = seat.person().stats()[post.stat().ordinal()];
            Component line = Component.translatable("crusadecraft.ai.labelled", Component.translatable(post.key(seat.person().female())),
                    seat.person().name()).append(Component.literal(" (" + stat + ")").withStyle(ChatFormatting.GRAY));
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(line.getString(), colW)), x, y, Ui.TEXT);
            if (Ui.inside(mouseX, mouseY, x, y - 1, colW, 10)) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(seat.person().name()).withStyle(ChatFormatting.GOLD));
                tip.add(Component.translatable("crusadecraft.ai.labelled", Component.translatable(post.stat().key()), stat).withStyle(ChatFormatting.GRAY));
                tip.addAll(traitTooltip(seat.person().traits()));
                pendingTooltip = tip;
            }
            y += 11;
        }

        // Provinces
        int rx = left + w / 2 + 4;
        int ry = top + 56;
        Ui.text(g, font, Component.translatable("crusadecraft.ai.provinces", view.provinces().size()), rx, ry, Ui.TEXT_TITLE);
        ry += 12;
        for (AiRealmView.Province p : view.provinces()) {
            if (ry > top + h - 96) break;
            String label = p.name() + (p.capital() ? " ★" : "");
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(label, colW - 60)), rx, ry, p.capital() ? Ui.TEXT_TITLE : Ui.TEXT);
            String pop = Component.translatable("crusadecraft.ai.population", p.population()).getString();
            Ui.text(g, font, Component.literal(pop), rx + colW - font.width(pop), ry, Ui.TEXT_MUTED);
            if (Ui.inside(mouseX, mouseY, rx, ry - 1, colW, 10)) {
                pendingTooltip = List.of(Component.literal(p.name()).withStyle(ChatFormatting.GOLD),
                        Component.translatable(p.anchor().key()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("crusadecraft.ai.development", p.development()).withStyle(ChatFormatting.GRAY),
                        Component.literal("x " + p.x() + ", z " + p.z()).withStyle(ChatFormatting.DARK_GRAY));
            }
            ry += 11;
        }

        // Ressources et relations (le détail des relations est dans l'infobulle).
        int by = top + h - 84;
        resource(g, new ItemStack(Items.GOLD_NUGGET), view.gold(), rx, by);
        resource(g, new ItemStack(Items.GOLDEN_HELMET), view.prestige(), rx + 62, by);
        resource(g, new ItemStack(Items.CANDLE), view.piety(), rx + 124, by);
        Ui.wrapped(g, font, Component.translatable("crusadecraft.ai.discovered", view.discoveredDay(), view.distance()), rx, by + 20, colW, Ui.TEXT_MUTED);
        Component relations = Component.translatable("crusadecraft.map.status.neutral");
        Ui.text(g, font, relations, rx, by + 42, 0xFFE8C066);
        if (Ui.inside(mouseX, mouseY, rx, by + 41, font.width(relations), 10)) {
            pendingTooltip = List.of(relations, Component.translatable("crusadecraft.ai.relations").withStyle(ChatFormatting.GRAY));
        }

        super.render(g, mouseX, mouseY, partialTick);
        if (pendingTooltip != null) g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
    }

    private int statLine(GuiGraphics g, int[] stats, int x, int y, int width, int mouseX, int mouseY) {
        int cx = x;
        for (Stat s : Stat.values()) {
            String label = Component.translatable(s.key() + ".short").getString() + " " + stats[s.ordinal()];
            int lw = font.width(label);
            if (cx + lw > x + width) {
                cx = x;
                y += 11;
            }
            Ui.text(g, font, Component.literal(label), cx, y, Ui.TEXT);
            if (Ui.inside(mouseX, mouseY, cx, y - 1, lw, 10)) {
                pendingTooltip = List.of(Component.translatable("crusadecraft.ai.labelled", Component.translatable(s.key()), stats[s.ordinal()]));
            }
            cx += lw + 8;
        }
        return y + 12;
    }

    private static Component traits(List<Trait> traits) {
        MutableComponent c = Component.translatable("crusadecraft.ai.traits");
        for (int i = 0; i < traits.size(); i++) {
            c.append(i == 0 ? " " : ", ").append(Component.translatable(traits.get(i).key()));
        }
        return c;
    }

    private static List<Component> traitTooltip(List<Trait> traits) {
        List<Component> tip = new ArrayList<>();
        for (Trait t : traits) {
            tip.add(Component.translatable(t.key()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable(t.key() + ".desc").withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private void resource(GuiGraphics g, ItemStack icon, long value, int x, int y) {
        g.renderItem(icon, x, y);
        Ui.text(g, font, Component.literal(String.valueOf(value)), x + 18, y + 5, Ui.TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
