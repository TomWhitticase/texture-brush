package io.github.tomwhitticase.texturebrush;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Builds two walls with the brush in a real world, clicks through the editor, and checks old configs still load.
 * Screenshots land in build/run/clientGameTest/screenshots; the README images come from here.
 */
public class TextureBrushClientGameTest implements FabricClientGameTest {
    private static final int GROUND = 199;
    private static final int WALL_Z = 6;
    private static final int WALL_WIDTH = 10;
    private static final int WALL_HEIGHT = 6;
    private static final int BRICK_WALL_X = -11;
    private static final int STONE_WALL_X = 1;

    @Override
    public void runTest(ClientGameTestContext context) {
        checkOldConfigMigrates(context);

        context.getInput().resizeWindow(1280, 800);
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon");
            // A stone platform in the sky, so terrain doesn't get in the way.
            server.runCommand("fill -16 " + GROUND + " -14 16 " + GROUND + " 10 minecraft:smooth_stone");
            server.runCommand("fill -16 " + (GROUND + 1) + " -14 16 " + (GROUND + 12) + " 10 minecraft:air");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:bricks 64");
            server.runCommand("item replace entity @a hotbar.1 with minecraft:granite 64");
            server.runCommand("item replace entity @a hotbar.2 with minecraft:diamond_sword");
            server.runCommand("attribute @a minecraft:block_interaction_range base set 12");
            context.waitTicks(20);
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));

            context.getInput().pressKey(Keys.TOGGLE);
            context.waitTicks(2);
            if (!context.computeOnClient(mc -> BrushController.isEnabled())) throw new AssertionError("V did not turn the brush on");

            // Wall 1: hotbar mode, bricks 3 : granite 1, random.
            buildWall(context, server, BRICK_WALL_X);
            Map<Block, Integer> brickWall = countWall(server, BRICK_WALL_X);
            System.out.println("[TextureBrushTest] brick wall: " + describe(brickWall));
            assertOnly(brickWall, Set.of(Blocks.BRICKS, Blocks.GRANITE));

            // Wall 2: the Weathered Stone palette with the Noise pattern, switched with the in-game keys.
            server.runCommand("item replace entity @a hotbar.0 with minecraft:stone_bricks 64");
            context.waitTicks(5);
            context.getInput().pressKey(Keys.CYCLE_PALETTE);
            context.getInput().pressKey(Keys.CYCLE_PALETTE);
            context.getInput().pressKey(Keys.CYCLE_PATTERN);
            context.waitTicks(2);
            String active = context.computeOnClient(mc -> BrushConfig.get().activePalette + "/" + BrushConfig.get().pattern);
            if (!active.equals("Weathered Stone/NOISE")) throw new AssertionError("N/Y keys gave " + active);
            buildWall(context, server, STONE_WALL_X);
            Map<Block, Integer> stoneWall = countWall(server, STONE_WALL_X);
            System.out.println("[TextureBrushTest] stone wall: " + describe(stoneWall));
            assertOnly(stoneWall, Set.of(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS));

            server.runCommand("tp @a 0.5 " + (GROUND + 2) + " -9.5 0 4");
            context.waitTicks(20);
            context.runOnClient(mc -> mc.gui.hud.getChat().clearMessages(false));
            context.takeScreenshot("showcase-walls");

            context.setScreen(() -> new BrushScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("editor-palette-noise");

            context.clickScreenButton("+ Add block");
            context.waitTicks(5);
            context.getInput().typeChars("stone brick");
            context.waitTicks(5);
            context.takeScreenshot("block-picker");

            // Back to hotbar mode with bricks and granite, then raise granite with the "+" button like a player.
            server.runCommand("item replace entity @a hotbar.0 with minecraft:bricks 64");
            context.runOnClient(mc -> {
                BrushConfig.get().activePalette = "";
                BrushConfig.get().pattern = BrushPattern.RANDOM;
            });
            context.setScreen(() -> new BrushScreen(null));
            context.waitTicks(5);
            int before = context.computeOnClient(mc -> Palettes.weightOf(Items.GRANITE));
            clickButton(context, "+", 1);
            int after = context.computeOnClient(mc -> Palettes.weightOf(Items.GRANITE));
            System.out.println("[TextureBrushTest] granite weight " + before + " -> " + after);
            if (after != before + 1) throw new AssertionError("+ button did not raise the weight");
            context.takeScreenshot("editor-hotbar");
        }
    }

    /** Builds a wall column by column from above, right-clicking like a player, so each block goes on top. */
    private static void buildWall(ClientGameTestContext context, TestServerContext server, int startX) {
        for (int y = 0; y < WALL_HEIGHT; y++) {
            for (int x = startX; x < startX + WALL_WIDTH; x++) {
                server.runCommand("tp @a " + (x + 0.5) + " " + (GROUND + y + 3) + " " + (WALL_Z + 0.5) + " 0 90");
                context.waitTicks(3);
                context.getInput().pressKey(options -> options.keyUse);
                context.waitTicks(4);
            }
        }
    }

    private static Map<Block, Integer> countWall(TestServerContext server, int startX) {
        return server.computeOnServer(s -> {
            Map<Block, Integer> found = new HashMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(startX, GROUND + 1, WALL_Z, startX + WALL_WIDTH - 1, GROUND + WALL_HEIGHT, WALL_Z)) {
                found.merge(s.overworld().getBlockState(pos).getBlock(), 1, Integer::sum);
            }
            return found;
        });
    }

    private static void assertOnly(Map<Block, Integer> counts, Set<Block> allowed) {
        if (!allowed.containsAll(counts.keySet())) throw new AssertionError("unexpected blocks: " + describe(counts));
        if (counts.size() < 2) throw new AssertionError("brush did not mix blocks: " + describe(counts));
    }

    private static String describe(Map<Block, Integer> counts) {
        StringBuilder summary = new StringBuilder();
        counts.forEach((block, count) -> summary.append(BuiltInRegistries.BLOCK.getKey(block)).append('=').append(count).append(' '));
        return summary.toString().trim();
    }

    /** A config written by 1.0.0 (text lines for weights and palettes) must load into the structured format. */
    private static void checkOldConfigMigrates(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("texturebrush.json");
            try {
                Files.writeString(file, """
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
                // Start the rest of the test from the defaults.
                Files.delete(file);
                BrushConfig.load();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Clicks the nth (0-based) button on the current screen whose label is exactly {@code label}. */
    private static void clickButton(ClientGameTestContext context, String label, int nth) {
        double[] center = context.computeOnClient(mc -> {
            int seen = 0;
            for (var child : mc.gui.screen().children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(label) && seen++ == nth) {
                    // Cursor positions are in window units; the GUI is scaled from those.
                    double sx = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                    double sy = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
                    return new double[] {(button.getX() + button.getWidth() / 2.0) * sx, (button.getY() + button.getHeight() / 2.0) * sy};
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
