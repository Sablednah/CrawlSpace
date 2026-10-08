package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.CorridorStyle;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Link;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;

/**
 * Picks somewhere worth looking at on a level, for {@code /crawlspace goto}:
 * the middle of a corridor of a given style, facing along it, or a room of a
 * given role. A testing aid; it is how screenshots get taken from a script.
 */
final class Tour {

    static final List<String> KINDS = List.of(
            "straight", "angled", "winding", "curved", "lair", "hall", "exit", "entry", "shrine", "guard",
            "treasure", "secret", "key", "room", "lever", "trap", "pit", "decoy", "secretdoor", "locked", "puzzle");

    private Tour() {
    }

    /**
     * Next to one of the level's triggers, facing it: two blocks off a lever or
     * secret wall, and two short of a trap tile so a step forward springs it.
     * {x, z, yaw} in dungeon coordinates, or null.
     */
    static double[] findTrigger(LevelPlan level, com.sablednah.crawlspace.build.Blueprint bp, String what) {
        com.sablednah.crawlspace.build.Trigger.Kind kind = switch (what.toLowerCase(Locale.ROOT)) {
            case "lever", "locked" -> com.sablednah.crawlspace.build.Trigger.Kind.LEVER;
            case "secretdoor" -> com.sablednah.crawlspace.build.Trigger.Kind.SECRET;
            case "trap" -> null;
            case "decoy" -> com.sablednah.crawlspace.build.Trigger.Kind.DECOY;
            case "pit" -> com.sablednah.crawlspace.build.Trigger.Kind.PIT;
            default -> throw new IllegalArgumentException(what);
        };
        for (com.sablednah.crawlspace.build.Trigger t : bp.triggers()) {
            boolean match = kind == null ? t.kind().isTrap() : t.kind() == kind;
            if (!match || t.level() != level.index) {
                continue;
            }
            if (what.equalsIgnoreCase("locked") && t.targets().length > 0) {
                // In front of the first door the lever works: the door cell's open neighbour.
                int[] door = t.targets()[0];
                for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                    if (level.cell(door[0] + d[0], door[2] + d[1]).isWalkable()
                            && level.cell(door[0] + d[0] / 2, door[2] + d[1] / 2).isWalkable()) {
                        return new double[] {door[0] + d[0] + 0.5, door[2] + d[1] + 0.5, yaw(-d[0], -d[1])};
                    }
                }
                continue;
            }
            for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                Cell c = level.cell(t.x() + d[0], t.z() + d[1]);
                Cell mid = level.cell(t.x() + d[0] / 2, t.z() + d[1] / 2);
                if (c.isWalkable() && mid.isWalkable() && !c.isDoor()) {
                    return new double[] {t.x() + d[0] + 0.5, t.z() + d[1] + 0.5, yaw(-d[0], -d[1])};
                }
            }
        }
        return null;
    }

    /** {x, z, yaw} in dungeon coordinates (block centres), or null. */
    static double[] find(LevelPlan level, String what, long salt) {
        String w = what.toLowerCase(Locale.ROOT);
        Dice dice = Dice.of(level.index, salt);
        for (CorridorStyle style : CorridorStyle.values()) {
            if (style.name().toLowerCase(Locale.ROOT).equals(w)) {
                List<Link> links = new ArrayList<>();
                for (Link l : level.links) {
                    if (l.style == style && l.path != null && l.path.length >= 8) {
                        links.add(l);
                    }
                }
                if (links.isEmpty()) {
                    return null;
                }
                // Prefer wide corridors: they photograph.
                links.sort((a, b) -> Integer.compare(b.width * 1000 + b.path.length, a.width * 1000 + a.path.length));
                Link l = links.get(Math.min(links.size() - 1, dice.nextInt(Math.min(3, links.size()))));
                int i = l.path.length / 3;
                int[] p = l.path[i];
                int[] q = l.path[Math.min(l.path.length - 1, i + 3)];
                return new double[] {p[0] + 0.5, p[1] + 0.5, yaw(q[0] - p[0], q[1] - p[1])};
            }
        }
        for (Role role : Role.values()) {
            if (role.name().toLowerCase(Locale.ROOT).equals(w)) {
                List<Room> rooms = new ArrayList<>();
                for (Room r : level.rooms) {
                    if (r.role == role) {
                        rooms.add(r);
                    }
                }
                if (rooms.isEmpty()) {
                    return null;
                }
                Room r = rooms.get(dice.nextInt(rooms.size()));
                // From the floor cell nearest the room's south-west corner, looking across at its centre.
                int[] best = null;
                double bestD = Double.MAX_VALUE;
                for (int x = r.minX(); x <= r.maxX(); x++) {
                    for (int z = r.minZ(); z <= r.maxZ(); z++) {
                        if (r.contains(x, z) && level.cell(x, z) == Cell.FLOOR) {
                            double d = Math.hypot(x - (r.minX() + 1), z - (r.maxZ() - 1));
                            if (d < bestD) {
                                bestD = d;
                                best = new int[] {x, z};
                            }
                        }
                    }
                }
                if (best != null) {
                    return new double[] {best[0] + 0.5, best[1] + 0.5, yaw(r.centerX() - best[0], r.centerZ() - best[1])};
                }
                return null;
            }
        }
        return null;
    }

    /** Minecraft yaw for a heading: 0 is south (+z), 90 west, 180 north, -90 east. */
    private static double yaw(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
