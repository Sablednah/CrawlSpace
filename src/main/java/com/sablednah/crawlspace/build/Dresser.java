package com.sablednah.crawlspace.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Link;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;

/**
 * Turns stone boxes into rooms: props by role and theme, wall trim, and
 * corridor fittings. Everything is a {@link Part}; the palette decides the
 * blocks.
 *
 * <p>Props never land within two cells of a doorway, on a stair or pit's
 * five-by-five (a stair down's seven-by-seven, for its railing), or on a trigger. A room's props are only committed if every
 * doorway, the stair and any lever or hoard are still reachable from the
 * first doorway round them; otherwise the room is left plain. Corridor
 * fittings sit against walls and leave the middle of the corridor clear.</p>
 */
final class Dresser {

    private static final int R = LevelPlan.RADIUS;
    /** Directions in facing order: 0 north, 1 east, 2 south, 3 west. */
    static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private record Prop(int x, int y, int z, Part part, int facing, boolean blocks) {
    }

    private Dresser() {
    }

    /**
     * Dresses level {@code i}. Returns the ids of rooms that must stay dark
     * (they hold a spawner, which only works without light).
     */
    static Set<Integer> dress(Blueprint bp, DungeonPlan plan, int i) {
        LevelPlan level = plan.levels().get(i);
        Dice dice = Dice.of(plan.seed(), i, 0xD2E55L);
        boolean[][] reserved = reserved(bp, level, i);
        Set<Integer> dark = new HashSet<>();
        String theme = level.theme.name();
        boolean built = !theme.equals("Old Mines") && !theme.equals("Caverns");

        for (Room r : level.rooms) {
            if (r.role == com.sablednah.crawlspace.plan.Role.PUZZLE) {
                continue; // the maze is the whole room: nothing goes on the paths, and nothing on the void
            }
            List<Prop> props = new ArrayList<>();
            List<int[]> perim = perimeter(level, r, reserved, dice);
            int f = Blueprinter.floorAt(plan, i, r.centerX(), r.centerZ());
            int h = Blueprinter.clearHeight(level, r.centerX(), r.centerZ());
            boolean centreFree = centreFree(level, r, reserved);
            int[] centre = {r.centerX(), r.centerZ()};
            boolean spawner = false;

            switch (r.role) {
                case ENTRY, EXIT -> {
                    take(perim, 2, p -> props.add(banner(p, f)));
                    if (built) {
                        take(perim, 2, p -> props.add(floor(p, f, Part.CANDLES, dice.nextInt(3), true)));
                    }
                }
                case TREASURE -> {
                    take(perim, 2, p -> props.add(floor(p, f, Part.POT, 0, true)));
                    Trigger hoard = find(bp, Trigger.Kind.TREASURE, i, r);
                    if (hoard != null) {
                        props.add(new Prop(hoard.x(), hoard.y(), hoard.z(), Part.HOARD_CHEST, dice.nextInt(4), true));
                    }
                    take(perim, 1, p -> props.add(floor(p, f, Part.CHEST, p[2], true)));
                    take(perim, 3, p -> props.add(floor(p, f, Part.CANDLES, 1 + dice.nextInt(3), true)));
                }
                case SECRET -> {
                    take(perim, 1, p -> props.add(floor(p, f, Part.CHEST, p[2], true)));
                    take(perim, 2, p -> props.add(floor(p, f, Part.COBWEB, 0, true)));
                    take(perim, 1, p -> props.add(floor(p, f, Part.SKULL, dice.nextInt(4), true)));
                }
                case LAIR -> {
                    take(perim, 1, p -> props.add(floor(p, f, Part.HOARD_CHEST, p[2], true)));
                    take(perim, 1, p -> props.add(floor(p, f, Part.THRONE, (p[2] + 2) % 4, true)));
                    take(perim, 3, p -> props.add(floor(p, f, Part.BONES, 0, true)));
                    take(perim, 3, p -> props.add(floor(p, f, Part.SKULL, dice.nextInt(4), true)));
                    take(perim, 3, p -> props.add(floor(p, f, Part.COBWEB, 0, true)));
                    take(perim, 2, p -> props.add(banner(p, f)));
                }
                case SHRINE -> {
                    take(perim, 2, p -> props.add(floor(p, f, Part.POT, 0, true)));
                    if (!r.pool && centreFree) {
                        props.add(new Prop(centre[0], f, centre[1], Part.ALTAR, 0, true));
                        props.add(new Prop(centre[0], f + 1, centre[1], Part.CANDLES, 2 + dice.nextInt(2), false));
                    } else {
                        // Every shrine has its altar, the thing to use: a pooled one's stands by the wall.
                        take(perim, 1, p -> props.add(floor(p, f, Part.ALTAR, 0, true)));
                    }
                    take(perim, 4, p -> props.add(floor(p, f, Part.CANDLES, dice.nextInt(4), true)));
                    take(perim, 1, p -> props.add(banner(p, f)));
                }
                case GUARD -> {
                    take(perim, 2 + dice.nextInt(2), p -> props.add(floor(p, f, Part.BARREL, 0, true)));
                    if (centreFree) {
                        tableSet(centre, f, dice, props); // the guards' mess table
                    }
                    take(perim, 1, p -> props.add(banner(p, f)));
                }
                case HALL -> {
                    aisle(level, r, f, reserved, props);
                    take(perim, 3, p -> props.add(banner(p, f)));
                }
                case KEY -> take(perim, 2, p -> props.add(floor(p, f, Part.CANDLES, dice.nextInt(4), true)));
                default -> flavour(theme, level, r, f, h, perim, reserved, centreFree, centre, dice, props);
            }

            spawner = spawners(level, r, i, f, reserved, props, dice);
            boolean columns = columns(theme, r, dice);
            if (columns) {
                engaged(level, r, f, h, reserved, props, perim);
            }
            clutter(theme, r, i, f, perim, dice, props);
            if (dice.chance(overgrown(theme))) {
                overgrowth(level, r, f, h, theme, reserved, perim, dice, props);
            }

            // Cobwebs gather in corners of the old, dry places.
            if (theme.equals("Crypt") || theme.equals("Old Mines")) {
                for (int[] p : new ArrayList<>(perim)) {
                    if (corner(level, p) && dice.chance(0.4)) {
                        props.add(floor(p, f, Part.COBWEB, 0, true));
                        perim.remove(p);
                    }
                }
            }

            // A trap's plate (or a decoy's) is never covered by a rug or a barrel.
            props.removeIf(p -> trapLook(bp.get(p.x(), p.y(), p.z())));
            if (!reachable(level, r, props, reserved, bp, i)) {
                continue; // too crowded to walk: leave it plain
            }
            for (Prop p : props) {
                bp.set(p.x(), p.y(), p.z(), p.part(), p.facing(), i);
            }
            if (spawner) {
                dark.add(r.id);
            }
            if (built) {
                skirting(bp, level, r, plan, i);
            }
            if (!theme.equals("Caverns")) {
                finish(bp, level, r, plan, i, theme, reserved);
                cove(bp, level, r, plan, i);
                if (panelled(r.role, theme, dice)) {
                    panel(bp, level, r, plan, i);
                }
            }
            if (r.role == Role.ROOM && theme.equals("Crypt") && dice.chance(0.25)) {
                shelves(bp, level, r, plan, i, reserved);
            }
            if (!theme.equals("Caverns")) {
                arches(bp, level, r, plan, i);
            }
            if (built) {
                doorFrames(bp, level, r, plan, i);
                if (columns || dice.chance(0.3)) {
                    niches(bp, level, r, plan, i, reserved, dice);
                }
            }
        }
        corridors(bp, plan, i, dice);
        return dark;
    }

