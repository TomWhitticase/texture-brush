package io.github.tomwhitticase.texturebrush;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Key bindings, all rebindable under Options > Controls > Key Binds > Texture Brush. */
final class Keys {
    private static final String CATEGORY = "key.categories.texturebrush";

    static final KeyMapping TOGGLE = key("toggle", KeyModifier.NONE, GLFW.GLFW_KEY_G);
    static final KeyMapping TOGGLE_SLOT = key("toggle_slot", KeyModifier.ALT, GLFW.GLFW_KEY_G);
    static final KeyMapping CYCLE_PATTERN = key("cycle_pattern", KeyModifier.ALT, GLFW.GLFW_KEY_P);
    static final KeyMapping CYCLE_PALETTE = key("cycle_palette", KeyModifier.ALT, GLFW.GLFW_KEY_L);

    private Keys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(TOGGLE_SLOT);
        event.register(CYCLE_PATTERN);
        event.register(CYCLE_PALETTE);
    }

    private static KeyMapping key(String name, KeyModifier modifier, int keyCode) {
        return new KeyMapping("key.texturebrush." + name, KeyConflictContext.IN_GAME, modifier,
                InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }
}
