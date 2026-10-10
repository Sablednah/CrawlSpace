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
    /**
     * How far the blueprint reaches below the bottom level's floor: its floor
     * block (3, with the heights' spread), and the finale arena sunk under it.
     */
    public static final int BELOW = 4 + 6;
    /** How far the finale arena's floor sinks below the lair's gallery. */
    static final int ARENA_DEPTH = 5;

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
        Blueprint bp = new Blueprint(floorY(plan, levels - 1) - BELOW, TOP);
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
                    case DOOR, ARCH, DOOR_LOCKED, DOOR_SECRET, DOOR_ONEWAY -> {
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
                                if (keyLock(plan, i)) {
                                    bp.fill(x, z, f, f + 1, Part.PORTCULLIS, i);
                                } else {
                                    bp.set(x, f, z, Part.LOCKED_LOWER, facing, i);
                                    bp.set(x, f + 1, z, Part.LOCKED_UPPER, facing, i);
                                }
                            }
                            case DOOR_SECRET -> bp.fill(x, z, f, f + 1, Part.SECRET_WALL, i);
                            case DOOR_ONEWAY -> {
                                bp.set(x, f, z, Part.ONEWAY_LOWER, facing, i);
                                bp.set(x, f + 1, z, Part.ONEWAY_UPPER, facing, i);
                            }
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
        puzzles(bp, plan, i);
        // The finale's pit is settled first, so no trigger, decoy or winch is put where it will be.
        ArenaPlan finale = null;
        if (i == plan.levels().size() - 1) {
            for (Room r : level.rooms) {
                if (r.role == Role.LAIR && finale == null) {
                    finale = arenaPlan(level, r);
                }
            }
            if (finale != null) {
                bp.markArena(i, finale.cells());
            }
        }
        triggers(bp, plan, i);
        java.util.Set<Integer> dark = Dresser.dress(bp, plan, i);
        windows(bp, plan, i, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x3E7DL));
        if (i == 0) {
            rumours(bp, plan);
        }
        tells(bp, plan, i, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x7E11L));
        lights(bp, plan, i, dark);
        if (finale != null) {
            arena(bp, plan, i, finale);
        }
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
                case PUZZLE -> 0; // the maze is the danger; a mob on the void would be sent nowhere
                default -> Math.min(0.95, 0.6 + 0.05 * i);
            };
            boolean crowded = level.feeling == com.sablednah.crawlspace.plan.Feeling.CROWDED && r.role != Role.ENTRY && r.role != Role.PUZZLE;
            if (crowded) {
                chance = Math.min(1, chance + 0.3);
            }
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
                    // Down to whatever there is to stand on: the finale's arena floor is below the gallery's.
                    while (f > bp.minY + 1 && bp.get(x, f - 1, z) != 0 && Blueprint.part(bp.get(x, f - 1, z)) == Part.AIR) {
                        f--;
                    }
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
            // In a finale arena they wait in the pit, the boss first, not on the gallery beside whoever walks in.
            java.util.List<int[]> pit = new java.util.ArrayList<>();
            for (int[] sp : spots) {
                if (bp.inArena(i, sp[0], sp[2])) {
                    pit.add(sp);
                }
            }
            if (!pit.isEmpty()) {
                spots = pit;
            }
            int n = Math.min(spots.size(), (r.role == Role.LAIR ? 4 + i / 2 : 2 + i / 2 + dice.nextInt(3)) + (crowded ? 2 : 0));
            // Its own dice, so no other room's encounter changes: some guard rooms, from level 2, are numbered.
            boolean ordered = r.role == Role.GUARD && i >= 1
                    && com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, r.id, 0x0DE5L).chance(ORDERED_CHANCE) && n >= 3;
            if (ordered) {
                n = Math.min(n, 4);
            }
            Trigger.Kind kind = r.role == Role.LAIR ? Trigger.Kind.BOSS : ordered ? Trigger.Kind.ORDERED : Trigger.Kind.ENCOUNTER;
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
                if (keyLock(plan, i)) {
                    // The key in a chest; each portcullis raised with it is its own trigger, on its lower bar.
                    bp.set(spot[0], f, spot[1], Part.KEY_CHEST, facingToward(spot, key), i);
                    // Kept clear and reachable like a hoard, and hinted like one.
                    bp.addTrigger(new Trigger(Trigger.Kind.TREASURE, spot[0], f, spot[1], i, new int[0][]));
                    for (int[] d : locked) {
                        bp.addTrigger(new Trigger(Trigger.Kind.PORTCULLIS, d[0], d[1], d[2], i,
                                new int[][] {{d[0], d[1], d[2]}, {d[0], d[1] + 1, d[2]}}));
                    }
                } else {
                    bp.set(spot[0], f, spot[1], Part.LEVER, 0, i);
                    bp.addTrigger(new Trigger(Trigger.Kind.LEVER, spot[0], f, spot[1], i, locked.toArray(new int[0][])));
                }
            }
        }
        onewayLevers(bp, plan, i);
        portcullisTrap(bp, plan, i, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x9C11L));
        for (Room r : level.rooms) {
            if (r.role == Role.TREASURE) {
                int[] spot = clearFloorNear(level, r);
                if (spot != null) {
                    bp.addTrigger(new Trigger(Trigger.Kind.TREASURE, spot[0], floorAt(plan, i, spot[0], spot[1]), spot[1], i, new int[0][]));
                }
                vault(bp, plan, i, r, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, r.id, 0x7A017L));
            }
        }
        // Its own stream, so adding plates moved nothing else in a dungeon.
        com.sablednah.crawlspace.plan.Dice look = com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x7A9F1L);
        // And pits theirs: a trap in a room may become a crumbling pit where there is room below for one.
        com.sablednah.crawlspace.plan.Dice pits = com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x917FL);
        for (int[] t : level.traps) {
            Cell c = level.cell(t[0], t[1]);
            if (c != Cell.FLOOR && c != Cell.CORRIDOR || nearWell(level, t[0], t[1])) {
                continue; // a pit or second stair arrived on it, or beside it, afterwards: its railing would cover the plate
            }
            Trigger.Kind kind = Trigger.Kind.valueOf(com.sablednah.crawlspace.plan.TrapKind.values()[t[2]].name());
            int[] at = flatSpot(bp, plan, i, t[0], t[1]);
            if (at == null) {
                continue; // no flat floor near it to hold a plate: a trap nobody could see is left out
            }
            int f = floorAt(plan, i, at[0], at[1]);
            if (level.cell(at[0], at[1]) == Cell.FLOOR && pits.chance(PIT_CHANCE) && pitfall(bp, level, i, at[0], f, at[1])) {
                continue;
            }
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
                    if (c != Cell.FLOOR && c != Cell.CORRIDOR || inPuzzle(level, cx, cz) || bp.inArena(i, cx, cz)) {
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

    /**
     * Each one-way door's lever: on the floor just inside its room, beside the
     * doorway, so whoever reaches the far side finds it at once. From the other
     * side the door is iron with nothing to pull.
     */
    private static void onewayLevers(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        for (Link l : level.links) {
            Room room = l.kind == com.sablednah.crawlspace.plan.LinkKind.ONEWAY
                    ? com.sablednah.crawlspace.plan.Planner.onewayRoom(level, l) : null;
            if (room == null) {
                continue;
            }
            int[] d = room == l.a ? l.doorA : l.doorB;
            int ix = d[0] - d[2];
            int iz = d[1] - d[3];
            int f = floorAt(plan, i, d[0], d[1]);
            // Beside the cell inside the doorway, on either hand; else that cell itself; else, where a pool
            // came down by the door, the room's floor nearest it.
            java.util.List<int[]> spots = new java.util.ArrayList<>(java.util.List.of(
                    new int[] {ix + d[3], iz + d[2]}, new int[] {ix - d[3], iz - d[2]}, new int[] {ix, iz}));
            java.util.List<int[]> rest = new java.util.ArrayList<>();
            for (int x = room.minX(); x <= room.maxX(); x++) {
                for (int z = room.minZ(); z <= room.maxZ(); z++) {
                    rest.add(new int[] {x, z});
                }
            }
            rest.sort(java.util.Comparator.comparingInt(c -> Math.abs(c[0] - ix) + Math.abs(c[1] - iz)));
            spots.addAll(rest);
            for (int[] s : spots) {
                int code = bp.get(s[0], f, s[1]);
                if (level.cell(s[0], s[1]) == Cell.FLOOR && level.region(s[0], s[1]) == room.id
                        && level.height(s[0], s[1]) == level.height(d[0], d[1]) && (code == 0 || Blueprint.part(code) == Part.AIR)) {
                    bp.set(s[0], f, s[1], Part.LEVER, facingOf(-d[2], -d[3]), i);
                    bp.addTrigger(new Trigger(Trigger.Kind.ONEWAY, s[0], f, s[1], i, new int[][] {{d[0], f, d[1]}}));
                    break;
                }
            }
        }
    }

    /**
     * The finale: the bottom level's lair as a sunken arena, the reason to
     * reach the bottom (Sable, 2026-10-09; Warhammer Quest's objective room,
     * the Fighting Pit). A gallery two cells wide runs round the walls at the
     * doorways' height; inside it the floor drops
     * {@link #ARENA_DEPTH} blocks, so the arena stands eleven high. Two straight
     * flights go down from opposite sides, and a second hoard waits at the
     * arena's far end. The lair's dressing is round its walls, on the gallery,
     * and stays; its boss wakes in the pit. A lair with pillars or a pool, or
     * too small for a flight and room to land, stays a plain lair.
     */
    /** A finale arena worked out: its room, the pit's cells, and its flights as {start x, z, dx, dz}. */
    record ArenaPlan(Room room, java.util.Set<Long> cells, java.util.List<int[]> flights) {
    }

    /** The arena a lair would take, or null if it is too small or has a pillar or pool where the pit would go. */
    static ArenaPlan arenaPlan(LevelPlan level, Room r) {
        java.util.Set<Long> arena = new java.util.HashSet<>();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                boolean inner = true;
                for (int dx = -2; dx <= 2 && inner; dx++) {
                    for (int dz = -2; dz <= 2 && inner; dz++) {
                        inner = r.contains(x + dx, z + dz);
                    }
                }
                if (inner) {
                    if (level.cell(x, z) != Cell.FLOOR) {
                        return null; // a pillar or a pool where the pit would go
                    }
                    arena.add(key(x, z));
                }
            }
        }
        // The flights: from a gallery cell straight into the arena, five steps down and at least two to land.
        java.util.List<int[]> runs = new java.util.ArrayList<>(); // {start x, z, dx, dz}
        for (int[] d : DIRS4) {
            int[] best = null;
            int bestOff = Integer.MAX_VALUE;
            for (long k : arena) {
                int x = (int) (k >> 32);
                int z = (int) k;
                if (arena.contains(key(x - d[0], z - d[1]))) {
                    continue; // not on the arena's edge facing this way
                }
                boolean fits = true;
                for (int n = 0; n < ARENA_DEPTH + 2 && fits; n++) {
                    fits = arena.contains(key(x + n * d[0], z + n * d[1]));
                }
                int off = Math.abs(d[0] == 0 ? x - r.centerX() : z - r.centerZ());
                if (fits && off < bestOff) {
                    bestOff = off;
                    best = new int[] {x, z, d[0], d[1]};
                }
            }
            if (best != null) {
                runs.add(best);
            }
        }
        // Two flights facing each other if there are, else whatever single one fits.
        java.util.List<int[]> chosen = new java.util.ArrayList<>();
        for (int[] a : runs) {
            for (int[] b : runs) {
                if (chosen.isEmpty() && a[2] == -b[2] && a[3] == -b[3] && a != b) {
                    chosen.add(a);
                    chosen.add(b);
                }
            }
        }
        if (chosen.isEmpty() && !runs.isEmpty()) {
            chosen.add(runs.get(0));
        }
        if (chosen.isEmpty() || arena.size() < 25) {
            return null;
        }
        return new ArenaPlan(r, arena, chosen);
    }

    private static void arena(Blueprint bp, DungeonPlan plan, int i, ArenaPlan a) {
        LevelPlan level = plan.levels().get(i);
        Room r = a.room();
        java.util.Set<Long> arena = a.cells();
        java.util.List<int[]> chosen = a.flights();
        int f = floorAt(plan, i, r.centerX(), r.centerZ());
        int h = clearHeight(level, r.centerX(), r.centerZ());
        int bottom = f - ARENA_DEPTH - 1; // the arena's floor block
        java.util.Set<Long> flight = new java.util.HashSet<>();
        for (int[] run : chosen) {
            for (int n = 0; n < ARENA_DEPTH; n++) {
                flight.add(key(run[0] + n * run[2], run[1] + n * run[3]));
            }
        }
        // The pit: arena floor at the bottom, air to the ceiling. The dressing's props in it go with it.
        for (long k : arena) {
            int x = (int) (k >> 32);
            int z = (int) k;
            bp.set(x, bottom, z, Part.FLOOR, 0, i);
            bp.fill(x, z, bottom + 1, f + h - 1, Part.AIR, i);
        }
        // The gallery stands on solid wall down to the arena floor.
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (r.contains(x, z) && !arena.contains(key(x, z))) {
                    bp.fill(x, z, bottom, f - 2, Part.WALL, i);
                }
            }
        }
        // The flights: each step a stair facing back up, on solid fill.
        for (int[] run : chosen) {
            int facing = facingOf(-run[2], -run[3]);
            for (int n = 0; n < ARENA_DEPTH; n++) {
                int x = run[0] + n * run[2];
                int z = run[1] + n * run[3];
                int y = f - 1 - n;
                if (y - 1 >= bottom) {
                    bp.fill(x, z, bottom, y - 1, Part.WALL, i);
                }
                bp.set(x, y, z, Part.STEP, facing, i);
            }
        }
        // No railing round the pit: the lair's furniture lines the gallery's outer ring, and a railing on
        // the inner one closed the gallery off. The fall is five blocks, about a heart, and sneaking stops it.
        // The finale's hoard: the arena cell furthest from the flights.
        long far = 0;
        int farD = -1;
        for (long k : arena) {
            if (flight.contains(k)) {
                continue;
            }
            int x = (int) (k >> 32);
            int z = (int) k;
            int d = Integer.MAX_VALUE;
            for (int[] run : chosen) {
                d = Math.min(d, Math.abs(x - run[0]) + Math.abs(z - run[1]));
            }
            if (d > farD) {
                farD = d;
                far = k;
            }
        }
        int hx = (int) (far >> 32);
        int hz = (int) far;
        bp.set(hx, bottom + 1, hz, Part.HOARD_CHEST, facingOf(Integer.signum(r.centerX() - hx), Integer.signum(r.centerZ() - hz)), i);
        bp.addTrigger(new Trigger(Trigger.Kind.TREASURE, hx, bottom + 1, hz, i, new int[0][]));
        hang(bp, plan, i, r.centerX(), r.centerZ());
    }

    /**
     * The rumour book's lectern: against a wall of the first level's entry room,
     * the room you arrive in, on floor the dressing left clear and away from
     * the doorways and the stair.
     */
    private static void rumours(Blueprint bp, DungeonPlan plan) {
        LevelPlan level = plan.levels().get(0);
        Room r = level.roomWith(Role.ENTRY);
        if (r == null) {
            return;
        }
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR || nearDoor(level, x, z)
                        || Math.max(Math.abs(x - r.centerX()), Math.abs(z - r.centerZ())) < 3) {
                    continue;
                }
                for (int[] d : DIRS4) {
                    if (level.cell(x + d[0], z + d[1]) != Cell.WALL) {
                        continue;
                    }
                    int f = floorAt(plan, 0, x, z);
                    int here = bp.get(x, f, z);
                    int above = bp.get(x, f + 1, z);
                    if ((here == 0 || Blueprint.part(here) == Part.AIR) && (above == 0 || Blueprint.part(above) == Part.AIR)
                            && bp.triggerAt(x, f, z) == null) {
                        bp.set(x, f, z, Part.RUMOURS, facingOf(-d[0], -d[1]), 0);
                        return;
                    }
                }
            }
        }
    }

    /** How often a trap has a tell beside it. Not every one: a delver who learns to read them should still be careful. */
    static final double TELL_CHANCE = 0.75;
    /** What a tell may stand on: plain floor, never a tile that does something. */
    private static final java.util.Set<Part> SOLID_FLOOR = java.util.EnumSet.of(Part.FLOOR, Part.CORRIDOR_FLOOR, Part.FLOOR_ACCENT,
            Part.FLOOR_INLAY, Part.MOSS_FLOOR);

    private static Part tellFor(Trigger.Kind kind) {
        return switch (kind) {
            case DARTS, ALARM -> Part.SKULL;
            case GAS -> Part.TELL_DEAD;
            case WEBS -> Part.COBWEB;
            case ROCKFALL -> Part.TELL_PEBBLE;
            case FROST -> Part.TELL_FROST;
            case FIRE -> Part.TELL_SCORCH;
            case SUMMON -> Part.TELL_SCULK;
            default -> null;
        };
    }

    /**
     * Telegraphed traps (Goblin Punch): beside most traps, on room floor
     * against a wall and never on the trap, something that says what it does.
     * Placed after dressing, into cells it left empty, on their own dice.
     */
    private static void tells(Blueprint bp, DungeonPlan plan, int i, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        for (Trigger t : bp.triggers()) {
            if (t.level() != i || !t.kind().isTrap()) {
                continue;
            }
            Part tell = tellFor(t.kind());
            if (tell == null || !dice.chance(TELL_CHANCE)) {
                continue;
            }
            search:
            for (int r = 1; r <= 2; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int x = t.x() + dx;
                        int z = t.z() + dz;
                        // A skull or a web is in the way, so only against a room's wall; the rest lie flat, anywhere.
                        boolean bulky = tell == Part.SKULL || tell == Part.COBWEB;
                        Cell c = level.cell(x, z);
                        if ((c != Cell.FLOOR && (bulky || c != Cell.CORRIDOR)) || nearDoor(level, x, z) || floorAt(plan, i, x, z) != t.y()) {
                            continue;
                        }
                        boolean wall = !bulky;
                        for (int[] d : DIRS4) {
                            wall |= level.cell(x + d[0], z + d[1]) == Cell.WALL;
                        }
                        int here = bp.get(x, t.y(), z);
                        int below = bp.get(x, t.y() - 1, z);
                        if (!wall || here != 0 && Blueprint.part(here) != Part.AIR || below == 0 || !SOLID_FLOOR.contains(Blueprint.part(below))
                                || bp.triggerAt(x, t.y(), z) != null) {
                            continue;
                        }
                        bp.set(x, t.y(), z, tell, dice.nextInt(4), i);
                        break search;
                    }
                }
            }
        }
    }

    /** How often a guard room from the second level down wakes numbered monsters, to be killed in order. */
    static final double ORDERED_CHANCE = 0.3;

    /** How often a treasure room from the second level down is a vault: take one of three. */
    static final double VAULT_CHANCE = 0.4;

    /**
     * A vault (Brogue's reward rooms; Pixel Dungeon's crystal choice): three
     * pedestals in a row along one wall, two apart, an item floating over each
     * once someone comes near. Take one and the others are caged. Needs three
     * floor cells by the wall, clear above, away from the doorways.
     */
    private static void vault(Blueprint bp, DungeonPlan plan, int i, Room r, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        if (i < 1 || !dice.chance(VAULT_CHANCE)) {
            return;
        }
        for (int[] d : DIRS4) {
            // Cells with this wall at their back: the room's edge facing d.
            java.util.List<int[]> row = new java.util.ArrayList<>();
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    if (r.contains(x, z) && level.cell(x, z) == Cell.FLOOR && level.cell(x + d[0], z + d[1]) == Cell.WALL
                            && !nearDoor(level, x, z)) {
                        int f = floorAt(plan, i, x, z);
                        int c = bp.get(x, f, z);
                        int c2 = bp.get(x, f + 1, z);
                        if ((c == 0 || Blueprint.part(c) == Part.AIR) && (c2 == 0 || Blueprint.part(c2) == Part.AIR)
                                && bp.triggerAt(x, f, z) == null) {
                            row.add(new int[] {x, z});
                        }
                    }
                }
            }
            // Three of them in a line, two apart.
            for (int[] a : row) {
                int sx = d[1] != 0 ? 2 : 0;
                int sz = d[0] != 0 ? 2 : 0;
                int[] b = null;
                int[] c = null;
                for (int[] o : row) {
                    if (o[0] == a[0] + sx && o[1] == a[1] + sz) {
                        b = o;
                    }
                    if (o[0] == a[0] + 2 * sx && o[1] == a[1] + 2 * sz) {
                        c = o;
                    }
                }
                if (b == null || c == null) {
                    continue;
                }
                int[][] three = {a, b, c};
                int[][] targets = new int[3][];
                for (int k = 0; k < 3; k++) {
                    targets[k] = new int[] {three[k][0], floorAt(plan, i, three[k][0], three[k][1]), three[k][1]};
                }
                for (int[] t : targets) {
                    bp.set(t[0], t[1], t[2], Part.PEDESTAL, 0, i);
                    bp.addTrigger(new Trigger(Trigger.Kind.VAULT, t[0], t[1], t[2], i, targets));
                }
                return;
            }
        }
    }

    /** How often a room worth seeing early gets a window on to a corridor that passes it. */
    static final double WINDOW_CHANCE = 0.65;

    /**
     * Foreshadowing (Dormans; Zelda): where a corridor passes three cells from
     * a lair, treasure, key or shrine room, the two wall blocks between them
     * become iron bars at eye level. You see the room long before the way in.
     * Only through straight wall, floor level on both sides, away from
     * doorways, and from a corridor that is not one of the room's own. Run
     * after the dressing, so no shelf or panelling takes the slot.
     */
    private static void windows(Blueprint bp, DungeonPlan plan, int i, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        for (Room r : level.rooms) {
            boolean worth = switch (r.role) {
                case LAIR, TREASURE, KEY, SHRINE -> true;
                default -> false;
            };
            if (!worth || !dice.chance(WINDOW_CHANCE)) {
                continue;
            }
            java.util.List<int[]> fits = new java.util.ArrayList<>(); // {x, z, dx, dz}: a room cell and the way out
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR) {
                        continue;
                    }
                    for (int[] d : DIRS4) {
                        if (window(level, r, x, z, d[0], d[1]) && inFront(bp, plan, i, x, z, d[0], d[1])) {
                            fits.add(new int[] {x, z, d[0], d[1]});
                        }
                    }
                }
            }
            if (fits.isEmpty()) {
                continue;
            }
            int[] w = fits.get(dice.nextInt(fits.size()));
            int f = floorAt(plan, i, w[0], w[1]);
            // Two wide where the wall beside it will take a second.
            int sx = w[3];
            int sz = w[2];
            boolean wide = window(level, r, w[0] + sx, w[1] + sz, w[2], w[3]) && inFront(bp, plan, i, w[0] + sx, w[1] + sz, w[2], w[3]);
            for (int k = 0; k <= (wide ? 1 : 0); k++) {
                int x = w[0] + k * sx;
                int z = w[1] + k * sz;
                for (int step = 1; step <= 2; step++) {
                    bp.set(x + step * w[2], f + 1, z + step * w[3], Part.WINDOW_BARS, 0, i);
                }
                // Whatever hung on the wall that is now bars comes down with it.
                for (int step : new int[] {0, 3}) {
                    int code = bp.get(x + step * w[2], f + 1, z + step * w[3]);
                    if (code != 0 && Blueprint.part(code) != Part.AIR) {
                        bp.set(x + step * w[2], f + 1, z + step * w[3], Part.AIR, 0, i);
                    }
                }
            }
        }
    }

    /** Things hung on a wall: a window may go where one of these is, and takes it down. */
    private static boolean onTheWall(Part p) {
        return switch (p) {
            case BANNER, WALL_TORCH, VINE, CHAIN, ROOTS -> true;
            default -> false;
        };
    }

    /** Whether the cells either side of a window, at eye level, are clear or hold only something hung on the wall. */
    private static boolean inFront(Blueprint bp, DungeonPlan plan, int i, int x, int z, int dx, int dz) {
        int f = floorAt(plan, i, x, z);
        for (int step : new int[] {0, 3}) {
            int code = bp.get(x + step * dx, f + 1, z + step * dz);
            if (code != 0 && Blueprint.part(code) != Part.AIR && !onTheWall(Blueprint.part(code))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a window fits from room cell (x, z) going (dx, dz): two wall
     * cells, then corridor, all at the room's floor height, the wall straight
     * either side, and no doorway, stair or pit within two of either end.
     */
    private static boolean window(LevelPlan level, Room r, int x, int z, int dx, int dz) {
        if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR) {
            return false;
        }
        int h = level.height(x, z);
        int cx = x + 3 * dx;
        int cz = z + 3 * dz;
        if (level.cell(x + dx, z + dz) != Cell.WALL || level.cell(x + 2 * dx, z + 2 * dz) != Cell.WALL
                || level.cell(cx, cz) != Cell.CORRIDOR || level.height(cx, cz) != h) {
            return false;
        }
        for (int step = 1; step <= 2; step++) {
            int wx = x + step * dx;
            int wz = z + step * dz;
            if (level.cell(wx + dz, wz + dx) != Cell.WALL || level.cell(wx - dz, wz - dx) != Cell.WALL) {
                return false;
            }
        }
        for (int[] end : new int[][] {{x, z}, {cx, cz}}) {
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    Cell n = level.cell(end[0] + ox, end[1] + oz);
                    if (n.isDoor() || n == Cell.STAIR_UP || n == Cell.STAIR_DOWN || n == Cell.PIT) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** How often a level's lock is a portcullis with a key, rather than an iron door with a lever. */
    static final double KEY_LOCK_CHANCE = 0.5;
    /** How often a level from the second down has a portcullis that drops behind you. */
    static final double PORTCULLIS_TRAP_CHANCE = 0.4;

    /** Whether level {@code i}'s lock is a keyed portcullis. Its own dice: it moves nothing else. */
    public static boolean keyLock(DungeonPlan plan, int i) {
        return com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, 0x6E70L).chance(KEY_LOCK_CHANCE);
    }

    /** A facing from a spot toward a room's centre, for a chest that should open toward the room. */
    private static int facingToward(int[] spot, Room r) {
        int dx = r.centerX() - spot[0];
        int dz = r.centerZ() - spot[1];
        if (dx == 0 && dz == 0) {
            return 2;
        }
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
    }

    /**
     * A portcullis that drops behind you (Sable, 2026-10-09: "if it drops after
     * walking through it"): over an archway into a room that has another way
     * out, so it bars the way back, never the way on. The floor under the arch
     * is a sill, scored where the bars land: the tell. Walking two cells in
     * drops it; a winch lever further inside raises it, and so, in the end,
     * does time. A blocked retreat, in one doorway.
     */
    private static void portcullisTrap(Blueprint bp, DungeonPlan plan, int i, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        if (i < 1 || !dice.chance(PORTCULLIS_TRAP_CHANCE)) {
            return;
        }
        java.util.Map<Room, Integer> doorways = new java.util.HashMap<>();
        java.util.List<int[]> arches = new java.util.ArrayList<>(); // {door x, z, normal x, z, room id}
        for (Link l : level.links) {
            for (int side = 0; side < 2; side++) {
                int[] d = side == 0 ? l.doorA : l.doorB;
                Room r = side == 0 ? l.a : l.b;
                if (d == null) {
                    continue;
                }
                doorways.merge(r, 1, Integer::sum);
                if (level.cell(d[0], d[1]) == Cell.ARCH) {
                    arches.add(new int[] {d[0], d[1], d[2], d[3], r.id});
                }
            }
        }
        java.util.List<int[]> fits = new java.util.ArrayList<>();
        for (int[] a : arches) {
            Room r = level.room(a[4]);
            boolean role = switch (r.role) {
                case ROOM, HALL, GUARD, LAIR -> true;
                default -> false;
            };
            int tx = a[0] - 2 * a[2];
            int tz = a[1] - 2 * a[3];
            int mx = a[0] - a[2];
            int mz = a[1] - a[3];
            if (role && doorways.getOrDefault(r, 0) >= 2
                    && level.cell(tx, tz) == Cell.FLOOR && level.region(tx, tz) == r.id
                    && level.cell(mx, mz) == Cell.FLOOR && level.region(mx, mz) == r.id
                    && level.height(tx, tz) == level.height(a[0], a[1]) && level.height(mx, mz) == level.height(a[0], a[1])
                    && clearAround(bp, level, i, tx, tz)) {
                fits.add(a);
            }
        }
        if (fits.isEmpty()) {
            return;
        }
        int[] a = fits.get(dice.nextInt(fits.size()));
        Room r = level.room(a[4]);
        int f = floorAt(plan, i, a[0], a[1]);
        int[][] bars = {{a[0], f, a[1]}, {a[0], f + 1, a[1]}, {a[0], f + 2, a[1]}};
        bp.set(a[0], f - 1, a[1], Part.PORTCULLIS_SILL, 0, i);
        bp.fill(a[0], a[1], f, f + 2, Part.PORTCULLIS_GAP, i);
        int tx = a[0] - 2 * a[2];
        int tz = a[1] - 2 * a[3];
        bp.addTrigger(new Trigger(Trigger.Kind.PORTCULLIS_TRAP, tx, floorAt(plan, i, tx, tz), tz, i, bars));
        // The winch: floor as near the room's middle as there is, well away from the arch.
        int[] winch = null;
        for (int d = 0; d <= Math.max(r.w, r.h) && winch == null; d++) {
            for (int dx = -d; dx <= d && winch == null; dx++) {
                for (int dz = -d; dz <= d && winch == null; dz++) {
                    int x = r.centerX() + dx;
                    int z = r.centerZ() + dz;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && level.cell(x, z) == Cell.FLOOR && level.region(x, z) == r.id
                            && Math.max(Math.abs(x - a[0]), Math.abs(z - a[1])) >= 4 && clearAround(bp, level, i, x, z)) {
                        winch = new int[] {x, z};
                    }
                }
            }
        }
        if (winch != null) {
            int wf = floorAt(plan, i, winch[0], winch[1]);
            bp.set(winch[0], wf, winch[1], Part.WINCH, facingToward(winch, r), i);
            bp.addTrigger(new Trigger(Trigger.Kind.WINCH, winch[0], wf, winch[1], i, bars));
        }
    }

    /** How often a trap in a room becomes a crumbling pit, where one fits. */
    static final double PIT_CHANCE = 0.5;

    /**
     * A pit trap: a 3x3 patch of floor tiles that crumble away under whoever
     * stands on them, over a pit four deep with dripstone spikes, walled in
     * so it cannot open into a cave. Four, because from three the fall onto
     * the spikes cost one point of health on the rig; from four it is about
     * three hearts. Needs the 5x5 round it to be room floor, and nothing of
     * the dungeon's in the six layers below. Returns false, changing nothing,
     * where it does not fit.
     */
    private static boolean pitfall(Blueprint bp, LevelPlan level, int i, int cx, int f, int cz) {
        if (f - 6 < bp.minY) {
            return false; // the bottom of the world, or of the blueprint, is too near
        }
        int region = level.region(cx, cz);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (level.cell(x, z) != Cell.FLOOR || level.region(x, z) != region || level.height(x, z) != level.height(cx, cz)) {
                    return false;
                }
                int top = bp.get(x, f, z);
                if (top != 0 && Blueprint.part(top) != Part.AIR) {
                    return false;
                }
                int under = bp.get(x, f - 1, z);
                if (under == 0 || Blueprint.part(under) != Part.FLOOR) {
                    return false;
                }
                for (int y = f - 6; y <= f - 2; y++) {
                    if (bp.get(x, y, z) != 0) {
                        return false;
                    }
                }
            }
        }
        int[][] tiles = new int[9][];
        int k = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    bp.set(x, f - 1, z, Part.PIT_TILE, 0, i);
                    bp.set(x, f - 2, z, Part.AIR, 0, i);
                    bp.set(x, f - 3, z, Part.AIR, 0, i);
                    bp.set(x, f - 4, z, Part.STALAGMITE, 0, i);
                    bp.set(x, f - 5, z, Part.FLOOR, 0, i);
                    tiles[k++] = new int[] {x, f, z};
                } else {
                    bp.fill(x, z, f - 5, f - 2, Part.WALL, i);
                }
            }
        }
        bp.addTrigger(new Trigger(Trigger.Kind.PIT, cx, f, cz, i, tiles));
        for (int[] t : tiles) {
            if (t[0] != cx || t[2] != cz) {
                bp.addTrigger(new Trigger(Trigger.Kind.PIT_EDGE, t[0], f, t[2], i, new int[][] {{cx, f, cz}}));
            }
        }
        return true;
    }

    /** Whether a stair or pit cell is within two of (x, z): where a railing or a shaft's rim goes. */
    private static boolean nearWell(LevelPlan level, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Cell n = level.cell(x + dx, z + dz);
                if (n == Cell.STAIR_UP || n == Cell.STAIR_DOWN || n == Cell.PIT) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether (x, z) is in a puzzle room: no trap, decoy or prop belongs there. */
    static boolean inPuzzle(LevelPlan level, int x, int z) {
        Room r = level.room(level.region(x, z));
        return r != null && r.role == Role.PUZZLE;
    }

    /**
     * Each puzzle room's maze: a random spanning tree over every other cell,
     * grown from the centre, so every path cell is reachable from it and every
     * branch that is not on the way somewhere is a dead end. Each doorway is
     * joined to the tree by the shortest way in, and the deepest dead end left
     * holds the hoard chest. Every floor cell not on a path becomes the void,
     * one block down, where the floor block was, so it reads as sunken.
     */
    private static void puzzles(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        for (Room r : level.rooms) {
            if (r.role == Role.PUZZLE) {
                maze(bp, plan, i, r, com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, r.id, 0x3A2EL));
            }
        }
    }

    private static final int[][] DIRS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** How often a puzzle room from the second level down is an ice board instead of a maze. */
    static final double ICE_CHANCE = 0.3;

    /**
     * An ice board (Hypixel's Ice Fill): a rectangle of ice in the middle of the
     * room, two cells in from every edge, at most 5 by 4 and always an even
     * number of tiles, so it can be crossed once each from any tile
     * (BlueprintTest proves it for every size used). The hoard appears beyond
     * it when it is solved. Returns false, changing nothing, where it does not fit.
     */
    private static boolean iceBoard(Blueprint bp, DungeonPlan plan, int i, Room r) {
        LevelPlan level = plan.levels().get(i);
        int bw = Math.min(r.w - 4, 5);
        int bh = Math.min(r.h - 4, 4);
        if (bw * bh % 2 == 1) {
            bw--;
        }
        if (bw < 3 || bh < 3) {
            return false;
        }
        int x0 = r.centerX() - bw / 2;
        int z0 = r.centerZ() - bh / 2;
        int f = floorAt(plan, i, r.centerX(), r.centerZ());
        for (int x = x0; x < x0 + bw; x++) {
            for (int z = z0; z < z0 + bh; z++) {
                if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR || floorAt(plan, i, x, z) != f) {
                    return false;
                }
            }
        }
        int rx = r.centerX();
        int rz = z0 + bh + 1;
        if (!r.contains(rx, rz) || level.cell(rx, rz) != Cell.FLOOR) {
            return false;
        }
        int[][] targets = new int[bw * bh + 1][];
        targets[0] = new int[] {rx, f, rz};
        int k = 1;
        for (int x = x0; x < x0 + bw; x++) {
            for (int z = z0; z < z0 + bh; z++) {
                bp.set(x, f - 1, z, Part.ICE_TILE, 0, i);
                targets[k++] = new int[] {x, f - 1, z};
            }
        }
        bp.addTrigger(new Trigger(Trigger.Kind.ICE_BOARD, r.centerX(), f + 2, r.centerZ(), i, targets));
        return true;
    }

    private static void maze(Blueprint bp, DungeonPlan plan, int i, Room r, com.sablednah.crawlspace.plan.Dice dice) {
        LevelPlan level = plan.levels().get(i);
        // Its own dice, so the mazes of rooms that stay mazes are as they were.
        if (i >= 1 && com.sablednah.crawlspace.plan.Dice.of(plan.seed(), i, r.id, 0x1CEL).chance(ICE_CHANCE) && iceBoard(bp, plan, i, r)) {
            return;
        }
        int cx = r.centerX();
        int cz = r.centerZ();
        java.util.function.BiPredicate<Integer, Integer> floor = (x, z) -> r.contains(x, z) && level.cell(x, z) == Cell.FLOOR;
        java.util.Set<Long> path = new java.util.HashSet<>();
        java.util.Map<Long, Integer> depth = new java.util.HashMap<>();
        java.util.Map<Long, Long> parent = new java.util.HashMap<>();
        // The cells between two junctions: a straight run of three, where a gap can go.
        java.util.Set<Long> mids = new java.util.HashSet<>();
        // Depth-first, so the paths wind; a stack rather than recursion, so a big room cannot overflow.
        java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[] {cx, cz});
        path.add(key(cx, cz));
        depth.put(key(cx, cz), 0);
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            java.util.List<int[]> next = new java.util.ArrayList<>();
            for (int[] d : DIRS4) {
                int nx = c[0] + 2 * d[0];
                int nz = c[1] + 2 * d[1];
                if (floor.test(nx, nz) && floor.test(c[0] + d[0], c[1] + d[1]) && !path.contains(key(nx, nz))) {
                    next.add(new int[] {nx, nz, d[0], d[1]});
                }
            }
            if (next.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] n = next.get(dice.nextInt(next.size()));
            path.add(key(c[0] + n[2], c[1] + n[3]));
            mids.add(key(c[0] + n[2], c[1] + n[3]));
            path.add(key(n[0], n[1]));
            depth.put(key(n[0], n[1]), depth.get(key(c[0], c[1])) + 2);
            parent.put(key(n[0], n[1]), key(c[0], c[1]));
            stack.push(new int[] {n[0], n[1]});
        }
        // Every way in: a room cell beside a walkable cell outside the room. Joined by the shortest way.
        java.util.Set<Long> entrance = new java.util.HashSet<>();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!floor.test(x, z)) {
                    continue;
                }
                for (int[] d : DIRS4) {
                    int ox = x + d[0];
                    int oz = z + d[1];
                    if (!r.contains(ox, oz) && level.cell(ox, oz).isWalkable()) {
                        entrance.addAll(joinPath(x, z, path, floor));
                    }
                }
            }
        }
        path.addAll(entrance);
        // The chest: the deepest node of the tree that is a dead end and not on a way in.
        long chest = Long.MIN_VALUE;
        int best = -1;
        for (java.util.Map.Entry<Long, Integer> e : depth.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 32);
            int z = (int) k;
            int ways = 0;
            for (int[] d : DIRS4) {
                ways += path.contains(key(x + d[0], z + d[1])) ? 1 : 0;
            }
            if (ways == 1 && !entrance.contains(k) && e.getValue() > best) {
                best = e.getValue();
                chest = k;
            }
        }
        int f = floorAt(plan, i, cx, cz);
        // Which puzzle. Deeper levels may be a leap of faith (from level 3), and an ordinary maze
        // grows gaps to jump (from level 2), crumbling blocks and dripleaf (from level 2).
        // The void sits a block below the paths, with air above it, wherever there is room for it: a
        // player standing at a path's edge has their centre over the next column, and with the void
        // right there that sent them back (Sable could not cross a room). Otherwise the void takes the
        // floor block's place, as at first.
        boolean sunk = f - 3 >= bp.minY && roomFree(bp, r, floor, f - 3, f - 2);
        boolean leap = i >= 2 && dice.chance(LEAP_CHANCE) && sunk;
        double gaps = leap ? 0 : Math.min(0.35, 0.08 * (i - 0.5));
        double crumble = leap ? 0 : Math.min(0.25, 0.06 * (i - 0.5));
        double leaves = leap ? 0 : Math.min(0.15, 0.04 * (i - 0.5));
        long chestKey = chest;
        java.util.function.Predicate<Long> fixed = k -> {
            int x = (int) (k >> 32);
            int z = (int) (long) k;
            // The centre, its four neighbours, the ways in, the chest and the cells beside it stay plain
            // stone: a gap beside the chest would leave nowhere to stand and open it.
            int chx = (int) (chestKey >> 32);
            int chz = (int) chestKey;
            return entrance.contains(k) || Math.abs(x - cx) + Math.abs(z - cz) <= 1
                    || chestKey != Long.MIN_VALUE && Math.abs(x - chx) + Math.abs(z - chz) <= 1;
        };
        for (long k : mids) {
            if (!fixed.test(k) && dice.chance(gaps)) {
                path.remove(k);
            }
        }
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!floor.test(x, z)) {
                    continue;
                }
                long k = key(x, z);
                if (x == cx && z == cz) {
                    bp.set(x, f - 1, z, Part.RESTART, 0, i);
                    if (leap) {
                        bp.set(x, f - 2, z, Part.VOID, 0, i);
                        bp.set(x, f - 3, z, Part.FLOOR, 0, i);
                    } else if (sunk) {
                        bp.set(x, f - 2, z, Part.FLOOR, 0, i);
                    }
                } else if (leap) {
                    // Everything shows void, a block lower; the path is invisible over it.
                    bp.set(x, f - 1, z, path.contains(k) ? Part.PATH_HIDDEN : Part.AIR, 0, i);
                    bp.set(x, f - 2, z, Part.VOID, 0, i);
                    bp.set(x, f - 3, z, Part.FLOOR, 0, i);
                } else if (!path.contains(k)) {
                    if (sunk) {
                        bp.set(x, f - 1, z, Part.AIR, 0, i);
                        bp.set(x, f - 2, z, Part.VOID, 0, i);
                        bp.set(x, f - 3, z, Part.FLOOR, 0, i);
                    } else {
                        bp.set(x, f - 1, z, Part.VOID, 0, i);
                    }
                } else if (!fixed.test(k) && dice.chance(crumble)) {
                    bp.set(x, f - 1, z, Part.CRUMBLE, 0, i);
                    if (sunk) {
                        bp.set(x, f - 2, z, Part.FLOOR, 0, i);
                    }
                } else if (!fixed.test(k) && f - 2 >= bp.minY && roomFree(bp, x, z, f - 2) && dice.chance(leaves)) {
                    int facing = 0;
                    for (int d = 0; d < 4; d++) {
                        if (path.contains(key(x + DIRS4[d][0], z + DIRS4[d][1]))) {
                            facing = com.sablednah.crawlspace.build.Dresser.facingOf(DIRS4[d][0], DIRS4[d][1]);
                        }
                    }
                    bp.set(x, f - 1, z, Part.DRIPLEAF, facing, i);
                    bp.set(x, f - 2, z, Part.MOSS_FLOOR, 0, i);
                } else if (sunk) {
                    bp.set(x, f - 2, z, Part.FLOOR, 0, i); // under a plain path block, beside the sunken void
                }
            }
        }
        int[][] bounds = {{r.minX(), f, r.minZ()}, {r.maxX(), f, r.maxZ()}};
        bp.addTrigger(new Trigger(Trigger.Kind.PUZZLE, cx, f, cz, i, bounds));
        if (chest != Long.MIN_VALUE) {
            int x = (int) (chest >> 32);
            int z = (int) chest;
            long from = parent.getOrDefault(chest, key(cx, cz));
            int facing = com.sablednah.crawlspace.build.Dresser.facingOf((int) (from >> 32) - x, (int) from - z);
            bp.set(x, floorAt(plan, i, x, z), z, Part.HOARD_CHEST, facing, i);
        }
    }

    /** How often a puzzle room from level 3 down is a leap of faith. */
    static final double LEAP_CHANCE = 0.35;

    /** Whether the dungeon has set nothing under the room's floor in layers y0..y1. */
    private static boolean roomFree(Blueprint bp, Room r, java.util.function.BiPredicate<Integer, Integer> floor, int y0, int y1) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (floor.test(x, z)) {
                    for (int y = y0; y <= y1; y++) {
                        if (bp.get(x, y, z) != 0) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean roomFree(Blueprint bp, int x, int z, int y) {
        return bp.get(x, y, z) == 0;
    }

    /** The cells from (x, z) to the nearest path cell, through room floor, breadth first; empty if none. */
    private static java.util.List<Long> joinPath(int x, int z, java.util.Set<Long> path,
            java.util.function.BiPredicate<Integer, Integer> floor) {
        java.util.Map<Long, Long> came = new java.util.HashMap<>();
        java.util.ArrayDeque<long[]> q = new java.util.ArrayDeque<>();
        long start = key(x, z);
        q.add(new long[] {x, z});
        came.put(start, start);
        while (!q.isEmpty()) {
            long[] c = q.poll();
            long k = key((int) c[0], (int) c[1]);
            if (path.contains(k)) {
                java.util.List<Long> out = new java.util.ArrayList<>();
                for (long at = k; at != start; at = came.get(at)) {
                    out.add(at);
                }
                out.add(start);
                return out;
            }
            for (int[] d : DIRS4) {
                int nx = (int) c[0] + d[0];
                int nz = (int) c[1] + d[1];
                long nk = key(nx, nz);
                if (!came.containsKey(nk) && floor.test(nx, nz)) {
                    came.put(nk, k);
                    q.add(new long[] {nx, nz});
                }
            }
        }
        return java.util.List.of();
    }

    /** No door, stair, pit or pool within one cell, and no trigger of this level within two. */
    private static boolean clearAround(Blueprint bp, LevelPlan level, int i, int x, int z) {
        if (inPuzzle(level, x, z) || bp.inArena(i, x, z)) {
            return false;
        }
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
        // A dark level has light only where the stairs are.
        double chance = level.feeling == com.sablednah.crawlspace.plan.Feeling.DARK ? 0 : Math.max(0.15, 0.7 - 0.07 * i);
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
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                boolean hole = Math.abs(x - s[0]) <= 1 && Math.abs(z - s[1]) <= 1;
                // Every part of the room, and every doorway into it: a second stair dropped beside a small
                // room's door once railed the doorway off from the room it opened into (seed 14, level 1).
                boolean mine = r.contains(x, z) || upper.cell(x, z).isDoor();
                if (mine && !hole && !rail.contains(key(x, z)) && upper.cell(x, z).isWalkable()
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
