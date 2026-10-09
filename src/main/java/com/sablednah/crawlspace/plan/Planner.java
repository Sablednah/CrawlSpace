package com.sablednah.crawlspace.plan;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a whole dungeon from a seed: levels top to bottom, each starting
 * where the stair from the one above comes down, then extra ways down (pits
 * and second stairs) wherever a room on one level sits over a room on the
 * next.
 *
 * <p>Pure Java with no Minecraft in it, and deterministic: the same seed and
 * depth give the same dungeon on any machine.</p>
 */
public final class Planner {

    /** Floor to floor, in blocks. */
    public static final int LEVEL_SPACING = 12;

    private Planner() {
    }

    public static DungeonPlan plan(long seed, int levels) {
        if (levels < 1) {
            throw new IllegalArgumentException("a dungeon needs at least one level");
        }
        List<LevelPlan> out = new ArrayList<>();
        int[] arrival = {0, 0};
        for (int i = 0; i < levels; i++) {
            boolean bottom = i == levels - 1;
            LevelPlan level = LevelPlanner.plan(seed, i, arrival, Theme.forDepth(i), bottom);
            out.add(level);
            if (i > 0) {
                extraRoutes(out.get(i - 1), level, Dice.of(seed, i, 0xD0D0L));
            }
            if (!bottom) {
                arrival = level.stairsDown.get(0);
            }
        }
        // Puzzle rooms last, from their own dice, once pits and second stairs are in: so adding them
        // moved no wall, room or role anywhere else.
        for (int i = 0; i < out.size(); i++) {
            puzzleRoom(out.get(i), Dice.of(seed, i, 0x9022EL));
        }
        return new DungeonPlan(seed, LEVEL_SPACING, DungeonPlan.MIN_TOP, out);
    }

    /** How often a level gets a puzzle room, where it has a room that suits one. */
    static final double PUZZLE_CHANCE = 0.45;

    /**
     * Turns one plain rectangular room, all open floor, 7 to 17 cells a side,
     * into a puzzle room. Its doorways stay where they are: the maze is built
     * to meet them.
     */
    static void puzzleRoom(LevelPlan level, Dice dice) {
        if (!dice.chance(PUZZLE_CHANCE)) {
            return;
        }
        List<Room> candidates = new ArrayList<>();
        for (Room r : level.rooms) {
            if (r.role != Role.ROOM || r.shape != Shape.RECT || Math.min(r.w, r.h) < 7 || Math.max(r.w, r.h) > 17) {
                continue;
            }
            boolean onewayLever = false; // a one-way door's lever stands just inside its room: not on a maze's void
            for (Link l : level.links) {
                onewayLever |= l.kind == LinkKind.ONEWAY && l.a == r;
            }
            if (onewayLever) {
                continue;
            }
            boolean open = true;
            for (int x = r.minX(); x <= r.maxX() && open; x++) {
                for (int z = r.minZ(); z <= r.maxZ() && open; z++) {
                    open = !r.contains(x, z) || level.cell(x, z) == Cell.FLOOR;
                }
            }
            if (open && level.cell(r.centerX(), r.centerZ()) == Cell.FLOOR) {
                candidates.add(r);
            }
        }
        if (!candidates.isEmpty()) {
            candidates.get(dice.nextInt(candidates.size())).role = Role.PUZZLE;
        }
    }

    /**
     * Adds a pit, and sometimes a second stair, from {@code upper} to
     * {@code lower} where a room on one overlaps a room on the other. Each is
     * kept only if both levels still pass {@link PlanCheck}.
     */
    static void extraRoutes(LevelPlan upper, LevelPlan lower, Dice dice) {
        if (dice.chance(0.55 + 0.05 * upper.index)) {
            tryRoute(upper, lower, dice, false);
        }
        if (dice.chance(0.3)) {
            tryRoute(upper, lower, dice, true);
        }
    }

    private static boolean tryRoute(LevelPlan upper, LevelPlan lower, Dice dice, boolean stair) {
        List<int[]> spots = new ArrayList<>();
        for (Room r : upper.rooms) {
            if (r.role == Role.ENTRY || r.role == Role.EXIT || r.role == Role.SECRET || r.role == Role.KEY) {
                continue;
            }
            for (int x = r.minX() + 2; x <= r.maxX() - 2; x++) {
                for (int z = r.minZ() + 2; z <= r.maxZ() - 2; z++) {
                    if (clearFloor(upper, x, z, 2) && landing(lower, x, z)) {
                        spots.add(new int[] {x, z});
                    }
                }
            }
        }
        for (int tries = 0; tries < 6 && !spots.isEmpty(); tries++) {
            int[] s = spots.remove(dice.nextInt(spots.size()));
            Cell[] upperWas = take(upper, s);
            Cell[] lowerWas = take(lower, s);
            fill(upper, s, stair ? Cell.STAIR_DOWN : Cell.PIT);
            fill(lower, s, stair ? Cell.STAIR_UP : Cell.POOL);
            if (stair) {
                upper.stairsDown.add(s);
                lower.stairsUp.add(s);
            } else {
                upper.pits.add(s);
            }
            if (PlanCheck.level(upper).isEmpty() && PlanCheck.level(lower).isEmpty()) {
                return true;
            }
            put(upper, s, upperWas);
            put(lower, s, lowerWas);
            if (stair) {
                upper.stairsDown.remove(s);
                lower.stairsUp.remove(s);
            } else {
                upper.pits.remove(s);
            }
        }
        return false;
    }

    /** Somewhere to come down: the middle of an ordinary room, clear floor all round. */
    private static boolean landing(LevelPlan lower, int x, int z) {
        int reg = lower.region(x, z);
        if (reg < 0) {
            return false;
        }
        Role role = lower.room(reg).role;
        if (role == Role.ENTRY || role == Role.EXIT || role == Role.SECRET) {
            return false;
        }
        return clearFloor(lower, x, z, 2);
    }

    private static boolean clearFloor(LevelPlan level, int x, int z, int r) {
        int reg = level.region(x, z);
        if (reg < 0) {
            return false;
        }
        int y = level.height(x, z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (level.cell(x + dx, z + dz) != Cell.FLOOR || level.region(x + dx, z + dz) != reg
                        || level.height(x + dx, z + dz) != y) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Cell[] take(LevelPlan level, int[] s) {
        Cell[] out = new Cell[9];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out[i++] = level.cell(s[0] + dx, s[1] + dz);
            }
        }
        return out;
    }

    private static void put(LevelPlan level, int[] s, Cell[] cells) {
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.set(s[0] + dx, s[1] + dz, cells[i++]);
            }
        }
    }

    private static void fill(LevelPlan level, int[] s, Cell c) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.set(s[0] + dx, s[1] + dz, c);
            }
        }
    }
}
