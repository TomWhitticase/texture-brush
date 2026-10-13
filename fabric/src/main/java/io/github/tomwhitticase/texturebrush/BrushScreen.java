package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.BrushConfig.PaletteData;
import io.github.tomwhitticase.texturebrush.Palettes.Entry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Point-and-click editor for everything the brush does: pick the mix, change weights with buttons, add blocks from a
 * searchable grid, and see a live preview wall. Built for mouse or trackpad, no typing needed except names and search.
 */
public class BrushScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int ROW = 20;
    private static final int LIST_ROW = 18;
    private static final int LEFT_WIDTH = 92;
    private static final int PREVIEW_CELLS = 8;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int PANEL = 0x55000000;
    private static final int BAR = 0xFF55CCFF;
    private static final int SELECTED = 0xFFFFD040;

    private final @Nullable Screen parent;
    private final Random random = new Random();
    private int listScroll;
    private int rowScroll;
    private boolean confirmDelete;
    private int previewSeed = 1;

    // Layout, worked out in init() from the current screen size.
    private int top;
    private int bottom;
    private int midX;
    private int midWidth;
    private int previewX;
    private int previewTile;
    private int rowsTop;
    private int visibleRows;
    private final List<RowInfo> rows = new ArrayList<>();
    private @Nullable Button selectedMixButton;
    private int hiddenRows;
    private int hiddenListItems;

    /** One block in the mix being edited. */
    private record RowInfo(Item item, int weight, int y) {}

    public BrushScreen(@Nullable Screen parent) {
        super(Component.literal("Texture Brush"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BrushConfig config = BrushConfig.get();
        rows.clear();
        selectedMixButton = null;

        // Top row: on/off, pattern, HUD.
        int barWidth = Math.min(width - 2 * MARGIN, 360);
        int barX = (width - barWidth) / 2;
        int third = (barWidth - 8) / 3;
        addRenderableWidget(Button.builder(brushLabel(), b -> {
                    BrushController.setEnabled(!BrushController.isEnabled());
                    b.setMessage(brushLabel());
                })
                .bounds(barX, 18, third, 20)
                .tooltip(Tooltip.create(Component.literal("Turn the brush on or off. In game: V")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("Pattern: " + config.pattern.displayName()), b -> {
                    config.pattern = config.pattern.next();
                    refresh();
                })
                .bounds(barX + third + 4, 18, third, 20)
                .tooltip(Tooltip.create(patternHelp(config.pattern)))
                .build());
        addRenderableWidget(Button.builder(hudLabel(), b -> {
                    config.showHud = !config.showHud;
                    b.setMessage(hudLabel());
                })
                .bounds(barX + 2 * (third + 4), 18, third, 20)
                .tooltip(Tooltip.create(Component.literal("Show the mix above the hotbar while the brush is on")))
                .build());

        top = 44;
        if (config.pattern == BrushPattern.NOISE) {
            int sliderWidth = barWidth - third - 4;
            addRenderableWidget(new PatchSizeSlider(barX, 42, sliderWidth, 20));
            addRenderableWidget(Button.builder(Component.literal("Shuffle patches"), b -> {
                        config.noiseSeed = random.nextInt(1_000_000);
                    })
                    .bounds(barX + sliderWidth + 4, 42, third, 20)
                    .tooltip(Tooltip.create(Component.literal("Move the patches around without changing the mix")))
                    .build());
            top = 68;
        }
        bottom = height - 30;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 75, height - 25, 150, 20)
                .build());

        // Columns: mixes on the left, blocks in the middle, preview on the right when there's room.
        int leftX = MARGIN;
        midX = leftX + LEFT_WIDTH + 6;
        previewTile = width >= 470 ? 12 : 8;
        int previewWidth = PREVIEW_CELLS * previewTile;
        boolean showPreview = width >= 340 && bottom - top >= previewWidth + 30;
        previewX = showPreview ? width - MARGIN - previewWidth : width;
        midWidth = (showPreview ? previewX - 8 : width - MARGIN) - midX;

        initMixList(leftX);
        initBlockRows();

        if (showPreview) {
            addRenderableWidget(Button.builder(Component.literal("New preview"), b -> previewSeed++)
                    .bounds(previewX, top + 12 + previewWidth + 4, previewWidth, 16)
                    .tooltip(Tooltip.create(Component.literal("Roll a different example. Noise patches stay put; use Shuffle patches to move them.")))
                    .build());
        }
    }

    private void initMixList(int x) {
        BrushConfig config = BrushConfig.get();
        PaletteData active = Palettes.active();
        List<PaletteData> palettes = config.palettes;

        // Entry 0 is hotbar mode; the rest are saved palettes.
        int capacity = Math.max(1, (bottom - 18 - (top + 12)) / LIST_ROW);
        int total = palettes.size() + 1;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, total - capacity));
        hiddenListItems = Math.max(0, total - capacity);
        for (int i = listScroll; i < Math.min(total, listScroll + capacity); i++) {
            int y = top + 12 + (i - listScroll) * LIST_ROW;
            PaletteData palette = i == 0 ? null : palettes.get(i - 1);
            boolean selected = palette == active;
            String label = palette == null ? "Hotbar blocks" : palette.name;
            Button button = Button.builder(Component.literal(label), b -> {
                        config.activePalette = palette == null ? "" : palette.name;
                        rowScroll = 0;
                        refresh();
                    })
                    .bounds(x, y, LEFT_WIDTH, 16)
                    .tooltip(Tooltip.create(Component.literal(palette == null
                            ? "Mix whatever blocks are in your hotbar"
                            : "Saved mix. Holding any of its blocks starts the brush.")))
                    .build();
            addRenderableWidget(button);
            if (selected) selectedMixButton = button;
        }

        int half = (LEFT_WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("+ New"), b -> createPalette())
                .bounds(x, bottom - 16, half, 16)
                .tooltip(Tooltip.create(Component.literal("New saved mix, starting with the blocks in your hotbar")))
                .build());
        Button delete = Button.builder(Component.literal(confirmDelete ? "Sure?" : "Delete"), b -> {
                    if (!confirmDelete) {
                        confirmDelete = true;
                        rebuildWidgets();
                        return;
                    }
                    config.palettes.remove(active);
                    config.activePalette = "";
                    refresh();
                })
                .bounds(x + half + 4, bottom - 16, half, 16)
                .tooltip(Tooltip.create(Component.literal(confirmDelete ? "Click again to delete this mix" : "Delete the selected saved mix")))
                .build();
        delete.active = active != null;
        addRenderableWidget(delete);
    }

    private void initBlockRows() {
        PaletteData palette = Palettes.active();
        int controlsX = midX + midWidth - (palette == null ? 74 : 92);

        if (palette != null) {
            EditBox name = new EditBox(font, midX, top, midWidth, 16, Component.literal("Mix name"));
            name.setMaxLength(32);
            name.setValue(palette.name);
            name.setResponder(value -> rename(palette, value));
            addRenderableWidget(name);
            addRenderableWidget(Button.builder(Component.literal("+ Add block"), b -> minecraft.gui.setScreen(new BlockPickerScreen(this, palette)))
                    .bounds(midX, bottom - 16, Math.min(100, midWidth), 16)
                    .tooltip(Tooltip.create(Component.literal("Pick a block to add to this mix")))
                    .build());
        }

        rowsTop = top + 20;
        int rowsBottom = palette != null ? bottom - 20 : bottom - 12;
        visibleRows = Math.max(1, (rowsBottom - rowsTop) / ROW);
        List<Item> items = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        if (palette != null) {
            palette.blocks.forEach((id, weight) -> {
                Item item = Palettes.resolve(id);
                if (item != null) {
                    items.add(item);
                    weights.add(weight);
                }
            });
        } else {
            for (Item item : hotbarBlocks()) {
                items.add(item);
                weights.add(Palettes.weightOf(item));
            }
        }

        rowScroll = Math.clamp(rowScroll, 0, Math.max(0, items.size() - visibleRows));
        hiddenRows = Math.max(0, items.size() - visibleRows);
        for (int i = rowScroll; i < Math.min(items.size(), rowScroll + visibleRows); i++) {
            Item item = items.get(i);
            int weight = weights.get(i);
            int y = rowsTop + (i - rowScroll) * ROW;
            rows.add(new RowInfo(item, weight, y));
            int min = palette == null ? 0 : 1;
            IntConsumer setWeight = value -> {
                int clamped = Math.clamp(value, min, 999);
                if (palette != null) palette.blocks.put(Palettes.idOf(item), clamped);
                else BrushConfig.get().hotbarWeights.put(Palettes.idOf(item), clamped);
                refresh();
            };
            Tooltip stepTip = Tooltip.create(Component.literal("Shift-click to change by 5"));
            addRenderableWidget(Button.builder(Component.literal("-"), b -> setWeight.accept(weight - step()))
                    .bounds(controlsX, y + 1, 16, 16).tooltip(stepTip).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> setWeight.accept(weight + step()))
                    .bounds(controlsX + 42, y + 1, 16, 16).tooltip(stepTip).build());
            if (palette != null) {
                addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                            palette.blocks.remove(Palettes.idOf(item));
                            refresh();
                        })
                        .bounds(controlsX + 76, y + 1, 16, 16)
                        .tooltip(Tooltip.create(Component.literal("Remove from this mix")))
                        .build());
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 6, TEXT);

        graphics.fill(MARGIN - 2, top - 2, MARGIN + LEFT_WIDTH + 2, bottom + 2, PANEL);
        graphics.text(font, "Mixes", MARGIN, top, MUTED, false);
        if (selectedMixButton != null) {
            graphics.outline(selectedMixButton.getX() - 1, selectedMixButton.getY() - 1,
                    selectedMixButton.getWidth() + 2, selectedMixButton.getHeight() + 2, SELECTED);
        }
        if (hiddenListItems > 0) graphics.text(font, "↕", MARGIN + LEFT_WIDTH - font.width("↕"), top, MUTED, false);

        graphics.fill(midX - 2, top - 2, midX + midWidth + 2, bottom + 2, PANEL);
        PaletteData palette = Palettes.active();
        if (palette == null) graphics.text(font, "Blocks in your hotbar", midX, top + 4, MUTED, false);
        extractRows(graphics, palette, mouseX, mouseY);

        if (previewX < width) extractPreview(graphics, palette);
    }

    private void extractRows(GuiGraphicsExtractor graphics, @Nullable PaletteData palette, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            String message = palette != null ? "Empty. Click + Add block."
                    : minecraft.player == null ? "Join a world to see your hotbar."
                    : "No blocks in your hotbar yet.";
            graphics.text(font, message, midX + 2, rowsTop + 4, MUTED, false);
            return;
        }

        // Percentages are of the whole mix, including rows scrolled out of view.
        int mixTotal = mixTotal(palette);
        int controlsX = midX + midWidth - (palette == null ? 74 : 92);
        int nameWidth = controlsX - (midX + 20) - 4;
        for (RowInfo row : rows) {
            int y = row.y();
            graphics.item(new ItemStack(row.item()), midX, y + 1);
            String name = new ItemStack(row.item()).getHoverName().getString();
            graphics.text(font, ellipsize(name, nameWidth), midX + 20, y + 2, row.weight() == 0 ? MUTED : TEXT, false);
            float share = mixTotal == 0 ? 0 : (float) row.weight() / mixTotal;
            String percent = row.weight() == 0 ? "off" : Math.round(share * 100) + "%";
            graphics.text(font, percent, midX + 20, y + 11, MUTED, false);
            int barX = midX + 20 + font.width("100%") + 4;
            int barWidth = Math.max(0, controlsX - 4 - barX);
            graphics.fill(barX, y + 13, barX + barWidth, y + 15, 0x40FFFFFF);
            graphics.fill(barX, y + 13, barX + Math.round(barWidth * share), y + 15, BAR);
            graphics.centeredText(font, String.valueOf(row.weight()), controlsX + 29, y + 5, TEXT);

            if (mouseX >= midX && mouseX < midX + 18 && mouseY >= y && mouseY < y + 18) {
                graphics.setTooltipForNextFrame(Component.literal(name), mouseX, mouseY);
            }
        }
        String more = hiddenRows > 0 ? "↕ " + hiddenRows + " more" : "";
        graphics.text(font, more, midX + midWidth - font.width(more), bottom - 11, MUTED, false);
        if (palette == null) {
            graphics.text(font, ellipsize(hotbarHint(), midWidth - font.width(more) - 4), midX, bottom - 11, MUTED, false);
        }
    }

    private void extractPreview(GuiGraphicsExtractor graphics, @Nullable PaletteData palette) {
        int size = PREVIEW_CELLS * previewTile;
        graphics.text(font, "Preview", previewX, top, MUTED, false);
        int gridY = top + 12;
        graphics.fill(previewX - 1, gridY - 1, previewX + size + 1, gridY + size + 1, 0xFF000000);

        List<Entry> entries = palette != null ? Palettes.entries(palette) : hotbarEntries();
        if (entries.isEmpty()) return;
        BrushConfig config = BrushConfig.get();
        int[] weights = entries.stream().mapToInt(Entry::weight).toArray();
        // A fresh seeded picker each frame keeps the preview still until "New preview" is pressed.
        Picker picker = new Picker(new Random(previewSeed));
        for (int row = 0; row < PREVIEW_CELLS; row++) {
            for (int col = 0; col < PREVIEW_CELLS; col++) {
                int blockY = PREVIEW_CELLS - 1 - row;
                int index = picker.pick(config.pattern, weights, col, blockY, 0, config.noiseScale, config.noiseSeed);
                Item item = entries.get(index).item();
                if (!(item instanceof BlockItem blockItem)) continue;
                TextureAtlasSprite sprite = minecraft.getModelManager().getBlockStateModelSet()
                        .getParticleMaterial(blockItem.getBlock().defaultBlockState()).sprite();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, previewX + col * previewTile, gridY + row * previewTile,
                        previewTile, previewTile);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) return true;
        int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (delta == 0) return false;
        if (x < midX - 4 && hiddenListItems > 0) {
            listScroll += delta;
        } else if (x >= midX - 4 && x < midX + midWidth + 4 && hiddenRows > 0) {
            rowScroll += delta;
        } else {
            return false;
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        BrushConfig.save();
    }

    private void refresh() {
        confirmDelete = false;
        rebuildWidgets();
    }

    private void createPalette() {
        BrushConfig config = BrushConfig.get();
        String name = "My Mix";
        for (int n = 2; nameTaken(name); n++) name = "My Mix " + n;
        PaletteData palette = new PaletteData(name);
        for (Item item : hotbarBlocks()) palette.blocks.put(Palettes.idOf(item), Math.max(1, Palettes.weightOf(item)));
        config.palettes.add(palette);
        config.activePalette = name;
        listScroll = Integer.MAX_VALUE;
        rowScroll = 0;
        refresh();
    }

    private void rename(PaletteData palette, String value) {
        String name = value.trim();
        if (name.isEmpty() || (!name.equalsIgnoreCase(palette.name) && nameTaken(name))) return;
        palette.name = name;
        BrushConfig.get().activePalette = name;
        if (selectedMixButton != null) selectedMixButton.setMessage(Component.literal(name));
    }

    private static boolean nameTaken(String name) {
        return BrushConfig.get().palettes.stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
    }

    /** Distinct block items in the hotbar, honouring the slots chosen with the toggle-slot key. */
    private List<Item> hotbarBlocks() {
        LocalPlayer player = minecraft.player;
        if (player == null) return List.of();
        Set<Integer> chosen = BrushController.chosenSlots();
        Set<Item> items = new LinkedHashSet<>();
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            Item item = player.getInventory().getItem(slot).getItem();
            if (item instanceof BlockItem && (chosen.isEmpty() || chosen.contains(slot))) items.add(item);
        }
        return new ArrayList<>(items);
    }

    private List<Entry> hotbarEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Item item : hotbarBlocks()) {
            int weight = Palettes.weightOf(item);
            if (weight > 0) entries.add(new Entry(item, weight));
        }
        return entries;
    }

    private int mixTotal(@Nullable PaletteData palette) {
        return Palettes.totalWeight(palette != null ? Palettes.entries(palette) : hotbarEntries());
    }

    private String hotbarHint() {
        Set<Integer> chosen = BrushController.chosenSlots();
        if (chosen.isEmpty()) return "Set a block to 0 to leave it out.";
        return "Using slots " + String.join(", ", chosen.stream().map(s -> String.valueOf(s + 1)).toList()) + " (U in game)";
    }

    private int step() {
        return minecraft.hasShiftDown() ? 5 : 1;
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static Component brushLabel() {
        return Component.literal("Brush: " + (BrushController.isEnabled() ? "ON" : "OFF"));
    }

    private static Component hudLabel() {
        return Component.literal("HUD: " + (BrushConfig.get().showHud ? "On" : "Off"));
    }

    private static Component patternHelp(BrushPattern pattern) {
        return Component.literal(switch (pattern) {
            case RANDOM -> "Random: every block is a fresh weighted pick. Click to change.";
            case NOISE -> "Noise: natural-looking patches that follow the weights. Click to change.";
            case CYCLE -> "Cycle: evenly spread repeating order, e.g. A A B A A A B. Click to change.";
            case CHECKER -> "Checker: alternates by position, ignoring weights. Click to change.";
        });
    }

    private static final class PatchSizeSlider extends AbstractSliderButton {
        private static final int MAX = 16;

        PatchSizeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), (Math.min(BrushConfig.get().noiseScale, MAX) - 1) / (double) (MAX - 1));
            updateMessage();
            setTooltip(Tooltip.create(Component.literal("How big the patches are, in blocks")));
        }

        private int size() {
            return 1 + (int) Math.round(value * (MAX - 1));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Patch size: " + size()));
        }

        @Override
        protected void applyValue() {
            BrushConfig.get().noiseScale = size();
        }
    }
}
