package com.sablednah.crawlspace.plan;

import java.util.ArrayDeque;

/**
 * Turns a {@link Shape} and a size into a floor mask. Every mask returned is
 * one 4-connected piece and contains the room's centre cell, with a clear
 * {@code clear}-wide square around it so a stairwell always fits.
 */
public final class RoomShapes {

    private RoomShapes() {
    }

    /**
     * @param clear side of the square kept open around the centre (0 for none)
     */
    public static boolean[][] mask(Shape shape, int w, int h, int clear, Dice dice) {
        boolean[][] m = switch (shape) {
            case RECT, HALL -> rect(w, h);
            case ROUNDED -> rounded(w, h);
            case CIRCLE -> ellipse(w, h);
            case OCTAGON -> octagon(w, h);
            case CROSS -> cross(w, h, dice);
            case L -> ell(w, h, dice);
            case CAVE -> cave(w, h, dice);
        };
        int cx = w / 2;
        int cz = h / 2;
        int r = clear / 2;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (x >= 0 && z >= 0 && x < w && z < h) {
                    m[x][z] = true;
                }
            }
        }
        return keepCentrePiece(m);
    }

    static boolean[][] rect(int w, int h) {
        boolean[][] m = new boolean[w][h];
        for (boolean[] col : m) {
            java.util.Arrays.fill(col, true);
        }
        return m;
    }

    static boolean[][] rounded(int w, int h) {
        boolean[][] m = rect(w, h);
        int r = Math.max(1, Math.min(w, h) / 4);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                int dx = Math.max(0, Math.max(r - x, x - (w - 1 - r)));
                int dz = Math.max(0, Math.max(r - z, z - (h - 1 - r)));
                if (dx * dx + dz * dz > r * r) {
                    m[x][z] = false;
                }
            }
        }
        return m;
    }

    static boolean[][] ellipse(int w, int h) {
        boolean[][] m = new boolean[w][h];
        double rx = w / 2.0;
        double rz = h / 2.0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                double dx = (x + 0.5 - rx) / rx;
                double dz = (z + 0.5 - rz) / rz;
                m[x][z] = dx * dx + dz * dz <= 1.0;
            }
        }
        return m;
    }

    static boolean[][] octagon(int w, int h) {
        boolean[][] m = rect(w, h);
        int c = Math.max(1, Math.min(w, h) / 3);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                int dx = Math.min(x, w - 1 - x);
                int dz = Math.min(z, h - 1 - z);
                if (dx + dz < c) {
                    m[x][z] = false;
                }
            }
        }
        return m;
    }

    static boolean[][] cross(int w, int h, Dice dice) {
        boolean[][] m = new boolean[w][h];
        int bw = Math.max(3, odd(w / 3 + dice.between(0, 2)));
        int bh = Math.max(3, odd(h / 3 + dice.between(0, 2)));
        int x0 = (w - bw) / 2;
        int z0 = (h - bh) / 2;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                m[x][z] = (x >= x0 && x < x0 + bw) || (z >= z0 && z < z0 + bh);
            }
        }
        return m;
    }

    static boolean[][] ell(int w, int h, Dice dice) {
        boolean[][] m = rect(w, h);
        // Cut one corner quadrant, leaving arms at least 3 wide and the centre intact.
        int cutW = Math.max(0, Math.min(w / 2 - 1, w - 4));
        int cutH = Math.max(0, Math.min(h / 2 - 1, h - 4));
        boolean east = dice.chance(0.5);
        boolean south = dice.chance(0.5);
        for (int x = 0; x < cutW; x++) {
            for (int z = 0; z < cutH; z++) {
                m[east ? w - 1 - x : x][south ? h - 1 - z : z] = false;
            }
        }
        return m;
    }

    static boolean[][] cave(int w, int h, Dice dice) {
        boolean[][] inside = ellipse(w, h);
        boolean[][] m = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                m[x][z] = inside[x][z] && dice.chance(0.58);
            }
        }
        // The classic 4-5 rule: a cell is open if most of its neighbourhood is.
        for (int pass = 0; pass < 4; pass++) {
            boolean[][] next = new boolean[w][h];
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < h; z++) {
                    int open = 0;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx >= 0 && nz >= 0 && nx < w && nz < h && m[nx][nz]) {
                                open++;
                            }
                        }
                    }
                    next[x][z] = inside[x][z] && open >= 5;
                }
            }
            m = next;
        }
        // Too eaten away to read as a room: fall back to the ellipse it grew in.
        int area = 0;
        int full = 0;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < h; z++) {
                if (m[x][z]) {
                    area++;
                }
                if (inside[x][z]) {
                    full++;
                }
            }
        }
        if (area < full * 0.45) {
            return inside;
        }
        // Force the centre open so the kept piece is the middle of the cave.
        int r = Math.min(w, h) / 5;
        for (int x = w / 2 - r; x <= w / 2 + r; x++) {
            for (int z = h / 2 - r; z <= h / 2 + r; z++) {
                m[x][z] = true;
            }
        }
        return m;
    }

    /** Keeps the 4-connected piece containing the centre; drops islands. */
    static boolean[][] keepCentrePiece(boolean[][] m) {
        int w = m.length;
        int h = m[0].length;
        boolean[][] keep = new boolean[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {w / 2, h / 2});
        keep[w / 2][h / 2] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int x = c[0] + d[0];
                int z = c[1] + d[1];
                if (x >= 0 && z >= 0 && x < w && z < h && m[x][z] && !keep[x][z]) {
                    keep[x][z] = true;
                    queue.add(new int[] {x, z});
                }
            }
        }
        return keep;
    }

    private static int odd(int v) {
        return v % 2 == 0 ? v + 1 : v;
    }
}
