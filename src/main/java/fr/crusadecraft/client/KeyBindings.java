package fr.crusadecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Touches de CrusadeCraft, réglables dans Options → Commandes.
 *
 * <p>Xaero's World Map prend déjà M ; la carte du royaume prend donc la virgule par défaut.
 */
public final class KeyBindings {
    public static final String CATEGORY = "key.categories.crusadecraft";

    public static final KeyMapping REALM_MENU = new KeyMapping("key.crusadecraft.realm_menu",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static final KeyMapping MAP = new KeyMapping("key.crusadecraft.map",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_COMMA, CATEGORY);

    private KeyBindings() {}
}
