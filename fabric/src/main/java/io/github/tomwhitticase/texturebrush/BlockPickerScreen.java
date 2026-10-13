package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.BrushConfig.PaletteData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Searchable grid of every block. Blocks in your inventory come first; clicking one adds it to the mix. */
class BlockPickerScreen extends Screen {
    private static final int CELL = 18;
    private static final int GRID_TOP = 42;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int IN_MIX = 0x8055FF55;

    private final BrushScreen parent;
    private final PaletteData palette;
    private final List<Item> allBlocks;
    private List<Item> shown = List.of();
    private String query = "";
    private int scroll;
    private int columns;
    private int visibleRows;
    private int gridX;

    BlockPickerScreen(BrushScreen parent, PaletteData palette) {
        super(Component.literal("Add a block to " + palette.name));
        this.parent = parent;
        this.palette = palette;
        this.allBlocks = orderedBlocks();
    }

    @Override
    protected void init() {
        int searchWidth = Math.min(200, width - 16);
        EditBox search = new EditBox(font, (width - searchWidth) / 2, 18, searchWidth, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search blocks..."));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scroll = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        columns = Math.max(1, (width - 16) / CELL);
        gridX = (width - columns * CELL) / 2;
        visibleRows = Math.max(1, (height - 32 - GRID_TOP) / CELL);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(width / 2 - 50, height - 25, 100, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> scrollBy(-visibleRows + 1))
                .bounds(width / 2 + 56, height - 25, 40, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Down"), b -> scrollBy(visibleRows - 1))
                .bounds(width / 2 + 100, height - 25, 40, 20)
                .build());
        filter();
    }

    private void filter() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Item> matches = new ArrayList<>();
        for (Item item : allBlocks) {
            if (needle.isEmpty() || Palettes.idOf(item).contains(needle)
                    || new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(item);
            }
        }
        shown = matches;
        scrollBy(0);
    }

    private void scrollBy(int rows) {
        int totalRows = (shown.size() + columns - 1) / columns;
        scroll = Math.clamp(scroll + rows, 0, Math.max(0, totalRows - visibleRows));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 6, TEXT);
        if (shown.isEmpty()) {
            graphics.centeredText(font, Component.literal("No blocks match"), width / 2, GRID_TOP + 10, MUTED);
            return;
        }

        for (int i = 0; i < visibleRows * columns; i++) {
            int index = scroll * columns + i;
            if (index >= shown.size()) break;
            Item item = shown.get(index);
            int x = gridX + (i % columns) * CELL;
            int y = GRID_TOP + (i / columns) * CELL;
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            if (Palettes.contains(palette, item)) graphics.fill(x, y, x + CELL, y + CELL, IN_MIX);
            if (hovered) graphics.fill(x, y, x + CELL, y + CELL, 0x60FFFFFF);
            graphics.item(new ItemStack(item), x + 1, y + 1);
            if (hovered) {
                Component name = new ItemStack(item).getHoverName();
                graphics.setTooltipForNextFrame(Palettes.contains(palette, item)
                        ? name.copy().append(Component.literal(" (in mix, click for +1)")) : name, mouseX, mouseY);
            }
        }
        int totalRows = (shown.size() + columns - 1) / columns;
        if (totalRows > visibleRows) {
            String page = "rows " + (scroll + 1) + "-" + Math.min(totalRows, scroll + visibleRows) + " of " + totalRows;
            graphics.text(font, page, gridX, height - 19, MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int col = (int) Math.floor((event.x() - gridX) / CELL);
        int row = (int) Math.floor((event.y() - GRID_TOP) / CELL);
        if (col < 0 || col >= columns || row < 0 || row >= visibleRows) return false;
        int index = (scroll + row) * columns + col;
        if (index >= shown.size()) return false;
        palette.blocks.merge(Palettes.idOf(shown.get(index)), 1, Integer::sum);
        onClose();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        scrollBy(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private List<Item> orderedBlocks() {
        Set<Item> ordered = new LinkedHashSet<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
                Item item = player.getInventory().getItem(slot).getItem();
                if (item instanceof BlockItem) ordered.add(item);
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof BlockItem) ordered.add(item);
        }
        return new ArrayList<>(ordered);
    }
}
