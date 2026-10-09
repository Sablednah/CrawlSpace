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
 *
 * <p>Worldgen keeps a few of these in memory, so {@link #compact()} trims each
 * column to the heights it actually uses once building is done.</p>
 */
public final class Blueprint {

    /** One column: codes from {@code y0} upwards, 0 where the world is left alone. */
    public record Column(int x, int z, int y0, int[] codes) {
    }

    public final int minY;
    public final int maxY;
    private final Map<Long, Column> columns = new HashMap<>();
    private final Map<Long, Trigger> triggers = new HashMap<>();
    private boolean compact;
    /** Per level, the cells of a finale arena's pit: no trigger, decoy or prop belongs there. */
    private final Map<Integer, java.util.Set<Long>> arenas = new HashMap<>();

    public void markArena(int level, java.util.Set<Long> cells) {
        arenas.put(level, cells);
    }

    /** Whether (x, z) on {@code level} is in a finale arena's pit; keys are (x << 32) ^ z. */
    public boolean inArena(int level, int x, int z) {
        java.util.Set<Long> a = arenas.get(level);
        return a != null && a.contains(((long) x << 32) ^ (z & 0xffffffffL));
    }

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
        if (compact) {
            throw new IllegalStateException("blueprint already compacted");
        }
        if (y < minY || y > maxY) {
            throw new IllegalArgumentException("y " + y + " outside " + minY + ".." + maxY);
        }
        columns.computeIfAbsent(key(x, z), k -> new Column(x, z, minY, new int[maxY - minY + 1]))
                .codes()[y - minY] = code(part, facing, level);
    }

    public void fill(int x, int z, int y0, int y1, Part part, int level) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, z, part, 0, level);
        }
    }

    /** Trims every column to its lowest and highest set position. */
    public Blueprint compact() {
        if (compact) {
            return this;
        }
        for (Map.Entry<Long, Column> e : columns.entrySet()) {
            Column c = e.getValue();
            int lo = 0;
            int hi = c.codes().length - 1;
            while (lo < hi && c.codes()[lo] == 0) {
                lo++;
            }
            while (hi > lo && c.codes()[hi] == 0) {
                hi--;
            }
            e.setValue(new Column(c.x(), c.z(), c.y0() + lo, java.util.Arrays.copyOfRange(c.codes(), lo, hi + 1)));
        }
        compact = true;
        return this;
    }

    /** The code at a position, or 0 where the blueprint leaves the world alone. */
    public int get(int x, int y, int z) {
        Column c = columns.get(key(x, z));
        if (c == null) {
            return 0;
        }
        int i = y - c.y0();
        return i < 0 || i >= c.codes().length ? 0 : c.codes()[i];
    }

    public Column column(int x, int z) {
        return columns.get(key(x, z));
    }

    public int columnCount() {
        return columns.size();
    }

    public void forEachColumn(Consumer<Column> visitor) {
        columns.values().forEach(visitor);
    }

    public void addTrigger(Trigger t) {
        triggers.put(Trigger.key(t.x(), t.y(), t.z()), t);
    }

    /** The trigger at a blueprint position, or null. */
    public Trigger triggerAt(int x, int y, int z) {
        return triggers.get(Trigger.key(x, y, z));
    }

    public java.util.Collection<Trigger> triggers() {
        return triggers.values();
    }

    /** Positions set, by any part including air. */
    public long blockCount() {
        long n = 0;
        for (Column c : columns.values()) {
            for (int code : c.codes()) {
                if (code != 0) {
                    n++;
                }
            }
        }
        return n;
    }
}
