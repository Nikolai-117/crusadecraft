package fr.crusadecraft.client.hud;

import fr.crusadecraft.client.city.CityClient;
import fr.crusadecraft.client.screen.Ui;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.item.IntendantStaffItem;
import fr.crusadecraft.network.packet.BuildingPackets;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le HUD du bâton d'intendant (P38) : en haut à gauche, la ville où l'on est (ses bâtiments, ses gens), le bâtiment
 * visé (sa fonction, ou celle qu'on propose) et les gestes. Il paraît et s'efface en fondu, comme celui du plan.
 */
public final class CityHud {
    private static final int W = 220, PAD = 7;
    private static float alpha;
    private static long lastNanos;

    private CityHud() {}

    private static int fade(int argb, float a) {
        return (Math.max(5, (int) ((argb >>> 24) * a)) << 24) | (argb & 0xFFFFFF);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        boolean holding = IntendantStaffItem.held(mc.player).isPresent() && mc.screen == null;
        long now = Util.getNanos();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        alpha = Mth.clamp(alpha + (holding ? dt * 5 : -dt * 5), 0, 1);
        if (alpha < 0.04f) return;
        Font font = mc.font;
        Optional<BuildingPackets.Outlines> city = CityClient.current().filter(c -> !c.city().isEmpty());
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (city.isPresent()) {
            int decided = 0;
            for (BuildingPackets.Outline o : city.get().outlines()) if (o.function() >= 0) decided++;
            lines.add(Component.translatable("crusadecraft.city.hud.city", city.get().city()));
            colors.add(Ui.TEXT_TITLE);
            lines.add(Component.translatable("crusadecraft.city.hud.counts", city.get().outlines().size(), decided, city.get().people()));
            colors.add(Ui.TEXT_MUTED);
            Optional<BuildingPackets.Outline> aimed = CityClient.aimed();
            if (aimed.isPresent()) {
                BuildingPackets.Outline o = aimed.get();
                BuildingFunction f = BuildingFunction.byOrdinal(o.function());
                BuildingFunction s = BuildingFunction.byOrdinal(o.suggestion());
                Component what = f != null ? Component.translatable(f.key())
                        : s != null ? Component.translatable("crusadecraft.city.screen.row.suggested", Component.translatable(s.key()))
                        : Component.translatable("crusadecraft.city.screen.undecided");
                if (!o.name().isEmpty()) what = Component.literal(o.name() + " — ").append(what);
                lines.add(what);
                colors.add(0xFF000000 | CityClient.color(o.function()));
                if (o.occupants() > 0) {
                    lines.add(Component.translatable("crusadecraft.city.hud.people", o.occupants()));
                    colors.add(Ui.TEXT_MUTED);
                }
            }
        } else {
            lines.add(Component.translatable("crusadecraft.city.hud.outside"));
            colors.add(Ui.TEXT_MUTED);
        }
        lines.add(Component.translatable("crusadecraft.city.hud.hint.1"));
        colors.add(0xFF8A7C60);
        lines.add(Component.translatable("crusadecraft.city.hud.hint.2"));
        colors.add(0xFF8A7C60);
        lines.add(Component.translatable("crusadecraft.city.hud.hint.3"));
        colors.add(0xFF8A7C60);
        int x = 8, y = 8;
        int h = PAD * 2 + 12 + lines.size() * 10;
        g.fill(x, y, x + W, y + h, fade(0xC81A1511, alpha));
        g.fill(x, y, x + W, y + 1, fade(0xFFC9A55C, alpha));
        Ui.fitted(g, font, Component.translatable("item.crusadecraft.baton_intendant"), x + PAD, y + PAD, W - PAD * 2, fade(Ui.GOLD, alpha));
        int ly = y + PAD + 12;
        for (int i = 0; i < lines.size(); i++) {
            Ui.fitted(g, font, lines.get(i), x + PAD, ly, W - PAD * 2, fade(colors.get(i), alpha));
            ly += 10;
        }
    }
}
