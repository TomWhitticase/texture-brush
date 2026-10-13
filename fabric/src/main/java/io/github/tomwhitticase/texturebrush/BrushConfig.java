package io.github.tomwhitticase.texturebrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Settings stored in config/texturebrush.json. Edit in game through Mod Menu (needs Cloth Config) or by hand. */
public final class BrushConfig {
    static final List<String> DEFAULT_WEIGHTS = List.of("minecraft:bricks=3", "minecraft:granite=1");
    static final List<String> DEFAULT_PALETTES = List.of(
            "Rustic Brick: minecraft:bricks=3, minecraft:granite=1",
            "Weathered Stone: minecraft:stone_bricks=60, minecraft:cracked_stone_bricks=25, minecraft:mossy_stone_bricks=15",
            "Deepslate Ruin: minecraft:deepslate_bricks=5, minecraft:cracked_deepslate_bricks=3, minecraft:deepslate_tiles=2, minecraft:cobbled_deepslate=1",
            "Mossy Path: minecraft:cobblestone=4, minecraft:mossy_cobblestone=3, minecraft:gravel=2, minecraft:andesite=1");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("texturebrush.json");
    private static BrushConfig instance = new BrushConfig();

    public BrushPattern pattern = BrushPattern.RANDOM;
    public int noiseScale = 3;
    public int noiseSeed = 0;
    public int defaultWeight = 1;
    public List<String> weights = new ArrayList<>(DEFAULT_WEIGHTS);
    public List<String> palettes = new ArrayList<>(DEFAULT_PALETTES);
    public String activePalette = "";
    public boolean showHud = true;

    public static BrushConfig get() {
        return instance;
    }

    static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                BrushConfig loaded = GSON.fromJson(reader, BrushConfig.class);
                if (loaded != null) instance = loaded.sanitised();
            } catch (IOException | JsonParseException e) {
                TextureBrush.LOGGER.error("Could not read {}, using defaults", FILE, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            TextureBrush.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    private BrushConfig sanitised() {
        if (pattern == null) pattern = BrushPattern.RANDOM;
        noiseScale = Math.clamp(noiseScale, 1, 32);
        noiseSeed = Math.max(noiseSeed, 0);
        defaultWeight = Math.clamp(defaultWeight, 0, 1000);
        if (weights == null) weights = new ArrayList<>();
        if (palettes == null) palettes = new ArrayList<>();
        if (activePalette == null) activePalette = "";
        return this;
    }
}
