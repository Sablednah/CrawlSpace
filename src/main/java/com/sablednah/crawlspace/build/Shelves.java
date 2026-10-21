package com.sablednah.crawlspace.build;

import java.util.HashSet;
import java.util.Set;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.Dressing;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;
import com.sablednah.crawlspace.plan.Shape;

/**
 * Shelf skirting (Sable, 2026-10-21, from a photo): shelves standing on the
 * floor round the foot of a room's columns, and, more rarely, all along its
 * walls with a column in each corner so the runs end cleanly.
 *
 * <p><b>The last thing laid on a level, on its own dice, and only on floor
 * still empty.</b> So it moves no wall, prop, chest, trap or spawn point: a
 * run simply stops at a barrel, and a dungeon planned before shelves existed
 * differs by its shelves and nothing else. Shelves keep a cell's width clear
 * of every doorway and stair, never stand on a trigger or anything a trigger
 * acts on, and never hide a wall that is anything but wall (a secret door, a
 * lever).</p>
 */
final class Shelves {

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private Shelves() {
    }

    static void place(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        Dressing dr = level.theme.dressing();
        if (dr.pillarShelves() <= 0 && dr.wallShelves() <= 0) {
            return;
        }
        Dice dice = Dice.of(plan.seed(), i, 0x5E1F5L);
        Set<Long> busy = triggered(bp, i);
        for (Room r : level.rooms) {
            // Both rolled for every room, so one knob never changes which rooms the other picks.
            boolean pillars = dice.chance(dr.pillarShelves());
            boolean walls = dice.chance(dr.wallShelves());
            if (!suits(r) || !(pillars || walls)) {
                continue;
            }
            Set<Long> before = reached(bp, plan, i, level, r);
            java.util.List<int[]> laid = new java.util.ArrayList<>();
            if (walls && squared(r) && Math.min(r.w, r.h) >= 7) {
                walls(bp, plan, i, level, r, busy, laid);
            }
            if (pillars) {
                pillars(bp, plan, i, level, r, busy, laid);
            }
            // Anything the room could reach before, it must still reach; otherwise this room goes without.
            if (!laid.isEmpty() && !reached(bp, plan, i, level, r).containsAll(before)) {
                for (int[] c : laid) {
                    bp.set(c[0], c[1], c[2], Part.AIR, 0, i);
                }
            }
        }
    }

