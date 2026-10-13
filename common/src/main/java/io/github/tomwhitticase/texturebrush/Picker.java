package io.github.tomwhitticase.texturebrush;

import java.util.Arrays;
import java.util.Random;

/**
 * Chooses the next palette option for a placement. Kept free of Minecraft classes so it can be unit tested.
 * Every weight passed in must be above zero.
 */
final class Picker {
    private final Random random;
    // Smooth weighted round-robin state for CYCLE, reset whenever the weights change.
    private int[] cycleWeights = new int[0];
    private int[] cycleCurrent = new int[0];

    Picker(Random random) {
        this.random = random;
    }

    /** Returns the index of the chosen weight. */
    int pick(BrushPattern pattern, int[] weights, int x, int y, int z, int noiseScale, long noiseSeed) {
        if (weights.length == 1) return 0;
        int total = 0;
        for (int weight : weights) total += weight;
        return switch (pattern) {
            case RANDOM -> byWeight(weights, random.nextInt(total));
            case NOISE -> byWeight(weights, (int) (patchValue(x, y, z, noiseScale, noiseSeed) * total));
            case CYCLE -> cycle(weights, total);
            case CHECKER -> Math.floorMod(x + y + z, weights.length);
        };
    }

    private static int byWeight(int[] weights, int roll) {
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }

    /** Interleaves options evenly, e.g. weights 3:1 give A A B A A A B A ... rather than long runs of each. */
    private int cycle(int[] weights, int total) {
        if (!Arrays.equals(weights, cycleWeights)) {
            cycleWeights = weights.clone();
            cycleCurrent = new int[weights.length];
        }
        int best = 0;
        for (int i = 0; i < weights.length; i++) {
            cycleCurrent[i] += weights[i];
            if (cycleCurrent[i] > cycleCurrent[best]) best = i;
        }
        cycleCurrent[best] -= total;
        return best;
    }

    /**
     * Voronoi-style patches: each block belongs to its nearest randomly jittered feature point, and every feature point
     * has one uniform random value. Unlike smooth noise this keeps the configured ratios accurate on average.
     */
    static double patchValue(int blockX, int blockY, int blockZ, double scale, long seed) {
        double px = (blockX + 0.5) / scale;
        double py = (blockY + 0.5) / scale;
        double pz = (blockZ + 0.5) / scale;
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);

        double bestDistance = Double.MAX_VALUE;
        long bestCell = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    long cell = hash(x, y, z, seed);
                    double fx = x + unit(cell, 1) - px;
                    double fy = y + unit(cell, 2) - py;
                    double fz = z + unit(cell, 3) - pz;
                    double distance = fx * fx + fy * fy + fz * fz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestCell = cell;
                    }
                }
            }
        }
        return unit(bestCell, 4);
    }

    private static long hash(int x, int y, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h = mix(h ^ x);
        h = mix(h ^ y);
        return mix(h ^ z);
    }

    /** A value in [0, 1) derived from a cell hash; different salts give independent values. */
    private static double unit(long cellHash, int salt) {
        return (mix(cellHash + salt * 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }

    // SplitMix64 finaliser.
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
