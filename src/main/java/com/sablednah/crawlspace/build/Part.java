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
    TOWER_DOOR_UPPER,
    /** Quoins, string courses, door frames and lintels. */
    TOWER_TRIM,
    /** A stair in the style's stone: steps, stepped faces, a roof's edge; facing is the way it climbs. */
    TOWER_STAIR,
    /** An upside-down stair carrying a parapet; facing is toward the wall. */
    TOWER_CORBEL,
    TOWER_WINDOW,
    /** A temple's column. */
    TOWER_PILLAR,
    /** A roof's covering. */
    TOWER_ROOF,
    /** A lever on the floor: the mod watches it (see {@link Trigger}). */
    LEVER,
    /** A spiral stair's flat corner, level with the top of the step before it. */
    LANDING,

    // ---- Dressing and population (see Dresser). Facing is the way the thing faces: away from its wall. ----
    /** A chest of the level's loot tier. */
    CHEST,
    /** A treasure room's or lair's chest: one tier richer. */
    HOARD_CHEST,
    /** A barrel of supplies. */
    BARREL,
    /** A mob spawner of the level's theme. */
    SPAWNER,
    COBWEB,
    /** A skull on the floor; facing picks one of four turns. */
    SKULL,
    BONES,
    /** Lit candles; facing + 1 is how many. */
    CANDLES,
    CARPET,
    MOSS,
    /** A rail; facing 0 runs north-south, 1 east-west. */
    RAIL,
    ALTAR,
    SARCOPHAGUS,
    BRAZIER,
    STALAGMITE,
    /** A banner hung on a wall. */
    BANNER,
    WALL_TORCH,
    /** Hangs from the ceiling. */
    CHAIN,
    /** A ceiling beam; facing 0 runs along x, 1 along z. */
    BEAM,
    /** A timber post holding a beam up. */
    SUPPORT,
    /** Round the hole at the top of a spiral stair, so nobody walks into it. */
    RAILING,
    /** A wall block swapped for shelves. */
    SHELF,
    /** A wall block swapped for the theme's trim, along the foot of a room's walls. */
    WALL_ACCENT,
    /** A seat with its back to the wall. */
    THRONE,
    /** A floor block swapped for the theme's border, just inside the walls. */
    FLOOR_ACCENT,
    /** The centre of a floor medallion. */
    FLOOR_INLAY,
    /** A wall column swapped for the theme's pilaster, to break up a flat wall. */
    PILASTER,
    /** Coving: an upside-down stair under the ceiling against a wall; facing is toward the wall. */
    COVE,
    /** A wall block swapped for panelling, the lower part of a wall. */
    PANEL,
    /** A wall block swapped for the rail along the top of the panelling. */
    DADO,
    /** A table: a fence post, with {@link #TABLE_TOP} (a pressure plate) on it. */
    TABLE,
    TABLE_TOP,
    /** A chair: a stair with its back to the table; facing is the way its back faces. */
    CHAIR,
    /** A decorated pot. */
    POT,
    /** A rug: carpet in the theme's second colour. */
    RUG,
    // ---- clutter ----
    FLOOR_LANTERN,
    ANVIL,
    GRINDSTONE,
    CAULDRON,
    LECTERN,
    MUSHROOM,
    // ---- overgrowth ----
    /** Vines on the wall behind; facing is the direction of that wall. */
    VINE,
    /** A fern or grass tuft on a moss floor. */
    PLANT,
    /** A floor block swapped for moss, for plants to grow from. */
    MOSS_FLOOR,
    /** An azalea bush, which never decays. */
    LEAVES,
    /** Roots hanging from the ceiling. */
    ROOTS,
    /** A ladder; facing is away from the wall it hangs on. */
    LADDER,
    /** A statue's body: a wall post. */
    STATUE,
    // ---- traps ----
    /** A real trap's pressure plate: shown when nobody's perception does the finding, else air until noticed. */
    TRAP_PLATE,
    /** A real trap's tripwire, shown and hidden the same way. */
    TRAP_WIRE,
    /** A pressure plate that does nothing, always shown. */
    DECOY_PLATE,
    /** A tripwire that does nothing, always shown. */
    DECOY_WIRE,
    // ---- puzzle rooms ----
    /** The void around a puzzle room's maze, in place of a floor block. Touch it and you are sent back. */
    VOID,
    /** The block at a puzzle room's centre that the void sends you back to; a floor block. */
    RESTART,
    /** A leap-of-faith path block: solid, and invisible (a barrier) over the void below. */
    PATH_HIDDEN,
    /** A path block that cracks and gives way a moment after you step on it, then re-forms. */
    CRUMBLE,
    /** A big dripleaf path block: tips you off if you stand on it; facing is the way it points. */
    DRIPLEAF,
    /** A pit trap's floor tile: crumbles away under you, into the spikes below. */
    PIT_TILE
}
