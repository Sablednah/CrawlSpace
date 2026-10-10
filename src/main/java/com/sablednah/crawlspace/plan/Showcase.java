package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * The showcase dungeon (Sable, 2026-10-10): every feature at once, for seeing
 * them without hunting. It is an ordinary dungeon, planned and built the
 * ordinary way, so what comes in pairs (a lever and its door, a portcullis and
 * its key's chest) still does. Only the dice that decide whether a feature
 * appears say yes, and the rest take turns: each level its own theme and
 * feeling, the trap kinds in rotation, puzzle rooms alternating maze and ice.
 *
 * <p>Which dungeons are showcases is written into the seed, so a showcase
 * stays one after a restart with nothing else to save: everything about a
 * dungeon is rebuilt from its seed.</p>
 */
public final class Showcase {

    // The top 32 bits: a random seed is one by accident about once in four billion.
    private static final long MASK = 0xFFFFFFFFL << 32;
    private static final long TAG = 0x5C0E5C0EL << 32;

    /** One theme of each, in turn, level by level. */
    public static final List<Theme> THEMES = List.of(Theme.CRYPT, Theme.SUNKEN_HALLS, Theme.OLD_MINES, Theme.CAVERNS, Theme.DEEP_HALLS);

    private Showcase() {
    }

    /** The showcase seed for {@code n}. */
    public static long seed(long n) {
        return TAG | (n & ~MASK);
    }

    public static boolean on(long seed) {
        return (seed & MASK) == TAG;
    }

    /** A feature's roll: always yes in a showcase. */
    public static boolean chance(long seed, Dice dice, double p) {
        return on(seed) || dice.chance(p);
    }

    public static Theme theme(int index) {
        return THEMES.get(index % THEMES.size());
    }

    /** Every feeling but none, in turn. */
    public static Feeling feeling(int index) {
        Feeling[] all = Feeling.values();
        return all[1 + index % (all.length - 1)];
    }

    /** The trap kinds in turn, from wherever this level starts, whatever the depth. */
    public static TrapKind trap(int index, int nth) {
        TrapKind[] all = TrapKind.values();
        return all[(index + nth) % all.length];
    }
}
