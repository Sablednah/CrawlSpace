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
        /** A guard room's numbered monsters, to be killed in order (Hypixel's Higher or Lower). */
        ORDERED,
        /** Not a trigger: a plate or wire that does nothing, kept here so dressing leaves it alone. */
        DECOY,
        /**
         * A puzzle room's restart point, on its centre block: where the void sends you.
         * Targets: the room's floor bounds, {minX, y, minZ} and {maxX, y, maxZ}.
         */
        PUZZLE,
        /** A pit trap, at the middle of its 3x3 floor; targets are all nine tiles. */
        PIT,
        /** Not a trap of its own: one of a pit trap's eight outer tiles; its target is the middle. */
        PIT_EDGE,
        /** A portcullis locking a doorway, on its lower bar; the targets are its bars. Raised with the level's key. */
        PORTCULLIS,
        /**
         * A portcullis that drops behind you: on the floor tile two in from the archway, fired by
         * walking in (never out). The targets are the archway's three blocks, bottom first.
         */
        PORTCULLIS_TRAP,
        /** A lever that raises a dropped portcullis; the targets are its bars. */
        WINCH,
        /**
         * An ice board (Hypixel's Ice Fill): cross every tile exactly once. At the room's centre; the
         * first target is where the hoard appears when it is solved, the rest are the tiles.
         */
        ICE_BOARD,
        /** A vault's pedestal; the targets are all three pedestals, its own first among them for the group's first. */
        VAULT,
        /** Hidden floor tiles, as the plan's {@code TrapKind}s of the same names. */
        ALARM,
        WEBS,
        ROCKFALL,
        FROST,
        FIRE,
        SUMMON,
        /** A one-way door's lever, inside the room on its far side; the target is the door's lower half. */
        ONEWAY;

        public boolean isTrap() {
            return switch (this) {
                case DARTS, GAS, PIT, PORTCULLIS_TRAP, ALARM, WEBS, ROCKFALL, FROST, FIRE, SUMMON -> true;
                default -> false;
            };
        }

        public boolean isEncounter() {
            return this == ENCOUNTER || this == BOSS || this == ORDERED;
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
