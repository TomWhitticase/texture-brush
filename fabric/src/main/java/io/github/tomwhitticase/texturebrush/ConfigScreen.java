package io.github.tomwhitticase.texturebrush;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Cloth Config screen for the settings in {@link BrushConfig}. */
final class ConfigScreen {
    private ConfigScreen() {}

    static Screen create(Screen parent) {
        BrushConfig config = BrushConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("texturebrush.configuration.title"))
                .setSavingRunnable(BrushConfig::save);
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("texturebrush.configuration.title"));

        general.addEntry(entries.startEnumSelector(label("pattern"), BrushPattern.class, config.pattern)
                .setDefaultValue(BrushPattern.RANDOM)
                .setEnumNameProvider(p -> Component.literal(((BrushPattern) p).displayName()))
                .setTooltip(tooltip("pattern"))
                .setSaveConsumer(value -> config.pattern = value)
                .build());
        general.addEntry(entries.startStrList(label("weights"), config.weights)
                .setDefaultValue(BrushConfig.DEFAULT_WEIGHTS)
                .setExpanded(true)
                .setInsertInFront(false)
                .setCellErrorSupplier(errors(Palettes::checkWeightEntry))
                .setTooltip(tooltip("weights"))
                .setSaveConsumer(value -> config.weights = new ArrayList<>(value))
                .build());
        general.addEntry(entries.startIntField(label("defaultWeight"), config.defaultWeight)
                .setDefaultValue(1)
                .setMin(0)
                .setMax(1000)
                .setTooltip(tooltip("defaultWeight"))
                .setSaveConsumer(value -> config.defaultWeight = value)
                .build());
        general.addEntry(entries.startStrList(label("palettes"), config.palettes)
                .setDefaultValue(BrushConfig.DEFAULT_PALETTES)
                .setExpanded(true)
                .setInsertInFront(false)
                .setCellErrorSupplier(errors(Palettes::checkPaletteEntry))
                .setTooltip(tooltip("palettes"))
                .setSaveConsumer(value -> config.palettes = new ArrayList<>(value))
                .build());
        general.addEntry(entries.startStrField(label("activePalette"), config.activePalette)
                .setDefaultValue("")
                .setTooltip(tooltip("activePalette"))
                .setSaveConsumer(value -> config.activePalette = value.trim())
                .build());
        general.addEntry(entries.startIntSlider(label("noiseScale"), config.noiseScale, 1, 32)
                .setDefaultValue(3)
                .setTooltip(tooltip("noiseScale"))
                .setSaveConsumer(value -> config.noiseScale = value)
                .build());
        general.addEntry(entries.startIntField(label("noiseSeed"), config.noiseSeed)
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(tooltip("noiseSeed"))
                .setSaveConsumer(value -> config.noiseSeed = value)
                .build());
        general.addEntry(entries.startBooleanToggle(label("showHud"), config.showHud)
                .setDefaultValue(true)
                .setTooltip(tooltip("showHud"))
                .setSaveConsumer(value -> config.showHud = value)
                .build());
        return builder.build();
    }

    private static Component label(String key) {
        return Component.translatable("texturebrush.configuration." + key);
    }

    private static Component[] tooltip(String key) {
        return new Component[] {Component.translatable("texturebrush.configuration." + key + ".tooltip")};
    }

    private static Function<String, Optional<Component>> errors(Function<String, String> check) {
        return line -> Optional.ofNullable(check.apply(line)).map(Component::literal);
    }
}
