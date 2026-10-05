package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * A whole dungeon: every level, top first. Level 0's stair up is the entrance
 * tower's, at (0, 0).
 *
 * @param levelSpacing blocks from one level's floor to the next one's
 * @param top          blocks from the ground at the tower down to level 0's floor;
 *                     deeper where the land over the dungeon dips (see {@code Blueprinter.requiredTop})
 */
public record DungeonPlan(long seed, int levelSpacing, int top, List<LevelPlan> levels) {

    /** The shallowest the first level goes: room for the tower's stair and a roof of ground. */
    public static final int MIN_TOP = 14;

    public DungeonPlan withTop(int newTop) {
        return new DungeonPlan(seed, levelSpacing, newTop, levels);
    }
}
