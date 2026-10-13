package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.Palettes.Entry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** Status line above the hotbar (where the vanilla item name normally goes) plus markers under palette slots. */
final class BrushHud {
    private static final int MARKER_COLOR = 0xFF55FF55;
    private static final int MAX_LISTED = 4;
    private static final int ICON = 10;

    private BrushHud() {}

    static boolean isShowing() {
        LocalPlayer player = Minecraft.getInstance().player;
        return BrushController.isEnabled() && BrushConfig.get().showHud && player != null && !player.isSpectator();
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!isShowing() || mc.gameMode == null) return;

        BrushController.Status status = BrushController.status(mc.player);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        for (int slot : status.markedSlots()) {
            int x = width / 2 - 88 + slot * 20;
            graphics.fill(x, height - 3, x + 16, height - 2, MARKER_COLOR);
        }

        // Same spot as the vanilla held item name.
        int y = height - 59;
        if (!mc.gameMode.canHurtPlayer()) y += 14;
        Font font = mc.font;
        MutableComponent header = header(status);
        List<Entry> entries = status.entries();
        if (entries.isEmpty()) {
            header.append(Component.literal(status.palette() == null ? "no blocks in hotbar" : "no palette blocks in inventory")
                    .withStyle(ChatFormatting.RED));
            graphics.text(font, header, (width - font.width(header)) / 2, y, 0xFFFFFFFF, true);
            return;
        }

        Component full = withNames(header.copy(), entries);
        if (font.width(full) <= width - 20) {
            graphics.text(font, full, (width - font.width(full)) / 2, y, 0xFFFFFFFF, true);
            return;
        }

        // Too wide for block names: show a small icon for each block instead.
        int shown = Math.min(entries.size(), MAX_LISTED);
        int lineWidth = font.width(header);
        for (int i = 0; i < shown; i++) lineWidth += ICON + 2 + font.width(percent(entries, i)) + 4;
        int x = (width - lineWidth) / 2;
        graphics.text(font, header, x, y, 0xFFFFFFFF, true);
        x += font.width(header);
        for (int i = 0; i < shown; i++) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y - 1);
            graphics.pose().scale(ICON / 16f);
            graphics.item(new ItemStack(entries.get(i).item()), 0, 0);
            graphics.pose().popMatrix();
            x += ICON + 2;
            String percent = percent(entries, i);
            graphics.text(font, percent, x, y, 0xFF55FFFF, true);
            x += font.width(percent) + 4;
        }
    }

    private static MutableComponent header(BrushController.Status status) {
        return Component.literal(status.palette() == null ? "Brush" : status.palette().name)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" [" + BrushConfig.get().pattern.displayName() + "] ").withStyle(ChatFormatting.GRAY));
    }

    private static Component withNames(MutableComponent line, List<Entry> entries) {
        for (int i = 0; i < Math.min(entries.size(), MAX_LISTED); i++) {
            if (i > 0) line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(entries.get(i).item().getDefaultInstance().getHoverName().copy().withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(" " + percent(entries, i)).withStyle(ChatFormatting.AQUA));
        }
        if (entries.size() > MAX_LISTED) {
            line.append(Component.literal(" +" + (entries.size() - MAX_LISTED) + " more").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    private static String percent(List<Entry> entries, int index) {
        if (BrushConfig.get().pattern == BrushPattern.CHECKER) return Math.round(100f / entries.size()) + "%";
        return Math.round(100f * entries.get(index).weight() / Palettes.totalWeight(entries)) + "%";
    }
}
