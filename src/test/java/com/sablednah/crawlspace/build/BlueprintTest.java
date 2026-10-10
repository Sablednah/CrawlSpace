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
            case AIR, WATER, DOOR_LOWER, DOOR_UPPER, LOCKED_LOWER, LOCKED_UPPER, TOWER_DOOR_LOWER, TOWER_DOOR_UPPER, STEP, LIGHT, LEVER,
                    PORTCULLIS_GAP, ONEWAY_LOWER, ONEWAY_UPPER -> true;
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

    /**
     * Protection keeps only the shell ({@link Part#shell()}); everything else
     * may be broken. So with every breakable block gone, the dungeon must still
     * be sealed: no breakable block underground may touch the world it is cut
     * into, or digging out a barrel would be a way round a level.
     */
    static List<String> breakableLeaks(Blueprint bp) {
        List<String> out = new ArrayList<>();
        bp.forEachColumn(col -> {
            int[] codes = col.codes();
            for (int i = 0; i < codes.length; i++) {
                int y = col.y0() + i;
                if (codes[i] == 0 || Blueprint.part(codes[i]).shell() || y >= 0) {
                    continue;
                }
                for (int[] d : SIX) {
                    if (bp.get(col.x() + d[0], y + d[1], col.z() + d[2]) == 0 && out.size() < 10) {
                        out.add(Blueprint.part(codes[i]) + " at " + col.x() + "," + y + "," + col.z() + " is breakable and touches the world");
                    }
                }
            }
        });
        return out;
    }

    @Test
    void protectedShellSealsEveryLevel() {
        for (long seed = 0; seed < 40; seed++) {
            Blueprint bp = Blueprinter.blueprint(Planner.plan(seed, 6)).compact();
            List<String> leaks = breakableLeaks(bp);
            assertTrue(leaks.isEmpty(), "seed " + seed + ": " + leaks);
        }
    }

    /** ...and it can fail: make one outer wall block a barrel, and it must be noticed. */
    @Test
    void noticesABreakableHoleInTheShell() {
        Blueprint bp = Blueprinter.blueprint(Planner.plan(3L, 3));
        assertTrue(breakableLeaks(bp).isEmpty());
        int[] found = null;
        for (int x = -60; x <= 60 && found == null; x++) {
            for (int z = -60; z <= 60 && found == null; z++) {
                for (int y = -40; y < -5 && found == null; y++) {
                    int c = bp.get(x, y, z);
                    if (c != 0 && Blueprint.part(c) == Part.WALL && bp.get(x + 1, y, z) == 0) {
                        found = new int[] {x, y, z};
                    }
                }
            }
        }
        assertTrue(found != null);
        bp.set(found[0], found[1], found[2], Part.BARREL, 0, 0);
        assertFalse(breakableLeaks(bp).isEmpty());
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
        java.util.Set<Integer> keyed = new java.util.HashSet<>();
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
            if (t.kind() == Trigger.Kind.ONEWAY) {
                for (int[] d : t.targets()) {
                    aimed.add(d[0] + "," + d[1] + "," + d[2]);
                }
                if (Blueprint.part(bp.get(t.x(), t.y(), t.z())) != Part.LEVER) {
                    out.add("one-way trigger with no lever at " + t.x() + "," + t.y() + "," + t.z());
                }
            }
            if (t.kind() == Trigger.Kind.PORTCULLIS) {
                aimed.add(t.x() + "," + t.y() + "," + t.z());
                keyed.add(t.level());
            }
            if (t.kind() == Trigger.Kind.WINCH && Blueprint.part(bp.get(t.x(), t.y(), t.z())) != Part.WINCH) {
                out.add("winch trigger with no winch at " + t.x() + "," + t.y() + "," + t.z() + " (" + Blueprint.part(bp.get(t.x(), t.y(), t.z())) + ")");
            }
            if (t.kind() == Trigger.Kind.PORTCULLIS_TRAP || t.kind() == Trigger.Kind.WINCH) {
                for (int[] b : t.targets()) {
                    if (Blueprint.part(bp.get(b[0], b[1], b[2])) != Part.PORTCULLIS_GAP) {
                        out.add(t.kind() + " aimed at " + b[0] + "," + b[1] + "," + b[2] + ", which is no portcullis gap");
                    }
                }
            }
        }
        // Every keyed level has its key chest.
        java.util.Set<Integer> chests = new java.util.HashSet<>();
        bp.forEachColumn(col -> {
            for (int i = 0; i < col.codes().length; i++) {
                if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.KEY_CHEST) {
                    chests.add(Blueprint.level(col.codes()[i]));
                }
            }
        });
        for (int lv : keyed) {
            if (!chests.contains(lv)) {
                out.add("level " + lv + " has a keyed portcullis and no key chest");
            }
        }
        bp.forEachColumn(col -> {
            for (int i = 0; i < col.codes().length; i++) {
                if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.LOCKED_LOWER
                        && !aimed.contains(col.x() + "," + (col.y0() + i) + "," + col.z())) {
                    out.add("locked door at " + col.x() + "," + (col.y0() + i) + "," + col.z() + " has no lever");
                }
                if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.ONEWAY_LOWER
                        && !aimed.contains(col.x() + "," + (col.y0() + i) + "," + col.z())) {
                    out.add("one-way door at " + col.x() + "," + (col.y0() + i) + "," + col.z() + " has no lever");
                }
                boolean lowerBar = col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.PORTCULLIS
                        && (i == 0 || col.codes()[i - 1] == 0 || Blueprint.part(col.codes()[i - 1]) != Part.PORTCULLIS);
                if (lowerBar && !aimed.contains(col.x() + "," + (col.y0() + i) + "," + col.z())) {
                    out.add("portcullis at " + col.x() + "," + (col.y0() + i) + "," + col.z() + " has no trigger");
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

    @Test
    void portcullisesAreWired() {
        int keyed = 0;
        int dropping = 0;
        int winches = 0;
        for (long seed = 0; seed < 30; seed++) {
            Blueprint bp = Blueprinter.blueprint(Planner.plan(seed, 6));
            for (Trigger t : bp.triggers()) {
                keyed += t.kind() == Trigger.Kind.PORTCULLIS ? 1 : 0;
                dropping += t.kind() == Trigger.Kind.PORTCULLIS_TRAP ? 1 : 0;
                winches += t.kind() == Trigger.Kind.WINCH ? 1 : 0;
            }
        }
        assertTrue(keyed > 10 && dropping > 10 && winches >= dropping * 9 / 10,
                "keyed " + keyed + ", dropping " + dropping + ", winches " + winches);
    }

    /**
     * Windows: each is a pair of bars at eye level with a room's air on one side
     * and a corridor's air on the other, in a straight line, so you can see
     * through. Several in 30 dungeons.
     */
    @Test
    void windowsSeeThrough() {
        int windows = 0;
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            java.util.List<int[]> bars = new ArrayList<>();
            bp.forEachColumn(col -> {
                for (int i = 0; i < col.codes().length; i++) {
                    if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.WINDOW_BARS) {
                        bars.add(new int[] {col.x(), col.y0() + i, col.z()});
                    }
                }
            });
            for (int[] b : bars) {
                boolean through = false;
                for (int[] d : SIX) {
                    if (d[1] != 0) {
                        continue;
                    }
                    int a1 = bp.get(b[0] + d[0], b[1], b[2] + d[2]);
                    int a2 = bp.get(b[0] + 2 * d[0], b[1], b[2] + 2 * d[2]);
                    int back = bp.get(b[0] - d[0], b[1], b[2] - d[2]);
                    boolean airAhead = a1 != 0 && Blueprint.part(a1) == Part.AIR
                            || a1 != 0 && Blueprint.part(a1) == Part.WINDOW_BARS && a2 != 0 && Blueprint.part(a2) == Part.AIR;
                    boolean airBehind = back != 0 && (Blueprint.part(back) == Part.AIR || Blueprint.part(back) == Part.WINDOW_BARS);
                    through |= airAhead && airBehind;
                }
                assertTrue(through, "seed " + seed + ": window bars at " + b[0] + "," + b[1] + "," + b[2] + " see nothing");
                windows++;
            }
        }
        assertTrue(windows > 30, "only " + windows + " window bars in 30 dungeons");
    }

    /**
     * The finale arena: on the bottom level the lair's middle is a pit, and a
     * player on its gallery, at the doorways' height, can walk down into it
     * and back up by the flights alone, never climbing a block without a stair.
     */
    @Test
    void finaleArenasCanBeWalkedInto() {
        int arenas = 0;
        for (long seed = 0; seed < 40; seed++) {
            DungeonPlan plan = Planner.plan(seed, 4);
            Blueprint bp = Blueprinter.blueprint(plan);
            int last = plan.levels().size() - 1;
            LevelPlan level = plan.levels().get(last);
            for (com.sablednah.crawlspace.plan.Room r : level.rooms) {
                if (r.role != com.sablednah.crawlspace.plan.Role.LAIR) {
                    continue;
                }
                int f = Blueprinter.floorY(plan, last) + level.height(r.centerX(), r.centerZ());
                int pit = bp.get(r.centerX(), f - 1, r.centerZ());
                if (pit == 0 || Blueprint.part(pit) != Part.AIR) {
                    continue; // a plain lair
                }
                arenas++;
                // Walk from every gallery cell at door level.
                java.util.Set<String> seen = new java.util.HashSet<>();
                java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int z = r.minZ(); z <= r.maxZ(); z++) {
                        if (r.contains(x, z) && stands(bp, x, f, z)) {
                            q.add(new int[] {x, f, z});
                            seen.add(x + "," + f + "," + z);
                        }
                    }
                }
                boolean down = false;
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    down |= c[1] == f - 5;
                    for (int[] d : SIX) {
                        if (d[1] != 0) {
                            continue;
                        }
                        for (int dy = -1; dy <= 1; dy++) {
                            int x = c[0] + d[0];
                            int y = c[1] + dy;
                            int z = c[2] + d[2];
                            if (!r.contains(x, z) || !stands(bp, x, y, z) || !seen.add(x + "," + y + "," + z)) {
                                continue;
                            }
                            int under = bp.get(x, y - 1, z);
                            if (dy == 1 && (under == 0 || Blueprint.part(under) != Part.STEP)) {
                                seen.remove(x + "," + y + "," + z);
                                continue; // a block up with no stair: nobody climbs that
                            }
                            q.add(new int[] {x, y, z});
                        }
                    }
                }
                assertTrue(down, "seed " + seed + ": the finale arena cannot be walked into");
            }
        }
        System.out.println("finale arenas: " + arenas + " in 40 dungeons");
        assertTrue(arenas > 15, "only " + arenas + " finale arenas in 40 dungeons");
    }

    /**
     * Rooms you can only fall into: a pool under a pit from the level above,
     * and a one-way door whose lever is inside. Several in 40 dungeons.
     */
    @Test
    void pitRoomsAreReachedFromAbove() {
        int found = 0;
        for (long seed = 0; seed < 40; seed++) {
            DungeonPlan plan = Planner.plan(seed, 4);
            for (int i = 1; i < plan.levels().size(); i++) {
                LevelPlan lower = plan.levels().get(i);
                LevelPlan upper = plan.levels().get(i - 1);
                for (com.sablednah.crawlspace.plan.Link l : lower.links) {
                    com.sablednah.crawlspace.plan.Room r = l.kind == com.sablednah.crawlspace.plan.LinkKind.ONEWAY
                            ? com.sablednah.crawlspace.plan.Planner.onewayRoom(lower, l) : null;
                    if (r == null) {
                        continue;
                    }
                    int links = 0;
                    for (com.sablednah.crawlspace.plan.Link m : lower.links) {
                        links += m.a == r || m.b == r ? 1 : 0;
                    }
                    if (links != 1) {
                        continue; // a one-way shortcut into a room with other doors, not a pit room
                    }
                    boolean pitAbove = false;
                    for (int[] p : upper.pits) {
                        pitAbove |= r.contains(p[0], p[1]) && lower.cell(p[0], p[1]) == com.sablednah.crawlspace.plan.Cell.POOL;
                    }
                    assertTrue(pitAbove, "seed " + seed + ": a sealed room on level " + i + " with no pit into it");
                    found++;
                }
            }
        }
        System.out.println("pit rooms: " + found + " in 40 dungeons");
        assertTrue(found > 5, "only " + found + " pit rooms in 40 dungeons");
    }

    /**
     * The showcase dungeon has everything: every theme and every feeling, every
     * trap kind, a vault, a puzzle, a portcullis and a window, a pit room, an ice
     * board and a numbered guard room.
     */
    @Test
    void theShowcaseHasEverything() {
        DungeonPlan plan = Planner.plan(com.sablednah.crawlspace.plan.Showcase.seed(1), 6);
        Blueprint bp = Blueprinter.blueprint(plan);
        java.util.Set<String> themes = new java.util.HashSet<>();
        java.util.Set<com.sablednah.crawlspace.plan.Feeling> feelings = java.util.EnumSet.noneOf(com.sablednah.crawlspace.plan.Feeling.class);
        for (LevelPlan l : plan.levels()) {
            themes.add(l.theme.name());
            feelings.add(l.feeling);
        }
        assertEquals(5, themes.size(), "themes");
        assertEquals(6, feelings.size(), "feelings " + feelings);
        java.util.Set<Trigger.Kind> kinds = java.util.EnumSet.noneOf(Trigger.Kind.class);
        for (Trigger t : bp.triggers()) {
            kinds.add(t.kind());
        }
        System.out.println("SHOWCASE triggers " + kinds);
        for (Trigger.Kind k : new Trigger.Kind[] {Trigger.Kind.DARTS, Trigger.Kind.GAS, Trigger.Kind.ALARM, Trigger.Kind.WEBS,
                Trigger.Kind.ROCKFALL, Trigger.Kind.FROST, Trigger.Kind.FIRE, Trigger.Kind.SUMMON, Trigger.Kind.VAULT,
                Trigger.Kind.PORTCULLIS, Trigger.Kind.ICE_BOARD, Trigger.Kind.ORDERED, Trigger.Kind.BOSS, Trigger.Kind.LEVER}) {
            assertTrue(kinds.contains(k), "the showcase has no " + k);
        }
        assertFalse(com.sablednah.crawlspace.plan.Showcase.on(12345L), "an ordinary seed is not a showcase");
    }

    /** Most traps have a tell, and every kind that has one shows it somewhere in 40 dungeons. */
    @Test
    void trapsHaveTells() {
        int traps = 0;
        java.util.Set<Part> seen = java.util.EnumSet.noneOf(Part.class);
        int[] tells = {0};
        for (long seed = 1; seed <= 40; seed++) {
            Blueprint bp = Blueprinter.blueprint(Planner.plan(seed, 4));
            for (Trigger t : bp.triggers()) {
                if (t.kind().isTrap() && t.kind() != Trigger.Kind.PIT && t.kind() != Trigger.Kind.PORTCULLIS_TRAP) {
                    traps++;
                }
            }
            bp.forEachColumn(col -> {
                for (int code : col.codes()) {
                    if (code != 0 && Blueprint.part(code).name().startsWith("TELL_")) {
                        seen.add(Blueprint.part(code));
                        tells[0]++;
                    }
                }
            });
        }
        System.out.println("TELLS " + tells[0] + " non-skull/web tells; " + traps + " traps; kinds " + seen);
        assertEquals(java.util.EnumSet.of(Part.TELL_PEBBLE, Part.TELL_FROST, Part.TELL_SCORCH, Part.TELL_SCULK, Part.TELL_DEAD), seen);
    }

    /** Every dungeon has its rumour book: one lectern, on level 1, with clear air above it. */
    @Test
    void everyDungeonHasItsRumours() {
        for (long seed = 1; seed <= 40; seed++) {
            long sd = seed;
            Blueprint bp = Blueprinter.blueprint(Planner.plan(seed, 3));
            int[] found = {0, 0};
            bp.forEachColumn(col -> {
                for (int k = 0; k < col.codes().length; k++) {
                    int code = col.codes()[k];
                    if (code != 0 && Blueprint.part(code) == Part.RUMOURS) {
                        found[0]++;
                        found[1] = Blueprint.level(code);
                        int above = bp.get(col.x(), col.y0() + k + 1, col.z());
                        assertTrue(above == 0 || Blueprint.part(above) == Part.AIR, "seed " + sd + ": the lectern is buried");
                    }
                }
            });
            assertEquals(1, found[0], "seed " + seed + ": rumour lecterns");
            assertEquals(0, found[1], "seed " + seed + ": the lectern is not on level 1");
        }
    }

    /**
     * Feelings do what they say: over many levels, hollow ones have more
     * secret doors, trapped ones more traps, damp ones more pools, and a dark
     * level hangs lanterns only round its stairs. And every feeling turns up.
     */
    @Test
    void feelingsChangeTheirLevels() {
        java.util.Map<com.sablednah.crawlspace.plan.Feeling, int[]> seen = new java.util.EnumMap<>(com.sablednah.crawlspace.plan.Feeling.class);
        for (long seed = 0; seed < 60; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            for (int i = 0; i < plan.levels().size(); i++) {
                LevelPlan level = plan.levels().get(i);
                int[] n = seen.computeIfAbsent(level.feeling, k -> new int[5]);
                n[0]++;
                for (int x = -LevelPlan.RADIUS; x <= LevelPlan.RADIUS; x++) {
                    for (int z = -LevelPlan.RADIUS; z <= LevelPlan.RADIUS; z++) {
                        com.sablednah.crawlspace.plan.Cell c = level.cell(x, z);
                        n[1] += c == com.sablednah.crawlspace.plan.Cell.DOOR_SECRET ? 1 : 0;
                        n[3] += c == com.sablednah.crawlspace.plan.Cell.POOL ? 1 : 0;
                    }
                }
                n[2] += level.traps.size();
                if (level.feeling == com.sablednah.crawlspace.plan.Feeling.DARK) {
                    final int li = i;
                    bp.forEachColumn(col -> {
                        for (int k = 0; k < col.codes().length; k++) {
                            int code = col.codes()[k];
                            if (code != 0 && Blueprint.part(code) == Part.LIGHT && Blueprint.level(code) == li) {
                                com.sablednah.crawlspace.plan.Room r = level.room(level.region(col.x(), col.z()));
                                assertTrue(r != null && (r.role == com.sablednah.crawlspace.plan.Role.ENTRY
                                        || r.role == com.sablednah.crawlspace.plan.Role.EXIT
                                        || r.role == com.sablednah.crawlspace.plan.Role.LAIR),
                                        "a lantern on a dark level away from the stairs at " + col.x() + "," + col.z());
                            }
                        }
                    });
                }
            }
        }
        StringBuilder b = new StringBuilder();
        seen.forEach((f, n) -> b.append(f).append(": ").append(n[0]).append(" levels, ")
                .append(String.format("%.1f secret doors, %.1f traps, %.1f pool cells a level%n", n[1] / (double) n[0], n[2] / (double) n[0], n[3] / (double) n[0])));
        System.out.println(b);
        for (com.sablednah.crawlspace.plan.Feeling f : com.sablednah.crawlspace.plan.Feeling.values()) {
            assertTrue(seen.containsKey(f) && seen.get(f)[0] >= 5, f + " turned up too seldom: " + b);
        }
        java.util.function.ToDoubleBiFunction<com.sablednah.crawlspace.plan.Feeling, Integer> per = (f, k) -> seen.get(f)[k] / (double) seen.get(f)[0];
        com.sablednah.crawlspace.plan.Feeling none = com.sablednah.crawlspace.plan.Feeling.NONE;
        assertTrue(per.applyAsDouble(com.sablednah.crawlspace.plan.Feeling.HOLLOW, 1) > 1.5 * per.applyAsDouble(none, 1), b.toString());
        assertTrue(per.applyAsDouble(com.sablednah.crawlspace.plan.Feeling.TRAPPED, 2) > 1.5 * per.applyAsDouble(none, 2), b.toString());
        assertTrue(per.applyAsDouble(com.sablednah.crawlspace.plan.Feeling.DAMP, 3) > 2 * per.applyAsDouble(none, 3), b.toString());
    }

    /**
     * Every ice board can be crossed, each tile once, from whichever tile you
     * step on first: checked by search for every board size the builder makes.
     * And boards turn up.
     */
    @Test
    void iceBoardsCanBeSolved() {
        java.util.Set<String> sizes = new java.util.TreeSet<>();
        int boards = 0;
        for (long seed = 0; seed < 40; seed++) {
            Blueprint bp = Blueprinter.blueprint(Planner.plan(seed, 6));
            for (Trigger t : bp.triggers()) {
                if (t.kind() != Trigger.Kind.ICE_BOARD) {
                    continue;
                }
                boards++;
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                for (int k = 1; k < t.targets().length; k++) {
                    minX = Math.min(minX, t.targets()[k][0]);
                    maxX = Math.max(maxX, t.targets()[k][0]);
                    minZ = Math.min(minZ, t.targets()[k][2]);
                    maxZ = Math.max(maxZ, t.targets()[k][2]);
                }
                int w = maxX - minX + 1;
                int h = maxZ - minZ + 1;
                assertEquals(w * h, t.targets().length - 1, "seed " + seed + ": a board that is not a full rectangle");
                sizes.add(w + "x" + h);
            }
        }
        for (String size : sizes) {
            int w = Integer.parseInt(size.split("x")[0]);
            int h = Integer.parseInt(size.split("x")[1]);
            for (int sx = 0; sx < w; sx++) {
                for (int sz = 0; sz < h; sz++) {
                    assertTrue(tour(w, h, sx, sz, new boolean[w][h], 1), "a " + size + " board cannot be crossed from " + sx + "," + sz);
                }
            }
        }
        System.out.println("ice boards: " + boards + " in 40 dungeons, sizes " + sizes);
        assertTrue(boards >= 5, "only " + boards + " ice boards");
    }

    /** Whether every cell can be visited once, from (x, z), with {@code n} visited so far. */
    private static boolean tour(int w, int h, int x, int z, boolean[][] seen, int n) {
        seen[x][z] = true;
        if (n == w * h) {
            seen[x][z] = false;
            return true;
        }
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + d[0];
            int nz = z + d[1];
            if (nx >= 0 && nz >= 0 && nx < w && nz < h && !seen[nx][nz] && tour(w, h, nx, nz, seen, n + 1)) {
                seen[x][z] = false;
                return true;
            }
        }
        seen[x][z] = false;
        return false;
    }

    /** Somewhere to stand: two clear blocks over something solid. */
    private static boolean stands(Blueprint bp, int x, int y, int z) {
        int under = bp.get(x, y - 1, z);
        if (under == 0 || Blueprint.part(under) == Part.AIR || Blueprint.part(under) == Part.RAILING) {
            return false;
        }
        for (int k = 0; k < 2; k++) {
            int c = bp.get(x, y + k, z);
            if (c != 0 && Blueprint.part(c) != Part.AIR && Blueprint.part(c) != Part.CARPET && Blueprint.part(c) != Part.CHAIN
                    && Blueprint.part(c) != Part.LIGHT && Blueprint.part(c) != Part.COBWEB && Blueprint.part(c) != Part.BANNER) {
                return false;
            }
        }
        return true;
    }

    /** One-way doors exist, and each has its lever on its own side: inside the room further from the entry. */
    @Test
    void onewayDoorsOpenFromTheFarSide() {
        int doors = 0;
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 6);
            Blueprint bp = Blueprinter.blueprint(plan);
            for (Trigger t : bp.triggers()) {
                if (t.kind() != Trigger.Kind.ONEWAY) {
                    continue;
                }
                doors++;
                LevelPlan level = plan.levels().get(t.level());
                com.sablednah.crawlspace.plan.Link link = null;
                com.sablednah.crawlspace.plan.Room own = null;
                for (com.sablednah.crawlspace.plan.Link l : level.links) {
                    if (l.kind != com.sablednah.crawlspace.plan.LinkKind.ONEWAY) {
                        continue;
                    }
                    com.sablednah.crawlspace.plan.Room r = com.sablednah.crawlspace.plan.Planner.onewayRoom(level, l);
                    int[] d = r == null ? null : r == l.a ? l.doorA : l.doorB;
                    if (d != null && d[0] == t.targets()[0][0] && d[1] == t.targets()[0][2]) {
                        link = l;
                        own = r;
                    }
                }
                assertTrue(link != null, "seed " + seed + ": a one-way lever with no one-way link");
                assertEquals(own.id, level.region(t.x(), t.z()), "seed " + seed + ": the lever is not in the door's own room");
            }
        }
        assertTrue(doors > 15, "only " + doors + " one-way doors in 30 dungeons");
    }

    /** ...and the wiring check notices a keyed level whose key chest is missing. */
    @Test
    void noticesAMissingKeyChest() {
        for (long seed = 0; seed < 30; seed++) {
            DungeonPlan plan = Planner.plan(seed, 4);
            Blueprint bp = Blueprinter.blueprint(plan);
            int[] chest = null;
            for (int x = -120; x <= 120 && chest == null; x++) {
                for (int z = -120; z <= 120 && chest == null; z++) {
                    Blueprint.Column col = bp.column(x, z);
                    if (col == null) {
                        continue;
                    }
                    for (int i = 0; i < col.codes().length; i++) {
                        if (col.codes()[i] != 0 && Blueprint.part(col.codes()[i]) == Part.KEY_CHEST) {
                            chest = new int[] {x, col.y0() + i, z, Blueprint.level(col.codes()[i])};
                        }
                    }
                }
            }
            if (chest == null) {
                continue;
            }
            assertTrue(triggerProblems(plan, bp).isEmpty());
            bp.set(chest[0], chest[1], chest[2], Part.AIR, 0, chest[3]);
            assertFalse(triggerProblems(plan, bp).isEmpty());
            return;
        }
        throw new AssertionError("no seed had a keyed portcullis to test with");
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
                    RUG, PLANT, MUSHROOM, VINE, ROOTS, TABLE_TOP, TRAP_PLATE, TRAP_WIRE, DECOY_PLATE, DECOY_WIRE,
                    PORTCULLIS, PORTCULLIS_GAP, ONEWAY_LOWER, LEVER, WINCH,
                    TELL_PEBBLE, TELL_FROST, TELL_SCORCH, TELL_SCULK, TELL_DEAD -> true; // a lever has no collision
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
                if (t.kind() == Trigger.Kind.PORTCULLIS_TRAP) {
                    // No plate: its tell is the sill under the arch.
                    int[] door = t.targets()[0];
                    int sill = bp.get(door[0], door[1] - 1, door[2]);
                    assertTrue(sill != 0 && Blueprint.part(sill) == Part.PORTCULLIS_SILL, "seed " + seed + ": a portcullis with no sill");
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
                final int li = i;
                if (bp.triggers().stream().anyMatch(t -> t.kind() == Trigger.Kind.ICE_BOARD && t.level() == li && r.contains(t.x(), t.z()))) {
                    continue; // an ice board, not a maze: iceBoardsCanBeSolved
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
