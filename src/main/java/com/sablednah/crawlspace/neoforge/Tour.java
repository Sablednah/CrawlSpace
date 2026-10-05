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
            "treasure", "secret", "key", "room");

    private Tour() {
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
                // Stand near the south edge looking north, across the room.
                for (int z = r.maxZ(); z >= r.minZ(); z--) {
                    int x = r.centerX();
                    if (level.cell(x, z) == Cell.FLOOR && level.cell(x, z - 1) == Cell.FLOOR) {
                        return new double[] {x + 0.5, z - 0.5, 180};
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** Minecraft yaw for a heading: 0 is south (+z), 90 west, 180 north, -90 east. */
    private static double yaw(int dx, int dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }
}
