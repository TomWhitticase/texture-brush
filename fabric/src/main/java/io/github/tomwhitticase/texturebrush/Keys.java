package io.github.tomwhitticase.texturebrush;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Key bindings, all rebindable under Options > Controls > Key Binds > Texture Brush. Vanilla key mappings have no
 * modifier support, so each action gets its own key. These defaults are only otherwise used by F3 debug
 * combos, which don't trigger plain key mappings.
 */
final class Keys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(TextureBrush.id("main"));

    static final KeyMapping TOGGLE = key("toggle", InputConstants.KEY_V);
    static final KeyMapping TOGGLE_SLOT = key("toggle_slot", InputConstants.KEY_U);
    static final KeyMapping CYCLE_PATTERN = key("cycle_pattern", InputConstants.KEY_Y);
    static final KeyMapping CYCLE_PALETTE = key("cycle_palette", InputConstants.KEY_N);
    static final KeyMapping OPEN_EDITOR = key("open_editor", InputConstants.KEY_B);

    private Keys() {}

    static void register() {
        KeyMappingHelper.registerKeyMapping(TOGGLE);
        KeyMappingHelper.registerKeyMapping(TOGGLE_SLOT);
        KeyMappingHelper.registerKeyMapping(CYCLE_PATTERN);
        KeyMappingHelper.registerKeyMapping(CYCLE_PALETTE);
        KeyMappingHelper.registerKeyMapping(OPEN_EDITOR);
    }

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping("key.texturebrush." + name, InputConstants.Type.KEYBOARD, keyCode, CATEGORY);
    }
}
