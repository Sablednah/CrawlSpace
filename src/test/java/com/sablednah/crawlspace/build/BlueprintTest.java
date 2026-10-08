package com.sablednah.crawlspace.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Planner;

class BlueprintTest {

    private static final int[][] SIX = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Parts a player, water or a mob could pass through. */
    private static boolean open(Part p) {
        return switch (p) {
            case AIR, WATER, DOOR_LOWER, DOOR_UPPER, LOCKED_LOWER, LOCKED_UPPER, TOWER_DOOR_LOWER, TOWER_DOOR_UPPER, STEP, LIGHT, LEVER -> true;
            default -> false;
        };
    }

    /**
     * Every open block the dungeon makes is bounded by blocks the dungeon also
     * sets: nothing opens on to the world it is cut into, so no cave, aquifer or
     * lava lake can get in. The only exceptions are above ground, round the tower.
     */
    static List<String> leaks(Blueprint bp) {
        List<String> out = new ArrayList<>();
        bp.forEachColumn(col -> {
            int x = col.x();
            int z = col.z();
            int[] codes = col.codes();
            for (int i = 0; i < codes.length; i++) {
                int y = col.y0() + i;
                if (codes[i] == 0 || !open(Blueprint.part(codes[i])) || y >= 0) {
                    continue;
                }
                for (int[] d : SIX) {
                    if (bp.get(x + d[0], y + d[1], z + d[2]) == 0 && out.size() < 10) {
                        out.add(Blueprint.part(codes[i]) + " at " + x + "," + y + "," + z + " opens on to the world");
                    }
                }
            }
        });
        return out;
    }

    @Test
    void dungeonsAreSealed() {
        for (long seed = 0; seed < 40; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            List<String> leaks = leaks(bp.compact());
            assertTrue(leaks.isEmpty(), "seed " + seed + ": " + leaks);
        }
    }

    /** The check above has to be able to fail: knock a block out of a wall and watch it notice. */
    @Test
    void noticesALeak() {
        DungeonPlan plan = Planner.plan(3L, 3);
        Blueprint bp = Blueprinter.blueprint(plan);
        assertTrue(leaks(bp).isEmpty());
        // Find a corridor air block and open the column beside it on to the world.
        int[] found = new int[3];
        boolean[] done = {false};
        bp.forEachColumn(col -> {
            if (done[0]) {
                return;
            }
            int x = col.x();
            int z = col.z();
            int[] codes = col.codes();
            for (int i = 0; i < codes.length; i++) {
                int y = col.y0() + i;
                if (y < -20 && codes[i] != 0 && Blueprint.part(codes[i]) == Part.AIR
                        && bp.get(x + 1, y, z) != 0 && Blueprint.part(bp.get(x + 1, y, z)) == Part.WALL
                        && bp.get(x + 2, y, z) == 0) {
                    found[0] = x + 1;
                    found[1] = y;
                    found[2] = z;
                    done[0] = true;
                    return;
                }
            }
        });
        assertTrue(done[0]);
        bp.set(found[0], found[1], found[2], Part.AIR, 0, 0);
        assertFalse(leaks(bp).isEmpty());
    }

