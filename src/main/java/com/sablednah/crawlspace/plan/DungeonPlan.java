package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * A whole dungeon: every level, top first. Level 0's stair up is the entrance
 * tower's, at (0, 0).
 *
 * @param levelSpacing blocks from one level's floor to the next one's
 */
public record DungeonPlan(long seed, int levelSpacing, List<LevelPlan> levels) {
}
