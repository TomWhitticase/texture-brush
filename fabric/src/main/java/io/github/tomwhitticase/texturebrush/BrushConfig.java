package io.github.tomwhitticase.texturebrush;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Settings stored in config/texturebrush.json, edited in game with the brush editor ({@link BrushScreen}). */
public final class BrushConfig {
    /** A saved mix: block ids in display order, each with a weight. */
    public static final class PaletteData {
        public String name;
        public LinkedHashMap<String, Integer> blocks = new LinkedHashMap<>();

        PaletteData(String name) {
            this.name = name;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("texturebrush.json");
    private static BrushConfig instance = defaults();

    public BrushPattern pattern = BrushPattern.RANDOM;
    public int noiseScale = 3;
    public int noiseSeed = 0;
    public int defaultWeight = 1;
    public boolean showHud = true;
    /** Weights for blocks in hotbar mode, by block id. Blocks not listed use {@link #defaultWeight}. */
    public LinkedHashMap<String, Integer> hotbarWeights = new LinkedHashMap<>();
    public List<PaletteData> palettes = new ArrayList<>();
    /** Name of the palette in use, or empty for hotbar mode. */
    public String activePalette = "";

    public static BrushConfig get() {
        return instance;
    }

    private static BrushConfig defaults() {
        BrushConfig config = new BrushConfig();
        config.hotbarWeights.put("minecraft:bricks", 3);
        config.hotbarWeights.put("minecraft:granite", 1);
        config.palettes.add(palette("Rustic Brick", "minecraft:bricks", 3, "minecraft:granite", 1));
        config.palettes.add(palette("Weathered Stone", "minecraft:stone_bricks", 60, "minecraft:cracked_stone_bricks", 25,
                "minecraft:mossy_stone_bricks", 15));
        config.palettes.add(palette("Deepslate Ruin", "minecraft:deepslate_bricks", 5, "minecraft:cracked_deepslate_bricks", 3,
                "minecraft:deepslate_tiles", 2, "minecraft:cobbled_deepslate", 1));
        config.palettes.add(palette("Mossy Path", "minecraft:cobblestone", 4, "minecraft:mossy_cobblestone", 3,
                "minecraft:gravel", 2, "minecraft:andesite", 1));
        return config;
    }

    private static PaletteData palette(String name, Object... idsAndWeights) {
        PaletteData palette = new PaletteData(name);
        for (int i = 0; i < idsAndWeights.length; i += 2) {
            palette.blocks.put((String) idsAndWeights[i], (Integer) idsAndWeights[i + 1]);
        }
        return palette;
    }

    static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                migrateTextFormat(json);
                BrushConfig loaded = GSON.fromJson(json, BrushConfig.class);
                if (loaded != null) instance = loaded.sanitised();
            } catch (IOException | JsonParseException | IllegalStateException e) {
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

    /** Version 1.0.0 stored weights and palettes as "id=weight" text lines; convert them to the structured form. */
    private static void migrateTextFormat(JsonObject json) {
        if (json.has("weights") && json.get("weights").isJsonArray() && !json.has("hotbarWeights")) {
            JsonObject weights = new JsonObject();
            for (JsonElement line : json.getAsJsonArray("weights")) addWeights(weights, line.getAsString());
            json.add("hotbarWeights", weights);
        }
        json.remove("weights");
        if (json.has("palettes") && json.get("palettes").isJsonArray()) {
            JsonArray palettes = new JsonArray();
            for (JsonElement element : json.getAsJsonArray("palettes")) {
                if (!element.isJsonPrimitive()) {
                    palettes.add(element);
                    continue;
                }
                String line = element.getAsString();
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                JsonObject palette = new JsonObject();
                palette.addProperty("name", line.substring(0, colon).trim());
                JsonObject blocks = new JsonObject();
                for (String part : line.substring(colon + 1).split(",")) addWeights(blocks, part);
                palette.add("blocks", blocks);
                palettes.add(palette);
            }
            json.add("palettes", palettes);
        }
    }

    private static void addWeights(JsonObject target, String text) {
        int eq = text.indexOf('=');
        String id = (eq < 0 ? text : text.substring(0, eq)).trim();
        if (id.isEmpty()) return;
        try {
            target.addProperty(id, eq < 0 ? 1 : Integer.parseInt(text.substring(eq + 1).trim()));
        } catch (NumberFormatException ignored) {
            // Skip malformed entries, as the old parser did.
        }
    }

    private BrushConfig sanitised() {
        if (pattern == null) pattern = BrushPattern.RANDOM;
        noiseScale = Math.clamp(noiseScale, 1, 32);
        noiseSeed = Math.max(noiseSeed, 0);
        defaultWeight = Math.clamp(defaultWeight, 0, 1000);
        if (hotbarWeights == null) hotbarWeights = new LinkedHashMap<>();
        if (palettes == null) palettes = new ArrayList<>();
        palettes.removeIf(p -> p == null || p.name == null);
        for (PaletteData palette : palettes) {
            if (palette.blocks == null) palette.blocks = new LinkedHashMap<>();
        }
        if (activePalette == null) activePalette = "";
        return this;
    }
}
