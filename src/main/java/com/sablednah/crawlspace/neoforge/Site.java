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
    public static final int PLANNER_VERSION = 13; // 2: stairs with landings; 3: dressing, encounters, loot; 5: puzzle rooms;
                                                 // 6: portcullises and the layout push; 7: feelings;
                                                 // 8: more kinds of trap; 9: vaults; 10: ice boards;
                                                 // 11: ordered kills; 12: shrine altars, the rumour lectern;
                                                 // 13: trap tells

    /**
     * A plan and its blueprint, built once and shared by every chunk that asks.
     *
     * @param buriedTop the highest block, relative to the origin, outside the tower's columns
     */
    public record Built(DungeonPlan plan, Blueprint blueprint, int buriedTop) {
        Built(DungeonPlan plan, Blueprint blueprint) {
            this(plan, blueprint, buriedTop(blueprint));
        }

        private static int buriedTop(Blueprint bp) {
            int[] top = {Integer.MIN_VALUE};
            bp.forEachColumn(c -> {
                if (!towerColumn(c.x(), c.z())) {
                    for (int i = c.codes().length - 1; i >= 0; i--) {
                        if (c.codes()[i] != 0) {
                            top[0] = Math.max(top[0], c.y0() + i);
                            break;
                        }
                    }
                }
            });
            return top[0];
        }
    }

    /** Half the tower's width: columns this close to the origin belong to the tower's piece. */
    public static final int TOWER_REACH = com.sablednah.crawlspace.build.Towers.REACH;

    /** Whether a blueprint column belongs to the tower (and the stair under it) rather than the buried levels. */
    public static boolean towerColumn(int x, int z) {
        return Math.abs(x) <= TOWER_REACH && Math.abs(z) <= TOWER_REACH;
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
            int lowest = origin.getY() - top - (n - 1) * plan.levelSpacing() - Blueprinter.BELOW;
            if (lowest >= worldMinY + 6) {
                Site site = new Site(seed, n, top, style, origin, PLANNER_VERSION);
                synchronized (CACHE) {
                    CACHE.putIfAbsent(site.key(), new Built(plan.withTop(top), Blueprinter.blueprint(plan.withTop(top), style).compact()));
                }
                return site;
            }
        }
        return null;
    }

    private String key() {
        return seed + "/" + levels + "/" + top + "/" + style;
    }

    public Built built() {
        synchronized (CACHE) {
            Built b = CACHE.get(key());
            if (b == null) {
                DungeonPlan plan = Planner.plan(seed, levels).withTop(top);
                b = new Built(plan, Blueprinter.blueprint(plan, style).compact());
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

    /**
     * After a block is set: chests and barrels get their loot table, spawners
     * their monster. Loot tiers run one per two levels; a hoard is a tier
     * richer.
     */
    public void afterPlace(Built built, net.minecraft.world.level.LevelAccessor level, BlockPos pos, int code,
            net.minecraft.util.RandomSource random) {
        Part part = Blueprint.part(code);
        int li = Math.max(0, Blueprint.level(code));
        // A dark level's loot is a tier better: the dark is the price.
        boolean dark = built.plan().levels().get(Math.min(li, built.plan().levels().size() - 1)).feeling == com.sablednah.crawlspace.plan.Feeling.DARK;
        int tier = Math.min(5, 1 + li / 2 + (dark ? 1 : 0));
        String table = switch (part) {
            case CHEST -> "chests/tier" + tier;
            case HOARD_CHEST -> "chests/tier" + Math.min(5, tier + 1);
            case BARREL -> "chests/supplies";
            default -> null;
        };
        if (part == Part.RUMOURS && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern) {
            lectern.setBook(Rumours.book(this, built));
            return;
        }
        if (part == Part.KEY_CHEST && level.getBlockEntity(pos) instanceof net.minecraft.world.Container chest) {
            // Only the key, and no loot table: a per-player loot mod (Lootr) takes over containers that have
            // one, and the key must stay a real item in a plain chest that everyone shares.
            String theme = built.plan().levels().get(Math.min(li, built.plan().levels().size() - 1)).theme.name();
            chest.setItem(13, Keys.make(this, li, theme));
        } else if (table != null) {
            net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, random, pos,
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                            net.minecraft.resources.Identifier.fromNamespaceAndPath("crawlspace", table)));
        } else if (part == Part.SPAWNER
                && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            String theme = built.plan().levels().get(Math.min(li, built.plan().levels().size() - 1)).theme.name();
            spawner.setEntityId(Bestiary.common(theme, random), random);
        }
    }

    /** Whether a world position is inside this dungeon's footprint, tower included. */
    public boolean contains(BlockPos pos) {
        int r = com.sablednah.crawlspace.plan.LevelPlan.RADIUS;
        return Math.abs(pos.getX() - origin.getX()) <= r && Math.abs(pos.getZ() - origin.getZ()) <= r
                && pos.getY() >= bottomY() && pos.getY() <= origin.getY() + Blueprinter.TOP;
    }

    /** The lowest block the dungeon sets, in world y. */
    public int bottomY() {
        return origin.getY() - top - (levels - 1) * Planner.LEVEL_SPACING - Blueprinter.BELOW;
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
