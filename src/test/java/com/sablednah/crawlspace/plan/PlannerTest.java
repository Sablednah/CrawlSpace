package com.sablednah.crawlspace.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.debug.PlanRenderer;

class PlannerTest {

    private static final File OUT = new File(System.getProperty("crawlspace.renderDir", "build/plan-renders"));

    @Test
    void sameSeedSameDungeon() {
        DungeonPlan a = Planner.plan(1234L, 4);
        DungeonPlan b = Planner.plan(1234L, 4);
        for (int i = 0; i < 4; i++) {
            LevelPlan la = a.levels().get(i);
            LevelPlan lb = b.levels().get(i);
            for (int x = -LevelPlan.RADIUS; x <= LevelPlan.RADIUS; x++) {
                for (int z = -LevelPlan.RADIUS; z <= LevelPlan.RADIUS; z++) {
                    assertEquals(la.cell(x, z), lb.cell(x, z));
                    assertEquals(la.height(x, z), lb.height(x, z));
                }
            }
        }
    }

    /** Every seed in a long run plans, and passes every check. */
    @Test
    void manySeedsAreSound() {
        int seeds = Integer.getInteger("crawlspace.seeds", 300);
        int levels = 8;
        List<String> failures = new ArrayList<>();
        int retries = 0;
        int total = 0;
        int pits = 0;
        int extraStairs = 0;
        long started = System.nanoTime();
        for (long seed = 0; seed < seeds; seed++) {
            try {
                DungeonPlan plan = Planner.plan(seed, levels);
                List<String> problems = PlanCheck.dungeon(plan);
                if (!problems.isEmpty()) {
                    failures.add("seed " + seed + ": " + problems.get(0));
                }
                for (LevelPlan l : plan.levels()) {
                    total++;
                    retries += l.attempts - 1;
                    pits += l.pits.size();
                    extraStairs += Math.max(0, l.stairsDown.size() - 1);
                }
            } catch (IllegalStateException e) {
                failures.add("seed " + seed + ": " + e.getMessage());
            }
        }
        double ms = (System.nanoTime() - started) / 1e6 / seeds;
        System.out.printf("%d dungeons x %d levels: %.1f ms each, %d retries over %d levels, %d pits, %d extra stairs%n",
                seeds, levels, ms, retries, total, pits, extraStairs);
        System.out.println("thrown-away attempts: " + LevelPlanner.FAILURES);
        assertTrue(failures.isEmpty(), failures.size() + " failures, first: " + failures.stream().limit(5).toList());
    }

    /** Sheets to look at: the point of the exercise. */
    @Test
    void renderSheets() throws Exception {
        for (long seed : new long[] {1, 2, 3, 7, 42, 1234, 2026, 31337}) {
            DungeonPlan plan = Planner.plan(seed, 9);
            PlanRenderer.write(plan, 3, 3, new File(OUT, "dungeon-" + seed + ".png"));
        }
    }

    // ---- Each check must be able to fail: break a sound plan and watch it notice. ----

    private static LevelPlan sound() {
        LevelPlan l = Planner.plan(99L, 3).levels().get(1);
        assertTrue(PlanCheck.level(l).isEmpty());
        return l;
    }

    @Test
    void noticesARoomCutOff() {
        LevelPlan l = sound();
        // Wall up every doorway of the exit room.
        Room exit = l.roomWith(Role.EXIT);
        for (Link link : l.links) {
            if (link.a == exit) {
                l.set(link.doorA[0], link.doorA[1], Cell.WALL);
            }
            if (link.b == exit && link.doorB != null) {
                l.set(link.doorB[0], link.doorB[1], Cell.WALL);
            }
        }
        assertFalse(PlanCheck.level(l).isEmpty());
    }

    @Test
    void noticesABreachedWall() {
        LevelPlan l = sound();
        Room r = l.rooms.get(1);
        // Open the cell just outside the room's east edge, at its centre row, as corridor.
        int x = r.maxX() + 1;
        int z = r.centerZ();
        while (r.contains(x - 1, z) == false && x > r.minX()) {
            x--;
        }
        l.set(x, z, Cell.CORRIDOR);
        l.setRegion(x, z, LevelPlan.CORRIDOR);
        List<String> problems = PlanCheck.level(l);
        assertTrue(problems.stream().anyMatch(p -> p.contains("breaks into") || p.contains("no wall")), problems.toString());
    }

    @Test
    void noticesACliff() {
        LevelPlan l = sound();
        Room r = l.roomWith(Role.ENTRY);
        l.setHeight(r.centerX() + 2, r.centerZ() + 2, 5);
        assertTrue(PlanCheck.level(l).stream().anyMatch(p -> p.contains("step")));
    }

    @Test
    void noticesAStairThatLandsNowhere() {
        DungeonPlan plan = Planner.plan(5L, 3);
        assertTrue(PlanCheck.dungeon(plan).isEmpty());
        int[] s = plan.levels().get(0).stairsDown.get(0);
        plan.levels().get(1).set(s[0], s[1], Cell.FLOOR);
        assertFalse(PlanCheck.dungeon(plan).isEmpty());
    }

    /**
     * Every doorway is level with the room it opens into. A doorway is two
     * blocks high, so a step up into one cannot be jumped: Sable met an open
     * door he could not walk through.
     */
    @Test
    void doorwaysAreLevelWithTheirRooms() {
        for (long seed = 0; seed < 60; seed++) {
            for (LevelPlan l : Planner.plan(seed, 6).levels()) {
                for (int x = -LevelPlan.RADIUS; x <= LevelPlan.RADIUS; x++) {
                    for (int z = -LevelPlan.RADIUS; z <= LevelPlan.RADIUS; z++) {
                        if (!l.cell(x, z).isDoor()) {
                            continue;
                        }
                        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int reg = l.region(x + d[0], z + d[1]);
                            if (reg >= 0) {
                                assertEquals(l.room(reg).floor, l.height(x, z),
                                        "seed " + seed + " level " + l.index + " doorway " + x + "," + z);
                            }
                        }
                    }
                }
            }
        }
    }
}
