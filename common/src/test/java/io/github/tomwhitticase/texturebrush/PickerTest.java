package io.github.tomwhitticase.texturebrush;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PickerTest {
    private static final int[] BRICKS_AND_GRANITE = {3, 1};

    @Test
    void randomFollowsWeights() {
        Picker picker = new Picker(new Random(42));
        int[] counts = new int[2];
        for (int i = 0; i < 40_000; i++) counts[picker.pick(BrushPattern.RANDOM, BRICKS_AND_GRANITE, 0, 0, 0, 3, 0)]++;
        assertEquals(0.75, counts[0] / 40_000.0, 0.01);
    }

    @Test
    void cycleInterleavesEvenly() {
        Picker picker = new Picker(new Random());
        int[] sequence = new int[8];
        for (int i = 0; i < sequence.length; i++) sequence[i] = picker.pick(BrushPattern.CYCLE, BRICKS_AND_GRANITE, i, 0, 0, 3, 0);
        assertArrayEquals(new int[] {0, 0, 1, 0, 0, 0, 1, 0}, sequence);
    }

    @Test
    void cycleRestartsWhenWeightsChange() {
        Picker picker = new Picker(new Random());
        picker.pick(BrushPattern.CYCLE, BRICKS_AND_GRANITE, 0, 0, 0, 3, 0);
        assertEquals(1, picker.pick(BrushPattern.CYCLE, new int[] {1, 5}, 0, 0, 0, 3, 0));
    }

    @Test
    void noiseFollowsWeightsOverAnArea() {
        Picker picker = new Picker(new Random());
        int[] counts = new int[2];
        int total = 0;
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                counts[picker.pick(BrushPattern.NOISE, BRICKS_AND_GRANITE, x, 64, z, 3, 7)]++;
                total++;
            }
        }
        assertEquals(0.75, counts[0] / (double) total, 0.05);
    }

    @Test
    void noiseIsStablePerPositionAndFormsPatches() {
        Picker picker = new Picker(new Random());
        int same = 0;
        for (int x = 0; x < 256; x++) {
            int here = picker.pick(BrushPattern.NOISE, new int[] {1, 1}, x, 10, 5, 4, 1);
            assertEquals(here, picker.pick(BrushPattern.NOISE, new int[] {1, 1}, x, 10, 5, 4, 1));
            if (here == picker.pick(BrushPattern.NOISE, new int[] {1, 1}, x + 1, 10, 5, 4, 1)) same++;
        }
        // Independent 50/50 picks would match their neighbour about half the time; patches match far more often.
        assertTrue(same / 256.0 > 0.7, "neighbours matched " + same + "/256 times");
    }

    @Test
    void noiseSeedChangesTheLayout() {
        int differences = 0;
        for (int x = 0; x < 64; x++) {
            if ((Picker.patchValue(x, 0, 0, 3, 1) < 0.5) != (Picker.patchValue(x, 0, 0, 3, 2) < 0.5)) differences++;
        }
        assertNotEquals(0, differences);
    }

    @Test
    void checkerAlternatesByPosition() {
        Picker picker = new Picker(new Random());
        assertEquals(0, picker.pick(BrushPattern.CHECKER, BRICKS_AND_GRANITE, 0, 0, 0, 3, 0));
        assertEquals(1, picker.pick(BrushPattern.CHECKER, BRICKS_AND_GRANITE, 1, 0, 0, 3, 0));
        assertEquals(1, picker.pick(BrushPattern.CHECKER, BRICKS_AND_GRANITE, 0, 0, -1, 3, 0));
        assertEquals(0, picker.pick(BrushPattern.CHECKER, BRICKS_AND_GRANITE, 1, 1, 0, 3, 0));
    }

    @Test
    void singleOptionAlwaysWins() {
        Picker picker = new Picker(new Random());
        for (BrushPattern pattern : BrushPattern.values()) {
            assertEquals(0, picker.pick(pattern, new int[] {5}, 3, 4, 5, 3, 0));
        }
    }
}
