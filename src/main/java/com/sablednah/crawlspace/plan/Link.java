package com.sablednah.crawlspace.plan;

/** A planned connection between two rooms, and once routed, the corridor that makes it. */
public final class Link {

    public final Room a;
    public final Room b;
    public LinkKind kind;
    public CorridorStyle style;
    /** 1 or 3. */
    public int width;
    /** On the level's main loop. Losing one of these loses the loop. */
    public final boolean loop;
    /**
     * A spur: {@code a}'s corridor runs to the nearest existing corridor rather
     * than to {@code b}'s door, making a T-junction. {@code b} is only where the
     * spur was laid out from. {@link #doorB} stays null.
     */
    public boolean spur;
    /** Set once routed: the corridor's centre line, door to door. Null if routing failed. */
    public int[][] path;
    /** The doorway cells in each room's wall, as {x, z, normalX, normalZ}; the normal points out of the room. */
    public int[] doorA;
    public int[] doorB;

    public Link(Room a, Room b, LinkKind kind, boolean loop) {
        this.a = a;
        this.b = b;
        this.kind = kind;
        this.loop = loop;
    }

    public Room other(Room r) {
        return r == a ? b : a;
    }

    @Override
    public String toString() {
        return a.role + "#" + a.id + " -" + kind + "/" + style + "/" + width + "- " + b.role + "#" + b.id;
    }
}
