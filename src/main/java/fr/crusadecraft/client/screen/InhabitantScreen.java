package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.CityTalk;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.InhabitantPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * La conversation avec un habitant (P42) : son portrait, sa fiche, ce qu'il vient de dire, les sujets qu'on peut aborder.
 * Ses réponses sont écrites d'avance, en grand nombre, et choisies selon qui il est et ce qu'il pense de vous.
 */
public class InhabitantScreen extends Screen {
    private static final int W = 410, H = 244;
    private InhabitantPackets.View view;
    private int left, top;

    public InhabitantScreen(InhabitantPackets.View view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    /** Ouvre la fiche, ou y affiche la nouvelle réplique. */
    public static void open(InhabitantPackets.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InhabitantScreen s && s.view.id().equals(view.id())) {
            s.view = view;
            // Ses commandes ont pu changer : les boutons aussi.
            s.rebuildWidgets();
            return;
        }
        mc.setScreen(new InhabitantScreen(view));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        CityTalk.Topic[] all = CityTalk.Topic.values();
        int bw = (W - 20 - 8) / 4, y = top + H - 46;
        for (int i = 0; i < all.length; i++) {
            CityTalk.Topic t = all[i];
            addRenderableWidget(Button.builder(Component.translatable(t.key()), b -> CrusadeNetwork.toServer(new InhabitantPackets.Say(view.id(), t.ordinal())))
                    .bounds(left + 10 + (i % 4) * (bw + 2), y + (i / 4) * 18, bw, 16).build());
        }
        Button trade = Button.builder(Component.translatable("crusadecraft.city.talk.trade"), b -> {
            CrusadeNetwork.toServer(new InhabitantPackets.Say(view.id(), -1));
            onClose();
        }).bounds(left + 10 + 3 * (bw + 2), y + 18, bw, 16).build();
        trade.active = view.trades();
        addRenderableWidget(trade);
        // P49 : ce que la ville a commandé ; P47 : l'éditeur, pour qui a la main sur la ville.
        if (!view.orders().isEmpty()) {
            net.minecraft.network.chat.MutableComponent tip = Component.translatable("crusadecraft.city.order.tip");
            for (String s : view.orders()) tip.append(Component.literal((char) 10 + "· " + s));
            Button deliver = Button.builder(Component.translatable("crusadecraft.city.order.deliver"), b -> CrusadeNetwork.toServer(new InhabitantPackets.Say(view.id(), InhabitantPackets.DELIVER)))
                    .bounds(left + W - 10 - 64, top + 10, 64, 14).tooltip(net.minecraft.client.gui.components.Tooltip.create(tip)).build();
            deliver.active = view.canDeliver();
            addRenderableWidget(deliver);
        }
        if (view.canEdit()) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.npc.open"), b -> CrusadeNetwork.toServer(new InhabitantPackets.Say(view.id(), InhabitantPackets.EDIT)))
                    .bounds(left + W - 10 - 64, top + 26, 64, 14).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.inset(g, left + 10, top + 10, 70, 96);
        Portraits.render(g, view.entity(), left + 10, top + 10, 70, 96, mx, my);
        int x = left + 90, tw = W - 100, y = top + 10;
        Ui.scaled(g, font, Component.literal(view.name()), x, y, 1.3f, Ui.TEXT_TITLE, false);
        y += 15;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.talk.who", view.job(), view.age()), x, y, tw, Ui.TEXT);
        y += 11;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.talk.where", view.city(), view.realm()), x, y, tw, Ui.TEXT_MUTED);
        y += 11;
        MutableComponent traits = Component.empty();
        Trait[] all = Trait.values();
        boolean first = true;
        for (int t : view.traits()) {
            if (t < 0 || t >= all.length) continue;
            if (!first) traits.append(" · ");
            traits.append(Component.translatable(all[t].key()));
            first = false;
        }
        Ui.fitted(g, font, traits, x, y, tw, Ui.GOLD);
        y += 11;
        int oc = view.opinion() >= 15 ? Ui.GREEN : view.opinion() <= -15 ? Ui.RED : Ui.TEXT_MUTED;
        Ui.fitted(g, font, Component.translatable(opinionKey(view.opinion())), x, y, tw, oc);
        y += 12;
        if (!view.bio().isEmpty()) Ui.wrapped(g, font, Component.literal(view.bio()).withStyle(ChatFormatting.ITALIC), x, y, tw, Ui.TEXT_MUTED);
        int ly = top + 112, lh = H - 112 - 52;
        Ui.inset(g, left + 10, ly, W - 20, lh);
        Ui.wrapped(g, font, Component.literal("« " + view.line() + " »"), left + 18, ly + 6, W - 36, Ui.TEXT);
        if (!view.family().isEmpty()) {
            Ui.fitted(g, font, Component.literal(String.join(" · ", view.family())), left + 18, ly + lh - 11, W - 36, Ui.TEXT_MUTED);
        }
        super.render(g, mx, my, partial);
    }

    private static String opinionKey(int op) {
        if (op >= 50) return "crusadecraft.city.talk.opinion.devoted";
        if (op >= 15) return "crusadecraft.city.talk.opinion.friendly";
        if (op > -15) return "crusadecraft.city.talk.opinion.neutral";
        if (op > -50) return "crusadecraft.city.talk.opinion.cold";
        return "crusadecraft.city.talk.opinion.hostile";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