    /**
     * What, of the places a player must get to in this room (beside each
     * doorway, its stairs, beside a lever, winch or hoard), can be reached
     * from the first of them, walking open floor.
     */
    private static Set<Long> reached(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r) {
        java.util.List<int[]> must = new java.util.ArrayList<>();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || !level.cell(x, z).isWalkable()) {
                    continue;
                }
                boolean byDoor = false;
                for (int[] d : DIRS) {
                    byDoor |= level.cell(x + d[0], z + d[1]).isDoor();
                }
                Cell c = level.cell(x, z);
                if (byDoor || c == Cell.STAIR_UP || c == Cell.STAIR_DOWN) {
                    must.add(new int[] {x, z});
                }
            }
        }
        for (Trigger t : bp.triggers()) {
            if (t.level() == i && r.contains(t.x(), t.z()) && (t.kind() == Trigger.Kind.LEVER || t.kind() == Trigger.Kind.WINCH
                    || t.kind() == Trigger.Kind.ONEWAY || t.kind() == Trigger.Kind.TREASURE)) {
                for (int[] d : DIRS) {
                    must.add(new int[] {t.x() + d[0], t.z() + d[1]});
                }
            }
        }
        Set<Long> seen = new HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int[] m : must) {
            if (open(bp, plan, i, level, r, m[0], m[1])) {
                seen.add(Blueprint.key(m[0], m[1]));
                queue.add(m);
                break;
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : DIRS) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                if (!seen.contains(Blueprint.key(x, z)) && open(bp, plan, i, level, r, x, z)) {
                    seen.add(Blueprint.key(x, z));
                    queue.add(new int[] {x, z});
                }
            }
        }
        Set<Long> out = new HashSet<>();
        for (int[] m : must) {
            if (seen.contains(Blueprint.key(m[0], m[1]))) {
                out.add(Blueprint.key(m[0], m[1]));
            }
        }
        return out;
    }

    /** A cell of the room a player can stand in: walkable, with nothing solid at foot height. */
    private static boolean open(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r, int x, int z) {
        if (!r.contains(x, z) || !level.cell(x, z).isWalkable()) {
            return false;
        }
        int code = bp.get(x, Blueprinter.floorAt(plan, i, x, z), z);
        if (code == 0) {
            return true;
        }
        return switch (Blueprint.part(code)) {
            case AIR, CARPET, MOSS, RAIL, WATER, DOOR_LOWER, LOCKED_LOWER, SECRET_WALL, LIGHT, BANNER, WALL_TORCH, CHAIN, STEP,
                    STEP_SLAB, LANDING, RUG, PLANT, MUSHROOM, VINE, ROOTS, TABLE_TOP, TRAP_PLATE, TRAP_WIRE, DECOY_PLATE, DECOY_WIRE,
                    PORTCULLIS, PORTCULLIS_GAP, ONEWAY_LOWER, LEVER, WINCH,
                    TELL_PEBBLE, TELL_FROST, TELL_SCORCH, TELL_SCULK, TELL_DEAD -> true;
            default -> false;
        };
    }

    /** Rooms people would furnish; not a puzzle, an objective's set piece or a finale arena. */
    private static boolean suits(Room r) {
        return switch (r.role) {
            case PUZZLE, OBJECTIVE -> false;
            default -> true;
        };
    }

    /** Straight walls to run along: not caves or round rooms. */
    private static boolean squared(Room r) {
        return r.shape != Shape.CAVE && r.shape != Shape.CIRCLE && r.shape != Shape.ROUNDED;
    }

    private static void walls(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r, Set<Long> busy,
            java.util.List<int[]> laid) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!free(bp, plan, i, level, r, x, z, busy)) {
                    continue;
                }
                int walls = 0;
                int along = -1;
                boolean plain = true;
                for (int d = 0; d < 4; d++) {
                    if (level.cell(x + DIRS[d][0], z + DIRS[d][1]) == Cell.WALL) {
                        walls++;
                        along = d;
                        plain &= plainWall(bp, plan, i, x + DIRS[d][0], z + DIRS[d][1], x, z);
                    }
                }
                if (!plain) {
                    continue;
                }
                int f = Blueprinter.floorAt(plan, i, x, z);
                if (walls == 1) {
                    bp.set(x, f, z, Part.SHELF_UNIT, facing((along + 2) % 4), i);
                    laid.add(new int[] {x, f, z});
                } else if (walls == 2 && corner(level, x, z)) {
                    column(bp, level, i, x, z, f, laid);
                }
            }
        }
    }

    /** Two walls at right angles: a corner, not a passage between two walls. */
    private static boolean corner(LevelPlan level, int x, int z) {
        boolean ns = level.cell(x, z - 1) == Cell.WALL || level.cell(x, z + 1) == Cell.WALL;
        boolean ew = level.cell(x - 1, z) == Cell.WALL || level.cell(x + 1, z) == Cell.WALL;
        return ns && ew;
    }

    /** A column floor to ceiling, as the dresser stands them against walls; nothing if anything is in the way. */
    private static void column(Blueprint bp, LevelPlan level, int i, int x, int z, int f, java.util.List<int[]> laid) {
        int h = Blueprinter.clearHeight(level, x, z);
        for (int y = f; y < f + h; y++) {
            int code = bp.get(x, y, z);
            if (code == 0 || Blueprint.part(code) != Part.AIR) {
                return;
            }
        }
        for (int y = f; y < f + h; y++) {
            bp.set(x, y, z, y == f || y == f + h - 1 ? Part.DADO : Part.PILLAR, 0, i);
            laid.add(new int[] {x, y, z});
        }
    }

    private static void pillars(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r, Set<Long> busy,
            java.util.List<int[]> laid) {
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (!column(bp, plan, i, level, x, z)) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    int nx = x + DIRS[d][0];
                    int nz = z + DIRS[d][1];
                    if (free(bp, plan, i, level, r, nx, nz, busy)) {
                        int f = Blueprinter.floorAt(plan, i, nx, nz);
                        bp.set(nx, f, nz, Part.SHELF_UNIT, facing(d), i);
                        laid.add(new int[] {nx, f, nz});
                    }
                }
            }
        }
    }

    /** A column standing in or against the room: its foot and the block above are column. */
    private static boolean column(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, int x, int z) {
        if (level.cell(x, z) == Cell.PILLAR) {
            return true;
        }
        if (level.cell(x, z) != Cell.FLOOR) {
            return false;
        }
        int f = Blueprinter.floorAt(plan, i, x, z);
        Part foot = Blueprint.part(bp.get(x, f, z));
        return bp.get(x, f, z) != 0 && (foot == Part.DADO || foot == Part.PILLAR)
                && bp.get(x, f + 1, z) != 0 && Blueprint.part(bp.get(x, f + 1, z)) == Part.PILLAR;
    }

    /**
     * Room floor with nothing on it, on a plain floor block, with air above,
     * not on or acted on by a trigger, and a cell clear of every doorway,
     * stair, pit and pool, so nothing is walled off.
     */
    private static boolean free(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r, int x, int z, Set<Long> busy) {
        if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR || busy.contains(Blueprint.key(x, z))) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Cell c = level.cell(x + dx, z + dz);
                if (c != Cell.FLOOR && c != Cell.WALL && c != Cell.PILLAR && c != Cell.ROCK) {
                    return false; // a doorway, a stair, a pit, a pool or a corridor
                }
            }
        }
        int f = Blueprinter.floorAt(plan, i, x, z);
        int here = bp.get(x, f, z);
        int above = bp.get(x, f + 1, z);
        int below = bp.get(x, f - 1, z);
        if (here == 0 || Blueprint.part(here) != Part.AIR || above == 0 || Blueprint.part(above) != Part.AIR || below == 0) {
            return false;
        }
        Part floor = Blueprint.part(below);
        return floor == Part.FLOOR || floor == Part.FLOOR_ACCENT || floor == Part.FLOOR_INLAY || floor == Part.MOSS_FLOOR;
    }

    /** The wall behind a shelf at its height and the next: ordinary wall, never a secret door or a lever. */
    private static boolean plainWall(Blueprint bp, DungeonPlan plan, int i, int wx, int wz, int x, int z) {
        int f = Blueprinter.floorAt(plan, i, x, z);
        for (int y = f; y <= f + 1; y++) {
            int code = bp.get(wx, y, wz);
            if (code == 0) {
                return false;
            }
            switch (Blueprint.part(code)) {
                case WALL, WALL_ACCENT, PANEL, DADO, PILASTER, PILLAR -> {
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    /** Every cell a trigger on this level sits on or acts on. */
    private static Set<Long> triggered(Blueprint bp, int i) {
        Set<Long> out = new HashSet<>();
        for (Trigger t : bp.triggers()) {
            if (t.level() != i) {
                continue;
            }
            out.add(Blueprint.key(t.x(), t.z()));
            if (t.targets() != null) {
                for (int[] p : t.targets()) {
                    if (p.length >= 3) {
                        out.add(Blueprint.key(p[0], p[2]));
                    }
                }
            }
        }
        return out;
    }

    /** DIRS index to the blueprint's facing (0 north, 1 east, 2 south, 3 west): the way the shelf's front looks. */
    private static int facing(int d) {
        return d; // DIRS is north, east, south, west already
    }
}
