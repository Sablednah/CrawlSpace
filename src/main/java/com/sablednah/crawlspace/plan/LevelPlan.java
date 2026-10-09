package com.sablednah.crawlspace.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * One planned level: rooms, links and the finished cell map. Coordinates are
 * dungeon coordinates; the arrays are offset by {@link #RADIUS} so that (0, 0),
 * the entrance stair, is the middle cell.
 *
 * <p>Every level shares the same square, which is what lets a stair down on one
 * line up with the stair up on the next. Its size is set by vanilla: a
 * structure's pieces have to stay within reach of the chunk it started in, and
 * {@code RADIUS} keeps a whole dungeon inside that.</p>
 */
public final class LevelPlan {

    public static final int RADIUS = 120;
    public static final int SIZE = RADIUS * 2 + 1;

    /** Region value for a corridor cell; room cells hold the room's id. */
    public static final int CORRIDOR = -1;
    public static final int NONE = -2;

    public final int index;
    public final Theme theme;
    public final List<Room> rooms = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();

    final Cell[][] cells = new Cell[SIZE][SIZE];
    final int[][] height = new int[SIZE][SIZE];
    final int[][] region = new int[SIZE][SIZE];

    /** Centres of every stair arriving from above; the first is the main one. */
    public final List<int[]> stairsUp = new ArrayList<>();
    /** Centres of every stair down; the first is the main one. */
    public final List<int[]> stairsDown = new ArrayList<>();
    /** Centres of every pit down. */
    public final List<int[]> pits = new ArrayList<>();
    /** Hidden trap tiles, as {x, z, TrapKind ordinal}. */
    public final List<int[]> traps = new ArrayList<>();
    /** Its mood: what it is announced as, and what that changed. */
    public Feeling feeling = Feeling.NONE;
    /** How many tries the planner needed. 1 is the healthy figure. */
    public int attempts;

    public LevelPlan(int index, Theme theme) {
        this.index = index;
        this.theme = theme;
        for (int i = 0; i < SIZE; i++) {
            java.util.Arrays.fill(cells[i], Cell.ROCK);
            java.util.Arrays.fill(region[i], NONE);
        }
    }

    public static boolean inBounds(int x, int z) {
        return x >= -RADIUS && z >= -RADIUS && x <= RADIUS && z <= RADIUS;
    }

    public Cell cell(int x, int z) {
        return inBounds(x, z) ? cells[x + RADIUS][z + RADIUS] : Cell.ROCK;
    }

    public void set(int x, int z, Cell c) {
        cells[x + RADIUS][z + RADIUS] = c;
    }

    /** Floor height relative to the level's floor, in blocks. */
    public int height(int x, int z) {
        return inBounds(x, z) ? height[x + RADIUS][z + RADIUS] : 0;
    }

    public void setHeight(int x, int z, int y) {
        height[x + RADIUS][z + RADIUS] = y;
    }

    /** The room id at (x, z), or {@link #CORRIDOR}, or {@link #NONE}. */
    public int region(int x, int z) {
        return inBounds(x, z) ? region[x + RADIUS][z + RADIUS] : NONE;
    }

    public void setRegion(int x, int z, int r) {
        region[x + RADIUS][z + RADIUS] = r;
    }

    public Room room(int id) {
        for (Room r : rooms) {
            if (r.id == id) {
                return r;
            }
        }
        return null;
    }

    public Room roomWith(Role role) {
        for (Room r : rooms) {
            if (r.role == role) {
                return r;
            }
        }
        return null;
    }
}