    /** Ordinary rooms: what the theme fills them with. */
    private static void flavour(String theme, LevelPlan level, Room r, int f, int h, List<int[]> perim,
            boolean[][] reserved, boolean centreFree, int[] centre, Dice dice, List<Prop> props) {
        switch (theme) {
            case "Crypt" -> {
                if (centreFree && Math.min(r.w, r.h) >= 9 && dice.chance(0.6)) {
                    // Two sarcophagi side by side, each two long, with a candle at the head.
                    boolean alongX = r.w >= r.h;
                    for (int side : new int[] {-1, 1}) {
                        int x0 = centre[0] + (alongX ? 0 : side);
                        int z0 = centre[1] + (alongX ? side : 0);
                        int x1 = x0 + (alongX ? 1 : 0);
                        int z1 = z0 + (alongX ? 0 : 1);
                        if (free(level, reserved, r, x0, z0) && free(level, reserved, r, x1, z1)) {
                            props.add(new Prop(x0, f, z0, Part.SARCOPHAGUS, 0, true));
                            props.add(new Prop(x1, f, z1, Part.SARCOPHAGUS, 0, true));
                            props.add(new Prop(x0, f + 1, z0, Part.CANDLES, 0, false));
                        }
                    }
                } else if (centreFree && dice.chance(0.5)) {
                    rug(level, r, f, reserved, props);
                }
                take(perim, 2, p -> props.add(floor(p, f, Part.SKULL, dice.nextInt(4), true)));
                take(perim, 2, p -> props.add(floor(p, f, Part.CANDLES, dice.nextInt(3), true)));
                take(perim, 1, p -> props.add(floor(p, f, Part.POT, 0, true)));
            }
            case "Sunken Halls" -> {
                if (centreFree && dice.chance(0.5)) {
                    tableSet(centre, f, dice, props);
                }
                scatter(level, r, reserved, dice, 0.2, (x, z) -> props.add(new Prop(x, f, z, Part.MOSS, 0, false)));
                take(perim, 2, p -> props.add(floor(p, f, Part.CANDLES, dice.nextInt(3), true)));
                take(perim, 2, p -> props.add(floor(p, f, Part.POT, 0, true)));
            }
            case "Old Mines" -> {
                if (centreFree && dice.chance(0.6)) {
                    tableSet(centre, f, dice, props);
                }
                take(perim, 2 + dice.nextInt(3), p -> props.add(floor(p, f, Part.BARREL, 0, true)));
            }
            case "Caverns" -> {
                scatter(level, r, reserved, dice, 0.08, (x, z) -> props.add(new Prop(x, f, z, Part.STALAGMITE, 0, true)));
                scatter(level, r, reserved, dice, 0.15, (x, z) -> props.add(new Prop(x, f, z, Part.MOSS, 0, false)));
            }
            case "Deep Halls" -> {
                if (centreFree && dice.chance(0.5)) {
                    props.add(new Prop(centre[0], f, centre[1], Part.BRAZIER, 0, true));
                } else if (centreFree) {
                    rug(level, r, f, reserved, props);
                }
                for (int k = 0; k < 3; k++) {
                    int x = r.minX() + 1 + dice.nextInt(Math.max(1, r.w - 2));
                    int z = r.minZ() + 1 + dice.nextInt(Math.max(1, r.h - 2));
                    if (r.contains(x, z) && level.cell(x, z) == Cell.FLOOR && !(x == centre[0] && z == centre[1])) {
                        props.add(new Prop(x, f + h - 1, z, Part.CHAIN, 0, false));
                        props.add(new Prop(x, f + h - 2, z, Part.CHAIN, 0, false));
                    }
                }
                take(perim, 2, p -> props.add(floor(p, f, Part.SKULL, dice.nextInt(4), true)));
            }
            default -> {
            }
        }
    }

