package com.sablednah.crawlspace.plan;

/** How a corridor meets the rooms at each end. */
public enum LinkKind {
    /** An open archway. */
    OPEN,
    /** A door. */
    DOOR,
    /** A door opened by the level's lever. Only ever on the loop, so there is always another way. */
    LOCKED,
    /** Hidden: looks like wall until found. Only ever to an optional room. */
    SECRET,
    /**
     * A shortcut back: an iron door in the wall of the room further from the
     * entry ({@code a}), whose lever is inside that room only. Found from the
     * far side and opened from there; from the near side it will not open.
     */
    ONEWAY
}
