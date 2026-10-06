package com.sablednah.crawlspace.build;

import com.sablednah.crawlspace.plan.Dice;

/**
 * The entrance: a building over the spiral stair's top, in one of four designs.
 * All of them keep the stair's hole open at (0, 0), a floor round it at y = -1
 * (the ground), and the door or opening on the north side. Materials come from
 * the biome's style through the palette; these only lay out parts.
 *
 * <ul>
 *   <li><b>KEEP</b>: a square castle tower. Buttressed, quoined corners,
 *       string courses, barred windows, and corbels carrying an overhanging
 *       crenellated parapet.</li>
 *   <li><b>ROUND</b>: a round tower with bands and windows, under a stepped
 *       cone or corbelled battlements.</li>
 *   <li><b>PYRAMID</b>: stepped and stair-faced, with a capstone and an
 *       entrance tunnel to a chamber over the stair.</li>
 *   <li><b>TEMPLE</b>: a stepped platform, a colonnade, an entablature and a
 *       gabled pediment roof.</li>
 * </ul>
 *
 * <p>The spiral's newel rises to y = 6, so every design closes its ground
 * floor at y = 7 or lower. Everything below ground skips the 5x5 round the
 * hole, which belongs to the stair well.</p>
 */
public final class Towers {

    public enum Design { KEEP, ROUND, PYRAMID, TEMPLE }

    /** The highest any design reaches above the ground, for the blueprint's height. */
    public static final int MAX_HEIGHT = 30;
    /** The furthest any design reaches from its centre. */
    public static final int REACH = 6;

    private Towers() {
    }

    /** The design for a biome style, from the dungeon's seed. */
    public static Design choose(String style, Dice dice) {
        double[] w = switch (style) { // KEEP, ROUND, PYRAMID, TEMPLE
            case "sandstone" -> new double[] {0, 1, 3, 2};
            case "terracotta" -> new double[] {1, 0, 2, 2};
            case "mossy" -> new double[] {1, 1, 2, 0};
            case "cherry" -> new double[] {0, 2, 0, 2};
            case "mushroom" -> new double[] {0, 1, 0, 0};
            case "snowy" -> new double[] {2, 2, 0, 0};
            default -> new double[] {3, 2, 0, 1};
        };
        return Design.values()[dice.weighted(w)];
    }

    public static void build(Blueprint bp, Design design, Dice dice) {
        switch (design) {
            case KEEP -> keep(bp, dice);
            case ROUND -> round(bp, dice);
            case PYRAMID -> pyramid(bp);
            case TEMPLE -> temple(bp, dice);
        }
    }

    private static void set(Blueprint bp, int x, int y, int z, Part part) {
        bp.set(x, y, z, part, 0, 0);
    }

    private static void set(Blueprint bp, int x, int y, int z, Part part, int facing) {
        bp.set(x, y, z, part, facing, 0);
    }

