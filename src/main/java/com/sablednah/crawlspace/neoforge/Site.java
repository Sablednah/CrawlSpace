package com.sablednah.crawlspace.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Planner;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One dungeon in a world: everything needed to rebuild its plan and blueprint
 * exactly. The command and worldgen both make these; worldgen saves one in its
 * structure piece.
 *
 * <p><b>The plan is regenerated from the seed, not saved.</b> A saved plan
 * would be several hundred kilobytes per dungeon in one chunk's data. The
 * price is that a planner change between versions moves the parts of a
 * dungeon not yet generated: {@code planner} records the version that planned
 * it, and a mismatch is logged.</p>
 *
 * @param top    blocks from the ground at the tower down to level 0's floor
 * @param style  how the entrance tower is dressed (see {@code Palettes})
 * @param origin the ground at the tower's centre
 */
public record Site(long seed, int levels, int top, String style, BlockPos origin, int planner) {

    /** Bump whenever a planner or blueprint change would alter an existing seed's dungeon. */
    public static final int PLANNER_VERSION = 1;

    /** A plan and its blueprint, built once and shared by every chunk that asks. */
    public record Built(DungeonPlan plan, Blueprint blueprint) {
    }

    private static final Map<String, Built> CACHE = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Built> eldest) {
            return size() > 6;
        }
    };

    /**
     * The deepest site that fits: as many levels as wanted, fewer if the
     * ground dips or the world's floor is near, or null if not even
     * {@code minLevels} fit.
     *
     * @param surface the ground at (x, z) relative to the origin's
     */
    public static Site fit(long seed, int wanted, int minLevels, BlockPos origin, IntBinaryOperator surface,
            int worldMinY, String style) {
        for (int n = wanted; n >= minLevels; n--) {
            DungeonPlan plan = Planner.plan(seed, n);
            int top = Blueprinter.requiredTop(plan, surface);
            // The lowest block is level n-1's floor block, 3 under its lowest floor; keep it clear of the bedrock layers.
            int lowest = origin.getY() - top - (n - 1) * plan.levelSpacing() - 4;
            if (lowest >= worldMinY + 6) {
                Site site = new Site(seed, n, top, style, origin, PLANNER_VERSION);
                synchronized (CACHE) {
                    CACHE.putIfAbsent(site.key(), new Built(plan.withTop(top), Blueprinter.blueprint(plan.withTop(top)).compact()));
                }
                return site;
            }
        }
        return null;
    }

    private String key() {
        return seed + "/" + levels + "/" + top;
    }

    public Built built() {
        synchronized (CACHE) {
            Built b = CACHE.get(key());
            if (b == null) {
                DungeonPlan plan = Planner.plan(seed, levels).withTop(top);
                b = new Built(plan, Blueprinter.blueprint(plan).compact());
                CACHE.put(key(), b);
            }
            return b;
        }
    }

    /** The block a blueprint code stands for at a world position. */
    public BlockState state(Built built, int code, BlockPos pos) {
        Part part = Blueprint.part(code);
        int li = Math.max(0, Blueprint.level(code));
        String theme = built.plan().levels().get(Math.min(li, built.plan().levels().size() - 1)).theme.name();
        return Palettes.state(theme, style, part, Blueprint.facing(code), pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether a world position is inside this dungeon's footprint, tower included. */
    public boolean contains(BlockPos pos) {
        int r = com.sablednah.crawlspace.plan.LevelPlan.RADIUS;
        return Math.abs(pos.getX() - origin.getX()) <= r && Math.abs(pos.getZ() - origin.getZ()) <= r
                && pos.getY() >= bottomY() && pos.getY() <= origin.getY() + Blueprinter.TOWER_HEIGHT + 3;
    }

    /** The lowest block the dungeon sets, in world y. */
    public int bottomY() {
        return origin.getY() - top - (levels - 1) * Planner.LEVEL_SPACING - 4;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("seed", seed);
        t.putInt("levels", levels);
        t.putInt("top", top);
        t.putString("style", style);
        t.putInt("x", origin.getX());
        t.putInt("y", origin.getY());
        t.putInt("z", origin.getZ());
        t.putInt("planner", planner);
        return t;
    }

    public static Site load(CompoundTag t) {
        return new Site(t.getLongOr("seed", 0L), t.getIntOr("levels", 1), t.getIntOr("top", DungeonPlan.MIN_TOP),
                t.getStringOr("style", "stone"),
                new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0)), t.getIntOr("planner", 0));
    }
}
