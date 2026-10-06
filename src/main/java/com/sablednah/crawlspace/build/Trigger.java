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
        GAS;

        public boolean isTrap() {
            return this == DARTS || this == GAS;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
