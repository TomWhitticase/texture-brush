package io.github.tomwhitticase.texturebrush;

import io.github.tomwhitticase.texturebrush.Palettes.Entry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Status line above the hotbar (where the vanilla item name normally goes) plus markers under palette slots. */
final class BrushHud {
    private static final int MARKER_COLOR = 0xFF55FF55;
    private static final int MAX_LISTED = 4;

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
        Component line = describe(status);
        int y = height - 59;
        if (!mc.gameMode.canHurtPlayer()) y += 14;
        graphics.text(mc.font, line, (width - mc.font.width(line)) / 2, y, 0xFFFFFFFF, true);
    }

    private static Component describe(BrushController.Status status) {
        BrushPattern pattern = BrushConfig.get().pattern;
        MutableComponent line = Component.literal(status.palette() == null ? "Brush" : status.palette().name())
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" [" + pattern.displayName() + "] ").withStyle(ChatFormatting.GRAY));

        List<Entry> entries = status.entries();
        if (entries.isEmpty()) {
            return line.append(Component.literal(status.palette() == null ? "no blocks in hotbar" : "no palette blocks in inventory")
                    .withStyle(ChatFormatting.RED));
        }
        int total = Palettes.totalWeight(entries);
        boolean usesWeights = pattern != BrushPattern.CHECKER;
        for (int i = 0; i < Math.min(entries.size(), MAX_LISTED); i++) {
            Entry entry = entries.get(i);
            if (i > 0) line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(entry.item().getDefaultInstance().getHoverName().copy().withStyle(ChatFormatting.WHITE));
            int percent = usesWeights ? Math.round(100f * entry.weight() / total) : Math.round(100f / entries.size());
            line.append(Component.literal(" " + percent + "%").withStyle(ChatFormatting.AQUA));
        }
        if (entries.size() > MAX_LISTED) {
            line.append(Component.literal(" +" + (entries.size() - MAX_LISTED) + " more").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }
}
