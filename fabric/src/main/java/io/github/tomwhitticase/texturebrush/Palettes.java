package io.github.tomwhitticase.texturebrush;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Parses the weight and palette strings from the config. Unknown or non-block items are skipped with a warning. */
public final class Palettes {
    public record Entry(Item item, int weight) {}

    public record Palette(String name, List<Entry> entries) {
        public boolean contains(Item item) {
            return entries.stream().anyMatch(e -> e.item() == item);
        }
    }

    private record RawWeight(Identifier id, int weight) {}

    private static List<String> cachedWeightsSource;
    private static Map<Item, Integer> cachedWeights = Map.of();
    private static List<String> cachedPalettesSource;
    private static List<Palette> cachedPalettes = List.of();

    private Palettes() {}

    public static int weightOf(Item item) {
        return weights().getOrDefault(item, BrushConfig.get().defaultWeight);
    }

    public static List<Palette> all() {
        List<String> raw = BrushConfig.get().palettes;
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
    public static @Nullable Palette active() {
        String name = BrushConfig.get().activePalette.trim();
        if (name.isEmpty()) return null;
        return all().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static Map<Item, Integer> weights() {
        List<String> raw = BrushConfig.get().weights;
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

    /** Returns an error message for the config screen, or null if the line is fine. */
    static @Nullable String checkWeightEntry(String line) {
        return parseWeight(line) == null ? "Use the form namespace:item=weight" : null;
    }

    static @Nullable String checkPaletteEntry(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0 || line.substring(0, colon).isBlank()) return "Start with a name, e.g. My Mix: minecraft:stone=2, ...";
        for (String part : line.substring(colon + 1).split(",")) {
            if (parseWeight(part) == null) return "Bad entry '" + part.trim() + "', use namespace:item=weight";
        }
        return null;
    }

    private static @Nullable Palette parsePalette(String line) {
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

    private static @Nullable RawWeight parseWeight(String text) {
        int eq = text.indexOf('=');
        String idText = (eq < 0 ? text : text.substring(0, eq)).trim();
        if (idText.isEmpty()) return null;
        Identifier id = Identifier.tryParse(idText);
        if (id == null) return null;
        if (eq < 0) return new RawWeight(id, 1);
        try {
            int weight = Integer.parseInt(text.substring(eq + 1).trim());
            return weight < 0 ? null : new RawWeight(id, weight);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @Nullable Item resolve(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (!(item instanceof BlockItem)) {
            TextureBrush.LOGGER.warn("'{}' is not a placeable block item, skipping it", id);
            return null;
        }
        return item;
    }

    static int totalWeight(List<Entry> entries) {
        return entries.stream().mapToInt(Entry::weight).sum();
    }
}
