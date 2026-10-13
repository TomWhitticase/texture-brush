package io.github.tomwhitticase.texturebrush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the weight and palette strings from the config. Validation only checks syntax: client configs load before
 * items are registered, so item ids are resolved lazily and unknown ones are skipped with a log warning.
 */
public final class Palettes {
    public record Entry(Item item, int weight) {}

    public record Palette(String name, List<Entry> entries) {
        public boolean contains(Item item) {
            return entries.stream().anyMatch(e -> e.item() == item);
        }
    }

    private record RawWeight(ResourceLocation id, int weight) {}

    private static List<?> cachedWeightsSource;
    private static Map<Item, Integer> cachedWeights = Map.of();
    private static List<?> cachedPalettesSource;
    private static List<Palette> cachedPalettes = List.of();

    private Palettes() {}

    public static int weightOf(Item item) {
        return weights().getOrDefault(item, BrushConfig.DEFAULT_WEIGHT.get());
    }

    public static List<Palette> all() {
        List<? extends String> raw = BrushConfig.PALETTES.get();
        if (!raw.equals(cachedPalettesSource)) {
            List<Palette> parsed = new ArrayList<>();
            for (String line : raw) {
                Palette palette = parsePalette(line);
                if (palette != null && !palette.entries().isEmpty()) parsed.add(palette);
            }
            cachedPalettes = List.copyOf(parsed);
            cachedPalettesSource = List.copyOf(raw);
        }
        return cachedPalettes;
    }

    /** The active named palette, or null when the brush should use the hotbar. */
    @Nullable
    public static Palette active() {
        String name = BrushConfig.ACTIVE_PALETTE.get().trim();
        if (name.isEmpty()) return null;
        return all().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static Map<Item, Integer> weights() {
        List<? extends String> raw = BrushConfig.WEIGHTS.get();
        if (!raw.equals(cachedWeightsSource)) {
            Map<Item, Integer> parsed = new HashMap<>();
            for (String line : raw) {
                RawWeight weight = parseWeight(line);
                Item item = weight == null ? null : resolve(weight.id());
                if (item != null) parsed.put(item, weight.weight());
            }
            cachedWeights = Map.copyOf(parsed);
            cachedWeightsSource = List.copyOf(raw);
        }
        return cachedWeights;
    }

    static boolean isValidWeightEntry(Object value) {
        return value instanceof String s && parseWeight(s) != null;
    }

    static boolean isValidPaletteEntry(Object value) {
        if (!(value instanceof String s)) return false;
        int colon = s.indexOf(':');
        if (colon <= 0 || s.substring(0, colon).isBlank()) return false;
        String[] parts = s.substring(colon + 1).split(",");
        for (String part : parts) {
            if (parseWeight(part) == null) return false;
        }
        return true;
    }

    @Nullable
    private static Palette parsePalette(String line) {
        // The name ends at the first colon; item ids contain colons too, so split there only once.
        int colon = line.indexOf(':');
        if (colon <= 0) {
            TextureBrush.LOGGER.warn("Ignoring palette without a name: '{}'", line);
            return null;
        }
        String name = line.substring(0, colon).trim();
        List<Entry> entries = new ArrayList<>();
        for (String part : line.substring(colon + 1).split(",")) {
            RawWeight weight = parseWeight(part);
            Item item = weight == null ? null : resolve(weight.id());
            if (item != null && weight.weight() > 0) entries.add(new Entry(item, weight.weight()));
        }
        return new Palette(name, List.copyOf(entries));
    }

    @Nullable
    private static RawWeight parseWeight(String text) {
        int eq = text.indexOf('=');
        String idText = (eq < 0 ? text : text.substring(0, eq)).trim();
        if (idText.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(idText);
        if (id == null) return null;
        if (eq < 0) return new RawWeight(id, 1);
        try {
            int weight = Integer.parseInt(text.substring(eq + 1).trim());
            return weight < 0 ? null : new RawWeight(id, weight);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static Item resolve(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (!(item instanceof BlockItem)) {
            TextureBrush.LOGGER.warn("Texture Brush: '{}' is not a placeable block item, skipping it", id);
            return null;
        }
        return item;
    }

    static int totalWeight(List<Entry> entries) {
        return entries.stream().mapToInt(Entry::weight).sum();
    }
}