    /**
     * Spawners: likely from the first level, and the deeper you go the likelier
     * a room holds two or three (Sable: "not more likely to get a spawner as you
     * go down, but likely from the start, and more likely to get multiple").
     * They stand on clear floor away from the walls, at least three apart.
     * Returns whether any were placed: such a room stays dark.
     */
    private static boolean spawners(LevelPlan level, Room r, int i, int f, boolean[][] reserved, List<Prop> props, Dice dice) {
        double any = switch (r.role) {
            case GUARD -> 0.65;
            case ROOM, HALL -> 0.35;
            case LAIR -> i >= 2 ? 0.5 : 0;
            default -> 0;
        };
        if (!dice.chance(any)) {
            return false;
        }
        int count = 1;
        if (dice.chance(Math.min(0.7, 0.1 * i))) {
            count++;
        }
        if (dice.chance(Math.min(0.5, 0.06 * i))) {
            count++;
        }
        Set<Long> taken = new HashSet<>();
        for (Prop p : props) {
            taken.add(key(p.x(), p.z()));
        }
        List<int[]> spots = new ArrayList<>();
        for (int x = r.minX() + 2; x <= r.maxX() - 2; x++) {
            for (int z = r.minZ() + 2; z <= r.maxZ() - 2; z++) {
                if (free(level, reserved, r, x, z) && !taken.contains(key(x, z)) && !nearWall(level, x, z)) {
                    spots.add(new int[] {x, z});
                }
            }
        }
        List<int[]> placed = new ArrayList<>();
        while (placed.size() < count && !spots.isEmpty()) {
            int[] s = spots.remove(dice.nextInt(spots.size()));
            boolean spaced = true;
            for (int[] p : placed) {
                spaced &= Math.max(Math.abs(p[0] - s[0]), Math.abs(p[1] - s[1])) >= 3;
            }
            if (spaced) {
                placed.add(s);
                props.add(new Prop(s[0], f, s[1], Part.SPAWNER, 0, true));
            }
        }
        return !placed.isEmpty();
    }

