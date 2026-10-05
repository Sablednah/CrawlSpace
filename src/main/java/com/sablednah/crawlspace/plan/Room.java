package com.sablednah.crawlspace.plan;

/**
 * One room on a level. Positions are in dungeon coordinates: x and z in
 * blocks, with the entrance tower's stair at (0, 0).
 *
 * <p>Width and depth are always odd, so a room has a centre cell for its
 * stairwell. While the layout is being relaxed the centre moves as a double;
 * {@link #snap()} fixes it to a cell.</p>
 */
public final class Room {

    public final int id;
    public final Role role;
    public final Shape shape;
    public final int w;
    public final int h;
    /** mask[lx][lz]: true where the room has floor, in room-local cells. */
    public final boolean[][] mask;

    /** Centre, in dungeon coordinates. Doubles until {@link #snap()}. */
    public double cx;
    public double cz;
    /** Pinned rooms do not move during relaxation (the entry, under the stair from above). */
    public boolean pinned;
    /** Floor height relative to the level's floor, in blocks. */
    public int floor;
    public boolean pool;

    public Room(int id, Role role, Shape shape, int w, int h, boolean[][] mask) {
        this.id = id;
        this.role = role;
        this.shape = shape;
        this.w = w;
        this.h = h;
        this.mask = mask;
    }

    public void snap() {
        cx = Math.round(cx);
        cz = Math.round(cz);
    }

    /** Dungeon x of the room's west edge. */
    public int minX() {
        return (int) Math.round(cx) - w / 2;
    }

    public int minZ() {
        return (int) Math.round(cz) - h / 2;
    }

    public int maxX() {
        return minX() + w - 1;
    }

    public int maxZ() {
        return minZ() + h - 1;
    }

    public int centerX() {
        return (int) Math.round(cx);
    }

    public int centerZ() {
        return (int) Math.round(cz);
    }

    /** Whether dungeon cell (x, z) is this room's floor. */
    public boolean contains(int x, int z) {
        int lx = x - minX();
        int lz = z - minZ();
        return lx >= 0 && lz >= 0 && lx < w && lz < h && mask[lx][lz];
    }

    @Override
    public String toString() {
        return role + "#" + id + " " + shape + " " + w + "x" + h + " @" + centerX() + "," + centerZ();
    }
}
