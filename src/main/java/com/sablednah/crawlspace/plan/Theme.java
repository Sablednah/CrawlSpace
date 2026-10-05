package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * The planner's half of a theme: how a level is shaped. The other half, which
 * blocks it is built from, belongs to the builder and does not exist yet.
 *
 * <p>These are hard-coded while the planner is being judged by eye. They are
 * meant to become data, like LegendQuest's races and classes, once the knobs
 * stop moving.</p>
 *
 * @param shapeWeights   one weight per {@link Shape} ordinal
 * @param styleWeights   one weight per {@link CorridorStyle} ordinal
 * @param narrowChance   chance a corridor is 1 wide instead of 3
 * @param heightSpread   how far a room's floor may sit above or below the level's (blocks)
 * @param poolChance     chance a shrine or cave gets a pool
 * @param roomScale      multiplies room sizes
 */
public record Theme(
        String name,
        double[] shapeWeights,
        double[] styleWeights,
        double narrowChance,
        int heightSpread,
        double poolChance,
        double roomScale) {

    //                                        RECT ROUND CIRC OCT CROSS  L   CAVE HALL
    public static final Theme CRYPT = new Theme("Crypt",
            new double[] {4, 1, 0.5, 2, 2, 1, 0, 1.5},
            new double[] {6, 2, 0, 1}, // STRAIGHT ANGLED WINDING CURVED
            0.35, 0, 0.0, 1.0);

    public static final Theme SUNKEN_HALLS = new Theme("Sunken Halls",
            new double[] {2, 1, 2, 2, 1, 1, 0, 3},
            new double[] {2, 3, 0, 3},
            0.2, 1, 0.3, 1.1);

    public static final Theme OLD_MINES = new Theme("Old Mines",
            new double[] {2, 2, 0, 0, 0, 2, 2, 0},
            new double[] {1, 3, 3, 0},
            0.5, 2, 0.1, 0.95);

    public static final Theme CAVERNS = new Theme("Caverns",
            new double[] {0, 1, 2, 0, 0, 0, 6, 0},
            new double[] {0, 1, 5, 2},
            0.3, 2, 0.35, 1.15);

    public static final Theme DEEP_HALLS = new Theme("Deep Halls",
            new double[] {1, 1, 2, 2, 2, 0, 0, 3},
            new double[] {2, 2, 0, 3},
            0.15, 1, 0.2, 1.25);

    public static final List<Theme> BY_DEPTH = List.of(
            CRYPT, CRYPT, SUNKEN_HALLS, SUNKEN_HALLS, OLD_MINES, OLD_MINES, CAVERNS, CAVERNS, DEEP_HALLS);

    /** The default theme for level {@code index} (0 = the top). */
    public static Theme forDepth(int index) {
        return BY_DEPTH.get(Math.min(index, BY_DEPTH.size() - 1));
    }
}
