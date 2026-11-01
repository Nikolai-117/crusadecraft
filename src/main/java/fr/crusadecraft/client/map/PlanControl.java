package fr.crusadecraft.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import fr.crusadecraft.client.KeyBindings;
import fr.crusadecraft.item.PlanItem;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.PlanPackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

/**
 * Les gestes du plan d'architecte, comme dans Schematica (P35) : le fantôme suit le bloc visé, ou reste figé là où on
 * l'a laissé ; on le monte, le descend, le pousse devant, derrière, à gauche, à droite (selon le regard), on le
 * tourne, et le clic droit pose exactement là où il est. Rien de tout cela ne part au serveur avant la pose.
 */
public final class PlanControl {
    private static final String CAT = KeyBindings.CATEGORY;
    public static final KeyMapping ROTATE = key("plan_rotate", GLFW.GLFW_KEY_R);
    public static final KeyMapping UP = key("plan_up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping DOWN = key("plan_down", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping FORWARD = key("plan_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping BACK = key("plan_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping LEFT = key("plan_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping RIGHT = key("plan_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping FREEZE = key("plan_freeze", GLFW.GLFW_KEY_G);
    public static final KeyMapping RESET = key("plan_reset", GLFW.GLFW_KEY_HOME);
    public static final KeyMapping[] ALL = {ROTATE, UP, DOWN, FORWARD, BACK, LEFT, RIGHT, FREEZE, RESET};

    private static int dx, dy, dz;
    private static BlockPos frozen;
    private static String lastRef = "";

    private PlanControl() {}

    private static KeyMapping key(String name, int glfw) {
        return new KeyMapping("key.crusadecraft." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfw, CAT);
    }

    public static boolean frozen() {
        return frozen != null;
    }

    public static int[] offset() {
        return new int[]{dx, dy, dz};
    }

    /** Remet le fantôme sous le viseur, sans décalage. */
    public static void reset() {
        dx = dy = dz = 0;
        frozen = null;
    }

    /** Où le plan sera posé : le bloc figé ou visé, plus le décalage ; vide si l'on ne vise rien. */
    public static Optional<BlockPos> ground(Minecraft mc) {
        if (mc.player == null) return Optional.empty();
        BlockPos base = frozen != null ? frozen : PlanItem.aim(mc.player).map(h -> h.getBlockPos()).orElse(null);
        return base == null ? Optional.empty() : Optional.of(base.offset(dx, dy, dz));
    }

    /** Chaque tick : les touches, tant qu'on tient l'outil et qu'aucun écran n'est ouvert. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        Optional<PlanItem.Choice> choice = PlanItem.held(mc.player).flatMap(PlanItem::choice);
        String ref = choice.map(c -> c.town() ? "town" + c.seed() : c.ref()).orElse("");
        if (!ref.equals(lastRef)) {
            // Un autre plan : le fantôme repart du viseur.
            lastRef = ref;
            reset();
        }
        if (choice.isEmpty() || mc.screen != null) {
            for (KeyMapping k : ALL) while (k.consumeClick()) { /* rien en main : on ignore */ }
            return;
        }
        Direction facing = mc.player.getDirection();
        Direction left = facing.getCounterClockWise();
        boolean moved = false;
        while (ROTATE.consumeClick()) CrusadeNetwork.toServer(new PlanPackets.Rotate());
        while (UP.consumeClick()) {
            dy++;
            moved = true;
        }
        while (DOWN.consumeClick()) {
            dy--;
            moved = true;
        }
        while (FORWARD.consumeClick()) moved |= nudge(facing);
        while (BACK.consumeClick()) moved |= nudge(facing.getOpposite());
        while (LEFT.consumeClick()) moved |= nudge(left);
        while (RIGHT.consumeClick()) moved |= nudge(left.getOpposite());
        while (FREEZE.consumeClick()) {
            if (frozen != null) {
                reset();
                mc.player.displayClientMessage(Component.translatable("crusadecraft.plan.unfrozen"), true);
            } else {
                Optional<BlockPos> g = ground(mc);
                if (g.isPresent()) {
                    frozen = g.get();
                    dx = dy = dz = 0;
                    mc.player.displayClientMessage(Component.translatable("crusadecraft.plan.frozen", frozen.getX(), frozen.getY(), frozen.getZ()), true);
                }
            }
        }
        while (RESET.consumeClick()) {
            reset();
            moved = true;
        }
        if (moved) mc.player.displayClientMessage(Component.translatable("crusadecraft.plan.offset", dx, dy, dz), true);
    }

    private static boolean nudge(Direction d) {
        dx += d.getStepX();
        dz += d.getStepZ();
        return true;
    }

    /** Le clic droit : poser là où est le fantôme (vrai si une pose est partie). */
    public static boolean place(Minecraft mc) {
        Optional<BlockPos> g = ground(mc);
        if (g.isEmpty()) return false;
        CrusadeNetwork.toServer(new PlanPackets.Place(g.get()));
        return true;
    }
}