    /** Chebyshev ring: 0 at the centre. */
    private static int ring(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z));
    }

    /** The facing that points from (x, z) toward the centre. */
    private static int inward(int x, int z) {
        if (Math.abs(x) >= Math.abs(z)) {
            return x > 0 ? 3 : 1;
        }
        return z > 0 ? 0 : 2;
    }

    /** Below ground under a footprint: solid, but never the stair well's 5x5. */
    private static void foundation(Blueprint bp, int x, int z) {
        if (ring(x, z) <= 2) {
            return;
        }
        for (int y = -6; y <= -2; y++) {
            set(bp, x, y, z, Part.TOWER);
        }
    }

    /** The ground floor round the hole, and air over it up to {@code top}. */
    private static void groundFloor(Blueprint bp, int x, int z, int top) {
        int r = ring(x, z);
        if (r >= 2) {
            set(bp, x, -1, z, Part.TOWER_FLOOR);
        }
        if (r >= 1) {
            for (int y = 0; y <= top; y++) {
                set(bp, x, y, z, Part.AIR);
            }
        }
    }

    /** A door in the north wall at z = -wall, with a frame, a doorstep out, and torches either side. */
    private static void door(Blueprint bp, int wall) {
        set(bp, 0, 0, -wall, Part.TOWER_DOOR_LOWER, 0);
        set(bp, 0, 1, -wall, Part.TOWER_DOOR_UPPER, 0);
        for (int y = 0; y <= 2; y++) {
            set(bp, -1, y, -wall, Part.TOWER_TRIM);
            set(bp, 1, y, -wall, Part.TOWER_TRIM);
        }
        set(bp, 0, 2, -wall, Part.TOWER_TRIM);
        for (int k = 1; k <= 2; k++) {
            set(bp, 0, -1, -wall - k, Part.TOWER_FLOOR);
            for (int y = 0; y <= 2; y++) {
                set(bp, 0, y, -wall - k, Part.AIR);
            }
        }
        set(bp, -1, 2, -wall - 1, Part.WALL_TORCH, 0);
        set(bp, 1, 2, -wall - 1, Part.WALL_TORCH, 0);
    }

    // ---- KEEP ----

    private static void keep(Blueprint bp, Dice dice) {
        int w = 4;
        int h = 15 + dice.nextInt(4);
        for (int x = -w - 1; x <= w + 1; x++) {
            for (int z = -w - 1; z <= w + 1; z++) {
                int r = ring(x, z);
                if (r <= w) {
                    foundation(bp, x, z);
                }
                if (r < w) {
                    groundFloor(bp, x, z, 6);
                    set(bp, x, 7, z, Part.TOWER_FLOOR); // the floor of the storey above
                    for (int y = 8; y < h; y++) {
                        set(bp, x, y, z, Part.AIR);
                    }
                    set(bp, x, h, z, Part.TOWER); // roof
                } else if (r == w) {
                    boolean corner = Math.abs(x) == w && Math.abs(z) == w;
                    for (int y = -1; y < h; y++) {
                        boolean band = y == 3 || y == h - 3;
                        Part p = band || (corner && Math.floorMod(y, 2) == 0) ? Part.TOWER_TRIM : Part.TOWER;
                        set(bp, x, y, z, p);
                    }
                    set(bp, x, h, z, Part.TOWER);
                    // Windows, two high, at the middle of each face, on both storeys.
                    if ((x == 0 || z == 0) && !(x == 0 && z == -w)) {
                        for (int y : new int[] {4, 5}) {
                            set(bp, x, y, z, Part.TOWER_WINDOW);
                        }
                    }
                    if (x == 0 || z == 0) {
                        for (int y : new int[] {10, 11}) {
                            set(bp, x, y, z, Part.TOWER_WINDOW);
                        }
                    }
                } else {
                    // One out: buttresses at the corners low down, corbels and a parapet up top.
                    boolean nearCorner = Math.abs(x) >= w && Math.abs(z) >= w - 1 || Math.abs(z) >= w && Math.abs(x) >= w - 1;
                    if (nearCorner) {
                        for (int y = -4; y <= 2; y++) {
                            set(bp, x, y, z, Part.TOWER_TRIM);
                        }
                        set(bp, x, 3, z, Part.TOWER_STAIR, inward(x, z)); // the buttress's sloped cap
                    }
                    set(bp, x, h - 1, z, Part.TOWER_CORBEL, inward(x, z));
                    set(bp, x, h, z, Part.TOWER);
                    set(bp, x, h + 1, z, Part.TOWER);
                    if (Math.floorMod(x + z, 2) == 0) {
                        set(bp, x, h + 2, z, Part.TOWER_TOP);
                    }
                }
            }
        }
        door(bp, w);
        for (int[] c : new int[][] {{2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
            set(bp, c[0], 6, c[1], Part.LIGHT);
        }
    }

    // ---- ROUND ----

    private static void round(Blueprint bp, Dice dice) {
        double outer = 4.6;
        double inner = 3.5;
        int h = 14 + dice.nextInt(6);
        boolean cone = dice.chance(0.55);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                double d = Math.hypot(x, z);
                if (d <= outer) {
                    foundation(bp, x, z);
                }
                if (d <= inner) {
                    groundFloor(bp, x, z, 6);
                    set(bp, x, 7, z, Part.TOWER_FLOOR);
                    for (int y = 8; y < h; y++) {
                        set(bp, x, y, z, Part.AIR);
                    }
                    set(bp, x, h, z, Part.TOWER);
                } else if (d <= outer) {
                    for (int y = -1; y < h; y++) {
                        set(bp, x, y, z, y == 3 || y == h - 3 ? Part.TOWER_TRIM : Part.TOWER);
                    }
                    set(bp, x, h, z, Part.TOWER);
                    boolean cardinal = (x == 0 || z == 0) && Math.abs(x + z) == 4;
                    if (cardinal) {
                        if (!(x == 0 && z == -4)) {
                            set(bp, x, 4, z, Part.TOWER_WINDOW);
                            set(bp, x, 5, z, Part.TOWER_WINDOW);
                        }
                        set(bp, x, 10, z, Part.TOWER_WINDOW);
                        set(bp, x, 11, z, Part.TOWER_WINDOW);
                    }
                } else if (d <= outer + 1 && !cone) {
                    set(bp, x, h - 1, z, Part.TOWER_CORBEL, inward(x, z));
                    set(bp, x, h, z, Part.TOWER);
                    set(bp, x, h + 1, z, Part.TOWER);
                    if (Math.floorMod(x + z, 2) == 0) {
                        set(bp, x, h + 2, z, Part.TOWER_TOP);
                    }
                }
                if (cone) {
                    // Rings shrinking by one a layer, from just over the wall to a finial.
                    for (int k = 0; k <= 5; k++) {
                        double rr = outer + 0.9 - k;
                        if (d <= rr && d > rr - 1.2) {
                            set(bp, x, h + 1 + k, z, Part.TOWER_ROOF);
                        }
                    }
                }
            }
        }
        if (cone) {
            set(bp, 0, h + 7, 0, Part.TOWER_TOP);
            set(bp, 0, h + 8, 0, Part.TOWER_TOP);
        }
        door(bp, 4);
        for (int[] c : new int[][] {{2, 2}, {-2, -2}, {2, -2}, {-2, 2}}) {
            set(bp, c[0], 6, c[1], Part.LIGHT);
        }
    }

    // ---- PYRAMID ----

    private static void pyramid(Blueprint bp) {
        int r0 = 6;
        for (int x = -r0; x <= r0; x++) {
            for (int z = -r0; z <= r0; z++) {
                int r = ring(x, z);
                foundation(bp, x, z);
                for (int k = 0; k <= r0; k++) {
                    int rr = r0 - k;
                    int y = k - 1;
                    if (r > rr) {
                        break;
                    }
                    boolean hole = r <= 1 && y <= 2;
                    if (hole && y >= -1) {
                        continue; // the stair comes up here
                    }
                    if (r == rr && rr > 0) {
                        set(bp, x, y, z, Part.TOWER_STAIR, inward(x, z)); // stepped face, climbing inward
                    } else {
                        set(bp, x, y, z, rr == 0 ? Part.TOWER_TOP : Part.TOWER);
                    }
                }
            }
        }
        // The chamber over the stair, three high, and its floor.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int r = ring(x, z);
                if (r == 2) {
                    set(bp, x, -1, z, Part.TOWER_FLOOR);
                }
                if (r >= 1) {
                    for (int y = 0; y <= 2; y++) {
                        set(bp, x, y, z, Part.AIR);
                    }
                }
            }
        }
        // The entrance tunnel from the north face, framed in trim.
        for (int z = -r0 - 1; z <= -3; z++) {
            set(bp, 0, -1, z, Part.TOWER_FLOOR);
            set(bp, 0, 0, z, Part.AIR);
            set(bp, 0, 1, z, Part.AIR);
            set(bp, 0, 2, z, z >= -r0 + 2 ? Part.TOWER_TRIM : Part.AIR);
        }
        for (int y = 0; y <= 2; y++) {
            set(bp, -1, y, -r0 + 1, Part.TOWER_TRIM);
            set(bp, 1, y, -r0 + 1, Part.TOWER_TRIM);
        }
        set(bp, -1, 2, -r0, Part.WALL_TORCH, 0);
        set(bp, 1, 2, -r0, Part.WALL_TORCH, 0);
        set(bp, 2, 2, 2, Part.LIGHT);
        set(bp, -2, 2, -2, Part.LIGHT);
    }

    // ---- TEMPLE ----

    private static void temple(Blueprint bp, Dice dice) {
        int wx = 5;
        int wz = 6;
        for (int x = -wx; x <= wx; x++) {
            for (int z = -wz; z <= wz; z++) {
                foundation(bp, x, z);
                boolean edge = Math.abs(x) == wx || Math.abs(z) == wz;
                if (ring(x, z) >= 2) {
                    set(bp, x, -1, z, edge ? Part.TOWER_STAIR : Part.TOWER_FLOOR,
                            edge ? (Math.abs(z) == wz ? (z > 0 ? 0 : 2) : (x > 0 ? 3 : 1)) : 0);
                }
                boolean column = (Math.abs(x) == wx - 1 && Math.floorMod(z, 2) == 0)
                        || (Math.abs(z) == wz - 1 && Math.floorMod(x, 2) == 0);
                for (int y = 0; y <= 4; y++) {
                    if (ring(x, z) >= 1) { // the centre is the stair's newel
                        set(bp, x, y, z, column ? (y == 4 ? Part.TOWER_TRIM : Part.TOWER_PILLAR) : Part.AIR);
                    }
                }
                // Entablature, then the gabled roof along the long axis.
                set(bp, x, 5, z, edge || Math.abs(x) == wx - 1 || Math.abs(z) == wz - 1 ? Part.TOWER_TRIM : Part.TOWER);
                for (int k = 0; k <= wx; k++) {
                    int y = 6 + k;
                    int ax = Math.abs(x);
                    if (ax == wx - k) {
                        set(bp, x, y, z, Math.abs(z) == wz ? Part.TOWER_TRIM : Part.TOWER_STAIR, x > 0 ? 3 : 1);
                    } else if (ax < wx - k) {
                        set(bp, x, y, z, Math.abs(z) == wz ? Part.TOWER_TRIM : (k == 0 ? Part.TOWER : Part.AIR));
                    }
                }
            }
        }
        for (int[] c : new int[][] {{2, 3}, {-2, -3}, {2, -3}, {-2, 3}}) {
            set(bp, c[0], 4, c[1], Part.LIGHT);
        }
        if (dice.chance(0.5)) {
            set(bp, 0, 4, 0, Part.LIGHT);
        }
    }
}
