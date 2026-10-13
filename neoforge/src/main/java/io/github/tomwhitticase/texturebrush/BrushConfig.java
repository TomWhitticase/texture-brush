package io.github.tomwhitticase.texturebrush;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class BrushConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<BrushPattern> PATTERN = BUILDER
            .comment("How the next block is chosen: RANDOM, NOISE (patches), CYCLE or CHECKER.")
            .defineEnum("pattern", BrushPattern.RANDOM);

    public static final ModConfigSpec.IntValue NOISE_SCALE = BUILDER
            .comment("Rough size, in blocks, of the patches made by the NOISE pattern.")
            .defineInRange("noiseScale", 3, 1, 32);

    public static final ModConfigSpec.IntValue NOISE_SEED = BUILDER
            .comment("Change this to get a different arrangement of NOISE patches.")
            .defineInRange("noiseSeed", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DEFAULT_WEIGHT = BUILDER
            .comment("Weight for hotbar blocks that have no entry in 'weights'.")
            .defineInRange("defaultWeight", 1, 0, 1000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WEIGHTS = BUILDER
            .comment("Hotbar mode weights, as \"item=weight\". Example: \"minecraft:bricks=3\" with granite at the",
                    "default weight of 1 gives 75% bricks and 25% granite. A weight of 0 leaves the block out.")
            .defineListAllowEmpty("weights", List.of("minecraft:bricks=3", "minecraft:granite=1"),
                    () -> "minecraft:stone=1", Palettes::isValidWeightEntry);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PALETTES = BUILDER
            .comment("Named palettes, as \"Name: item=weight, item=weight, ...\". Cycle through them in game",
                    "(Alt+L by default). The brush then pulls the blocks from your inventory, or creates them in creative.")
            .defineListAllowEmpty("palettes", List.of(
                    "Rustic Brick: minecraft:bricks=3, minecraft:granite=1",
                    "Weathered Stone: minecraft:stone_bricks=60, minecraft:cracked_stone_bricks=25, minecraft:mossy_stone_bricks=15",
                    "Deepslate Ruin: minecraft:deepslate_bricks=5, minecraft:cracked_deepslate_bricks=3, minecraft:deepslate_tiles=2, minecraft:cobbled_deepslate=1",
                    "Mossy Path: minecraft:cobblestone=4, minecraft:mossy_cobblestone=3, minecraft:gravel=2, minecraft:andesite=1"
            ), () -> "My Palette: minecraft:stone=1", Palettes::isValidPaletteEntry);

    public static final ModConfigSpec.ConfigValue<String> ACTIVE_PALETTE = BUILDER
            .comment("Name of the palette in use. Leave empty to use the blocks in your hotbar.")
            .define("activePalette", "");

    public static final ModConfigSpec.BooleanValue SHOW_HUD = BUILDER
            .comment("Show the brush status above the hotbar and mark palette slots while the brush is on.")
            .define("showHud", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private BrushConfig() {}
}