    /** Cells round a well, clockwise from north: the order a climber walks them. */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /**
     * Walks every spiral stair from its floor to the floor above, the way a
     * player would: round the ring, never rising more than half a block
     * without a stair facing the way you walk, and with room for your head.
     * The first stairs rose a full block at every cell, corners included, and
     * nobody could climb them; counting the steps never noticed.
     */
    static List<String> climbProblems(DungeonPlan plan, Blueprint bp) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < plan.levels().size(); i++) {
            LevelPlan level = plan.levels().get(i);
            for (int[] s : level.stairsUp) {
                int bottom = Blueprinter.floorY(plan, i) + level.height(s[0], s[1]);
                int top = i == 0 ? 0 : Blueprinter.floorY(plan, i - 1) + plan.levels().get(i - 1).height(s[0], s[1]);
                int feet = bottom; // standing on the floor beside the first step
                int prevDir = -1;
                for (int n = 0; feet < top; n++) {
                    if (n > 64) {
                        out.add("level " + i + ": the stair never reaches the floor above (stuck at " + feet + " of " + top + ")");
                        break;
                    }
                    int[] c = RING[n % 8];
                    int x = s[0] + c[0];
                    int z = s[1] + c[1];
                    int[] next = RING[(n + 1) % 8];
                    int dir = dirOf(next[0] - c[0], next[1] - c[1]);
                    int arrive = n == 0 ? dir : prevDir;
                    // What is underfoot in this cell at or just below the current feet?
                    int code = bp.get(x, feet, z);
                    Part p = code == 0 ? null : Blueprint.part(code);
                    if (p == Part.STEP) {
                        if (Blueprint.facing(code) != arrive) {
                            out.add("level " + i + ": step " + n + " faces " + Blueprint.facing(code) + " but is climbed going " + arrive);
                            break;
                        }
                        feet += 1;
                    } else if (bp.get(x, feet - 1, z) != 0 && Blueprint.part(bp.get(x, feet - 1, z)) != Part.AIR) {
                        // A flat landing level with where we stand.
                    } else {
                        out.add("level " + i + ": nothing to stand on at step " + n + " (y " + feet + ")");
                        break;
                    }
                    for (int h = 0; h < 2; h++) {
                        int above = bp.get(x, feet + h, z);
                        if (above != 0 && Blueprint.part(above) != Part.AIR && Blueprint.part(above) != Part.LIGHT) {
                            out.add("level " + i + ": no headroom at step " + n);
                        }
                    }
                    prevDir = dir;
                }
            }
        }
        return out;
    }

    private static int dirOf(int dx, int dz) {
        return dz < 0 ? 0 : dx > 0 ? 1 : dz > 0 ? 2 : 3;
    }

    @Test
    void stairsCanBeClimbed() {
        for (long seed = 0; seed < 20; seed++) {
            DungeonPlan plan = Planner.plan(seed, 4);
            List<String> problems = climbProblems(plan, Blueprinter.blueprint(plan));
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
        }
    }

    /** Flat ground needs no extra depth; a valley over part of the dungeon sinks every level under it. */
    @Test
    void levelsSinkUnderLowGround() {
        DungeonPlan plan = Planner.plan(8L, 3);
        assertEquals(DungeonPlan.MIN_TOP, Blueprinter.requiredTop(plan, (x, z) -> 0));
        int valley = Blueprinter.requiredTop(plan, (x, z) -> x < -10 ? -20 : 0);
        assertTrue(valley > DungeonPlan.MIN_TOP, "a 20-block valley should push the levels down, got " + valley);
        // Every ceiling under the valley must now keep COVER blocks of ground over it.
        Blueprint bp = Blueprinter.blueprint(plan.withTop(valley));
        bp.forEachColumn(col -> {
            int x = col.x();
            int[] codes = col.codes();
            if (x >= -10 || Math.abs(x) > LevelPlan.RADIUS - 8) {
                return;
            }
            for (int i = codes.length - 1; i >= 0; i--) {
                if (codes[i] != 0) {
                    int y = col.y0() + i;
                    assertTrue(y <= -20 - Blueprinter.COVER, "a block at y " + y + " under a valley floor at -20");
                    break;
                }
            }
        });
    }

    /** Every locked door has a lever aimed at it, every secret wall block is a trigger, every trap is on floor. */
    static List<String> triggerProblems(DungeonPlan plan, Blueprint bp) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> aimed = new java.util.HashSet<>();
        for (Trigger t : bp.triggers()) {
            if (t.kind() == Trigger.Kind.LEVER) {
                for (int[] d : t.targets()) {
                    aimed.add(d[0] + "," + d[1] + "," + d[2]);
                }
                if (Blueprint.part(bp.get(t.x(), t.y(), t.z())) != Part.LEVER) {
                    out.add("lever trigger with no lever at " + t.x() + "," + t.y() + "," + t.z());
                }
            }
            if (t.kind().isTrap()) {
                int below = bp.get(t.x(), t.y() - 1, t.z());
                Part p = below == 0 ? null : Blueprint.part(below);
                if (t.kind() == Trigger.Kind.PIT ? p != Part.PIT_TILE
                        : p != Part.FLOOR && p != Part.CORRIDOR_FLOOR && p != Part.FLOOR_ACCENT && p != Part.FLOOR_INLAY) {
                    out.add("trap at " + t.x() + "," + t.y() + "," + t.z() + " is not on a floor");
                }
            }
            if (t.kind() == Trigger.Kind.SECRET && Blueprint.part(bp.get(t.x(), t.y(), t.z())) != Part.SECRET_WALL) {
                out.add("secret trigger on something that is not a secret wall");
            }
        }
        bp.forEachColumn(col -> {
            for (int i = 0; i < col.codes().length; i++) {
                if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.LOCKED_LOWER
                        && !aimed.contains(col.x() + "," + (col.y0() + i) + "," + col.z())) {
                    out.add("locked door at " + col.x() + "," + (col.y0() + i) + "," + col.z() + " has no lever");
                }
            }
        });
        return out;
    }

    @Test
    void triggersAreWired() {
        int levers = 0;
        int traps = 0;
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            List<String> problems = triggerProblems(plan, bp);
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
            for (Trigger t : bp.triggers()) {
                levers += t.kind() == Trigger.Kind.LEVER ? 1 : 0;
                traps += t.kind().isTrap() ? 1 : 0;
            }
        }
        assertTrue(levers > 10 && traps > 100, "levers " + levers + ", traps " + traps);
    }

    /** ...and that check notices a locked door nobody can open. */
    @Test
    void noticesAnUnwiredLock() {
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 4);
            Blueprint bp = Blueprinter.blueprint(plan);
            Trigger lever = bp.triggers().stream().filter(t -> t.kind() == Trigger.Kind.LEVER).findFirst().orElse(null);
            if (lever == null) {
                continue;
            }
            bp.addTrigger(new Trigger(Trigger.Kind.LEVER, lever.x(), lever.y(), lever.z(), lever.level(), new int[0][]));
            assertFalse(triggerProblems(plan, bp).isEmpty());
            return;
        }
        throw new AssertionError("no seed had a locked door to test with");
    }

    /** Props a player can walk through or over. */
    private static boolean passable(int code) {
        if (code == 0) {
            return true;
        }
        return switch (Blueprint.part(code)) {
            case AIR, CARPET, MOSS, RAIL, WATER, DOOR_LOWER, LOCKED_LOWER, SECRET_WALL, LIGHT, BANNER, WALL_TORCH, CHAIN, STEP, LANDING,
                    RUG, PLANT, MUSHROOM, VINE, ROOTS, TABLE_TOP, TRAP_PLATE, TRAP_WIRE, DECOY_PLATE, DECOY_WIRE -> true;
            default -> false;
        };
    }

    /**
     * After dressing, every doorway, every room and every way down is still
     * reachable from where the stair arrives: props never wall anything off.
     */
    static List<String> dressingProblems(DungeonPlan plan, Blueprint bp) {
        List<String> out = new ArrayList<>();
        int lim = LevelPlan.RADIUS;
        for (int i = 0; i < plan.levels().size(); i++) {
            LevelPlan level = plan.levels().get(i);
            boolean[][] seen = walk(plan, bp, i);
            if (seen == null) {
                continue;
            }
            for (int x = -lim; x <= lim; x++) {
                for (int z = -lim; z <= lim; z++) {
                    if (level.cell(x, z).isDoor() && !seen[x + lim][z + lim]) {
                        out.add("level " + i + ": doorway " + x + "," + z + " walled off by dressing");
                    }
                }
            }
            for (int[] d : level.stairsDown) {
                boolean ok = false;
                for (int dx = -2; dx <= 2 && !ok; dx++) {
                    for (int dz = -2; dz <= 2 && !ok; dz++) {
                        ok = seen[d[0] + dx + lim][d[1] + dz + lim];
                    }
                }
                if (!ok) {
                    out.add("level " + i + ": stair down at " + d[0] + "," + d[1] + " walled off by dressing");
                }
            }
        }
        return out;
    }

    /** Cells reachable on foot from where the stair arrives, with props as obstacles. */
    static boolean[][] walk(DungeonPlan plan, Blueprint bp, int i) {
        int lim = LevelPlan.RADIUS;
        LevelPlan level = plan.levels().get(i);
        {
            boolean[][] seen = new boolean[LevelPlan.SIZE][LevelPlan.SIZE];
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            int[] s = level.stairsUp.get(0);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 2 && level.cell(s[0] + dx, s[1] + dz).isWalkable()) {
                        seen[s[0] + dx + lim][s[1] + dz + lim] = true;
                        queue.add(new int[] {s[0] + dx, s[1] + dz});
                    }
                }
            }
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int x = c[0] + d[0];
                    int z = c[1] + d[1];
                    if (!LevelPlan.inBounds(x, z) || seen[x + lim][z + lim] || !level.cell(x, z).isWalkable()) {
                        continue;
                    }
                    int f = Blueprinter.floorY(plan, i) + level.height(x, z);
                    if (!passable(bp.get(x, f, z))) {
                        continue;
                    }
                    if (noFloor(bp.get(x, f - 1, z))) {
                        // A one-block gap in a puzzle room: jump it, straight on, to floor at the same height.
                        int jx = x + d[0];
                        int jz = z + d[1];
                        if (LevelPlan.inBounds(jx, jz) && !seen[jx + lim][jz + lim] && level.cell(jx, jz).isWalkable()
                                && level.height(jx, jz) == level.height(x, z) && passable(bp.get(jx, f, jz))
                                && !noFloor(bp.get(jx, f - 1, jz))) {
                            seen[jx + lim][jz + lim] = true;
                            queue.add(new int[] {jx, jz});
                        }
                        continue;
                    }
                    seen[x + lim][z + lim] = true;
                    queue.add(new int[] {x, z});
                }
            }
            return seen;
        }
    }

    @Test
    void dressingLeavesEverythingReachable() {
        int props = 0;
        int encounters = 0;
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            List<String> problems = dressingProblems(plan, bp);
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems.subList(0, Math.min(5, problems.size())));
            final int[] n = {0};
            bp.forEachColumn(c -> {
                for (int code : c.codes()) {
                    if (code != 0 && Blueprint.part(code).ordinal() >= Part.CHEST.ordinal()) {
                        n[0]++;
                    }
                }
            });
            props += n[0];
            for (Trigger t : bp.triggers()) {
                encounters += t.kind().isEncounter() ? 1 : 0;
            }
        }
        System.out.println("dressing: " + props + " props and " + encounters + " encounters over 30 dungeons");
        assertTrue(props > 3000 && encounters > 300, props + " props, " + encounters + " encounters");
    }

    /**
     * Every trap not on a step carries a plate or wire, every decoy carries a
     * decoy one, nothing else does, and there are decoys at all.
     */
    @Test
    void trapsAndDecoysHaveTheirPlates() {
        int traps = 0;
        int shown = 0;
        int decoys = 0;
        int pits = 0;
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            java.util.Set<Long> marked = new java.util.HashSet<>();
            for (Trigger t : bp.triggers()) {
                int code = bp.get(t.x(), t.y(), t.z());
                Part part = code == 0 ? Part.AIR : Blueprint.part(code);
                if (t.kind() == Trigger.Kind.PIT) {
                    pits++;
                    int tile = bp.get(t.x(), t.y() - 1, t.z());
                    assertTrue(tile != 0 && Blueprint.part(tile) == Part.PIT_TILE, "seed " + seed + ": a pit with no tile");
                    continue;
                }
                if (t.kind().isTrap()) {
                    traps++;
                    // Every trap, step or not: one on a stair moves to flat floor or is left out.
                    assertTrue(part == Part.TRAP_PLATE || part == Part.TRAP_WIRE,
                            "seed " + seed + ": trap at " + t.x() + "," + t.z() + " carries " + part);
                    shown++;
                } else if (t.kind() == Trigger.Kind.DECOY) {
                    decoys++;
                    assertTrue(part == Part.DECOY_PLATE || part == Part.DECOY_WIRE, "seed " + seed + ": decoy carries " + part);
                } else {
                    continue;
                }
                marked.add(Trigger.key(t.x(), t.y(), t.z()));
            }
            final long s = seed;
            bp.forEachColumn(c -> {
                for (int k = 0; k < c.codes().length; k++) {
                    int code = c.codes()[k];
                    Part part = code == 0 ? Part.AIR : Blueprint.part(code);
                    if (part.name().startsWith("TRAP_") || part.name().startsWith("DECOY_")) {
                        assertTrue(marked.contains(Trigger.key(c.x(), c.y0() + k, c.z())),
                                "seed " + s + ": a stray " + part + " at " + c.x() + "," + c.z());
                    }
                }
            });
        }
        System.out.println("traps: " + traps + " (" + shown + " with a plate or wire), " + pits + " pits, " + decoys + " decoys over 30 dungeons");
        assertTrue(shown == traps && traps > 900 && decoys >= traps * 0.8, traps + " traps, " + shown + " shown, " + decoys + " decoys");
    }

    private static boolean isVoid(int code) {
        return code != 0 && Blueprint.part(code) == Part.VOID;
    }

    /** No floor to stand on: the void, or the open air over a leap of faith's sunken void. */
    private static boolean noFloor(int code) {
        return code != 0 && (Blueprint.part(code) == Part.VOID || Blueprint.part(code) == Part.AIR);
    }

    /**
     * Each puzzle room, walked from its restart block over path cells only:
     * every way in is reached, the hoard chest stands beside a reached cell,
     * and there is void to fall into. {@code extraVoid} treats more cells as
     * void, which is how the test below proves the walk can fail.
     */
    static List<String> puzzleProblems(DungeonPlan plan, Blueprint bp, java.util.Set<Long> extraVoid) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < plan.levels().size(); i++) {
            LevelPlan level = plan.levels().get(i);
            for (com.sablednah.crawlspace.plan.Room r : level.rooms) {
                if (r.role != com.sablednah.crawlspace.plan.Role.PUZZLE) {
                    continue;
                }
                int cx = r.centerX();
                int cz = r.centerZ();
                int f = Blueprinter.floorY(plan, i) + level.height(cx, cz);
                String where = "level " + i + " puzzle at " + cx + "," + cz;
                if (bp.get(cx, f - 1, cz) == 0 || Blueprint.part(bp.get(cx, f - 1, cz)) != Part.RESTART) {
                    out.add(where + ": no restart block at the centre");
                }
                java.util.Set<Long> seen = new java.util.HashSet<>();
                java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                q.add(new int[] {cx, cz});
                seen.add(((long) cx << 32) ^ (cz & 0xffffffffL));
                int voids = 0;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int x = c[0] + d[0];
                        int z = c[1] + d[1];
                        long k = ((long) x << 32) ^ (z & 0xffffffffL);
                        if (!r.contains(x, z) || seen.contains(k) || !passable(bp.get(x, f, z))) {
                            continue;
                        }
                        if (extraVoid.contains(k) || noFloor(bp.get(x, f - 1, z))) {
                            // A gap: jump it if there is floor straight beyond.
                            int jx = x + d[0];
                            int jz = z + d[1];
                            long jk = ((long) jx << 32) ^ (jz & 0xffffffffL);
                            if (r.contains(jx, jz) && !seen.contains(jk) && !extraVoid.contains(jk)
                                    && !noFloor(bp.get(jx, f - 1, jz)) && passable(bp.get(jx, f, jz))) {
                                seen.add(jk);
                                q.add(new int[] {jx, jz});
                            }
                            continue;
                        }
                        seen.add(k);
                        q.add(new int[] {x, z});
                    }
                }
                boolean chest = false;
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int z = r.minZ(); z <= r.maxZ(); z++) {
                        if (!r.contains(x, z)) {
                            continue;
                        }
                        voids += noFloor(bp.get(x, f - 1, z)) ? 1 : 0;
                        int code = bp.get(x, f, z);
                        boolean entrance = false;
                        boolean besideSeen = false;
                        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int ox = x + d[0];
                            int oz = z + d[1];
                            entrance |= !r.contains(ox, oz) && level.cell(ox, oz).isWalkable();
                            besideSeen |= seen.contains(((long) ox << 32) ^ (oz & 0xffffffffL));
                        }
                        if (entrance && !seen.contains(((long) x << 32) ^ (z & 0xffffffffL))) {
                            out.add(where + ": the way in at " + x + "," + z + " is not on the maze");
                        }
                        if (code != 0 && Blueprint.part(code) == Part.HOARD_CHEST) {
                            chest = true;
                            if (!besideSeen) {
                                out.add(where + ": the chest at " + x + "," + z + " cannot be reached");
                            }
                        }
                    }
                }
                if (!chest) {
                    out.add(where + ": no chest");
                }
                if (voids < 8) {
                    out.add(where + ": only " + voids + " void cells");
                }
            }
        }
        return out;
    }

    /** Puzzle rooms appear, and every one is a maze that joins all its doorways and its chest. */
    @Test
    void puzzleRoomsAreSolvable() {
        int rooms = 0;
        java.util.Map<Part, Integer> kinds = new java.util.EnumMap<>(Part.class);
        for (long seed = 0; seed < 40; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            List<String> problems = puzzleProblems(plan, bp, java.util.Set.of());
            assertTrue(problems.isEmpty(), "seed " + seed + ": " + problems);
            bp.forEachColumn(c -> {
                for (int code : c.codes()) {
                    if (code != 0 && (Blueprint.part(code) == Part.PATH_HIDDEN || Blueprint.part(code) == Part.CRUMBLE
                            || Blueprint.part(code) == Part.DRIPLEAF)) {
                        kinds.merge(Blueprint.part(code), 1, Integer::sum);
                    }
                }
            });
            for (LevelPlan level : plan.levels()) {
                for (com.sablednah.crawlspace.plan.Room r : level.rooms) {
                    rooms += r.role == com.sablednah.crawlspace.plan.Role.PUZZLE ? 1 : 0;
                }
            }
        }
        System.out.println("puzzle rooms: " + rooms + " over 40 dungeons of 6 levels; blocks: " + kinds);
        assertTrue(rooms >= 20, rooms + " puzzle rooms");
        for (Part p : new Part[] {Part.PATH_HIDDEN, Part.CRUMBLE, Part.DRIPLEAF}) {
            assertTrue(kinds.getOrDefault(p, 0) > 0, "no " + p + " in any puzzle room");
        }
    }

    /** ...and that walk notices a doorway cut off: the void put on the first step in from a door. */
    @Test
    void noticesACutOffPuzzleDoor() {
        for (long seed = 0; seed < 40; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            for (LevelPlan level : plan.levels()) {
                for (com.sablednah.crawlspace.plan.Room r : level.rooms) {
                    if (r.role != com.sablednah.crawlspace.plan.Role.PUZZLE) {
                        continue;
                    }
                    for (int x = r.minX(); x <= r.maxX(); x++) {
                        for (int z = r.minZ(); z <= r.maxZ(); z++) {
                            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                                if (r.contains(x, z) && !r.contains(x + d[0], z + d[1])
                                        && level.cell(x + d[0], z + d[1]).isWalkable()
                                        && (x != r.centerX() || z != r.centerZ())) {
                                    List<String> problems = puzzleProblems(plan, bp,
                                            java.util.Set.of(((long) x << 32) ^ (z & 0xffffffffL)));
                                    assertTrue(problems.stream().anyMatch(m -> m.contains("not on the maze")),
                                            "a void on the way in at " + x + "," + z + " went unnoticed: " + problems);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
        throw new AssertionError("no puzzle room with a doorway in 40 dungeons");
    }

    /** ...and that check notices a doorway blocked by a barrel. */
    @Test
    void noticesABlockedDoorway() {
        DungeonPlan plan = Planner.plan(4L, 3);
        Blueprint bp = Blueprinter.blueprint(plan);
        assertTrue(dressingProblems(plan, bp).isEmpty());
        LevelPlan level = plan.levels().get(1);
        for (int x = -LevelPlan.RADIUS; x <= LevelPlan.RADIUS; x++) {
            for (int z = -LevelPlan.RADIUS; z <= LevelPlan.RADIUS; z++) {
                if (level.cell(x, z).isDoor()) {
                    bp.set(x, Blueprinter.floorY(plan, 1) + level.height(x, z), z, Part.BARREL, 0, 1);
                    assertFalse(dressingProblems(plan, bp).isEmpty());
                    return;
                }
            }
        }
    }

    /** Every tower design, in every style: still sealed below ground, and the stair still climbs into it. */
    @Test
    void everyTowerDesignIsSoundAndClimbable() {
        java.util.Set<Towers.Design> seen = java.util.EnumSet.noneOf(Towers.Design.class);
        for (String style : new String[] {"stone", "sandstone", "terracotta", "mossy", "snowy", "mushroom", "cherry"}) {
            for (long seed = 0; seed < 8; seed++) {
                DungeonPlan plan = Planner.plan(seed, 2);
                seen.add(Towers.choose(style, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), 0x70E3L)));
                Blueprint bp = Blueprinter.blueprint(plan, style);
                List<String> climb = climbProblems(plan, bp);
                assertTrue(climb.isEmpty(), style + " seed " + seed + ": " + climb);
                List<String> leaks = leaks(bp.compact());
                assertTrue(leaks.isEmpty(), style + " seed " + seed + ": " + leaks);
            }
        }
        assertEquals(java.util.EnumSet.allOf(Towers.Design.class), seen, "every design came up");
    }
}
