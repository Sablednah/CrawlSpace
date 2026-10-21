package com.sablednah.crawlspace.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Planner;
import com.sablednah.crawlspace.plan.Theme;

/**
 * A datapack can set a theme's numbers anywhere in the range Theme allows.
 * Every shape alone, every corridor style alone, at both ends of the room
 * scale and the most height spread, has to plan, on every level, and stay
 * sealed from the world it is cut into.
 */
class ThemeKnobsTest {

    private static Theme extreme(int shape, int style, double scale, double narrow) {
        double[] shapes = new double[Theme.SHAPES.size()];
        shapes[shape] = 1;
        double[] styles = new double[Theme.CORRIDORS.size()];
        styles[style] = 1;
        return new Theme("Test", shapes, styles, narrow, Theme.MAX_SPREAD, 1.0, scale, Theme.CAVERNS.dressing());
    }

    @Test
    void everyExtremePlans() {
        List<String> failed = new ArrayList<>();
        int n = 0;
        for (int shape = 0; shape < Theme.SHAPES.size(); shape++) {
            for (int style = 0; style < Theme.CORRIDORS.size(); style++) {
                for (double scale : new double[] {Theme.MIN_SCALE, Theme.MAX_SCALE}) {
                    Theme t = extreme(shape, style, scale, style % 2 == 0 ? 0 : 1);
                    for (long seed = 0; seed < 3; seed++) {
                        n++;
                        try {
                            DungeonPlan plan = Planner.plan(seed * 7919 + shape * 31 + style, 3, List.of(t));
                            List<String> leaks = BlueprintTest.leaks(Blueprinter.blueprint(plan).compact());
                            if (!leaks.isEmpty()) {
                                failed.add(Theme.SHAPES.get(shape) + "/" + Theme.CORRIDORS.get(style) + "/" + scale + " seed " + seed + ": " + leaks.get(0));
                            }
                        } catch (RuntimeException e) {
                            failed.add(Theme.SHAPES.get(shape) + "/" + Theme.CORRIDORS.get(style) + "/" + scale + " seed " + seed + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        // Every flavour, with every dressing rule on at full and then all off.
        for (String flavour : com.sablednah.crawlspace.plan.Dressing.FLAVOURS) {
            for (boolean on : new boolean[] {true, false}) {
                double x = on ? 1 : 0;
                com.sablednah.crawlspace.plan.Dressing d = new com.sablednah.crawlspace.plan.Dressing(flavour, on, on, on, x, x,
                        on ? com.sablednah.crawlspace.plan.Dressing.CLUTTER : List.of(), x, on, x, on, on, x, x, x);
                Theme base = Theme.DEEP_HALLS;
                Theme t = new Theme("Test", base.shapeWeights(), base.styleWeights(), base.narrowChance(), Theme.MAX_SPREAD,
                        base.poolChance(), base.roomScale(), d);
                for (long seed = 0; seed < 2; seed++) {
                    n++;
                    try {
                        DungeonPlan plan = Planner.plan(seed * 104729 + flavour.hashCode(), 4, List.of(t));
                        List<String> leaks = BlueprintTest.leaks(Blueprinter.blueprint(plan).compact());
                        if (!leaks.isEmpty()) {
                            failed.add(flavour + "/" + on + " seed " + seed + ": " + leaks.get(0));
                        }
                    } catch (RuntimeException e) {
                        failed.add(flavour + "/" + on + " seed " + seed + ": " + e);
                    }
                }
            }
        }
        System.out.println("EXTREMES " + n + " planned, " + failed.size() + " failed");
        failed.stream().limit(10).forEach(System.out::println);
        assertTrue(failed.isEmpty(), failed.size() + " failed, first: " + (failed.isEmpty() ? "" : failed.get(0)));
    }
}
