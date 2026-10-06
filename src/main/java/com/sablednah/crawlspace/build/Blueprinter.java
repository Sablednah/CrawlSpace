package com.sablednah.crawlspace.build;

import java.util.HashMap;
import java.util.Map;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Link;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;
import com.sablednah.crawlspace.plan.Shape;

/**
 * Turns a {@link DungeonPlan} into a {@link Blueprint}: every block by role.
 *
 * <p>Vertically, level {@code i}'s floor (where a player stands) is at
 * {@code -top - i * spacing}. Each open cell gets a floor block under it, air
 * up to its clear height and a ceiling block on top. Each wall cell is solid
 * from the lowest floor beside it to the highest ceiling beside it. That is
 * enough to seal everything, which the tests check block by block: no air the
 * dungeon makes ever touches the world it was cut into.</p>
 *
 * <p>Spiral stairs, pit shafts and the tower are added after the levels and
 * overwrite what they cross.</p>
 */
public final class Blueprinter {

    /** Blocks of ground kept over every ceiling, so no level breaks the surface. */
    public static final int COVER = 3;
    /** Inside the tower, floor to roof. */
    public static final int TOWER_HEIGHT = 7;
    /** Cells round a spiral stair, clockwise from north, and the way each step faces (0 N, 1 E, 2 S, 3 W). */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final int[] RING_FACING = {1, 2, 2, 3, 3, 0, 0, 1};

    private Blueprinter() {
    }

    public static int floorY(DungeonPlan plan, int level) {
        return -plan.top() - level * plan.levelSpacing();
    }

    /**
     * How far below the tower level 0 has to start so that every ceiling on
     * every level has {@link #COVER} blocks of ground over it. Levels hang a
     * fixed distance under the tower, and the land over them is not flat: a
     * valley beside the tower once left a run of level 1's ceiling showing.
     *
     * @param surface the height of the ground at (x, z), relative to the tower's (0)
     */
    public static int requiredTop(DungeonPlan plan, java.util.function.IntBinaryOperator surface) {
        int top = DungeonPlan.MIN_TOP;
        int lim = LevelPlan.RADIUS;
        for (int i = 0; i < plan.levels().size(); i++) {
            LevelPlan level = plan.levels().get(i);
            for (int x = -lim; x <= lim; x++) {
                for (int z = -lim; z <= lim; z++) {
                    if (level.cell(x, z) == Cell.ROCK) {
                        continue;
                    }
                    // Ceiling (or wall top) relative to a top of zero; walls reach their tallest neighbour's.
                    int ceiling = -i * plan.levelSpacing() + level.height(x, z) + clearHeight(level, x, z) + 2;
                    top = Math.max(top, ceiling + COVER - surface.applyAsInt(x, z));
                }
            }
        }
        return top;
    }

    public static Blueprint blueprint(DungeonPlan plan) {
        int levels = plan.levels().size();
        Blueprint bp = new Blueprint(floorY(plan, levels - 1) - 4, TOWER_HEIGHT + 3);
        for (int i = 0; i < levels; i++) {
            level(bp, plan, i);
        }
        for (int i = 0; i < levels; i++) {
            LevelPlan level = plan.levels().get(i);
            for (int[] s : level.stairsUp) {
                well(bp, plan, i, s);
            }
            for (int[] p : level.pits) {
                shaft(bp, plan, i, p);
            }
        }
        tower(bp, plan);
        return bp;
    }

    /** Air above a cell's floor: taller in halls and lairs, low in corridors. */
    static int clearHeight(LevelPlan level, int x, int z) {
        int reg = level.region(x, z);
        if (reg < 0) {
            return 4;
        }
        Room r = level.room(reg);
        if (r.shape == Shape.HALL || r.role == Role.LAIR) {
            return 6;
        }
        if (r.shape == Shape.CAVE) {
            return 5;
        }
        return 4;
    }

    private static int floorAt(DungeonPlan plan, int i, int x, int z) {
        return floorY(plan, i) + plan.levels().get(i).height(x, z);
    }

    /** The lowest solid block under an open cell. */
    private static int bottomOf(LevelPlan level, int f, int x, int z) {
        return level.cell(x, z) == Cell.POOL ? f - 2 : f - 1;
    }

