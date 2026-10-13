package io.github.tomwhitticase.texturebrush;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** Only loaded by Mod Menu. The screen itself needs Cloth Config, so it is skipped when that isn't installed. */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) return parent -> null;
        return ConfigScreen::create;
    }
}
