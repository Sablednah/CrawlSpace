package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * The planner's only source of randomness: SplitMix64, written out here so
 * that a seed means the same dungeon on every Java version and every machine.
 * {@code java.util.Random} would do that too, but its quality is poor and a
 * library change elsewhere could not alter this one.
 *
 * <p>Use {@link #of(long, long...)} to derive an independent stream for a
 * sub-task (a level, an attempt, a room) rather than threading one stream
 * through everything: then adding a roll in one place does not reshuffle
 * every dungeon generated after it.</p>
 */
public final class Dice {

    private long state;

    public Dice(long seed) {
        this.state = seed;
    }

    /** An independent stream for {@code seed} and a path of salts. */
    public static Dice of(long seed, long... salts) {
        long s = mix(seed);
        for (long salt : salts) {
            s = mix(s ^ mix(salt + 0x632BE59BD9B4E019L));
        }
        return new Dice(s);
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        return mix(state += 0x9E3779B97F4A7C15L);
    }

    /** Uniform in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    /** Uniform in [lo, hi], both inclusive. */
    public int between(int lo, int hi) {
        return lo + nextInt(hi - lo + 1);
    }

    /** An odd number in [lo, hi]; rooms are odd-sized so they have a centre cell. */
    public int oddBetween(int lo, int hi) {
        int v = between(lo, hi);
        return (v % 2 == 0) ? (v + 1 <= hi ? v + 1 : v - 1) : v;
    }

    /** Uniform in [0, 1). */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double range(double lo, double hi) {
        return lo + (hi - lo) * nextDouble();
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    public <T> T pick(List<T> items) {
        return items.get(nextInt(items.size()));
    }

    /** Picks an index by weight; weights of zero are never picked. */
    public int weighted(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double r = nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0 && weights[i] > 0) {
                return i;
            }
        }
        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("no positive weight");
    }
}
