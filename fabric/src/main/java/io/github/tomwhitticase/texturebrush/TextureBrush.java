package io.github.tomwhitticase.texturebrush;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureBrush implements ClientModInitializer {
    public static final String MODID = "texturebrush";
    public static final Logger LOGGER = LoggerFactory.getLogger("Texture Brush");

    @Override
    public void onInitializeClient() {
        BrushConfig.load();
        Keys.register();
        ClientTickEvents.END_CLIENT_TICK.register(BrushController::onClientTick);

        HudElementRegistry.attachElementAfter(VanillaHudElements.HELD_ITEM_TOOLTIP, id("hud"), BrushHud::render);
        // The brush switches items constantly; hide the vanilla item name popup, the HUD takes its place.
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original -> (HudElement) (graphics, delta) -> {
            if (!BrushHud.isShowing()) original.extractRenderState(graphics, delta);
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
