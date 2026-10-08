package com.sablednah.crawlspace.plan;

/**
 * What a room is for. Roles decide size and shape now, and decor and
 * population later: a theme dresses a LAIR differently from a SHRINE.
 */
public enum Role {
    /** Where the stair from above arrives. */
    ENTRY('E'),
    /** Holds the stair down. */
    EXIT('X'),
    /** An ordinary room on the way. */
    ROOM('R'),
    /** A big room, often pillared. */
    HALL('H'),
    /** Guards the way to something. */
    GUARD('G'),
    /** A dead end worth reaching. */
    TREASURE('T'),
    /** Behind a secret door. */
    SECRET('S'),
    /** Where the level's worst lives. */
    LAIR('L'),
    /** A quiet room: a pool, an altar. */
    SHRINE('W'),
    /** Holds the lever for the level's locked door. */
    KEY('K'),
    /**
     * A maze over the void: one-wide paths from a restart block at the centre
     * to every doorway and to a hoard chest. Step off and you are sent back.
     * Chosen after the level is planned, from its own dice (see Planner).
     */
    PUZZLE('P');

    public final char letter;

    Role(char letter) {
        this.letter = letter;
    }
}
