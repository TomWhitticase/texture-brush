package io.github.tomwhitticase.texturebrush;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Builds a floor with the brush in a real world, checks the mix, and screenshots the editor screens. */
public class TextureBrushClientGameTest implements FabricClientGameTest {
    private static final int SIZE = 6;

    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            checkOldConfigMigrates(context);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        // 768x496 at GUI scale 2 gives a 384x248 GUI, about what a MacBook gets at the default "Auto" scale.
        context.getInput().resizeWindow(768, 496);
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    settings.setGameMode(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.CREATIVE);
                })
                .create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:bricks 64");
            world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:granite 64");
            world.getServer().runCommand("item replace entity @a hotbar.2 with minecraft:diamond_sword");
            // A stone platform in the sky, so terrain doesn't get in the way.
            world.getServer().runCommand("fill -6 199 -4 6 199 8 minecraft:stone");
            world.getServer().runCommand("fill -6 200 -4 6 210 8 minecraft:air");
            world.getServer().runCommand("tp @a 0 200 -3 0 45");
            world.getServer().runCommand("attribute @a minecraft:block_interaction_range base set 12");
            context.waitTicks(20);

            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            context.getInput().pressKey(Keys.TOGGLE);
            context.waitTicks(2);
            if (!context.computeOnClient(mc -> BrushController.isEnabled())) throw new AssertionError("V did not turn the brush on");

            // Place a floor one block at a time, the way a player would.
            int groundY = 199;
            // Hover over each spot looking straight down and right-click, so nothing blocks the aim.
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    world.getServer().runCommand("tp @a " + (x - SIZE / 2 + 0.5) + " " + (groundY + 3) + " " + (z + 0.5) + " 0 90");
                    context.waitTicks(3);
                    context.getInput().pressKey(options -> options.keyUse);
                    context.waitTicks(5);
                }
            }
            world.getServer().runCommand("tp @a 0.5 205 -4 0 50");
            context.waitTicks(10);

            int finalGroundY = groundY;
            Map<Block, Integer> counts = world.getServer().computeOnServer(server -> {
                Map<Block, Integer> found = new HashMap<>();
                for (BlockPos pos : BlockPos.betweenClosed(-6, finalGroundY + 1, -4, 6, finalGroundY + 10, 8)) {
                    Block block = server.overworld().getBlockState(pos).getBlock();
                    if (block != Blocks.AIR) found.merge(block, 1, Integer::sum);
                }
                return found;
            });
            StringBuilder summary = new StringBuilder();
            counts.forEach((block, count) -> summary.append(BuiltInRegistries.BLOCK.getKey(block)).append('=').append(count).append(' '));
            System.out.println("[TextureBrushTest] placed: " + summary);
            int bricks = counts.getOrDefault(Blocks.BRICKS, 0);
            int granite = counts.getOrDefault(Blocks.GRANITE, 0);
            if (bricks + granite != SIZE * SIZE) context.takeScreenshot("texturebrush-0-failure");
            if (bricks + granite != SIZE * SIZE) throw new AssertionError("expected only bricks and granite, got " + summary);
            if (bricks == 0 || granite == 0) throw new AssertionError("brush did not mix blocks: " + summary);

            context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            context.waitTicks(5);
            context.takeScreenshot("texturebrush-1-world-hud");

            context.setScreen(() -> new BrushScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("texturebrush-2-editor-hotbar");

            // Click the "+" next to granite (second row) like a player, then check the weight went up.
            int before = context.computeOnClient(mc -> Palettes.weightOf(net.minecraft.world.item.Items.GRANITE));
            clickButton(context, "+", 1);
            int after = context.computeOnClient(mc -> Palettes.weightOf(net.minecraft.world.item.Items.GRANITE));
            System.out.println("[TextureBrushTest] granite weight " + before + " -> " + after);
            if (after != before + 1) throw new AssertionError("+ button did not raise the weight");

            context.runOnClient(mc -> {
                BrushConfig.get().activePalette = "Weathered Stone";
                BrushConfig.get().pattern = BrushPattern.NOISE;
            });
            context.setScreen(() -> new BrushScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("texturebrush-3-editor-palette-noise");

            context.clickScreenButton("+ Add block");
            context.waitTicks(5);
            context.takeScreenshot("texturebrush-4-block-picker");
            context.getInput().typeChars("brick");
            context.waitTicks(5);
            context.takeScreenshot("texturebrush-5-block-picker-search");

            context.setScreen(() -> null);
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:stone_bricks 64");
            context.waitTicks(5);
            context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("texturebrush-6-hud-palette");
        }
    }

    /** A config written by 1.0.0 (text lines for weights and palettes) must load into the structured format. */
    private static void checkOldConfigMigrates(ClientGameTestContext context) throws java.io.IOException {
        context.<java.io.IOException>runOnClient(mc -> {
            java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("texturebrush.json");
            java.nio.file.Files.writeString(file, """
                    {"pattern": "NOISE", "noiseScale": 3, "noiseSeed": 0, "defaultWeight": 1,
                     "weights": ["minecraft:bricks\\u003d3", "minecraft:granite\\u003d1"],
                     "palettes": ["Rustic Brick: minecraft:bricks\\u003d3, minecraft:granite\\u003d1",
                                  "Weathered Stone: minecraft:stone_bricks\\u003d60, minecraft:cracked_stone_bricks\\u003d25"],
                     "activePalette": "", "showHud": true}
                    """);
            BrushConfig.load();
            BrushConfig config = BrushConfig.get();
            System.out.println("[TextureBrushTest] migrated: pattern=" + config.pattern + " hotbar=" + config.hotbarWeights
                    + " palettes=" + config.palettes.stream().map(p -> p.name + p.blocks).toList());
            if (config.pattern != BrushPattern.NOISE || config.hotbarWeights.get("minecraft:granite") != 1
                    || config.palettes.size() != 2 || config.palettes.get(1).blocks.get("minecraft:stone_bricks") != 60) {
                throw new AssertionError("old config did not migrate");
            }
            config.pattern = BrushPattern.RANDOM;
        });
    }

    /** Clicks the nth (0-based) button on the current screen whose label is exactly {@code label}. */
    private static void clickButton(ClientGameTestContext context, String label, int nth) {
        double[] center = context.computeOnClient(mc -> {
            int seen = 0;
            for (var child : mc.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) {
                    if (seen++ == nth) {
                        // Cursor positions are in window units; the GUI is scaled from those.
                        double sx = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                        double sy = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
                        return new double[] {(button.getX() + button.getWidth() / 2.0) * sx, (button.getY() + button.getHeight() / 2.0) * sy};
                    }
                }
            }
            throw new AssertionError("no button '" + label + "' #" + nth);
        });
        context.getInput().setCursorPos(center[0], center[1]);
        context.waitTicks(1);
        // 26.3 uses SDL mouse numbering, where the left button is 1.
        context.getInput().pressMouse(1);
        context.waitTicks(3);
    }
}
