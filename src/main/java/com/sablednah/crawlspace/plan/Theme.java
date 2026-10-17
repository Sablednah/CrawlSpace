package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * The planner's half of a theme: how a level is shaped. The other half, which
 * blocks it is built from, belongs to the builder and does not exist yet.
 *
 * <p>The five built-in themes are here; a datapack can change their numbers
 * or add themes of its own (ThemeData). A dungeon keeps a copy of the themes
 * it was planned with (Site), so editing a pack never moves a wall in one
 * that already exists.</p>
 *
 * @param shapeWeights   one weight per {@link Shape} ordinal
 * @param styleWeights   one weight per {@link CorridorStyle} ordinal
 * @param narrowChance   chance a corridor is 1 wide instead of 3
 * @param heightSpread   how far a room's floor may sit above or below the level's (blocks)
 * @param poolChance     chance a shrine or cave gets a pool
 * @param roomScale      multiplies room sizes
 * @param dressing       how its rooms are furnished
 */
public record Theme(
        String name,
        double[] shapeWeights,
        double[] styleWeights,
        double narrowChance,
        int heightSpread,
        double poolChance,
        double roomScale,
        Dressing dressing) {

    /** The range of the numbers a datapack may set, proven by ThemeKnobsTest: outside it a level may not plan. */
    public static final int MAX_SPREAD = 2;
    public static final double MIN_SCALE = 0.8;
    public static final double MAX_SCALE = 1.3;

    /** Shape names, in {@link Shape} order, as a datapack writes them. */
    public static final List<String> SHAPES = List.of("rect", "rounded", "circle", "octagon", "cross", "l", "cave", "hall");
    /** Corridor style names, in {@link CorridorStyle} order. */
    public static final List<String> CORRIDORS = List.of("straight", "angled", "winding", "curved");

    //                                        RECT ROUND CIRC OCT CROSS  L   CAVE HALL
    public static final Theme CRYPT = new Theme("Crypt",
            new double[] {4, 1, 0.5, 2, 2, 1, 0, 1.5},
            new double[] {6, 2, 0, 1}, // STRAIGHT ANGLED WINDING CURVED
            0.35, 0, 0.0, 1.0, Dressing.CRYPT);

    public static final Theme SUNKEN_HALLS = new Theme("Sunken Halls",
            new double[] {2, 1, 2, 2, 1, 1, 0, 3},
            new double[] {2, 3, 0, 3},
            0.2, 1, 0.3, 1.1, Dressing.SUNKEN_HALLS);

    public static final Theme OLD_MINES = new Theme("Old Mines",
            new double[] {2, 2, 0, 0, 0, 2, 2, 0},
            new double[] {1, 3, 3, 0},
            0.5, 2, 0.1, 0.95, Dressing.OLD_MINES);

    public static final Theme CAVERNS = new Theme("Caverns",
            new double[] {0, 1, 2, 0, 0, 0, 6, 0},
            new double[] {0, 1, 5, 2},
            0.3, 2, 0.35, 1.15, Dressing.CAVERNS);

    public static final Theme DEEP_HALLS = new Theme("Deep Halls",
            new double[] {1, 1, 2, 2, 2, 0, 0, 3},
            new double[] {2, 2, 0, 3},
            0.15, 1, 0.2, 1.25, Dressing.DEEP_HALLS);

    public static final List<Theme> BY_DEPTH = List.of(
            CRYPT, CRYPT, SUNKEN_HALLS, SUNKEN_HALLS, OLD_MINES, OLD_MINES, CAVERNS, CAVERNS, DEEP_HALLS);

    /** The five built-in themes, by name. */
    public static final List<Theme> BUILT_IN = List.of(CRYPT, SUNKEN_HALLS, OLD_MINES, CAVERNS, DEEP_HALLS);

    /** "Sunken Halls", "sunken_halls" and "Sunken-Halls" are one theme. */
    public static String id(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    /** A built-in theme by any spelling of its name, or null. */
    public static Theme builtIn(String name) {
        for (Theme t : BUILT_IN) {
            if (id(t.name()).equals(id(name))) {
                return t;
            }
        }
        return null;
    }

    /** The default theme for level {@code index} (0 = the top). */
    public static Theme forDepth(int index) {
        return at(BY_DEPTH, index);
    }

    /** Level {@code index}'s theme from a list by depth: past its end, the last one repeats. */
    public static Theme at(List<Theme> byDepth, int index) {
        return byDepth.get(Math.min(index, byDepth.size() - 1));
    }
}
