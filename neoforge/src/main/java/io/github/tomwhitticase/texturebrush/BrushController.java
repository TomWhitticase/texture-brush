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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * Swaps the held block just before each placement. NeoForge fires the click event before vanilla reads the held item
 * and sends the selected slot, so the server only ever sees normal slot changes and inventory clicks.
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        while (Keys.TOGGLE.consumeClick()) toggle(player);
        while (Keys.TOGGLE_SLOT.consumeClick()) toggleSlot(player);
        while (Keys.CYCLE_PATTERN.consumeClick()) cyclePattern(player);
        while (Keys.CYCLE_PALETTE.consumeClick()) cyclePalette(player);
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!enabled || !event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || player.isSpectator()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem)) return;

        Status status = status(player);
        // Only take over when the player is actually holding one of the palette blocks.
        boolean holdingPaletteBlock = status.palette() == null
                ? status.markedSlots().contains(player.getInventory().selected)
                : status.palette().contains(held.getItem());
        if (!holdingPaletteBlock || status.entries().isEmpty()) return;

        BlockPos target = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, held, hit).getClickedPos();
        List<Entry> entries = status.entries();
        int[] weights = entries.stream().mapToInt(Entry::weight).toArray();
        int chosen = PICKER.pick(BrushConfig.PATTERN.get(), weights, target.getX(), target.getY(), target.getZ(),
                BrushConfig.NOISE_SCALE.get(), BrushConfig.NOISE_SEED.get());
        equip(mc, player, entries.get(chosen).item());
    }

    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        // The brush switches items constantly; hide the vanilla item name popup, the HUD takes its place.
        if (enabled && BrushConfig.SHOW_HUD.get() && event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.setCanceled(true);
        }
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
            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                if (palette.contains(inventory.getItem(slot).getItem())) slots.add(slot);
            }
            return new Status(palette, available, slots);
        }

        Map<Item, Integer> weights = new LinkedHashMap<>();
        Set<Integer> slots = new TreeSet<>();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
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
        if (inventory.getSelected().is(item)) return;

        int hotbarSlot = slotOf(inventory, item, 0, Inventory.getSelectionSize());
        if (hotbarSlot >= 0) {
            inventory.selected = hotbarSlot;
            return;
        }

        // Named palettes may need blocks that aren't on the hotbar: bring them into the held slot.
        if (player.hasInfiniteMaterials()) {
            ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize());
            inventory.setItem(inventory.selected, stack);
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + inventory.selected);
            return;
        }
        int inventorySlot = slotOf(inventory, item, Inventory.getSelectionSize(), Inventory.INVENTORY_SIZE);
        if (inventorySlot >= 0 && player.containerMenu == player.inventoryMenu) {
            // In the player's inventory menu, main inventory slots 9-35 have the same index as in Inventory.
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, inventorySlot,
                    inventory.selected, ClickType.SWAP, player);
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
        int slot = player.getInventory().selected;
        if (!chosenSlots.remove(slot)) chosenSlots.add(slot);
        String message = chosenSlots.isEmpty()
                ? "Palette: every block in the hotbar"
                : "Slot " + (slot + 1) + (chosenSlots.contains(slot) ? " added" : " removed")
                        + " (palette slots: " + String.join(", ", chosenSlots.stream().map(s -> String.valueOf(s + 1)).toList()) + ")";
        notify(player, Component.literal(message));
    }

    private static void cyclePattern(LocalPlayer player) {
        BrushPattern next = BrushConfig.PATTERN.get().next();
        BrushConfig.PATTERN.set(next);
        BrushConfig.PATTERN.save();
        notify(player, Component.literal("Brush pattern: " + next.displayName()));
    }

    private static void cyclePalette(LocalPlayer player) {
        List<String> names = new ArrayList<>();
        names.add("");
        Palettes.all().forEach(p -> names.add(p.name()));
        Palette current = Palettes.active();
        int index = current == null ? 0 : names.indexOf(current.name());
        String next = names.get((index + 1) % names.size());
        BrushConfig.ACTIVE_PALETTE.set(next);
        BrushConfig.ACTIVE_PALETTE.save();
        notify(player, Component.literal("Brush palette: " + (next.isEmpty() ? "Hotbar" : next)));
    }

    private static void notify(LocalPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}
