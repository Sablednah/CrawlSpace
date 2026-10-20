package com.sablednah.crawlspace.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.Planner;

/**
 * The built-in dungeons, block for block, as one number. Data-driven themes
 * (1.1.0) moved every hard-coded theme rule into data; this proves the
 * built-in themes still build exactly what they did.
 *
 * <p>A change that is meant to alter dungeons updates this number, with the
 * reason below. Site.PLANNER_VERSION moves too only if rooms, walls or triggers
 * move: a dungeon of an older planner loses its protection, traps and clock, a
 * far bigger price than a few blocks of dressing that differ.</p>
 *
 * <ul>
 * <li>499445310656077114: 1.1.0, data themes (the refactor this test was taken for).</li>
 * <li>218812467420928347: 4bf3c89, a stair well's railing no longer cuts off a
 * doorway. Railing only; planner unchanged.</li>
 * </ul>
 */
class FingerprintTest {

    static final long EXPECTED = 218812467420928347L;

    static long fingerprint() {
        long sum = 0;
        for (long seed = 100; seed < 112; seed++) {
            DungeonPlan plan = Planner.plan(seed, 9);
            Blueprint bp = Blueprinter.blueprint(plan, "stone").compact();
            long[] acc = {0};
            bp.forEachColumn(col -> {
                for (int i = 0; i < col.codes().length; i++) {
                    if (col.codes()[i] != 0) {
                        acc[0] += Dice.mix(Dice.mix(Dice.mix(col.x() * 0x9E3779B1L) ^ (col.y0() + i) * 0x85EBCA77L)
                                ^ col.z() * 0xC2B2AE3DL ^ (long) col.codes()[i] << 20);
                    }
                }
            });
            for (Trigger t : bp.triggers()) {
                acc[0] += Dice.mix(java.util.Objects.hash(t.kind().ordinal(), t.x(), t.y(), t.z(), t.level(), java.util.Arrays.deepHashCode(t.targets())));
            }
            sum = sum * 31 + acc[0];
        }
        return sum;
    }

    @Test
    void builtInDungeonsAreUnchanged() {
        long got = fingerprint();
        System.out.println("FINGERPRINT " + got);
        assertEquals(EXPECTED, got);
    }
}
