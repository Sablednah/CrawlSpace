package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floors that crumble: stone brick, then cracked, then cobblestone, then gone.
 * Puzzle-room path blocks give way a moment after you step on them and re-form
 * a few seconds later, so a maze can never be left unsolvable. A pit trap's
 * tiles go faster, all nine together, and stay gone. Kept in memory: a
 * crumble cut short by a restart leaves whatever stage it had reached.
 */
public final class Crumbles {

    /** The stages a crumbling block passes through before it is gone. */
    private static final BlockState[] STAGES = {
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()};

    private static final class Crumble {
        final ServerLevel level;
        final List<BlockPos> blocks;
        final int interval;
        final int restoreAfter;
        final Runnable onCollapse;
        int stage;
        long next;
        long restoreAt = -1;

        Crumble(ServerLevel level, List<BlockPos> blocks, int interval, int restoreAfter, Runnable onCollapse) {
            this.level = level;
            this.blocks = blocks;
            this.interval = interval;
            this.restoreAfter = restoreAfter;
            this.onCollapse = onCollapse;
            this.next = level.getGameTime() + interval;
        }
    }

    private static final List<Crumble> ACTIVE = new ArrayList<>();
    private static final Set<BlockPos> BUSY = new HashSet<>();

    private Crumbles() {
    }

    /**
     * Starts the given blocks crumbling, unless any already is.
     *
     * @param interval     ticks per stage
     * @param restoreAfter ticks after collapse before the blocks re-form as stone brick, or -1 never
     * @param onCollapse   run as the last stage goes, or null
     */
    static void start(ServerLevel level, List<BlockPos> blocks, int interval, int restoreAfter, Runnable onCollapse) {
        for (BlockPos p : blocks) {
            if (BUSY.contains(p)) {
                return;
            }
        }
        BUSY.addAll(blocks);
        Crumble c = new Crumble(level, List.copyOf(blocks), interval, restoreAfter, onCollapse);
        ACTIVE.add(c);
        level.playSound(null, blocks.get(0), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1f, 0.6f);
    }

    private record Later(ServerLevel level, long at, Runnable task) {
    }

    private static final List<Later> LATER = new ArrayList<>();

    /** Runs a task {@code ticks} from now: a pit's ladder, once whoever fell has landed. */
    static void later(ServerLevel level, int ticks, Runnable task) {
        LATER.add(new Later(level, level.getGameTime() + ticks, task));
    }

    public static void tick() {
        for (Iterator<Later> l = LATER.iterator(); l.hasNext();) {
            Later t = l.next();
            if (t.level().getGameTime() >= t.at()) {
                l.remove();
                t.task().run();
            }
        }
        Iterator<Crumble> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Crumble c = it.next();
            long now = c.level.getGameTime();
            if (c.restoreAt >= 0) {
                if (now >= c.restoreAt) {
                    for (BlockPos p : c.blocks) {
                        if (c.level.getBlockState(p).isAir()) {
                            c.level.setBlock(p, STAGES[0], 3);
                        }
                    }
                    BUSY.removeAll(c.blocks);
                    it.remove();
                }
                continue;
            }
            if (now < c.next) {
                continue;
            }
            c.stage++;
            c.next = now + c.interval;
            boolean gone = c.stage >= STAGES.length;
            for (BlockPos p : c.blocks) {
                BlockState was = c.level.getBlockState(p);
                c.level.setBlock(p, gone ? Blocks.AIR.defaultBlockState() : STAGES[c.stage], 3);
                c.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, was), p.getX() + 0.5, p.getY() + 0.9,
                        p.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
            }
            c.level.playSound(null, c.blocks.get(0), gone ? SoundEvents.GRAVEL_BREAK : SoundEvents.STONE_BREAK,
                    SoundSource.BLOCKS, 1f, gone ? 0.7f : 1.1f);
            if (gone) {
                if (c.onCollapse != null) {
                    c.onCollapse.run();
                }
                if (c.restoreAfter >= 0) {
                    c.restoreAt = now + c.restoreAfter;
                } else {
                    BUSY.removeAll(c.blocks);
                    it.remove();
                }
            }
        }
    }

    public static void clear() {
        LATER.clear();
        ACTIVE.clear();
        BUSY.clear();
    }
}
