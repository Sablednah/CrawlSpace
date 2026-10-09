package com.sablednah.crawlspace.plan;

/** What a hidden trap tile does when somebody steps on it. Each fires once. */
public enum TrapKind {
    /** Darts from the nearest wall. */
    DARTS("dart trap", 0, 4),
    /** A cloud of poison where it was sprung. */
    GAS("gas trap", 2, 3),
    /** A bell: every sleeping room near it wakes. */
    ALARM("alarm", 1, 2),
    /** Cobwebs burst round you, and you are slowed. */
    WEBS("web trap", 1, 2),
    /** Stalactites drop from the ceiling on to you. */
    ROCKFALL("rockfall", 2, 2),
    /** A freezing mist. */
    FROST("frost trap", 2, 1),
    /** Flames from the floor round you. */
    FIRE("fire trap", 3, 1),
    /** Monsters burst out round you. */
    SUMMON("ambush", 3, 1);

    /** What the player is told they disarmed. */
    public final String description;
    /** The first level (0-based) it is set on. */
    public final int fromLevel;
    final int weight;

    TrapKind(String description, int fromLevel, int weight) {
        this.description = description;
        this.fromLevel = fromLevel;
        this.weight = weight;
    }

    /** A trap for level {@code index}: a weighted draw among those set that deep. */
    static TrapKind roll(Dice dice, int index) {
        int total = 0;
        for (TrapKind k : values()) {
            total += index >= k.fromLevel ? k.weight : 0;
        }
        int r = dice.nextInt(total);
        for (TrapKind k : values()) {
            if (index >= k.fromLevel) {
                r -= k.weight;
                if (r < 0) {
                    return k;
                }
            }
        }
        return DARTS;
    }
}
