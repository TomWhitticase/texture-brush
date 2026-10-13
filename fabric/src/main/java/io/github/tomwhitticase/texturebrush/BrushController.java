package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.Palettes.Entry;
import io.github.tomwhitticase.texturebrush.Palettes.Palette;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Swaps the held block just before each placement. The mixin calls in before vanilla reads the held item and sends the
 * selected slot, so the server only ever sees normal slot changes and inventory clicks.
 */
public final class BrushController {
    private static final Picker PICKER = new Picker(new Random());
    private static boolean enabled;
    // Hotbar slots the player picked with the toggle-slot key. Empty means every block in the hotbar.
    private static final Set<Integer> chosenSlots = new TreeSet<>();

    /** What the brush is currently working with, for placement and the HUD. */
    record Status(@Nullable Palette palette, List<Entry> entries, Set<Integer> markedSlots) {}

    private BrushController() {}

    static boolean isEnabled() {
        return enabled;
    }

    static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (Keys.TOGGLE.consumeClick()) toggle(player);
        while (Keys.TOGGLE_SLOT.consumeClick()) toggleSlot(player);
        while (Keys.CYCLE_PATTERN.consumeClick()) cyclePattern(player);
        while (Keys.CYCLE_PALETTE.consumeClick()) cyclePalette(player);
    }

    public static void beforeUse(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!enabled || player == null || mc.gameMode == null || player.isSpectator()) return;
        if (mc.gameMode.isDestroying() || player.isHandsBusy()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem)) return;

        Status status = status(player);
        // Only take over when the player is actually holding one of the palette blocks.
        boolean holdingPaletteBlock = status.palette() == null
                ? status.markedSlots().contains(player.getInventory().getSelectedSlot())
                : status.palette().contains(held.getItem());
        if (!holdingPaletteBlock || status.entries().isEmpty()) return;

        BlockPos target = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit).getClickedPos();
        List<Entry> entries = status.entries();
        int[] weights = entries.stream().mapToInt(Entry::weight).toArray();
        BrushConfig config = BrushConfig.get();
        int chosen = PICKER.pick(config.pattern, weights, target.getX(), target.getY(), target.getZ(),
                config.noiseScale, config.noiseSeed);
        equip(mc, player, entries.get(chosen).item());
    }

    static Status status(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        Palette palette = Palettes.active();
        if (palette != null) {
            boolean infinite = player.hasInfiniteMaterials();
            List<Entry> available = palette.entries().stream()
                    .filter(e -> infinite || slotOf(inventory, e.item(), 0, Inventory.INVENTORY_SIZE) >= 0)
                    .toList();
            Set<Integer> slots = new TreeSet<>();
            for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
                if (palette.contains(inventory.getItem(slot).getItem())) slots.add(slot);
            }
            return new Status(palette, available, slots);
        }

        Map<Item, Integer> weights = new LinkedHashMap<>();
        Set<Integer> slots = new TreeSet<>();
        for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
            Item item = inventory.getItem(slot).getItem();
            if (!(item instanceof BlockItem) || (!chosenSlots.isEmpty() && !chosenSlots.contains(slot))) continue;
            int weight = Palettes.weightOf(item);
            if (weight <= 0) continue;
            slots.add(slot);
            weights.put(item, weight);
        }
        List<Entry> entries = new ArrayList<>();
        weights.forEach((item, weight) -> entries.add(new Entry(item, weight)));
        return new Status(null, entries, slots);
    }

    private static void equip(Minecraft mc, LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedItem().is(item)) return;

        int hotbarSlot = slotOf(inventory, item, 0, Inventory.SELECTION_SIZE);
        if (hotbarSlot >= 0) {
            inventory.setSelectedSlot(hotbarSlot);
            return;
        }

        // Named palettes may need blocks that aren't on the hotbar: bring them into the held slot.
        int selected = inventory.getSelectedSlot();
        if (player.hasInfiniteMaterials()) {
            ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize());
            inventory.setItem(selected, stack);
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + selected);
            return;
        }
        int inventorySlot = slotOf(inventory, item, Inventory.SELECTION_SIZE, Inventory.INVENTORY_SIZE);
        if (inventorySlot >= 0 && player.containerMenu == player.inventoryMenu) {
            // In the player's inventory menu, main inventory slots 9-35 have the same index as in Inventory.
            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, inventorySlot, selected,
                    ContainerInput.SWAP, player);
        }
    }

    private static int slotOf(Inventory inventory, Item item, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (inventory.getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static void toggle(LocalPlayer player) {
        enabled = !enabled;
        notify(player, Component.literal("Texture Brush: ")
                .append(enabled ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED)));
    }

    private static void toggleSlot(LocalPlayer player) {
        int slot = player.getInventory().getSelectedSlot();
        if (!chosenSlots.remove(slot)) chosenSlots.add(slot);
        String message = chosenSlots.isEmpty()
                ? "Palette: every block in the hotbar"
                : "Slot " + (slot + 1) + (chosenSlots.contains(slot) ? " added" : " removed")
                        + " (palette slots: " + String.join(", ", chosenSlots.stream().map(s -> String.valueOf(s + 1)).toList()) + ")";
        notify(player, Component.literal(message));
    }

    private static void cyclePattern(LocalPlayer player) {
        BrushConfig config = BrushConfig.get();
        config.pattern = config.pattern.next();
        BrushConfig.save();
        notify(player, Component.literal("Brush pattern: " + config.pattern.displayName()));
    }

    private static void cyclePalette(LocalPlayer player) {
        List<String> names = new ArrayList<>();
        names.add("");
        Palettes.all().forEach(p -> names.add(p.name()));
        Palette current = Palettes.active();
        int index = current == null ? 0 : names.indexOf(current.name());
        String next = names.get((index + 1) % names.size());
        BrushConfig.get().activePalette = next;
        BrushConfig.save();
        notify(player, Component.literal("Brush palette: " + (next.isEmpty() ? "Hotbar" : next)));
    }

    private static void notify(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
