package com.sablednah.crawlspace.build;

/**
 * What a block is for, before a theme's palette says what it is. The builder
 * in the {@code neoforge} package turns each into a block state.
 */
public enum Part {
    AIR,
    /** A room's floor. */
    FLOOR,
    /** A corridor's floor. */
    CORRIDOR_FLOOR,
    WALL,
    CEILING,
    PILLAR,
    WATER,
    /** A wooden door, lower and upper halves. Faces out of the room it belongs to. */
    DOOR_LOWER,
    DOOR_UPPER,
    /** A door opened by the level's lever. */
    LOCKED_LOWER,
    LOCKED_UPPER,
    /** Wall that is really a way through: a palette's weakest-looking wall block. */
    SECRET_WALL,
    /** A spiral stair's step, facing up the way it climbs. */
    STEP,
    /** The column a spiral stair winds round. */
    NEWEL,
    /** A lantern hanging from the block above. */
    LIGHT,
    /** The entrance tower's walls, floor and roof, dressed for the biome it stands in. */
    TOWER,
    TOWER_FLOOR,
    /** The tower's battlements. */
    TOWER_TOP,
    TOWER_DOOR_LOWER,
    TOWER_DOOR_UPPER
}
