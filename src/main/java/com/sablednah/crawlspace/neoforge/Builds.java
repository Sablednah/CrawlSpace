package com.sablednah.crawlspace.neoforge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.plan.DungeonPlan;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places blueprints a slice per server tick, one build at a time, and keeps
 * each player's last build so it can be undone or visited. The same shape as
 * WadCraft's queue: the per-tick cost is one budget however many builds wait.
 */
public final class Builds {

    /** Blocks per tick; WadCraft measured this as keeping a dedicated server near 20 TPS. */
    static final int BLOCKS_PER_TICK = 20_000;
    /** Beyond this many blocks, undo is dropped rather than eating the heap. */
    private static final int UNDO_LIMIT = 8_000_000;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** A finished or running build: which dungeon, and in which world. */
    record Placed(ServerLevel level, Site site, Site.Built built) {
        BlockPos origin() {
            return site.origin();
        }

        DungeonPlan plan() {
            return built.plan();
        }
    }

    interface Job {
        /** Do up to {@code budget} blocks; true when finished. */
        boolean tick(int budget);

        void cancel(String why);
    }

    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, Placed> LAST = new HashMap<>();
    private static final Map<UUID, Undo> UNDO = new HashMap<>();

    private Builds() {
    }

    public static void tick() {
        Job job = QUEUE.peek();
        if (job != null && job.tick(BLOCKS_PER_TICK)) {
            QUEUE.poll();
        }
    }

    public static void clear() {
        for (Job j : QUEUE) {
            j.cancel("the server is stopping");
        }
        QUEUE.clear();
        LAST.clear();
        UNDO.clear();
    }

    static int cancelAll() {
        int n = QUEUE.size();
        for (Job j : QUEUE) {
            j.cancel("cancelled");
        }
        QUEUE.clear();
        return n;
    }

    static Placed last(UUID who) {
        return LAST.get(who);
    }

    static Undo takeUndo(UUID who) {
        return UNDO.remove(who);
    }

    static void add(Job job) {
        QUEUE.add(job);
    }

    /** Queues a blueprint; {@code done} hears the block count, or a negative number if it was cancelled. */
    static void build(UUID who, Placed placed, Blueprint bp, Consumer<String> progress, Consumer<Long> done) {
        LAST.put(who, placed);
        QUEUE.add(new Place(who, placed, bp, progress, done));
    }

    /** Sets a blueprint column by column. */
    private static final class Place implements Job {
        private final UUID who;
        private final Placed placed;
        private final Blueprint bp;
        private final List<Blueprint.Column> columns = new ArrayList<>();
        private final Consumer<String> progress;
        private final Consumer<Long> done;
        private int column;
        private int y;
        private long count;
        private int lastQuarter;
        private final List<BlockPos> connect = new ArrayList<>();
        private LongArrayList undoPositions = new LongArrayList();
        private List<BlockState> undoStates = new ArrayList<>();

        Place(UUID who, Placed placed, Blueprint bp, Consumer<String> progress, Consumer<Long> done) {
            this.who = who;
            this.placed = placed;
            this.bp = bp;
            this.progress = progress;
            this.done = done;
            bp.forEachColumn(columns::add);
            // Top-down by column is fine; sort so neighbouring columns land together, chunk by chunk.
            columns.sort((a, b) -> {
                int ax = a.x() >> 4;
                int bx = b.x() >> 4;
                if (ax != bx) {
                    return Integer.compare(ax, bx);
                }
                int az = a.z() >> 4;
                int bz = b.z() >> 4;
                if (az != bz) {
                    return Integer.compare(az, bz);
                }
                return Integer.compare(a.x() * 31 + a.z(), b.x() * 31 + b.z());
            });
        }

        @Override
        public boolean tick(int budget) {
            ServerLevel level = placed.level();
            BlockPos origin = placed.origin();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (budget > 0 && column < columns.size()) {
                Blueprint.Column col = columns.get(column);
                int x = col.x();
                int z = col.z();
                int[] codes = col.codes();
                while (y < codes.length && budget > 0) {
                    int code = codes[y];
                    int by = col.y0() + y;
                    y++;
                    if (code == 0) {
                        continue;
                    }
                    budget--;
                    pos.set(origin.getX() + x, origin.getY() + by, origin.getZ() + z);
                    if (level.isOutsideBuildHeight(pos.getY())) {
                        continue;
                    }
                    BlockState state = placed.site().state(placed.built(), code, pos);
                    BlockState before = level.getBlockState(pos);
                    if (before == state) {
                        continue;
                    }
                    if (undoPositions != null) {
                        undoPositions.add(pos.asLong());
                        undoStates.add(before);
                        if (undoPositions.size() > UNDO_LIMIT) {
                            undoPositions = null;
                            undoStates = null;
                        }
                    }
                    level.setBlock(pos, state, FLAGS);
                    placed.site().afterPlace(placed.built(), level, pos, code, level.getRandom());
                    if (Palettes.connects(Blueprint.part(code))) {
                        connect.add(pos.immutable());
                    }
                    count++;
                }
                if (y >= codes.length) {
                    column++;
                    y = 0;
                }
            }
            int quarter = (int) (4L * column / Math.max(1, columns.size()));
            if (quarter > lastQuarter && quarter < 4) {
                progress.accept(quarter * 25 + "%");
            }
            lastQuarter = quarter;
            if (column < columns.size()) {
                return false;
            }
            // Railings and fences join up now everything round them is in.
            for (BlockPos p : connect) {
                level.setBlock(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), FLAGS);
            }
            connect.clear();
            if (undoPositions != null) {
                UNDO.put(who, new Undo(placed.level(), undoPositions, undoStates));
            } else {
                UNDO.remove(who);
            }
            done.accept(count);
            return true;
        }

        @Override
        public void cancel(String why) {
            done.accept(-1L);
        }
    }

    /** Puts back what a build replaced, newest first. Block states only. */
    static final class Undo implements Job {
        private final ServerLevel level;
        private final LongArrayList positions;
        private final List<BlockState> states;
        private int next;
        Consumer<Integer> done = n -> { };

        Undo(ServerLevel level, LongArrayList positions, List<BlockState> states) {
            this.level = level;
            this.positions = positions;
            this.states = states;
            this.next = positions.size() - 1;
        }

        @Override
        public boolean tick(int budget) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (budget-- > 0 && next >= 0) {
                pos.set(positions.getLong(next));
                level.setBlock(pos, states.get(next), FLAGS);
                next--;
            }
            if (next >= 0) {
                return false;
            }
            done.accept(positions.size());
            return true;
        }

        @Override
        public void cancel(String why) {
        }
    }
}