    private static boolean nearWall(LevelPlan level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.cell(x + dx, z + dz).isOpen()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Which rooms get columns standing out from their walls: the grand ones, and some others. */
    private static boolean columns(String theme, Room r, Dice dice) {
        if (theme.equals("Old Mines") || theme.equals("Caverns") || Math.min(r.w, r.h) < 9 || r.shape == com.sablednah.crawlspace.plan.Shape.HALL) {
            return false;
        }
        return switch (r.role) {
            case LAIR, SHRINE -> true;
            case HALL, GUARD, ROOM, EXIT, ENTRY -> dice.chance(0.4);
            default -> false;
        };
    }

    /**
     * Engaged columns: every fourth cell along the walls, standing out into
     * the room, floor to ceiling, with a base and a capital in the trim.
     */
    private static void engaged(LevelPlan level, Room r, int f, int h, boolean[][] reserved, List<Prop> props, List<int[]> perim) {
        Set<Long> taken = new HashSet<>();
        for (Prop p : props) {
            taken.add(key(p.x(), p.z())); // never stand a column on the hoard, the throne or a spawner
        }
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!free(level, reserved, r, x, z) || Math.floorMod(x + z, 4) != 0 || taken.contains(key(x, z))) {
                    continue;
                }
                boolean wall = false;
                for (int[] d : DIRS) {
                    wall |= level.cell(x + d[0], z + d[1]) == Cell.WALL;
                }
                if (!wall) {
                    continue;
                }
                for (int y = f; y < f + h; y++) {
                    Part part = y == f || y == f + h - 1 ? Part.DADO : Part.PILLAR;
                    props.add(new Prop(x, y, z, part, 0, y == f));
                }
                final int cx = x;
                final int cz = z;
                perim.removeIf(p -> p[0] == cx && p[1] == cz); // nothing else goes on a column's cell
            }
        }
    }

    /** More things lying about, by theme: some of what is left of the wall-side floor. */
    private static void clutter(String theme, Room r, int i, int f, List<int[]> perim, Dice dice, List<Prop> props) {
        int n = perim.size() / 4;
        for (int k = 0; k < n && !perim.isEmpty(); k++) {
            int[] p = perim.remove(perim.size() - 1);
            Part part = switch (theme) {
                case "Crypt" -> pick(dice, Part.SKULL, Part.CANDLES, Part.POT, Part.FLOOR_LANTERN, Part.BONES, Part.COBWEB);
                case "Sunken Halls" -> pick(dice, Part.POT, Part.CANDLES, Part.CAULDRON, Part.FLOOR_LANTERN, Part.MOSS);
                case "Old Mines" -> pick(dice, Part.BARREL, Part.BARREL, Part.FLOOR_LANTERN, Part.ANVIL, Part.GRINDSTONE, Part.CAULDRON, Part.COBWEB);
                case "Caverns" -> pick(dice, Part.STALAGMITE, Part.MUSHROOM, Part.MUSHROOM, Part.CANDLES, Part.POT);
                default -> pick(dice, Part.SKULL, Part.CANDLES, Part.FLOOR_LANTERN, Part.CAULDRON, Part.LECTERN, Part.POT);
            };
            boolean blocks = part != Part.MOSS && part != Part.MUSHROOM;
            int facing = part == Part.SKULL ? dice.nextInt(4) : part == Part.CANDLES ? dice.nextInt(4) : p[2];
            props.add(floor(p, f, part, facing, blocks));
            if (part == Part.BARREL && dice.chance(0.5)) {
                props.add(new Prop(p[0], f + 1, p[1], Part.BARREL, 0, false)); // a stack of two
            }
        }
    }

    private static Part pick(Dice dice, Part... parts) {
        return parts[dice.nextInt(parts.length)];
    }

    private static double overgrown(String theme) {
        return switch (theme) {
            case "Sunken Halls" -> 0.6;
            case "Caverns" -> 0.7;
            case "Old Mines" -> 0.25;
            case "Crypt" -> 0.2;
            default -> 0.1;
        };
    }

    /**
     * Overgrowth: vines hanging down the walls, moss with ferns and grass, an
     * azalea bush or two in the corners, and in the caverns roots from the
     * ceiling.
     */
    private static void overgrowth(LevelPlan level, Room r, int f, int h, String theme, boolean[][] reserved,
            List<int[]> perim, Dice dice, List<Prop> props) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || !level.cell(x, z).isOpen()) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    if (level.cell(x + DIRS[d][0], z + DIRS[d][1]) == Cell.WALL && dice.chance(0.3)) {
                        int len = 1 + dice.nextInt(Math.max(1, h - 2));
                        for (int y = f + h - 2; y > f + h - 2 - len && y > f + 1; y--) {
                            props.add(new Prop(x, y, z, Part.VINE, d, false));
                        }
                        break;
                    }
                }
            }
        }
        scatter(level, r, reserved, dice, 0.12, (x, z) -> {
            props.add(new Prop(x, f - 1, z, Part.MOSS_FLOOR, 0, false));
            if (dice.chance(0.6)) {
                props.add(new Prop(x, f, z, Part.PLANT, dice.nextInt(2), false));
            }
        });
        for (int[] p : new ArrayList<>(perim)) {
            if (corner(level, p) && dice.chance(0.5)) {
                props.add(floor(p, f, Part.LEAVES, dice.nextInt(2), true));
                perim.remove(p);
            }
        }
        if (theme.equals("Caverns") || theme.equals("Sunken Halls")) {
            scatter(level, r, reserved, dice, 0.06, (x, z) -> props.add(new Prop(x, f + h - 1, z, Part.ROOTS, 0, false)));
        }
    }

    /**
     * Arches: where two solid columns stand in a row with a gap of two or three
     * between them (a hall's pillars, or columns along a wall), a lintel
     * spans the gap under the ceiling, with an upside-down stair either side
     * of it curving down onto each column.
     */
    private static void arches(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i) {
        int f = Blueprinter.floorAt(plan, i, r.centerX(), r.centerZ());
        int h = Blueprinter.clearHeight(level, r.centerX(), r.centerZ());
        if (h < 5) {
            return; // too low for an arch to clear your head
        }
        int top = f + h - 1;
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!columnAt(bp, r, x, f + 1, z)) {
                    continue;
                }
                for (int d : new int[] {1, 2}) { // east and south: each pair once
                    int dx = DIRS[d][0];
                    int dz = DIRS[d][1];
                    for (int gap = 2; gap <= 3; gap++) {
                        int ex = x + dx * (gap + 1);
                        int ez = z + dz * (gap + 1);
                        if (!columnAt(bp, r, ex, f + 1, ez)) {
                            continue;
                        }
                        boolean clear = true;
                        for (int k = 1; k <= gap; k++) {
                            int code = bp.get(x + dx * k, top, z + dz * k);
                            clear &= code == 0 || Blueprint.part(code) == Part.AIR || Blueprint.part(code) == Part.COVE;
                        }
                        if (!clear) {
                            continue;
                        }
                        for (int k = 1; k <= gap; k++) {
                            bp.set(x + dx * k, top, z + dz * k, Part.DADO, 0, i);
                        }
                        bp.set(x + dx, top - 1, z + dz, Part.COVE, (d + 2) % 4, i);
                        bp.set(ex - dx, top - 1, ez - dz, Part.COVE, d, i);
                        break;
                    }
                }
            }
        }
    }

    /** Whether a solid column (a pillar or an engaged column) stands at (x, y, z) in the room. */
    private static boolean columnAt(Blueprint bp, Room r, int x, int y, int z) {
        if (!r.contains(x, z)) {
            return false;
        }
        int code = bp.get(x, y, z);
        return code != 0 && (Blueprint.part(code) == Part.PILLAR);
    }

    /** A frame round each doorway on the room side: the walls either side and the lintel, in the pilaster stone. */
    private static void doorFrames(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i) {
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (!level.cell(x, z).isDoor()) {
                    continue;
                }
                boolean ours = false;
                for (int[] d : DIRS) {
                    ours |= level.region(x + d[0], z + d[1]) == r.id;
                }
                if (!ours) {
                    continue;
                }
                int f = Blueprinter.floorAt(plan, i, x, z);
                for (int[] d : DIRS) {
                    int wx = x + d[0];
                    int wz = z + d[1];
                    if (level.cell(wx, wz) != Cell.WALL) {
                        continue;
                    }
                    for (int y = f; y <= f + 3; y++) {
                        swapWall(bp, wx, y, wz, Part.PILASTER, i);
                    }
                }
                for (int y = f + 2; y <= f + 3; y++) {
                    int code = bp.get(x, y, z);
                    if (code != 0 && Blueprint.part(code) == Part.WALL) {
                        bp.set(x, y, z, Part.PILASTER, 0, i); // the lintel
                    }
                }
            }
        }
    }

    private static void swapWall(Blueprint bp, int x, int y, int z, Part part, int i) {
        int code = bp.get(x, y, z);
        if (code != 0) {
            Part p = Blueprint.part(code);
            if (p == Part.WALL || p == Part.WALL_ACCENT || p == Part.PANEL || p == Part.DADO) {
                bp.set(x, y, z, part, 0, i);
            }
        }
    }

    /**
     * Niches: a recess two high cut into a wall, with a skull, candles, a pot
     * or a lantern in it. Only where the wall has plain rock behind it: the
     * rock behind is walled in, so the dungeon stays sealed.
     */
    private static void niches(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i, boolean[][] reserved, Dice dice) {
        int made = 0;
        // Crypts, lairs and shrines line their niches with statues, as a row (Sable's reference: a lair's wall of skeletons).
        boolean statues = r.role == Role.LAIR || r.role == Role.SHRINE
                || (level.theme.name().equals("Crypt") && dice.chance(0.5));
        int most = statues ? 8 : 4;
        for (int x = r.minX() - 1; x <= r.maxX() + 1 && made < most; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1 && made < most; z++) {
                if (level.cell(x, z) != Cell.WALL || Math.floorMod(x + z, statues ? 2 : 4) != (statues ? 0 : 2) || !dice.chance(statues ? 0.8 : 0.5)) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    int rx = x - DIRS[d][0]; // the room cell in front
                    int rz = z - DIRS[d][1];
                    int bx = x + DIRS[d][0]; // the rock behind
                    int bz = z + DIRS[d][1];
                    int px = -DIRS[d][1];
                    int pz = DIRS[d][0];
                    if (!r.contains(rx, rz) || level.cell(rx, rz) != Cell.FLOOR || reserved[rx + R][rz + R]
                            || level.cell(bx, bz) != Cell.ROCK
                            || level.cell(x + px, z + pz) != Cell.WALL || level.cell(x - px, z - pz) != Cell.WALL) {
                        continue;
                    }
                    int f = Blueprinter.floorAt(plan, i, rx, rz);
                    int h = Blueprinter.clearHeight(level, rx, rz);
                    if (h < 4 || bp.get(bx, f, bz) != 0) {
                        continue;
                    }
                    for (int y = f; y <= f + 3; y++) {
                        bp.set(bx, y, bz, Part.WALL, 0, i); // back the niche with wall
                    }
                    if (statues) {
                        // A statue at floor level: a wall-post body under a skull, three high.
                        bp.set(x, f, z, Part.STATUE, 0, i);
                        bp.set(x, f + 1, z, Part.STATUE, 0, i);
                        bp.set(x, f + 2, z, Part.SKULL, (d + 2) % 4, i);
                    } else {
                        bp.set(x, f + 1, z, pick(dice, Part.SKULL, Part.CANDLES, Part.POT, Part.FLOOR_LANTERN), (d + 2) % 4, i);
                        bp.set(x, f + 2, z, Part.AIR, 0, i);
                    }
                    made++;
                    break;
                }
            }
        }
    }

    /** A table with a chair on each side, backs turned away from it. */
    private static void tableSet(int[] c, int f, Dice dice, List<Prop> props) {
        props.add(new Prop(c[0], f, c[1], Part.TABLE, 0, true));
        props.add(new Prop(c[0], f + 1, c[1], Part.TABLE_TOP, 0, false));
        for (int d = 0; d < 4; d++) {
            if (dice.chance(0.75)) {
                props.add(new Prop(c[0] + DIRS[d][0], f, c[1] + DIRS[d][1], Part.CHAIR, d, true));
            }
        }
    }

    /** A rug over the middle of the room: three by three, or three by five in a long room. */
    private static void rug(LevelPlan level, Room r, int f, boolean[][] reserved, List<Prop> props) {
        boolean alongX = r.w > r.h + 2;
        boolean alongZ = r.h > r.w + 2;
        for (int dx = alongX ? -2 : -1; dx <= (alongX ? 2 : 1); dx++) {
            for (int dz = alongZ ? -2 : -1; dz <= (alongZ ? 2 : 1); dz++) {
                int x = r.centerX() + dx;
                int z = r.centerZ() + dz;
                if (free(level, reserved, r, x, z)) {
                    props.add(new Prop(x, f, z, Part.RUG, 0, false));
                }
            }
        }
    }

    // ---- placement helpers ----

    private static Prop floor(int[] p, int f, Part part, int facing, boolean blocks) {
        return new Prop(p[0], f, p[1], part, facing, blocks);
    }

    /** A banner on the wall behind a perimeter cell, two blocks up. */
    private static Prop banner(int[] p, int f) {
        return new Prop(p[0], f + 2, p[1], Part.BANNER, p[2], false);
    }

    private static void take(List<int[]> perim, int n, java.util.function.Consumer<int[]> use) {
        for (int k = 0; k < n && !perim.isEmpty(); k++) {
            use.accept(perim.remove(perim.size() - 1));
        }
    }

    private interface CellUse {
        void accept(int x, int z);
    }

    private static void scatter(LevelPlan level, Room r, boolean[][] reserved, Dice dice, double chance, CellUse use) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (free(level, reserved, r, x, z) && dice.chance(chance)) {
                    use.accept(x, z);
                }
            }
        }
    }

    /** A carpet down the middle of a hall, along its long axis, between the pillars. */
    private static void aisle(LevelPlan level, Room r, int f, boolean[][] reserved, List<Prop> props) {
        boolean alongX = r.w >= r.h;
        for (int k = alongX ? r.minX() : r.minZ(); k <= (alongX ? r.maxX() : r.maxZ()); k++) {
            int x = alongX ? k : r.centerX();
            int z = alongX ? r.centerZ() : k;
            // Round a winch or other trigger the carpet breaks off: it once laid itself over a winch.
            if (r.contains(x, z) && level.cell(x, z) == Cell.FLOOR && !reserved[x + R][z + R]) {
                props.add(new Prop(x, f, z, Part.CARPET, 0, false));
            }
        }
    }

    static boolean trapLook(int code) {
        if (code == 0) {
            return false;
        }
        Part part = Blueprint.part(code);
        return part == Part.TRAP_PLATE || part == Part.TRAP_WIRE || part == Part.DECOY_PLATE || part == Part.DECOY_WIRE;
    }

    private static boolean free(LevelPlan level, boolean[][] reserved, Room r, int x, int z) {
        return r.contains(x, z) && level.cell(x, z) == Cell.FLOOR && !reserved[x + R][z + R];
    }

    private static boolean centreFree(LevelPlan level, Room r, boolean[][] reserved) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!free(level, reserved, r, r.centerX() + dx, r.centerZ() + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Floor cells along a room's walls, as {x, z, facing away from the wall}, shuffled. */
    private static List<int[]> perimeter(LevelPlan level, Room r, boolean[][] reserved, Dice dice) {
        List<int[]> out = new ArrayList<>();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!free(level, reserved, r, x, z)) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    if (level.cell(x + DIRS[d][0], z + DIRS[d][1]) == Cell.WALL) {
                        out.add(new int[] {x, z, (d + 2) % 4});
                        break;
                    }
                }
            }
        }
        for (int k = out.size() - 1; k > 0; k--) {
            int j = dice.nextInt(k + 1);
            int[] t = out.get(k);
            out.set(k, out.get(j));
            out.set(j, t);
        }
        return out;
    }

    private static boolean corner(LevelPlan level, int[] p) {
        int walls = 0;
        for (int[] d : DIRS) {
            if (level.cell(p[0] + d[0], p[1] + d[1]) == Cell.WALL) {
                walls++;
            }
        }
        return walls >= 2;
    }

    private static Trigger find(Blueprint bp, Trigger.Kind kind, int level, Room r) {
        for (Trigger t : bp.triggers()) {
            if (t.kind() == kind && t.level() == level && r.contains(t.x(), t.z())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Cells no prop may cover: two around every doorway, the five-by-five of
     * every stair and pit, one round pools, and every trigger and the cells
     * round a lever or hoard.
     */
    private static boolean[][] reserved(Blueprint bp, LevelPlan level, int i) {
        boolean[][] res = new boolean[LevelPlan.SIZE][LevelPlan.SIZE];
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                Cell c = level.cell(x, z);
                // A stair down gets one more: its railing goes in later, and a prop just outside the
                // railing's gap closed it (seed 9, level 1).
                int r = c == Cell.STAIR_DOWN ? 3
                        : c.isDoor() || c == Cell.STAIR_UP || c == Cell.PIT ? 2
                        : c == Cell.POOL ? 1 : -1;
                mark(res, x, z, r);
            }
        }
        for (int[] s : level.stairsUp) {
            mark(res, s[0], s[1], 2);
        }
        for (int[] s : level.stairsDown) {
            mark(res, s[0], s[1], 2);
        }
        for (Trigger t : bp.triggers()) {
            if (t.level() == i) {
                mark(res, t.x(), t.z(), t.kind() == Trigger.Kind.LEVER || t.kind() == Trigger.Kind.WINCH || t.kind() == Trigger.Kind.ONEWAY
                        || t.kind() == Trigger.Kind.TREASURE ? 1 : 0);
            }
        }
        return res;
    }

    private static void mark(boolean[][] res, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (LevelPlan.inBounds(x + dx, z + dz)) {
                    res[x + dx + R][z + dz + R] = true;
                }
            }
        }
    }

    /**
     * Whether, with these props, every doorway into the room, its stairs and
     * its lever or hoard can still be reached from the first doorway.
     */
    private static boolean reachable(LevelPlan level, Room r, List<Prop> props, boolean[][] reserved, Blueprint bp, int i) {
        Set<Long> blocked = new HashSet<>();
        for (Prop p : props) {
            if (p.blocks()) {
                blocked.add(key(p.x(), p.z()));
            }
        }
        List<int[]> must = new ArrayList<>();
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || !level.cell(x, z).isWalkable() || blocked.contains(key(x, z))) {
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
            if (t.level() == i && r.contains(t.x(), t.z())
                    && (t.kind() == Trigger.Kind.LEVER || t.kind() == Trigger.Kind.WINCH || t.kind() == Trigger.Kind.ONEWAY
                        || t.kind() == Trigger.Kind.TREASURE)) {
                // Stand beside it: one of its four neighbours must be reachable.
                must.add(new int[] {t.x(), t.z(), 1});
            }
        }
        if (must.isEmpty()) {
            return true;
        }
        int[] start = null;
        for (int[] m : must) {
            if (m.length == 2) {
                start = m;
                break;
            }
        }
        if (start == null) {
            return true;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen.add(key(start[0], start[1]));
        queue.add(start);
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : DIRS) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                if (r.contains(x, z) && level.cell(x, z).isWalkable() && !blocked.contains(key(x, z)) && seen.add(key(x, z))) {
                    queue.add(new int[] {x, z});
                }
            }
        }
        for (int[] m : must) {
            if (m.length == 2) {
                if (!seen.contains(key(m[0], m[1]))) {
                    return false;
                }
            } else {
                boolean near = false;
                for (int[] d : DIRS) {
                    near |= seen.contains(key(m[0] + d[0], m[1] + d[1]));
                }
                if (!near) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    // ---- walls ----

    /** The bottom course of a room's walls in the theme's trim. */
    private static void skirting(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i) {
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (level.cell(x, z) != Cell.WALL) {
                    continue;
                }
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (r.contains(nx, nz) && level.cell(nx, nz) == Cell.FLOOR) {
                        int f = Blueprinter.floorAt(plan, i, nx, nz);
                        if (bp.get(x, f, z) != 0 && Blueprint.part(bp.get(x, f, z)) == Part.WALL) {
                            bp.set(x, f, z, Part.WALL_ACCENT, 0, i);
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * The finish that stops a room looking like a box: a floor border just
     * inside the walls and a medallion in the middle (built themes), wall
     * pilasters every few blocks (timber posts in the mines), and ceiling
     * beams across bigger crypt and mine rooms.
     */
    private static void finish(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i, String theme, boolean[][] reserved) {
        boolean mines = theme.equals("Old Mines");
        int f = Blueprinter.floorAt(plan, i, r.centerX(), r.centerZ());
        int h = Blueprinter.clearHeight(level, r.centerX(), r.centerZ());
        // Floor border: room floor cells touching a wall. Medallion: the centre 3x3 of a room 9 or more across.
        if (!mines && Math.min(r.w, r.h) >= 7) {
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    if (!r.contains(x, z) || level.cell(x, z) != Cell.FLOOR) {
                        continue;
                    }
                    boolean edge = false;
                    for (int[] d : DIRS) {
                        edge |= level.cell(x + d[0], z + d[1]) == Cell.WALL;
                    }
                    if (edge) {
                        swapFloor(bp, plan, i, x, z, Part.FLOOR_ACCENT);
                    }
                }
            }
        }
        if (Math.min(r.w, r.h) >= 9) {
            boolean clear = true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    clear &= level.cell(r.centerX() + dx, r.centerZ() + dz) == Cell.FLOOR;
                }
            }
            if (clear) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        swapFloor(bp, plan, i, r.centerX() + dx, r.centerZ() + dz,
                                dx == 0 && dz == 0 && !mines ? Part.FLOOR_INLAY : Part.FLOOR_ACCENT);
                    }
                }
            }
        }
        // Pilasters: every fourth wall cell along the room, never beside a doorway.
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (level.cell(x, z) != Cell.WALL || Math.floorMod(x + z, 4) != 0 || nearDoorway(level, x, z)) {
                    continue;
                }
                boolean faces = false;
                for (int[] d : DIRS) {
                    faces |= r.contains(x + d[0], z + d[1]) && level.cell(x + d[0], z + d[1]) == Cell.FLOOR;
                }
                if (!faces) {
                    continue;
                }
                for (int y = f; y < f + h; y++) {
                    int code = bp.get(x, y, z);
                    if (code != 0 && (Blueprint.part(code) == Part.WALL || Blueprint.part(code) == Part.WALL_ACCENT)) {
                        bp.set(x, y, z, Part.PILASTER, 0, i);
                    }
                }
            }
        }
        // Beams across the short axis, every four cells, flush with the ceiling.
        if ((mines || theme.equals("Crypt")) && Math.min(r.w, r.h) >= 7) {
            boolean alongX = r.w >= r.h; // the room is long in x, so beams run along z, spaced in x
            for (int x = r.minX(); x <= r.maxX(); x++) {
                for (int z = r.minZ(); z <= r.maxZ(); z++) {
                    int along = alongX ? x : z;
                    if (Math.floorMod(along, 4) != 0 || !r.contains(x, z) || !level.cell(x, z).isOpen()) {
                        continue;
                    }
                    int top = Blueprinter.floorAt(plan, i, x, z) + Blueprinter.clearHeight(level, x, z);
                    int code = bp.get(x, top, z);
                    if (code != 0 && Blueprint.part(code) == Part.CEILING) {
                        bp.set(x, top, z, Part.BEAM, alongX ? 1 : 0, i);
                    }
                }
            }
        }
    }

    private static void swapFloor(Blueprint bp, DungeonPlan plan, int i, int x, int z, Part part) {
        int y = Blueprinter.floorAt(plan, i, x, z) - 1;
        int code = bp.get(x, y, z);
        if (code != 0 && Blueprint.part(code) == Part.FLOOR) {
            bp.set(x, y, z, part, 0, i);
        }
    }

    private static boolean nearDoorway(LevelPlan level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.cell(x + dx, z + dz).isDoor()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Coving: an upside-down stair against the wall under the ceiling, all the
     * way round the room. The palette sets it again once its neighbours are in,
     * so the stairs turn the corners.
     */
    private static void cove(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i) {
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || !level.cell(x, z).isOpen()) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    if (level.cell(x + DIRS[d][0], z + DIRS[d][1]) != Cell.WALL) {
                        continue;
                    }
                    int y = Blueprinter.floorAt(plan, i, x, z) + Blueprinter.clearHeight(level, x, z) - 1;
                    int code = bp.get(x, y, z);
                    if (code != 0 && Blueprint.part(code) == Part.AIR) {
                        bp.set(x, y, z, Part.COVE, d, i);
                    }
                    break;
                }
            }
        }
    }

    /** Which rooms are panelled: most built-theme rooms people would live or work in; mine rooms boarded. */
    private static boolean panelled(Role role, String theme, Dice dice) {
        return switch (role) {
            case GUARD, SHRINE, KEY, TREASURE -> true;
            case ROOM, HALL -> dice.chance(theme.equals("Old Mines") ? 0.6 : 0.5);
            default -> false;
        };
    }

    /** Panelling up the lower two blocks of a room's walls with a dado rail above, round the pilasters. */
    private static void panel(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i) {
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (level.cell(x, z) != Cell.WALL) {
                    continue;
                }
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (r.contains(nx, nz) && level.cell(nx, nz) == Cell.FLOOR) {
                        int f = Blueprinter.floorAt(plan, i, nx, nz);
                        for (int y = f; y <= f + 2; y++) {
                            int code = bp.get(x, y, z);
                            if (code != 0 && (Blueprint.part(code) == Part.WALL || Blueprint.part(code) == Part.WALL_ACCENT)) {
                                bp.set(x, y, z, y == f + 2 ? Part.DADO : Part.PANEL, 0, i);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Bookshelves along the walls, two high above the trim: a crypt's library. */
    private static void shelves(Blueprint bp, LevelPlan level, Room r, DungeonPlan plan, int i, boolean[][] reserved) {
        for (int x = r.minX() - 1; x <= r.maxX() + 1; x++) {
            for (int z = r.minZ() - 1; z <= r.maxZ() + 1; z++) {
                if (level.cell(x, z) != Cell.WALL) {
                    continue;
                }
                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    if (r.contains(nx, nz) && level.cell(nx, nz) == Cell.FLOOR && !reserved[nx + R][nz + R]) {
                        int f = Blueprinter.floorAt(plan, i, nx, nz);
                        for (int y = f + 1; y <= f + 2; y++) {
                            if (bp.get(x, y, z) != 0 && Blueprint.part(bp.get(x, y, z)) == Part.WALL) {
                                bp.set(x, y, z, Part.SHELF, 0, i);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    // ---- corridors ----

    /**
     * Wall torches every so often (fewer the deeper you go), and in the mines
     * timber frames and rails. Frames only stand where the cell beyond each
     * post is wall, so they never close a side passage.
     */
    private static void corridors(Blueprint bp, DungeonPlan plan, int i, Dice dice) {
        LevelPlan level = plan.levels().get(i);
        boolean mines = level.theme.name().equals("Old Mines");
        double torchChance = Math.max(0.15, 0.75 - 0.1 * i);
        for (Link l : level.links) {
            if (l.path == null || l.path.length < 6) {
                continue;
            }
            boolean rails = mines && l.width == 3 && dice.chance(0.5);
            int offset = dice.nextInt(6);
            for (int k = 1; k < l.path.length - 1; k++) {
                int[] c = l.path[k];
                int[] n = l.path[k + 1];
                int dx = Integer.signum(n[0] - c[0]);
                int dz = Integer.signum(n[1] - c[1]);
                if (dx != 0 && dz != 0) {
                    continue; // diagonal runs get nothing
                }
                if (level.cell(c[0], c[1]) != Cell.CORRIDOR) {
                    continue;
                }
                int f = Blueprinter.floorAt(plan, i, c[0], c[1]);
                int h = Blueprinter.clearHeight(level, c[0], c[1]);
                int px = -dz;
                int pz = dx;
                if ((k + offset) % 9 == 0 && dice.chance(torchChance)) {
                    torch(bp, level, plan, i, c, px, pz);
                }
                if (mines && l.width == 3 && (k + offset) % 6 == 3) {
                    frame(bp, level, plan, i, c, f, h, px, pz);
                }
                if (rails && bp.get(c[0], f, c[1]) != 0 && Blueprint.part(bp.get(c[0], f, c[1])) == Part.AIR
                        && Blueprinter.floorAt(plan, i, n[0], n[1]) == f
                        && Blueprinter.floorAt(plan, i, l.path[k - 1][0], l.path[k - 1][1]) == f) {
                    bp.set(c[0], f, c[1], Part.RAIL, dx != 0 ? 1 : 0, i);
                }
            }
        }
    }

    /** A wall torch on whichever side of the corridor has a wall nearest, two blocks up. */
    private static void torch(Blueprint bp, LevelPlan level, DungeonPlan plan, int i, int[] c, int px, int pz) {
        for (int side : new int[] {1, -1}) {
            for (int s = 0; s <= 2; s++) {
                int x = c[0] + px * side * s;
                int z = c[1] + pz * side * s;
                if (level.cell(x + px * side, z + pz * side) == Cell.WALL && level.cell(x, z) == Cell.CORRIDOR) {
                    int f = Blueprinter.floorAt(plan, i, x, z);
                    if (bp.get(x, f + 2, z) != 0 && Blueprint.part(bp.get(x, f + 2, z)) == Part.AIR) {
                        bp.set(x, f + 2, z, Part.WALL_TORCH, facingOf(-px * side, -pz * side), i);
                    }
                    return;
                }
                if (level.cell(x, z) != Cell.CORRIDOR) {
                    break;
                }
            }
        }
    }

    /** Two posts against the walls and a beam across, in a three-wide mine corridor. */
    private static void frame(Blueprint bp, LevelPlan level, DungeonPlan plan, int i, int[] c, int f, int h, int px, int pz) {
        int[][] sides = {{c[0] + px, c[1] + pz}, {c[0] - px, c[1] - pz}};
        for (int[] s : sides) {
            int bx = s[0] + (s[0] - c[0]);
            int bz = s[1] + (s[1] - c[1]);
            if (level.cell(s[0], s[1]) != Cell.CORRIDOR || level.cell(bx, bz) != Cell.WALL
                    || Blueprinter.floorAt(plan, i, s[0], s[1]) != f) {
                return;
            }
            // Not over a trap's plate or wire, nor a decoy's.
            int code = bp.get(s[0], f, s[1]);
            if (code != 0 && Blueprint.part(code) != Part.AIR) {
                return;
            }
        }
        for (int[] s : sides) {
            for (int y = f; y <= f + h - 2; y++) {
                bp.set(s[0], y, s[1], Part.SUPPORT, 0, i);
            }
        }
        int axis = px != 0 ? 0 : 1;
        for (int[] s : new int[][] {sides[0], sides[1], c}) {
            bp.set(s[0], f + h - 1, s[1], Part.BEAM, axis, i);
        }
    }

    static int facingOf(int dx, int dz) {
        if (dz < 0) {
            return 0;
        }
        if (dx > 0) {
            return 1;
        }
        if (dz > 0) {
            return 2;
        }
        return 3;
    }
}
