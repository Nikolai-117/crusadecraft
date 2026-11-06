package fr.crusadecraft.client.hud;

import fr.crusadecraft.client.screen.Ui;
import fr.crusadecraft.core.build.plan.PlanRef;
import fr.crusadecraft.item.PlanItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le HUD du plan d'architecte (P34.1) : en haut à gauche, tant qu'on tient l'outil, le plan choisi (nom, d'où il
 * vient, taille, côté de l'entrée), la distance du bloc visé, les gestes ; et, pendant une pose, sa barre
 * d'avancement, même l'outil rangé. Il paraît et s'efface en fondu.
 */
public final class PlanHud {
    private static final int W = 236, PAD = 7;
    private static float alpha;
    private static long lastNanos;
    private static String jobName;
    private static int jobPercent = -1;
    private static long jobUntil;
    private static float shownPercent;

    private PlanHud() {}

    /** L'avancement d'une pose, reçu du serveur. */
    public static void progress(String name, int percent) {
        if (!name.equals(jobName) || percent < jobPercent) shownPercent = 0;
        jobName = name;
        jobPercent = percent;
        jobUntil = Util.getMillis() + (percent >= 100 ? 3500 : 20000);
    }

    private static int fade(int argb, float a) {
        // Le texte de Minecraft tient un alpha presque nul pour opaque : jamais sous 5.
        return (Math.max(5, (int) ((argb >>> 24) * a)) << 24) | (argb & 0xFFFFFF);
    }

