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
            case AIR, WATER, DOOR_LOWER, DOOR_UPPER, LOCKED_LOWER, LOCKED_UPPER, TOWER_DOOR_LOWER, TOWER_DOOR_UPPER, STEP, LIGHT -> true;
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

    /** A spiral stair climbs one block per step from each level's floor to the floor above. */
    @Test
    void stairsClimbAllTheWay() {
        DungeonPlan plan = Planner.plan(11L, 4);
        Blueprint bp = Blueprinter.blueprint(plan);
        for (int i = 0; i < 4; i++) {
            LevelPlan level = plan.levels().get(i);
            int[] s = level.stairsUp.get(0);
            int bottom = Blueprinter.floorY(plan, i) + level.height(s[0], s[1]);
            int top = i == 0 ? 0 : Blueprinter.floorY(plan, i - 1) + plan.levels().get(i - 1).height(s[0], s[1]);
            int steps = 0;
            for (int y = bottom; y < top; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int c = bp.get(s[0] + dx, y, s[1] + dz);
                        if (c != 0 && Blueprint.part(c) == Part.STEP) {
                            steps++;
                        }
                    }
                }
            }
            assertEquals(top - bottom, steps, "level " + i);
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
}
