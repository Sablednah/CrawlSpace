package com.sablednah.crawlspace.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Room;

/**
 * The objective rooms' set pieces, after Warhammer Quest's room cards (Sable,
 * 2026-10-12: "they are a bit plain... the dragon head in place of a full
 * statue"). Each is built in a room already cleared to its floor, and the way
 * out is part of it: set in the dragon's plinth, the fountain's rim, the
 * idol's dais, the tomb's head, the pit's gong. Until its job is done the frame
 * wears the block around it (Blueprint.disguise).
 *
 * <p>Statues are templates: layers bottom up, each row from the back (against
 * the wall) to the front, written facing south and turned to face the room.
 * Letters are block states as /setblock takes them; '.' leaves the cell, and a
 * few letters are places the room needs to know (the exit, the dragon's head,
 * where the shaman stands).</p>
 */
final class SetPieces {

    /** What a set piece tells its trigger: the exit frame, the job's anchor, and the job's own places. */
    static final class Built {
        int[] exit;
        int[] anchor;
        final List<int[]> places = new ArrayList<>();
    }

    private record Template(Map<Character, String> legend, String[][] layers) {
        int width() {
            return layers[0][0].length();
        }
    }

    private static Map<Character, String> legend(String... pairs) {
        Map<Character, String> m = new HashMap<>();
        for (int k = 0; k < pairs.length; k += 2) {
            m.put(pairs[k].charAt(0), pairs[k + 1]);
        }
        return m;
    }

