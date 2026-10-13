package io.github.tomwhitticase.texturebrush;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

// Client-only entrypoint: on a dedicated server this mod contributes nothing.
@Mod(value = TextureBrush.MODID, dist = Dist.CLIENT)
public class TextureBrush {
    public static final String MODID = "texturebrush";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TextureBrush(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, BrushConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener((RegisterKeyMappingsEvent event) -> Keys.register(event));
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.SELECTED_ITEM_NAME, id("hud"), BrushHud::render));

        NeoForge.EVENT_BUS.register(BrushController.class);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
