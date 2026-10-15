package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * Ice boards (Hypixel's Ice Fill): every tile crossed exactly once, in one go.
 * A tile stepped on turns to packed ice. Stepping on packed ice again, to a
 * tile that is not beside the last one, or off the board, cracks the whole
 * board back to fresh ice (Sable, 2026-10-15: it was walked in rows). While
 * anyone is out on it, a monster comes every ten seconds or so, with
 * knockback, to push them off the line. Every tile packed, and the hoard appears where the
 * board's trigger says, with a blessing for whoever finished it. The board's
 * progress is kept in memory: a restart gives a fresh board.
 */
public final class IceBoards {

    /** Per board (its trigger's key): the tiles crossed so far, as world positions. */
    private static final Map<Long, Set<Long>> CROSSED = new HashMap<>();
    /** Per player: the tile they were last on, so standing still counts once. */
    private static final Map<java.util.UUID, Long> ON = new HashMap<>();
    /** Per player: the board they are crossing (its key), so stepping off it can crack it. */
    private static final Map<java.util.UUID, Long> BOARD = new HashMap<>();
    /** Per board: when the next monster comes. */
    private static final Map<Long, Long> NEXT = new HashMap<>();

    private IceBoards() {
    }

    /** From Triggers' tick: a player on the ground in a dungeon, with their feet at {@code feet}. */
    static void step(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        BlockPos below = feet.below();
        net.minecraft.world.level.block.state.BlockState under = level.getBlockState(below);
        if (!under.is(FRESH) && !under.is(Blocks.ICE) && !under.is(Blocks.PACKED_ICE)) {
            ON.remove(player.getUUID());
            Long left = BOARD.remove(player.getUUID());
            if (left != null) {
                crack(level, left, null, player, "You stepped off the ice, and it cracks back. Cross it in one go.");
            }
            return;
        }
        Long was = ON.put(player.getUUID(), below.asLong());
        spawn(level, player, site, below);
        if (was != null && was == below.asLong()) {
            return;
        }
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.ICE_BOARD) {
                continue;
            }
            boolean mine = false;
            for (int k = 1; k < t.targets().length && !mine; k++) {
                mine = o.offset(t.targets()[k][0], t.targets()[k][1], t.targets()[k][2]).equals(below);
            }
            if (!mine) {
                continue;
            }
            BlockPos key = o.offset(t.x(), t.y(), t.z());
            CrawlState state = CrawlState.of(level);
            if (state.hasFired(key)) {
                return; // solved
            }
            Set<Long> crossed = CROSSED.computeIfAbsent(key.asLong(), k -> new HashSet<>());
            mend(level, o, t, crossed);
            BOARD.put(player.getUUID(), key.asLong());
            // A step must be to a tile beside the last: a jump over tiles, or back on from elsewhere, starts again.
            boolean beside = was == null || BlockPos.of(was).distManhattan(below) == 1;
            if (!crossed.isEmpty() && !beside) {
                crack(level, key.asLong(), below, player, "Not beside the last tile: the ice cracks back. Start again from here, one step at a time.");
                return;
            }
            if (!crossed.add(below.asLong())) {
                // Twice on one tile: the board cracks back.
                for (long p : crossed) {
                    level.setBlock(BlockPos.of(p), FRESH.defaultBlockState(), 3);
                }
                crossed.clear();
                // The tile you stand on is where you start again: on the rig, without this, the tile a
                // reset left you on was never counted, and a perfect walk from there fell one short.
                crossed.add(below.asLong());
                level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 3);
                level.playSound(null, below, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 1.4f);
                Triggers.tell(player, "The ice cracks back. Start again from here: every tile once, and only once.");
                return;
            }
            level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 3);
            level.playSound(null, below, SoundEvents.GLASS_STEP, SoundSource.BLOCKS, 0.8f, 1.6f);
            if (crossed.size() == t.targets().length - 1) {
                state.fire(key);
                CROSSED.remove(key.asLong());
                NEXT.remove(key.asLong());
                BOARD.values().removeIf(b -> b == key.asLong());
                BlockPos at = o.offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
                level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), at,
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID,
                                        "chests/tier" + Math.min(5, 2 + t.level() / 2))));
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
                level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
                Triggers.tell(player, "Every tile crossed once! Something appears beyond the ice.");
                Powers.bless(level, player, at);
            }
            return;
        }
    }

    /**
     * Cracks a board back to fresh ice, with the player starting again on
     * {@code from} if they are on it (null when they stepped off).
     */
    private static void crack(ServerLevel level, long key, BlockPos from, ServerPlayer player, String why) {
        Set<Long> crossed = CROSSED.get(key);
        if (crossed == null || crossed.isEmpty() || CrawlState.of(level).hasFired(BlockPos.of(key))) {
            return;
        }
        for (long p : crossed) {
            level.setBlock(BlockPos.of(p), FRESH.defaultBlockState(), 3);
        }
        crossed.clear();
        if (from != null) {
            crossed.add(from.asLong());
            level.setBlock(from, Blocks.PACKED_ICE.defaultBlockState(), 3);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 1.4f);
        Triggers.tell(player, why);
    }

    /**
     * Someone is out on an unsolved board: now and then a monster from the
     * level's theme comes for them, with knockback, never more than three
     * about the board at once. Every 10 seconds, give or take 3.
     */
    private static void spawn(ServerLevel level, ServerPlayer player, Site site, BlockPos tile) {
        Long key = BOARD.get(player.getUUID());
        Set<Long> crossed = key == null ? null : CROSSED.get(key);
        if (crossed == null || crossed.isEmpty() || player.isCreative()
                || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        long now = level.getGameTime();
        Long due = NEXT.get(key);
        if (due == null) {
            NEXT.put(key, now + 140 + level.getRandom().nextInt(120));
            return;
        }
        if (now < due) {
            return;
        }
        NEXT.put(key, now + 140 + level.getRandom().nextInt(120));
        if (level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new net.minecraft.world.phys.AABB(tile).inflate(10),
                m -> m.isAlive() && m.getTags().contains(Bestiary.KIN)).size() >= 3) {
            return;
        }
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.ICE_BOARD && site.origin().offset(t.x(), t.y(), t.z()).asLong() == key) {
                BlockPos at = site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
                Bestiary.band(level, site, t.level(), at, 1, player, ObjectiveRooms::shove);
                return;
            }
        }
    }

    /** An uncrossed tile. Blue ice, which never melts; plain ice, on boards built before, is mended to it. */
    static final net.minecraft.world.level.block.Block FRESH = Blocks.BLUE_ICE;

    /**
     * Puts back any tile that is not what it should be: melted to water,
     * broken, or the plain ice of a board built before tiles were blue. A
     * board with a hole in it can never be finished, and the player cannot
     * stand on a hole to have it noticed, so every tile is checked whenever
     * anyone steps onto the board.
     */
    private static void mend(ServerLevel level, BlockPos o, Trigger t, Set<Long> crossed) {
        for (int k = 1; k < t.targets().length; k++) {
            BlockPos p = o.offset(t.targets()[k][0], t.targets()[k][1], t.targets()[k][2]);
            net.minecraft.world.level.block.Block want = crossed.contains(p.asLong()) ? Blocks.PACKED_ICE : FRESH;
            net.minecraft.world.level.block.state.BlockState now = level.getBlockState(p);
            if (now.is(FRESH) || now.is(Blocks.PACKED_ICE)) {
                continue; // fresh or packed is the player's doing, not damage
            }
            level.setBlock(p, want.defaultBlockState(), 3);
        }
    }

    public static void forget(java.util.UUID player) {
        ON.remove(player);
        BOARD.remove(player);
    }

    public static void clear() {
        CROSSED.clear();
        ON.clear();
        BOARD.clear();
        NEXT.clear();
    }
}
