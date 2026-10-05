package com.sablednah.crawlspace.build;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every block a dungeon sets, by role, relative to its origin: (0, 0, 0) is the
 * ground at the foot of the entrance tower's door, and y = 0 is where a player
 * stands there. Positions nobody wrote stay as the world has them.
 *
 * <p>Each entry packs the {@link Part}, a facing (0 = north, 1 = east,
 * 2 = south, 3 = west) and the level whose theme dresses it.</p>
 */
public final class Blueprint {

    public final int minY;
    public final int maxY;
    private final Map<Long, int[]> columns = new HashMap<>();

    public Blueprint(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    public static int code(Part part, int facing, int level) {
        return ((level + 1) << 10) | (part.ordinal() << 2) | (facing & 3);
    }

    public static Part part(int code) {
        return Part.values()[(code >> 2) & 0xff];
    }

    public static int facing(int code) {
        return code & 3;
    }

    public static int level(int code) {
        return (code >> 10) - 1;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public void set(int x, int y, int z, Part part, int facing, int level) {
        if (y < minY || y > maxY) {
            throw new IllegalArgumentException("y " + y + " outside " + minY + ".." + maxY);
        }
        columns.computeIfAbsent(key(x, z), k -> new int[maxY - minY + 1])[y - minY] = code(part, facing, level);
    }

    public void fill(int x, int z, int y0, int y1, Part part, int level) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, z, part, 0, level);
        }
    }

    /** The code at a position, or 0 where the blueprint leaves the world alone. */
    public int get(int x, int y, int z) {
        if (y < minY || y > maxY) {
            return 0;
        }
        int[] col = columns.get(key(x, z));
        return col == null ? 0 : col[y - minY];
    }

    public int columnCount() {
        return columns.size();
    }

    /** Visits every column as {x, z, codes}, codes indexed from {@link #minY}. */
    public void forEachColumn(Consumer<Object[]> visitor) {
        for (Map.Entry<Long, int[]> e : columns.entrySet()) {
            long k = e.getKey();
            visitor.accept(new Object[] {(int) (k >> 32), (int) k, e.getValue()});
        }
    }

    /** Positions set, by any part including air. */
    public long blockCount() {
        long n = 0;
        for (int[] col : columns.values()) {
            for (int c : col) {
                if (c != 0) {
                    n++;
                }
            }
        }
        return n;
    }
}
