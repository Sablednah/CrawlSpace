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
    PIT_TILE,
    // ---- portcullises ----
    /** Iron bars filling a doorway: a lock whose key is in a chest elsewhere on the level. */
    PORTCULLIS,
    /** The open air under a raised portcullis that drops behind you: air until it falls. */
    PORTCULLIS_GAP,
    /** The floor block under a dropping portcullis: worn, scored where the bars land (the tell). */
    PORTCULLIS_SILL,
    /** The key room's chest when its level's lock is a portcullis: it holds the key. */
    KEY_CHEST,
    /** A lever that raises a dropped portcullis: inside the room it shut you into. */
    WINCH,
    /** An iron door that opens only from its own side: a shortcut back. */
    ONEWAY_LOWER,
    ONEWAY_UPPER,
    /** Iron bars at eye level through the wall between a corridor and a room: the goal seen before it is reached. */
    WINDOW_BARS,
    /** A vault's pedestal: an item floats over it, and taking one cages the others. */
    PEDESTAL,
    /** An ice board's tile: cross every one exactly once. */
    ICE_TILE,
    /** The rumour book's lectern, in the first level's entry room. Facing is toward the room. */
    RUMOURS,
    /** A trap's tells (Goblin Punch): a pebble by a rockfall, frost by a frost trap, a scorch by fire, sculk by a summoning, a dead fan by gas. */
    TELL_PEBBLE,
    TELL_FROST,
    TELL_SCORCH,
    TELL_SCULK,
    TELL_DEAD,
    /**
     * Door previews (Hades): the lintel over a room's doorway says what is
     * inside. Gilded blackstone for treasure, amethyst for a shrine, a chiseled
     * bookshelf for a puzzle, iron for a guard room; over a lair, blackstone
     * with a wither skull hung on its corridor face.
     */
    PREVIEW_TREASURE,
    PREVIEW_SHRINE,
    PREVIEW_PUZZLE,
    PREVIEW_GUARD,
    PREVIEW_LAIR,
    /** The lair's skull, on the lintel's corridor face. Facing is away from the wall. */
    PREVIEW_SKULL,
    /** The finale's altar, at the head of the arena's stairs: an offering wakes the boss, and calls it back. */
    BOSS_ALTAR,
    /** A teleport maze's pad, in the floor: stepping on it sends you to another cell. */
    TP_PAD,
    /** The thing you should not touch: a sealed sarcophagus, chained, ringed with skulls (Death Frost Doom). */
    FORBIDDEN,
    /*
     * Objective rooms (Warhammer Quest; Sable's ruling, 2026-10-12): big set pieces with a job, and a way out.
     */
    /** The quick exit: an end portal frame, given its eye when the room's job is done. Facing is into the room. */
    EXIT_FRAME,
    /** The firechasm's lava. */
    OBJ_LAVA,
    /** The firechasm's bridge over the lava. */
    OBJ_BRIDGE,
    /** The pedestal the firechasm's relic is taken from. */
    OBJ_RELIC,
    /** The firechasm's dragon: a head on its plinth. Facing is toward the chasm. */
    OBJ_DRAGON,
    /** The fountain's rim. */
    OBJ_RIM,
    /** The fountain's light, at the rim's corners. */
    OBJ_GLOW,
    /** The fountain's corrupted blocks: break all four, at a price. Not shell: they are meant to be broken. */
    OBJ_CORRUPT,
    /** The tomb chamber's sarcophagus. */
    OBJ_TOMB,
    /** The idol's body, and its head. Facing is toward the room. */
    OBJ_IDOL,
    OBJ_IDOL_HEAD,
    /**
     * A corridor's step up where nothing is free in front of a stair's low side:
     * the theme's slab, which can be climbed from any side. A stair there met a
     * player coming up a one-wide corridor with its tall flank (Sable, 2026-10-12).
     */
    STEP_SLAB,
    /** The Fountain's dry basin, a cauldron by the way out: the vial is poured into it. */
    OBJ_BASIN;

    /**
     * The dungeon's shell: what protection keeps whole (see {@code Protection}).
     * Walls, floors, ceilings, the stairs, the tower, the locked doors and their
     * lever, the puzzle rooms; everything that, broken, would be a way round the
     * dungeon rather than through it. Dressing (chests, spawners, pots, cobwebs,
     * furniture, wooden doors) and trap plates are not: breaking those is play.
     * Together with the open parts it seals every level; BlueprintTest checks
     * that no breakable block opens on to the world.
     */
    public boolean shell() {
        return switch (this) {
            case FLOOR, CORRIDOR_FLOOR, WALL, CEILING, PILLAR, SECRET_WALL, STEP, STEP_SLAB, NEWEL, LANDING,
                    LOCKED_LOWER, LOCKED_UPPER, LEVER,
                    TOWER, TOWER_FLOOR, TOWER_TOP, TOWER_TRIM, TOWER_STAIR, TOWER_CORBEL, TOWER_WINDOW, TOWER_PILLAR, TOWER_ROOF,
                    SHELF, WALL_ACCENT, FLOOR_ACCENT, FLOOR_INLAY, PILASTER, COVE, PANEL, DADO, MOSS_FLOOR, BEAM,
                    VOID, RESTART, PATH_HIDDEN, CRUMBLE, DRIPLEAF, PIT_TILE,
                    PORTCULLIS, PORTCULLIS_SILL, WINCH, ONEWAY_LOWER, ONEWAY_UPPER, WINDOW_BARS, PEDESTAL, ICE_TILE,
                    PREVIEW_TREASURE, PREVIEW_SHRINE, PREVIEW_PUZZLE, PREVIEW_GUARD, PREVIEW_LAIR, BOSS_ALTAR, TP_PAD, FORBIDDEN,
                    EXIT_FRAME, OBJ_BRIDGE, OBJ_RELIC, OBJ_DRAGON, OBJ_RIM, OBJ_GLOW, OBJ_TOMB, OBJ_IDOL, OBJ_IDOL_HEAD, OBJ_BASIN -> true;
            default -> false;
        };
    }

    /**
     * Shell parts that come and go by design, so a missing one is never put
     * back: crumbling path and pit floor, dripleaf, the iron doors (two
     * halves that only make sense together) and a keyed portcullis, which is
     * gone once raised.
     */
    public boolean mayBeMissing() {
        return switch (this) {
            // The quick exit is not built until its job is done: nothing to put back before then.
            case CRUMBLE, DRIPLEAF, PIT_TILE, LOCKED_LOWER, LOCKED_UPPER, PORTCULLIS, ONEWAY_LOWER, ONEWAY_UPPER, EXIT_FRAME -> true;
            default -> false;
        };
    }
}
