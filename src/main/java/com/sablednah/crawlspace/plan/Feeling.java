package com.sablednah.crawlspace.plan;

/**
 * A level's mood (Shattered Pixel Dungeon's level feelings): one change to how
 * it is planned, built or woken, announced as you arrive so you know what you
 * are in for. Most levels have none. Chosen before the level is planned, from
 * its own dice, because some of them change the plan itself.
 */
public enum Feeling {
    NONE(""),
    /** More secret rooms, and more of the shortcuts behind secret walls. */
    HOLLOW("The walls here sound hollow."),
    /** Pools in many more rooms. */
    DAMP("The air is damp, and water drips somewhere."),
    /** No lanterns but at the stairs, and better loot for it. */
    DARK("It is very dark here."),
    /** More of the rooms are occupied, and more crowded. */
    CROWDED("You hear many feet."),
    /** Twice the traps. */
    TRAPPED("Something clicks, far off. Watch the floor."),
    /** A hunter roams the level and comes for whoever arrives. */
    HUNTED("Something large hunts these halls.");

    public final String message;

    Feeling(String message) {
        this.message = message;
    }

    /** How often a level has a feeling: never the first, then more often deeper down. */
    static double chance(int index) {
        return index == 0 ? 0 : Math.min(0.55, 0.3 + 0.05 * index);
    }

    /** A level's feeling, from its own dice: adding feelings moved nothing in a level without one. */
    static Feeling roll(Dice dice, int index) {
        if (!dice.chance(chance(index))) {
            return NONE;
        }
        Feeling[] all = values();
        return all[1 + dice.nextInt(all.length - 1)];
    }
}
