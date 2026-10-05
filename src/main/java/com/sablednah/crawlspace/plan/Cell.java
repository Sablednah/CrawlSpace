package com.sablednah.crawlspace.plan;

/**
 * What one column of a level is, seen from above. The builder turns each into
 * blocks through a theme's palette; the planner only ever deals in these.
 */
public enum Cell {
    /** Untouched ground. The builder leaves it alone. */
    ROCK,
    /** The skin around every open space: what a palette's {@code wall} paints. */
    WALL,
    /** A room's floor. */
    FLOOR,
    /** A corridor's floor. */
    CORRIDOR,
    /** A doorway with a door in it. */
    DOOR,
    /** An open doorway: an arch, no door. */
    ARCH,
    /** A door opened by a lever somewhere else on the level. */
    DOOR_LOCKED,
    /** A door that looks like wall until found. */
    DOOR_SECRET,
    /** A solid column inside a room, floor to ceiling. */
    PILLAR,
    /** The spiral stair arriving from the level above. */
    STAIR_UP,
    /** The spiral stair down to the next level. */
    STAIR_DOWN,
    /** A hole to the level below: a one-way route down. */
    PIT,
    /** Shallow water, walkable. Where a pit lands, so the fall does not kill. */
    POOL;

    /** Open space: anything a wall has to enclose. */
    public boolean isOpen() {
        return this != ROCK && this != WALL;
    }

    /** Somewhere a player can stand and walk on to a neighbour. */
    public boolean isWalkable() {
        return switch (this) {
            case FLOOR, CORRIDOR, DOOR, ARCH, DOOR_LOCKED, DOOR_SECRET, STAIR_UP, STAIR_DOWN, POOL -> true;
            default -> false;
        };
    }

    public boolean isDoor() {
        return this == DOOR || this == ARCH || this == DOOR_LOCKED || this == DOOR_SECRET;
    }
}