    /** The Firechasm's dragon: seated on a plinth against the far wall, wings spread up the wall, head over the steps. */
    private static final Template DRAGON = new Template(legend(
            "P", "minecraft:polished_blackstone_bricks",
            "B", "minecraft:blackstone",
            "G", "minecraft:gilded_blackstone",
            "M", "minecraft:shroomlight",
            "s", "minecraft:polished_blackstone_brick_stairs[facing=north]",
            "F", "minecraft:campfire[lit=true,facing=south]",
            "w", "minecraft:blackstone_slab[type=top]"),
            new String[][] {
                    {"PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "MPPPXPPPM", "sssssssss"},
                    {".PBBBBBP.", "..BBBBB..", "..BBBBB..", "F.G...G.F"},
                    {"B.BBBBB.B", "..BBBBB..", "...BBB..."},
                    {"B..BBB..B", "BB.BBB.BB", "....B...."},
                    {"BB.BBB.BB", ".B..B..B.", "....B....", "....H...."},
                    {"B..wBw..B"},
                    {"B.......B"}});

    /** The Idol: a gold idol enthroned at the back of a sandstone dais, a burning orb before it. */
    private static final Template IDOL = new Template(legend(
            "S", "minecraft:cut_sandstone",
            "C", "minecraft:chiseled_sandstone",
            "s", "minecraft:sandstone_stairs[facing=north]",
            "G", "minecraft:gold_block",
            "R", "minecraft:raw_gold_block",
            "E", "minecraft:redstone_block",
            "O", "minecraft:shroomlight",
            "b", "minecraft:bone_block",
            "c", "minecraft:candle[candles=4,lit=true]"),
            new String[][] {
                    {"SSSSSSSSS", "SSSSSSSSS", "SSSSSSSSS", "SSSSCSSSS", "CSSSXSSSC", "sssssssss"},
                    {"..RGGGR..", "..GGGGG..", ".G.....G.", "....C....", "c.A.....c"},
                    {"..GGGGG..", "...GGG...", ".G.....G.", "....O...."},
                    {".GGGGGGG.", ".G.GGG.G."},
                    {"..GGGGG..", "...GGG..."},
                    {"..RGGGR..", "...EGE..."},
                    {"..b.G.b..", "...GGG..."}});

    /** The Tomb: a sarcophagus on a raised plinth, its head toward the far wall, soul lanterns on its four posts. */
    private static final Template TOMB = new Template(legend(
            "P", "minecraft:polished_deepslate",
            "s", "minecraft:polished_deepslate_stairs[facing=north]",
            "W", "minecraft:deepslate_tile_wall",
            "N", "minecraft:soul_lantern",
            "c", "minecraft:candle[candles=3,lit=true]",
            "K", "minecraft:skeleton_skull[rotation=8]",
            "L", "minecraft:deepslate_tile_slab[type=bottom]"),
            new String[][] {
                    {"WPPPXPPPW", ".PPPPPPP.", ".PPPPPPP.", ".PPPPPPP.", "WsssssssW"},
                    {"Wc.....cW", "....T....", "....T....", ".c..T..c.", "W.......W"},
                    {"W.......W", "....K....", "....L....", "....L....", "W.......W"},
                    {"N.......N", ".........", ".........", ".........", "N.......N"}});

    private SetPieces() {
    }

    /** Quarter turns clockwise that bring a template's front (south) round to face (fx, fz). */
    private static int turns(int fx, int fz) {
        if (fz > 0) {
            return 0;
        }
        if (fx < 0) {
            return 1;
        }
        if (fz < 0) {
            return 2;
        }
        return 3;
    }

    /** (a, d) turned {@code t} quarter turns clockwise: across and depth in a template, to world offsets. */
    private static int[] turn(int a, int d, int t) {
        int x = a;
        int z = d;
        for (int k = 0; k < (t & 3); k++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[] {x, z};
    }

    /**
     * Stamps a template with the middle of its back row at (bx, by, bz), its front
     * facing (fx, fz). Returns the places of its special letters, as {x, y, z}.
     */
    private static Map<Character, List<int[]>> stamp(Blueprint bp, int level, Template tp, int bx, int by, int bz, int fx, int fz) {
        int t = turns(fx, fz);
        int half = tp.width() / 2;
        Map<Character, List<int[]>> specials = new HashMap<>();
        for (int y = 0; y < tp.layers().length; y++) {
            String[] rows = tp.layers()[y];
            for (int d = 0; d < rows.length; d++) {
                for (int c = 0; c < rows[d].length(); c++) {
                    char ch = rows[d].charAt(c);
                    if (ch == '.') {
                        continue;
                    }
                    int[] off = turn(c - half, d, t);
                    int x = bx + off[0];
                    int z = bz + off[1];
                    String state = tp.legend().get(ch);
                    if (state == null) {
                        specials.computeIfAbsent(ch, k -> new ArrayList<>()).add(new int[] {x, by + y, z});
                    } else {
                        bp.statue(x, by + y, z, state, t, level);
                    }
                }
            }
        }
        return specials;
    }

    /**
     * The room's wall farthest from its doors: {midX, midZ, inX, inZ, depth}, the
     * floor cell against the middle of it, the way into the room from it, and
     * how many cells deep the room is from it.
     */
    static int[] farWall(LevelPlan level, Room r) {
        int[][] walls = {
                {r.centerX(), r.minZ(), 0, 1, r.maxZ() - r.minZ() + 1},
                {r.centerX(), r.maxZ(), 0, -1, r.maxZ() - r.minZ() + 1},
                {r.minX(), r.centerZ(), 1, 0, r.maxX() - r.minX() + 1},
                {r.maxX(), r.centerZ(), -1, 0, r.maxX() - r.minX() + 1}};
        int[] best = null;
        int bestD = -1;
        for (int[] w : walls) {
            if (level.cell(w[0], w[1]) != Cell.FLOOR || level.cell(w[0] - w[2], w[1] - w[3]) != Cell.WALL) {
                continue;
            }
            int near = Integer.MAX_VALUE;
            for (int dx = -24; dx <= 24; dx++) {
                for (int dz = -24; dz <= 24; dz++) {
                    if (level.cell(w[0] + dx, w[1] + dz).isDoor()) {
                        near = Math.min(near, Math.abs(dx) + Math.abs(dz));
                    }
                }
            }
            if (near > bestD) {
                bestD = near;
                best = w;
            }
        }
        return best;
    }

    private static boolean floor(LevelPlan level, Room r, int x, int z) {
        return r.contains(x, z) && level.cell(x, z) == Cell.FLOOR;
    }

    private static Built exitFrom(Blueprint bp, int i, Map<Character, List<int[]>> sp, int fx, int fz, String disguise) {
        Built b = new Built();
        int[] x = sp.get('X').get(0);
        bp.set(x[0], x[1], x[2], Part.EXIT_FRAME, Blueprinter.facingOf(fx, fz), i);
        bp.disguise(x[0], x[1], x[2], disguise);
        b.exit = x;
        return b;
    }

    /**
     * The Firechasm: a chasm across the room, lava three below its lip, and a
     * two-wide plank bridge that gives way behind you (Sable: "the chasm is only
     * 2 blocks across... needs to not be so easy to jump"). The dragon sits on
     * the far side, the relic beside it, and the way out is set in its plinth.
     */
    static Built firechasm(Blueprint bp, LevelPlan level, Room r, int f, int i, int job, Dice dice) {
        int[] w = farWall(level, r);
        if (w == null) {
            return null;
        }
        int ix = w[2];
        int iz = w[3];
        int jx = -iz; // across the room, at right angles to (ix, iz)
        int jz = ix;
        Map<Character, List<int[]>> sp = stamp(bp, i, DRAGON, w[0], f, w[1], ix, iz);
        Built b = exitFrom(bp, i, sp, ix, iz, "minecraft:chiseled_polished_blackstone");
        // The head is its own part: the second job's eye is set in it.
        int[] head = sp.get('H').get(0);
        bp.set(head[0], head[1], head[2], Part.OBJ_DRAGON, Blueprinter.facingOf(ix, iz), i);
        int depth = w[4];
        int width = depth >= 17 ? 5 : 4;
        int c0 = Math.min(7, depth - width - 3);
        for (int d = c0; d < c0 + width; d++) {
            boolean rot = dice.chance(0.4);
            int which = dice.nextInt(2);
            for (int a = -12; a <= 12; a++) {
                int x = w[0] + ix * d + jx * a;
                int z = w[1] + iz * d + jz * a;
                if (!floor(level, r, x, z)) {
                    continue;
                }
                boolean bridge = a == 0 || a == 1;
                bp.set(x, f - 1, z, bridge ? (rot && a == which ? Part.OBJ_ROTTEN : Part.OBJ_PLANK) : Part.OBJ_CHASM, 0, i);
                bp.fill(x, z, f - 3, f - 2, Part.OBJ_CHASM, i);
                bp.set(x, f - 4, z, Part.OBJ_LAVA, 0, i);
                bp.set(x, f - 5, z, Part.WALL, 0, i);
                // Seal its sides: below the room's floor is the world's own ground, or a cave.
                for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + n[0];
                    int nz = z + n[1];
                    int nd = (nx - w[0]) * ix + (nz - w[1]) * iz;
                    if (!floor(level, r, nx, nz) || nd < c0 || nd >= c0 + width) {
                        bp.fill(nx, nz, f - 5, f - 2, Part.WALL, i);
                    }
                }
            }
        }
        // The lip either side, cracked and scorched.
        for (int d : new int[] {c0 - 1, c0 + width}) {
            for (int a = -12; a <= 12; a++) {
                int x = w[0] + ix * d + jx * a;
                int z = w[1] + iz * d + jz * a;
                if (floor(level, r, x, z)) {
                    // Not magma: a monster standing on it burned itself (Sable, 2026-10-14).
                    bp.statue(x, f - 1, z, dice.chance(0.3) ? "minecraft:blackstone" : "minecraft:cracked_polished_blackstone_bricks", 0, i);
                }
            }
        }
        // The near foot of the bridge, where a fall puts you back.
        int nd = c0 + width;
        b.places.add(new int[] {w[0] + ix * nd, f, w[1] + iz * nd});
        // The relic's pedestal (or, for the thief, where he stands) on the far side, beside the dragon.
        int[] relic = {w[0] + ix * 5 + jx * 3, f, w[1] + iz * 5 + jz * 3};
        if (!floor(level, r, relic[0], relic[2])) {
            relic = new int[] {w[0] + ix * 5 - jx * 3, f, w[1] + iz * 5 - jz * 3};
        }
        if (job == 0) {
            bp.set(relic[0], f, relic[2], Part.OBJ_RELIC, 0, i);
            bp.statue(relic[0], f + 1, relic[2], "minecraft:candle[candles=1,lit=true]", 0, i);
        }
        b.anchor = relic;
        return b;
    }

    /** The Idol: the idol on its dais at the far wall; the shaman stands before it, and the way out is set in the dais. */
    static Built idol(Blueprint bp, LevelPlan level, Room r, int f, int i) {
        int[] w = farWall(level, r);
        if (w == null) {
            return null;
        }
        Map<Character, List<int[]>> sp = stamp(bp, i, IDOL, w[0], f, w[1], w[2], w[3]);
        Built b = exitFrom(bp, i, sp, w[2], w[3], "minecraft:chiseled_sandstone");
        b.anchor = sp.get('A').get(0);
        // A carved square in the floor by the way in, as on the card.
        int cx = w[0] + w[2] * (w[4] - 4);
        int cz = w[1] + w[3] * (w[4] - 4);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (floor(level, r, cx + dx, cz + dz)) {
                    boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == 2;
                    bp.statue(cx + dx, f - 1, cz + dz, edge ? "minecraft:chiseled_sandstone"
                            : dx == 0 && dz == 0 ? "minecraft:gold_block" : "minecraft:smooth_sandstone", 0, i);
                }
            }
        }
        return b;
    }

    /** The Tomb: the sarcophagus on its plinth in the middle of the room; the way out is set in the plinth's head. */
    static Built tomb(Blueprint bp, LevelPlan level, Room r, int f, int i) {
        int[] w = farWall(level, r);
        if (w == null) {
            return null;
        }
        int ix = w[2];
        int iz = w[3];
        int back = w[4] / 2 - 2;
        Map<Character, List<int[]>> sp = stamp(bp, i, TOMB, w[0] + ix * back, f, w[1] + iz * back, ix, iz);
        Built b = exitFrom(bp, i, sp, ix, iz, "minecraft:chiseled_deepslate");
        List<int[]> tomb = sp.get('T');
        for (int[] t : tomb) {
            bp.set(t[0], t[1], t[2], Part.OBJ_TOMB, 0, i);
            b.places.add(t);
        }
        b.anchor = tomb.get(1);
        // Cobwebs in the corners, high up.
        for (int x : new int[] {r.minX(), r.maxX()}) {
            for (int z : new int[] {r.minZ(), r.maxZ()}) {
                if (floor(level, r, x, z)) {
                    bp.statue(x, f + 6, z, "minecraft:cobweb", 0, i);
                    bp.statue(x, f + 5, z, "minecraft:cobweb", 0, i);
                }
            }
        }
        return b;
    }

    /**
     * The Fighting Pit: three deep, a ladder out on the near side, a barred cage
     * in its far wall, bones and blood on its floor, spikes in its corners, one
     * lamp hanging over it, and the way out under the gong on the far rim.
     */
    static Built pit(Blueprint bp, LevelPlan level, Room r, int f, int i, Dice dice) {
        int[] w = farWall(level, r);
        if (w == null) {
            return null;
        }
        int ix = w[2];
        int iz = w[3];
        int jx = -iz;
        int jz = ix;
        int cx = r.centerX();
        int cz = r.centerZ();
        int s = Math.max(5, Math.min(9, Math.min(r.w, r.h) - 6)) | 1;
        int h = s / 2;
        for (int x = cx - h - 1; x <= cx + h + 1; x++) {
            for (int z = cz - h - 1; z <= cz + h + 1; z++) {
                boolean in = Math.abs(x - cx) <= h && Math.abs(z - cz) <= h;
                if (in) {
                    bp.set(x, f - 4, z, Part.FLOOR, 0, i);
                    bp.fill(x, z, f - 3, f - 1, Part.AIR, i);
                } else {
                    bp.fill(x, z, f - 4, f - 2, Part.WALL, i);
                }
            }
        }
        // The ladder, on the near side.
        int lx = cx + ix * h;
        int lz = cz + iz * h;
        for (int y = f - 3; y <= f - 1; y++) {
            bp.set(lx, y, lz, Part.LADDER, Blueprinter.facingOf(-ix, -iz), i);
        }
        // The cage in the far wall, barred, and the gong above it on the rim.
        int gx = cx - ix * (h + 1);
        int gz = cz - iz * (h + 1);
        int bx = gx - ix;
        int bz = gz - iz;
        if (floor(level, r, bx, bz)) {
            bp.statue(gx, f - 3, gz, "minecraft:iron_bars[" + (ix != 0 ? "north=true,south=true" : "east=true,west=true") + "]", 0, i);
            bp.statue(gx, f - 2, gz, "minecraft:iron_bars[" + (ix != 0 ? "north=true,south=true" : "east=true,west=true") + "]", 0, i);
            bp.set(bx, f - 4, bz, Part.FLOOR, 0, i);
            bp.fill(bx, bz, f - 3, f - 2, Part.AIR, i);
            bp.statue(bx, f - 3, bz, "minecraft:skeleton_skull[rotation=4]", 0, i);
            for (int[] n : new int[][] {{jx, jz}, {-jx, -jz}, {-ix, -iz}}) {
                bp.fill(bx + n[0], bz + n[1], f - 4, f - 2, Part.WALL, i);
            }
        }
        Built b = new Built();
        bp.set(gx, f, gz, Part.EXIT_FRAME, Blueprinter.facingOf(ix, iz), i);
        bp.disguise(gx, f, gz, "minecraft:chiseled_stone_bricks");
        b.exit = new int[] {gx, f, gz};
        for (int side : new int[] {1, -1}) {
            int px = gx + jx * side;
            int pz = gz + jz * side;
            for (int y = f; y <= f + 2; y++) {
                bp.statue(px, y, pz, "minecraft:dark_oak_fence", 0, i);
            }
            bp.statue(px, f + 3, pz, "minecraft:dark_oak_planks", 0, i);
        }
        bp.statue(gx, f + 3, gz, "minecraft:dark_oak_planks", 0, i);
        bp.statue(gx, f + 2, gz, "minecraft:bell[attachment=ceiling,facing=" + (ix != 0 ? "east" : "north") + "]", 0, i);
        // Spikes in the corners, bones and blood on the floor.
        for (int[] c : new int[][] {{h, h}, {h, -h}, {-h, h}, {-h, -h}}) {
            if (cx + c[0] != lx || cz + c[1] != lz) {
                bp.statue(cx + c[0], f - 3, cz + c[1], "minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]", 0, i);
            }
        }
        String[] mess = {"minecraft:red_carpet", "minecraft:red_carpet", "minecraft:skeleton_skull[rotation=3]",
                "minecraft:bone_block[axis=x]", "minecraft:red_carpet", "minecraft:skeleton_skull[rotation=11]"};
        for (String m : mess) {
            int x = cx + dice.nextInt(s) - h;
            int z = cz + dice.nextInt(s) - h;
            if ((x != cx || z != cz) && (x != lx || z != lz) && Math.abs(x - cx) < h && Math.abs(z - cz) < h) {
                bp.statue(x, f - 3, z, m, 0, i);
            }
        }
        // One lamp, hanging over the pit.
        bp.statue(cx, f + 6, cz, "minecraft:chain", 0, i);
        bp.statue(cx, f + 5, cz, "minecraft:chain", 0, i);
        bp.statue(cx, f + 4, cz, "minecraft:lantern[hanging=true]", 0, i);
        b.anchor = new int[] {cx, f - 3, cz};
        return b;
    }

    /**
     * The Fountain of Light: a round basin, its water level with the floor so
     * anyone can climb out, glowstone under it, and a raised bowl of water on a
     * pillar in its middle. The way out is a block in the rim on the near side;
     * the second job's dry basin sits in the rim beside it.
     */
    static Built fountain(Blueprint bp, LevelPlan level, Room r, int f, int i, int job, Dice dice) {
        int[] w = farWall(level, r);
        if (w == null) {
            return null;
        }
        int ix = w[2];
        int iz = w[3];
        int jx = -iz;
        int jz = ix;
        int cx = r.centerX();
        int cz = r.centerZ();
        Built b = new Built();
        List<int[]> rims = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int ax = Math.abs(dx);
                int az = Math.abs(dz);
                int x = cx + dx;
                int z = cz + dz;
                if (ax == 3 && az == 3) {
                    continue; // rounded off
                }
                boolean rim = Math.max(ax, az) == 3 || ax == 2 && az == 2;
                if (rim) {
                    boolean glow = ax == 2 && az == 2;
                    boolean mid = ax == 0 || az == 0;
                    bp.set(x, f, z, glow ? Part.OBJ_GLOW : mid && job == 0 ? Part.OBJ_CORRUPT : Part.OBJ_RIM, 0, i);
                    rims.add(new int[] {x, f, z});
                    if (glow) {
                        bp.statue(x, f + 1, z, "minecraft:end_rod[facing=up]", 0, i);
                    }
                    if (mid && job == 0) {
                        b.places.add(new int[] {x, f, z});
                    }
                } else if (dx == 0 && dz == 0) {
                    bp.set(x, f - 1, z, Part.FLOOR, 0, i);
                    bp.statue(x, f, z, "minecraft:quartz_pillar", 0, i);
                    bp.statue(x, f + 1, z, "minecraft:sea_lantern", 0, i);
                    bp.statue(x, f + 2, z, "minecraft:water", 0, i);
                } else {
                    bp.statue(x, f - 1, z, ax <= 1 && az <= 1 ? "minecraft:glowstone" : "minecraft:prismarine_bricks", 0, i);
                    bp.set(x, f, z, Part.WATER, 0, i);
                    if (ax <= 1 && az <= 1) {
                        // The raised bowl's lip, round the water on top of the pillar.
                        String dir = dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
                        bp.statue(x, f + 2, z, ax == 1 && az == 1 ? "minecraft:smooth_quartz_slab[type=bottom]"
                                : "minecraft:quartz_stairs[facing=" + dir + ",half=top]", 0, i);
                    }
                }
            }
        }
        // The way out, in the rim on the near side; the dry basin beside it.
        int ex = cx + ix * 3 + jx;
        int ez = cz + iz * 3 + jz;
        bp.set(ex, f, ez, Part.EXIT_FRAME, Blueprinter.facingOf(ix, iz), i);
        bp.disguise(ex, f, ez, "minecraft:chiseled_quartz_block");
        b.exit = new int[] {ex, f, ez};
        if (job == 1) {
            int bx = cx + ix * 3 - jx;
            int bz = cz + iz * 3 - jz;
            bp.set(bx, f, bz, Part.OBJ_BASIN, 0, i);
            // Only one stone of the rim gives the vial, and it has to be found (Sable, 2026-10-14).
            rims.removeIf(q -> q[0] == ex && q[2] == ez || q[0] == bx && q[2] == bz);
            b.places.add(rims.get(dice.nextInt(rims.size())));
        }
        // The card's floor: cracked, with jars about and a corner gone green.
        for (int n = 0; n < 14; n++) {
            int x = r.minX() + dice.nextInt(r.maxX() - r.minX() + 1);
            int z = r.minZ() + dice.nextInt(r.maxZ() - r.minZ() + 1);
            if (floor(level, r, x, z) && (Math.abs(x - cx) > 3 || Math.abs(z - cz) > 3)) {
                bp.statue(x, f - 1, z, "minecraft:cracked_stone_bricks", 0, i);
            }
        }
        for (int[] p : new int[][] {{5, 1}, {-5, -2}, {2, -5}}) {
            int x = cx + p[0];
            int z = cz + p[1];
            if (floor(level, r, x, z)) {
                bp.statue(x, f, z, "minecraft:decorated_pot", 0, i);
            }
        }
        int gx = cx - ix * 6 + jx * 6;
        int gz = cz - iz * 6 + jz * 6;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (floor(level, r, gx + dx, gz + dz)) {
                    bp.statue(gx + dx, f - 1, gz + dz, "minecraft:moss_block", 0, i);
                    bp.statue(gx + dx, f, gz + dz, dx == 0 && dz == 0 ? "minecraft:flowering_azalea" : "minecraft:moss_carpet", 0, i);
                }
            }
        }
        b.anchor = new int[] {cx, f, cz};
        return b;
    }

    private static boolean air(Blueprint bp, int x, int y, int z) {
        int code = bp.get(x, y, z);
        return code == 0 || Blueprint.part(code) == Part.AIR;
    }

    /**
     * The room round the set piece (Sable, 2026-10-12: "the room itself is
     * plain"). Clearing it for the set piece took the dressing with it, so it is
     * dressed again, grander than an ordinary room: the theme's floor border,
     * medallion and pilasters, coving all round, panelling with a dado rail, a
     * colonnade two in from the walls, lamps hanging on chains and banners
     * between the pilasters. Nothing goes where the set piece stands, in front
     * of a doorway, or by the way out.
     */
    static void dress(Blueprint bp, com.sablednah.crawlspace.plan.DungeonPlan plan, LevelPlan level, Room r, int f, int i, int[] exit, Dice dice) {
        String theme = level.theme.name();
        int h = Blueprinter.clearHeight(level, r.centerX(), r.centerZ());
        Dresser.finish(bp, level, r, plan, i, theme, null);
        Dresser.cove(bp, level, r, plan, i);
        Dresser.panel(bp, level, r, plan, i);
        // The colonnade: a column every four cells round a ring two in from the walls.
        for (int x = r.minX() + 2; x <= r.maxX() - 2; x++) {
            for (int z = r.minZ() + 2; z <= r.maxZ() - 2; z++) {
                boolean ring = x == r.minX() + 2 || x == r.maxX() - 2 || z == r.minZ() + 2 || z == r.maxZ() - 2;
                if (!ring || Math.floorMod(x + z, 4) != 0 || !standClear(bp, level, r, f, h, x, z, exit, 1)) {
                    continue;
                }
                for (int y = f; y < f + h; y++) {
                    bp.set(x, y, z, y == f || y == f + h - 1 ? Part.DADO : Part.PILLAR, 0, i);
                }
            }
        }
        // Lamps on chains, one over each quarter of the room; on a dark level, the chains hang empty.
        boolean dark = level.feeling == com.sablednah.crawlspace.plan.Feeling.DARK;
        for (int[] q : new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}) {
            int x = r.centerX() + q[0] * Math.max(3, r.w / 4);
            int z = r.centerZ() + q[1] * Math.max(3, r.h / 4);
            if (r.contains(x, z) && air(bp, x, f + h - 1, z) && air(bp, x, f + h - 2, z) && air(bp, x, f + h - 3, z)) {
                bp.set(x, f + h - 1, z, Part.CHAIN, 0, i);
                bp.set(x, f + h - 2, z, Part.CHAIN, 0, i);
                if (!dark) {
                    bp.set(x, f + h - 3, z, Part.LIGHT, 0, i);
                }
            }
        }
        // Banners on the walls, two up, between the pilasters.
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!floor(level, r, x, z) || Math.floorMod(x + z, 4) != 2 || nearDoorway(level, x, z) || !air(bp, x, f + 2, z)
                        || exit != null && Math.abs(x - exit[0]) + Math.abs(z - exit[2]) < 3) {
                    continue;
                }
                for (int[] d : dirs) {
                    if (level.cell(x + d[0], z + d[1]) == Cell.WALL) {
                        bp.set(x, f + 2, z, Part.BANNER, Blueprinter.facingOf(-d[0], -d[1]), i);
                        break;
                    }
                }
            }
        }
    }

    /** Whether a column fits at (x, z): floor under it, air all the way up, clear of the set piece, doors and the way out. */
    private static boolean standClear(Blueprint bp, LevelPlan level, Room r, int f, int h, int x, int z, int[] exit, int margin) {
        if (!floor(level, r, x, z) || nearDoorway(level, x, z)) {
            return false;
        }
        int under = bp.get(x, f - 1, z);
        if (under == 0 || Blueprint.part(under) != Part.FLOOR && Blueprint.part(under) != Part.FLOOR_ACCENT) {
            return false;
        }
        if (exit != null && Math.abs(x - exit[0]) + Math.abs(z - exit[2]) < 3) {
            return false;
        }
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dz = -margin; dz <= margin; dz++) {
                for (int y = f - 1; y < f + h; y++) {
                    int code = bp.get(x + dx, y, z + dz);
                    if (code == 0) {
                        continue;
                    }
                    Part p = Blueprint.part(code);
                    boolean open = p == Part.AIR || p == Part.FLOOR || p == Part.FLOOR_ACCENT || p == Part.FLOOR_INLAY
                            || p == Part.WALL || p == Part.PILASTER || p == Part.PANEL || p == Part.DADO || p == Part.COVE || p == Part.CEILING;
                    if (!open) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean nearDoorway(LevelPlan level, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.cell(x + dx, z + dz).isDoor()) {
                    return true;
                }
            }
        }
        return false;
    }
}