    private static void level(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        int lim = LevelPlan.RADIUS;
        Map<Long, int[]> doorNormals = new HashMap<>();
        for (Link l : level.links) {
            if (l.doorA != null) {
                doorNormals.put(key(l.doorA[0], l.doorA[1]), l.doorA);
            }
            if (l.doorB != null) {
                doorNormals.put(key(l.doorB[0], l.doorB[1]), l.doorB);
            }
        }
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                Cell c = level.cell(x, z);
                if (c == Cell.ROCK) {
                    continue;
                }
                int f = floorAt(plan, i, x, z);
                if (c == Cell.WALL) {
                    int lo = Integer.MAX_VALUE;
                    int hi = Integer.MIN_VALUE;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            Cell n = level.cell(x + dx, z + dz);
                            if (!n.isOpen()) {
                                continue;
                            }
                            int nf = floorAt(plan, i, x + dx, z + dz);
                            lo = Math.min(lo, bottomOf(level, nf, x + dx, z + dz));
                            hi = Math.max(hi, nf + clearHeight(level, x + dx, z + dz));
                        }
                    }
                    bp.fill(x, z, lo, hi, Part.WALL, i);
                    continue;
                }
                int h = clearHeight(level, x, z);
                switch (c) {
                    case FLOOR, CORRIDOR, PILLAR -> {
                        bp.set(x, f - 1, z, level.region(x, z) >= 0 ? Part.FLOOR : Part.CORRIDOR_FLOOR, 0, i);
                        bp.fill(x, z, f, f + h - 1, c == Cell.PILLAR ? Part.PILLAR : Part.AIR, i);
                        bp.set(x, f + h, z, Part.CEILING, 0, i);
                    }
                    case POOL -> {
                        bp.set(x, f - 2, z, Part.FLOOR, 0, i);
                        bp.set(x, f - 1, z, Part.WATER, 0, i);
                        bp.fill(x, z, f, f + h - 1, Part.AIR, i);
                        bp.set(x, f + h, z, Part.CEILING, 0, i);
                    }
                    case STAIR_UP -> bp.set(x, f - 1, z, Part.FLOOR, 0, i); // the well does the rest
                    case STAIR_DOWN, PIT -> { // no floor: the well or shaft below opens here
                        bp.fill(x, z, f, f + h - 1, Part.AIR, i);
                        bp.set(x, f + h, z, Part.CEILING, 0, i);
                    }
                    case DOOR, ARCH, DOOR_LOCKED, DOOR_SECRET -> {
                        int[] d = doorNormals.get(key(x, z));
                        int facing = d == null ? 0 : facingOf(d[2], d[3]);
                        int top = h;
                        for (int k = 0; k < 4; k++) {
                            int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                            int nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                            if (level.cell(nx, nz).isOpen()) {
                                top = Math.max(top, floorAt(plan, i, nx, nz) - f + clearHeight(level, nx, nz));
                            }
                        }
                        bp.set(x, f - 1, z, Part.CORRIDOR_FLOOR, 0, i);
                        int opening = 2;
                        switch (c) {
                            case DOOR -> {
                                bp.set(x, f, z, Part.DOOR_LOWER, facing, i);
                                bp.set(x, f + 1, z, Part.DOOR_UPPER, facing, i);
                            }
                            case DOOR_LOCKED -> {
                                bp.set(x, f, z, Part.LOCKED_LOWER, facing, i);
                                bp.set(x, f + 1, z, Part.LOCKED_UPPER, facing, i);
                            }
                            case DOOR_SECRET -> bp.fill(x, z, f, f + 1, Part.SECRET_WALL, i);
                            default -> {
                                opening = 3;
                                bp.fill(x, z, f, f + 2, Part.AIR, i);
                            }
                        }
                        bp.fill(x, z, f + opening, f + top, Part.WALL, i);
                    }
                    default -> {
                    }
                }
            }
        }
        lights(bp, plan, i);
    }

    /** Lanterns: round the stairs always, in other rooms less often the deeper it gets. */
    private static void lights(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        double chance = Math.max(0.15, 0.7 - 0.07 * i);
        for (Room r : level.rooms) {
            int cx = r.centerX();
            int cz = r.centerZ();
            if (r.role == Role.ENTRY || r.role == Role.EXIT) {
                for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                    hang(bp, plan, i, cx + d[0], cz + d[1]);
                }
            } else if (unit(plan.seed(), i, r.id) < chance) {
                hang(bp, plan, i, cx, cz);
            }
        }
    }

    private static void hang(Blueprint bp, DungeonPlan plan, int i, int x, int z) {
        LevelPlan level = plan.levels().get(i);
        if (level.cell(x, z) != Cell.FLOOR) {
            return;
        }
        int f = floorAt(plan, i, x, z);
        bp.set(x, f + clearHeight(level, x, z) - 1, z, Part.LIGHT, 0, i);
    }

    /** A spiral stair from level {@code i}'s stair up to the floor above it (or the tower). */
    private static void well(Blueprint bp, DungeonPlan plan, int i, int[] s) {
        LevelPlan lower = plan.levels().get(i);
        int bottom = floorAt(plan, i, s[0], s[1]);
        int top;
        int upperHeight;
        int upperLevel;
        if (i == 0) {
            top = 0;
            upperHeight = TOWER_HEIGHT;
            upperLevel = 0;
        } else {
            LevelPlan upper = plan.levels().get(i - 1);
            top = floorAt(plan, i - 1, s[0], s[1]);
            upperHeight = clearHeight(upper, s[0], s[1]);
            upperLevel = i - 1;
        }
        int lowerHeight = clearHeight(lower, s[0], s[1]);
        // The shaft's lining, between the two levels.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    bp.fill(s[0] + dx, s[1] + dz, bottom + lowerHeight, top - 2, Part.WALL, i);
                }
            }
        }
        for (int[] r : RING) {
            bp.fill(s[0] + r[0], s[1] + r[1], bottom, top - 1, Part.AIR, i);
        }
        for (int n = 0; n < top - bottom; n++) {
            int k = n % RING.length;
            bp.set(s[0] + RING[k][0], bottom + n, s[1] + RING[k][1], Part.STEP, RING_FACING[k], upperLevel);
        }
        bp.fill(s[0], s[1], bottom - 1, top + upperHeight - 1, Part.NEWEL, upperLevel);
    }

    /** A pit from level {@code i} down into the pool on the level below. */
    private static void shaft(Blueprint bp, DungeonPlan plan, int i, int[] p) {
        LevelPlan lower = plan.levels().get(i + 1);
        int bottom = floorAt(plan, i + 1, p[0], p[1]) + clearHeight(lower, p[0], p[1]);
        int top = floorAt(plan, i, p[0], p[1]) - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                bp.fill(p[0] + dx, p[1] + dz, bottom, rim ? top - 1 : top, rim ? Part.WALL : Part.AIR, i);
            }
        }
    }

    /** A squat tower over level 0's stair: 7 by 7, a door to the north, crenellated. */
    private static void tower(Blueprint bp, DungeonPlan plan) {
        int[] s = plan.levels().get(0).stairsUp.get(0);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = s[0] + dx;
                int z = s[1] + dz;
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 3) {
                    bp.fill(x, z, -4, TOWER_HEIGHT, Part.TOWER, 0);
                    if ((dx + dz) % 2 == 0) {
                        bp.set(x, TOWER_HEIGHT + 1, z, Part.TOWER_TOP, 0, 0);
                    }
                } else {
                    if (ring == 2) {
                        bp.set(x, -1, z, Part.TOWER_FLOOR, 0, 0);
                    }
                    if (ring > 0) { // the centre is the stair's newel, which runs up to the roof
                        bp.fill(x, z, 0, TOWER_HEIGHT - 1, Part.AIR, 0);
                    }
                    bp.set(x, TOWER_HEIGHT, z, Part.TOWER, 0, 0);
                }
            }
        }
        bp.set(s[0], 0, s[1] - 3, Part.TOWER_DOOR_LOWER, 0, 0);
        bp.set(s[0], 1, s[1] - 3, Part.TOWER_DOOR_UPPER, 0, 0);
        for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            bp.set(s[0] + d[0], TOWER_HEIGHT - 1, s[1] + d[1], Part.LIGHT, 0, 0);
        }
    }

    private static int facingOf(int nx, int nz) {
        if (nz < 0) {
            return 0;
        }
        if (nx > 0) {
            return 1;
        }
        if (nz > 0) {
            return 2;
        }
        return 3;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** A stable fraction in [0, 1) for a seed and a path, for choices the builder makes. */
    private static double unit(long seed, long a, long b) {
        return (com.sablednah.crawlspace.plan.Dice.of(seed, a, b, 0x11647L).nextLong() >>> 11) * 0x1.0p-53;
    }
}
