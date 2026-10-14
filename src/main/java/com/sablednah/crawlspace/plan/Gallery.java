package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * The objective gallery (Sable, 2026-10-14: "a devshowcase that skips the
 * planner and just lays out all the objective rooms with a corridor beside
 * them"). One hand-laid level: a long entry hall from the tower stair, and off
 * it, alternately either side, one objective room for every job of every
 * type, in order (the Fighting Pit's first job, its second, the Firechasm's
 * first...). Adding a job adds a room. Built by the ordinary blueprinter,
 * which sees {@link LevelPlan#gallery} and fills each room with its own job.
 *
 * <p>Like a showcase, a gallery is written into its seed, so it stays one
 * after a restart.</p>
 */
public final class Gallery {

    private static final long MASK = 0xFFFFFFFFL << 32;
    private static final long TAG = 0x6A11E4A1L << 32;
    /** Each objective type's number of jobs, in type order (pit, firechasm, fountain, tomb, idol). */
    public static final int[] JOBS = {2, 2, 2, 2, 2};
    /** An objective room's side, and the spacing between their middles along the hall. */
    private static final int ROOM = 15;
    private static final int PITCH = ROOM + 5;
    /** The hall's width, and how far the rooms stand off it (the corridor between). */
    private static final int HALL = 7;
    private static final int OFF = 5;

    private Gallery() {
    }

    public static long seed(long n) {
        return TAG | (n & ~MASK);
    }

    public static boolean on(long seed) {
        return (seed & MASK) == TAG;
    }

    /** How many rooms: one per job of every type. */
    public static int rooms() {
        int n = 0;
        for (int j : JOBS) {
            n += j;
        }
        return n;
    }

    /** The k-th room's {type, job}. */
    public static int[] job(int k) {
        for (int t = 0; t < JOBS.length; t++) {
            if (k < JOBS[t]) {
                return new int[] {t, k};
            }
            k -= JOBS[t];
        }
        throw new IllegalArgumentException("no room " + k);
    }

    static DungeonPlan plan(long seed) {
        Dice dice = Dice.of(seed, 0, 0x6A11L);
        LevelPlan level = new LevelPlan(0, Theme.CRYPT);
        level.gallery = true;
        int n = rooms();
        int pairs = (n + 1) / 2;
        int length = pairs * PITCH + 6;
        Room hall = rect(0, Role.ENTRY, length, HALL);
        hall.cx = 0;
        hall.cz = 0;
        hall.pinned = true;
        level.rooms.add(hall);
        double first = -(pairs - 1) * PITCH / 2.0;
        for (int k = 0; k < n; k++) {
            Room r = rect(level.rooms.size(), Role.OBJECTIVE, ROOM, ROOM);
            r.open = true;
            int side = k % 2 == 0 ? -1 : 1; // the first job of a type to the north, the second to the south
            r.cx = first + (k / 2) * PITCH;
            r.cz = side * (HALL / 2 + OFF + ROOM / 2 + 1);
            level.rooms.add(r);
            Link l = new Link(hall, r, LinkKind.OPEN, false);
            l.style = CorridorStyle.STRAIGHT;
            l.width = 3;
            level.links.add(l);
        }
        for (Room r : level.rooms) {
            r.snap();
            for (int lx = 0; lx < r.w; lx++) {
                for (int lz = 0; lz < r.h; lz++) {
                    int x = r.minX() + lx;
                    int z = r.minZ() + lz;
                    level.set(x, z, Cell.FLOOR);
                    level.setRegion(x, z, r.id);
                }
            }
        }
        Router router = new Router(level, dice.nextLong());
        for (Link l : List.copyOf(level.links)) {
            if (!router.route(l, dice)) {
                throw new IllegalStateException("the gallery could not route " + l);
            }
        }
        LevelPlanner.features(level, dice);
        LevelPlanner.walls(level);
        return new DungeonPlan(seed, Planner.LEVEL_SPACING, DungeonPlan.MIN_TOP, List.of(level));
    }

    private static Room rect(int id, Role role, int w, int h) {
        boolean[][] mask = new boolean[w][h];
        for (boolean[] col : mask) {
            java.util.Arrays.fill(col, true);
        }
        return new Room(id, role, Shape.RECT, w, h, mask);
    }
}
