package com.sablednah.crawlspace.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes and paints corridors between rooms already painted on a level.
 *
 * <p>The search is A* over (cell, heading), so a turn can be priced: that one
 * cost is what separates a HeroQuest corridor (square turns, expensive) from a
 * worn tunnel (cheap turns over a noise field). Cells already holding a
 * corridor are cheap to reuse, which is where T and cross junctions come from
 * without anybody placing one. Cells just beside an existing corridor are
 * dear, so two corridors do not run fused side by side.</p>
 *
 * <p>Clearance is measured, not hoped for: a corridor's centre line stays at
 * least {@code 2 + halfWidth} cells (Chebyshev) from every room floor, so a
 * wall always survives between a corridor and a room it is not entering.</p>
 */
final class Router {

    private static final int S = LevelPlan.SIZE;
    private static final int R = LevelPlan.RADIUS;
    /** Headings, clockwise from east: E, SE, S, SW, W, NW, N, NE. */
    static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int CAP = 16;
    private static final int MAX_EXPANSIONS = 700_000;

    private final LevelPlan level;
    private final int[] roomDist = new int[S * S];
    private final int[] corrDist = new int[S * S];
    private final boolean[] corr = new boolean[S * S];
    /** Which link first painted each corridor cell. */
    private final Link[] owner = new Link[S * S];
    /** While routing a spur: the corridors it may join. */
    private java.util.function.Predicate<Link> joinable = l -> true;
    private final float[] noise;
    private final float[] g = new float[S * S * 8];
    private final int[] parent = new int[S * S * 8];
    private final boolean[] closed = new boolean[S * S * 8];
    private final Map<Room, List<int[]>> doors = new HashMap<>();

    Router(LevelPlan level, long noiseSeed) {
        this.level = level;
        boolean[] rooms = new boolean[S * S];
        for (int i = 0; i < S * S; i++) {
            rooms[i] = level.region[i / S][i % S] >= 0;
        }
        distance(rooms, roomDist);
        Arrays.fill(corrDist, CAP);
        this.noise = valueNoise(noiseSeed, 9);
    }

    static int idx(int x, int z) {
        return (x + R) * S + (z + R);
    }

    /** Multi-source Chebyshev distance, capped at {@link #CAP}. */
    private static void distance(boolean[] source, int[] out) {
        int[] queue = new int[S * S];
        int head = 0;
        int tail = 0;
        for (int i = 0; i < S * S; i++) {
            if (source[i]) {
                out[i] = 0;
                queue[tail++] = i;
            } else {
                out[i] = CAP;
            }
        }
        while (head < tail) {
            int c = queue[head++];
            int d = out[c] + 1;
            if (d >= CAP) {
                continue;
            }
            int ax = c / S;
            int az = c % S;
            for (int k = 0; k < 8; k++) {
                int nx = ax + DX[k];
                int nz = az + DZ[k];
                if (nx < 0 || nz < 0 || nx >= S || nz >= S) {
                    continue;
                }
                int n = nx * S + nz;
                if (out[n] > d) {
                    out[n] = d;
                    queue[tail++] = n;
                }
            }
        }
    }

