package com.sablednah.crawlspace.plan;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.debug.PlanRenderer;

/** Draws the first rejected attempt it can provoke, with its problems printed: for debugging the planner. */
class RejectedTest {

    @Test
    void drawARejectedAttempt() throws Exception {
        for (long seed = 0; seed < 50; seed++) {
            LevelPlanner.lastRejected = null;
            Planner.plan(seed, 4);
            LevelPlan bad = LevelPlanner.lastRejected;
            if (bad != null) {
                System.out.println("rejected: seed " + seed + " level " + bad.index + ": " + PlanCheck.level(bad));
                for (Link l : bad.links) {
                    System.out.println("  " + l + (l.spur ? " spur" : ""));
                }
                PlanRenderer.write(new DungeonPlan(seed, 12, DungeonPlan.MIN_TOP, List.of(bad)), 6, 1,
                        new File(System.getProperty("crawlspace.renderDir", "build/plan-renders"), "rejected.png"));
                return;
            }
        }
    }
}
