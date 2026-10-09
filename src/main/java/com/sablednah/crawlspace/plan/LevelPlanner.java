package com.sablednah.crawlspace.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plans one level, in stages that each only add to the last:
 *
 * <ol>
 *   <li><b>Topology.</b> A loop through the entry and the exit, so there are
 *       two ways to the stair down; branches off it for the optional rooms; a
 *       lock on one loop edge with its lever down a branch.</li>
 *   <li><b>Layout.</b> Loop rooms around a lumpy curve (the idea behind
 *       Shattered Pixel Dungeon's loop builder), branches outward from it,
 *       then pushed apart until nothing overlaps.</li>
 *   <li><b>Shortcuts.</b> Rooms that ended up near each other but far apart
 *       in the graph get a link, which makes extra loops out of chance.</li>
 *   <li><b>Routing.</b> {@link Router}, shortest links first, so long ones
 *       find corridors to join.</li>
 *   <li><b>Finishing.</b> Walls round everything open, stairs, pillars,
 *       pools, then floor heights solved so corridors ramp between rooms.</li>
 * </ol>
 *
 * <p>An attempt that fails anywhere, including {@link PlanCheck}, is thrown
 * away and the next attempt starts from a new stream of dice.</p>
 */
public final class LevelPlanner {

    private static final int ATTEMPTS = 24;
    /** Cells of rock kept between room rectangles: room clearance either side plus a 3-wide corridor. */
    private static final int GAP = 6;

    /** The last attempt that failed its check, for a test to draw. Diagnostics only. */
    static volatile LevelPlan lastRejected;
    /** Why attempts were thrown away, for the tests' report. Concurrent: worldgen plans on several threads. */
    static final java.util.Map<String, Integer> FAILURES = new java.util.concurrent.ConcurrentSkipListMap<>();

    private LevelPlanner() {
    }

    private static LevelPlan fail(String why) {
        FAILURES.merge(why, 1, Integer::sum);
        return null;
    }

    /**
     * @param arrival where the stair from above comes down; the entry room is centred on it
     * @param bottom  the last level: its far room is the final lair, with no stair further down
     */
    public static LevelPlan plan(long seed, int index, int[] arrival, Theme theme, boolean bottom) {
        return plan(seed, index, arrival, theme, bottom, Feeling.NONE);
    }

    /** @param feeling the level's mood, which some of its planning follows (a hollow level's secrets, a damp one's pools) */
    public static LevelPlan plan(long seed, int index, int[] arrival, Theme theme, boolean bottom, Feeling feeling) {
        List<String> lastProblems = List.of();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Dice dice = Dice.of(seed, index, attempt);
            LevelPlan level = tryPlan(dice, index, arrival, theme, attempt, bottom, feeling);
            if (level == null) {
                continue;
            }
            lastProblems = PlanCheck.level(level);
            if (!lastProblems.isEmpty()) {
                lastRejected = level;
                fail("check: " + lastProblems.get(0).replaceAll("[-0-9,#@ ]+", " ").trim());
            }
            if (lastProblems.isEmpty()) {
                level.attempts = attempt;
                // Traps last, from their own dice: adding or tuning them never moves a wall.
                traps(level, Dice.of(seed, index, 0x7EA95L));
                return level;
            }
        }
        throw new IllegalStateException("no valid plan for level " + index + " of seed " + seed
                + " after " + ATTEMPTS + " attempts; last problems: " + lastProblems);
    }

    private static LevelPlan tryPlan(Dice dice, int index, int[] arrival, Theme theme, int attempt, boolean bottom, Feeling feeling) {
        LevelPlan level = new LevelPlan(index, theme);
        level.feeling = feeling;
        boolean hollow = feeling == Feeling.HOLLOW;
        int depth = Math.min(index, 8);

        // ---- Topology ----
        int loopSize = 5 + depth / 2 + dice.between(0, 2);
        int branchCount = 2 + depth / 2 + dice.between(0, 2);
        boolean lock = index >= 1 && dice.chance(0.55);

        List<Room> loop = new ArrayList<>();
        int exitAt = loopSize / 2 + dice.between(0, loopSize % 2);
        int lairAt = bottom ? exitAt
                : index >= 1 && dice.chance(0.6) ? pickOther(dice, loopSize, exitAt) : -1;
        for (int i = 0; i < loopSize; i++) {
            Role role = i == 0 ? Role.ENTRY
                    : i == lairAt ? Role.LAIR
                    : i == exitAt ? Role.EXIT
                    : dice.chance(0.25) ? Role.GUARD : Role.ROOM;
            loop.add(makeRoom(level.rooms.size(), role, theme, dice, level));
        }
        for (int i = 0; i < loopSize; i++) {
            Link link = new Link(loop.get(i), loop.get((i + 1) % loopSize), LinkKind.DOOR, true);
            link.kind = dice.chance(0.4) ? LinkKind.OPEN : LinkKind.DOOR;
            level.links.add(link);
        }
        if (lock) {
            // Never the entry's own links: the lock should be met, not stood beside.
            List<Link> candidates = new ArrayList<>();
            for (Link l : level.links) {
                if (l.a.role != Role.ENTRY && l.b.role != Role.ENTRY) {
                    candidates.add(l);
                }
            }
            if (!candidates.isEmpty()) {
                dice.pick(candidates).kind = LinkKind.LOCKED;
            } else {
                lock = false;
            }
        }

        // Branches: chains of one or two rooms hanging off loop rooms other than the entry.
        List<Room[]> branches = new ArrayList<>(); // {parent, child}
        boolean keyPlaced = false;
        int secrets = 0;
        for (int b = 0; b < branchCount; b++) {
            Room parent = loop.get(1 + dice.nextInt(loopSize - 1));
            int length = dice.chance(0.35) ? 2 : 1;
            for (int k = 0; k < length; k++) {
                boolean leaf = k == length - 1;
                Role role;
                if (!leaf) {
                    role = dice.chance(0.5) ? Role.GUARD : Role.ROOM;
                } else if (lock && !keyPlaced) {
                    role = Role.KEY;
                    keyPlaced = true;
                } else if (secrets < (hollow ? 2 : 1) && dice.chance(hollow ? 0.8 : 0.45)) {
                    role = Role.SECRET;
                    secrets++;
                } else {
                    role = dice.chance(0.5) ? Role.TREASURE : Role.SHRINE;
                }
                Room child = makeRoom(level.rooms.size(), role, theme, dice, level);
                LinkKind kind = role == Role.SECRET ? LinkKind.SECRET
                        : dice.chance(0.5) ? LinkKind.DOOR : LinkKind.OPEN;
                // The first room of a branch often joins the nearest corridor instead of its
                // parent's wall: that is what makes T-junctions.
                if (k == 0 && dice.chance(0.5)) {
                    Link spur = new Link(child, parent, kind, false);
                    spur.spur = true;
                    level.links.add(spur);
                } else {
                    level.links.add(new Link(parent, child, kind, false));
                }
                branches.add(new Room[] {parent, child});
                parent = child;
            }
        }
        if (lock && !keyPlaced) {
            for (Link l : level.links) {
                if (l.kind == LinkKind.LOCKED) {
                    l.kind = LinkKind.DOOR;
                }
            }
        }

        // ---- Layout ----
        Room entry = loop.get(0);
        double meanSpan = 0;
        for (Room r : loop) {
            meanSpan += Math.max(r.w, r.h) + GAP + 4;
        }
        double radius = Math.max(16, meanSpan / (2 * Math.PI) * dice.range(0.95, 1.2));
        double toward;
        double ax = arrival[0];
        double az = arrival[1];
        if (Math.hypot(ax, az) < 8) {
            toward = dice.range(0, 2 * Math.PI);
        } else {
            toward = Math.atan2(-az, -ax) + dice.range(-1.0, 1.0);
        }
        double lim = LevelPlan.RADIUS - radius - 18;
        if (lim < 0) {
            return fail("radius");
        }
        double cx = clamp(ax + Math.cos(toward) * radius, -lim, lim);
        double cz = clamp(az + Math.sin(toward) * radius, -lim, lim);
        double theta0 = Math.atan2(az - cz, ax - cx);
        double turn = dice.chance(0.5) ? 1 : -1;
        double lumps = dice.between(2, 3);
        double phase = dice.range(0, 2 * Math.PI);
        double lumpiness = dice.range(0.1, 0.3);
        entry.cx = ax;
        entry.cz = az;
        entry.pinned = true;
        for (int i = 1; i < loopSize; i++) {
            double t = theta0 + turn * (2 * Math.PI * i / loopSize + dice.range(-0.2, 0.2) * 2 * Math.PI / loopSize);
            double r = radius * (1 + lumpiness * Math.sin(lumps * t + phase)) * dice.range(0.9, 1.1);
            loop.get(i).cx = cx + Math.cos(t) * r;
            loop.get(i).cz = cz + Math.sin(t) * r;
        }
        for (Room[] pc : branches) {
            Room parent = pc[0];
            Room child = pc[1];
            double out = Math.atan2(parent.cz - cz, parent.cx - cx) + dice.range(-0.9, 0.9);
            double dist = (Math.max(parent.w, parent.h) + Math.max(child.w, child.h)) / 2.0 + GAP + dice.between(2, 9);
            child.cx = parent.cx + Math.cos(out) * dist;
            child.cz = parent.cz + Math.sin(out) * dist;
        }
        // ---- Inner rooms ----
        // Rooms in the middle of the loop, joined to the loop rooms nearest them. Without these a
        // level reads as a ring of rooms round an empty middle; with them, paths cut across it.
        int inner = radius >= 24 ? Math.min(3, 1 + depth / 3 + dice.between(0, 1)) : 0;
        Room previousInner = null;
        for (int k = 0; k < inner; k++) {
            Role role = dice.chance(0.3) ? Role.GUARD : dice.chance(0.3) ? Role.SHRINE
                    : dice.chance(0.25) ? Role.TREASURE : Role.ROOM;
            Room room = makeRoom(level.rooms.size(), role, theme, dice, level);
            double a = dice.range(0, 2 * Math.PI);
            double r = radius * dice.range(0, 0.35);
            room.cx = cx + Math.cos(a) * r;
            room.cz = cz + Math.sin(a) * r;
            List<Room> near = new ArrayList<>(loop);
            near.sort(Comparator.comparingDouble(o -> Math.hypot(o.cx - room.cx, o.cz - room.cz)));
            Room anchor = near.get(0);
            level.links.add(new Link(anchor, room, dice.chance(0.5) ? LinkKind.DOOR : LinkKind.OPEN, false));
            branches.add(new Room[] {anchor, room}); // the first link is essential, like a branch's
            // A second way out, across the middle: to another loop room or the previous inner room.
            Room other = previousInner != null && dice.chance(0.4) ? previousInner
                    : near.get(1 + dice.nextInt(Math.min(3, near.size() - 1)));
            if (dice.chance(0.7)) {
                level.links.add(new Link(room, other, dice.chance(0.5) ? LinkKind.DOOR : LinkKind.OPEN, false));
            }
            previousInner = room;
        }
        if (!relax(level, dice)) {
            return fail("relax");
        }

        // ---- Shortcuts ----
        int shortcuts = dice.between(1, 2 + depth / 3);
        addShortcuts(level, dice, shortcuts + (hollow ? 1 : 0), hollow ? 0.6 : 0.25);

        // ---- Styles ----
        double[] styleWeights = theme.styleWeights();
        for (Link l : level.links) {
            l.style = CorridorStyle.values()[dice.weighted(styleWeights)];
            l.width = (l.kind == LinkKind.SECRET || dice.chance(theme.narrowChance())) ? 1 : 3;
        }

        // ---- Paint rooms, then route ----
        for (Room r : level.rooms) {
            for (int lx = 0; lx < r.w; lx++) {
                for (int lz = 0; lz < r.h; lz++) {
                    if (r.mask[lx][lz]) {
                        int x = r.minX() + lx;
                        int z = r.minZ() + lz;
                        level.set(x, z, Cell.FLOOR);
                        level.setRegion(x, z, r.id);
                    }
                }
            }
        }
        Router router = new Router(level, dice.nextLong());
        List<Link> order = new ArrayList<>(level.links);
        // Spurs last, so there are corridors for them to join.
        order.sort(Comparator.comparing((Link l) -> l.spur)
                .thenComparingDouble(l -> Math.hypot(l.a.cx - l.b.cx, l.a.cz - l.b.cz)));
        int loopLost = 0;
        for (Link l : order) {
            boolean ok = l.spur ? router.routeSpur(l, dice, joinableFrom(level, entry, l)) : router.route(l, dice);
            if (!ok && l.spur) {
                l.spur = false; // join the parent's wall after all
                ok = router.route(l, dice);
            }
            if (!ok && l.width == 3) {
                l.width = 1;
                ok = router.route(l, dice);
            }
            if (!ok) {
                if (l.loop) {
                    loopLost++;
                } else if (!isShortcut(l, branches)) {
                    return fail("branch"); // an unreachable branch: try again
                }
                level.links.remove(l);
            }
        }
        // A level that lost its loop is a tree; accept that only once better attempts have run out.
        if (loopLost > 0 && attempt <= ATTEMPTS / 2) {
            return fail("loop");
        }
        if (loopLost > 0) {
            for (Link l : level.links) {
                if (l.kind == LinkKind.LOCKED) {
                    l.kind = LinkKind.DOOR;
                    level.set(l.doorA[0], l.doorA[1], Cell.DOOR);
                }
            }
        }

        // ---- Finishing ----
        features(level, dice);
        walls(level);
        heights(level, dice);
        return level;
    }

    /** The corridors a spur may join: those of routed links already connected to the entry. */
    private static java.util.function.Predicate<Link> joinableFrom(LevelPlan level, Room entry, Link spur) {
        java.util.Set<Room> reached = new java.util.HashSet<>();
        ArrayDeque<Room> queue = new ArrayDeque<>();
        reached.add(entry);
        queue.add(entry);
        // Spurs are routed one at a time and each joins a connected corridor, so every routed spur's room is connected.
        for (Link l : level.links) {
            if (l.spur && l.path != null && reached.add(l.a)) {
                queue.add(l.a);
            }
        }
        while (!queue.isEmpty()) {
            Room r = queue.poll();
            for (Link l : level.links) {
                if (l.path == null || l.spur) {
                    continue;
                }
                Room other = l.a == r ? l.b : l.b == r ? l.a : null;
                if (other != null && reached.add(other)) {
                    queue.add(other);
                }
            }
        }
        return l -> l != null && l != spur && l.path != null && reached.contains(l.a);
    }

    private static int pickOther(Dice dice, int loopSize, int exitAt) {
        if (loopSize < 3) {
            return -1;
        }
        int i;
        do {
            i = 1 + dice.nextInt(loopSize - 1);
        } while (i == exitAt);
        return i;
    }

    private static boolean isShortcut(Link l, List<Room[]> branches) {
        for (Room[] pc : branches) {
            if ((l.a == pc[0] && l.b == pc[1]) || (l.a == pc[1] && l.b == pc[0])) {
                return false;
            }
        }
        return true;
    }

    static Room makeRoom(int id, Role role, Theme theme, Dice dice, LevelPlan level) {
        double scale = theme.roomScale();
        int lo;
        int hi;
        switch (role) {
            case ENTRY, EXIT -> { lo = 11; hi = 15; }
            case LAIR -> { lo = 15; hi = 23; }
            case TREASURE, SECRET, KEY -> { lo = 5; hi = 9; }
            case SHRINE -> { lo = 9; hi = 13; }
            default -> { lo = 9; hi = 15; }
        }
        Shape shape = Shape.values()[dice.weighted(theme.shapeWeights())];
        if (shape == Shape.HALL && role == Role.ROOM) {
            role = Role.HALL;
        }
        if (role == Role.HALL) {
            lo = 13;
            hi = 21;
        }
        if (shape == Shape.CAVE) {
            lo = Math.max(lo, 13);
            hi = Math.max(hi, 21);
        }
        int w = dice.oddBetween(lo, hi);
        int h = dice.oddBetween(lo, hi);
        if (role != Role.TREASURE && role != Role.SECRET && role != Role.KEY) {
            w = oddAtLeast((int) Math.round(w * scale), 5);
            h = oddAtLeast((int) Math.round(h * scale), 5);
        }
        // Long thin halls read better than squares; nudge one axis out.
        if (role == Role.HALL && dice.chance(0.5)) {
            if (dice.chance(0.5)) {
                w = oddAtLeast(w + 6, 5);
            } else {
                h = oddAtLeast(h + 6, 5);
            }
        }
        int small = Math.min(w, h);
        if (small < 9 && (shape == Shape.CROSS || shape == Shape.L || shape == Shape.CAVE || shape == Shape.HALL)) {
            shape = dice.chance(0.5) ? Shape.RECT : Shape.ROUNDED;
        }
        if (small < 7 && shape == Shape.OCTAGON) {
            shape = Shape.RECT;
        }
        int clear = (role == Role.ENTRY || role == Role.EXIT) ? 5 : 1;
        boolean[][] mask = RoomShapes.mask(shape, w, h, clear, dice);
        Room room = new Room(id, role, shape, w, h, mask);
        level.rooms.add(room);
        return room;
    }

    private static int oddAtLeast(int v, int min) {
        v = Math.max(v, min);
        return v % 2 == 0 ? v + 1 : v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Pushes overlapping rooms apart along their shallower axis and pulls
     * linked rooms that drifted far apart back together. Returns false if
     * rooms still overlap afterwards.
     */
    private static boolean relax(LevelPlan level, Dice dice) {
        List<Room> rooms = level.rooms;
        for (int iter = 0; iter < 400; iter++) {
            boolean overlap = false;
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    Room a = rooms.get(i);
                    Room b = rooms.get(j);
                    double dx = b.cx - a.cx;
                    double dz = b.cz - a.cz;
                    double needX = (a.w + b.w) / 2.0 + GAP + 1 - Math.abs(dx);
                    double needZ = (a.h + b.h) / 2.0 + GAP + 1 - Math.abs(dz);
                    if (needX <= 0 || needZ <= 0) {
                        continue;
                    }
                    overlap = true;
                    double sx = dx == 0 ? (dice.chance(0.5) ? 1 : -1) : Math.signum(dx);
                    double sz = dz == 0 ? (dice.chance(0.5) ? 1 : -1) : Math.signum(dz);
                    double shareA = a.pinned ? 0 : b.pinned ? 1 : 0.5;
                    double shareB = 1 - shareA;
                    if (needX < needZ) {
                        a.cx -= sx * (needX + 0.5) * shareA;
                        b.cx += sx * (needX + 0.5) * shareB;
                    } else {
                        a.cz -= sz * (needZ + 0.5) * shareA;
                        b.cz += sz * (needZ + 0.5) * shareB;
                    }
                }
            }
            for (Link l : level.links) {
                Room a = l.a;
                Room b = l.b;
                double dx = b.cx - a.cx;
                double dz = b.cz - a.cz;
                double dist = Math.hypot(dx, dz);
                double target = (Math.max(a.w, a.h) + Math.max(b.w, b.h)) / 2.0 + GAP + 10;
                if (dist > target * 1.6) {
                    double pull = (dist - target * 1.6) * 0.04 / dist;
                    double shareA = a.pinned ? 0 : b.pinned ? 1 : 0.5;
                    a.cx += dx * pull * shareA;
                    a.cz += dz * pull * shareA;
                    b.cx -= dx * pull * (1 - shareA);
                    b.cz -= dz * pull * (1 - shareA);
                }
            }
            for (Room r : rooms) {
                if (r.pinned) {
                    continue;
                }
                double limX = LevelPlan.RADIUS - r.w / 2.0 - 5;
                double limZ = LevelPlan.RADIUS - r.h / 2.0 - 5;
                r.cx = clamp(r.cx, -limX, limX);
                r.cz = clamp(r.cz, -limZ, limZ);
            }
            if (!overlap && iter > 20) {
                break;
            }
        }
        for (Room r : rooms) {
            r.snap();
        }
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                Room a = rooms.get(i);
                Room b = rooms.get(j);
                int gapX = Math.max(a.minX(), b.minX()) - Math.min(a.maxX(), b.maxX()) - 1;
                int gapZ = Math.max(a.minZ(), b.minZ()) - Math.min(a.maxZ(), b.maxZ()) - 1;
                if (gapX < GAP && gapZ < GAP) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How often a shortcut is a one-way door: a way back opened from the far end. */
    static final double ONEWAY_CHANCE = 0.35;

    /** Links rooms that are close on the map but at least three steps apart in the graph. */
    private static void addShortcuts(LevelPlan level, Dice dice, int count, double secretChance) {
        if (count <= 0) {
            return;
        }
        Map<Room, List<Room>> adj = new HashMap<>();
        for (Link l : level.links) {
            adj.computeIfAbsent(l.a, k -> new ArrayList<>()).add(l.b);
            adj.computeIfAbsent(l.b, k -> new ArrayList<>()).add(l.a);
        }
        List<Room[]> candidates = new ArrayList<>();
        for (Room a : level.rooms) {
            Map<Room, Integer> hops = hops(a, adj);
            for (Room b : level.rooms) {
                if (b.id <= a.id || a.role == Role.SECRET || b.role == Role.SECRET) {
                    continue;
                }
                int gx = Math.max(a.minX(), b.minX()) - Math.min(a.maxX(), b.maxX()) - 1;
                int gz = Math.max(a.minZ(), b.minZ()) - Math.min(a.maxZ(), b.maxZ()) - 1;
                int gap = Math.max(gx, gz);
                if (gap < 18 && hops.getOrDefault(b, 99) >= 3) {
                    candidates.add(new Room[] {a, b});
                }
            }
        }
        // How far each room is from the entry, before any shortcut: a one-way door's lever goes on the far side.
        Room entry = level.roomWith(Role.ENTRY);
        Map<Room, Integer> fromEntry = entry == null ? Map.of() : hops(entry, adj);
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            Room[] pair = candidates.remove(dice.nextInt(candidates.size()));
            int ha = fromEntry.getOrDefault(pair[0], -1);
            int hb = fromEntry.getOrDefault(pair[1], -1);
            boolean oneway = ha >= 0 && hb >= 0 && ha != hb && dice.chance(ONEWAY_CHANCE);
            LinkKind kind = oneway ? LinkKind.ONEWAY
                    : dice.chance(secretChance) ? LinkKind.SECRET : dice.chance(0.5) ? LinkKind.DOOR : LinkKind.OPEN;
            Room a = oneway && hb > ha ? pair[1] : pair[0];
            level.links.add(new Link(a, a == pair[0] ? pair[1] : pair[0], kind, false));
        }
    }

    private static Map<Room, Integer> hops(Room from, Map<Room, List<Room>> adj) {
        Map<Room, Integer> out = new HashMap<>();
        ArrayDeque<Room> queue = new ArrayDeque<>();
        out.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            Room r = queue.poll();
            for (Room n : adj.getOrDefault(r, List.of())) {
                if (!out.containsKey(n)) {
                    out.put(n, out.get(r) + 1);
                    queue.add(n);
                }
            }
        }
        return out;
    }

    /**
     * Hidden trap tiles, more of them deeper down: corridor floor and the floor of
     * guard and treasure rooms, never within three cells of a doorway, stair or pit,
     * so a trap is met in passing rather than stood on arriving.
     */
    static void traps(LevelPlan level, Dice dice) {
        int want = Math.min(12, 2 + level.index + dice.between(0, 2));
        if (level.feeling == Feeling.TRAPPED) {
            want = Math.min(24, want * 2);
        }
        List<int[]> spots = new ArrayList<>();
        int lim = LevelPlan.RADIUS - 1;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                Cell c = level.cell(x, z);
                int reg = level.region(x, z);
                boolean candidate = c == Cell.CORRIDOR
                        || (c == Cell.FLOOR && reg >= 0
                            && (level.room(reg).role == Role.GUARD || level.room(reg).role == Role.TREASURE));
                if (candidate && clearOf(level, x, z, 3)) {
                    spots.add(new int[] {x, z});
                }
            }
        }
        for (int i = 0; i < want && !spots.isEmpty(); i++) {
            int[] s = spots.remove(dice.nextInt(spots.size()));
            boolean spaced = true;
            for (int[] t : level.traps) {
                spaced &= Math.max(Math.abs(t[0] - s[0]), Math.abs(t[1] - s[1])) >= 6;
            }
            if (!spaced) {
                i--;
                continue;
            }
            TrapKind kind = TrapKind.roll(dice, level.index);
            level.traps.add(new int[] {s[0], s[1], kind.ordinal()});
        }
    }

    private static boolean clearOf(LevelPlan level, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Cell c = level.cell(x + dx, z + dz);
                if (c.isDoor() || c == Cell.STAIR_UP || c == Cell.STAIR_DOWN || c == Cell.PIT || c == Cell.POOL) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Stairwells, pillars and pools. */
    private static void features(LevelPlan level, Dice dice) {
        for (Room r : level.rooms) {
            int cx = r.centerX();
            int cz = r.centerZ();
            if (r.role == Role.ENTRY) {
                square(level, cx, cz, 1, Cell.STAIR_UP);
                level.stairsUp.add(new int[] {cx, cz});
            } else if (r.role == Role.EXIT) {
                square(level, cx, cz, 1, Cell.STAIR_DOWN);
                level.stairsDown.add(new int[] {cx, cz});
            } else if (r.shape == Shape.HALL && Math.min(r.w, r.h) >= 9) {
                pillars(level, r, dice);
            } else if ((r.role == Role.SHRINE || r.shape == Shape.CAVE
                        || level.feeling == Feeling.DAMP && (r.role == Role.ROOM || r.role == Role.GUARD || r.role == Role.TREASURE))
                    && Math.min(r.w, r.h) >= 9
                    && dice.chance(level.feeling == Feeling.DAMP ? 0.85
                        : r.role == Role.SHRINE ? 0.5 + level.theme.poolChance() : level.theme.poolChance())) {
                pool(level, r, dice);
            }
        }
    }

    private static void square(LevelPlan level, int cx, int cz, int r, Cell c) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                level.set(x, z, c);
            }
        }
    }

    /** A grid of single pillars, kept two cells in from every wall so no doorway is blocked. */
    private static void pillars(LevelPlan level, Room r, Dice dice) {
        int step = dice.chance(0.5) ? 3 : 4;
        List<Integer> xs = spread(r.w, step);
        List<Integer> zs = spread(r.h, step);
        boolean colonnade = dice.chance(0.5); // only the outer ring of pillars: an aisle down the middle
        for (int i = 0; i < xs.size(); i++) {
            for (int j = 0; j < zs.size(); j++) {
                boolean edge = i == 0 || j == 0 || i == xs.size() - 1 || j == zs.size() - 1;
                if (colonnade && !edge) {
                    continue;
                }
                int x = r.minX() + xs.get(i);
                int z = r.minZ() + zs.get(j);
                if (r.contains(x, z)) {
                    level.set(x, z, Cell.PILLAR);
                }
            }
        }
    }

    /** Positions from 2 to size-3, {@code step} apart, centred. */
    private static List<Integer> spread(int size, int step) {
        int span = size - 5;
        int count = span / step + 1;
        int start = 2 + (span - (count - 1) * step) / 2;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(start + i * step);
        }
        return out;
    }

    private static void pool(LevelPlan level, Room r, Dice dice) {
        double rx = Math.max(1.5, r.w / 4.0 * dice.range(0.8, 1.2));
        double rz = Math.max(1.5, r.h / 4.0 * dice.range(0.8, 1.2));
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                double dx = (x - r.cx) / rx;
                double dz = (z - r.cz) / rz;
                if (dx * dx + dz * dz <= 1 && r.contains(x, z) && insetFloor(level, r, x, z, 2)) {
                    level.set(x, z, Cell.POOL);
                }
            }
        }
        r.pool = true;
    }

    /** Whether every cell within {@code d} of (x, z) is this room's floor. */
    private static boolean insetFloor(LevelPlan level, Room r, int x, int z, int d) {
        for (int i = -d; i <= d; i++) {
            for (int j = -d; j <= d; j++) {
                if (!r.contains(x + i, z + j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Every rock cell touching open space (8 ways) becomes wall. */
    static void walls(LevelPlan level) {
        int lim = LevelPlan.RADIUS;
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                if (level.cell(x, z) != Cell.ROCK) {
                    continue;
                }
                search:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (level.cell(x + dx, z + dz).isOpen()) {
                            level.set(x, z, Cell.WALL);
                            break search;
                        }
                    }
                }
            }
        }
    }

    /**
     * Room floors are chosen; corridor floors are solved. Each corridor cell
     * settles to the average of its neighbours with room floors held fixed (a
     * Laplace solve), which turns a corridor between a raised room and a
     * sunken one into an even ramp. If rounding leaves a step taller than one
     * block anywhere walkable, the level is flattened instead.
     */
    private static void heights(LevelPlan level, Dice dice) {
        int spread = level.theme.heightSpread();
        for (Room r : level.rooms) {
            boolean fixed = r.role == Role.ENTRY || r.role == Role.EXIT;
            r.floor = (fixed || spread == 0 || dice.chance(0.5)) ? 0 : dice.between(-spread, spread);
        }
        if (!solveHeights(level)) {
            for (Room r : level.rooms) {
                r.floor = 0;
            }
            solveHeights(level);
        }
    }

    private static boolean solveHeights(LevelPlan level) {
        int lim = LevelPlan.RADIUS;
        List<int[]> free = new ArrayList<>();
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                int reg = level.region(x, z);
                if (reg >= 0) {
                    level.setHeight(x, z, level.room(reg).floor);
                } else if (level.cell(x, z).isDoor()) {
                    // A doorway sits level with its room. Solved like corridor, it could come out a block
                    // higher, and a step up into a two-high doorway cannot be jumped: your head hits the
                    // lintel (Sable, at an open door he could not walk through).
                    level.setHeight(x, z, doorRoomFloor(level, x, z));
                }
            }
        }
        // Corridor cells, in a second pass so every doorway's height is known. One beside a doorway
        // is held level with it, an apron: a stair next to a door cannot be climbed either way (Sable,
        // seed 774, a stair outside a door that blocked it), so the first step must be a cell further on.
        java.util.Set<Long> apron = new java.util.HashSet<>();
        int[][] four = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = -lim; x <= lim; x++) {
            for (int z = -lim; z <= lim; z++) {
                if (level.region(x, z) != LevelPlan.CORRIDOR || level.cell(x, z).isDoor()) {
                    continue;
                }
                Integer door = null;
                for (int[] d : four) {
                    if (level.cell(x + d[0], z + d[1]).isDoor()) {
                        door = level.height(x + d[0], z + d[1]);
                        break;
                    }
                }
                if (door != null) {
                    level.setHeight(x, z, door);
                    apron.add(key(x, z));
                } else {
                    free.add(new int[] {x, z});
                    level.setHeight(x, z, 0);
                }
            }
        }
        boolean anyRaised = false;
        for (Room r : level.rooms) {
            anyRaised |= r.floor != 0;
        }
        if (anyRaised && !free.isEmpty()) {
            double[] v = new double[free.size()];
            Map<Long, Integer> slot = new HashMap<>();
            for (int i = 0; i < free.size(); i++) {
                slot.put(key(free.get(i)[0], free.get(i)[1]), i);
            }
            int[][] nbr = new int[free.size()][];
            double[] fixedSum = new double[free.size()];
            int[] count = new int[free.size()];
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int i = 0; i < free.size(); i++) {
                int[] c = free.get(i);
                List<Integer> ns = new ArrayList<>();
                for (int[] d : dirs) {
                    int x = c[0] + d[0];
                    int z = c[1] + d[1];
                    int reg = level.region(x, z);
                    if (reg >= 0) {
                        fixedSum[i] += level.room(reg).floor;
                        count[i]++;
                    } else if (level.cell(x, z).isDoor() || apron.contains(key(x, z))) {
                        fixedSum[i] += level.height(x, z);
                        count[i]++;
                    } else if (reg == LevelPlan.CORRIDOR) {
                        ns.add(slot.get(key(x, z)));
                        count[i]++;
                    }
                }
                nbr[i] = ns.stream().mapToInt(Integer::intValue).toArray();
            }
            double omega = 1.85;
            for (int iter = 0; iter < 3000; iter++) {
                double change = 0;
                for (int i = 0; i < v.length; i++) {
                    if (count[i] == 0) {
                        continue;
                    }
                    double sum = fixedSum[i];
                    for (int n : nbr[i]) {
                        sum += v[n];
                    }
                    double target = sum / count[i];
                    double nv = v[i] + omega * (target - v[i]);
                    change = Math.max(change, Math.abs(nv - v[i]));
                    v[i] = nv;
                }
                if (change < 1e-3) {
                    break;
                }
            }
            for (int i = 0; i < v.length; i++) {
                level.setHeight(free.get(i)[0], free.get(i)[1], (int) Math.round(v[i]));
            }
        }
        return PlanCheck.maxStep(level) <= 1;
    }

    /** The floor of the room a doorway opens into. */
    private static int doorRoomFloor(LevelPlan level, int x, int z) {
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int reg = level.region(x + d[0], z + d[1]);
            if (reg >= 0) {
                return level.room(reg).floor;
            }
        }
        return 0;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