    /** Smooth value noise in [0, 1], one lattice point every {@code cell} blocks. */
    private static float[] valueNoise(long seed, int cell) {
        int n = S / cell + 2;
        float[] lattice = new float[n * n];
        Dice dice = new Dice(seed);
        for (int i = 0; i < lattice.length; i++) {
            lattice[i] = (float) dice.nextDouble();
        }
        float[] out = new float[S * S];
        for (int ax = 0; ax < S; ax++) {
            for (int az = 0; az < S; az++) {
                int gx = ax / cell;
                int gz = az / cell;
                float fx = smooth((ax % cell) / (float) cell);
                float fz = smooth((az % cell) / (float) cell);
                float a = lattice[gx * n + gz];
                float b = lattice[(gx + 1) * n + gz];
                float c = lattice[gx * n + gz + 1];
                float d = lattice[(gx + 1) * n + gz + 1];
                out[ax * S + az] = (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
            }
        }
        return out;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Whether a corridor of half-width {@code h} may have its centre line at (x, z). */
    boolean passable(int x, int z, int h) {
        int lim = R - h - 2;
        if (x < -lim || z < -lim || x > lim || z > lim) {
            return false;
        }
        return roomDist[idx(x, z)] >= 2 + h;
    }

    static int dirOf(int dx, int dz) {
        for (int k = 0; k < 8; k++) {
            if (DX[k] == Integer.signum(dx) && DZ[k] == Integer.signum(dz)) {
                return k;
            }
        }
        throw new IllegalArgumentException(dx + "," + dz);
    }

    static int turnSteps(int a, int b) {
        int d = Math.abs(a - b) % 8;
        return Math.min(d, 8 - d);
    }

    /**
     * Routes and paints {@code link}. Returns false, leaving the level
     * untouched, if no doorway or path could be found.
     */
    boolean route(Link link, Dice dice) {
        int h = link.width / 2;
        int[] da = socket(link.a, link.b.cx, link.b.cz, h, dice);
        if (da == null) {
            return false;
        }
        int[] db = socket(link.b, link.a.cx, link.a.cz, h, dice);
        if (db == null) {
            return false;
        }
        int sx = da[0] + da[2] * (1 + h);
        int sz = da[1] + da[3] * (1 + h);
        int tx = db[0] + db[2] * (1 + h);
        int tz = db[1] + db[3] * (1 + h);
        List<int[]> centres = search(sx, sz, dirOf(da[2], da[3]), tx, tz, dirOf(-db[2], -db[3]), h, link.style);
        if (centres == null) {
            return false;
        }
        if (link.style == CorridorStyle.CURVED) {
            List<int[]> curved = curve(centres, h);
            if (curved != null) {
                centres = curved;
            }
        }

        List<int[]> painted = new ArrayList<>();
        stub(da, h, painted);
        stub(db, h, painted);
        brush(centres, h, painted);
        door(da, link.kind == LinkKind.LOCKED ? Cell.DOOR_LOCKED
                : link.kind == LinkKind.ONEWAY ? Cell.DOOR_ONEWAY
                : link.kind == LinkKind.SECRET ? Cell.DOOR_SECRET
                : link.kind == LinkKind.OPEN ? Cell.ARCH : Cell.DOOR);
        door(db, link.kind == LinkKind.OPEN ? Cell.ARCH : Cell.DOOR);
        doors.computeIfAbsent(link.a, k -> new ArrayList<>()).add(da);
        doors.computeIfAbsent(link.b, k -> new ArrayList<>()).add(db);

        own(painted, link);
        link.doorA = da;
        link.doorB = db;
        link.path = centres.toArray(new int[0][]);
        return true;
    }

    private void own(List<int[]> painted, Link link) {
        for (int[] p : painted) {
            int i = idx(p[0], p[1]);
            corr[i] = true;
            owner[i] = link;
        }
        distance(corr, corrDist);
    }

    /**
     * Routes {@code link.a} to the nearest corridor that {@code joinable}
     * accepts: a spur, joining at a T. The caller passes only corridors already
     * connected to the entry; the nearest corridor is often the spur's own
     * branch, and joining that makes an island. Returns false, leaving the
     * level untouched, if it cannot.
     */
    boolean routeSpur(Link link, Dice dice, java.util.function.Predicate<Link> joinable) {
        this.joinable = joinable;
        try {
            return spur(link, dice);
        } finally {
            this.joinable = l -> true;
        }
    }

    private boolean spur(Link link, Dice dice) {
        int h = link.width / 2;
        Room room = link.a;
        // Aim at the nearest corridor cell that a corridor of this width could join.
        int[] aim = null;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < S * S; i++) {
            if (!corr[i] || !joinable.test(owner[i])) {
                continue;
            }
            int x = i / S - R;
            int z = i % S - R;
            if (!passable(x, z, h)) {
                continue;
            }
            double d = Math.hypot(x - room.cx, z - room.cz);
            if (d < best) {
                best = d;
                aim = new int[] {x, z};
            }
        }
        if (aim == null) {
            return false;
        }
        int[] da = socket(room, aim[0], aim[1], h, dice);
        if (da == null) {
            return false;
        }
        int sx = da[0] + da[2] * (1 + h);
        int sz = da[1] + da[3] * (1 + h);
        List<int[]> centres = search(sx, sz, dirOf(da[2], da[3]), Integer.MIN_VALUE, 0, -1, h, link.style);
        if (centres == null) {
            return false;
        }
        if (link.style == CorridorStyle.CURVED) {
            List<int[]> curved = curve(centres, h);
            if (curved != null) {
                centres = curved;
            }
        }
        List<int[]> painted = new ArrayList<>();
        stub(da, h, painted);
        brush(centres, h, painted);
        door(da, link.kind == LinkKind.SECRET ? Cell.DOOR_SECRET
                : link.kind == LinkKind.OPEN ? Cell.ARCH : Cell.DOOR);
        doors.computeIfAbsent(room, k -> new ArrayList<>()).add(da);
        own(painted, link);
        link.doorA = da;
        link.path = centres.toArray(new int[0][]);
        return true;
    }

    /**
     * Picks a doorway in {@code room}'s wall facing (tx, tz): a floor cell on a
     * straight stretch of wall, so the door has wall either side of it.
     *
     * @return {x, z, nx, nz}: the doorway cell (in the wall) and the outward normal
     */
    private int[] socket(Room room, double tx, double tz, int h, Dice dice) {
        double dx = tx - room.cx;
        double dz = tz - room.cz;
        double len = Math.max(1e-6, Math.hypot(dx, dz));
        dx /= len;
        dz /= len;
        int[][] normals = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        Integer[] order = {0, 1, 2, 3};
        final double fdx = dx;
        final double fdz = dz;
        Arrays.sort(order, (p, q) -> Double.compare(
                -(normals[p][0] * fdx + normals[p][1] * fdz),
                -(normals[q][0] * fdx + normals[q][1] * fdz)));
        List<int[]> existing = doors.getOrDefault(room, List.of());

        int[] best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < 4; k++) {
            if (k >= 2 && best != null) {
                break;
            }
            int nx = normals[order[k]][0];
            int nz = normals[order[k]][1];
            int px = -nz;
            int pz = nx;
            for (int lx = 0; lx < room.w; lx++) {
                for (int lz = 0; lz < room.h; lz++) {
                    if (!room.mask[lx][lz]) {
                        continue;
                    }
                    int bx = room.minX() + lx;
                    int bz = room.minZ() + lz;
                    if (room.contains(bx + nx, bz + nz) || !room.contains(bx - nx, bz - nz)
                            || !room.contains(bx + px, bz + pz) || !room.contains(bx - px, bz - pz)
                            || room.contains(bx + nx + px, bz + nz + pz) || room.contains(bx + nx - px, bz + nz - pz)) {
                        continue;
                    }
                    int wx = bx + nx;
                    int wz = bz + nz;
                    if (!doorwayClear(room, wx, wz, nx, nz, h, existing)) {
                        continue;
                    }
                    double score = (bx - room.cx) * dx + (bz - room.cz) * dz
                            + (nx * dx + nz * dz) * 6 + dice.range(0, 2.5);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[] {wx, wz, nx, nz};
                    }
                }
            }
        }
        return best;
    }

    private boolean doorwayClear(Room room, int wx, int wz, int nx, int nz, int h, List<int[]> existing) {
        for (int[] d : existing) {
            if (Math.max(Math.abs(d[0] - wx), Math.abs(d[1] - wz)) < 3) {
                return false;
            }
        }
        if (!LevelPlan.inBounds(wx, wz) || level.cell(wx, wz) != Cell.ROCK) {
            return false;
        }
        // The doorway may touch only its own room.
        for (int k = 0; k < 8; k++) {
            int r = level.region(wx + DX[k], wz + DZ[k]);
            if (r >= 0 && r != room.id) {
                return false;
            }
        }
        for (int s = 1; s <= 1 + h; s++) {
            int x = wx + nx * s;
            int z = wz + nz * s;
            if (!LevelPlan.inBounds(x, z) || roomDist[idx(x, z)] < 2) {
                return false;
            }
        }
        return passable(wx + nx * (1 + h), wz + nz * (1 + h), h);
    }

    /**
     * A* over (cell, heading). Returns centre cells from start to target, or
     * null. A target x of {@code Integer.MIN_VALUE} means "any existing
     * corridor": the search ends on the first corridor cell it reaches.
     */
    private List<int[]> search(int sx, int sz, int sd, int tx, int tz, int td, int h, CorridorStyle style) {
        boolean toCorridor = tx == Integer.MIN_VALUE;
        if (!passable(sx, sz, h) || (!toCorridor && !passable(tx, tz, h))) {
            return null;
        }
        Arrays.fill(g, Float.MAX_VALUE);
        Arrays.fill(closed, false);
        boolean orth = style == CorridorStyle.STRAIGHT;
        float turnCost = switch (style) {
            case STRAIGHT -> 4f;
            case ANGLED, CURVED -> 2.5f;
            case WINDING -> 0.7f;
        };
        Heap heap = new Heap();
        int start = idx(sx, sz) * 8 + sd;
        g[start] = 0;
        parent[start] = -1;
        heap.push(start, toCorridor ? corrDist[idx(sx, sz)] : octile(sx, sz, tx, tz));
        int expansions = 0;
        while (!heap.isEmpty()) {
            int st = heap.pop();
            if (closed[st]) {
                continue;
            }
            closed[st] = true;
            int cell = st >> 3;
            int d = st & 7;
            int x = cell / S - R;
            int z = cell % S - R;
            if (toCorridor ? (corr[cell] && joinable.test(owner[cell])) : (x == tx && z == tz)) {
                return trace(st);
            }
            if (++expansions > MAX_EXPANSIONS) {
                return null;
            }
            for (int nd = 0; nd < 8; nd++) {
                if (orth && (nd & 1) == 1) {
                    continue;
                }
                int t = turnSteps(d, nd);
                if (t > 2) {
                    continue;
                }
                int nx = x + DX[nd];
                int nz = z + DZ[nd];
                if (!passable(nx, nz, h)) {
                    continue;
                }
                boolean diagonal = (nd & 1) == 1;
                if (diagonal && h == 0 && !passable(x + DX[nd], z, 0) && !passable(x, z + DZ[nd], 0)) {
                    continue;
                }
                int ni = idx(nx, nz);
                float step = diagonal ? 1.4142f : 1f;
                float mult = 1f;
                if (style == CorridorStyle.WINDING) {
                    mult += 2.4f * noise[ni];
                }
                if (corr[ni]) {
                    mult *= 0.5f;
                } else if (corrDist[ni] <= h + 1) {
                    step += 1.5f;
                }
                float cost = step * mult + turnCost * t;
                if (!toCorridor && nx == tx && nz == tz) {
                    int te = turnSteps(nd, td);
                    if (te > 2 || (orth && te == 1)) {
                        continue;
                    }
                    cost += turnCost * te;
                }
                int ns = ni * 8 + nd;
                float ng = g[st] + cost;
                if (ng < g[ns]) {
                    g[ns] = ng;
                    parent[ns] = st;
                    heap.push(ns, ng + (toCorridor ? corrDist[ni] : octile(nx, nz, tx, tz)));
                }
            }
        }
        return null;
    }

    private List<int[]> trace(int st) {
        List<int[]> out = new ArrayList<>();
        for (int s = st; s != -1; s = parent[s]) {
            int cell = s >> 3;
            out.add(new int[] {cell / S - R, cell % S - R});
        }
        java.util.Collections.reverse(out);
        return out;
    }

    private static float octile(int x, int z, int tx, int tz) {
        int dx = Math.abs(tx - x);
        int dz = Math.abs(tz - z);
        return Math.max(dx, dz) + 0.4142f * Math.min(dx, dz);
    }

    /**
     * Smooths a routed path into curves (Chaikin's corner cutting on its turning
     * points), or returns null if the curve would cut into a room's clearance.
     */
    private List<int[]> curve(List<int[]> centres, int h) {
        if (centres.size() < 4) {
            return null;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {centres.get(0)[0], centres.get(0)[1]});
        for (int i = 1; i < centres.size() - 1; i++) {
            int[] a = centres.get(i - 1);
            int[] b = centres.get(i);
            int[] c = centres.get(i + 1);
            if (b[0] - a[0] != c[0] - b[0] || b[1] - a[1] != c[1] - b[1]) {
                pts.add(new double[] {b[0], b[1]});
            }
        }
        int[] last = centres.get(centres.size() - 1);
        pts.add(new double[] {last[0], last[1]});
        for (int pass = 0; pass < 3; pass++) {
            List<double[]> next = new ArrayList<>();
            next.add(pts.get(0));
            for (int i = 0; i < pts.size() - 1; i++) {
                double[] p = pts.get(i);
                double[] q = pts.get(i + 1);
                if (i > 0) {
                    next.add(new double[] {0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]});
                }
                if (i < pts.size() - 2) {
                    next.add(new double[] {0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]});
                }
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
        }
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] p = pts.get(i);
            double[] q = pts.get(i + 1);
            double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
            int steps = Math.max(1, (int) Math.ceil(len / 0.25));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                int x = (int) Math.round(p[0] + (q[0] - p[0]) * t);
                int z = (int) Math.round(p[1] + (q[1] - p[1]) * t);
                int[] prev = out.isEmpty() ? null : out.get(out.size() - 1);
                if (prev != null && prev[0] == x && prev[1] == z) {
                    continue;
                }
                if (!passable(x, z, h)) {
                    return null;
                }
                if (prev != null && h == 0 && prev[0] != x && prev[1] != z
                        && !passable(x, prev[1], 0) && !passable(prev[0], z, 0)) {
                    return null;
                }
                out.add(new int[] {x, z});
            }
        }
        return out;
    }

    /** The cells from just outside a doorway to where the corridor's centre line starts. */
    private void stub(int[] d, int h, List<int[]> painted) {
        for (int s = 1; s <= 1 + h; s++) {
            paint(d[0] + d[2] * s, d[1] + d[3] * s, painted);
        }
    }

    private void brush(List<int[]> centres, int h, List<int[]> painted) {
        int[] prev = null;
        for (int[] c : centres) {
            for (int dx = -h; dx <= h; dx++) {
                for (int dz = -h; dz <= h; dz++) {
                    paint(c[0] + dx, c[1] + dz, painted);
                }
            }
            // A one-wide diagonal is two cells touching at a corner, which nobody can walk through.
            if (h == 0 && prev != null && prev[0] != c[0] && prev[1] != c[1]) {
                if (passable(c[0], prev[1], 0)) {
                    paint(c[0], prev[1], painted);
                } else {
                    paint(prev[0], c[1], painted);
                }
            }
            prev = c;
        }
    }

    private void paint(int x, int z, List<int[]> painted) {
        if (level.cell(x, z) == Cell.ROCK) {
            level.set(x, z, Cell.CORRIDOR);
            level.setRegion(x, z, LevelPlan.CORRIDOR);
            painted.add(new int[] {x, z});
        }
    }

    private void door(int[] d, Cell kind) {
        level.set(d[0], d[1], kind);
        level.setRegion(d[0], d[1], LevelPlan.CORRIDOR);
    }

    /** A binary min-heap of int states keyed by float priority. */
    private static final class Heap {
        private int[] items = new int[1024];
        private float[] keys = new float[1024];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void push(int item, float key) {
            if (size == items.length) {
                items = Arrays.copyOf(items, size * 2);
                keys = Arrays.copyOf(keys, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (keys[p] <= key) {
                    break;
                }
                items[i] = items[p];
                keys[i] = keys[p];
                i = p;
            }
            items[i] = item;
            keys[i] = key;
        }

        int pop() {
            int top = items[0];
            int item = items[--size];
            float key = keys[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size) {
                    break;
                }
                int r = l + 1;
                int c = (r < size && keys[r] < keys[l]) ? r : l;
                if (keys[c] >= key) {
                    break;
                }
                items[i] = items[c];
                keys[i] = keys[c];
                i = c;
            }
            items[i] = item;
            keys[i] = key;
            return top;
        }
    }
}
