package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.BrushConfig.PaletteData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Resolves the block ids stored in the config to items. Unknown or non-block ids are skipped with a warning. */
public final class Palettes {
    public record Entry(Item item, int weight) {}

    private static final Set<String> warned = new HashSet<>();

    private Palettes() {}

    public static int weightOf(Item item) {
        BrushConfig config = BrushConfig.get();
        return config.hotbarWeights.getOrDefault(idOf(item), config.defaultWeight);
    }

    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The active named palette, or null when the brush should use the hotbar. */
    public static @Nullable PaletteData active() {
        String name = BrushConfig.get().activePalette.trim();
        if (name.isEmpty()) return null;
        return BrushConfig.get().palettes.stream().filter(p -> p.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** The palette's placeable blocks with weights above zero, in display order. */
    public static List<Entry> entries(PaletteData palette) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> block : palette.blocks.entrySet()) {
            Item item = resolve(block.getKey());
            if (item != null && block.getValue() > 0) entries.add(new Entry(item, block.getValue()));
        }
        return entries;
    }

    public static boolean contains(PaletteData palette, Item item) {
        return palette.blocks.containsKey(idOf(item));
    }

    public static @Nullable Item resolve(String id) {
        Identifier identifier = Identifier.tryParse(id);
        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
        if (!(item instanceof BlockItem)) {
            if (warned.add(id)) TextureBrush.LOGGER.warn("'{}' is not a placeable block item, skipping it", id);
            return null;
        }
        return item;
    }

    static int totalWeight(List<Entry> entries) {
        return entries.stream().mapToInt(Entry::weight).sum();
    }
}
