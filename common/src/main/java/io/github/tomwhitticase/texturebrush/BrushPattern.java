package io.github.tomwhitticase.texturebrush;

/** How the brush chooses the next block from the palette. */
public enum BrushPattern {
    /** Weighted random choice on every placement. */
    RANDOM,
    /** Weighted choice per patch of blocks, so the mix forms natural clumps. Same spot always gives the same block. */
    NOISE,
    /** Steps through the palette in an evenly interleaved order that respects the weights. */
    CYCLE,
    /** Alternates blocks by coordinate, ignoring weights. */
    CHECKER;

    public BrushPattern next() {
        BrushPattern[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String displayName() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
