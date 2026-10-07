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
    /** The highest a blueprint goes above the ground: the tallest tower design and its finial. */
    public static final int TOP = Towers.MAX_HEIGHT + 2;
    /**
     * Cells round a spiral stair, clockwise from north, and the way a climber
     * walks through each (0 N, 1 E, 2 S, 3 W). Only the side cells (even
     * indices) hold stairs, and each faces the way it is walked; the corners
     * are flat landings.
     */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    private static final int[] SIDE_FACING = {1, -1, 2, -1, 3, -1, 0, -1};

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
        return blueprint(plan, "stone");
    }

    /** @param style the entrance's biome style, which picks the tower's design */
    public static Blueprint blueprint(DungeonPlan plan, String style) {
        int levels = plan.levels().size();
        Blueprint bp = new Blueprint(floorY(plan, levels - 1) - 4, TOP);
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
        Towers.build(bp, Towers.choose(style, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), 0x70E3L)),
                com.sablednah.crawlspace.plan.Dice.of(plan.seed(), 0x70E4L));
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

    static int floorAt(DungeonPlan plan, int i, int x, int z) {
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
        steps(bp, plan, i);
        triggers(bp, plan, i);
        java.util.Set<Integer> dark = Dresser.dress(bp, plan, i);
        lights(bp, plan, i, dark);
        encounters(bp, plan, i);
        ambushes(bp, plan, i, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0xA4B5L));
    }

    /**
     * A stair block wherever a corridor floor steps up one block, facing up
     * the step, so nobody has to jump anywhere in a dungeon.
     */
    private static void steps(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        int lim = LevelPlan.RADIUS - 1;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                if (level.cell(x, z) != Cell.CORRIDOR) {
                    continue;
                }
                int h = level.height(x, z);
                for (int d = 0; d < 4; d++) {
                    Cell n = level.cell(x + dirs[d][0], z + dirs[d][1]);
                    if (n.isWalkable() && level.height(x + dirs[d][0], z + dirs[d][1]) == h + 1) {
                        int f = floorAt(plan, i, x, z);
                        int code = bp.get(x, f, z);
                        if (code != 0 && Blueprint.part(code) == Part.AIR) {
                            bp.set(x, f, z, Part.STEP, d, i);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Each room's monsters, as a trigger the mod wakes when a player first
     * comes near: likelier in guard rooms and halls, never where you arrive,
     * always in a lair (its boss). They stand on floor the dressing left free,
     * away from doorways. The key is a block above the floor, so it never
     * collides with a trigger on the floor.
     */
    private static void encounters(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        com.sablednah.crawlspace.plan.Dice dice = com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0xE2C0L);
        for (Room r : level.rooms) {
            double chance = switch (r.role) {
                case ENTRY -> 0;
                case EXIT -> 0.5;
                case GUARD, LAIR -> 1;
                case HALL -> 0.85;
                case TREASURE, KEY -> 0.75;
                case SECRET -> 0.6;
                case SHRINE -> 0.4;
                default -> Math.min(0.95, 0.6 + 0.05 * i);
            };
            if (!dice.chance(chance)) {
                continue;
            }
            java.util.List<int[]> spots = new java.util.ArrayList<>();
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR || nearDoor(level, x, z)) {
                        continue;
                    }
                    int f = floorAt(plan, i, x, z);
                    int here = bp.get(x, f, z);
                    int above = bp.get(x, f + 1, z);
                    if ((here == 0 || Blueprint.part(here) == Part.AIR || Blueprint.part(here) == Part.CARPET
                            || Blueprint.part(here) == Part.MOSS) && (above == 0 || Blueprint.part(above) == Part.AIR)) {
                        spots.add(new int[] {x, f, z});
                    }
                }
            }
            if (spots.isEmpty()) {
                continue;
            }
            for (int k = spots.size() - 1; k > 0; k--) {
                int j = dice.nextInt(k + 1);
                int[] t = spots.get(k);
                spots.set(k, spots.get(j));
                spots.set(j, t);
            }
            int n = Math.min(spots.size(), r.role == Role.LAIR ? 4 + i / 2 : 2 + i / 2 + dice.nextInt(3));
            Trigger.Kind kind = r.role == Role.LAIR ? Trigger.Kind.BOSS : Trigger.Kind.ENCOUNTER;
            int f = floorAt(plan, i, r.centerX(), r.centerZ());
            bp.addTrigger(new Trigger(kind, r.centerX(), f + 1, r.centerZ(), i,
                    spots.subList(0, n).toArray(new int[0][])));
        }
    }

    /**
     * Ambushes: a group waiting partway along a long corridor, likelier deeper
     * down. They stand along the corridor's middle, on clear floor.
     */
    private static void ambushes(Blueprint bp, DungeonPlan plan, int i, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        for (com.sablednah.crawlspace.plan.Link l : level.links) {
            if (l.path == null || l.path.length < 14 || !dice.chance(Math.min(0.6, 0.25 + 0.04 * i))) {
                continue;
            }
            int mid = l.path.length / 2;
            java.util.List<int[]> spots = new java.util.ArrayList<>();
            for (int k = mid - 3; k <= mid + 3; k++) {
                int[] c = l.path[k];
                if (level.cell(c[0], c[1]) != Cell.CORRIDOR || nearDoor(level, c[0], c[1])) {
                    continue;
                }
                int f = floorAt(plan, i, c[0], c[1]);
                int here = bp.get(c[0], f, c[1]);
                int above = bp.get(c[0], f + 1, c[1]);
                if ((here == 0 || Blueprint.part(here) == Part.AIR || Blueprint.part(here) == Part.RAIL)
                        && (above == 0 || Blueprint.part(above) == Part.AIR)) {
                    spots.add(new int[] {c[0], f, c[1]});
                }
            }
            if (spots.size() < 2) {
                continue;
            }
            int n = Math.min(spots.size(), 1 + dice.nextInt(2) + i / 3);
            int[] c = l.path[mid];
            int key = floorAt(plan, i, c[0], c[1]) + 1;
            if (bp.triggerAt(c[0], key, c[1]) == null) {
                bp.addTrigger(new Trigger(Trigger.Kind.ENCOUNTER, c[0], key, c[1], i,
                        spots.subList(0, n).toArray(new int[0][])));
            }
        }
    }

    private static boolean nearDoor(LevelPlan level, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.cell(x + dx, z + dz).isDoor()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The lever for the level's locked doors, triggers on secret walls, and the
     * hidden trap tiles. The lever goes in the KEY room, as near its middle as
     * there is clear floor.
     */
    private static void triggers(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        java.util.List<int[]> locked = new java.util.ArrayList<>();
        int lim = LevelPlan.RADIUS;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                Cell c = level.cell(x, z);
                if (c == Cell.DOOR_LOCKED) {
                    locked.add(new int[] {x, floorAt(plan, i, x, z), z});
                } else if (c == Cell.DOOR_SECRET) {
                    int f = floorAt(plan, i, x, z);
                    int[][] both = {{x, f, z}, {x, f + 1, z}};
                    bp.addTrigger(new Trigger(Trigger.Kind.SECRET, x, f, z, i, both));
                    bp.addTrigger(new Trigger(Trigger.Kind.SECRET, x, f + 1, z, i, both));
                }
            }
        }
        Room key = level.roomWith(Role.KEY);
        if (key != null && !locked.isEmpty()) {
            int[] spot = clearFloorNear(level, key);
            if (spot != null) {
                int f = floorAt(plan, i, spot[0], spot[1]);
                bp.set(spot[0], f, spot[1], Part.LEVER, 0, i);
                bp.addTrigger(new Trigger(Trigger.Kind.LEVER, spot[0], f, spot[1], i, locked.toArray(new int[0][])));
            }
        }
        for (Room r : level.rooms) {
            if (r.role == Role.TREASURE) {
                int[] spot = clearFloorNear(level, r);
                if (spot != null) {
                    bp.addTrigger(new Trigger(Trigger.Kind.TREASURE, spot[0], floorAt(plan, i, spot[0], spot[1]), spot[1], i, new int[0][]));
                }
            }
        }
        // Its own stream, so adding plates moved nothing else in a dungeon.
        com.sablednah.crawlspace.plan.Dice look = com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x7A9F1L);
        for (int[] t : level.traps) {
            Cell c = level.cell(t[0], t[1]);
            if (c != Cell.FLOOR && c != Cell.CORRIDOR) {
                continue; // a pit or stair arrived on it afterwards
            }
            Trigger.Kind kind = t[2] == com.sablednah.crawlspace.plan.TrapKind.GAS.ordinal() ? Trigger.Kind.GAS : Trigger.Kind.DARTS;
            int[] at = flatSpot(bp, plan, i, t[0], t[1]);
            if (at == null) {
                continue; // no flat floor near it to hold a plate: a trap nobody could see is left out
            }
            int f = floorAt(plan, i, at[0], at[1]);
            bp.addTrigger(new Trigger(kind, at[0], f, at[1], i, new int[0][]));
            trapLook(bp, level, i, at[0], f, at[1], false, look);
        }
        decoys(bp, plan, i, look);
    }

    /**
     * A plate in a room, mostly a wire in a corridor. A trap on a step keeps
     * its stair and stays hidden: there is nowhere to put a plate.
     */
    private static void trapLook(Blueprint bp, LevelPlan level, int i, int x, int f, int z, boolean decoy,
            com.sablednah.crawlspace.plan.Dice look) {
        int code = bp.get(x, f, z);
        if (code != 0 && Blueprint.part(code) != Part.AIR) {
            return;
        }
        boolean wire = look.chance(level.cell(x, z) == Cell.CORRIDOR ? 0.65 : 0.15);
        Part part = decoy ? (wire ? Part.DECOY_WIRE : Part.DECOY_PLATE) : (wire ? Part.TRAP_WIRE : Part.TRAP_PLATE);
        bp.set(x, f, z, part, 0, i);
    }

    /**
     * Where a trap planned at (x, z) can show its plate: there, unless a
     * corridor step's stair took the cell, else the nearest clear flat cell
     * within three. Null if there is none, and the trap is dropped: every trap
     * has to be one a player could see.
     */
    private static int[] flatSpot(Blueprint bp, DungeonPlan plan, int i, int x, int z) {
        LevelPlan level = plan.levels().get(i);
        for (int d = 0; d <= 3; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    int cx = x + dx;
                    int cz = z + dz;
                    Cell c = level.cell(cx, cz);
                    if (c != Cell.FLOOR && c != Cell.CORRIDOR) {
                        continue;
                    }
                    int code = bp.get(cx, floorAt(plan, i, cx, cz), cz);
                    if (code != 0 && Blueprint.part(code) != Part.AIR) {
                        continue;
                    }
                    if (d == 0 || clearAround(bp, level, i, cx, cz)) {
                        return new int[] {cx, cz};
                    }
                }
            }
        }
        return null;
    }

    /**
     * Plates and wires that do nothing, about one per real trap: so a plate in
     * the floor is a question rather than an answer. Kept off doorways and
     * stairs, two cells from any trap, and off steps.
     */
    private static void decoys(Blueprint bp, DungeonPlan plan, int i, com.sablednah.crawlspace.plan.Dice look) {
        LevelPlan level = plan.levels().get(i);
        int want = Math.max(1, level.traps.size());
        int lim = LevelPlan.RADIUS - 2;
        for (int tries = 0; tries < 600 && want > 0; tries++) {
            int x = look.nextInt(2 * lim + 1) - lim;
            int z = look.nextInt(2 * lim + 1) - lim;
            Cell c = level.cell(x, z);
            if (c != Cell.FLOOR && c != Cell.CORRIDOR || !clearAround(bp, level, i, x, z)) {
                continue;
            }
            int f = floorAt(plan, i, x, z);
            int code = bp.get(x, f, z);
            if (code != 0 && Blueprint.part(code) != Part.AIR) {
                continue;
            }
            bp.addTrigger(new Trigger(Trigger.Kind.DECOY, x, f, z, i, new int[0][]));
            trapLook(bp, level, i, x, f, z, true, look);
            want--;
        }
    }

    /** No door, stair, pit or pool within one cell, and no trigger of this level within two. */
    private static boolean clearAround(Blueprint bp, LevelPlan level, int i, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Cell n = level.cell(x + dx, z + dz);
                if (n.isDoor() || n == Cell.STAIR_UP || n == Cell.STAIR_DOWN || n == Cell.PIT || n == Cell.POOL) {
                    return false;
                }
            }
        }
        for (Trigger t : bp.triggers()) {
            if (t.level() == i && Math.abs(t.x() - x) <= 2 && Math.abs(t.z() - z) <= 2) {
                return false;
            }
        }
        return true;
    }

    /** The floor cell nearest a room's centre, searching outward. */
    private static int[] clearFloorNear(LevelPlan level, Room r) {
        for (int d = 0; d <= Math.max(r.w, r.h); d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    int x = r.centerX() + dx;
                    int z = r.centerZ() + dz;
                    if (level.cell(x, z) == Cell.FLOOR && level.region(x, z) == r.id) {
                        return new int[] {x, z};
                    }
                }
            }
        }
        return null;
    }

    /** Lanterns: round the stairs always, in other rooms less often the deeper it gets. */
    private static void lights(Blueprint bp, DungeonPlan plan, int i, java.util.Set<Integer> dark) {
        LevelPlan level = plan.levels().get(i);
        double chance = Math.max(0.15, 0.7 - 0.07 * i);
        for (Room r : level.rooms) {
            int cx = r.centerX();
            int cz = r.centerZ();
            if (r.role == Role.ENTRY || r.role == Role.EXIT) {
                for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                    hang(bp, plan, i, cx + d[0], cz + d[1]);
                }
            } else if (!dark.contains(r.id) && unit(plan.seed(), i, r.id) < chance) {
                hang(bp, plan, i, cx, cz);
            }
        }
    }

    /** A lantern from the ceiling; in a tall room, a chandelier: a chain with the lantern below it. */
    private static void hang(Blueprint bp, DungeonPlan plan, int i, int x, int z) {
        LevelPlan level = plan.levels().get(i);
        if (level.cell(x, z) != Cell.FLOOR) {
            return;
        }
        int f = floorAt(plan, i, x, z);
        int h = clearHeight(level, x, z);
        if (h >= 6) {
            bp.set(x, f + h - 1, z, Part.CHAIN, 0, i);
            bp.set(x, f + h - 2, z, Part.LIGHT, 0, i);
        } else {
            bp.set(x, f + h - 1, z, Part.LIGHT, 0, i);
        }
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
        // A stair on each side and a landing in each corner, rising one block per side: four a turn,
        // so three blocks of headroom under the turn above. Every rise is a stair taken head on, so
        // nobody has to jump. The first version rose a block at every cell, corners included, where
        // a stair can face only one of the two ways you walk through it: nobody could climb it.
        for (int n = 0; n < 2 * (top - bottom); n++) {
            int k = n % RING.length;
            int y = bottom + n / 2;
            if (SIDE_FACING[k] >= 0) {
                bp.set(s[0] + RING[k][0], y, s[1] + RING[k][1], Part.STEP, SIDE_FACING[k], upperLevel);
            } else {
                bp.set(s[0] + RING[k][0], y, s[1] + RING[k][1], Part.LANDING, 0, upperLevel);
            }
        }
        bp.fill(s[0], s[1], bottom - 1, top + upperHeight - 1, Part.NEWEL, upperLevel);
        if (i > 0) {
            railing(bp, plan, i - 1, s, top, bottom);
        }
    }

    /**
     * A railing round the hole on the floor above, open only beside the top
     * step and landing, so nobody walks into the hole by accident. It goes in
     * only if a walk from its gap still reaches every part of the room: in a
     * cross-shaped room the railing cut two arms off from the gap. The tower
     * gets none, because its floor is only the ring round the hole. A fall
     * into any spiral lands on a step at most four blocks down.
     */
    private static void railing(Blueprint bp, DungeonPlan plan, int upperIndex, int[] s, int top, int bottom) {
        LevelPlan upper = plan.levels().get(upperIndex);
        int last = 2 * (top - bottom) - 1;
        int[] topLanding = RING[last % RING.length];
        int[] topStair = RING[(last - 1) % RING.length];
        java.util.Set<Long> rail = new java.util.HashSet<>();
        java.util.List<int[]> gap = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                boolean open = Math.max(Math.abs(dx - topLanding[0]), Math.abs(dz - topLanding[1])) <= 1
                        || Math.max(Math.abs(dx - topStair[0]), Math.abs(dz - topStair[1])) <= 1;
                if (open) {
                    gap.add(new int[] {s[0] + dx, s[1] + dz});
                } else {
                    rail.add(key(s[0] + dx, s[1] + dz));
                }
            }
        }
        // Walk the level from the gap, round the railing and the hole.
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int[] g : gap) {
            if (upper.cell(g[0], g[1]).isWalkable() && seen.add(key(g[0], g[1]))) {
                queue.add(g);
            }
        }
        int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : four) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                boolean hole = Math.abs(x - s[0]) <= 1 && Math.abs(z - s[1]) <= 1;
                if (!hole && upper.cell(x, z).isWalkable() && !rail.contains(key(x, z)) && seen.add(key(x, z))) {
                    queue.add(new int[] {x, z});
                }
            }
        }
        int room = upper.region(s[0], s[1]);
        Room r = upper.room(room);
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                boolean hole = Math.abs(x - s[0]) <= 1 && Math.abs(z - s[1]) <= 1;
                if (r.contains(x, z) && !hole && !rail.contains(key(x, z)) && upper.cell(x, z).isWalkable()
                        && !seen.contains(key(x, z))) {
                    return; // the railing would cut part of the room off from the stair: leave the hole open
                }
            }
        }
        for (long k : rail) {
            bp.set((int) (k >> 32), top, (int) k, Part.RAILING, 0, upperIndex);
        }
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
