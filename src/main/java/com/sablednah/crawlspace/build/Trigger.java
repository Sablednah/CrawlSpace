package com.sablednah.crawlspace.build;

/**
 * Something the mod watches for in a built dungeon, at a blueprint position:
 * an ordinary vanilla block or floor tile, so nothing needs redstone and a
 * vanilla client sees nothing unusual until it fires.
 *
 * @param targets blueprint positions it acts on, as {x, y, z}
 */
public record Trigger(Kind kind, int x, int y, int z, int level, int[][] targets) {

    public enum Kind {
        /** A lever: opens (or closes) its level's locked doors. */
        LEVER,
        /** A secret wall: using it opens the doorway behind it. */
        SECRET,
        /** A hidden floor tile: darts from the nearest wall. */
        DARTS,
        /** A hidden floor tile: a cloud of poison. */
        GAS,
        /** Not a trigger: where a treasure room's hoard is, for hints. */
        TREASURE,
        /** A room's monsters, woken the first time a player comes near. Targets are where they stand. */
        ENCOUNTER,
        /** A lair's boss and its followers; the first target is the boss's place. */
        BOSS,
        /** Not a trigger: a plate or wire that does nothing, kept here so dressing leaves it alone. */
        DECOY;

        public boolean isTrap() {
            return this == DARTS || this == GAS;
        }

        public boolean isEncounter() {
            return this == ENCOUNTER || this == BOSS;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