    /** Le côté de l'entrée une fois tourné : l'entrée des plans est au sud. */
    private static Component facing(int rotation) {
        String side = switch (Math.floorMod(rotation, 4)) {
            case 1 -> "west";
            case 2 -> "north";
            case 3 -> "east";
            default -> "south";
        };
        return Component.translatable("crusadecraft.plan.hud.side." + side);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Optional<ItemStack> tool = PlanItem.held(mc.player);
        boolean holding = tool.isPresent() && mc.screen == null;
        long now = Util.getNanos();
        float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        alpha = Mth.clamp(alpha + (holding ? dt * 5 : -dt * 5), 0, 1);
        boolean job = jobPercent >= 0 && Util.getMillis() < jobUntil;
        if (job) shownPercent += (jobPercent - shownPercent) * Math.min(1, dt * 8);
        if (alpha < 0.04f && !job) return;
        Font font = mc.font;
        float a = job && alpha < 0.04f ? 1f : alpha;
        int x = 8, y = 8;

        List<Component[]> lines = new ArrayList<>();
        Component title = Component.translatable("item.crusadecraft.plan_architecte");
        Optional<PlanItem.Choice> choice = tool.flatMap(PlanItem::choice);
        int barY;
        int h;
        if (alpha >= 0.04f) {
            if (choice.isPresent()) {
                PlanItem.Choice c = choice.get();
                lines.add(new Component[]{Component.literal(c.name()), null});
                Component origin = fr.crusadecraft.core.build.plan.UrbanService.isDesign(c.ref()) ? Component.translatable("crusadecraft.urban.hud") : c.town() ? Component.translatable("crusadecraft.plan.hud.town")
                        : PlanRef.decode(c.ref()).map(r -> (Component) Component.literal(r.source() == PlanRef.Source.FILE
                        ? Component.translatable("crusadecraft.plan.source.files").getString() : r.pack())).orElse(Component.empty());
                lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.from"), origin});
                lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.size"), c.town()
                        ? Component.translatable("crusadecraft.plan.hud.extent", c.sizeX(), c.sizeZ())
                        : Component.translatable("crusadecraft.plan.tooltip.size", c.sizeX(), c.sizeY(), c.sizeZ())});
                lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.door"), facing(c.rotation())});
                Optional<net.minecraft.core.BlockPos> target = fr.crusadecraft.client.map.PlanControl.ground(mc);
                lines.add(new Component[]{Component.translatable(fr.crusadecraft.client.map.PlanControl.frozen()
                        ? "crusadecraft.plan.hud.frozen" : "crusadecraft.plan.hud.target"), target.map(pos -> (Component) Component.translatable(
                        "crusadecraft.plan.hud.distance", (int) Math.round(Math.sqrt(pos.distToCenterSqr(mc.player.getEyePosition()))),
                        pos.getX(), pos.getY(), pos.getZ()))
                        .orElse(Component.translatable("crusadecraft.plan.hud.no_target"))});
                int[] off = fr.crusadecraft.client.map.PlanControl.offset();
                if (off[0] != 0 || off[1] != 0 || off[2] != 0) {
                    lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.shift"),
                            Component.translatable("crusadecraft.plan.hud.shift.value", off[0], off[1], off[2])});
                }
                {
                    String ghost = fr.crusadecraft.core.build.plan.PlanTool.ghostRef(c);
                    boolean here = fr.crusadecraft.client.map.GhostRenderer.ref().equals(ghost);
                    int n = here ? fr.crusadecraft.client.map.GhostRenderer.size() : -1;
                    lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.ghost"), n < 0 ? Component.translatable("crusadecraft.plan.loading")
                            : !fr.crusadecraft.client.map.GhostRenderer.ready() ? Component.translatable("crusadecraft.plan.hud.ghost.preparing", n)
                            : Component.translatable("crusadecraft.plan.hud.ghost.blocks", n)});
                }
            } else {
                lines.add(new Component[]{Component.translatable("crusadecraft.plan.hud.none"), null});
            }
        }
        List<Component> hints = alpha >= 0.04f ? List.of(Component.translatable("crusadecraft.plan.hud.hint.place"),
                Component.translatable("crusadecraft.plan.hud.hint.turn"), Component.translatable("crusadecraft.plan.hud.hint.raise"),
                Component.translatable("crusadecraft.plan.hud.hint.move"), Component.translatable("crusadecraft.plan.hud.hint.freeze"),
                Component.translatable("crusadecraft.plan.hud.hint.catalog")) : List.of();
        h = PAD + (alpha >= 0.04f ? 12 + lines.size() * 11 + 4 + hints.size() * 10 : 0) + (job ? 22 : 0) + PAD - 2;

        // Le panneau : fond sombre, liseré doré, filet de lumière en haut.
        g.fill(x + 2, y + 2, x + W + 2, y + h + 2, fade(0x60000000, a));
        g.fill(x, y, x + W, y + h, fade(Ui.PANEL, a * 0.92f));
        g.fillGradient(x + 1, y + 1, x + W - 1, y + 14, fade(0x40C9A55C, a), fade(0x00C9A55C, a));
        g.renderOutline(x, y, W, h, fade(Ui.GOLD, a));
        int cy = y + PAD;
        if (alpha >= 0.04f) {
            if (alpha > 0.95f && tool.isPresent()) g.renderItem(tool.get(), x + W - 22, y + 4);
            g.drawString(font, title, x + PAD, cy, fade(Ui.TEXT_TITLE, a), false);
            cy += 12;
            for (Component[] line : lines) {
                if (line[1] == null) {
                    g.drawString(font, font.plainSubstrByWidth(line[0].getString(), W - 2 * PAD), x + PAD, cy, fade(Ui.TEXT, a), false);
                } else {
                    g.drawString(font, line[0], x + PAD, cy, fade(Ui.TEXT_MUTED, a), false);
                    int lw = font.width(line[0]) + 4;
                    g.drawString(font, font.plainSubstrByWidth(line[1].getString(), W - 2 * PAD - lw), x + PAD + lw, cy, fade(Ui.TEXT, a), false);
                }
                cy += 11;
            }
            g.fill(x + PAD, cy + 1, x + W - PAD, cy + 2, fade(Ui.GOLD_DARK, a));
            cy += 4;
            for (Component hint : hints) {
                g.drawString(font, font.plainSubstrByWidth(hint.getString(), W - 2 * PAD), x + PAD, cy, fade(Ui.TEXT_MUTED, a * 0.9f), false);
                cy += 10;
            }
        }
        if (job) {
            barY = cy + 2;
            Component label = jobPercent >= 100 ? Component.translatable("crusadecraft.plan.hud.done", jobName)
                    : Component.translatable("crusadecraft.plan.hud.pasting", jobName, Math.round(shownPercent));
            g.drawString(font, font.plainSubstrByWidth(label.getString(), W - 2 * PAD), x + PAD, barY, fade(jobPercent >= 100 ? Ui.GREEN : Ui.TEXT, a), false);
            int bx = x + PAD, by = barY + 11, bw = W - 2 * PAD;
            g.fill(bx, by, bx + bw, by + 5, fade(Ui.PANEL_INNER, a));
            g.fill(bx, by, bx + (int) (bw * Mth.clamp(shownPercent / 100f, 0, 1)), by + 5, fade(jobPercent >= 100 ? Ui.GREEN : Ui.GOLD, a));
            g.renderOutline(bx - 1, by - 1, bw + 2, 7, fade(Ui.GOLD_DARK, a));
        }
    }
}
