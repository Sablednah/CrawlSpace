package com.sablednah.crawlspace.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a plan for the things that make a dungeon broken rather than merely
 * ugly. The planner runs it on every attempt, so a plan that reaches the
 * builder has passed; the tests run it again over thousands of seeds, and
 * against deliberately broken plans to prove each check can fail.
 */
public final class PlanCheck {

    private static final int[][] FOUR = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PlanCheck() {
    }

    /** Problems with one level on its own. Empty means sound. */
    public static List<String> level(LevelPlan level) {
        List<String> out = new ArrayList<>();
        int lim = LevelPlan.RADIUS;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                Cell c = level.cell(x, z);
                if (!c.isOpen()) {
                    continue;
                }
                if (Math.abs(x) >= lim || Math.abs(z) >= lim) {
                    out.add("open cell on the edge at " + x + "," + z);
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (level.cell(x + dx, z + dz) == Cell.ROCK) {
                            out.add("open cell " + x + "," + z + " has no wall");
                        }
                    }
                }
                int reg = level.region(x, z);
                if (reg < 0) {
                    continue;
                }
                for (int[] d : FOUR) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    Cell n = level.cell(nx, nz);
                    int nreg = level.region(nx, nz);
                    if (!n.isOpen() || nreg == reg) {
                        continue;
                    }
                    if (nreg >= 0) {
                        out.add("rooms " + reg + " and " + nreg + " touch at " + x + "," + z);
                    } else if (!n.isDoor()) {
                        out.add("corridor breaks into room " + reg + " at " + nx + "," + nz);
                    }
                }
            }
        }
        if (out.size() > 20) {
            return out.subList(0, 20);
        }

        for (int[] s : level.stairsUp) {
            if (!square(level, s, Cell.STAIR_UP)) {
                out.add("stair up at " + s[0] + "," + s[1] + " is not a 3x3 well");
            }
        }
        for (int[] s : level.stairsDown) {
            if (!square(level, s, Cell.STAIR_DOWN)) {
                out.add("stair down at " + s[0] + "," + s[1] + " is not a 3x3 well");
            }
        }
        if (level.stairsUp.isEmpty()) {
            out.add("no stair up");
            return out;
        }

        int step = maxStep(level);
        if (step > 1) {
            out.add("a walkable step of " + step + " blocks");
        }
        // No step beside a doorway: the stair it would need cannot be climbed next to a door, either way.
        for (int x = -lim + 1; x < lim; x++) {
            for (int z = -lim + 1; z < lim; z++) {
                if (!level.cell(x, z).isDoor()) {
                    continue;
                }
                for (int[] d : FOUR) {
                    Cell n = level.cell(x + d[0], z + d[1]);
                    if (n == Cell.CORRIDOR && level.height(x + d[0], z + d[1]) != level.height(x, z)) {
                        out.add("a step beside the doorway at " + x + "," + z);
                    }
                }
            }
        }

        int[] start = level.stairsUp.get(0);
        boolean[][] all = reach(level, start, true);
        for (Room r : level.rooms) {
            if (!reachedRoom(all, r)) {
                out.add(r + " cannot be reached");
            }
        }
        for (int[] s : level.stairsDown) {
            if (!all[s[0] + lim][s[1] + lim]) {
                out.add("stair down at " + s[0] + "," + s[1] + " cannot be reached");
            }
        }
        for (int[] p : level.pits) {
            boolean edge = false;
            for (int dx = -2; dx <= 2 && !edge; dx++) {
                for (int dz = -2; dz <= 2 && !edge; dz++) {
                    int x = p[0] + dx;
                    int z = p[1] + dz;
                    edge = LevelPlan.inBounds(x, z) && all[x + lim][z + lim];
                }
            }
            if (!edge) {
                out.add("pit at " + p[0] + "," + p[1] + " cannot be reached");
            }
        }
        // The locked door is a shortcut: the exit and the lever must both be reachable without it.
        boolean[][] unlocked = reach(level, start, false);
        for (int[] s : level.stairsDown) {
            if (!unlocked[s[0] + lim][s[1] + lim]) {
                out.add("stair down at " + s[0] + "," + s[1] + " needs the locked door");
            }
        }
        Room key = level.roomWith(Role.KEY);
        if (key != null && !reachedRoom(unlocked, key)) {
            out.add("the lever room is behind its own lock");
        }
        return out;
    }

    /** Problems between levels: every way down has to land somewhere. */
    public static List<String> dungeon(DungeonPlan plan) {
        List<String> out = new ArrayList<>();
        List<LevelPlan> levels = plan.levels();
        for (int i = 0; i < levels.size(); i++) {
            LevelPlan level = levels.get(i);
            for (String p : level(level)) {
                out.add("level " + i + ": " + p);
            }
            if (i == 0 && (level.stairsUp.isEmpty() || level.stairsUp.get(0)[0] != 0 || level.stairsUp.get(0)[1] != 0)) {
                out.add("level 0's stair up is not under the tower at 0,0");
            }
            if (i + 1 >= levels.size()) {
                continue;
            }
            LevelPlan below = levels.get(i + 1);
            if (level.stairsDown.isEmpty()) {
                out.add("level " + i + " has no stair down");
            }
            for (int[] s : level.stairsDown) {
                if (!square(below, s, Cell.STAIR_UP)) {
                    out.add("level " + i + "'s stair down at " + s[0] + "," + s[1] + " lands on no stair up");
                }
            }
            for (int[] p : level.pits) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (level.cell(p[0] + dx, p[1] + dz) != Cell.PIT) {
                            out.add("level " + i + "'s pit at " + p[0] + "," + p[1] + " is not 3x3");
                        }
                        if (below.cell(p[0] + dx, p[1] + dz) != Cell.POOL) {
                            out.add("level " + i + "'s pit at " + p[0] + "," + p[1] + " has no pool under it");
                        }
                    }
                }
            }
        }
        LevelPlan bottom = levels.get(levels.size() - 1);
        if (!bottom.stairsDown.isEmpty() || !bottom.pits.isEmpty()) {
            out.add("the bottom level has a way further down");
        }
        return out;
    }

    /** The tallest step between walkable neighbours, in blocks. */
    static int maxStep(LevelPlan level) {
        int lim = LevelPlan.RADIUS;
        int worst = 0;
        for (int x = -lim; x < lim; x++) {
            for (int z = -lim; z < lim; z++) {
                if (!level.cell(x, z).isWalkable()) {
                    continue;
                }
                int y = level.height(x, z);
                if (level.cell(x + 1, z).isWalkable()) {
                    worst = Math.max(worst, Math.abs(level.height(x + 1, z) - y));
                }
                if (level.cell(x, z + 1).isWalkable()) {
                    worst = Math.max(worst, Math.abs(level.height(x, z + 1) - y));
                }
            }
        }
        return worst;
    }

    private static boolean square(LevelPlan level, int[] c, Cell want) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.cell(c[0] + dx, c[1] + dz) != want) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean reachedRoom(boolean[][] reached, Room r) {
        int lim = LevelPlan.RADIUS;
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (r.contains(x, z) && LevelPlan.inBounds(x, z) && reached[x + lim][z + lim]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Walkable cells reachable from {@code start}, stepping at most one block. */
    static boolean[][] reach(LevelPlan level, int[] start, boolean throughLocks) {
        int lim = LevelPlan.RADIUS;
        boolean[][] seen = new boolean[LevelPlan.SIZE][LevelPlan.SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        if (level.cell(start[0], start[1]).isWalkable()) {
            seen[start[0] + lim][start[1] + lim] = true;
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int y = level.height(c[0], c[1]);
            for (int[] d : FOUR) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                if (!LevelPlan.inBounds(x, z) || seen[x + lim][z + lim]) {
                    continue;
                }
                Cell n = level.cell(x, z);
                if (!n.isWalkable() || (!throughLocks && n == Cell.DOOR_LOCKED)) {
                    continue;
                }
                if (Math.abs(level.height(x, z) - y) > 1) {
                    continue;
                }
                seen[x + lim][z + lim] = true;
                queue.add(new int[] {x, z});
            }
        }
        return seen;
    }
}
