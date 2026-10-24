package com.sablednah.crawlspace.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.Dressing;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Planner;
import com.sablednah.crawlspace.plan.Room;
import com.sablednah.crawlspace.plan.Theme;

/**
 * Shelf skirting is laid last, on floor still empty, so the same dungeon with
 * shelves forced into every room and with none must differ by shelves (and
 * corner columns) only, and every doorway of every room must still reach every
 * other one it reached before.
 */
class ShelvesTest {

    private static Theme with(Theme t, double shelves) {
        Dressing d = t.dressing();
        return new Theme(t.name(), t.shapeWeights(), t.styleWeights(), t.narrowChance(), t.heightSpread(), t.poolChance(),
                t.roomScale(), new Dressing(d.flavour(), d.built(), d.finished(), d.columns(), d.cobwebs(), d.shelves(),
                        d.clutter(), d.overgrowth(), d.roots(), d.nicheStatues(), d.beams(), d.timber(), d.panels(), shelves, shelves));
    }

    private static List<Theme> all(double shelves) {
        List<Theme> out = new ArrayList<>();
        for (Theme t : Theme.BY_DEPTH) {
            out.add(with(t, shelves));
        }
        return out;
    }

    @Test
    void shelvesWallNothingOff() {
        int shelves = 0;
        List<String> problems = new ArrayList<>();
        for (long seed = 300; seed < 316; seed++) {
            DungeonPlan bare = Planner.plan(seed, 9, all(0));
            DungeonPlan shelved = Planner.plan(seed, 9, all(1));
            Blueprint a = Blueprinter.blueprint(bare);
            Blueprint b = Blueprinter.blueprint(shelved);
            for (int i = 0; i < bare.levels().size(); i++) {
                LevelPlan level = bare.levels().get(i);
                for (Room r : level.rooms) {
                    for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
                        for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                            int f = Blueprinter.floorAt(bare, i, x, z);
                            if (b.get(x, f, z) != 0 && Blueprint.part(b.get(x, f, z)) == Part.SHELF_UNIT) {
                                shelves++;
                            }
                        }
                    }
                    int before = reached(a, bare, i, level, r);
                    int after = reached(b, shelved, i, level, r);
                    if (after < before) {
                        problems.add("seed " + seed + " level " + i + " room " + r.id + " (" + r.role + "): doorways reached "
                                + before + " -> " + after);
                    }
                }
            }
        }
        System.out.println("SHELVES placed " + shelves + ", problems " + problems.size());
        problems.stream().limit(10).forEach(System.out::println);
        assertTrue(shelves > 500, "hardly any shelves were placed: " + shelves);
        assertTrue(problems.isEmpty(), problems.size() + " rooms lost a way through, first: " + (problems.isEmpty() ? "" : problems.get(0)));
    }

    @Test
    void shelvesAreTheOnlyDifference() {
        for (long seed = 300; seed < 306; seed++) {
            Blueprint a = Blueprinter.blueprint(Planner.plan(seed, 9, all(0))).compact();
            Blueprint b = Blueprinter.blueprint(Planner.plan(seed, 9, all(1))).compact();
            List<String> other = new ArrayList<>();
            b.forEachColumn(col -> {
                for (int k = 0; k < col.codes().length; k++) {
                    int y = col.y0() + k;
                    int was = a.get(col.x(), y, col.z());
                    int now = col.codes()[k];
                    if (was == now) {
                        continue;
                    }
                    Part p = Blueprint.part(now);
                    boolean ok = Blueprint.part(was) == Part.AIR && (p == Part.SHELF_UNIT || p == Part.PILLAR || p == Part.DADO);
                    if (!ok && other.size() < 5) {
                        other.add(Blueprint.part(was) + " -> " + p + " at " + col.x() + "," + y + "," + col.z());
                    }
                }
            });
            assertTrue(other.isEmpty(), "seed " + seed + ": shelves changed something else: " + other);
            assertTrue(a.triggers().size() == b.triggers().size(), "seed " + seed + ": triggers changed");
        }
    }

    /** How many of a room's doorways (and stairs) are reached from its first, walking floor that is open. */
    private static int reached(Blueprint bp, DungeonPlan plan, int i, LevelPlan level, Room r) {
        List<long[]> doors = new ArrayList<>();
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                Cell c = level.cell(x, z);
                if (c == Cell.DOOR || c == Cell.ARCH || c == Cell.DOOR_LOCKED || c == Cell.DOOR_SECRET || c == Cell.DOOR_ONEWAY
                        || c == Cell.STAIR_UP || c == Cell.STAIR_DOWN) {
                    doors.add(new long[] {x, z});
                }
            }
        }
        if (doors.isEmpty()) {
            return 0;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {(int) doors.get(0)[0], (int) doors.get(0)[1]});
        seen.add(Blueprint.key((int) doors.get(0)[0], (int) doors.get(0)[1]));
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                boolean inside = r.contains(x, z) || doors.stream().anyMatch(p -> p[0] == x && p[1] == z);
                if (!inside || !level.cell(x, z).isWalkable() || !seen.add(Blueprint.key(x, z))) {
                    continue;
                }
                int code = bp.get(x, Blueprinter.floorAt(plan, i, x, z), z);
                Part p = code == 0 ? Part.AIR : Blueprint.part(code);
                if (p == Part.SHELF_UNIT || p == Part.PILLAR || p == Part.DADO) {
                    continue; // what shelving adds; everything else is the same in both, so counts the same
                }
                queue.add(new int[] {x, z});
            }
        }
        int n = 0;
        for (long[] p : doors) {
            n += seen.contains(Blueprint.key((int) p[0], (int) p[1])) ? 1 : 0;
        }
        return n;
    }
}
